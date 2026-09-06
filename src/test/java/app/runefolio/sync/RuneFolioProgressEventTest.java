package app.runefolio.sync;

import org.junit.Assert;
import org.junit.Test;

public class RuneFolioProgressEventTest
{
    @Test
    public void recognizesAchievementDiaryTaskMessages()
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
            "Congratulations, you've completed an easy combat task: Into the Den of Giants."
        ));
        Assert.assertFalse(RuneFolioPlugin.isDiaryTaskCompletionMessage(
            "Your Varrock kill count is: 10"
        ));
        Assert.assertFalse(RuneFolioPlugin.isDiaryTaskCompletionMessage(null));
    }
}
