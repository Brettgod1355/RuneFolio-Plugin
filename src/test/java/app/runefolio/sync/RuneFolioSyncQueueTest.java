package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioSyncQueueTest
{
    private static final String TOKEN_A = "synthetic-connection-a";
    private static final String TOKEN_B = "synthetic-connection-b";
    private static final String BOUND_A = RuneFolioSyncQueue.binding(TOKEN_A);
    private static final String BOUND_B = RuneFolioSyncQueue.binding(TOKEN_B);

    private static RuneFolioSyncQueue queue(MemoryStorage storage)
    {
        return new RuneFolioSyncQueue(storage, () -> Set.of(BOUND_A, BOUND_B));
    }

    private static RuneFolioSyncQueue queue(MemoryStorage storage, Supplier<Set<String>> deliverable)
    {
        return new RuneFolioSyncQueue(storage, deliverable);
    }

    @Test public void oversizedReplacementPreservesPreviouslyQueuedSnapshot()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent first = RuneFolioSyncEvent.historyEvent("bank.snapshot", "Example", new JsonObject());
        Assert.assertTrue(queue.enqueue(first, BOUND_A));
        JsonObject huge = new JsonObject(); huge.addProperty("value", "x".repeat(600_000));
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.historyEvent("bank.snapshot", "Example", huge), BOUND_A));
        Assert.assertEquals(first.getId(), queue(storage).snapshot(50, BOUND_A, event -> true).get(0).getId());
    }
    @Test public void uploadBatchesStayBelowOneMegabyte()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        JsonObject payload = new JsonObject(); payload.addProperty("value", "x".repeat(300_000));
        for (int i=0;i<5;i++) Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.historyEvent("bank.snapshot", "Example"+i, payload), BOUND_A));
        Assert.assertEquals(3, queue.snapshot(50, BOUND_A, event -> true).size());
        Assert.assertEquals(5, queue.size());
    }
    private static final class MemoryStorage implements RuneFolioSyncQueue.Storage
    {
        private String value;
        private String legacy;
        private int writes;
        private boolean fail;

        @Override
        public String get()
        {
            return value;
        }

        @Override
        public void set(String value)
        {
            if (fail) throw new IllegalStateException("Synthetic storage failure");
            writes++;
            this.value = value;
        }

        @Override
        public String getLegacy()
        {
            return legacy;
        }

        @Override
        public void clearLegacy()
        {
            legacy = null;
        }
    }

    @Test
    public void fullQueueRefusesWithoutRewritingAndDrainsInOrder()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        for (int i = 0; i < 1000; i++)
            Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Example", "Item " + i), BOUND_A));
        String saved = storage.value;
        int writes = storage.writes;
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Example", "Overflow"), BOUND_A));
        queue.acknowledge(Collections.singleton("unknown"));
        Assert.assertEquals(writes, storage.writes);
        Assert.assertEquals(saved, storage.value);
        RuneFolioSyncQueue restored = queue(storage);
        for (int i = 0; i < 1000; i += 50)
        {
            java.util.List<RuneFolioSyncEvent> batch = restored.snapshot(50, BOUND_A, event -> true);
            Assert.assertEquals(50, batch.size());
            Assert.assertEquals("Item " + i, batch.get(0).toJson().getAsJsonObject("payload").get("itemName").getAsString());
            java.util.List<String> ids = new java.util.ArrayList<>();
            batch.forEach(event -> ids.add(event.getId()));
            restored.acknowledge(ids);
        }
        Assert.assertEquals(0, queue(storage).size());
        Assert.assertEquals("[]", storage.value);
    }

    @Test
    public void utf8BudgetsSurviveAcknowledgementAndReload()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        JsonObject payload = new JsonObject();
        payload.addProperty("value", "界".repeat(100_000));
        for (int i = 0; i < 13; i++)
            Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload), BOUND_A));
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload), BOUND_A));
        RuneFolioSyncQueue restored = queue(storage);
        Assert.assertEquals(3, restored.snapshot(50, BOUND_A, event -> true).size());
        restored.acknowledge(Collections.singleton(restored.snapshot(1, BOUND_A, event -> true).get(0).getId()));
        Assert.assertTrue(restored.enqueue(RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload), BOUND_A));
        Assert.assertEquals(13, queue(storage).size());
    }

    @Test
    public void cachedEventsAreIsolatedFromCallerMutationAndKeepIdentityFilters()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        String identity = "a".repeat(64);
        RuneFolioSyncEvent original = RuneFolioSyncEvent.collectionLogUnlock("Example", "Item").withIdentityKey(identity);
        Assert.assertTrue(queue.enqueue(original, BOUND_A));
        original.withIdentityKey("b".repeat(64));
        RuneFolioSyncEvent upload = queue.snapshot(1, BOUND_A, event -> identity.equals(event.getIdentityKey())).get(0);
        upload.withIdentityKey("c".repeat(64));
        Assert.assertEquals(identity, queue.snapshot(1, BOUND_A, event -> true).get(0).getIdentityKey());
        Assert.assertEquals(identity, queue(storage).snapshot(1, BOUND_A, event -> true).get(0).getIdentityKey());
        Assert.assertTrue(queue.snapshot(0, BOUND_A, event -> true).isEmpty());
    }

    @Test
    public void oldInFlightAcknowledgementCannotRemoveReplacement()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent first = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "periodic", new JsonObject());
        RuneFolioSyncEvent next = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "manual", new JsonObject());
        Assert.assertTrue(queue.enqueue(first, BOUND_A));
        Assert.assertTrue(queue.enqueue(next, BOUND_A));
        queue.acknowledge(Collections.singleton(first.getId()));
        Assert.assertEquals(next.getId(), queue(storage).snapshot(1, BOUND_A, event -> true).get(0).getId());
    }

    @Test
    public void failedPersistenceDoesNotPublishReplacementOrAcknowledgement()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent first = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "periodic", new JsonObject());
        Assert.assertTrue(queue.enqueue(first, BOUND_A));
        String saved = storage.value;
        storage.fail = true;
        try
        {
            queue.enqueue(RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "manual", new JsonObject()), BOUND_A);
            Assert.fail("Expected storage failure");
        }
        catch (IllegalStateException expected) { }
        try
        {
            queue.acknowledge(Collections.singleton(first.getId()));
            Assert.fail("Expected storage failure");
        }
        catch (IllegalStateException expected) { }
        Assert.assertEquals(saved, storage.value);
        Assert.assertEquals(first.getId(), queue.snapshot(1, BOUND_A, event -> true).get(0).getId());
        storage.fail = false;
        queue.acknowledge(Collections.singleton(first.getId()));
        Assert.assertEquals(0, queue(storage).size());
    }

    @Test
    public void partialAcknowledgementRemovesOnlyConfirmedEvents()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent first = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Abyssal whip");
        RuneFolioSyncEvent second = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Jar of souls");
        RuneFolioSyncEvent third = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Pet kraken");

        Assert.assertTrue(queue.enqueue(first, BOUND_A));
        Assert.assertTrue(queue.enqueue(second, BOUND_A));
        Assert.assertTrue(queue.enqueue(third, BOUND_A));
        queue.acknowledge(Collections.singleton(first.getId()));

        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(
            Arrays.asList(second.getId(), third.getId()),
            Arrays.asList(
                queue.snapshot(10, BOUND_A, event -> true).get(0).getId(),
                queue.snapshot(10, BOUND_A, event -> true).get(1).getId()
            )
        );

        RuneFolioSyncQueue restored = queue(storage);
        Assert.assertEquals(2, restored.size());
    }

    @Test
    public void unacknowledgedEventsRemainQueued()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent event = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Dragon pickaxe");

        Assert.assertTrue(queue.enqueue(event, BOUND_A));
        queue.acknowledge(Collections.emptyList());

        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(event.getId(), queue.snapshot(1, BOUND_A, queued -> true).get(0).getId());
    }

    @Test
    public void corruptSavedQueueIsDiscardedAndRewrittenWithoutFailing()
    {
        MemoryStorage storage = new MemoryStorage();
        storage.value = "{\"characterName\":\"Example\",\"secret\":\"not-an-array\"}";
        Assert.assertEquals(0, queue(storage).size());
        Assert.assertEquals("[]", storage.value);

        RuneFolioSyncEvent event = RuneFolioSyncEvent.collectionLogUnlock("Example", "Abyssal whip");
        String stored = "{\"b\":\"" + BOUND_A + "\",\"e\":" + event.toJson() + "}";
        storage.value = "[\"string\",42,null," + stored + ",{\"type\":\"unknown\"},{\"b\":\"\",\"e\":" + event.toJson() + "}," + event.toJson() + "]";
        RuneFolioSyncQueue queue = queue(storage);
        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(event.getId(), queue.snapshot(1, BOUND_A, queued -> true).get(0).getId());
        Assert.assertEquals("[" + stored + "]", storage.value);

        storage.value = "not json at all";
        Assert.assertEquals(0, queue(storage).size());
        Assert.assertEquals("[]", storage.value);
    }

    @Test
    public void consecutiveLootDropsNeverReplaceEachOther()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        JsonArray items = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("itemId", 995);
        item.addProperty("itemName", "Coins");
        item.addProperty("quantity", 10);
        items.add(item);

        Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.lootDrop(
            "Example Main", "Goblin", "npc", 2, 1, items, 10, 10
        ), BOUND_A));
        Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.lootDrop(
            "Example Main", "Goblin", "npc", 2, 1, items, 10, 10
        ), BOUND_A));

        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(2, queue(storage).size());
    }

    @Test
    public void eventsOnlyFlushUnderTheConnectionThatRecordedThem()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent friends = RuneFolioSyncEvent.collectionLogUnlock("Friend", "Abyssal whip").withIdentityKey("f".repeat(64));
        RuneFolioSyncEvent owners = RuneFolioSyncEvent.collectionLogUnlock("Owner", "Jar of souls").withIdentityKey("e".repeat(64));
        Assert.assertTrue(queue.enqueue(friends, BOUND_A));
        Assert.assertTrue(queue.enqueue(owners, BOUND_B));

        List<RuneFolioSyncEvent> underB = queue.snapshot(50, BOUND_B, event -> true);
        Assert.assertEquals(1, underB.size());
        Assert.assertEquals(owners.getId(), underB.get(0).getId());
        Assert.assertEquals(friends.getId(), queue.snapshot(50, BOUND_A, event -> true).get(0).getId());
        Assert.assertTrue(queue.snapshot(50, RuneFolioSyncQueue.binding("unrelated"), event -> true).isEmpty());
        Assert.assertTrue(queue.snapshot(50, null, event -> true).isEmpty());

        RuneFolioSyncQueue restored = queue(storage);
        Assert.assertEquals(1, restored.snapshot(50, BOUND_B, event -> true).size());
        Assert.assertEquals(owners.getId(), restored.snapshot(50, BOUND_B, event -> true).get(0).getId());
    }

    @Test
    public void bindingNeverLeavesTheClient()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent event = RuneFolioSyncEvent.collectionLogUnlock("Example", "Abyssal whip");
        Assert.assertTrue(queue.enqueue(event, BOUND_A));

        JsonObject stored = new JsonParser().parse(storage.value).getAsJsonArray().get(0).getAsJsonObject();
        Assert.assertEquals(BOUND_A, stored.get("b").getAsString());
        Assert.assertFalse(stored.getAsJsonObject("e").has("b"));
        Assert.assertFalse(stored.getAsJsonObject("e").has("binding"));
        Assert.assertFalse(queue.snapshot(1, BOUND_A, queued -> true).get(0).toJson().toString().contains(BOUND_A));
        Assert.assertFalse(storage.value.contains(TOKEN_A));
        Assert.assertTrue(BOUND_A.matches("[0-9a-f]{64}"));
        Assert.assertNotEquals(BOUND_A, RuneFolioScreenshotSpool.fingerprint(TOKEN_A));
    }

    @Test
    public void captureWithoutAConnectionIsRefused()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Example", "Abyssal whip"), null));
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Example", "Abyssal whip"), " "));
        Assert.assertEquals(0, queue.size());
        Assert.assertNull(storage.value);
    }

    @Test
    public void queueSavedBeforeBindingsIsDiscardedOnce()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncEvent old = RuneFolioSyncEvent.collectionLogUnlock("Example", "Abyssal whip");
        storage.legacy = "[" + old.toJson() + "," + old.toJson() + "]";

        RuneFolioSyncQueue queue = queue(storage);
        Assert.assertEquals(0, queue.size());
        Assert.assertNull(storage.legacy);
        Assert.assertTrue(queue.snapshot(50, BOUND_A, event -> true).isEmpty());

        RuneFolioSyncEvent fresh = RuneFolioSyncEvent.collectionLogUnlock("Example", "Jar of souls");
        Assert.assertTrue(queue.enqueue(fresh, BOUND_A));
        Assert.assertEquals(fresh.getId(), queue(storage).snapshot(1, BOUND_A, event -> true).get(0).getId());
        Assert.assertFalse(storage.value.contains(old.getId()));
    }

    @Test
    public void fullQueueEvictsOnlyStrandedEntriesOldestFirst()
    {
        MemoryStorage storage = new MemoryStorage();
        Set<String>[] deliverable = new Set[] {Set.of(BOUND_A, BOUND_B)};
        RuneFolioSyncQueue queue = queue(storage, () -> deliverable[0]);
        RuneFolioSyncEvent oldestStranded = RuneFolioSyncEvent.collectionLogUnlock("Friend", "Item 0");
        Assert.assertTrue(queue.enqueue(oldestStranded, BOUND_A));
        RuneFolioSyncEvent ownersSnapshot = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Owner", "periodic", new JsonObject());
        Assert.assertTrue(queue.enqueue(ownersSnapshot, BOUND_B));
        for (int i = 1; i < 999; i++)
            Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Friend", "Item " + i), BOUND_A));
        Assert.assertEquals(1000, queue.size());

        // Both connections are still held: nothing may be dropped, so the queue refuses.
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Owner", "Overflow"), BOUND_B));
        Assert.assertEquals(1000, queue.size());

        // The friend's connection is gone: exactly enough of the oldest stranded entries make room.
        deliverable[0] = Set.of(BOUND_B);
        RuneFolioSyncEvent ownersNew = RuneFolioSyncEvent.collectionLogUnlock("Owner", "New drop");
        Assert.assertTrue(queue.enqueue(ownersNew, BOUND_B));
        Assert.assertEquals(1000, queue.size());
        Assert.assertTrue(queue.snapshot(1000, BOUND_A, event -> oldestStranded.getId().equals(event.getId())).isEmpty());
        Assert.assertEquals(998, queue.snapshot(1000, BOUND_A, event -> true).size());
        List<RuneFolioSyncEvent> owners = queue.snapshot(1000, BOUND_B, event -> true);
        Assert.assertEquals(2, owners.size());
        Assert.assertEquals(ownersSnapshot.getId(), owners.get(0).getId());
        Assert.assertEquals(ownersNew.getId(), owners.get(1).getId());

        // Survives reload with the same shape.
        RuneFolioSyncQueue restored = queue(storage, () -> deliverable[0]);
        Assert.assertEquals(1000, restored.size());
        Assert.assertEquals(2, restored.snapshot(1000, BOUND_B, event -> true).size());
    }
}
