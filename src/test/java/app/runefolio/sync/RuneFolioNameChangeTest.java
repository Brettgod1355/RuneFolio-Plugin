package app.runefolio.sync;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioNameChangeTest
{
    private static final String KEY = RuneFolioNameChange.identityKey(123);
    private static final String OTHER_KEY = RuneFolioNameChange.identityKey(456);

    @Test public void identityDigestIsStableAndDoesNotContainTheRawHash()
    {
        Assert.assertNull(RuneFolioNameChange.identityKey(0));
        Assert.assertNull(RuneFolioNameChange.identityKey(-1));
        String key = RuneFolioNameChange.identityKey(123);
        Assert.assertTrue(key.matches("[a-f0-9]{64}"));
        Assert.assertEquals(key, RuneFolioNameChange.identityKey(123));
        Assert.assertNotEquals(key, RuneFolioNameChange.identityKey(456));
    }

    @Test public void queuedEventsRetainTheirIdentityAndOriginalEventId()
    {
        String key = RuneFolioNameChange.identityKey(123);
        RuneFolioSyncEvent event = RuneFolioSyncEvent.collectionLogUnlock("Example One", "Example item")
            .withIdentityKey(key);
        RuneFolioSyncEvent restored = RuneFolioSyncEvent.fromJson(event.toJson());
        Assert.assertEquals(key, restored.getIdentityKey());
        Assert.assertEquals(event.getId(), restored.getId());
        Assert.assertEquals("Example One", restored.getCharacterName());
    }

    @Test public void sameNameOnAnotherAccountIsACharacterChange()
    {
        Assert.assertTrue(RuneFolioNameChange.characterChanged("Example One", "Example One", KEY, OTHER_KEY));
    }

    @Test public void renameOnTheSameAccountIsACharacterChange()
    {
        Assert.assertTrue(RuneFolioNameChange.characterChanged("Example One", "Example Two", KEY, KEY));
    }

    @Test public void unchangedCharacterIsNotAChange()
    {
        Assert.assertFalse(RuneFolioNameChange.characterChanged("Example One", "Example One", KEY, KEY));
        Assert.assertFalse(RuneFolioNameChange.characterChanged("Example One", "Example One", null, null));
    }

    @Test public void nothingIsTrackedWhileEitherNameIsMissing()
    {
        Assert.assertFalse(RuneFolioNameChange.characterChanged(null, "Example One", KEY, OTHER_KEY));
        Assert.assertFalse(RuneFolioNameChange.characterChanged("Example One", null, KEY, OTHER_KEY));
    }

    @Test public void identityAppearingAfterAFreshLoginCountsAsAChange()
    {
        Assert.assertTrue(RuneFolioNameChange.characterChanged("Example One", "Example One", null, KEY));
    }

    @Test public void nameComparisonFoldsCaseAndWhitespaceButNotUnderscores()
    {
        Assert.assertFalse(RuneFolioNameChange.characterChanged("Example One", "example  one", KEY, KEY));
        Assert.assertFalse(RuneFolioNameChange.characterChanged(" Example One ", "EXAMPLE ONE", KEY, KEY));
        Assert.assertTrue(RuneFolioNameChange.characterChanged("Example_One", "example one", KEY, KEY));
        Assert.assertTrue(RuneFolioNameChange.namesMatch("Example One", "example  one"));
        Assert.assertFalse(RuneFolioNameChange.namesMatch("Example_One", "Example One"));
        Assert.assertFalse(RuneFolioNameChange.namesMatch(null, "Example One"));
        Assert.assertEquals("example one", RuneFolioNameChange.normaliseName("  Example \t One "));
    }
}
