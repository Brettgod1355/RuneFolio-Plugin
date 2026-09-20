package app.runefolio.sync;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Opt-in synthetic queue benchmark. No client session, credentials or network. */
public final class RuneFolioQueueBenchmark
{
    private static final String BINDING = RuneFolioSyncQueue.binding("synthetic-benchmark-connection");

    private static final class MemoryStorage implements RuneFolioSyncQueue.Storage
    {
        private final Map<String, String> entries = new LinkedHashMap<>();

        @Override
        public Map<String, String> load()
        {
            return new LinkedHashMap<>(entries);
        }

        @Override
        public void put(String eventId, String json)
        {
            entries.put(eventId, json);
        }

        @Override
        public void remove(String eventId)
        {
            entries.remove(eventId);
        }

        private long storedBytes()
        {
            long bytes = 0;
            for (Map.Entry<String, String> entry : entries.entrySet())
            {
                bytes += entry.getKey().getBytes(StandardCharsets.UTF_8).length
                    + entry.getValue().getBytes(StandardCharsets.UTF_8).length;
            }
            return bytes;
        }
    }

    private static String stored(RuneFolioSyncEvent event, long sequence)
    {
        JsonObject entry = new JsonObject();
        entry.addProperty("s", sequence);
        entry.addProperty("b", BINDING);
        entry.add("e", event.toJson());
        return entry.toString();
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
        MemoryStorage storage = new MemoryStorage();
        for (int i = 0; i < count; i++)
        {
            RuneFolioSyncEvent event = RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload);
            storage.entries.put(event.getId(), stored(event, i));
        }
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage, () -> Set.of(BINDING));
        long[] nanos = new long[60];
        for (int i = -20; i < nanos.length; i++)
        {
            queue.acknowledge(Collections.singleton(queue.snapshot(1, BINDING, event -> true).get(0).getId()));
            RuneFolioSyncEvent next = RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload);
            long start = System.nanoTime();
            if (!queue.enqueue(next, BINDING)) throw new AssertionError("Synthetic queue refused an event");
            long elapsed = System.nanoTime() - start;
            if (i >= 0) nanos[i] = elapsed;
        }
        if (new RuneFolioSyncQueue(storage, () -> Set.of(BINDING)).size() != count) throw new AssertionError("Queue did not survive reload");
        Arrays.sort(nanos);
        System.out.printf(Locale.ROOT, "events=%d storedBytes=%d enqueueMedianMs=%.3f enqueueP95Ms=%.3f%n",
            count, storage.storedBytes(),
            nanos[nanos.length / 2] / 1_000_000.0, nanos[(int) Math.ceil(nanos.length * 0.95) - 1] / 1_000_000.0);
    }
}
