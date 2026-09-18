package app.runefolio.sync;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import org.junit.Test;

import static org.junit.Assert.*;

/** Exercises the real plugin capture path and DrawManager, without network or a game session. */
public class RuneFolioScreenshotCaptureTest
{
    @org.junit.Rule public org.junit.rules.TemporaryFolder temporary = new org.junit.rules.TemporaryFolder();

    @Test
    public void capturePersistsBeforeAnyNetworkUploadAndReleasesRawFrameSlot() throws Exception
    {
        java.nio.file.Path root = temporary.newFolder().toPath();
        RuneFolioScreenshotSpool spool = new RuneFolioScreenshotSpool(
            net.runelite.client.util.Filepath.Unchecked.getRooted(root), new com.google.gson.Gson());
        try (Fixture fixture = new Fixture())
        {
            fixture.set("screenshotSpool", spool);
            fixture.capture();
            fixture.draw.processDrawComplete(() -> new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));
            fixture.thread.drain();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (fixture.queue.outstandingCount() > 0 && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals(0, fixture.queue.outstandingCount());
            assertEquals(1, spool.stats().saved);
            fixture.assertRestored();
            spool.drainOnce(() -> List.of("synthetic-test-placeholder"), (token, entry, jpeg) -> {
                assertEquals("Example", entry.characterName);
                assertEquals("level_up", entry.category);
                assertEquals("Example level 2", entry.caption);
                assertTrue(jpeg.length > 4);
                assertEquals(255, jpeg[0] & 255);
                assertEquals(216, jpeg[1] & 255);
            });
            assertEquals(0, spool.stats().saved);
        }
    }

    @Test
    public void rewardCapturePreservesLinkedIdWithoutNativeScreenshotPlugin() throws Exception
    {
        for (String category : new String[]{"clue_reward", "raid_chest_reward", "pvp_kill", "loot_key"})
        {
            RuneFolioScreenshotSpool spool = new RuneFolioScreenshotSpool(
                net.runelite.client.util.Filepath.Unchecked.getRooted(temporary.newFolder().toPath()), new com.google.gson.Gson());
            java.util.UUID id = java.util.UUID.randomUUID();
            try (Fixture fixture = new Fixture())
            {
                fixture.set("screenshotSpool", spool);
                Method method = RuneFolioPlugin.class.getDeclaredMethod("requestScreenshot", String.class, String.class, java.util.UUID.class);
                method.setAccessible(true);
                method.invoke(fixture.plugin, category, "Observed reward", id);
                fixture.draw.processDrawComplete(() -> new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));
                fixture.thread.drain();
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                while (fixture.queue.outstandingCount() > 0 && System.nanoTime() < deadline) Thread.sleep(5);
                assertEquals(1, spool.stats().saved);
                spool.drainOnce(() -> List.of("synthetic-test-placeholder"), (token, entry, jpeg) -> {
                    assertEquals(id.toString(), entry.eventId);
                    assertEquals(category, entry.category);
                });
                assertEquals(0, spool.stats().saved);
                fixture.assertRestored();
            }
        }
    }

    @Test
    public void overloadIsRejectedBeforeRegisteringMoreFrameCallbacks() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            for (int i = 0; i < 100; i++) fixture.capture();
            assertEquals(3, fixture.draw.requests);
            assertEquals(3, fixture.queue.outstandingCount());
            fixture.queue.close();
            fixture.draw.processDrawComplete(() -> new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            fixture.thread.drain();
            fixture.assertRestored();
            assertEquals(0, fixture.queue.outstandingCount());
        }
    }

    @Test
    public void missingFrameRestoresPrivacyWidgetsAndFreesReservation() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.capture();
            assertTrue(fixture.hidden.values().stream().allMatch(Boolean::booleanValue));
            fixture.draw.processDrawComplete(() -> null);
            fixture.thread.drain();
            fixture.assertRestored();
            assertEquals(0, fixture.queue.outstandingCount());
            fixture.capture();
            assertEquals(1, fixture.queue.outstandingCount());
            fixture.draw.processDrawComplete(() -> null);
            fixture.thread.drain();
        }
    }

    @Test
    public void changedCharacterDiscardsDelayedFrameAndReleasesSlot() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.capture();
            fixture.plugin.resetTransientCharacterState();
            fixture.draw.processDrawComplete(() -> new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            fixture.thread.drain();
            fixture.assertRestored();
            assertEquals(0, fixture.queue.outstandingCount());
        }
    }

    @Test
    public void frameRegistrationFailureRestoresWidgetsAndReservation() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.draw.reject = true;
            fixture.capture();
            fixture.thread.drain();
            fixture.assertRestored();
            assertEquals(0, fixture.queue.outstandingCount());
        }
    }

    @Test
    public void deliveredFrameStaysReservedUntilValidationAndCannotUploadAfterClose() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.capture();
            fixture.draw.processDrawComplete(() -> new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            assertEquals(1, fixture.queue.outstandingCount());
            // First deferred task is the missing-frame check, which must not
            // release a frame already awaiting client-thread validation.
            fixture.thread.pending.remove(0).run();
            assertEquals(1, fixture.queue.outstandingCount());
            fixture.queue.close();
            fixture.thread.drain();
            fixture.assertRestored();
            assertEquals(0, fixture.queue.outstandingCount());
        }
    }

    private static final class QueuedClientThread extends ClientThread
    {
        final List<Runnable> pending = new ArrayList<>();
        @Override public void invokeLater(Runnable task) { pending.add(task); }
        void drain() { while (!pending.isEmpty()) pending.remove(0).run(); }
    }

    private static final class CountingDrawManager extends DrawManager
    {
        int requests;
        boolean reject;
        @Override public void requestNextFrameListener(Consumer<Image> listener)
        {
            if (reject) throw new IllegalStateException("Synthetic frame registration failure");
            requests++;
            super.requestNextFrameListener(listener);
        }
    }

    private static final class Fixture implements AutoCloseable
    {
        final RuneFolioPlugin plugin = new RuneFolioPlugin();
        final QueuedClientThread thread = new QueuedClientThread();
        final CountingDrawManager draw = new CountingDrawManager();
        final Map<Integer, Boolean> hidden = new HashMap<>();
        final RuneFolioScreenshotQueue queue;

        Fixture() throws Exception
        {
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "Example";
                    throw new AssertionError("Unexpected player call: " + method.getName());
                });
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getGameState": return GameState.LOGGED_IN;
                        case "getWorldType": return EnumSet.noneOf(WorldType.class);
                        case "getLocalPlayer": return player;
                        case "getWidget": return widget((Integer) args[0]);
                        default: throw new AssertionError("Unexpected client call: " + method.getName());
                    }
                });
            set("client", client);
            set("clientThread", thread);
            set("drawManager", draw);
            set("config", new RuneFolioConfig() {
                @Override public boolean uploadScreenshots() { return true; }
                @Override public boolean hideChatInScreenshots() { return true; }
            });
            set("connectionToken", "synthetic-test-placeholder");
            set("connectedCharacterName", "Example");
            set("running", true);
            queue = new RuneFolioScreenshotQueue();
            set("screenshotQueue", queue);
        }

        private Widget widget(int id)
        {
            hidden.putIfAbsent(id, false);
            return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isHidden")) return hidden.get(id);
                    if (method.getName().equals("setHidden")) { hidden.put(id, (Boolean) args[0]); return null; }
                    throw new AssertionError("Unexpected widget call: " + method.getName());
                });
        }

        void capture() throws Exception
        {
            Method method = RuneFolioPlugin.class.getDeclaredMethod("requestScreenshot", String.class, String.class);
            method.setAccessible(true);
            method.invoke(plugin, "level_up", "Example level 2");
        }

        void assertRestored()
        {
            assertEquals(2, hidden.size());
            assertTrue(hidden.values().stream().noneMatch(Boolean::booleanValue));
        }

        private void set(String name, Object value) throws Exception
        {
            Field field = RuneFolioPlugin.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(plugin, value);
        }

        @Override public void close() { queue.close(); }
    }
}
