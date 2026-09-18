package app.runefolio.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import sun.reflect.ReflectionFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Drives startUp()/shutDown() on one instance the way RuneLite's PluginManager does when a plugin is toggled. */
public class RuneFolioPluginLifecycleTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void toggledOffAndOnAgainRestartsWithFreshResources() throws Exception
    {
        Harness harness = new Harness(true);
        LifecyclePlugin plugin = harness.plugin;

        harness.onEdt(plugin::startUp);
        assertEquals(1, plugin.navigationAdded);
        assertTrue((Boolean) harness.get("running"));
        harness.assertScreenshotQueueUsable();
        ScheduledExecutorService firstSync = (ScheduledExecutorService) harness.get("syncExecutor");

        harness.onEdt(plugin::shutDown);
        assertEquals(1, plugin.navigationRemoved);
        assertFalse((Boolean) harness.get("running"));
        assertTrue(firstSync.isShutdown());
        assertNull(harness.get("syncExecutor"));
        assertNull(harness.get("connectionExecutor"));
        assertNull(harness.get("screenshotUploadExecutor"));
        assertNull(harness.get("screenshotQueue"));
        assertNull(harness.get("screenshotSpool"));

        harness.onEdt(plugin::startUp);
        assertEquals(2, plugin.navigationAdded);
        assertEquals(1, plugin.navigationRemoved);
        assertTrue((Boolean) harness.get("running"));
        harness.assertScreenshotQueueUsable();
        ScheduledExecutorService secondSync = (ScheduledExecutorService) harness.get("syncExecutor");
        assertNotNull(secondSync);
        assertFalse(secondSync.isShutdown());

        harness.onEdt(plugin::shutDown);
        assertEquals(2, plugin.navigationRemoved);
        assertTrue(secondSync.isShutdown());
    }

    @Test
    public void failedStartUpLeavesNothingBehindAndTheNextAttemptSucceeds() throws Exception
    {
        Harness harness = new Harness(true);
        LifecyclePlugin plugin = harness.plugin;
        plugin.failSpool = true;

        try
        {
            harness.onEdt(plugin::startUp);
            fail("expected the synthetic plugin directory failure");
        }
        catch (IOException expected)
        {
            assertEquals("Synthetic plugin directory failure", expected.getMessage());
        }

        harness.onEdt(plugin::shutDown);
        assertEquals(0, plugin.navigationAdded);
        assertEquals(0, plugin.navigationRemoved);
        assertFalse((Boolean) harness.get("running"));
        assertNull(harness.get("syncExecutor"));
        assertNull(harness.get("connectionExecutor"));
        assertNull(harness.get("screenshotUploadExecutor"));
        assertNull(harness.get("screenshotQueue"));
        assertNull(harness.get("navigationButton"));

        plugin.failSpool = false;
        harness.onEdt(plugin::startUp);
        assertEquals(1, plugin.navigationAdded);
        harness.assertScreenshotQueueUsable();
        harness.onEdt(plugin::shutDown);
        assertEquals(1, plugin.navigationRemoved);
    }

    @Test
    public void shutDownResetsPerRunStateAndSkipsQueuedClientThreadWork() throws Exception
    {
        Harness harness = new Harness(false);
        LifecyclePlugin plugin = harness.plugin;

        harness.onEdt(plugin::startUp);
        harness.set("worldHopInProgress", true);
        harness.set("lastKnownPlayerName", "Example");
        harness.set("activeIdentityKey", RuneFolioNameChange.identityKey(123));
        harness.set("nextManifestRefreshMillis", Long.MAX_VALUE);
        harness.set("ticksSinceLocalSnapshot", 7);
        assertFalse(harness.clientThread.pending.isEmpty());

        harness.onEdt(plugin::shutDown);
        assertFalse((Boolean) harness.get("worldHopInProgress"));
        assertNull(harness.get("lastKnownPlayerName"));
        assertNull(harness.get("activeIdentityKey"));
        assertNull(harness.get("accountConnectionToken"));
        assertEquals(0L, harness.get("nextManifestRefreshMillis"));
        assertEquals(0, harness.get("ticksSinceLocalSnapshot"));

        int clientReadsBefore = harness.clientReads;
        harness.clientThread.drain();
        assertEquals(clientReadsBefore, harness.clientReads);
    }

    @Test
    public void disconnectClearsTheLocalConnectionEvenWhenRuneFolioIsUnreachable() throws Exception
    {
        Harness harness = new Harness(true);
        harness.set("okHttpClient", new OkHttpClient.Builder()
            .addInterceptor(chain -> { throw new IOException("Synthetic outage"); })
            .build());

        harness.onEdt(harness.plugin::startUp);
        assertEquals("saved-token", harness.get("accountConnectionToken"));

        harness.onEdt(() -> harness.invoke("disconnectRuneFolioAccount"));
        harness.awaitConnectionExecutor();
        harness.onEdt(() -> { });

        assertNull(harness.get("accountConnectionToken"));
        assertNull(harness.configManager.getConfiguration("runefolio", "accountConnectionToken"));
        String status = harness.panelStatus();
        assertTrue(status, status.startsWith("Disconnected on this computer, but RuneFolio could not be reached"));
        assertTrue(status, status.contains("revoke this device"));

        harness.onEdt(harness.plugin::shutDown);
    }

    @Test
    public void disconnectReportsSuccessWhenRuneFolioAcknowledges() throws Exception
    {
        Harness harness = new Harness(true);
        harness.set("okHttpClient", new OkHttpClient.Builder()
            .addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), "{}"))
                .build())
            .build());

        harness.onEdt(harness.plugin::startUp);
        harness.onEdt(() -> harness.invoke("disconnectRuneFolioAccount"));
        harness.awaitConnectionExecutor();
        harness.onEdt(() -> { });

        assertNull(harness.get("accountConnectionToken"));
        assertNull(harness.configManager.getConfiguration("runefolio", "accountConnectionToken"));
        assertEquals("RuneFolio account disconnected. Temporary character codes are still available.", harness.panelStatus());

        harness.onEdt(harness.plugin::shutDown);
    }

    @Test
    public void flushSendsOnlyEventsRecordedUnderTheActiveConnection() throws Exception
    {
        Harness harness = new Harness(true);
        List<String> sentEventIds = new ArrayList<>();
        harness.set("okHttpClient", new OkHttpClient.Builder()
            .addInterceptor(chain ->
            {
                okio.Buffer body = new okio.Buffer();
                chain.request().body().writeTo(body);
                JsonObject request = new JsonParser().parse(body.readUtf8()).getAsJsonObject();
                JsonArray accepted = new JsonArray();
                for (JsonElement event : request.getAsJsonArray("events"))
                {
                    String id = event.getAsJsonObject().get("id").getAsString();
                    sentEventIds.add(id);
                    accepted.add(id);
                }
                JsonObject response = new JsonObject();
                response.add("acceptedEventIds", accepted);
                return new Response.Builder()
                    .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"), response.toString()))
                    .build();
            })
            .build());

        harness.onEdt(harness.plugin::startUp);
        RuneFolioSyncQueue queue = (RuneFolioSyncQueue) harness.get("syncQueue");
        RuneFolioSyncEvent owners = RuneFolioSyncEvent.collectionLogUnlock("Owner", "Jar of souls");
        RuneFolioSyncEvent friends = RuneFolioSyncEvent.collectionLogUnlock("Friend", "Abyssal whip");
        assertTrue(queue.enqueue(owners, RuneFolioSyncQueue.binding("saved-token")));
        assertTrue(queue.enqueue(friends, RuneFolioSyncQueue.binding("a-friends-temporary-code-token")));
        assertEquals(2, queue.size());

        harness.invoke("flushQueue");

        assertEquals(List.of(owners.getId()), sentEventIds);
        assertEquals(1, queue.size());
        assertTrue(queue.snapshot(10, RuneFolioSyncQueue.binding("saved-token"), event -> true).isEmpty());
        assertEquals(friends.getId(), queue.snapshot(10, RuneFolioSyncQueue.binding("a-friends-temporary-code-token"), event -> true).get(0).getId());

        harness.onEdt(harness.plugin::shutDown);
    }

    @Test
    public void deliverableBindingsCoverEveryConnectionStillHeld() throws Exception
    {
        Harness harness = new Harness(true);
        harness.configManager.setConfiguration("runefolio", "connectionToken.RXhhbXBsZQ", "character-token");
        harness.configManager.setConfiguration("runefolio", "identityToken." + "a".repeat(64), "identity-token");
        harness.configManager.setConfiguration("runefolio", "connectionToken", "legacy-token");
        harness.onEdt(harness.plugin::startUp);

        @SuppressWarnings("unchecked")
        Set<String> bindings = (Set<String>) harness.invokeReturning("deliverableSyncBindings");
        assertEquals(Set.of(
            RuneFolioSyncQueue.binding("saved-token"),
            RuneFolioSyncQueue.binding("character-token"),
            RuneFolioSyncQueue.binding("identity-token"),
            RuneFolioSyncQueue.binding("legacy-token")), bindings);
        assertFalse(bindings.contains("saved-token"));

        harness.onEdt(harness.plugin::shutDown);
    }

    static final class LifecyclePlugin extends RuneFolioPlugin
    {
        int navigationAdded;
        int navigationRemoved;
        boolean failSpool;
        private final File spoolRoot;

        LifecyclePlugin(File spoolRoot)
        {
            this.spoolRoot = spoolRoot;
        }

        @Override
        void addNavigation(NavigationButton button)
        {
            navigationAdded++;
        }

        @Override
        void removeNavigation(NavigationButton button)
        {
            navigationRemoved++;
        }

        @Override
        RuneFolioScreenshotSpool openScreenshotSpool() throws IOException
        {
            if (failSpool)
            {
                throw new IOException("Synthetic plugin directory failure");
            }
            return new RuneFolioScreenshotSpool(Filepath.Unchecked.getRooted(spoolRoot.toPath()), new Gson());
        }
    }

    private static final class QueuedClientThread extends ClientThread
    {
        final List<Runnable> pending = new ArrayList<>();

        @Override
        public void invokeLater(Runnable task)
        {
            pending.add(task);
        }

        void drain()
        {
            while (!pending.isEmpty())
            {
                pending.remove(0).run();
            }
        }
    }

    interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    private final class Harness
    {
        final LifecyclePlugin plugin;
        final QueuedClientThread clientThread = new QueuedClientThread();
        final ConfigManager configManager;
        /** Game-state and local-player reads: the ones the plugin's own deferred work performs. */
        int clientReads;

        /** Mirrors a user whose RuneFolio account token is saved, so shutDown() submits its final flush. */
        Harness(boolean accountConnected) throws Exception
        {
            plugin = new LifecyclePlugin(temporary.newFolder());
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getGameState": clientReads++; return GameState.LOGIN_SCREEN;
                        case "getWorldType": return EnumSet.noneOf(WorldType.class);
                        case "getLocalPlayer": clientReads++; return null;
                        case "getWidget": return null;
                        case "getAccountHash": return -1L;
                        case "getTickCount": return 0;
                        default: throw new AssertionError("Unexpected client call: " + method.getName());
                    }
                });
            EventBus eventBus = new EventBus();
            RuneFolioConfig config = new RuneFolioConfig() { };
            configManager = configManager(temporary.newFile("runelite.properties"));
            if (accountConnected)
            {
                configManager.setConfiguration("runefolio", "accountConnectionToken", "saved-token");
            }

            set("client", client);
            set("clientThread", clientThread);
            set("configManager", configManager);
            set("config", config);
            set("collectionLogButton", new RuneFolioCollectionLogButton(client, clientThread, eventBus, config));
            set("diaryTaskTracker", new RuneFolioDiaryTaskTracker(client, eventBus));
            set("bankCollector", new RuneFolioBankCollector(client, null, config, eventBus));
            set("unlockCollector", new RuneFolioUnlockCollector(client, config, eventBus, new Gson()));
            set("gson", new Gson());
        }

        void onEdt(ThrowingRunnable action) throws Exception
        {
            Exception[] failure = new Exception[1];
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    action.run();
                }
                catch (Exception exception)
                {
                    failure[0] = exception;
                }
            });
            if (failure[0] != null)
            {
                throw failure[0];
            }
        }

        void assertScreenshotQueueUsable() throws Exception
        {
            RuneFolioScreenshotQueue queue = (RuneFolioScreenshotQueue) get("screenshotQueue");
            assertNotNull(queue);
            RuneFolioScreenshotQueue.Reservation reservation = queue.tryReserve();
            assertNotNull(reservation);
            reservation.cancel();
            assertEquals(0, queue.outstandingCount());
        }

        Object get(String name) throws Exception
        {
            Field field = RuneFolioPlugin.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(plugin);
        }

        void invoke(String method) throws Exception
        {
            invokeReturning(method);
        }

        Object invokeReturning(String method) throws Exception
        {
            Method target = RuneFolioPlugin.class.getDeclaredMethod(method);
            target.setAccessible(true);
            return target.invoke(plugin);
        }

        /** The connection executor is single-threaded, so a trailing no-op completes after everything queued before it. */
        void awaitConnectionExecutor() throws Exception
        {
            ((ScheduledExecutorService) get("connectionExecutor")).submit(() -> { }).get(10, TimeUnit.SECONDS);
        }

        String panelStatus() throws Exception
        {
            Field field = RuneFolioPanel.class.getDeclaredField("statusValue");
            field.setAccessible(true);
            return ((JTextArea) field.get(get("panel"))).getText();
        }

        void set(String name, Object value) throws Exception
        {
            Field field = RuneFolioPlugin.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(plugin, value);
        }
    }

    /**
     * RuneLite's ConfigManager has no test-friendly constructor, so this allocates one and wires
     * only the state its get/set/unset methods read: a file-backed ConfigData, the config proxy
     * cache and an event bus.
     */
    private static ConfigManager configManager(File propertiesFile) throws Exception
    {
        ReflectionFactory factory = ReflectionFactory.getReflectionFactory();
        Constructor<?> allocator = factory.newConstructorForSerialization(
            ConfigManager.class, Object.class.getDeclaredConstructor());
        ConfigManager manager = (ConfigManager) allocator.newInstance();
        Constructor<?> configData = Class.forName("net.runelite.client.config.ConfigData")
            .getDeclaredConstructor(File.class);
        configData.setAccessible(true);
        Constructor<?> handler = Class.forName("net.runelite.client.config.ConfigInvocationHandler")
            .getDeclaredConstructor(ConfigManager.class);
        handler.setAccessible(true);
        setField(manager, "configProfile", configData.newInstance(propertiesFile));
        setField(manager, "handler", handler.newInstance(manager));
        setField(manager, "eventBus", new EventBus());
        return manager;
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
