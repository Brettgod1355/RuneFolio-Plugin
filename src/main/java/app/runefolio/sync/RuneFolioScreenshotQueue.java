package app.runefolio.sync;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounds capture-to-upload work, including pending frame callbacks. */
final class RuneFolioScreenshotQueue implements AutoCloseable
{
    static final int MAX_OUTSTANDING = 3;
    private final Set<Reservation> outstanding = new HashSet<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(MAX_OUTSTANDING),
        new ThreadPoolExecutor.AbortPolicy());
    private boolean closed;

    /** Nonblocking: preserve accepted captures and refuse newest work when full. */
    synchronized Reservation tryReserve()
    {
        if (closed || outstanding.size() >= MAX_OUTSTANDING) return null;
        Reservation reservation = new Reservation();
        outstanding.add(reservation);
        return reservation;
    }

    synchronized int outstandingCount()
    {
        return outstanding.size();
    }

    @Override
    public synchronized void close()
    {
        closed = true;
        for (Reservation reservation : outstanding) reservation.work = null;
        outstanding.clear();
        worker.shutdownNow();
    }

    final class Reservation implements Runnable
    {
        private Runnable work;
        private boolean submitted;
        private boolean frameReceived;

        private Reservation() { }

        boolean markFrameReceived()
        {
            synchronized (RuneFolioScreenshotQueue.this)
            {
                if (closed || frameReceived || !outstanding.contains(this)) return false;
                frameReceived = true;
                return true;
            }
        }

        boolean cancelIfFrameMissing()
        {
            synchronized (RuneFolioScreenshotQueue.this)
            {
                if (frameReceived || submitted) return false;
                release();
                return true;
            }
        }

        boolean submit(Runnable task)
        {
            Objects.requireNonNull(task);
            synchronized (RuneFolioScreenshotQueue.this)
            {
                if (closed || submitted || !outstanding.contains(this)) return false;
                submitted = true;
                work = task;
                try
                {
                    worker.execute(this);
                    return true;
                }
                catch (RejectedExecutionException rejected)
                {
                    release();
                    return false;
                }
            }
        }

        /** Release a capture that never reached the worker; safe to repeat. */
        void cancel()
        {
            synchronized (RuneFolioScreenshotQueue.this)
            {
                if (!submitted) release();
            }
        }

        private void release()
        {
            work = null;
            outstanding.remove(this);
        }

        @Override
        public void run()
        {
            Runnable task;
            synchronized (RuneFolioScreenshotQueue.this)
            {
                task = work;
                work = null;
            }
            boolean cancelled;
            synchronized (RuneFolioScreenshotQueue.this) { cancelled = closed; }
            try
            {
                if (task != null && !cancelled) task.run();
            }
            finally
            {
                synchronized (RuneFolioScreenshotQueue.this)
                {
                    release();
                }
            }
        }
    }
}
