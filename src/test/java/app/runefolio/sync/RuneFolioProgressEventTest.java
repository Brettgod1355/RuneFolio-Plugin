package app.runefolio.sync;

import net.runelite.client.util.Text;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioProgressEventTest
{
    @Test
    public void recognizesLiveAchievementDiaryTaskMessages()
    {
        Assert.assertTrue(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Well done! You have completed an easy task in the Ardougne area. Your Achievement Diary has been updated."
        ));
        Assert.assertTrue(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Well done! You have completed a medium task in the Lumbridge & Draynor area. Your Achievement Diary has been updated."
        ));
        Assert.assertTrue(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Well done! You have completed a hard task in the Wilderness area. Your Achievement Diary has been updated."
        ));
        Assert.assertTrue(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Well done! You have completed an elite task in the Western Provinces area. Your Achievement Diary has been updated."
        ));
    }

    @Test
    public void recognizesTheTaggedChatLineAfterTagStripping()
    {
        String raw = "<col=dc143c>Well done! You have completed an elite task in the Western Provinces area."
            + " Your Achievement Diary has been updated.</col>";
        Assert.assertTrue(RuneFolioPlugin.isDiaryTaskCompletionMessage(Text.removeTags(raw)));
    }

    @Test
    public void stillRecognizesTheShortForm()
    {
        Assert.assertTrue(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Well done! You have completed an easy task in the Ardougne area."
        ));
        Assert.assertTrue(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Well done! You have completed a medium task in the Varrock area."
        ));
        Assert.assertTrue(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Well done! You have completed a hard task in the Wilderness area."
        ));
        Assert.assertTrue(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Well done! You have completed an elite task in the Karamja area."
        ));
    }

    @Test
    public void ignoresUnrelatedGameMessages()
    {
        Assert.assertFalse(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Congratulations! You have completed all of the easy tasks in the Ardougne area."
        ));
        Assert.assertFalse(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Congratulations, you've completed an easy combat task: Into the Den of Giants."
        ));
        Assert.assertFalse(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Your Varrock kill count is: 10"
        ));
        Assert.assertFalse(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Well done! You have completed an easy task in the Ardougne area. Something else entirely."
        ));
        Assert.assertFalse(RuneFolioPlugin.isDiaryTaskCompletionMessage(null));
    }
}
