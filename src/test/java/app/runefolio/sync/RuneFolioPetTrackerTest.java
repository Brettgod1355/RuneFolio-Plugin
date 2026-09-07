package app.runefolio.sync;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioPetTrackerTest
{
    private final Set<String> names = Set.of("Rocky", "Vorki");
    @Test public void identifiesNamesInEitherMessageOrderAndEmitsOnce()
    {
        for (boolean reverse : new boolean[] {false, true})
        {
            RuneFolioPetTracker tracker = new RuneFolioPetTracker();
            String pet = "You have a funny feeling like you're being followed.";
            String item = "Untradeable drop: Rocky";
            tracker.message(reverse ? item : pet, 10, names);
            tracker.message(reverse ? pet : item, 11, names);
            Assert.assertNull(tracker.pollCaption(12));
            Assert.assertEquals("Pet received: Rocky", tracker.pollCaption(16));
            Assert.assertNull(tracker.pollCaption(17));
        }
    }
    @Test public void neverGuessesFromUnrelatedItemsAndPreservesDuplicateStatus()
    {
        RuneFolioPetTracker tracker = new RuneFolioPetTracker();
        tracker.message("You have a funny feeling like you would have been followed.", 10, names);
        tracker.message("Untradeable drop: Clue scroll", 11, names);
        Assert.assertEquals("Duplicate pet: Unidentified pet", tracker.pollCaption(15));
    }
    @Test public void sessionResetAndStaleNotificationsCannotNameANewPet()
    {
        RuneFolioPetTracker tracker = new RuneFolioPetTracker();
        tracker.observeName("Vorki", 1, names);
        tracker.message("You feel something weird sneaking into your backpack.", 10, names);
        Assert.assertEquals("Pet received: Unidentified pet (backpack)", tracker.pollCaption(15));
        tracker.message("You have a funny feeling like you're being followed.", 20, names);
        tracker.reset();
        Assert.assertNull(tracker.pollCaption(26));
    }
}
