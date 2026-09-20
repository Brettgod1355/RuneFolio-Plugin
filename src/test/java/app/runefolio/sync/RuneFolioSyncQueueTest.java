package app.runefolio.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /** One event per config key, so a client only ever rewrites the keys it touched. */
    private static final class MemoryStorage implements RuneFolioSyncQueue.Storage
    {
        private final Map<String, String> entries = new LinkedHashMap<>();
        private String blob;
        private String legacy;
        private int writes;
        private boolean fail;

        @Override
        public Map<String, String> load()
        {
            return new LinkedHashMap<>(entries);
        }

        @Override
        public void put(String eventId, String json)
        {
            if (fail) throw new IllegalStateException("Synthetic storage failure");
            writes++;
            entries.put(eventId, json);
        }

        @Override
        public void remove(String eventId)
        {
            if (fail) throw new IllegalStateException("Synthetic storage failure");
            entries.remove(eventId);
        }

        @Override
        public String getBlob()
        {
            return blob;
        }

        @Override
        public void clearBlob()
        {
            blob = null;
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

    /**
     * Two RuneLite clients sharing one config profile. Each reads the profile once at startup and
     * buffers its writes, then flushes them as a per-key patch — which is what ConfigManager's
     * sendConfig plus ConfigData.patch actually do.
     */
    private static final class Profile
    {
        private final Map<String, String> disk = new LinkedHashMap<>();
        private String blob;
    }

    private static final class ClientStorage implements RuneFolioSyncQueue.Storage
    {
        private final Profile profile;
        private final Map<String, String> view;
        private final Map<String, String> written = new LinkedHashMap<>();
        private final Set<String> deleted = new LinkedHashSet<>();

        private ClientStorage(Profile profile)
        {
            this.profile = profile;
            this.view = new LinkedHashMap<>(profile.disk);
        }

        @Override
        public Map<String, String> load()
        {
            return new LinkedHashMap<>(view);
        }

        @Override
        public void put(String eventId, String json)
        {
            view.put(eventId, json);
            written.put(eventId, json);
            deleted.remove(eventId);
        }

        @Override
        public void remove(String eventId)
        {
            view.remove(eventId);
            written.remove(eventId);
            deleted.add(eventId);
        }

        @Override
        public String getBlob()
        {
            return profile.blob;
        }

        @Override
        public void clearBlob()
        {
            profile.blob = null;
        }

        /** ConfigManager.sendConfig(): only the keys this client changed reach the profile. */
        private void flush()
        {
            profile.disk.putAll(written);
            profile.disk.keySet().removeAll(deleted);
            written.clear();
            deleted.clear();
        }
    }

    private static List<String> idsOnDisk(Profile profile)
    {
        ClientStorage reader = new ClientStorage(profile);
        List<String> ids = new ArrayList<>();
        new RuneFolioSyncQueue(reader, () -> Set.of(BOUND_A, BOUND_B))
            .snapshot(1000, BOUND_A, event -> true)
            .forEach(event -> ids.add(event.getId()));
        return ids;
    }

    @Test
    public void twoClientsOnOneProfileKeepBothQueues()
    {
        Profile profile = new Profile();
        ClientStorage seed = new ClientStorage(profile);
        RuneFolioSyncEvent existing = RuneFolioSyncEvent.collectionLogUnlock("Example", "Existing");
        Assert.assertTrue(new RuneFolioSyncQueue(seed, () -> Set.of(BOUND_A)).enqueue(existing, BOUND_A));
        seed.flush();

        // Both clients start from the same profile and never see each other's writes in memory.
        ClientStorage storageA = new ClientStorage(profile);
        ClientStorage storageB = new ClientStorage(profile);
        RuneFolioSyncQueue clientA = new RuneFolioSyncQueue(storageA, () -> Set.of(BOUND_A));
        RuneFolioSyncQueue clientB = new RuneFolioSyncQueue(storageB, () -> Set.of(BOUND_A));
        Assert.assertEquals(1, clientA.size());
        Assert.assertEquals(1, clientB.size());

        RuneFolioSyncEvent fromA = RuneFolioSyncEvent.collectionLogUnlock("Example", "From A");
        RuneFolioSyncEvent fromB = RuneFolioSyncEvent.collectionLogUnlock("Example", "From B");
        Assert.assertTrue(clientA.enqueue(fromA, BOUND_A));
        Assert.assertTrue(clientB.enqueue(fromB, BOUND_A));

        storageA.flush();
        storageB.flush();

        List<String> ids = idsOnDisk(profile);
        Assert.assertEquals(3, ids.size());
        Assert.assertTrue(ids.contains(existing.getId()));
        Assert.assertTrue("the first client's event must survive the second client's flush", ids.contains(fromA.getId()));
        Assert.assertTrue(ids.contains(fromB.getId()));
    }

    @Test
    public void acknowledgedEventIsNotResurrectedByAStaleClient()
    {
        Profile profile = new Profile();
        ClientStorage seed = new ClientStorage(profile);
        RuneFolioSyncEvent uploaded = RuneFolioSyncEvent.collectionLogUnlock("Example", "Uploaded");
        Assert.assertTrue(new RuneFolioSyncQueue(seed, () -> Set.of(BOUND_A)).enqueue(uploaded, BOUND_A));
        seed.flush();

        ClientStorage storageA = new ClientStorage(profile);
        ClientStorage storageB = new ClientStorage(profile);
        RuneFolioSyncQueue clientA = new RuneFolioSyncQueue(storageA, () -> Set.of(BOUND_A));
        RuneFolioSyncQueue clientB = new RuneFolioSyncQueue(storageB, () -> Set.of(BOUND_A));

        clientA.acknowledge(Collections.singleton(uploaded.getId()));
        storageA.flush();
        Assert.assertTrue(idsOnDisk(profile).isEmpty());

        // B still holds the acknowledged event in memory, but it never rewrites that key.
        RuneFolioSyncEvent fromB = RuneFolioSyncEvent.collectionLogUnlock("Example", "From B");
        Assert.assertTrue(clientB.enqueue(fromB, BOUND_A));
        storageB.flush();

        Assert.assertEquals(Collections.singletonList(fromB.getId()), idsOnDisk(profile));
    }

    @Test
    public void singleValueQueueIsMigratedOntoPerEventKeys()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncEvent first = RuneFolioSyncEvent.collectionLogUnlock("Example", "First");
        RuneFolioSyncEvent second = RuneFolioSyncEvent.collectionLogUnlock("Example", "Second");
        storage.blob = "[{\"b\":\"" + BOUND_A + "\",\"e\":" + first.toJson() + "}"
            + ",{\"b\":\"" + BOUND_A + "\",\"e\":" + second.toJson() + "}]";

        RuneFolioSyncQueue queue = queue(storage);
        Assert.assertNull("the old single value is cleared once every event has its own key", storage.blob);
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(Set.of(first.getId(), second.getId()), storage.entries.keySet());
        // Queue order survives the move, because the sequence is stored beside each event.
        List<RuneFolioSyncEvent> queued = queue.snapshot(10, BOUND_A, event -> true);
        Assert.assertEquals(first.getId(), queued.get(0).getId());
        Assert.assertEquals(second.getId(), queued.get(1).getId());
        Assert.assertEquals(2, queue(storage).size());
    }

    @Test
    public void strandedEntriesArePrunedOnLoad()
    {
        MemoryStorage storage = new MemoryStorage();
        String gone = RuneFolioSyncQueue.binding("a-connection-that-was-replaced");
        RuneFolioSyncQueue queue = queue(storage, () -> Set.of(BOUND_A, gone));
        RuneFolioSyncEvent live = RuneFolioSyncEvent.collectionLogUnlock("Example", "Live");
        RuneFolioSyncEvent stranded = RuneFolioSyncEvent.collectionLogUnlock("Example", "Stranded");
        Assert.assertTrue(queue.enqueue(live, BOUND_A));
        Assert.assertTrue(queue.enqueue(stranded, gone));
        Assert.assertEquals(2, queue.size());

        // The character reconnected, so its old token is no longer in the profile. The entry can
        // never be delivered again, and must not sit in the pending count forever.
        RuneFolioSyncQueue restored = queue(storage, () -> Set.of(BOUND_A));
        Assert.assertEquals(1, restored.size());
        Assert.assertEquals(live.getId(), restored.snapshot(10, BOUND_A, event -> true).get(0).getId());
        Assert.assertEquals(Set.of(live.getId()), storage.entries.keySet());
    }

    @Test
    public void pruningIsSkippedWhileNoConnectionIsHeld()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent event = RuneFolioSyncEvent.collectionLogUnlock("Example", "Kept");
        Assert.assertTrue(queue.enqueue(event, BOUND_A));

        // Having no connection at all is transient; it is not proof that the backlog is dead.
        RuneFolioSyncQueue restored = queue(storage, Set::of);
        Assert.assertEquals(1, restored.size());
        Assert.assertEquals(Set.of(event.getId()), storage.entries.keySet());
    }

    @Test
    public void fullQueueRefusesWithoutRewritingAndDrainsInOrder()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        for (int i = 0; i < 1000; i++)
            Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Example", "Item " + i), BOUND_A));
        Map<String, String> saved = new LinkedHashMap<>(storage.entries);
        int writes = storage.writes;
        Assert.assertFalse(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Example", "Overflow"), BOUND_A));
        queue.acknowledge(Collections.singleton("unknown"));
        Assert.assertEquals(writes, storage.writes);
        Assert.assertEquals(saved, storage.entries);
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
        Assert.assertTrue(storage.entries.isEmpty());
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
        Map<String, String> saved = new LinkedHashMap<>(storage.entries);
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
        Assert.assertEquals(saved, storage.entries);
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
    public void corruptSingleValueQueueIsDiscardedWithoutFailing()
    {
        MemoryStorage storage = new MemoryStorage();
        storage.blob = "{\"characterName\":\"Example\",\"secret\":\"not-an-array\"}";
        Assert.assertEquals(0, queue(storage).size());
        Assert.assertNull(storage.blob);
        Assert.assertTrue(storage.entries.isEmpty());

        RuneFolioSyncEvent event = RuneFolioSyncEvent.collectionLogUnlock("Example", "Abyssal whip");
        storage.blob = "[\"string\",42,null,{\"b\":\"" + BOUND_A + "\",\"e\":" + event.toJson() + "}"
            + ",{\"type\":\"unknown\"},{\"b\":\"\",\"e\":" + event.toJson() + "}," + event.toJson() + "]";
        RuneFolioSyncQueue queue = queue(storage);
        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(event.getId(), queue.snapshot(1, BOUND_A, queued -> true).get(0).getId());
        Assert.assertEquals(Set.of(event.getId()), storage.entries.keySet());
        Assert.assertNull(storage.blob);

        storage.entries.clear();
        storage.blob = "not json at all";
        Assert.assertEquals(0, queue(storage).size());
        Assert.assertNull(storage.blob);
    }

    @Test
    public void unreadableStoredEntryIsDiscardedWithoutTakingTheRestWithIt()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent good = RuneFolioSyncEvent.collectionLogUnlock("Example", "Abyssal whip");
        Assert.assertTrue(queue.enqueue(good, BOUND_A));
        storage.entries.put("corrupt-entry", "not json at all");
        storage.entries.put("blank-binding", "{\"s\":0,\"b\":\"\",\"e\":" + good.toJson() + "}");
        RuneFolioSyncEvent other = RuneFolioSyncEvent.collectionLogUnlock("Example", "Jar of souls");
        storage.entries.put("mismatched-key", "{\"s\":0,\"b\":\"" + BOUND_A + "\",\"e\":" + other.toJson() + "}");

        RuneFolioSyncQueue restored = queue(storage);
        Assert.assertEquals(1, restored.size());
        Assert.assertEquals(good.getId(), restored.snapshot(10, BOUND_A, event -> true).get(0).getId());
        Assert.assertEquals(Set.of(good.getId()), storage.entries.keySet());
    }

    @Test
    public void consecutiveLootDropsNeverReplaceEachOther()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        for (int i = 0; i < 5; i++)
            Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Example", "Drop " + i), BOUND_A));
        Assert.assertEquals(5, queue.size());
        Assert.assertEquals(5, queue(storage).size());
    }

    @Test
    public void eventsOnlyFlushUnderTheConnectionThatRecordedThem()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent underA = RuneFolioSyncEvent.collectionLogUnlock("Example", "Under A");
        RuneFolioSyncEvent underB = RuneFolioSyncEvent.collectionLogUnlock("Example", "Under B");
        Assert.assertTrue(queue.enqueue(underA, BOUND_A));
        Assert.assertTrue(queue.enqueue(underB, BOUND_B));

        List<RuneFolioSyncEvent> forA = queue.snapshot(10, BOUND_A, event -> true);
        Assert.assertEquals(1, forA.size());
        Assert.assertEquals(underA.getId(), forA.get(0).getId());
        List<RuneFolioSyncEvent> forB = queue.snapshot(10, BOUND_B, event -> true);
        Assert.assertEquals(1, forB.size());
        Assert.assertEquals(underB.getId(), forB.get(0).getId());
    }

    @Test
    public void bindingNeverLeavesTheClient()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent event = RuneFolioSyncEvent.collectionLogUnlock("Example", "Abyssal whip");
        Assert.assertTrue(queue.enqueue(event, BOUND_A));

        String saved = storage.entries.get(event.getId());
        JsonObject stored = new JsonParser().parse(saved).getAsJsonObject();
        Assert.assertEquals(BOUND_A, stored.get("b").getAsString());
        Assert.assertFalse(stored.getAsJsonObject("e").has("b"));
        Assert.assertFalse(stored.getAsJsonObject("e").has("binding"));
        Assert.assertFalse(queue.snapshot(1, BOUND_A, queued -> true).get(0).toJson().toString().contains(BOUND_A));
        Assert.assertFalse(saved.contains(TOKEN_A));
        // The binding is not part of the config key either, only the event id.
        Assert.assertFalse(String.join(",", storage.entries.keySet()).contains(BOUND_A));
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
        Assert.assertTrue(storage.entries.isEmpty());
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
        Assert.assertFalse(storage.entries.containsKey(old.getId()));
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

        // A reload now prunes the friend's stranded entries outright instead of keeping them until
        // the queue fills again, so only the owner's two deliverable events come back.
        RuneFolioSyncQueue restored = queue(storage, () -> deliverable[0]);
        Assert.assertEquals(2, restored.size());
        Assert.assertEquals(2, restored.snapshot(1000, BOUND_B, event -> true).size());
        Assert.assertTrue(restored.snapshot(1000, BOUND_A, event -> true).isEmpty());
    }

    @Test
    public void evictionNeverRemovesTheEntryBeingAdded()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage, () -> Set.of(BOUND_B));
        for (int i = 0; i < 1000; i++)
            Assert.assertTrue(queue.enqueue(RuneFolioSyncEvent.collectionLogUnlock("Friend", "Item " + i), BOUND_A));

        // A result finalised after its connection was dropped: its own binding is stale, yet it is
        // the entry being added, so the room comes from the oldest stranded entry instead.
        String stale = RuneFolioSyncQueue.binding("already-disconnected");
        RuneFolioSyncEvent late = RuneFolioSyncEvent.collectionLogUnlock("Friend", "Late");
        Assert.assertTrue(queue.enqueue(late, stale));
        Assert.assertEquals(1000, queue.size());
        Assert.assertEquals(late.getId(), queue.snapshot(10, stale, event -> true).get(0).getId());
        Assert.assertEquals(999, queue.snapshot(1000, BOUND_A, event -> true).size());
        Assert.assertTrue(queue.snapshot(1000, BOUND_A, event -> "Item 0".equals(event.toJson().getAsJsonObject("payload").get("itemName").getAsString())).isEmpty());
    }

    @Test
    public void snapshotsOnlySupersedeSnapshotsOfTheSameConnection()
    {
        MemoryStorage storage = new MemoryStorage();
        RuneFolioSyncQueue queue = queue(storage);
        RuneFolioSyncEvent underA = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "periodic", new JsonObject());
        RuneFolioSyncEvent underB = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "periodic", new JsonObject());
        RuneFolioSyncEvent newerUnderA = RuneFolioSyncEvent.progressSnapshot(RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, "Example", "manual", new JsonObject());
        Assert.assertTrue(queue.enqueue(underA, BOUND_A));
        Assert.assertTrue(queue.enqueue(underB, BOUND_B));
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(underA.getId(), queue.snapshot(10, BOUND_A, event -> true).get(0).getId());

        Assert.assertTrue(queue.enqueue(newerUnderA, BOUND_A));
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(newerUnderA.getId(), queue.snapshot(10, BOUND_A, event -> true).get(0).getId());
        Assert.assertEquals(underB.getId(), queue.snapshot(10, BOUND_B, event -> true).get(0).getId());
        // The superseded entry's key is gone, not merely overwritten in memory.
        Assert.assertFalse(storage.entries.containsKey(underA.getId()));
    }
}
