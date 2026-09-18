package app.runefolio.sync;

import com.google.gson.Gson;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;

/** Private, bounded disk outbox. JPEG bytes are stored and uploaded without re-encoding. */
final class RuneFolioScreenshotSpool
{
    static final long MAX_BYTES = 256L * 1024 * 1024;
    static final int MAX_FILES = 500;
    static final int MAX_JPEG_BYTES = 3 * 1024 * 1024;
    private static final int MAX_HEADER = 16 * 1024;
    private static final int MAGIC = 0x52465331;
    private static final int RECORD_PREAMBLE_BYTES = 8;
    private static final int MIN_JPEG_BYTES = 4;
    private static final int MAX_STATE_BYTES = 1024;
    private static final int MAX_FAILURES = 6;
    private static final long BASE_RETRY_MILLIS = 10_000;
    private static final long FIRST_BACKOFF_MILLIS = 30_000;
    private static final long MAX_RETRY_MILLIS = 900_000;
    private static final long MAX_JITTER_MILLIS = 5_000;
    private static final long SAVE_LOCK_WAIT_SECONDS = 1;
    // File locks only exclude other processes: within this client the lock is held by
    // whichever thread got queue.lock first, so the mutex lets the encoder worker wait
    // briefly for it instead of dropping a capture, while upload-side callers never block.
    private static final ReentrantLock QUEUE_MUTEX = new ReentrantLock();
    private final Gson gson;
    private final Filepath root;
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
        final Filepath path;
        final Entry entry;
        Stored(Filepath path, Entry entry) { this.path = path; this.entry = entry; }
    }

    RuneFolioScreenshotSpool(Filepath root, Gson gson)
    {
        this(root, gson, System::currentTimeMillis,
            () -> java.util.concurrent.ThreadLocalRandom.current().nextLong(MAX_JITTER_MILLIS + 1), MAX_BYTES, MAX_FILES);
    }

    RuneFolioScreenshotSpool(Filepath root, Gson gson, LongSupplier clock, LongSupplier jitter, long maxBytes, int maxFiles)
    {
        this.gson = java.util.Objects.requireNonNull(gson);
        this.root = java.util.Objects.requireNonNull(root);
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
        if (!root.exists()) root.createDirectories();
        try (Guard guard = queueLock(true))
        {
            if (guard == null) return false;
            Filepath target = root.joinSegment(entry.eventId + ".pending");
            if (root.joinSegment(entry.eventId + ".held").exists())
                throw new IOException("Screenshot event is held for review");
            if (target.exists())
            {
                Entry old = readHeader(target);
                if (old.binding.equals(entry.binding) && old.jpegHash.equals(entry.jpegHash)) return true;
                throw new IOException("Conflicting screenshot event");
            }
            Stats stats = scanStats();
            long bytes = (long) RECORD_PREAMBLE_BYTES + header.length + jpeg.length;
            if (stats.saved + stats.held >= maxFiles || bytes > maxBytes - stats.bytes) return false;
            Filepath temporary = root.joinSegment(UUID.randomUUID() + ".part");
            boolean created = false;
            try
            {
                try (FileChannel channel = temporary.openFileChannel(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
                {
                    created = true;
                    DataOutputStream output = new DataOutputStream(Channels.newOutputStream(channel));
                    output.writeInt(MAGIC);
                    output.writeInt(header.length);
                    output.write(header);
                    output.write(jpeg);
                    output.flush();
                    channel.force(true);
                }
                temporary.moveTo(target, StandardCopyOption.ATOMIC_MOVE);
                return true;
            }
            finally { if (created) temporary.deleteIfExists(); }
        }
    }

    /** One attempt per call; global lock and persisted pacing also cover multiple clients. */
    void drainOnce(Supplier<List<String>> tokens, Uploader uploader) throws IOException
    {
        if (!root.exists()) return;
        try (Guard uploadGuard = lock("upload.lock"))
        {
            if (uploadGuard == null) return;
            Stored selected = null;
            String token = null;
            State state;
            try (Guard guard = queueLock(false))
            {
                if (guard == null) return;
                state = readState();
                if (clock.getAsLong() < state.next) return;
                List<Stored> entries = new ArrayList<>();
                for (Filepath path : entries())
                {
                    if (!path.getFileName().endsWith(".pending")) continue;
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
                state.next = clock.getAsLong() + BASE_RETRY_MILLIS + randomDelay();
                writeState(state);
            }
            byte[] jpeg;
            try { jpeg = readJpeg(selected); }
            catch (IOException | RuntimeException invalid)
            {
                try (Guard guard = queueLock(false)) { if (guard != null) hold(selected.path); }
                return;
            }
            if (matchingToken(selected.entry, tokens.get()) == null) return;
            boolean acknowledged = false, permanent = false;
            long retryAfter = 0;
            try { uploader.upload(token, selected.entry, jpeg); acknowledged = true; }
            catch (UploadException failure) { permanent = !failure.retryable(); retryAfter = failure.retryAfterMillis; }
            catch (IOException | RuntimeException unavailable) { /* Keep the exact file for recovery. */ }
            try (Guard guard = queueLock(false))
            {
                if (guard == null) return; // Safe replay if acknowledgement cleanup could not acquire the lock.
                if (acknowledged) selected.path.deleteIfExists();
                else if (permanent) hold(selected.path);
                state.failures = acknowledged || permanent ? 0 : Math.min(MAX_FAILURES, state.failures + 1);
                long delay = state.failures == 0
                    ? BASE_RETRY_MILLIS
                    : Math.min(MAX_RETRY_MILLIS, FIRST_BACKOFF_MILLIS << (state.failures - 1));
                state.next = clock.getAsLong() + Math.max(delay, retryAfter) + randomDelay();
                writeState(state);
            }
        }
    }

    Stats stats() throws IOException
    {
        if (!root.exists()) return new Stats(0, 0, 0);
        try (Guard guard = queueLock(false)) { return guard == null ? null : scanStats(); }
    }

    /** Explicit user action only. Does not touch website images or unrelated files. */
    boolean clear() throws IOException
    {
        if (!root.exists()) return true;
        try (Guard uploadGuard = lock("upload.lock"); Guard guard = queueLock(false))
        {
            if (uploadGuard == null || guard == null) return false;
            for (Filepath path : entries()) path.delete();
            writeState(new State());
            return true;
        }
    }

    private long randomDelay() { return Math.max(0, Math.min(MAX_JITTER_MILLIS, jitter.getAsLong())); }

    private static String matchingToken(Entry entry, List<String> tokens)
    {
        for (String token : tokens)
            if (token != null && !token.isBlank() && entry.binding.equals(fingerprint(token))) return token;
        return null;
    }

    private List<Filepath> entries() throws IOException
    {
        List<Filepath> all = new ArrayList<>();
        try (Stream<Filepath> stream = root.walk(1)) { stream.forEach(all::add); }
        List<Filepath> matched = new ArrayList<>();
        for (Filepath path : all)
        {
            if (path.isRoot()) continue;
            String name = path.getFileName();
            if (!name.matches("[0-9a-f-]{36}\\.(pending|held|part)")) continue;
            if (!path.isFile()) throw new IOException("Invalid screenshot queue file");
            matched.add(path);
        }
        return matched;
    }

    private Stats scanStats() throws IOException
    {
        int saved = 0, held = 0;
        long bytes = 0;
        for (Filepath path : entries())
        {
            if (path.getFileName().endsWith(".pending")) saved++; else held++;
            bytes += path.size();
        }
        return new Stats(saved, held, bytes);
    }

    private Entry readHeader(Filepath path) throws IOException
    {
        long size = path.size();
        if (size < RECORD_PREAMBLE_BYTES + MIN_JPEG_BYTES || size > (long) MAX_JPEG_BYTES + MAX_HEADER + RECORD_PREAMBLE_BYTES)
            throw new IOException("Invalid screenshot file size");
        try (DataInputStream input = new DataInputStream(path.openInputStream(LinkOption.NOFOLLOW_LINKS)))
        {
            if (input.readInt() != MAGIC) throw new IOException("Invalid screenshot file format");
            int length = input.readInt();
            if (length < 1 || length > MAX_HEADER || length > size - RECORD_PREAMBLE_BYTES - MIN_JPEG_BYTES)
                throw new IOException("Invalid screenshot header");
            byte[] header = new byte[length];
            input.readFully(header);
            Entry entry = gson.fromJson(new String(header, StandardCharsets.UTF_8), Entry.class);
            validate(entry);
            if (!path.getFileName().equals(entry.eventId + ".pending"))
                throw new IOException("Screenshot event filename mismatch");
            return entry;
        }
    }

    private byte[] readJpeg(Stored stored) throws IOException
    {
        try (DataInputStream input = new DataInputStream(stored.path.openInputStream(LinkOption.NOFOLLOW_LINKS)))
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
        return jpeg != null && jpeg.length >= MIN_JPEG_BYTES && jpeg.length <= MAX_JPEG_BYTES
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
                    "collection_log", "pet", "high_value_drop", "untradeable_drop", "clue_reward", "raid_chest_reward", "pvp_kill", "loot_key").contains(entry.category)
                || (entry.identityKey != null && entry.identityKey.length() > 200)) throw new IOException("Invalid screenshot metadata");
            Instant.parse(entry.occurredAt);
        }
        catch (RuntimeException invalid) { throw new IOException("Invalid screenshot metadata"); }
    }

    private void hold(Filepath path) throws IOException
    {
        Filepath held = root.joinSegment(path.getFileName().replace(".pending", ".held"));
        if (!held.exists()) path.moveTo(held, StandardCopyOption.ATOMIC_MOVE);
    }

    private State readState() throws IOException
    {
        Filepath path = root.joinSegment("pacing.json");
        if (!path.exists()) return new State();
        if (path.size() > MAX_STATE_BYTES) throw new IOException("Invalid screenshot pacing state");
        try (InputStream input = path.openInputStream(LinkOption.NOFOLLOW_LINKS))
        {
            State state = gson.fromJson(new String(input.readNBytes(MAX_STATE_BYTES + 1), StandardCharsets.UTF_8), State.class);
            if (state == null || state.next < 0 || state.failures < 0 || state.failures > MAX_FAILURES)
                throw new IOException("Invalid screenshot pacing state");
            return state;
        }
        catch (RuntimeException invalid) { throw new IOException("Invalid screenshot pacing state"); }
    }

    private void writeState(State state) throws IOException
    {
        Filepath temporary = root.joinSegment("pacing.tmp");
        temporary.deleteIfExists();
        try (FileChannel channel = temporary.openFileChannel(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
        {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(gson.toJson(state));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        temporary.moveTo(root.joinSegment("pacing.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private Guard lock(String name) throws IOException
    {
        Filepath path = root.joinSegment(name);
        FileChannel channel = path.openFileChannel(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try
        {
            FileLock lock = channel.tryLock();
            if (lock != null) return new Guard(channel, lock);
        }
        catch (OverlappingFileLockException busy) { /* Another local client/task owns it. */ }
        catch (IOException | RuntimeException failure) { channel.close(); throw failure; }
        channel.close();
        return null;
    }

    /** Every queue.lock acquisition goes through here so no caller can bypass the in-process mutex. */
    private Guard queueLock(boolean wait) throws IOException
    {
        boolean held;
        try
        {
            held = wait ? QUEUE_MUTEX.tryLock(SAVE_LOCK_WAIT_SECONDS, TimeUnit.SECONDS) : QUEUE_MUTEX.tryLock();
        }
        catch (InterruptedException stopped)
        {
            throw new IOException("Screenshot queue wait interrupted", stopped);
        }
        if (!held) return null;
        Guard guard = null;
        try
        {
            guard = lock("queue.lock");
            return guard == null ? null : new Guard(guard.channel, guard.lock, QUEUE_MUTEX);
        }
        finally
        {
            if (guard == null) QUEUE_MUTEX.unlock();
        }
    }

    private static final class Guard implements AutoCloseable
    {
        final FileChannel channel;
        final FileLock lock;
        final ReentrantLock mutex;
        Guard(FileChannel channel, FileLock lock) { this(channel, lock, null); }
        Guard(FileChannel channel, FileLock lock, ReentrantLock mutex)
        {
            this.channel = channel;
            this.lock = lock;
            this.mutex = mutex;
        }
        @Override public void close() throws IOException
        {
            try { lock.release(); }
            finally
            {
                try { channel.close(); }
                finally { if (mutex != null) mutex.unlock(); }
            }
        }
    }
}
