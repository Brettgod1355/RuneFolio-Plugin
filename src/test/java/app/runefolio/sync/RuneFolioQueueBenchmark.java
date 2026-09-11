package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** Opt-in synthetic queue benchmark. No client session, credentials or network. */
public final class RuneFolioQueueBenchmark
{
    private static final class MemoryStorage implements RuneFolioSyncQueue.Storage
    {
        private String value;
        public String get() { return value; }
        public void set(String value) { this.value = value; }
    }

    public static void main(String[] args)
    {
        System.out.println("Queue-only timing; in-memory storage excludes ConfigManager, disk, game and network costs.");
        for (int count : new int[] {10, 500, 950}) run(count);
    }

    private static void run(int count)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("synthetic", "x".repeat(3500));
        JsonArray initial = new JsonArray();
        for (int i = 0; i < count; i++)
            initial.add(RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload).toJson());
        MemoryStorage storage = new MemoryStorage();
        storage.value = initial.toString();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        long[] nanos = new long[60];
        for (int i = -20; i < nanos.length; i++)
        {
            queue.acknowledge(Collections.singleton(queue.snapshot(1, event -> true).get(0).getId()));
            RuneFolioSyncEvent next = RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload);
            long start = System.nanoTime();
            if (!queue.enqueue(next)) throw new AssertionError("Synthetic queue refused an event");
            long elapsed = System.nanoTime() - start;
            if (i >= 0) nanos[i] = elapsed;
        }
        if (new RuneFolioSyncQueue(storage).size() != count) throw new AssertionError("Queue did not survive reload");
        Arrays.sort(nanos);
        System.out.printf(Locale.ROOT, "events=%d storedBytes=%d enqueueMedianMs=%.3f enqueueP95Ms=%.3f%n",
            count, storage.value.getBytes(StandardCharsets.UTF_8).length,
            nanos[nanos.length / 2] / 1_000_000.0, nanos[(int) Math.ceil(nanos.length * 0.95) - 1] / 1_000_000.0);
    }
}
