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
}
