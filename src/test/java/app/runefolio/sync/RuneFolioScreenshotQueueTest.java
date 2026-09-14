package app.runefolio.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static org.junit.Assert.*;

public class RuneFolioScreenshotQueueTest
{
    @Test
    public void missingFrameIsReleasedButDeliveredFrameKeepsItsSlot()
    {
        try (RuneFolioScreenshotQueue queue = new RuneFolioScreenshotQueue())
        {
            RuneFolioScreenshotQueue.Reservation missing = queue.tryReserve();
            assertTrue(missing.cancelIfFrameMissing());
            assertFalse(missing.markFrameReceived());
            assertFalse(missing.submit(() -> fail("Late frame must not upload")));
            RuneFolioScreenshotQueue.Reservation delivered = queue.tryReserve();
            assertTrue(delivered.markFrameReceived());
            assertFalse(delivered.markFrameReceived());
            assertFalse(delivered.cancelIfFrameMissing());
            assertEquals(1, queue.outstandingCount());
            delivered.cancel();
            assertEquals(0, queue.outstandingCount());
        }
    }

    @Test
    public void boundsReservationsBeforeFramesExistAndReleasesCanceledCapture()
    {
        try (RuneFolioScreenshotQueue queue = new RuneFolioScreenshotQueue())
        {
            RuneFolioScreenshotQueue.Reservation first = queue.tryReserve();
            assertNotNull(first);
            assertNotNull(queue.tryReserve());
            assertNotNull(queue.tryReserve());
            assertNull(queue.tryReserve());
            first.cancel();
            first.cancel();
            assertFalse(first.submit(() -> fail("Canceled callback must not run")));
            assertNotNull(queue.tryReserve());
            assertNull(queue.tryReserve());
            assertEquals(3, queue.outstandingCount());
        }
    }

    @Test
    public void slowUploadPreservesAcceptedOrderAndNeverRunsOnCaller() throws Exception
    {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread caller = Thread.currentThread();
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        try (RuneFolioScreenshotQueue queue = new RuneFolioScreenshotQueue())
        {
            RuneFolioScreenshotQueue.Reservation active = queue.tryReserve();
            assertTrue(active.submit(() -> {
                assertNotSame(caller, Thread.currentThread());
                started.countDown();
                await(release);
                order.add(1);
            }));
            await(started);
            active.cancel(); // A submitted job keeps its slot until completion.
            assertTrue(queue.tryReserve().submit(() -> order.add(2)));
            assertTrue(queue.tryReserve().submit(() -> order.add(3)));
            for (int i = 0; i < 10000; i++) assertNull(queue.tryReserve());
            assertEquals(3, queue.outstandingCount());
            assertTrue(order.isEmpty());
            release.countDown();
            awaitIdle(queue);
            assertEquals(List.of(1, 2, 3), order);
            assertNotNull(queue.tryReserve());
        }
        finally { release.countDown(); }
    }

    @Test
    public void unexpectedWorkerFailureReleasesSlotAndNextWorkRuns() throws Exception
    {
        CountDownLatch failed = new CountDownLatch(1), recovered = new CountDownLatch(1);
        try (RuneFolioScreenshotQueue queue = new RuneFolioScreenshotQueue())
        {
            assertTrue(queue.tryReserve().submit(() -> {
                // Keep expected synthetic failure out of the test runner's stderr.
                Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> failed.countDown());
                throw new IllegalStateException("Synthetic encoder failure");
            }));
            await(failed);
            awaitIdle(queue);
            assertTrue(queue.tryReserve().submit(recovered::countDown));
            await(recovered);
            awaitIdle(queue);
        }
    }

    @Test
    public void shutdownDiscardsWaitingWorkInterruptsActiveAndRejectsLateFrame() throws Exception
    {
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        AtomicBoolean waitingRan = new AtomicBoolean();
        try (RuneFolioScreenshotQueue queue = new RuneFolioScreenshotQueue())
        {
            assertTrue(queue.tryReserve().submit(() -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException expected) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            }));
            await(started);
            assertTrue(queue.tryReserve().submit(() -> waitingRan.set(true)));
            RuneFolioScreenshotQueue.Reservation pendingFrame = queue.tryReserve();
            queue.close();
            await(interrupted);
            assertFalse(waitingRan.get());
            assertNull(queue.tryReserve());
            assertFalse(pendingFrame.submit(() -> fail("Late frame must not upload")));
            pendingFrame.cancel();
            assertEquals(0, queue.outstandingCount());
        }
    }

    @Test
    public void concurrentProducersCannotExceedLimit() throws Exception
    {
        ExecutorService producers = Executors.newFixedThreadPool(8);
        try (RuneFolioScreenshotQueue queue = new RuneFolioScreenshotQueue())
        {
            List<Future<RuneFolioScreenshotQueue.Reservation>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) results.add(producers.submit(queue::tryReserve));
            int accepted = 0;
            for (Future<RuneFolioScreenshotQueue.Reservation> result : results)
                if (result.get(5, TimeUnit.SECONDS) != null) accepted++;
            assertEquals(RuneFolioScreenshotQueue.MAX_OUTSTANDING, accepted);
            assertEquals(accepted, queue.outstandingCount());
        }
        finally { producers.shutdownNow(); }
    }

    @Test
    public void duplicateSubmissionDoesNotReplaceAcceptedWork() throws Exception
    {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (RuneFolioScreenshotQueue queue = new RuneFolioScreenshotQueue())
        {
            RuneFolioScreenshotQueue.Reservation reservation = queue.tryReserve();
            assertTrue(reservation.submit(() -> { started.countDown(); await(release); }));
            await(started);
            assertFalse(reservation.submit(() -> fail("Must not replace accepted work")));
            release.countDown();
            awaitIdle(queue);
            assertFalse(reservation.submit(() -> fail("Must not reuse completed reservation")));
        }
        finally { release.countDown(); }
    }

    private static void await(CountDownLatch latch)
    {
        try { assertTrue("Timed out waiting for worker", latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private static void awaitIdle(RuneFolioScreenshotQueue queue) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (queue.outstandingCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals("Worker did not release reservations", 0, queue.outstandingCount());
    }
}
