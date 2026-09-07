package app.runefolio.sync;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioNameChangeTest
{
    @Test public void requiresLocalStableIdentityAndAGenuineNameChange()
    {
        Assert.assertTrue(RuneFolioNameChange.isCandidate(true, 123, 123, "Example One", "Example Two"));
        Assert.assertFalse(RuneFolioNameChange.isCandidate(false, 123, 123, "Example One", "Example Two"));
        Assert.assertFalse(RuneFolioNameChange.isCandidate(true, 123, 456, "Example One", "Example Two"));
        Assert.assertFalse(RuneFolioNameChange.isCandidate(true, 0, 0, "Example One", "Example Two"));
        Assert.assertFalse(RuneFolioNameChange.isCandidate(true, 123, 123, "Example_One", "example one"));
        Assert.assertFalse(RuneFolioNameChange.isCandidate(true, 123, 123, null, "Example Two"));
    }
}
