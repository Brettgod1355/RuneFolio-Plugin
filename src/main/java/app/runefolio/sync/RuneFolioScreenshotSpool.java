package app.runefolio.sync;

import com.google.gson.Gson;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Private, bounded disk outbox. JPEG bytes are stored and uploaded without re-encoding. */
final class RuneFolioScreenshotSpool
{
    static final long MAX_BYTES = 256L * 1024 * 1024;
    static final int MAX_FILES = 500;
    static final int MAX_JPEG_BYTES = 3 * 1024 * 1024;
    private static final int MAX_HEADER = 16 * 1024;
    private static final int MAGIC = 0x52465331;
    private final Gson gson;
    private final Path root;
    private final LongSupplier clock;
    private final LongSupplier jitter;
    private final long maxBytes;
    private final int maxFiles;

    interface Uploader { void upload(String token, Entry entry, byte[] jpeg) throws IOException; }

    static final class UploadException extends IOException
    {
        final int status;
        final long retryAfterMillis;
        UploadException(int status, long retryAfterMillis)
        {
            super("Screenshot upload returned HTTP " + status);
            this.status = status;
            this.retryAfterMillis = retryAfterMillis;
        }
        boolean retryable() { return status >= 500 || status == 408 || status == 425 || status == 429; }
    }

    static final class Entry
    {
        String eventId, binding, characterName, identityKey, category, caption, occurredAt, jpegHash;
        long queuedAt;

        Entry(UUID eventId, String token, String characterName, String identityKey,
            String category, String caption, String occurredAt)
        {
            this.eventId = eventId.toString();
            this.binding = fingerprint(token);
            this.characterName = characterName;
            this.identityKey = identityKey;
            this.category = category;
            this.caption = caption;
            this.occurredAt = occurredAt;
        }
    }

    static final class Stats
    {
        final int saved, held;
        final long bytes;
        Stats(int saved, int held, long bytes) { this.saved = saved; this.held = held; this.bytes = bytes; }
    }

    private static final class State { long next; int failures; }
    private static final class Stored
    {
        final Path path;
        final Entry entry;
        Stored(Path path, Entry entry) { this.path = path; this.entry = entry; }
    }

    RuneFolioScreenshotSpool(Path root, Gson gson)
    {
        this(root, gson, System::currentTimeMillis,
            () -> java.util.concurrent.ThreadLocalRandom.current().nextLong(5001), MAX_BYTES, MAX_FILES);
    }

    RuneFolioScreenshotSpool(Path root, Gson gson, LongSupplier clock, LongSupplier jitter, long maxBytes, int maxFiles)
    {
        this.gson = java.util.Objects.requireNonNull(gson);
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
        this.jitter = jitter;
        this.maxBytes = maxBytes;
        this.maxFiles = maxFiles;
    }

    static String fingerprint(String token)
    {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing screenshot connection");
        return hash(("runefolio-screenshot-v1:" + token).getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(byte[] bytes)
    {
        try
        {
            StringBuilder result = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            {
                result.append("0123456789abcdef".charAt((value & 255) >>> 4));
                result.append("0123456789abcdef".charAt(value & 15));
            }
            return result.toString();
        }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** False means full/busy. Existing accepted files are never evicted for new captures. */
    boolean save(Entry entry, byte[] jpeg) throws IOException
    {
        if (!validJpeg(jpeg)) throw new IOException("Invalid or oversized screenshot JPEG");
        entry.jpegHash = hash(jpeg);
        entry.queuedAt = clock.getAsLong();
        validate(entry);
        byte[] header = gson.toJson(entry).getBytes(StandardCharsets.UTF_8);
        if (header.length > MAX_HEADER) throw new IOException("Screenshot metadata too large");
        prepare();
        try (Guard guard = awaitQueueLock())
        {
            if (guard == null) return false;
            Path target = root.resolve(entry.eventId + ".pending");
            if (Files.exists(root.resolve(entry.eventId + ".held"), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Screenshot event is held for review");
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            {
                Entry old = readHeader(target);
                if (old.binding.equals(entry.binding) && old.jpegHash.equals(entry.jpegHash)) return true;
                throw new IOException("Conflicting screenshot event");
            }
            Stats stats = scanStats();
            long bytes = 8L + header.length + jpeg.length;
            if (stats.saved + stats.held >= maxFiles || bytes > maxBytes - stats.bytes) return false;
            Path temporary = root.resolve(UUID.randomUUID() + ".part");
            try
            {
                try (FileChannel channel = createPrivateFile(temporary))
                {
                    DataOutputStream output = new DataOutputStream(Channels.newOutputStream(channel));
                    output.writeInt(MAGIC);
                    output.writeInt(header.length);
                    output.write(header);
                    output.write(jpeg);
                    output.flush();
                    channel.force(true);
                }
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                return true;
            }
            finally { Files.deleteIfExists(temporary); }
        }
    }

    /** One attempt per call; global lock and persisted pacing also cover multiple clients. */
    void drainOnce(Supplier<List<String>> tokens, Uploader uploader) throws IOException
    {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        prepare();
        try (Guard uploadGuard = lock("upload.lock"))
        {
            if (uploadGuard == null || Thread.currentThread().isInterrupted()) return;
            Stored selected = null;
            String token = null;
            State state;
            try (Guard guard = lock("queue.lock"))
            {
                if (guard == null) return;
                state = readState();
                if (clock.getAsLong() < state.next) return;
                List<Stored> entries = new ArrayList<>();
                for (Path path : entries())
                {
                    if (!path.getFileName().toString().endsWith(".pending")) continue;
                    try { entries.add(new Stored(path, readHeader(path))); }
                    catch (IOException | RuntimeException invalid) { hold(path); }
                }
                entries.sort(Comparator.comparingLong((Stored item) -> item.entry.queuedAt)
                    .thenComparing(item -> item.entry.eventId));
                for (Stored item : entries)
                {
                    token = matchingToken(item.entry, tokens.get());
                    if (token != null) { selected = item; break; }
                }
                if (selected == null) return;
                // Persist before networking: a second client/restart cannot burst behind this attempt.
                state.next = clock.getAsLong() + 10_000 + randomDelay();
                writeState(state);
            }
            byte[] jpeg;
            try { jpeg = readJpeg(selected); }
            catch (IOException | RuntimeException invalid)
            {
                try (Guard guard = lock("queue.lock")) { if (guard != null) hold(selected.path); }
                return;
            }
            if (Thread.currentThread().isInterrupted()
                || matchingToken(selected.entry, tokens.get()) == null) return;
            boolean acknowledged = false, permanent = false;
            long retryAfter = 0;
            try { uploader.upload(token, selected.entry, jpeg); acknowledged = true; }
            catch (UploadException failure) { permanent = !failure.retryable(); retryAfter = failure.retryAfterMillis; }
            catch (IOException | RuntimeException unavailable) { /* Keep the exact file for recovery. */ }
            try (Guard guard = lock("queue.lock"))
            {
                if (guard == null) return; // Safe replay if acknowledgement cleanup could not acquire the lock.
                if (acknowledged) Files.deleteIfExists(selected.path);
                else if (permanent) hold(selected.path);
                state.failures = acknowledged || permanent ? 0 : Math.min(6, state.failures + 1);
                long delay = state.failures == 0 ? 10_000 : Math.min(900_000, 30_000L << (state.failures - 1));
                state.next = clock.getAsLong() + Math.max(delay, retryAfter) + randomDelay();
                writeState(state);
            }
        }
    }

    Stats stats() throws IOException
    {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return new Stats(0, 0, 0);
        prepare();
        try (Guard guard = lock("queue.lock")) { return guard == null ? null : scanStats(); }
    }

    /** Explicit user action only. Does not touch website images or unrelated files. */
    boolean clear() throws IOException
    {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return true;
        prepare();
        try (Guard uploadGuard = lock("upload.lock"); Guard guard = lock("queue.lock"))
        {
            if (uploadGuard == null || guard == null) return false;
            for (Path path : entries()) Files.delete(path);
            writeState(new State());
            return true;
        }
    }

    private long randomDelay() { return Math.max(0, Math.min(5000, jitter.getAsLong())); }

    private static String matchingToken(Entry entry, List<String> tokens)
    {
        for (String token : tokens)
            if (token != null && !token.isBlank() && entry.binding.equals(fingerprint(token))) return token;
        return null;
    }

    private void prepare() throws IOException
    {
        if (Files.isSymbolicLink(root)) throw new IOException("Screenshot queue directory must not be a symlink");
        Files.createDirectories(root);
        if (Files.getFileStore(root).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
    }

    private List<Path> entries() throws IOException
    {
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root))
        {
            for (Path path : stream)
            {
                String name = path.getFileName().toString();
                if (!name.matches("[0-9a-f-]{36}\\.(pending|held|part)")) continue;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Invalid screenshot queue file");
                paths.add(path);
            }
        }
        return paths;
    }

    private Stats scanStats() throws IOException
    {
        int saved = 0, held = 0;
        long bytes = 0;
        for (Path path : entries())
        {
            if (path.getFileName().toString().endsWith(".pending")) saved++; else held++;
            bytes += Files.size(path);
        }
        return new Stats(saved, held, bytes);
    }

    private Entry readHeader(Path path) throws IOException
    {
        long size = Files.size(path);
        if (size < 12 || size > MAX_JPEG_BYTES + MAX_HEADER + 8L) throw new IOException("Invalid screenshot file size");
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)))
        {
            if (input.readInt() != MAGIC) throw new IOException("Invalid screenshot file format");
            int length = input.readInt();
            if (length < 1 || length > MAX_HEADER || length > size - 12) throw new IOException("Invalid screenshot header");
            byte[] header = new byte[length];
            input.readFully(header);
            Entry entry = gson.fromJson(new String(header, StandardCharsets.UTF_8), Entry.class);
            validate(entry);
            if (!path.getFileName().toString().equals(entry.eventId + ".pending"))
                throw new IOException("Screenshot event filename mismatch");
            return entry;
        }
    }

    private byte[] readJpeg(Stored stored) throws IOException
    {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(stored.path, LinkOption.NOFOLLOW_LINKS)))
        {
            if (input.readInt() != MAGIC) throw new IOException("Invalid screenshot file");
            int header = input.readInt();
            if (header < 1 || header > MAX_HEADER) throw new IOException("Invalid screenshot header");
            if (input.skipBytes(header) != header) throw new IOException("Truncated screenshot header");
            byte[] jpeg = input.readNBytes(MAX_JPEG_BYTES + 1);
            if (!validJpeg(jpeg) || !hash(jpeg).equals(stored.entry.jpegHash)) throw new IOException("Corrupt screenshot JPEG");
            return jpeg;
        }
    }

    private static boolean validJpeg(byte[] jpeg)
    {
        return jpeg != null && jpeg.length >= 4 && jpeg.length <= MAX_JPEG_BYTES
            && (jpeg[0] & 255) == 255 && (jpeg[1] & 255) == 216
            && (jpeg[jpeg.length - 2] & 255) == 255 && (jpeg[jpeg.length - 1] & 255) == 217;
    }

    private static void validate(Entry entry) throws IOException
    {
        try
        {
            if (entry == null || !UUID.fromString(entry.eventId).toString().equals(entry.eventId)
                || !entry.binding.matches("[0-9a-f]{64}") || !entry.jpegHash.matches("[0-9a-f]{64}")
                || entry.characterName == null || entry.characterName.isBlank() || entry.characterName.length() > 12
                || entry.caption == null || entry.caption.isBlank() || entry.caption.length() > 240
                || entry.category == null || !Set.of("level_up", "quest_completion", "diary_task", "combat_achievement",
                    "collection_log", "pet", "high_value_drop", "untradeable_drop").contains(entry.category)
                || (entry.identityKey != null && entry.identityKey.length() > 200)) throw new IOException("Invalid screenshot metadata");
            Instant.parse(entry.occurredAt);
        }
        catch (RuntimeException invalid) { throw new IOException("Invalid screenshot metadata"); }
    }

    private void hold(Path path) throws IOException
    {
        Path held = root.resolve(path.getFileName().toString().replace(".pending", ".held"));
        if (!Files.exists(held, LinkOption.NOFOLLOW_LINKS)) Files.move(path, held, StandardCopyOption.ATOMIC_MOVE);
    }

    private State readState() throws IOException
    {
        Path path = root.resolve("pacing.json");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return new State();
        if (Files.size(path) > 1024) throw new IOException("Invalid screenshot pacing state");
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS))
        {
            State state = gson.fromJson(new String(input.readNBytes(1025), StandardCharsets.UTF_8), State.class);
            if (state == null || state.next < 0 || state.failures < 0 || state.failures > 6) throw new IOException("Invalid screenshot pacing state");
            return state;
        }
        catch (RuntimeException invalid) { throw new IOException("Invalid screenshot pacing state"); }
    }

    private void writeState(State state) throws IOException
    {
        Path temporary = root.resolve("pacing.tmp");
        if (Files.isSymbolicLink(temporary)) throw new IOException("Invalid screenshot pacing file");
        Files.deleteIfExists(temporary);
        try (FileChannel channel = createPrivateFile(temporary))
        {
            java.nio.ByteBuffer bytes = StandardCharsets.UTF_8.encode(gson.toJson(state));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        Files.move(temporary, root.resolve("pacing.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private FileChannel createPrivateFile(Path path) throws IOException
    {
        if (Files.getFileStore(root).supportsFileAttributeView("posix"))
            return FileChannel.open(path, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        return FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private Guard lock(String name) throws IOException
    {
        Path path = root.resolve(name);
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try
        {
            FileLock lock = channel.tryLock();
            if (lock != null) return new Guard(channel, lock);
        }
        catch (OverlappingFileLockException busy) { /* Another local client/task owns it. */ }
        catch (IOException failure) { channel.close(); throw failure; }
        channel.close();
        return null;
    }

    private Guard awaitQueueLock() throws IOException
    {
        // Only the background encoder calls this; never block the game thread.
        for (int attempt = 0; attempt < 100; attempt++)
        {
            Guard guard = lock("queue.lock");
            if (guard != null) return guard;
            try { Thread.sleep(10); }
            catch (InterruptedException stopped)
            {
                Thread.currentThread().interrupt();
                throw new IOException("Screenshot save interrupted");
            }
        }
        return null;
    }

    private static final class Guard implements AutoCloseable
    {
        final FileChannel channel;
        final FileLock lock;
        Guard(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
        @Override public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
    }
}
