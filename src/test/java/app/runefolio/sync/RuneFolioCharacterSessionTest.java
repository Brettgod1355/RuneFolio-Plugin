package app.runefolio.sync;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioCharacterSessionTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void resetDropsPartialCaptureButKeepsAlreadyQueuedReceiptIdentity() throws Exception
    {
        RuneFolioPlugin plugin = new RuneFolioPlugin();
        set(plugin, "collectionButtonSyncRequested", true);
        set(plugin, "interfaceScreenshotPending", true);
        set(plugin, "lastCollectionTransmitTick", 123);
        set(plugin, "lastCollectionButtonClickTick", 120);
        Map<Integer, Integer> items = (Map<Integer, Integer>) get(plugin, "collectionButtonItems");
        items.put(4151, 1);
        Set<String> pending = (Set<String>) get(plugin, "pendingCollectionButtonEventIds");
        pending.add("already-queued-event");
        AtomicLong session = (AtomicLong) get(plugin, "characterSession");
        long before = session.get();

        RuneFolioBossRecordTracker boss = (RuneFolioBossRecordTracker) get(plugin, "bossRecordTracker");
        RuneFolioClueRecordTracker clue = (RuneFolioClueRecordTracker) get(plugin, "clueRecordTracker");
        RuneFolioSlayerRecordTracker slayer = (RuneFolioSlayerRecordTracker) get(plugin, "slayerRecordTracker");
        boss.message("Your Vorkath kill count is: 5.", 1, Set.of("Vorkath"));
        clue.message("You have completed 5 easy Treasure Trails.", 1);
        slayer.message("You've completed 5 tasks.", 1);
        plugin.resetTransientCharacterState();
        Assert.assertNull(boss.poll(10));
        Assert.assertNull(clue.poll(10));
        Assert.assertNull(slayer.poll(10));

        Assert.assertEquals(before + 1, session.get());
        Assert.assertEquals(false, get(plugin, "collectionButtonSyncRequested"));
        Assert.assertEquals(false, get(plugin, "interfaceScreenshotPending"));
        Assert.assertEquals(-1, get(plugin, "lastCollectionTransmitTick"));
        Assert.assertEquals(-1, get(plugin, "lastCollectionButtonClickTick"));
        Assert.assertTrue(items.isEmpty());
        Assert.assertTrue(pending.contains("already-queued-event"));
        plugin.resetTransientCharacterState();
        Assert.assertEquals(before + 2, session.get());
    }

    private Object get(RuneFolioPlugin plugin, String name) throws Exception
    {
        Field field = RuneFolioPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(plugin);
    }

    private void set(RuneFolioPlugin plugin, String name, Object value) throws Exception
    {
        Field field = RuneFolioPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}

