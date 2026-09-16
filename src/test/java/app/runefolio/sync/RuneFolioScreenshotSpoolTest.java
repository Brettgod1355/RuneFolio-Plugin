package app.runefolio.sync;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class RuneFolioScreenshotSpoolTest
{
    private static final String TOKEN = "synthetic-connection-a";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final AtomicLong now = new AtomicLong(1_000_000);

    private RuneFolioScreenshotSpool spool(Path root)
    {
        return new RuneFolioScreenshotSpool(root, new com.google.gson.Gson(), now::get, () -> 0, RuneFolioScreenshotSpool.MAX_BYTES, 500);
    }

    private RuneFolioScreenshotSpool.Entry entry(String token, String name)
    {
        return new RuneFolioScreenshotSpool.Entry(UUID.randomUUID(), token, name,
            "synthetic-identity", "level_up", "Example level 2", "2026-01-01T00:00:00Z");
    }

    private byte[] jpeg() throws IOException
    {
        return RuneFolioScreenshotEncoder.encode(new BufferedImage(32, 20, BufferedImage.TYPE_INT_RGB));
    }

    @Test
    public void preservesExactJpegAndIdentityAcrossRestartAndDeletesOnlyAfterAck() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        byte[] jpeg = jpeg();
        RuneFolioScreenshotSpool.Entry original = entry(TOKEN, "Example");
        assertTrue(spool(root).save(original, jpeg));
        byte[] disk = Files.readAllBytes(root.resolve(original.eventId + ".pending"));
        assertFalse(new String(disk, java.nio.charset.StandardCharsets.ISO_8859_1).contains(TOKEN));
        AtomicInteger uploads = new AtomicInteger();
        spool(root).drainOnce(() -> List.of(TOKEN), (token, recovered, bytes) -> {
            assertEquals(TOKEN, token);
            assertEquals(original.eventId, recovered.eventId);
            assertEquals(original.identityKey, recovered.identityKey);
            assertEquals(original.characterName, recovered.characterName);
            assertEquals(original.occurredAt, recovered.occurredAt);
            assertArrayEquals(jpeg, bytes);
            assertTrue(Files.exists(root.resolve(original.eventId + ".pending")));
            uploads.incrementAndGet();
        });
        assertEquals(1, uploads.get());
        assertEquals(0, spool(root).stats().saved);
    }

    @Test
    public void outageRetainsMoreThanThreePicturesAndSurvivesRestart() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool spool = spool(root);
        byte[] jpeg = jpeg();
        for (int i = 0; i < 10; i++) assertTrue(spool.save(entry(TOKEN, "Example"), jpeg));
        AtomicInteger attempts = new AtomicInteger();
        RuneFolioScreenshotSpool.Uploader unavailable = (token, entry, bytes) -> { attempts.incrementAndGet(); throw new IOException("Synthetic outage"); };
        spool.drainOnce(() -> List.of(TOKEN), unavailable);
        assertEquals(10, spool(root).stats().saved);
        spool(root).drainOnce(() -> List.of(TOKEN), unavailable);
        assertEquals(1, attempts.get());
        now.addAndGet(30_000);
        spool(root).drainOnce(() -> List.of(TOKEN), unavailable);
        assertEquals(2, attempts.get());
        now.addAndGet(59_999);
        spool(root).drainOnce(() -> List.of(TOKEN), unavailable);
        assertEquals(2, attempts.get());
        now.incrementAndGet();
        spool(root).drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> attempts.incrementAndGet());
        assertEquals(3, attempts.get());
        assertEquals(9, spool.stats().saved);
    }

    @Test
    public void successDrainsOneAtATimeWithPersistedSpacing() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool spool = spool(root);
        byte[] jpeg = jpeg();
        for (int i = 0; i < 3; i++) assertTrue(spool.save(entry(TOKEN, "Example"), jpeg));
        AtomicInteger count = new AtomicInteger();
        RuneFolioScreenshotSpool.Uploader uploader = (token, entry, bytes) -> count.incrementAndGet();
        spool.drainOnce(() -> List.of(TOKEN), uploader);
        spool(root).drainOnce(() -> List.of(TOKEN), uploader);
        assertEquals(1, count.get());
        now.addAndGet(9999);
        spool.drainOnce(() -> List.of(TOKEN), uploader);
        assertEquals(1, count.get());
        now.incrementAndGet();
        spool.drainOnce(() -> List.of(TOKEN), uploader);
        assertEquals(2, count.get());
        assertEquals(1, spool.stats().saved);
    }

    @Test
    public void differentConnectionCannotReceiveOldPictures() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool spool = spool(root);
        assertTrue(spool.save(entry(TOKEN, "Example"), jpeg()));
        spool.drainOnce(() -> List.of("synthetic-connection-b"), (token, entry, bytes) -> fail("Cross-account upload"));
        assertEquals(1, spool.stats().saved);
        spool.drainOnce(List::of, (token, entry, bytes) -> fail("Disconnected upload"));
        assertEquals(1, spool.stats().saved);
        spool.drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> assertEquals("Example", entry.characterName));
        assertEquals(0, spool.stats().saved);
    }

    @Test
    public void respectsServerRetryAfterWithoutDiscardingRateLimitedPicture() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool spool = spool(root);
        assertTrue(spool.save(entry(TOKEN, "Example"), jpeg()));
        spool.drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> { throw new RuneFolioScreenshotSpool.UploadException(429, 120_000); });
        now.addAndGet(119_999);
        spool(root).drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> fail("Retry too early"));
        assertEquals(1, spool.stats().saved);
        now.incrementAndGet();
        spool.drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> { });
        assertEquals(0, spool.stats().saved);
    }

    @Test
    public void permanentRejectionIsHeldNotDeletedOrRepeated() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool spool = spool(root);
        assertTrue(spool.save(entry(TOKEN, "Example"), jpeg()));
        spool.drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> { throw new RuneFolioScreenshotSpool.UploadException(401, 0); });
        assertEquals(0, spool.stats().saved);
        assertEquals(1, spool.stats().held);
        now.addAndGet(1_000_000);
        spool(root).drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> fail("Held upload repeated"));
        assertEquals(1, spool.stats().held);
    }

    @Test
    public void countAndByteLimitsPreserveAcceptedPictures() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool spool = new RuneFolioScreenshotSpool(root, new com.google.gson.Gson(), now::get, () -> 0, 100_000, 2);
        assertTrue(spool.save(entry(TOKEN, "Example"), jpeg()));
        assertTrue(spool.save(entry(TOKEN, "Example"), jpeg()));
        assertFalse(spool.save(entry(TOKEN, "Example"), jpeg()));
        assertEquals(2, spool.stats().saved);
        RuneFolioScreenshotSpool tiny = new RuneFolioScreenshotSpool(temporary.newFolder().toPath(), new com.google.gson.Gson(), now::get, () -> 0, 1, 500);
        assertFalse(tiny.save(entry(TOKEN, "Example"), jpeg()));
        assertEquals(0, tiny.stats().saved);
    }

    @Test
    public void corruptedJpegIsHeldAndDoesNotBlockHealthyPicture() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool spool = spool(root);
        RuneFolioScreenshotSpool.Entry first = entry(TOKEN, "Example");
        assertTrue(spool.save(first, jpeg()));
        Path path = root.resolve(first.eventId + ".pending");
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 3] ^= 1;
        Files.write(path, bytes);
        spool.drainOnce(() -> List.of(TOKEN), (token, entry, image) -> fail("Corrupt image uploaded"));
        assertEquals(1, spool.stats().held);
        assertTrue(spool.save(entry(TOKEN, "Example"), jpeg()));
        now.addAndGet(10_000);
        spool.drainOnce(() -> List.of(TOKEN), (token, entry, image) -> { });
        assertEquals(0, spool.stats().saved);
        assertEquals(1, spool.stats().held);
    }

    @Test
    public void malformedHeaderAndInterruptedPartAreNotUploaded() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        Files.write(root.resolve(UUID.randomUUID() + ".pending"), ByteBuffer.allocate(16).putInt(0x52465331).putInt(Integer.MAX_VALUE).array());
        Files.write(root.resolve(UUID.randomUUID() + ".part"), new byte[] {1, 2, 3});
        RuneFolioScreenshotSpool spool = spool(root);
        spool.drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> fail("Incomplete file uploaded"));
        assertEquals(0, spool.stats().saved);
        assertEquals(2, spool.stats().held);
    }

    @Test
    public void explicitClearLeavesUnrelatedFilesAlone() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        Path unrelated = root.resolve("keep.txt");
        Files.writeString(unrelated, "Unrelated file");
        RuneFolioScreenshotSpool spool = spool(root);
        assertTrue(spool.save(entry(TOKEN, "Example"), jpeg()));
        assertTrue(spool.clear());
        assertEquals(0, spool.stats().saved);
        assertEquals("Unrelated file", Files.readString(unrelated));
    }

    @Test
    public void simultaneousClientsSerializeUploadsWithoutBlockingDiskCapture() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool first = spool(root), second = spool(root);
        byte[] jpeg = jpeg();
        assertTrue(first.save(entry(TOKEN, "Example"), jpeg));
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try
        {
            Future<?> upload = worker.submit(() -> {
                first.drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> {
                    started.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timeout"); }
                    catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IOException(stopped); }
                });
                return null;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(second.save(entry(TOKEN, "Example"), jpeg));
            now.addAndGet(100_000);
            second.drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> fail("Concurrent upload"));
            assertFalse(second.clear());
            release.countDown();
            upload.get(5, TimeUnit.SECONDS);
            assertEquals(1, second.stats().saved);
        }
        finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test
    public void concurrentSavesRespectOneSharedCapacity() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        byte[] jpeg = jpeg();
        try
        {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) results.add(workers.submit(() ->
                new RuneFolioScreenshotSpool(root, new com.google.gson.Gson(), now::get, () -> 0, 100_000, 5).save(entry(TOKEN, "Example"), jpeg)));
            int accepted = 0;
            for (Future<Boolean> result : results) if (result.get(5, TimeUnit.SECONDS)) accepted++;
            assertEquals(5, accepted);
            assertEquals(5, spool(root).stats().saved);
        }
        finally { workers.shutdownNow(); }
    }

    @Test
    public void corruptPacingStateFailsClosedWithoutRemovingImages() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool spool = spool(root);
        assertTrue(spool.save(entry(TOKEN, "Example"), jpeg()));
        Files.writeString(root.resolve("pacing.json"), "not-json");
        try { spool.drainOnce(() -> List.of(TOKEN), (token, entry, bytes) -> fail()); fail("Should reject bad state"); }
        catch (IOException expected) { }
        assertEquals(1, spool.stats().saved);
    }

    @Test
    public void symlinkedQueueCannotWriteOutsideItsDirectory() throws Exception
    {
        Path parent = temporary.newFolder().toPath(), target = temporary.newFolder().toPath();
        Path link = parent.resolve("queue");
        try
        {
            Files.createSymbolicLink(link, target);
        }
        catch (UnsupportedOperationException | java.nio.file.FileSystemException noSymlinkPrivilege)
        {
            // Creating a symlink needs an OS privilege (e.g. Windows Developer Mode) this
            // environment may not grant; skip rather than fail on an unrelated limitation.
            org.junit.Assume.assumeNoException(noSymlinkPrivilege);
        }
        try { spool(link).save(entry(TOKEN, "Example"), jpeg()); fail("Symlink accepted"); }
        catch (IOException expected) { }
        try (java.util.stream.Stream<Path> files = Files.list(target)) { assertEquals(0, files.count()); }
    }
}
