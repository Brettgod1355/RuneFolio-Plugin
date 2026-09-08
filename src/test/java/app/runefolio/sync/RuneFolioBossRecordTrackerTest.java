package app.runefolio.sync;
import java.util.Set;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioBossRecordTrackerTest
{
    @Test public void joinsResultMessagesInEitherOrder()
    {
        for (boolean reverse : new boolean[] {false, true})
        {
            RuneFolioBossRecordTracker tracker = new RuneFolioBossRecordTracker();
            String count = "Your Vorkath kill count is: 1,234.";
            String time = "Fight duration: 01:23.4. New personal best!";
            tracker.message(reverse ? time : count, 10, Set.of("Vorkath"));
            tracker.message(reverse ? count : time, 11, Set.of("Vorkath"));
            JsonObject result = tracker.poll(13);
            Assert.assertEquals(1234, result.get("count").getAsInt());
            Assert.assertEquals(83400, result.get("durationMillis").getAsLong());
            Assert.assertTrue(result.get("newPersonalBest").getAsBoolean());
            Assert.assertNull(tracker.poll(14));
        }
    }
    @Test public void unknownSourcesAndStaleTimesCannotProduceFalseRecords()
    {
        RuneFolioBossRecordTracker tracker = new RuneFolioBossRecordTracker();
        tracker.message("Your Unknown kill count is: 10.", 1, Set.of("Vorkath"));
        Assert.assertNull(tracker.poll(4));
        tracker.message("Fight duration: 01:23", 1, Set.of("Vorkath"));
        tracker.message("Your Vorkath kill count is: 10.", 10, Set.of("Vorkath"));
        Assert.assertFalse(tracker.poll(12).has("durationMillis"));
        Assert.assertNull(RuneFolioBossRecordTracker.millis("01:99"));
    }

    @Test public void supportsRaidCountAndDoesNotReplayTheSameCount()
    {
        RuneFolioBossRecordTracker tracker = new RuneFolioBossRecordTracker();
        tracker.message("Your completion count for Tombs of Amascut: Expert Mode is: 25.", 10, RuneFolioBossRecordTracker.SOURCES);
        Assert.assertEquals(25, tracker.poll(12).get("count").getAsInt());
        tracker.message("Your completion count for Tombs of Amascut: Expert Mode is: 25.", 13, RuneFolioBossRecordTracker.SOURCES);
        Assert.assertNull(tracker.poll(16));
        tracker.reset();
        tracker.message("Your Vorkath kill count is: 2.", 20, RuneFolioBossRecordTracker.SOURCES);
        Assert.assertFalse(tracker.poll(22).has("durationMillis"));
    }
}
