package app.runefolio.sync;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuneFolioPvpTrackerTest
{
    private JsonArray items() { JsonObject item = new JsonObject(); item.addProperty("itemId", 995); item.addProperty("itemName", "Coins"); item.addProperty("quantity", 10); item.addProperty("unitGeValue", 1); JsonArray items = new JsonArray(); items.add(item); return items; }
    @Test public void preservesKillIdentityTimestampAndUnknownLoot()
    {
        RuneFolioPvpTracker tracker = new RuneFolioPvpTracker();
        RuneFolioPvpTracker.Result result = tracker.death("Example", 10, "2026-01-01T00:00:00Z");
        assertNotNull(result); assertNull(tracker.death("Example", 11, "2026-01-01T00:00:01Z"));
        assertTrue(tracker.poll(18).isEmpty());
        RuneFolioPvpTracker.Result ready = tracker.poll(19).get(0);
        assertEquals(result.id, ready.id); assertEquals(result.occurredAt, ready.occurredAt); assertFalse(ready.payload.has("items")); assertTrue(tracker.poll(20).isEmpty());
    }
    @Test public void lootMatchesOnlyRecentSameOpponentAndCopiesInput()
    {
        RuneFolioPvpTracker tracker = new RuneFolioPvpTracker(); tracker.death("Example", 10, "2026-01-01T00:00:00Z");
        tracker.loot("Other", 11, items()); JsonArray items = items();tracker.loot("example", 12, items);items.remove(0);
        assertEquals(1, tracker.poll(19).get(0).payload.getAsJsonArray("items").size());
        tracker.death("Example", 30, "2026-01-01T00:01:00Z");tracker.loot("Example", 39, items());assertFalse(tracker.poll(39).get(0).payload.has("items"));
    }
    @Test public void handlesEventOrderingButRejectsAmbiguousEarlyLoot()
    {
        RuneFolioPvpTracker tracker = new RuneFolioPvpTracker();tracker.loot("Example", 9, items());
        assertTrue(tracker.death("Example", 10, "2026-01-01T00:00:00Z").payload.has("items"));
        tracker.reset();tracker.loot("Example", 9, items());tracker.loot("Example", 9, items());
        assertFalse(tracker.death("Example", 10, "2026-01-01T00:00:00Z").payload.has("items"));
        tracker.reset();assertTrue(tracker.poll(100).isEmpty());
    }
    @Test public void boundedPendingNamesAndInputs()
    {
        RuneFolioPvpTracker tracker = new RuneFolioPvpTracker();assertNull(tracker.death("<tag>", 1, "2026-01-01T00:00:00Z"));
        for(int i=0;i<32;i++)assertNotNull(tracker.death("Example "+i, 1, "2026-01-01T00:00:00Z"));
        assertNull(tracker.death("Overflow", 1, "2026-01-01T00:00:00Z"));assertEquals(32,tracker.poll(10).size());
    }
    @Test public void rewardCategoriesDoNotTreatNpcNamesOrUnrelatedEventsAsScreens()
    {
        for(String tier:new String[]{"Beginner","Easy","Medium","Hard","Elite","Master"})assertEquals("clue_reward",RuneFolioRewardScreenshots.category("Clue Scroll ("+tier+")","event"));
        assertEquals("loot_key",RuneFolioRewardScreenshots.category("Loot Chest","event"));
        for(String name:new String[]{"Barrows","Lunar Chest","Chambers of Xeric","Theatre of Blood","Tombs of Amascut","The Gauntlet","The Corrupted Gauntlet","Fortis Colosseum","Doom of Mokhaiotl","Chest (Crystal key)"})assertEquals("raid_chest_reward",RuneFolioRewardScreenshots.category(name,"event"));
        assertNull(RuneFolioRewardScreenshots.category("Chest Mimic","npc"));assertNull(RuneFolioRewardScreenshots.category("Kingdom of Miscellania","event"));assertNull(RuneFolioRewardScreenshots.category("Clue Scroll (Fake)","event"));assertNull(RuneFolioRewardScreenshots.category(null,"event"));
    }
}
