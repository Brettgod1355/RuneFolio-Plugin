package app.runefolio.sync;

import com.google.inject.Provides;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Image;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
    name = "RuneFolio Sync",
    description = "RuneFolio character connection client",
    tags = {"runefolio", "progress", "tracker", "loot"},
    internalName = "runefolio-sync",
    legacyDataDirectory = "runefolio-screenshot-queue"
)
public class RuneFolioPlugin extends Plugin
{
    private static final long MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L;
    private static final String CONFIG_GROUP = "runefolio";
    private static final String ACCOUNT_CONNECTION_TOKEN_KEY = "accountConnectionToken";
    private static final String LEGACY_CONNECTION_TOKEN_KEY = "connectionToken";
    private static final String CHARACTER_CONNECTION_TOKEN_PREFIX = "connectionToken.";
    private static final String LAST_SUCCESSFUL_SYNC_KEY = "lastSuccessfulSyncAt";
    private static final String PROGRESS_GUIDE_SHOWN_KEY = "progressSyncGuideShownV2";
    private static final int SYNC_BATCH_SIZE = 50;
    private static final int COLLECTION_LOG_TRANSMIT_SCRIPT = 4100;
    private static final int COLLECTION_LOG_INIT_SCRIPT = 2240;
    private static final int COLLECTION_SYNC_COOLDOWN_TICKS = 50;
    private static final String COLLECTION_LOG_TEXT = "New item added to your collection log: ";
    private static final int SCREENSHOT_MAX_BYTES = 3 * 1024 * 1024;
    private static final Pattern LEVEL_UP_PATTERN = Pattern.compile(
        ".*Your ([a-zA-Z]+) (?:level is|are)? now (\\d+)\\."
    );
    private static final Pattern QUEST_COMPLETION_PATTERN = Pattern.compile(
        ".*(?:completed|been|rebuilt|freed|defeated|saved).*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMBAT_ACHIEVEMENT_PATTERN = Pattern.compile(
        "Congratulations, you've completed an? (?<tier>\\w+) combat task: @.+?@(?<task>.+?)</col>"
            + "(?: \\((?<points>\\d+) points?\\))?\\.?"
    );
    private static final Pattern DIARY_TASK_COMPLETION_PATTERN = Pattern.compile(
        "^Well done! You have completed an? (?:easy|medium|hard|elite) task in the .+? area\\."
            + "(?:\\s+Your Achievement Diary has been updated\\.)?$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> PANEL_SETTING_KEYS = panelSettingKeys();
    private static final String PVP_OPPONENT_PLACEHOLDER = "PvP opponent";

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    // Deliberately NOT @Inject: Guice injects plugin fields while
    // pluginManager.loadCorePlugins()/loadSideLoadPlugins() run, which happens before
    // RuneLite.start() calls clientUI.init() (RuneLiteLAF.setup()). A Swing component
    // built that early gets its scrollbar UI installed and its colors/width cached
    // against the JVM's plain default look, before RuneLite's real theme is ever
    // installed - permanently, since nothing later revisits an already-built component
    // that isn't yet part of the visible tree. World Hopper and Loot Tracker avoid this
    // by constructing their panel manually inside startUp() (after clientUI.init()), so
    // this does the same.
    private RuneFolioPanel panel;

    @Inject
    private RuneFolioConfig config;

    @Inject
    private RuneFolioCollectionLogButton collectionLogButton;

    @Inject
    private RuneFolioDiaryTaskTracker diaryTaskTracker;
    @Inject private RuneFolioBankCollector bankCollector;
    @Inject private RuneFolioUnlockCollector unlockCollector;

    @Inject
    private ItemManager itemManager;

    @Inject
    private DrawManager drawManager;

    @Inject
    private Gson gson;

    @Inject
    private OkHttpClient okHttpClient;

    // RuneLite reuses this instance across enable/disable cycles, so everything
    // below is created in startUp() and torn down in shutDown().
    private volatile ScheduledExecutorService connectionExecutor;
    private volatile ScheduledExecutorService syncExecutor;
    private volatile RuneFolioScreenshotQueue screenshotQueue;
    private volatile RuneFolioScreenshotSpool screenshotSpool;
    private volatile ScheduledExecutorService screenshotUploadExecutor;
    private volatile boolean running;
    private final AtomicBoolean clearScreenshotSpoolRequested = new AtomicBoolean();
    private long lastScreenshotQueueWarningMillis;
    private final AtomicBoolean uploadInFlight = new AtomicBoolean();
    private NavigationButton navigationButton;
    private boolean navigationButtonAdded;
    private RuneFolioSyncQueue syncQueue;
    private volatile String accountConnectionToken;
    private volatile String connectionToken;
    private volatile String connectedCharacterName;
    private volatile String activeConnectionConfigKey;
    private volatile boolean activeConnectionIsLegacy;
    private volatile String lastKnownPlayerName;
    private volatile String activeIdentityKey;
    private final AtomicLong characterSession = new AtomicLong();
    private volatile List<RuneFolioApiClient.SkillSnapshot> lastKnownSkills = new ArrayList<>();
    private volatile JsonObject lastKnownQuestState;
    private volatile JsonObject lastKnownDiaryState;
    private volatile JsonObject lastKnownCombatState;
    private volatile long lastSuccessfulSyncAtMillis;
    private volatile String lastSuccessfullySyncedCharacterName;
    private boolean connectionLookupPending;
    private volatile boolean loginSyncPending;
    private boolean worldHopInProgress;
    private volatile String pendingCharacterSetupName;
    private volatile String pendingCharacterSetupUrl;
    private volatile String promptedCharacterName;
    private volatile String setupPollCharacter;
    private int ticksSinceLocalSnapshot;
    private final Map<Integer, Integer> collectionButtonItems = new LinkedHashMap<>();
    private final Set<String> pendingCollectionButtonEventIds = ConcurrentHashMap.newKeySet();
    private boolean collectionButtonSyncRequested;
    private int lastCollectionTransmitTick = -1;
    private int lastCollectionButtonClickTick = -1;
    private boolean questProgressRefreshPending;
    private boolean diaryProgressRefreshPending;
    private boolean combatProgressRefreshPending;
    private boolean interfaceScreenshotPending;
    private long nextManifestRefreshMillis;
    private final RuneFolioPetTracker petTracker = new RuneFolioPetTracker();
    private final RuneFolioBossRecordTracker bossRecordTracker = new RuneFolioBossRecordTracker();
    private final RuneFolioClueRecordTracker clueRecordTracker = new RuneFolioClueRecordTracker();
    private final RuneFolioPvpTracker pvpTracker = new RuneFolioPvpTracker();
    private final Map<Player, Integer> pvpHits = new IdentityHashMap<>();
    private final Map<Player, Integer> pvpDeaths = new IdentityHashMap<>();
    private int lootKeyScreenshotTick = -100;
    private UUID lootKeyScreenshotId;
    private final RuneFolioSlayerRecordTracker slayerRecordTracker = new RuneFolioSlayerRecordTracker();
    private final Set<String> knownPetNames = new HashSet<>();

    @Provides
    RuneFolioConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(RuneFolioConfig.class);
    }

    @Override
    protected void startUp() throws IOException
    {
        connectionExecutor = newExecutor("runefolio-connection");
        syncExecutor = newExecutor("runefolio-sync");
        screenshotUploadExecutor = newExecutor("runefolio-screenshots");
        screenshotQueue = new RuneFolioScreenshotQueue();
        running = true;

        panel = new RuneFolioPanel();
        screenshotSpool = openScreenshotSpool();
        navigationButton = NavigationButton.builder()
            .tooltip("RuneFolio Sync")
            .icon(RuneFolioBrand.createIcon(16))
            .priority(8)
            .panel(panel)
            .build();

        refreshNavigationButton();
        collectionLogButton.startUp(this::requestCollectionLogSyncFromButton);
        diaryTaskTracker.startUp(this::requestDiaryTaskSync);
        bankCollector.startUp(state -> {
            String name = currentPlayerName();
            return name != null && enqueueLiveEvent(RuneFolioSyncEvent.historyEvent("bank.snapshot", name, state));
        });
        unlockCollector.startUp(state -> {
            String name = currentPlayerName();
            return name != null && enqueueLiveEvent(RuneFolioSyncEvent.historyEvent("unlocks.snapshot", name, state));
        });
        panel.setTemporaryConnectAction(this::connectTemporaryCode);
        panel.setAccountConnectAction(this::connectRuneFolioAccount);
        panel.setAccountDisconnectAction(this::disconnectRuneFolioAccount);
        panel.setCharacterSetupAction(this::openCharacterSetup);
        panel.setManualSyncAction(() -> requestFullSync("manual"));
        panel.setClearScreenshotsAction(() -> clearScreenshotSpoolRequested.set(true));
        panel.configure((key, value) -> configManager.setConfiguration(CONFIG_GROUP, key, value));
        panel.syncSettings(config);

        syncQueue = new RuneFolioSyncQueue(configManager);
        lastSuccessfulSyncAtMillis = savedLong(LAST_SUCCESSFUL_SYNC_KEY);
        accountConnectionToken = configManager.getConfiguration(CONFIG_GROUP, ACCOUNT_CONNECTION_TOKEN_KEY);
        panel.setAccountConnected(isAccountMode());
        refreshSyncPanel();
        runOnClientThread(() ->
        {
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                connectionLookupPending = true;
            }
            refreshPanel();
        });

        screenshotUploadExecutor.scheduleWithFixedDelay(this::drainScreenshotSpool,
            5 + ThreadLocalRandom.current().nextInt(11), 5, TimeUnit.SECONDS);
        syncExecutor.scheduleAtFixedRate(
            () -> requestFullSync("periodic"),
            10,
            10,
            TimeUnit.MINUTES
        );
        syncExecutor.scheduleAtFixedRate(
            this::flushQueue,
            5,
            30,
            TimeUnit.SECONDS
        );
        syncExecutor.scheduleAtFixedRate(
            this::verifySavedConnection,
            1,
            1,
            TimeUnit.MINUTES
        );
        syncExecutor.scheduleAtFixedRate(this::refreshCollectorManifest, 1, 5, TimeUnit.MINUTES);

        log.info("RuneFolio Sync started");
    }

    RuneFolioScreenshotSpool openScreenshotSpool() throws IOException
    {
        return new RuneFolioScreenshotSpool(getPluginDirectory(), gson);
    }

    private static ScheduledExecutorService newExecutor(String threadName)
    {
        return Executors.newSingleThreadScheduledExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    private void refreshCollectorManifest()
    {
        if ((!isAccountMode() && connectionToken == null) || System.currentTimeMillis() < nextManifestRefreshMillis)
        {
            return;
        }
        nextManifestRefreshMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        try
        {
            RuneFolioCollectorManifest manifest = RuneFolioCollectorManifest.fetch(okHttpClient);
            if (manifest != null && running)
            {
                runOnClientThread(() -> RuneFolioCollectorManifest.install(manifest));
                nextManifestRefreshMillis = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
            }
        }
        catch (IOException unavailable)
        {
            log.debug("Collector manifest unavailable; retaining current catalog");
        }
    }

    @Override
    protected void shutDown()
    {
        running = false;
        if (navigationButton != null && navigationButtonAdded)
        {
            removeNavigation(navigationButton);
        }
        navigationButtonAdded = false;
        navigationButton = null;
        collectionLogButton.shutDown();
        diaryTaskTracker.shutDown();
        bankCollector.shutDown();
        unlockCollector.shutDown();

        finishPvpResults();
        collectionButtonSyncRequested = false;
        collectionButtonItems.clear();
        pendingCollectionButtonEventIds.clear();
        String playerName = lastKnownPlayerName;
        boolean accountMode = isAccountMode();
        String savedToken = accountMode ? accountConnectionToken : connectionToken;
        String filterIdentity = savedToken != null && savedToken.equals(connectionToken) ? activeIdentityKey : null;
        enqueueKnownSnapshots("shutdown");

        ScheduledExecutorService sync = syncExecutor;
        if (savedToken != null && !savedToken.isBlank() && syncQueue != null)
        {
            // Never awaited: shutDown() runs on the EDT and the request has its own timeout.
            submit(sync, () -> flushQueueWithToken(
                savedToken,
                accountMode ? null : playerName,
                filterIdentity,
                accountMode
            ));
        }
        if (sync != null)
        {
            sync.shutdown();
        }
        ScheduledExecutorService connection = connectionExecutor;
        if (connection != null)
        {
            connection.shutdownNow();
        }
        ScheduledExecutorService uploads = screenshotUploadExecutor;
        if (uploads != null)
        {
            uploads.shutdownNow();
        }
        RuneFolioScreenshotQueue queue = screenshotQueue;
        if (queue != null)
        {
            queue.close();
        }
        syncExecutor = null;
        connectionExecutor = null;
        screenshotUploadExecutor = null;
        screenshotQueue = null;
        screenshotSpool = null;

        deactivateCurrentCharacter();
        accountConnectionToken = null;
        worldHopInProgress = false;
        nextManifestRefreshMillis = 0;
        lastScreenshotQueueWarningMillis = 0;
        clearScreenshotSpoolRequested.set(false);
        log.info("RuneFolio Sync stopped");
    }

    void addNavigation(NavigationButton button)
    {
        clientToolbar.addNavigation(button);
    }

    void removeNavigation(NavigationButton button)
    {
        clientToolbar.removeNavigation(button);
    }

    private void runOnClientThread(Runnable task)
    {
        clientThread.invokeLater(() ->
        {
            if (running)
            {
                task.run();
            }
        });
    }

    private static boolean submit(ScheduledExecutorService executor, Runnable task)
    {
        return schedule(executor, task, 0, TimeUnit.MILLISECONDS);
    }

    private static boolean schedule(ScheduledExecutorService executor, Runnable task, long delay, TimeUnit unit)
    {
        if (executor == null || executor.isShutdown())
        {
            return false;
        }
        try
        {
            executor.schedule(task, delay, unit);
            return true;
        }
        catch (RejectedExecutionException stopped)
        {
            return false;
        }
    }

    private static Set<String> panelSettingKeys()
    {
        Set<String> keys = new HashSet<>();
        for (Method method : RuneFolioConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null)
            {
                keys.add(item.keyName());
            }
        }
        return Set.copyOf(keys);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }
        if ("syncPvpHistory".equals(event.getKey()))
        {
            runOnClientThread(() -> { pvpTracker.reset(); pvpHits.clear(); pvpDeaths.clear(); });
        }
        if ("hideSidePanel".equals(event.getKey()))
        {
            refreshNavigationButton();
        }
        if (PANEL_SETTING_KEYS.contains(event.getKey()))
        {
            SwingUtilities.invokeLater(() -> panel.syncSettings(config));
        }
    }

    private void refreshNavigationButton()
    {
        if (navigationButton == null)
        {
            return;
        }

        boolean shouldShow = !config.hideSidePanel();
        if (shouldShow && !navigationButtonAdded)
        {
            addNavigation(navigationButton);
            navigationButtonAdded = true;
        }
        else if (!shouldShow && navigationButtonAdded)
        {
            removeNavigation(navigationButton);
            navigationButtonAdded = false;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            // LOGGED_IN also fires after every scene reload; only a hop or a
            // fresh login (no character tracked yet) needs the login handling.
            if (worldHopInProgress)
            {
                worldHopInProgress = false;
                ticksSinceLocalSnapshot = 10;
                refreshPanel();
            }
            else if (lastKnownPlayerName == null)
            {
                ticksSinceLocalSnapshot = 10;
                connectionLookupPending = true;
                refreshPanel();
            }
        }
        else if (event.getGameState() == GameState.HOPPING)
        {
            finishPvpResults();
            pvpTracker.reset(); pvpHits.clear(); pvpDeaths.clear();
            lootKeyScreenshotTick = -100; lootKeyScreenshotId = null;
            bossRecordTracker.reset();
            clueRecordTracker.reset();
            slayerRecordTracker.reset();
            worldHopInProgress = true;
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            if (worldHopInProgress)
            {
                log.debug("Ignoring transient login screen during a world hop");
                return;
            }

            requestLogoutSkillSync();
            deactivateCurrentCharacter();
            refreshPanel();
        }
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        // Completed events retain their original identity in the durable queue.
        // Only in-progress, account-local observations are invalidated here.
        deactivateCurrentCharacter();
        connectionLookupPending = true;
    }

    private void connectRuneFolioAccount()
    {
        if (isAccountMode())
        {
            panel.setStatus("This RuneLite installation is already connected to your RuneFolio account.");
            return;
        }

        panel.setAccountConnecting(true);
        panel.setStatus("Opening RuneFolio in your browser...");

        ScheduledExecutorService executor = connectionExecutor;
        submit(executor, () ->
        {
            try
            {
                RuneFolioApiClient.AccountLoginRequest login =
                    RuneFolioApiClient.startAccountLogin(okHttpClient);
                if (!running)
                {
                    return;
                }
                SwingUtilities.invokeLater(() ->
                {
                    panel.openBrowser(login.getVerificationUrl());
                    panel.setStatus("Approve the connection in your browser. RuneLite is waiting...");
                });
                pollAccountLogin(executor, login, 0);
            }
            catch (Exception exception)
            {
                if (!running)
                {
                    return;
                }
                log.warn("RuneFolio account connection failed", exception);
                SwingUtilities.invokeLater(() ->
                {
                    panel.setAccountConnecting(false);
                    panel.setStatus("Account connection failed: " + safeMessage(exception));
                });
            }
        });
    }

    /** Runs on the executor it was started on; each attempt reschedules itself instead of blocking on sleep. */
    private void pollAccountLogin(ScheduledExecutorService executor, RuneFolioApiClient.AccountLoginRequest login, int attempt)
    {
        if (attempt >= 300)
        {
            SwingUtilities.invokeLater(() ->
            {
                panel.setAccountConnecting(false);
                panel.setStatus("Browser approval expired. Click Log in to RuneFolio to try again.");
            });
            return;
        }

        schedule(executor, () ->
        {
            try
            {
                RuneFolioApiClient.AccountPollResult result = RuneFolioApiClient.pollAccountLogin(
                    okHttpClient,
                    login.getRequestId(),
                    login.getPollToken()
                );
                if (!running)
                {
                    return;
                }
                if (!result.isApproved())
                {
                    pollAccountLogin(executor, login, attempt + 1);
                    return;
                }

                accountConnectionToken = result.getConnectionToken();
                configManager.setConfiguration(
                    CONFIG_GROUP,
                    ACCOUNT_CONNECTION_TOKEN_KEY,
                    accountConnectionToken
                );
                connectionToken = null;
                connectedCharacterName = null;
                activeConnectionConfigKey = null;
                activeConnectionIsLegacy = false;

                SwingUtilities.invokeLater(() ->
                {
                    panel.setAccountConnecting(false);
                    panel.setAccountConnected(true);
                    panel.setStatus("Connected to your RuneFolio account. Log in to a character to sync.");
                });
                runOnClientThread(() ->
                {
                    if (currentPlayerName() != null)
                    {
                        connectionLookupPending = true;
                    }
                });
            }
            catch (Exception exception)
            {
                if (!running)
                {
                    return;
                }
                log.warn("RuneFolio account connection failed", exception);
                SwingUtilities.invokeLater(() ->
                {
                    panel.setAccountConnecting(false);
                    panel.setStatus("Account connection failed: " + safeMessage(exception));
                });
            }
        }, 2, TimeUnit.SECONDS);
    }

    private void disconnectRuneFolioAccount()
    {
        String savedToken = accountConnectionToken;
        if (savedToken == null || savedToken.isBlank())
        {
            panel.setAccountConnected(false);
            return;
        }

        panel.setAccountConnecting(true);
        panel.setStatus("Disconnecting this RuneLite installation...");

        submit(connectionExecutor, () ->
        {
            try
            {
                RuneFolioApiClient.disconnectAccount(okHttpClient, savedToken);
                if (!running)
                {
                    return;
                }
                clearAccountConnection();
                SwingUtilities.invokeLater(() ->
                {
                    panel.setAccountConnecting(false);
                    panel.setAccountConnected(false);
                    panel.setStatus("RuneFolio account disconnected. Temporary character codes are still available.");
                });
                runOnClientThread(() ->
                {
                    String playerName = currentPlayerName();
                    if (playerName != null)
                    {
                        connectionLookupPending = true;
                    }
                });
            }
            catch (Exception exception)
            {
                if (!running)
                {
                    return;
                }
                log.warn("RuneFolio account disconnect failed", exception);
                SwingUtilities.invokeLater(() ->
                {
                    panel.setAccountConnecting(false);
                    panel.setStatus("Disconnect failed: " + safeMessage(exception));
                });
            }
        });
    }

    private void connectTemporaryCode(String code)
    {
        if (isAccountMode())
        {
            panel.setStatus("Disconnect the RuneFolio account before using a temporary character code.");
            return;
        }

        runOnClientThread(() ->
        {
            String playerName = currentPlayerName();
            if (playerName == null)
            {
                SwingUtilities.invokeLater(() ->
                    panel.setStatus("Log in to the linked RuneScape character before connecting.")
                );
                return;
            }

            long session = characterSession.get();
            SwingUtilities.invokeLater(() ->
            {
                panel.setConnecting(true);
                panel.setStatus("Verifying temporary character code...");
            });
            submit(connectionExecutor, () -> exchangeTemporaryCode(code, playerName, session));
        });
    }

    private void exchangeTemporaryCode(String code, String playerName, long session)
    {
        try
        {
            RuneFolioApiClient.ConnectionResult result = RuneFolioApiClient.exchange(
                okHttpClient,
                code,
                playerName,
                "RuneLite · " + playerName
            );
            if (!running)
            {
                return;
            }
            String configKey = connectionTokenKey(playerName);
            // Saved before the session check so the character keeps its token
            // even if the user switched characters while the code was verified.
            configManager.setConfiguration(CONFIG_GROUP, configKey, result.getConnectionToken());
            if (session != characterSession.get())
            {
                return;
            }
            connectionToken = result.getConnectionToken();
            connectedCharacterName = result.getCharacterName();
            activeConnectionConfigKey = configKey;
            activeConnectionIsLegacy = false;
            SwingUtilities.invokeLater(() ->
            {
                if (!running)
                {
                    return;
                }
                panel.clearCode();
                panel.setConnecting(false);
                panel.setCharacterName(playerName);
                showConnectionStatus(playerName, result.getCharacterName());
                loginSyncPending = true;
                verifySavedConnection();
            });
        }
        catch (Exception exception)
        {
            if (!running)
            {
                return;
            }
            log.warn("Temporary RuneFolio character connection failed", exception);
            if (session != characterSession.get())
            {
                return;
            }
            SwingUtilities.invokeLater(() ->
            {
                panel.setConnecting(false);
                panel.setStatus("Connection failed: " + safeMessage(exception));
            });
        }
    }

    private void verifySavedConnection()
    {
        if (isAccountMode())
        {
            verifyAccountConnection();
            return;
        }

        String savedToken = connectionToken;
        String playerName = lastKnownPlayerName;
        String configKey = activeConnectionConfigKey;
        boolean legacyToken = activeConnectionIsLegacy;
        long session = characterSession.get();
        String identityKey = activeIdentityKey;
        String previousName = previousIdentityName(identityKey, playerName);
        if (savedToken == null || savedToken.isBlank() || playerName == null || playerName.isBlank())
        {
            return;
        }

        submit(connectionExecutor, () ->
        {
            try
            {
                RuneFolioApiClient.ConnectionResult result = RuneFolioApiClient.heartbeat(okHttpClient, savedToken, playerName, identityKey, previousName);
                if (!running || session != characterSession.get() || !savedToken.equals(connectionToken))
                {
                    return;
                }
                String linkedCharacter = result.getCharacterName();
                if (!namesMatch(playerName, linkedCharacter))
                {
                    forgetTemporaryToken(savedToken, legacyToken ? null : configKey, false, identityKey);
                    if (savedToken.equals(connectionToken))
                    {
                        connectionToken = null;
                        connectedCharacterName = null;
                        activeConnectionConfigKey = null;
                        activeConnectionIsLegacy = false;
                        loginSyncPending = false;
                    }
                    SwingUtilities.invokeLater(() ->
                    {
                        panel.setCharacterName(playerName);
                        panel.setStatus(playerName + " is not connected to RuneFolio. Enter a temporary code for this character.");
                    });
                    return;
                }

                rememberIdentity(identityKey, linkedCharacter, savedToken);
                connectedCharacterName = linkedCharacter;
                if (legacyToken)
                {
                    String characterConfigKey = connectionTokenKey(playerName);
                    configManager.setConfiguration(CONFIG_GROUP, characterConfigKey, savedToken);
                    configManager.unsetConfiguration(CONFIG_GROUP, LEGACY_CONNECTION_TOKEN_KEY);
                    activeConnectionConfigKey = characterConfigKey;
                    activeConnectionIsLegacy = false;
                }

                SwingUtilities.invokeLater(() ->
                {
                    panel.setCharacterName(playerName);
                    showConnectionStatus(playerName, linkedCharacter);
                });

                if (loginSyncPending)
                {
                    loginSyncPending = false;
                    requestFullSync("login");
                }
            }
            catch (Exception exception)
            {
                if (!running || session != characterSession.get() || !savedToken.equals(connectionToken))
                {
                    return;
                }
                handleTemporaryConnectionFailure(savedToken, playerName, configKey, legacyToken, identityKey, exception);
            }
        });
    }

    private void verifyAccountConnection()
    {
        String savedToken = accountConnectionToken;
        String playerName = lastKnownPlayerName;
        long session = characterSession.get();
        String identityKey = activeIdentityKey;
        String previousName = previousIdentityName(identityKey, playerName);
        if (savedToken == null || savedToken.isBlank())
        {
            return;
        }

        submit(connectionExecutor, () ->
        {
            try
            {
                RuneFolioApiClient.AccountHeartbeatResult heartbeat =
                    RuneFolioApiClient.accountHeartbeat(okHttpClient, savedToken, playerName, identityKey, previousName);
                boolean characterConnected = heartbeat.isCharacterConnected();
                if (!running || session != characterSession.get() || !savedToken.equals(accountConnectionToken))
                {
                    return;
                }

                if (playerName != null && !playerName.isBlank() && !characterConnected)
                {
                    connectedCharacterName = null;
                    loginSyncPending = false;
                    showCharacterSetupRequired(playerName, heartbeat.getSetupUrl());
                    return;
                }

                if (playerName != null && !playerName.isBlank())
                {
                    rememberIdentity(identityKey, playerName, null);
                    connectedCharacterName = playerName;
                }
                boolean needsFirstSync = playerName != null
                    && !playerName.isBlank()
                    && !namesMatch(playerName, lastSuccessfullySyncedCharacterName);
                pendingCharacterSetupName = null;
                pendingCharacterSetupUrl = null;
                setupPollCharacter = null;
                SwingUtilities.invokeLater(() ->
                {
                    panel.hideCharacterSetup();
                    panel.setAccountConnected(true);
                    panel.setCharacterName(playerName);
                    panel.setStatus(playerName == null || playerName.isBlank()
                        ? "Connected to your RuneFolio account. Log in to a character to sync."
                        : (needsFirstSync
                            ? "RuneFolio account connected. Sending the first sync for " + playerName + "..."
                            : "Connected to " + playerName + ". Automatic sync is ready."));
                });

                if (loginSyncPending || needsFirstSync)
                {
                    loginSyncPending = false;
                    requestFullSync("login");
                }
            }
            catch (Exception exception)
            {
                String message = safeMessage(exception);
                if (!running || session != characterSession.get() || !savedToken.equals(accountConnectionToken))
                {
                    return;
                }
                log.warn("Saved RuneFolio account connection check failed", exception);
                String lowerMessage = message.toLowerCase(Locale.ROOT);
                if (lowerMessage.contains("revoked") || lowerMessage.contains("expired"))
                {
                    clearAccountConnection();
                    SwingUtilities.invokeLater(() ->
                    {
                        panel.setAccountConnected(false);
                        panel.hideCharacterSetup();
                        panel.setStatus("RuneFolio account connection revoked. Log in again or use a temporary code.");
                    });
                    runOnClientThread(() ->
                    {
                        if (playerName != null)
                        {
                            activateConnectionForCharacter(playerName);
                        }
                    });
                }
                else if (lowerMessage.contains("already connected to another runefolio account"))
                {
                    pendingCharacterSetupName = null;
                    pendingCharacterSetupUrl = null;
                    setupPollCharacter = null;
                    SwingUtilities.invokeLater(() ->
                    {
                        panel.hideCharacterSetup();
                        panel.setStatus("This character is already connected to another RuneFolio account.");
                    });
                }
                else
                {
                    SwingUtilities.invokeLater(() ->
                        panel.setStatus("Connection check failed. RuneFolio will try again automatically.")
                    );
                }
            }
        });
    }

    private void showCharacterSetupRequired(String playerName, String setupUrl)
    {
        if (setupUrl == null || setupUrl.isBlank())
        {
            SwingUtilities.invokeLater(() ->
                panel.setStatus("RuneFolio could not create a secure character setup link. It will try again automatically.")
            );
            return;
        }

        pendingCharacterSetupName = playerName;
        pendingCharacterSetupUrl = setupUrl;

        SwingUtilities.invokeLater(() ->
        {
            panel.setCharacterName(playerName);
            panel.showCharacterSetup();
            panel.setStatus(playerName + " is not on your RuneFolio account yet. Add this character to continue.");
        });

        if (namesMatch(promptedCharacterName, playerName))
        {
            return;
        }

        promptedCharacterName = playerName;
        runOnClientThread(() -> client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            "<col=d9b861>RuneFolio:</col> " + playerName
                + " is not on your account yet. Open the RuneFolio sidebar and click Add character.",
            null
        ));

        if (config.autoOpenCharacterSetup())
        {
            SwingUtilities.invokeLater(() -> panel.openBrowser(setupUrl));
            startCharacterSetupPolling(playerName);
        }
    }

    private void openCharacterSetup()
    {
        String setupUrl = pendingCharacterSetupUrl;
        String playerName = pendingCharacterSetupName;
        if (setupUrl == null || playerName == null)
        {
            panel.setStatus("Log in to a new character before opening character setup.");
            return;
        }

        panel.openBrowser(setupUrl);
        startCharacterSetupPolling(playerName);
    }

    private void startCharacterSetupPolling(String playerName)
    {
        setupPollCharacter = playerName;
        scheduleCharacterSetupCheck(syncExecutor, playerName, 0);
    }

    private void scheduleCharacterSetupCheck(ScheduledExecutorService executor, String playerName, int attempt)
    {
        long session = characterSession.get();
        String token = accountConnectionToken;
        String identityKey = activeIdentityKey;
        String previousName = previousIdentityName(identityKey, playerName);
        schedule(executor, () ->
        {
            if (!running
                || !isAccountMode()
                || !namesMatch(playerName, setupPollCharacter)
                || !namesMatch(playerName, lastKnownPlayerName))
            {
                return;
            }

            try
            {
                RuneFolioApiClient.AccountHeartbeatResult heartbeat =
                    RuneFolioApiClient.accountHeartbeat(okHttpClient, token, playerName, identityKey, previousName);
                if (!running || session != characterSession.get() || !Objects.equals(token, accountConnectionToken))
                {
                    return;
                }
                if (heartbeat.isCharacterConnected())
                {
                    rememberIdentity(identityKey, playerName, null);
                    setupPollCharacter = null;
                    pendingCharacterSetupName = null;
                    pendingCharacterSetupUrl = null;
                    connectedCharacterName = playerName;
                    SwingUtilities.invokeLater(() ->
                    {
                        panel.hideCharacterSetup();
                        panel.setStatus("Character approved. Sending the first skill sync...");
                    });
                    requestFullSync("login");
                    return;
                }
            }
            catch (Exception exception)
            {
                if (!running)
                {
                    return;
                }
                log.debug("Waiting for RuneFolio character setup", exception);
            }

            if (attempt < 149)
            {
                scheduleCharacterSetupCheck(executor, playerName, attempt + 1);
            }
        }, 2, TimeUnit.SECONDS);
    }

    private void handleTemporaryConnectionFailure(
        String savedToken,
        String playerName,
        String configKey,
        boolean legacyToken,
        String identityKey,
        Exception exception
    )
    {
        String message = safeMessage(exception);
        log.warn("Saved RuneFolio connection check failed", exception);
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        if (lowerMessage.contains("revoked") || lowerMessage.contains("expired"))
        {
            forgetTemporaryToken(savedToken, configKey, legacyToken, identityKey);
            if (savedToken.equals(connectionToken))
            {
                connectionToken = null;
                connectedCharacterName = null;
                activeConnectionConfigKey = null;
                activeConnectionIsLegacy = false;
                loginSyncPending = false;
            }
            SwingUtilities.invokeLater(() ->
                panel.setStatus("Connection revoked or expired. Create a new temporary code for " + playerName + ".")
            );
        }
        else
        {
            SwingUtilities.invokeLater(() ->
                panel.setStatus("Connection check failed. RuneFolio will try again automatically.")
            );
        }
    }

    private void activateConnectionForCharacter(String playerName)
    {
        connectedCharacterName = null;
        lastSuccessfullySyncedCharacterName = null;
        pendingCharacterSetupName = null;
        pendingCharacterSetupUrl = null;
        setupPollCharacter = null;
        SwingUtilities.invokeLater(panel::hideCharacterSetup);
        loginSyncPending = false;

        if (isAccountMode())
        {
            connectionToken = null;
            activeConnectionConfigKey = null;
            activeConnectionIsLegacy = false;
            loginSyncPending = true;
            SwingUtilities.invokeLater(() -> panel.setStatus("Checking your RuneFolio account connection..."));
            verifyAccountConnection();
            return;
        }

        String configKey = connectionTokenKey(playerName);
        String savedToken = configManager.getConfiguration(CONFIG_GROUP, configKey);
        boolean legacyToken = false;
        if ((savedToken == null || savedToken.isBlank()) && activeIdentityKey != null)
        {
            savedToken = configManager.getConfiguration(CONFIG_GROUP, "identityToken." + activeIdentityKey);
        }

        if (savedToken == null || savedToken.isBlank())
        {
            savedToken = configManager.getConfiguration(CONFIG_GROUP, LEGACY_CONNECTION_TOKEN_KEY);
            legacyToken = savedToken != null && !savedToken.isBlank();
        }

        activeConnectionConfigKey = configKey;
        activeConnectionIsLegacy = legacyToken;

        if (savedToken == null || savedToken.isBlank())
        {
            connectionToken = null;
            SwingUtilities.invokeLater(() ->
                panel.setStatus(playerName + " is not connected to RuneFolio. Enter a temporary code for this character.")
            );
            return;
        }

        connectionToken = savedToken;
        loginSyncPending = true;
        SwingUtilities.invokeLater(() -> panel.setStatus("Checking " + playerName + "'s RuneFolio connection..."));
        verifySavedConnection();
    }

    private void requestFullSync(String trigger)
    {
        boolean accountMode = isAccountMode();
        String savedToken = accountMode ? accountConnectionToken : connectionToken;
        long session = characterSession.get();
        if (savedToken == null || savedToken.isBlank())
        {
            if ("manual".equals(trigger))
            {
                SwingUtilities.invokeLater(() ->
                    panel.setStatus("Connect RuneFolio before starting a manual sync.")
                );
            }
            return;
        }

        runOnClientThread(() ->
        {
            if (session != characterSession.get() || !canCollectCurrentWorld()
                || !savedToken.equals(accountMode ? accountConnectionToken : connectionToken))
            {
                return;
            }
            String playerName = currentPlayerName();
            if (accountMode && namesMatch(playerName, pendingCharacterSetupName))
            {
                return;
            }
            List<RuneFolioApiClient.SkillSnapshot> skills = collectSkillSnapshot();
            if (playerName == null || skills.isEmpty())
            {
                if ("manual".equals(trigger))
                {
                    SwingUtilities.invokeLater(() ->
                        panel.setStatus("Log in to a RuneScape character before syncing.")
                    );
                }
                return;
            }

            lastKnownPlayerName = playerName;
            lastKnownSkills = new ArrayList<>(skills);
            JsonObject questState = RuneFolioProgressCollector.quests(client);
            JsonObject diaryState = RuneFolioProgressCollector.diaries(client);
            JsonObject combatState = RuneFolioProgressCollector.combatAchievements(client);
            lastKnownQuestState = questState.deepCopy();
            lastKnownDiaryState = diaryState.deepCopy();
            lastKnownCombatState = combatState.deepCopy();

            String linkedCharacter = connectedCharacterName;
            if (linkedCharacter == null || !namesMatch(playerName, linkedCharacter))
            {
                SwingUtilities.invokeLater(() -> showConnectionStatus(playerName, linkedCharacter));
                return;
            }

            List<RuneFolioSyncEvent> events = new ArrayList<>();
            unlockCollector.syncNow();
            events.add(RuneFolioSyncEvent.skillSnapshot(playerName, trigger, skills));
            events.add(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE, playerName, trigger, questState
            ));
            events.add(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.DIARY_SNAPSHOT_TYPE, playerName, trigger, diaryState
            ));
            events.add(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.COMBAT_SNAPSHOT_TYPE, playerName, trigger, combatState
            ));
            boolean queued = true;
            for (RuneFolioSyncEvent event : events)
            {
                queued &= enqueueIdentifiedEvent(event);
            }
            if (!queued)
            {
                String status = queueFullStatus()
                    + " If this persists, check your connection and the RuneLite log.";
                SwingUtilities.invokeLater(() -> panel.setStatus(status));
                refreshSyncPanel();
                return;
            }

            refreshSyncPanel();
            showProgressSyncGuideOnce();
            SwingUtilities.invokeLater(() ->
                panel.setStatus("Sync queued. Sending it to RuneFolio...")
            );
            submit(syncExecutor, () -> flushQueueWithToken(
                savedToken,
                accountMode ? null : playerName,
                accountMode
            ));
        });
    }

    private String queueFullStatus()
    {
        RuneFolioSyncQueue queue = syncQueue;
        int waiting = queue == null ? 0 : queue.size();
        return "The local sync queue is full (" + waiting + " events waiting). Existing events were kept"
            + " and are retried automatically every 30 seconds.";
    }

    private void showProgressSyncGuideOnce()
    {
        if (Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, PROGRESS_GUIDE_SHOWN_KEY)))
        {
            return;
        }

        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            "<col=d9b861>RuneFolio setup:</col> Quests, diary tier completion, and Combat Achievements "
                + "sync automatically. To collect individual diary tasks, open each diary area once; "
                + "RuneFolio captures and uploads it automatically without scrolling. For Collection Log progress, "
                + "open the Collection Log and click Sync all in the bottom-right corner.",
            null
        );
        configManager.setConfiguration(CONFIG_GROUP, PROGRESS_GUIDE_SHOWN_KEY, true);
    }

    private void enqueueKnownSnapshots(String trigger)
    {
        String playerName = lastKnownPlayerName;
        List<RuneFolioApiClient.SkillSnapshot> skills = new ArrayList<>(lastKnownSkills);
        boolean accountMode = isAccountMode();
        String linkedCharacter = connectedCharacterName;
        if (playerName == null || playerName.isBlank() || skills.isEmpty()
            || (accountMode && namesMatch(playerName, pendingCharacterSetupName))
            || (!accountMode && (linkedCharacter == null || !namesMatch(playerName, linkedCharacter))))
        {
            return;
        }

        boolean queued = enqueueIdentifiedEvent(RuneFolioSyncEvent.skillSnapshot(playerName, trigger, skills));
        if (lastKnownQuestState != null)
        {
            queued |= enqueueIdentifiedEvent(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE,
                playerName,
                trigger,
                lastKnownQuestState
            ));
        }
        if (lastKnownDiaryState != null)
        {
            queued |= enqueueIdentifiedEvent(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.DIARY_SNAPSHOT_TYPE,
                playerName,
                trigger,
                lastKnownDiaryState
            ));
        }
        if (lastKnownCombatState != null)
        {
            queued |= enqueueIdentifiedEvent(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.COMBAT_SNAPSHOT_TYPE,
                playerName,
                trigger,
                lastKnownCombatState
            ));
        }
        if (queued)
        {
            refreshSyncPanel();
        }
    }

    private void flushQueue()
    {
        boolean accountMode = isAccountMode();
        String savedToken = accountMode ? accountConnectionToken : connectionToken;
        String playerName = lastKnownPlayerName;
        if (savedToken == null || savedToken.isBlank())
        {
            refreshSyncPanel();
            return;
        }
        flushQueueWithToken(savedToken, accountMode ? null : playerName, accountMode);
    }

    private void flushQueueWithToken(String savedToken, String characterFilter, boolean accountMode)
    {
        flushQueueWithToken(savedToken, characterFilter,
            savedToken.equals(connectionToken) ? activeIdentityKey : null, accountMode);
    }

    private void flushQueueWithToken(String savedToken, String characterFilter, String filterIdentity, boolean accountMode)
    {
        RuneFolioSyncQueue syncQueue = this.syncQueue;
        if (syncQueue == null || !uploadInFlight.compareAndSet(false, true))
        {
            return;
        }

        try
        {
            List<RuneFolioSyncEvent> events = syncQueue.snapshot(
                SYNC_BATCH_SIZE,
                event -> characterFilter == null || (filterIdentity != null && filterIdentity.equals(event.getIdentityKey()))
                    || (event.getIdentityKey() == null && namesMatch(characterFilter, event.getCharacterName()))
            );
            if (events.isEmpty())
            {
                return;
            }

            RuneFolioApiClient.BatchSyncResult result = RuneFolioApiClient.syncEvents(okHttpClient, savedToken, events);
            syncQueue.acknowledge(result.getAcknowledgedEventIds());

            if (result.shouldRevoke())
            {
                throw new IOException("RuneFolio connection was revoked or expired.");
            }
            announceCollectionButtonResult(result);

            int sent = result.getAcknowledgedEventIds().size();
            int waiting = result.getRetryableRejectedCount();
            String confirmation = syncConfirmation(events);
            if (sent > 0)
            {
                lastSuccessfulSyncAtMillis = System.currentTimeMillis();
                configManager.setConfiguration(
                    CONFIG_GROUP,
                    LAST_SUCCESSFUL_SYNC_KEY,
                    Long.toString(lastSuccessfulSyncAtMillis)
                );
                lastSuccessfullySyncedCharacterName = characterFilter == null
                    ? lastKnownPlayerName
                    : characterFilter;
                SwingUtilities.invokeLater(() ->
                    panel.setStatus(
                        confirmation + sent + (sent == 1 ? " event" : " events") + " synced."
                            + (waiting > 0
                                ? " " + waiting + (waiting == 1 ? " event is" : " events are") + " waiting to retry."
                                : "")
                    )
                );
            }
            else if (waiting > 0)
            {
                SwingUtilities.invokeLater(() ->
                    panel.setStatus(
                        waiting + (waiting == 1 ? " event is" : " events are")
                            + " waiting to retry automatically."
                    )
                );
            }

            if (result.shouldRequestFullSync())
            {
                requestFullSync("server_request");
            }
        }
        catch (Exception exception)
        {
            handleSkillSyncFailure(savedToken, characterFilter, accountMode, exception);
        }
        finally
        {
            uploadInFlight.set(false);
            refreshSyncPanel();
        }
    }

    private void announceCollectionButtonResult(RuneFolioApiClient.BatchSyncResult result)
    {
        Set<String> acknowledged = new HashSet<>(result.getAcknowledgedEventIds());
        acknowledged.retainAll(pendingCollectionButtonEventIds);
        if (acknowledged.isEmpty())
        {
            return;
        }

        Set<String> successful = new HashSet<>(result.getSuccessfulEventIds());
        successful.retainAll(acknowledged);
        pendingCollectionButtonEventIds.removeAll(acknowledged);
        runOnClientThread(() ->
        {
            if (!successful.isEmpty())
            {
                addRuneFolioChatMessage("<col=64cd58>Your RuneFolio Collection Log has been updated!</col>");
            }
            else
            {
                addRuneFolioChatMessage("<col=d67966>The Collection Log update was rejected. Please reopen it and try again.</col>");
            }
        });
    }

    private static String syncConfirmation(List<RuneFolioSyncEvent> events)
    {
        boolean login = false;
        boolean manual = false;
        for (RuneFolioSyncEvent event : events)
        {
            String trigger = event.getTrigger();
            if ("logout".equals(trigger))
            {
                return "Logout sync sent. ";
            }
            if ("login".equals(trigger))
            {
                login = true;
            }
            else if ("manual".equals(trigger))
            {
                manual = true;
            }
        }
        if (login)
        {
            return "Login sync sent. ";
        }
        if (manual)
        {
            return "Manual sync sent. ";
        }
        return "Connected. ";
    }

    private void handleSkillSyncFailure(
        String savedToken,
        String playerName,
        boolean accountMode,
        Exception exception
    )
    {
        if (!accountMode && !savedToken.equals(connectionToken))
        {
            // A failed upload for the previous character must not revoke the new connection.
            return;
        }
        log.warn("RuneFolio skill sync failed", exception);
        String message = safeMessage(exception);
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        if (lowerMessage.contains("revoked") || lowerMessage.contains("expired"))
        {
            if (accountMode && savedToken.equals(accountConnectionToken))
            {
                clearAccountConnection();
                SwingUtilities.invokeLater(() -> panel.setAccountConnected(false));
            }
            else if (!accountMode)
            {
                forgetTemporaryToken(savedToken, activeConnectionConfigKey, activeConnectionIsLegacy, activeIdentityKey);
                connectionToken = null;
                connectedCharacterName = null;
                activeConnectionConfigKey = null;
                activeConnectionIsLegacy = false;
            }
            SwingUtilities.invokeLater(() ->
                panel.setStatus("Connection revoked or expired. Connect RuneFolio again.")
            );
        }
        else if (!accountMode && (lowerMessage.contains("does not match") || lowerMessage.contains("mismatch")))
        {
            SwingUtilities.invokeLater(() -> showConnectionStatus(playerName, connectedCharacterName));
        }
        else if (accountMode && lowerMessage.contains("not on your runefolio account yet"))
        {
            SwingUtilities.invokeLater(() ->
                panel.setStatus("Checking the secure character setup link...")
            );
            verifyAccountConnection();
        }
        else if (lowerMessage.contains("already connected to another runefolio account"))
        {
            SwingUtilities.invokeLater(() ->
            {
                panel.hideCharacterSetup();
                panel.setStatus("This character is already connected to another RuneFolio account.");
            });
        }
        else
        {
            SwingUtilities.invokeLater(() ->
                panel.setStatus("Sync queued (" + syncQueue.size() + " pending). RuneFolio will retry automatically.")
            );
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!canCollectCurrentWorld())
        {
            return;
        }

        String observedPlayer = currentPlayerName();
        String observedIdentity = RuneFolioNameChange.identityKey(client.getAccountHash());
        if (RuneFolioNameChange.characterChanged(lastKnownPlayerName, observedPlayer, activeIdentityKey, observedIdentity))
        {
            deactivateCurrentCharacter();
            connectionLookupPending = true;
        }

        activeIdentityKey = observedIdentity;
        captureCompletionRecords();
        capturePvpResults();
        capturePendingInterfaceScreenshot();
        String petCaption = petTracker.pollCaption(client.getTickCount());
        if (petCaption != null && config.screenshotPets()) requestScreenshot("pet", petCaption);

        finishCollectionButtonSyncIfReady();
        enqueuePendingProgressSnapshots();

        ticksSinceLocalSnapshot++;
        if (!loginSyncPending && !lastKnownSkills.isEmpty() && ticksSinceLocalSnapshot < 10)
        {
            return;
        }

        String playerName = currentPlayerName();
        if (playerName == null || playerName.isBlank())
        {
            return;
        }

        boolean characterChanged = lastKnownPlayerName == null || !namesMatch(lastKnownPlayerName, playerName);
        lastKnownPlayerName = playerName;
        lastKnownSkills = collectSkillSnapshot();
        ticksSinceLocalSnapshot = 0;
        SwingUtilities.invokeLater(() -> panel.setCharacterName(playerName));

        if (connectionLookupPending || characterChanged)
        {
            connectionLookupPending = false;
            activateConnectionForCharacter(playerName);
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (!canCollectCurrentWorld()
            || !namesMatch(lastKnownPlayerName, currentPlayerName()))
        {
            return;
        }
        Skill skill = event.getSkill();
        if (skill == null)
        {
            return;
        }

        List<RuneFolioApiClient.SkillSnapshot> updatedSkills = new ArrayList<>(lastKnownSkills);
        updatedSkills.removeIf(snapshot -> snapshot.getName().equals(skill.getName()));
        updatedSkills.add(new RuneFolioApiClient.SkillSnapshot(
            skill.getName(),
            event.getLevel(),
            event.getXp()
        ));
        lastKnownSkills = updatedSkills;
    }

    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        if ((!config.syncLootDrops() && !config.syncCompletionHistory() && !config.syncPvpHistory() && !config.uploadScreenshots()) || !canCollectCurrentWorld()
            || event.getItems() == null || event.getItems().isEmpty())
        {
            return;
        }

        String playerName = currentPlayerName();
        if (playerName == null)
        {
            return;
        }

        JsonArray items = new JsonArray();
        long totalGeValue = 0L;
        long totalHaValue = 0L;
        String firstUntradeableItem = null;
        for (ItemStack stack : event.getItems())
        {
            int quantity = stack.getQuantity();
            if (quantity <= 0)
            {
                continue;
            }

            int itemId = itemManager.canonicalize(stack.getId());
            String itemName = itemManager.getItemComposition(itemId).getName();
            if (firstUntradeableItem == null && !itemManager.getItemComposition(itemId).isTradeable())
            {
                firstUntradeableItem = itemName;
            }
            int unitGeValue = Math.max(0, itemManager.getItemPrice(itemId));
            int unitHaValue = Math.max(0, itemManager.getItemComposition(itemId).getHaPrice());
            long itemGeValue = safeLootValue(quantity, unitGeValue);
            long itemHaValue = safeLootValue(quantity, unitHaValue);

            JsonObject item = new JsonObject();
            item.addProperty("itemId", itemId);
            item.addProperty("itemName", itemName == null || itemName.isBlank() ? "Unknown item" : itemName);
            item.addProperty("quantity", quantity);
            item.addProperty("unitGeValue", unitGeValue);
            item.addProperty("unitHaValue", unitHaValue);
            item.addProperty("totalGeValue", itemGeValue);
            item.addProperty("totalHaValue", itemHaValue);
            items.add(item);
            totalGeValue = safeLootAdd(totalGeValue, itemGeValue);
            totalHaValue = safeLootAdd(totalHaValue, itemHaValue);
        }

        if (items.size() == 0)
        {
            return;
        }

        String sourceName = event.getName();
        String sourceType = event.getType() == null
            ? "unknown"
            : event.getType().name().toLowerCase(Locale.ROOT);
        if (config.syncPvpHistory() && "player".equals(sourceType)) pvpTracker.loot(sourceName, client.getTickCount(), items);
        String rewardCategory = RuneFolioRewardScreenshots.category(sourceName, sourceType);
        UUID rewardScreenshot = null;
        boolean rewardEnabled = ("clue_reward".equals(rewardCategory) && config.screenshotClueRewards())
            || ("raid_chest_reward".equals(rewardCategory) && config.screenshotRaidChestRewards())
            || ("loot_key".equals(rewardCategory) && config.screenshotLootKeys());
        if (config.uploadScreenshots() && rewardEnabled)
        {
            // Native loot-key events include every nonempty tab. Capture the visible window once.
            if ("loot_key".equals(rewardCategory) && lootKeyScreenshotTick == client.getTickCount()) rewardScreenshot = lootKeyScreenshotId;
            else
            {
                rewardScreenshot = UUID.randomUUID();
                requestScreenshot(rewardCategory, sourceName + " rewards", rewardScreenshot);
                if ("loot_key".equals(rewardCategory)) { lootKeyScreenshotTick = client.getTickCount(); lootKeyScreenshotId = rewardScreenshot; }
            }
        }
        if (config.syncPvpHistory() && "loot_key".equals(rewardCategory))
        {
            JsonObject result = new JsonObject();
            result.addProperty("kind", "loot_key"); result.addProperty("evidence", "loot_chest"); result.add("items", items.deepCopy());
            // This links the opening window, not a claim that its visible tab depicts this key.
            if (rewardScreenshot != null) result.addProperty("screenshotId", rewardScreenshot.toString());
            enqueueLiveEvent(RuneFolioSyncEvent.historyEvent("pvp.result", playerName, result));
        }
        if (config.syncCompletionHistory() && sourceName != null)
        {
            String lower = sourceName.toLowerCase(Locale.ROOT);
            for (String tier : new String[] {"beginner", "easy", "medium", "hard", "elite", "master"})
            {
                if (lower.equals("clue scroll (" + tier + ")"))
                {
                    JsonObject record = clueRecordTracker.rewards(items, client.getTickCount(), tier);
                    if (record != null) {
                        if (rewardScreenshot != null) record.addProperty("screenshotId", rewardScreenshot.toString());
                        enqueueLiveEvent(RuneFolioSyncEvent.historyEvent("clue.completion", playerName, record));
                    }
                }
            }
        }
        if (!config.syncLootDrops()) return;
        boolean pvpOptIn = config.syncPvpHistory();
        String safeSourceName = lootSourceName(sourceName, sourceType, pvpOptIn);
        enqueueLiveEvent(RuneFolioSyncEvent.lootDrop(
            playerName,
            safeSourceName,
            sourceType,
            lootSourceCombatLevel(event.getCombatLevel(), sourceType, pvpOptIn),
            Math.max(1, event.getAmount()),
            items,
            totalGeValue,
            totalHaValue
        ));

        if (rewardEnabled) return;
        if (config.uploadScreenshots() && config.screenshotValuableDrops()
            && totalGeValue >= Math.max(0, config.screenshotValuableDropThreshold()))
        {
            requestScreenshot("high_value_drop", safeSourceName + " drop worth " + totalGeValue + " gp");
        }
        else if (config.uploadScreenshots() && config.screenshotUntradeableDrops() && firstUntradeableItem != null)
        {
            requestScreenshot("untradeable_drop", firstUntradeableItem + " from " + safeSourceName);
        }
    }

    /** The defeated player's name only leaves the client once PvP history is opted in. */
    static String lootSourceName(String sourceName, String sourceType, boolean pvpOptIn)
    {
        if ("player".equals(sourceType) && !pvpOptIn) return PVP_OPPONENT_PLACEHOLDER;
        return sourceName == null || sourceName.isBlank() ? "Unknown loot source" : sourceName;
    }

    static int lootSourceCombatLevel(int combatLevel, String sourceType, boolean pvpOptIn)
    {
        if ("player".equals(sourceType) && !pvpOptIn) return 0;
        return Math.max(0, combatLevel);
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (!canCapturePvp() || (!config.syncPvpHistory() && !(config.uploadScreenshots() && config.screenshotPvpKills()))
            || !event.getHitsplat().isMine() || event.getHitsplat().getAmount() <= 0
            || !(event.getActor() instanceof Player) || event.getActor() == client.getLocalPlayer()) return;
        Player target = (Player) event.getActor();
        if (pvpHits.size() < 32 && !pvpDeaths.containsKey(target)) pvpHits.put(target, client.getTickCount());
    }

    private void capturePvpResults()
    {
        if (!config.syncPvpHistory() && !(config.uploadScreenshots() && config.screenshotPvpKills()))
        { pvpTracker.reset(); pvpHits.clear(); pvpDeaths.clear(); return; }
        int tick = client.getTickCount();
        pvpDeaths.entrySet().removeIf(entry -> tick - entry.getValue() > 10 || tick < entry.getValue());
        Iterator<Map.Entry<Player, Integer>> hits = pvpHits.entrySet().iterator();
        while (hits.hasNext())
        {
            Map.Entry<Player, Integer> hit = hits.next();
            if (tick < hit.getValue() || tick - hit.getValue() > 1) { hits.remove(); continue; }
            if (!hit.getKey().isDead()) continue;
            hits.remove(); pvpDeaths.put(hit.getKey(), tick);
            String name = hit.getKey().getName();
            RuneFolioPvpTracker.Result result = pvpTracker.death(name == null ? null : Text.toJagexName(name), tick, Instant.now().toString());
            if (result != null) {
                result.historyEnabled = config.syncPvpHistory() && canCapturePvp();
                result.characterName = currentPlayerName(); result.identityKey = activeIdentityKey;
            }
            if (result != null && config.uploadScreenshots() && config.screenshotPvpKills())
            {
                UUID screenshotId = UUID.randomUUID(); result.payload.addProperty("screenshotId", screenshotId.toString());
                requestScreenshot("pvp_kill", "PvP finishing blow", screenshotId);
            }
        }
        for (RuneFolioPvpTracker.Result result : pvpTracker.poll(tick))
            savePvpResult(result);
    }

    private boolean canCapturePvp()
    {
        String token = isAccountMode() ? accountConnectionToken : connectionToken;
        return canCollectCurrentWorld() && token != null && !token.isBlank()
            && namesMatch(currentPlayerName(), connectedCharacterName);
    }

    private void savePvpResult(RuneFolioPvpTracker.Result result)
    {
        if (config.syncPvpHistory() && result.historyEnabled && result.characterName != null && syncQueue != null)
        {
            if (!syncQueue.enqueue(RuneFolioSyncEvent.pvpResult(result.characterName, result).withIdentityKey(result.identityKey)))
                log.warn("RuneFolio PvP result could not fit in the local sync queue");
        }
    }

    private void finishPvpResults() { for (RuneFolioPvpTracker.Result result : pvpTracker.finish()) savePvpResult(result); }

    private void captureCompletionRecords()
    {
        if (!config.syncCompletionHistory())
        {
            bossRecordTracker.reset(); clueRecordTracker.reset(); slayerRecordTracker.reset();
            return;
        }
        String name = currentPlayerName();
        if (name == null) return;
        int tick = client.getTickCount();
        JsonObject boss = bossRecordTracker.poll(tick);
        if (boss != null)
        {
            String source = boss.get("sourceName").getAsString();
            if (source.startsWith("Tombs of Amascut"))
            {
                int level = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
                if (level >= 0 && level <= 1000) boss.addProperty("raidLevel", level);
                addObservedPartySize(boss, new int[] {VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3, VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7});
            }
            if (source.startsWith("Theatre of Blood")) addObservedPartySize(boss, new int[] {VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4});
            if (source.startsWith("Chambers of Xeric") && client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1)
            {
                int total = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSCORE);
                int personal = client.getVarpValue(VarPlayerID.RAIDS_PLAYERSCORE);
                if (total > 0 && personal >= 0 && personal <= total)
                { boss.addProperty("totalPoints", total); boss.addProperty("personalPoints", personal); }
            }
            enqueueLiveEvent(RuneFolioSyncEvent.historyEvent("boss.completion", name, boss));
        }
        JsonObject clue = clueRecordTracker.poll(tick);
        if (clue != null) enqueueLiveEvent(RuneFolioSyncEvent.historyEvent("clue.completion", name, clue));
        JsonObject slayer = slayerRecordTracker.poll(tick);
        if (slayer != null) enqueueLiveEvent(RuneFolioSyncEvent.historyEvent("slayer.completion", name, slayer));
    }

    private void addObservedPartySize(JsonObject record, int[] slots)
    {
        int present = 0;
        for (int slot : slots) if (client.getVarbitValue(slot) > 0) present++;
        if (present > 0) record.addProperty("partySize", present);
    }

    private static long safeLootValue(int quantity, int unitValue)
    {
        if (quantity <= 0 || unitValue <= 0)
        {
            return 0L;
        }
        return Math.min(MAX_SAFE_JSON_INTEGER, (long) quantity * unitValue);
    }

    private static long safeLootAdd(long current, long addition)
    {
        return current >= MAX_SAFE_JSON_INTEGER - addition
            ? MAX_SAFE_JSON_INTEGER
            : current + addition;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE || !canCollectCurrentWorld())
        {
            return;
        }

        String playerName = currentPlayerName();
        if (playerName == null)
        {
            return;
        }

        String rawMessage = event.getMessage();
        String plainMessage = Text.removeTags(rawMessage);
        if (config.syncCompletionHistory())
        {
            int tick = client.getTickCount();
            bossRecordTracker.message(plainMessage, tick, RuneFolioBossRecordTracker.SOURCES);
            clueRecordTracker.message(plainMessage, tick);
            slayerRecordTracker.message(plainMessage, tick);
        }
        if (config.uploadScreenshots() && config.screenshotPets())
        {
            petTracker.message(plainMessage, client.getTickCount(), petNames());
        }
        if (plainMessage.startsWith(COLLECTION_LOG_TEXT))
        {
            String itemName = plainMessage.substring(COLLECTION_LOG_TEXT.length()).trim();
            if (!itemName.isEmpty())
            {
                if (enqueueLiveEvent(RuneFolioSyncEvent.collectionLogUnlock(playerName, itemName)))
                {
                    submit(syncExecutor, this::flushQueue);
                }
                if (config.uploadScreenshots() && config.screenshotCollectionLogUnlocks())
                {
                    requestScreenshot("collection_log", "Collection Log: " + itemName);
                }
            }
        }

        if (isDiaryTaskCompletionMessage(plainMessage))
        {
            diaryProgressRefreshPending = true;
            if (config.uploadScreenshots() && config.screenshotDiaryCompletions())
            {
                requestScreenshot("diary_task", plainMessage);
            }
        }

        Matcher combatAchievement = COMBAT_ACHIEVEMENT_PATTERN.matcher(rawMessage);
        if (combatAchievement.find())
        {
            String pointsText = combatAchievement.group("points");
            Integer points = pointsText == null ? null : Integer.parseInt(pointsText);
            enqueueLiveEvent(RuneFolioSyncEvent.combatAchievementUnlock(
                playerName,
                combatAchievement.group("tier").toLowerCase(Locale.ROOT),
                Text.removeTags(combatAchievement.group("task")).trim(),
                points
            ));

            combatProgressRefreshPending = true;
            if (config.uploadScreenshots() && config.screenshotCombatAchievements())
            {
                requestScreenshot("combat_achievement", "Combat Achievement: " + Text.removeTags(combatAchievement.group("task")).trim());
            }
        }

    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.QUESTSCROLL
            && client.getGameState() == GameState.LOGGED_IN)
        {
            questProgressRefreshPending = true;
        }

        if (config.uploadScreenshots()
            && ((config.screenshotLevelUps()
                && (event.getGroupId() == InterfaceID.LEVELUP_DISPLAY || event.getGroupId() == InterfaceID.OBJECTBOX))
                || (config.screenshotQuestCompletions() && event.getGroupId() == InterfaceID.QUESTSCROLL)))
        {
            interfaceScreenshotPending = true;
        }
    }

    private void capturePendingInterfaceScreenshot()
    {
        if (!interfaceScreenshotPending || !config.uploadScreenshots())
        {
            return;
        }
        interfaceScreenshotPending = false;

        if (config.screenshotLevelUps())
        {
            Widget levelText = client.getWidget(InterfaceID.LevelupDisplay.TEXT2);
            if (levelText == null)
            {
                levelText = client.getWidget(InterfaceID.Objectbox.TEXT);
            }
            if (levelText != null)
            {
                String text = Text.removeTags(levelText.getText());
                Matcher match = LEVEL_UP_PATTERN.matcher(text);
                if (match.matches())
                {
                    requestScreenshot("level_up", match.group(1) + " level " + match.group(2));
                    return;
                }
            }
        }

        if (config.screenshotQuestCompletions())
        {
            Widget questTitle = client.getWidget(InterfaceID.Questscroll.QUEST_TITLE);
            if (questTitle != null)
            {
                String text = Text.removeTags(questTitle.getText()).trim();
                if (QUEST_COMPLETION_PATTERN.matcher(text).matches())
                {
                    requestScreenshot("quest_completion", text);
                }
            }
        }
    }

    private void requestScreenshot(String category, String caption)
    {
        requestScreenshot(category, caption, UUID.randomUUID());
    }

    private void requestScreenshot(String category, String caption, UUID eventId)
    {
        if (!config.uploadScreenshots() || !canCollectCurrentWorld())
        {
            return;
        }
        String playerName = currentPlayerName();
        String savedToken = isAccountMode() ? accountConnectionToken : connectionToken;
        if (playerName == null || savedToken == null || savedToken.isBlank()
            || !namesMatch(playerName, connectedCharacterName))
        {
            return;
        }

        // Reserve before requesting a frame, including frame/client-thread callbacks.
        RuneFolioScreenshotQueue queue = screenshotQueue;
        RuneFolioScreenshotQueue.Reservation reservation = queue == null ? null : queue.tryReserve();
        if (reservation == null)
        {
            long now = System.currentTimeMillis();
            if (now - lastScreenshotQueueWarningMillis >= TimeUnit.MINUTES.toMillis(1))
            {
                lastScreenshotQueueWarningMillis = now;
                log.warn("RuneFolio screenshot queue is busy or stopped; skipping new screenshot. Progress sync is unaffected.");
            }
            return;
        }
        String identityKey = activeIdentityKey;
        long session = characterSession.get();
        String occurredAt = Instant.now().toString();
        boolean chatHidden = false;
        boolean privateMessagesHidden = false;
        Runnable frameCheck = null;
        try
        {
            chatHidden = hideScreenshotWidget(config.hideChatInScreenshots(), InterfaceID.Chatbox.CHATAREA);
            privateMessagesHidden = hideScreenshotWidget(config.hideChatInScreenshots(), InterfaceID.PmChat.CONTAINER);
            final boolean restoreChat = chatHidden;
            final boolean restorePrivateMessages = privateMessagesHidden;
            // DrawManager drops next-frame callbacks if its image supplier fails.
            // Its every-frame hook runs first; defer checking until the client
            // thread resumes, after the frame callbacks have had a chance to run.
            frameCheck = new Runnable()
            {
                @Override
                public void run()
                {
                    drawManager.unregisterEveryFrameListener(this);
                    clientThread.invokeLater(() ->
                    {
                        if (reservation.cancelIfFrameMissing())
                        {
                            restoreScreenshotWidget(restoreChat, InterfaceID.Chatbox.CHATAREA);
                            restoreScreenshotWidget(restorePrivateMessages, InterfaceID.PmChat.CONTAINER);
                        }
                    });
                }
            };
            drawManager.registerEveryFrameListener(frameCheck);
            drawManager.requestNextFrameListener(frame ->
            {
                try
                {
                    restoreScreenshotWidget(restoreChat, InterfaceID.Chatbox.CHATAREA);
                    restoreScreenshotWidget(restorePrivateMessages, InterfaceID.PmChat.CONTAINER);
                    if (!reservation.markFrameReceived()) return;
                    clientThread.invokeLater(() ->
                    {
                        try
                        {
                            if (running && session == characterSession.get() && canCollectCurrentWorld()
                                && namesMatch(playerName, currentPlayerName()))
                            {
                                saveScreenshotAsync(reservation, savedToken, playerName, eventId,
                                    identityKey, category, caption, occurredAt, frame);
                            }
                        }
                        finally
                        {
                            reservation.cancel();
                        }
                    });
                }
                catch (RuntimeException exception)
                {
                    reservation.cancel();
                    log.warn("RuneFolio could not schedule a screenshot");
                }
            });
        }
        catch (RuntimeException exception)
        {
            reservation.cancel();
            if (frameCheck != null) drawManager.unregisterEveryFrameListener(frameCheck);
            restoreScreenshotWidget(chatHidden, InterfaceID.Chatbox.CHATAREA);
            restoreScreenshotWidget(privateMessagesHidden, InterfaceID.PmChat.CONTAINER);
            log.warn("RuneFolio could not request a screenshot frame");
        }
    }

    private boolean hideScreenshotWidget(boolean shouldHide, int componentId)
    {
        if (!shouldHide)
        {
            return false;
        }
        Widget widget = client.getWidget(componentId);
        if (widget == null || widget.isHidden())
        {
            return false;
        }
        widget.setHidden(true);
        return true;
    }

    private void restoreScreenshotWidget(boolean shouldRestore, int componentId)
    {
        if (!shouldRestore)
        {
            return;
        }
        clientThread.invokeLater(() ->
        {
            Widget widget = client.getWidget(componentId);
            if (widget != null)
            {
                widget.setHidden(false);
            }
        });
    }

    private void saveScreenshotAsync(
        RuneFolioScreenshotQueue.Reservation reservation,
        String token,
        String playerName,
        UUID eventId,
        String identityKey,
        String category,
        String caption,
        String occurredAt,
        Image frame
    )
    {
        RuneFolioScreenshotSpool spool = screenshotSpool;
        if (spool == null)
        {
            return;
        }
        reservation.submit(() ->
        {
            try
            {
                byte[] jpeg = RuneFolioScreenshotEncoder.encode(frame);
                if (jpeg.length > SCREENSHOT_MAX_BYTES)
                {
                    log.warn("RuneFolio screenshot was too large to upload: {} bytes", jpeg.length);
                    return;
                }
                RuneFolioScreenshotSpool.Entry entry = new RuneFolioScreenshotSpool.Entry(
                    eventId, token, playerName, identityKey, category, caption, occurredAt);
                if (!spool.save(entry, jpeg))
                {
                    log.warn("RuneFolio local screenshot storage is full or busy; newest screenshot was not saved");
                    SwingUtilities.invokeLater(() -> panel.setScreenshotQueueState("Storage full or busy - see log"));
                }
            }
            catch (IOException | RuntimeException exception)
            {
                log.warn("RuneFolio could not save a screenshot locally; progress sync is unaffected");
                SwingUtilities.invokeLater(() -> panel.setScreenshotQueueState("Save failed - see log"));
            }
        });
    }

    private List<String> screenshotConnectionTokens()
    {
        String account = accountConnectionToken;
        if (account != null && !account.isBlank()) return List.of(account);
        String temporary = connectionToken;
        return temporary == null || temporary.isBlank() ? List.of() : List.of(temporary);
    }

    private void drainScreenshotSpool()
    {
        RuneFolioScreenshotSpool spool = screenshotSpool;
        if (spool == null || !running)
        {
            return;
        }
        try
        {
            if (clearScreenshotSpoolRequested.getAndSet(false))
            {
                boolean cleared = spool.clear();
                SwingUtilities.invokeLater(() -> panel.setStatus(cleared
                    ? "Local screenshot queue cleared. Website images were not changed."
                    : "A screenshot operation is active in another client. Try clearing again shortly."));
            }
            spool.drainOnce(this::screenshotConnectionTokens, (token, entry, jpeg) ->
                RuneFolioApiClient.uploadScreenshot(okHttpClient, token, entry.characterName, UUID.fromString(entry.eventId),
                    entry.identityKey, entry.category, entry.caption, entry.occurredAt, jpeg));
            RuneFolioScreenshotSpool.Stats stats = spool.stats();
            if (stats != null && running)
            {
                String status = stats.saved + " saved" + (stats.held == 0 ? "" : ", " + stats.held + " held");
                SwingUtilities.invokeLater(() -> panel.setScreenshotQueueState(status));
            }
        }
        catch (IOException | RuntimeException exception)
        {
            if (!running)
            {
                return;
            }
            log.warn("RuneFolio screenshot outbox unavailable; saved files were retained");
            SwingUtilities.invokeLater(() -> panel.setScreenshotQueueState("Upload paused - check log"));
        }
    }

    static boolean isDiaryTaskCompletionMessage(String message)
    {
        return message != null && DIARY_TASK_COMPLETION_PATTERN.matcher(message.trim()).matches();
    }

    private void enqueuePendingProgressSnapshots()
    {
        if (!questProgressRefreshPending && !diaryProgressRefreshPending && !combatProgressRefreshPending)
        {
            return;
        }

        String playerName = currentPlayerName();
        if (playerName == null || playerName.isBlank())
        {
            return;
        }

        boolean queued = false;
        if (questProgressRefreshPending)
        {
            questProgressRefreshPending = false;
            JsonObject questState = RuneFolioProgressCollector.quests(client);
            lastKnownQuestState = questState.deepCopy();
            queued |= enqueueLiveEvent(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE,
                playerName,
                "event",
                questState
            ));
        }
        if (diaryProgressRefreshPending)
        {
            diaryProgressRefreshPending = false;
            JsonObject diaryState = RuneFolioProgressCollector.diaries(client);
            lastKnownDiaryState = diaryState.deepCopy();
            queued |= enqueueLiveEvent(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.DIARY_SNAPSHOT_TYPE,
                playerName,
                "event",
                diaryState
            ));
        }
        if (combatProgressRefreshPending)
        {
            combatProgressRefreshPending = false;
            JsonObject combatState = RuneFolioProgressCollector.combatAchievements(client);
            lastKnownCombatState = combatState.deepCopy();
            queued |= enqueueLiveEvent(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.COMBAT_SNAPSHOT_TYPE,
                playerName,
                "event",
                combatState
            ));
        }

        if (queued)
        {
            submit(syncExecutor, this::flushQueue);
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (!collectionButtonSyncRequested || event.getScriptId() != COLLECTION_LOG_TRANSMIT_SCRIPT)
        {
            return;
        }
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
        {
            cancelCollectionButtonSync("RuneFolio cannot sync another player's Collection Log.");
            return;
        }

        Object[] arguments = event.getScriptEvent().getArguments();
        if (arguments == null || arguments.length < 3
            || !(arguments[1] instanceof Integer) || !(arguments[2] instanceof Integer))
        {
            return;
        }

        int itemId = normalizeCollectionLogItemId((Integer) arguments[1]);
        int quantity = Math.max(0, (Integer) arguments[2]);
        if (itemId > 0)
        {
            collectionButtonItems.merge(itemId, quantity, Math::max);
            lastCollectionTransmitTick = client.getTickCount();
        }
    }

    static int normalizeCollectionLogItemId(int itemId)
    {
        switch (itemId)
        {
            case 29472: return 12013; // Prospector helmet
            case 29474: return 12014; // Prospector jacket
            case 29476: return 12015; // Prospector legs
            case 29478: return 12016; // Prospector boots
            case 10859: return 25617; // Tea flask
            case 10877: return 25618; // Plain satchel
            case 10878: return 25619; // Green satchel
            case 10879: return 25620; // Red satchel
            case 10880: return 25621; // Black satchel
            case 10881: return 25622; // Gold satchel
            case 10882: return 25623; // Rune satchel
            case 13273: return 25624; // Unsired
            case 12019: return 25627; // Coal bag
            case 12020: return 25628; // Gem bag
            case 24882: return 25629; // Plank sack
            case 12854: return 25630; // Flamtaer bag
            case 29990: return 29992; // Alchemist's amulet
            case 30803: return 30805; // Dossier
            default: return itemId;
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == ScriptID.NOTIFICATION_DELAY && canCollectCurrentWorld()
            && config.uploadScreenshots() && config.screenshotPets()
            && "Collection log".equalsIgnoreCase(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE)))
        {
            String message = client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN);
            if (message != null)
            {
                String text = Text.removeTags(message).trim();
                if (text.startsWith("New item:"))
                    petTracker.observeName(text.substring("New item:".length()).trim(), client.getTickCount(), petNames());
            }
        }
        if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST
            || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        String playerName = currentPlayerName();
        JsonObject categoryState = RuneFolioProgressCollector.collectionLogCategory(client, itemManager);
        if (playerName == null || categoryState == null)
        {
            return;
        }

        enqueueLiveEvent(RuneFolioSyncEvent.progressSnapshot(
            RuneFolioSyncEvent.COLLECTION_CATEGORY_TYPE,
            playerName,
            "interface",
            categoryState
        ));
    }

    private void requestCollectionLogSyncFromButton()
    {
        String playerName = currentPlayerName();
        boolean accountMode = isAccountMode();
        String savedToken = accountMode ? accountConnectionToken : connectionToken;
        if (playerName == null || playerName.isBlank())
        {
            addRuneFolioChatMessage("<col=d67966>Log in to a character before updating RuneFolio.</col>");
            return;
        }
        if (savedToken == null || savedToken.isBlank()
            || !namesMatch(playerName, connectedCharacterName))
        {
            addRuneFolioChatMessage("<col=d67966>Connect this character to RuneFolio before updating.</col>");
            return;
        }
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
        {
            addRuneFolioChatMessage("<col=d67966>RuneFolio cannot sync another player's Collection Log.</col>");
            return;
        }

        int currentTick = client.getTickCount();
        if (lastCollectionButtonClickTick >= 0
            && lastCollectionButtonClickTick + COLLECTION_SYNC_COOLDOWN_TICKS > currentTick)
        {
            int seconds = Math.max(1, Math.round(
                (lastCollectionButtonClickTick + COLLECTION_SYNC_COOLDOWN_TICKS - currentTick) * 0.6f
            ));
            addRuneFolioChatMessage("Please wait " + seconds + " seconds before updating again.");
            return;
        }

        lastCollectionButtonClickTick = currentTick;
        collectionButtonItems.clear();
        collectionButtonSyncRequested = true;
        lastCollectionTransmitTick = -1;
        addRuneFolioChatMessage("Updating your RuneFolio Collection Log...");
        client.menuAction(
            -1,
            InterfaceID.Collection.SEARCH_TOGGLE,
            MenuAction.CC_OP,
            1,
            -1,
            "Search",
            null
        );
        client.runScript(COLLECTION_LOG_INIT_SCRIPT);
    }

    private boolean requestDiaryTaskSync()
    {
        String playerName = currentPlayerName();
        if (playerName == null || playerName.isBlank())
        {
            return false;
        }

        JsonObject diaryState = RuneFolioProgressCollector.diaries(client);
        lastKnownDiaryState = diaryState.deepCopy();
        RuneFolioSyncEvent event = RuneFolioSyncEvent.progressSnapshot(
            RuneFolioSyncEvent.DIARY_SNAPSHOT_TYPE,
            playerName,
            "interface",
            diaryState
        );
        boolean queued = enqueueLiveEvent(event);
        if (queued)
        {
            submit(syncExecutor, this::flushQueue);
        }
        return queued;
    }

    private void finishCollectionButtonSyncIfReady()
    {
        if (!collectionButtonSyncRequested)
        {
            return;
        }
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) != 0)
        {
            cancelCollectionButtonSync("RuneFolio cannot sync another player's Collection Log.");
            return;
        }
        if (lastCollectionTransmitTick < 0)
        {
            if (lastCollectionButtonClickTick + 10 < client.getTickCount())
            {
                cancelCollectionButtonSync("RuneFolio could not read the Collection Log. Please reopen it and try again.");
            }
            return;
        }
        if (lastCollectionTransmitTick + 2 >= client.getTickCount())
        {
            return;
        }

        String playerName = currentPlayerName();
        if (playerName == null || collectionButtonItems.isEmpty())
        {
            cancelCollectionButtonSync("RuneFolio could not read the Collection Log. Please reopen it and try again.");
            return;
        }

        JsonArray items = new JsonArray();
        int obtainedCount = 0;
        for (Map.Entry<Integer, Integer> item : collectionButtonItems.entrySet())
        {
            int quantity = item.getValue();
            JsonObject state = new JsonObject();
            state.addProperty("itemId", item.getKey());
            state.addProperty("itemName", itemManager.getItemComposition(item.getKey()).getName());
            state.addProperty("obtained", quantity > 0);
            state.addProperty("quantity", quantity);
            items.add(state);
            if (quantity > 0)
            {
                obtainedCount++;
            }
        }

        JsonObject fullLog = new JsonObject();
        fullLog.addProperty("category", "Full Collection Log");
        fullLog.addProperty("obtainedCount", obtainedCount);
        fullLog.addProperty("totalCount", items.size());
        fullLog.add("items", items);
        RuneFolioSyncEvent syncEvent = RuneFolioSyncEvent.progressSnapshot(
            RuneFolioSyncEvent.COLLECTION_CATEGORY_TYPE,
            playerName,
            "manual",
            fullLog
        );

        collectionButtonSyncRequested = false;
        lastCollectionTransmitTick = -1;
        collectionButtonItems.clear();
        if (!enqueueLiveEvent(syncEvent))
        {
            addRuneFolioChatMessage("<col=d67966>The Collection Log could not be queued. RuneFolio will keep the existing data.</col>");
            return;
        }

        pendingCollectionButtonEventIds.add(syncEvent.getId());
        submit(syncExecutor, this::flushQueue);
    }

    private void cancelCollectionButtonSync(String message)
    {
        collectionButtonSyncRequested = false;
        lastCollectionTransmitTick = -1;
        collectionButtonItems.clear();
        addRuneFolioChatMessage("<col=d67966>" + message + "</col>");
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        // Cancel immediately: returning to our own book must not resume a stale capture.
        if (collectionButtonSyncRequested
            && client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) != 0)
        {
            cancelCollectionButtonSync("RuneFolio cannot sync another player's Collection Log.");
        }
    }

    private void addRuneFolioChatMessage(String message)
    {
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            "<col=d9b861>RuneFolio:</col> " + message,
            null
        );
    }

    private boolean enqueueIdentifiedEvent(RuneFolioSyncEvent event)
    {
        RuneFolioSyncQueue queue = syncQueue;
        if (queue == null) return false;
        if (namesMatch(event.getCharacterName(), lastKnownPlayerName)) event.withIdentityKey(activeIdentityKey);
        return queue.enqueue(event);
    }

    private void forgetTemporaryToken(String token, String configKey, boolean legacyToken, String identityKey)
    {
        if (configKey != null)
        {
            configManager.unsetConfiguration(CONFIG_GROUP, configKey);
        }
        if (legacyToken)
        {
            configManager.unsetConfiguration(CONFIG_GROUP, LEGACY_CONNECTION_TOKEN_KEY);
        }
        if (identityKey != null
            && token.equals(configManager.getConfiguration(CONFIG_GROUP, "identityToken." + identityKey)))
        {
            configManager.unsetConfiguration(CONFIG_GROUP, "identityToken." + identityKey);
        }
    }

    private String previousIdentityName(String key, String fallback)
    {
        if (key == null) return fallback;
        String saved = configManager.getConfiguration(CONFIG_GROUP, "identityName." + key);
        return saved == null || saved.isBlank() ? fallback : saved;
    }

    private void rememberIdentity(String key, String name, String temporaryToken)
    {
        if (key == null) return;
        configManager.setConfiguration(CONFIG_GROUP, "identityName." + key, name);
        if (temporaryToken != null)
            configManager.setConfiguration(CONFIG_GROUP, "identityToken." + key, temporaryToken);
    }

    private boolean enqueueLiveEvent(RuneFolioSyncEvent event)
    {
        if (!canCollectCurrentWorld())
        {
            return false;
        }
        boolean accountMode = isAccountMode();
        String savedToken = accountMode ? accountConnectionToken : connectionToken;
        String playerName = event.getCharacterName();
        if (savedToken == null || savedToken.isBlank()
            || (accountMode && namesMatch(playerName, pendingCharacterSetupName))
            || !namesMatch(playerName, connectedCharacterName))
        {
            return false;
        }

        if (!enqueueIdentifiedEvent(event))
        {
            String status = queueFullStatus();
            SwingUtilities.invokeLater(() -> panel.setStatus(status));
            return false;
        }

        refreshSyncPanel();
        return true;
    }

    private void requestLogoutSkillSync()
    {
        boolean accountMode = isAccountMode();
        String savedToken = accountMode ? accountConnectionToken : connectionToken;
        String playerName = lastKnownPlayerName;
        enqueueKnownSnapshots("logout");
        if (savedToken == null || savedToken.isBlank())
        {
            return;
        }

        submit(syncExecutor, () -> flushQueueWithToken(
            savedToken,
            accountMode ? null : playerName,
            accountMode
        ));
    }

    private List<RuneFolioApiClient.SkillSnapshot> collectSkillSnapshot()
    {
        List<RuneFolioApiClient.SkillSnapshot> skills = new ArrayList<>();
        for (Skill skill : Skill.values())
        {
            skills.add(new RuneFolioApiClient.SkillSnapshot(
                skill.getName(),
                client.getRealSkillLevel(skill),
                client.getSkillExperience(skill)
            ));
        }
        return skills;
    }

    /** Reads client state on the calling (client) thread, then updates the panel on the EDT. */
    private void refreshPanel()
    {
        String playerName = currentPlayerName();
        boolean accountMode = isAccountMode();
        String token = connectionToken;
        String linkedCharacter = connectedCharacterName;
        SwingUtilities.invokeLater(() ->
        {
            panel.setCharacterName(playerName);
            panel.setAccountConnected(accountMode);

            if (playerName == null || playerName.isBlank())
            {
                panel.setStatus(accountMode
                    ? "Connected to your RuneFolio account. Log in to a character to sync."
                    : "Log in to RuneLite, then connect your RuneFolio account or enter a temporary code.");
                return;
            }

            if (accountMode)
            {
                if (linkedCharacter == null)
                {
                    panel.setStatus("Checking your RuneFolio account connection...");
                }
                else
                {
                    showConnectionStatus(playerName, linkedCharacter);
                }
                return;
            }

            if (token == null || token.isBlank())
            {
                panel.setStatus(playerName + " is not connected to RuneFolio. Enter a temporary code for this character.");
                return;
            }

            showConnectionStatus(playerName, linkedCharacter);
        });
    }

    private void showConnectionStatus(String playerName, String linkedCharacter)
    {
        if (linkedCharacter == null || linkedCharacter.isBlank())
        {
            panel.setStatus("Checking " + playerName + "'s RuneFolio connection...");
        }
        else if (!namesMatch(playerName, linkedCharacter))
        {
            panel.setStatus("Character mismatch. RuneLite is logged into " + playerName + ". No data was sent.");
        }
        else
        {
            panel.setStatus("Connected to " + playerName + ". Automatic sync is ready.");
        }
    }

    private void deactivateCurrentCharacter()
    {
        resetTransientCharacterState();
        connectionToken = null;
        connectedCharacterName = null;
        activeConnectionConfigKey = null;
        activeConnectionIsLegacy = false;
        connectionLookupPending = false;
        loginSyncPending = false;
        pendingCharacterSetupName = null;
        pendingCharacterSetupUrl = null;
        promptedCharacterName = null;
        setupPollCharacter = null;
        hideCharacterSetup();
        lastKnownPlayerName = null;
        activeIdentityKey = null;
        lastKnownSkills = new ArrayList<>();
        lastKnownQuestState = null;
        lastKnownDiaryState = null;
        lastKnownCombatState = null;
        questProgressRefreshPending = false;
        diaryProgressRefreshPending = false;
        combatProgressRefreshPending = false;
        lastSuccessfullySyncedCharacterName = null;
        ticksSinceLocalSnapshot = 0;
        refreshSyncPanel();
    }

    void resetTransientCharacterState()
    {
        finishPvpResults();
        pvpTracker.reset(); pvpHits.clear(); pvpDeaths.clear(); lootKeyScreenshotTick = -100; lootKeyScreenshotId = null;
        bossRecordTracker.reset();
        clueRecordTracker.reset();
        slayerRecordTracker.reset();
        petTracker.reset();
        characterSession.incrementAndGet();
        collectionButtonSyncRequested = false;
        collectionButtonItems.clear();
        lastCollectionTransmitTick = -1;
        lastCollectionButtonClickTick = -1;
        interfaceScreenshotPending = false;
    }

    private void clearAccountConnection()
    {
        accountConnectionToken = null;
        lastSuccessfullySyncedCharacterName = null;
        configManager.unsetConfiguration(CONFIG_GROUP, ACCOUNT_CONNECTION_TOKEN_KEY);
        connectedCharacterName = null;
        pendingCharacterSetupName = null;
        pendingCharacterSetupUrl = null;
        setupPollCharacter = null;
        loginSyncPending = false;
        hideCharacterSetup();
    }

    private void hideCharacterSetup()
    {
        RuneFolioPanel currentPanel = panel;
        if (currentPanel != null)
        {
            SwingUtilities.invokeLater(currentPanel::hideCharacterSetup);
        }
    }

    private boolean isAccountMode()
    {
        return accountConnectionToken != null && !accountConnectionToken.isBlank();
    }

    private static String connectionTokenKey(String playerName)
    {
        String normalisedName = normaliseName(playerName);
        String encodedName = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(normalisedName.getBytes(StandardCharsets.UTF_8));
        return CHARACTER_CONNECTION_TOKEN_PREFIX + encodedName;
    }

    private static boolean namesMatch(String first, String second)
    {
        return RuneFolioNameChange.namesMatch(first, second);
    }

    private static String normaliseName(String value)
    {
        return RuneFolioNameChange.normaliseName(value);
    }

    private String currentPlayerName()
    {
        Player player = client.getLocalPlayer();
        return player == null ? null : player.getName();
    }

    private Set<String> petNames()
    {
        if (knownPetNames.isEmpty())
        {
            // Canonical All Pets Collection Log IDs, synchronized with the website catalog.
            for (int id : new int[] {13262, 22746, 13178, 13247, 11995, 12651, 12816, 12644, 12643, 12645, 13225, 12650, 12646, 21748, 21291, 12647, 12653, 12655, 12649, 12652, 13181, 21273, 12648, 13177, 13179, 21992, 20693, 12921, 20851, 22473, 19730, 12703, 13320, 13321, 13322, 13324, 20659, 20661, 20663, 20665, 21509, 13071, 23495, 23760, 23757, 24491, 25348, 25602, 26348, 26901, 27352, 27590, 28246, 28250, 28248, 28252, 28801, 28960, 28962, 29836, 30152, 30154, 30622, 30888, 31130, 31283, 31285, 33124, 33642, 34040, 34042})
            {
                String name = itemManager.getItemComposition(id).getName();
                if (name != null && !name.isBlank()) knownPetNames.add(name);
            }
        }
        return knownPetNames;
    }

    private boolean canCollectCurrentWorld()
    {
        return client.getGameState() == GameState.LOGGED_IN
            && RuneFolioWorldPolicy.supports(client.getWorldType());
    }

    private void refreshSyncPanel()
    {
        RuneFolioSyncQueue queue = syncQueue;
        RuneFolioPanel currentPanel = panel;
        if (currentPanel == null)
        {
            return;
        }
        int pendingEvents = queue == null ? 0 : queue.size();
        SwingUtilities.invokeLater(() ->
            currentPanel.setSyncState(lastSuccessfulSyncAtMillis, pendingEvents)
        );
    }

    private long savedLong(String key)
    {
        String value = configManager.getConfiguration(CONFIG_GROUP, key);
        if (value == null || value.isBlank())
        {
            return 0L;
        }
        try
        {
            return Long.parseLong(value);
        }
        catch (NumberFormatException exception)
        {
            configManager.unsetConfiguration(CONFIG_GROUP, key);
            return 0L;
        }
    }

    private static String safeMessage(Exception exception)
    {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Please try again." : message;
    }

}
