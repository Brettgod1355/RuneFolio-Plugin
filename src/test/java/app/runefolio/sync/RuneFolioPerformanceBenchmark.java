package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;

/** Opt-in, synthetic, offline component measurements. Never starts RuneLite. */
public final class RuneFolioPerformanceBenchmark
{
    private static final int SAMPLES = 30;
    private static final int WARMUPS = 10;

    private static final class MemoryStorage implements RuneFolioSyncQueue.Storage
    {
        private String value;
        public String get() { return value; }
        public void set(String value) { this.value = value; }
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 0) throw new IllegalArgumentException("Offline benchmark accepts no arguments");
        System.out.println("SYNTHETIC COMPONENTS ONLY: excludes game collectors, client FPS, ConfigManager/disk, network and server capacity.");
        for (int count : new int[] {10, 500, 950}) queue(count);
        for (int[] size : new int[][] {{765, 503}, {1920, 1080}, {3840, 2160}}) screenshot(size[0], size[1]);
    }

    private static void queue(int count)
    {
        JsonObject loot = new JsonObject();
        loot.addProperty("synthetic", "x".repeat(3500));
        JsonArray seed = new JsonArray();
        for (int i = 0; i < count; i++)
            seed.add(RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", loot).toJson());
        String saved = seed.toString();
        long[] reload = new long[SAMPLES], snapshot = new long[SAMPLES], acknowledge = new long[SAMPLES], burst = new long[SAMPLES];
        for (int sample = -WARMUPS; sample < SAMPLES; sample++)
        {
            MemoryStorage storage = new MemoryStorage();
            storage.value = saved;
            long start = System.nanoTime();
            RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
            long loaded = System.nanoTime();
            List<RuneFolioSyncEvent> batch = queue.snapshot(50, event -> true);
            long snapshotted = System.nanoTime();
            queue.acknowledge(batch.stream().map(RuneFolioSyncEvent::getId).collect(Collectors.toList()));
            long acknowledged = System.nanoTime();
            // Four different snapshot categories exercise the queue stage of a
            // full sync, NOT live game scripts or the real collector payloads.
            for (String type : new String[] {RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE,
                RuneFolioSyncEvent.DIARY_SNAPSHOT_TYPE, RuneFolioSyncEvent.COMBAT_SNAPSHOT_TYPE,
                RuneFolioSyncEvent.COLLECTION_CATEGORY_TYPE})
            {
                JsonObject state = new JsonObject();
                JsonArray entries = new JsonArray();
                for (int i = 0; i < 500; i++)
                {
                    JsonObject row = new JsonObject();
                    row.addProperty("name", "Example task " + i);
                    row.addProperty("completed", i % 2 == 0);
                    entries.add(row);
                }
                state.add("syntheticEntries", entries);
                if (!queue.enqueue(RuneFolioSyncEvent.progressSnapshot(type, "Example", "manual", state)))
                    throw new AssertionError("Snapshot-shaped event was refused");
            }
            long finished = System.nanoTime();
            int expected = count - Math.min(count, 50) + 4;
            if (new RuneFolioSyncQueue(storage).size() != expected)
                throw new AssertionError("Recovery changed queue count");
            if (sample >= 0)
            {
                reload[sample] = loaded - start;
                snapshot[sample] = snapshotted - loaded;
                acknowledge[sample] = acknowledged - snapshotted;
                burst[sample] = finished - acknowledged;
            }
        }
        report("reload-events-" + count, reload);
        report("snapshot50-events-" + count, snapshot);
        report("ack50-events-" + count, acknowledge);
        report("four-synthetic-snapshots-backlog-" + count, burst);
    }

    private static void screenshot(int width, int height) throws Exception
    {
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        int[] row = new int[width];
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++) row[x] = random.nextInt(0x1000000);
            frame.setRGB(0, y, width, 1, row, 0, width);
        }
        long[] times = new long[SAMPLES];
        int encodedBytes = 0;
        for (int sample = -WARMUPS; sample < SAMPLES; sample++)
        {
            long start = System.nanoTime();
            byte[] encoded = RuneFolioScreenshotEncoder.encode(frame);
            if (sample >= 0) times[sample] = System.nanoTime() - start;
            encodedBytes = encoded.length;
            if (encodedBytes == 0) throw new AssertionError("Empty JPEG");
        }
        report("jpeg-" + width + "x" + height, times);
        System.out.printf(Locale.ROOT, "frame=%dx%d rawPixelBytes=%d encodedBytes=%d%n",
            width, height, (long) width * height * 4, encodedBytes);
        frame.flush();
    }

    private static void report(String stage, long[] values)
    {
        Arrays.sort(values);
        System.out.printf(Locale.ROOT, "stage=%s samples=%d medianMs=%.3f p95Ms=%.3f%n",
            stage, values.length, values[values.length / 2] / 1_000_000.0,
            values[(int) Math.ceil(values.length * .95) - 1] / 1_000_000.0);
    }
}
