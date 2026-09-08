package app.runefolio.sync;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioSlayerRecordTrackerTest
{
    @Test public void joinsTaskAndPointsInEitherOrder()
    {
        for (boolean reverse : new boolean[] {false, true})
        {
            RuneFolioSlayerRecordTracker tracker = new RuneFolioSlayerRecordTracker();
            String task = "You have completed your task! You killed 123 gargoyles.";
            String points = "You've completed 50 tasks and received 150 points, giving you a total of 1,000.";
            tracker.message(reverse ? points : task, 10);
            tracker.message(reverse ? task : points, 11);
            JsonObject record = tracker.poll(14);
            Assert.assertEquals("gargoyles", record.get("monster").getAsString());
            Assert.assertEquals(123, record.get("kills").getAsInt());
            Assert.assertEquals(150, record.get("pointsEarned").getAsInt());
            Assert.assertEquals(1000, record.get("totalPoints").getAsInt());
            Assert.assertNull(tracker.poll(15));
        }
    }
    @Test public void unknownPointsRemainUnknownAndResetDropsPartialData()
    {
        RuneFolioSlayerRecordTracker tracker = new RuneFolioSlayerRecordTracker();
        tracker.message("You have completed your task! You killed 10 bats.", 10);
        Assert.assertFalse(tracker.poll(13).has("pointsEarned"));
        tracker.message("You have completed your task! You killed 10 bats.", 20);
        tracker.reset();
        Assert.assertNull(tracker.poll(24));
    }

    @Test public void pointOnlyAndMaximumPointMessagesRemainCompletions()
    {
        RuneFolioSlayerRecordTracker tracker = new RuneFolioSlayerRecordTracker();
        tracker.message("You've completed 50 Wilderness tasks and received 25 points, giving you a total of 500.", 10);
        JsonObject record = tracker.poll(13);
        Assert.assertEquals("wilderness", record.get("streakType").getAsString());
        Assert.assertFalse(record.has("monster"));
        tracker.message("You've completed at least 100 tasks. You already have the maximum number of Slayer points.", 20);
        Assert.assertFalse(tracker.poll(23).has("pointsEarned"));
    }
}
