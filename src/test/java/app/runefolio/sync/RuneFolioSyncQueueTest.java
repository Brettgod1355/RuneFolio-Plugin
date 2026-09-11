package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioSyncQueueTest
{
    @Test public void oversizedReplacementPreservesPreviouslyQueuedSnapshot()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        RuneFolioSyncEvent first = RuneFolioSyncEvent.historyEvent("bank.snapshot", "Example", new JsonObject());
        Assert.assertTrue(queue.enqueue(first));
        JsonObject huge = new JsonObject(); huge.addProperty("value", "x".repeat(600_000));
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.historyEvent("bank.snapshot", "Example", huge)));
        Assert.assertEquals(first.getId(), new RuneFolioSyncQueue(storage).snapshot(50, event -> true).get(0).getId());
    }
    @Test public void uploadBatchesStayBelowOneMegabyte()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        JsonObject payload = new JsonObject(); payload.addProperty("value", "x".repeat(300_000));
        for (int i=0;i<5;i++) Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.historyEvent("bank.snapshot", "Example"+i, payload)));
        Assert.assertEquals(3, queue.snapshot(50, event -> true).size());
        Assert.assertEquals(5, queue.size());
    }
    private static final class MemoryStorage implements RuneFolioSyncQueue.Storage
    {
        private String value;
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
    }

    @Test
    public void fullQueueRefusesWithoutRewritingAndDrainsInOrder()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        for (int i = 0; i < 1000; i++)
            Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Example", "Item " + i)));
        String saved = storage.value;
        int writes = storage.writes;
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Example", "Overflow")));
        queue.acknowledge(Collections.singleton("unknown"));
        Assert.assertEquals(writes, storage.writes);
        Assert.assertEquals(saved, storage.value);
        RuneFolioSyncQueue restored = new RuneFolioSyncQueue(storage);
        for (int i = 0; i < 1000; i += 50)
        {
            java.util.List<RuneFolioSyncEvent> batch = restored.snapshot(50, event -> true);
            Assert.assertEquals(50, batch.size());
            Assert.assertEquals("Item " + i, batch.get(0).toJson().getAsJsonObject("payload").get("itemName").getAsString());
            java.util.List<String> ids = new java.util.ArrayList<>();
            batch.forEach(event -> ids.add(event.getId()));
            restored.acknowledge(ids);
        }
        Assert.assertEquals(0, new RuneFolioSyncQueue(storage).size());
        Assert.assertEquals("[]", storage.value);
    }

    @Test
    public void utf8BudgetsSurviveAcknowledgementAndReload()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        JsonObject payload = new JsonObject();
        payload.addProperty("value", "界".repeat(100_000));
        for (int i = 0; i < 13; i++)
            Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload)));
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload)));
        RuneFolioSyncQueue restored = new RuneFolioSyncQueue(storage);
        Assert.assertEquals(3, restored.snapshot(50, event -> true).size());
        restored.acknowledge(Collections.singleton(restored.snapshot(1, event -> true).get(0).getId()));
        Assert.assertTrue(restored.enqueue(RuneFolioSyncEvent.historyEvent("slayer.completion", "Example", payload)));
        Assert.assertEquals(13, new RuneFolioSyncQueue(storage).size());
    }

    @Test
    public void cachedEventsAreIsolatedFromCallerMutationAndKeepIdentityFilters()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        String identity = "a".repeat(64);
        RuneFolioSyncEvent original = RuneFolioSyncEvent.collectionLogUnlock("Example", "Item").withIdentityKey(identity);
        Assert.assertTrue(queue.enqueue(original));
        original.withIdentityKey("b".repeat(64));
        RuneFolioSyncEvent upload = queue.snapshot(1, event -> identity.equals(event.getIdentityKey())).get(0);
        upload.withIdentityKey("c".repeat(64));
        Assert.assertEquals(identity, queue.snapshot(1, event -> true).get(0).getIdentityKey());
        Assert.assertEquals(identity, new RuneFolioSyncQueue(storage).snapshot(1, event -> true).get(0).getIdentityKey());
        Assert.assertTrue(queue.snapshot(0, event -> true).isEmpty());
    }

    @Test
    public void oldInFlightAcknowledgementCannotRemoveReplacement()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        RuneFolioSyncEvent first = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "periodic", new JsonObject());
        RuneFolioSyncEvent next = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "manual", new JsonObject());
        Assert.assertTrue(queue.enqueue(first));
        Assert.assertTrue(queue.enqueue(next));
        queue.acknowledge(Collections.singleton(first.getId()));
        Assert.assertEquals(next.getId(), new RuneFolioSyncQueue(storage).snapshot(1, event -> true).get(0).getId());
    }

    @Test
    public void failedPersistenceDoesNotPublishReplacementOrAcknowledgement()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        RuneFolioSyncEvent first = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "periodic", new JsonObject());
        Assert.assertTrue(queue.enqueue(first));
        String saved = storage.value;
        storage.fail = true;
        try
        {
            queue.enqueue(RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "manual", new JsonObject()));
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
        Assert.assertEquals(first.getId(), queue.snapshot(1, event -> true).get(0).getId());
        storage.fail = false;
        queue.acknowledge(Collections.singleton(first.getId()));
        Assert.assertEquals(0, new RuneFolioSyncQueue(storage).size());
    }

    @Test
    public void partialAcknowledgementRemovesOnlyConfirmedEvents()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        RuneFolioSyncEvent first = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Abyssal whip");
        RuneFolioSyncEvent second = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Jar of souls");
        RuneFolioSyncEvent third = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Pet kraken");

        Assert.assertTrue(queue.enqueue(first));
        Assert.assertTrue(queue.enqueue(second));
        Assert.assertTrue(queue.enqueue(third));
        queue.acknowledge(Collections.singleton(first.getId()));

        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(
            Arrays.asList(second.getId(), third.getId()),
            Arrays.asList(
                queue.snapshot(10, event -> true).get(0).getId(),
                queue.snapshot(10, event -> true).get(1).getId()
            )
        );

        RuneFolioSyncQueue restored = new RuneFolioSyncQueue(storage);
        Assert.assertEquals(2, restored.size());
    }

    @Test
    public void unacknowledgedEventsRemainQueued()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        RuneFolioSyncEvent event = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Dragon pickaxe");

        Assert.assertTrue(queue.enqueue(event));
        queue.acknowledge(Collections.emptyList());

        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(event.getId(), queue.snapshot(1, queued -> true).get(0).getId());
    }

    @Test
    public void consecutiveLootDropsNeverReplaceEachOther()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = new RuneFolioSyncQueue(storage);
        JsonArray items = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("itemId", 995);
        item.addProperty("itemName", "Coins");
        item.addProperty("quantity", 10);
        items.add(item);

        Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.lootDrop(
            "Example Main", "Goblin", "npc", 2, 1, items, 10, 10
        )));
        Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.lootDrop(
            "Example Main", "Goblin", "npc", 2, 1, items, 10, 10
        )));

        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(2, new RuneFolioSyncQueue(storage).size());
    }
}
