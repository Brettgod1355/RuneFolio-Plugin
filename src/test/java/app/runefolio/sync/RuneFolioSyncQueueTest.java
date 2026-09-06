package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioSyncQueueTest
{
    private static final class MemoryStorage implements RuneFolioSyncQueue.Storage
    {
        private String value;

        @Override
        public String get()
        {
            return value;
        }

        @Override
        public void set(String value)
        {
            this.value = value;
        }
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
