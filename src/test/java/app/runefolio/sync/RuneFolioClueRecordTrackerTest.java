package app.runefolio.sync;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioClueRecordTrackerTest
{
    @Test public void joinsTierCountAndRewardsOnlyOnce()
    {
        RuneFolioClueRecordTracker tracker = new RuneFolioClueRecordTracker();
        tracker.message("You have completed 1,234 hard Treasure Trails.", 10);
        JsonObject record = tracker.rewards(new JsonArray(), 11);
        Assert.assertEquals("hard", record.get("tier").getAsString());
        Assert.assertEquals(1234, record.get("count").getAsInt());
        tracker.message("You have completed 1,234 hard Treasure Trails.", 12);
        Assert.assertNull(tracker.rewards(new JsonArray(), 12));
    }
    @Test public void rejectsStaleUnknownAndPreviousCharacterData()
    {
        RuneFolioClueRecordTracker tracker = new RuneFolioClueRecordTracker();
        tracker.message("You have completed 5 mythical Treasure Trails.", 1);
        Assert.assertNull(tracker.rewards(new JsonArray(), 1));
        tracker.message("You have completed 5 easy Treasure Trails.", 1);
        Assert.assertNull(tracker.rewards(new JsonArray(), 6));
        tracker.message("You have completed 5 easy Treasure Trails.", 10);
        tracker.reset();
        Assert.assertNull(tracker.rewards(new JsonArray(), 11));
    }
}
