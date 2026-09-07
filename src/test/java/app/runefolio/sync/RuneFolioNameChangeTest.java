package app.runefolio.sync;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioNameChangeTest
{
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
