package app.runefolio.sync;

import com.google.inject.Provides;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Image;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import net.runelite.client.callback.ClientThread;
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

@Slf4j
@PluginDescriptor(
    name = "RuneFolio Sync",
    description = "Private-alpha RuneFolio character connection client",
    tags = {"runefolio", "progress", "tracker", "loot"}
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
        "^Well done! You have completed an? (?:easy|medium|hard|elite) task in the .+ area\\.?$",
        Pattern.CASE_INSENSITIVE
    );

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private RuneFolioPanel panel;

    @Inject
    private RuneFolioConfig config;

    @Inject
    private RuneFolioCollectionLogButton collectionLogButton;

    @Inject
    private RuneFolioDiaryTaskTracker diaryTaskTracker;

    @Inject
    private ItemManager itemManager;

    @Inject
    private DrawManager drawManager;

    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor();
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
    private final java.util.concurrent.atomic.AtomicLong characterSession = new java.util.concurrent.atomic.AtomicLong();
    private volatile List<RuneFolioApiClient.SkillSnapshot> lastKnownSkills = new ArrayList<>();
    private volatile JsonObject lastKnownQuestState;
    private volatile JsonObject lastKnownDiaryState;
    private volatile JsonObject lastKnownCombatState;
    private volatile long lastSuccessfulSyncAtMillis;
    private volatile String lastSuccessfullySyncedCharacterName;
    private boolean connectionLookupPending;
    private boolean loginSyncPending;
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
    private final Set<String> knownPetNames = new HashSet<>();

    @Provides
    RuneFolioConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(RuneFolioConfig.class);
    }

    @Override
    protected void startUp()
    {
        navigationButton = NavigationButton.builder()
            .tooltip("RuneFolio Sync")
            .icon(RuneFolioBrand.createIcon(16))
            .priority(8)
            .panel(panel)
            .build();

        refreshNavigationButton();
        collectionLogButton.startUp(this::requestCollectionLogSyncFromButton);
        diaryTaskTracker.startUp(this::requestDiaryTaskSync);
        panel.setTemporaryConnectAction(this::connectTemporaryCode);
        panel.setAccountConnectAction(this::connectRuneFolioAccount);
        panel.setAccountDisconnectAction(this::disconnectRuneFolioAccount);
        panel.setCharacterSetupAction(this::openCharacterSetup);
        panel.setManualSyncAction(() -> requestFullSync("manual"));

        syncQueue = new RuneFolioSyncQueue(configManager);
        lastSuccessfulSyncAtMillis = savedLong(LAST_SUCCESSFUL_SYNC_KEY);
        accountConnectionToken = configManager.getConfiguration(CONFIG_GROUP, ACCOUNT_CONNECTION_TOKEN_KEY);
        panel.setAccountConnected(isAccountMode());
        refreshSyncPanel();
        refreshPanel();

        if (client.getGameState() == GameState.LOGGED_IN)
        {
            connectionLookupPending = true;
        }

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

        log.info("RuneFolio Sync started");
        syncExecutor.scheduleAtFixedRate(() -> {
            if ((!isAccountMode() && connectionToken == null) || System.currentTimeMillis() < nextManifestRefreshMillis) return;
            nextManifestRefreshMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
            try
            {
                RuneFolioCollectorManifest manifest = RuneFolioCollectorManifest.fetch();
                if (manifest != null)
                {
                    clientThread.invokeLater(() -> RuneFolioCollectorManifest.install(manifest));
                    nextManifestRefreshMillis = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
                }
            }
            catch (java.io.IOException unavailable) { log.debug("Collector manifest unavailable; retaining current catalog"); }
        }, 1, 5, TimeUnit.MINUTES);
    }

    @Override
    protected void shutDown()
    {
        collectionLogButton.shutDown();
        collectionButtonSyncRequested = false;
        collectionButtonItems.clear();
        pendingCollectionButtonEventIds.clear();
        String playerName = lastKnownPlayerName;
        boolean accountMode = isAccountMode();
        String savedToken = accountMode ? accountConnectionToken : connectionToken;
        enqueueKnownSnapshots("shutdown");
        diaryTaskTracker.shutDown();
        if (savedToken != null && !savedToken.isBlank())
        {
            syncExecutor.submit(() -> flushQueueWithToken(
                savedToken,
                accountMode ? null : playerName,
                accountMode
            ));
        }

        if (navigationButton != null && navigationButtonAdded)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButtonAdded = false;
        }
        navigationButton = null;
        connectionExecutor.shutdownNow();
        screenshotExecutor.shutdownNow();
        syncExecutor.shutdown();
        try
        {
            if (!syncExecutor.awaitTermination(3, TimeUnit.SECONDS))
            {
                syncExecutor.shutdownNow();
            }
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            syncExecutor.shutdownNow();
        }
        log.info("RuneFolio Sync stopped");
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (CONFIG_GROUP.equals(event.getGroup()) && "hideSidePanel".equals(event.getKey()))
        {
            refreshNavigationButton();
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
            clientToolbar.addNavigation(navigationButton);
            navigationButtonAdded = true;
        }
        else if (!shouldShow && navigationButtonAdded)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButtonAdded = false;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            ticksSinceLocalSnapshot = 10;
            if (worldHopInProgress || lastKnownPlayerName != null)
            {
                worldHopInProgress = false;
                refreshPanel();
            }
            else
            {
                connectionLookupPending = true;
                refreshPanel();
            }
        }
        else if (event.getGameState() == GameState.HOPPING)
        {
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
            panel.setCharacterName(null);
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

        connectionExecutor.submit(() ->
        {
            try
            {
                RuneFolioApiClient.AccountLoginRequest login =
                    RuneFolioApiClient.startAccountLogin();
                SwingUtilities.invokeLater(() ->
                {
                    panel.openBrowser(login.getVerificationUrl());
                    panel.setStatus("Approve the connection in your browser. RuneLite is waiting...");
                });

                for (int attempt = 0; attempt < 300 && !Thread.currentThread().isInterrupted(); attempt++)
                {
                    Thread.sleep(2_000);
                    RuneFolioApiClient.AccountPollResult result = RuneFolioApiClient.pollAccountLogin(
                        login.getRequestId(),
                        login.getPollToken()
                    );
                    if (!result.isApproved())
                    {
                        continue;
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
                    clientThread.invokeLater(() ->
                    {
                        if (currentPlayerName() != null)
                        {
                            connectionLookupPending = true;
                        }
                    });
                    return;
                }

                SwingUtilities.invokeLater(() ->
                {
                    panel.setAccountConnecting(false);
                    panel.setStatus("Browser approval expired. Click Log in to RuneFolio to try again.");
                });
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
            catch (Exception exception)
            {
                log.warn("RuneFolio account connection failed", exception);
                SwingUtilities.invokeLater(() ->
                {
                    panel.setAccountConnecting(false);
                    panel.setStatus("Account connection failed: " + safeMessage(exception));
                });
            }
        });
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

        connectionExecutor.submit(() ->
        {
            try
            {
                RuneFolioApiClient.disconnectAccount(savedToken);
                clearAccountConnection();
                SwingUtilities.invokeLater(() ->
                {
                    panel.setAccountConnecting(false);
                    panel.setAccountConnected(false);
                    panel.setStatus("RuneFolio account disconnected. Temporary character codes are still available.");
                });
                clientThread.invokeLater(() ->
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

        String playerName = currentPlayerName();
        if (playerName == null)
        {
            panel.setStatus("Log in to the linked RuneScape character before connecting.");
            return;
        }

        panel.setConnecting(true);
        panel.setStatus("Verifying temporary character code...");
        long session = characterSession.get();

        connectionExecutor.submit(() ->
        {
            try
            {
                RuneFolioApiClient.ConnectionResult result = RuneFolioApiClient.exchange(
                    code,
                    playerName,
                    "RuneLite · " + playerName
                );
                String configKey = connectionTokenKey(playerName);
                configManager.setConfiguration(CONFIG_GROUP, configKey, result.getConnectionToken());
                if (session != characterSession.get())
                {
                    return;
                }
                connectionToken = result.getConnectionToken();
                connectedCharacterName = result.getCharacterName();
                activeConnectionConfigKey = configKey;
                activeConnectionIsLegacy = false;
                configManager.setConfiguration(CONFIG_GROUP, configKey, connectionToken);
                SwingUtilities.invokeLater(() ->
                {
                    panel.clearCode();
                    panel.setConnecting(false);
                    panel.setCharacterName(playerName);
                    showConnectionStatus(playerName, result.getCharacterName());
                    requestFullSync("login");
                });
            }
            catch (Exception exception)
            {
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
        });
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
        if (savedToken == null || savedToken.isBlank() || playerName == null || playerName.isBlank())
        {
            return;
        }

        connectionExecutor.submit(() ->
        {
            try
            {
                RuneFolioApiClient.ConnectionResult result = RuneFolioApiClient.heartbeat(savedToken);
                if (session != characterSession.get() || !savedToken.equals(connectionToken))
                {
                    return;
                }
                String linkedCharacter = result.getCharacterName();
                if (!namesMatch(playerName, linkedCharacter))
                {
                    if (!legacyToken && configKey != null)
                    {
                        configManager.unsetConfiguration(CONFIG_GROUP, configKey);
                    }
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
                if (session != characterSession.get() || !savedToken.equals(connectionToken))
                {
                    return;
                }
                handleTemporaryConnectionFailure(savedToken, playerName, configKey, legacyToken, exception);
            }
        });
    }

    private void verifyAccountConnection()
    {
        String savedToken = accountConnectionToken;
        String playerName = lastKnownPlayerName;
        long session = characterSession.get();
        if (savedToken == null || savedToken.isBlank())
        {
            return;
        }

        connectionExecutor.submit(() ->
        {
            try
            {
                RuneFolioApiClient.AccountHeartbeatResult heartbeat =
                    RuneFolioApiClient.accountHeartbeat(savedToken, playerName);
                boolean characterConnected = heartbeat.isCharacterConnected();
                if (session != characterSession.get() || !savedToken.equals(accountConnectionToken))
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
                if (session != characterSession.get() || !savedToken.equals(accountConnectionToken))
                {
                    return;
                }
                log.warn("Saved RuneFolio account connection check failed", exception);
                String lowerMessage = message.toLowerCase();
                if (lowerMessage.contains("revoked") || lowerMessage.contains("expired"))
                {
                    clearAccountConnection();
                    SwingUtilities.invokeLater(() ->
                    {
                        panel.setAccountConnected(false);
                        panel.hideCharacterSetup();
                        panel.setStatus("RuneFolio account connection revoked. Log in again or use a temporary code.");
                    });
                    clientThread.invokeLater(() ->
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
        clientThread.invokeLater(() -> client.addChatMessage(
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
        scheduleCharacterSetupCheck(playerName, 0);
    }

    private void scheduleCharacterSetupCheck(String playerName, int attempt)
    {
        long session = characterSession.get();
        String token = accountConnectionToken;
        syncExecutor.schedule(() ->
        {
            if (!isAccountMode()
                || !namesMatch(playerName, setupPollCharacter)
                || !namesMatch(playerName, lastKnownPlayerName))
            {
                return;
            }

            try
            {
                RuneFolioApiClient.AccountHeartbeatResult heartbeat =
                    RuneFolioApiClient.accountHeartbeat(token, playerName);
                if (session != characterSession.get() || !java.util.Objects.equals(token, accountConnectionToken))
                {
                    return;
                }
                if (heartbeat.isCharacterConnected())
                {
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
                log.debug("Waiting for RuneFolio character setup", exception);
            }

            if (attempt < 149)
            {
                scheduleCharacterSetupCheck(playerName, attempt + 1);
            }
        }, 2, TimeUnit.SECONDS);
    }

    private void handleTemporaryConnectionFailure(
        String savedToken,
        String playerName,
        String configKey,
        boolean legacyToken,
        Exception exception
    )
    {
        String message = safeMessage(exception);
        log.warn("Saved RuneFolio connection check failed", exception);
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("revoked") || lowerMessage.contains("expired"))
        {
            if (configKey != null)
            {
                configManager.unsetConfiguration(CONFIG_GROUP, configKey);
            }
            if (legacyToken)
            {
                configManager.unsetConfiguration(CONFIG_GROUP, LEGACY_CONNECTION_TOKEN_KEY);
            }
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
            connectedCharacterName = playerName;
            loginSyncPending = true;
            panel.setStatus("Checking your RuneFolio account connection...");
            verifyAccountConnection();
            return;
        }

        String configKey = connectionTokenKey(playerName);
        String savedToken = configManager.getConfiguration(CONFIG_GROUP, configKey);
        boolean legacyToken = false;

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
            panel.setStatus(playerName + " is not connected to RuneFolio. Enter a temporary code for this character.");
            return;
        }

        connectionToken = savedToken;
        loginSyncPending = true;
        panel.setStatus("Checking " + playerName + "'s RuneFolio connection...");
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

        clientThread.invokeLater(() ->
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
            JsonObject diaryState = RuneFolioProgressCollector.diaries(
                client,
                diaryTaskTracker.taskAreasForCurrentCharacter()
            );
            JsonObject combatState = RuneFolioProgressCollector.combatAchievements(client);
            lastKnownQuestState = questState.deepCopy();
            lastKnownDiaryState = diaryState.deepCopy();
            lastKnownCombatState = combatState.deepCopy();

            String linkedCharacter = connectedCharacterName;
            if (!accountMode && (linkedCharacter == null || !namesMatch(playerName, linkedCharacter)))
            {
                SwingUtilities.invokeLater(() -> showConnectionStatus(playerName, linkedCharacter));
                return;
            }

            List<RuneFolioSyncEvent> events = new ArrayList<>();
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
                queued &= syncQueue.enqueue(event);
            }
            if (!queued)
            {
                SwingUtilities.invokeLater(() ->
                    panel.setStatus("The local sync queue is full. RuneFolio kept the existing events; reconnect before collecting more.")
                );
                refreshSyncPanel();
                return;
            }

            refreshSyncPanel();
            showProgressSyncGuideOnce();
            SwingUtilities.invokeLater(() ->
                panel.setStatus("Sync queued. Sending it to RuneFolio...")
            );
            syncExecutor.submit(() -> flushQueueWithToken(
                savedToken,
                accountMode ? null : playerName,
                accountMode
            ));
        });
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

        boolean queued = syncQueue.enqueue(RuneFolioSyncEvent.skillSnapshot(playerName, trigger, skills));
        if (lastKnownQuestState != null)
        {
            queued |= syncQueue.enqueue(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.QUEST_SNAPSHOT_TYPE,
                playerName,
                trigger,
                lastKnownQuestState
            ));
        }
        if (lastKnownDiaryState != null)
        {
            queued |= syncQueue.enqueue(RuneFolioSyncEvent.progressSnapshot(
                RuneFolioSyncEvent.DIARY_SNAPSHOT_TYPE,
                playerName,
                trigger,
                lastKnownDiaryState
            ));
        }
        if (lastKnownCombatState != null)
        {
            queued |= syncQueue.enqueue(RuneFolioSyncEvent.progressSnapshot(
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
        if (!uploadInFlight.compareAndSet(false, true))
        {
            return;
        }

        try
        {
            List<RuneFolioSyncEvent> events = syncQueue.snapshot(
                SYNC_BATCH_SIZE,
                event -> characterFilter == null || namesMatch(characterFilter, event.getCharacterName())
            );
            if (events.isEmpty())
            {
                return;
            }

            RuneFolioApiClient.BatchSyncResult result = RuneFolioApiClient.syncEvents(savedToken, events);
            syncQueue.acknowledge(result.getAcknowledgedEventIds());

            if (result.shouldRevoke())
            {
                throw new java.io.IOException("RuneFolio connection was revoked or expired.");
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
        clientThread.invokeLater(() ->
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
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("revoked") || lowerMessage.contains("expired"))
        {
            if (accountMode && savedToken.equals(accountConnectionToken))
            {
                clearAccountConnection();
                SwingUtilities.invokeLater(() -> panel.setAccountConnected(false));
            }
            else if (!accountMode)
            {
                String configKey = activeConnectionConfigKey;
                if (configKey != null)
                {
                    configManager.unsetConfiguration(CONFIG_GROUP, configKey);
                }
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
        if (lastKnownPlayerName != null && observedPlayer != null
            && !namesMatch(lastKnownPlayerName, observedPlayer))
        {
            deactivateCurrentCharacter();
            connectionLookupPending = true;
        }

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
        if (skill == Skill.OVERALL)
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
        if (!config.syncLootDrops() || !canCollectCurrentWorld()
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
        enqueueLiveEvent(RuneFolioSyncEvent.lootDrop(
            playerName,
            sourceName == null || sourceName.isBlank() ? "Unknown loot source" : sourceName,
            sourceType,
            Math.max(0, event.getCombatLevel()),
            Math.max(1, event.getAmount()),
            items,
            totalGeValue,
            totalHaValue
        ));

        String safeSourceName = sourceName == null || sourceName.isBlank() ? "Unknown loot source" : sourceName;
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
                    syncExecutor.submit(this::flushQueue);
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
                combatAchievement.group("tier").toLowerCase(),
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
        if (!config.uploadScreenshots() || !canCollectCurrentWorld())
        {
            return;
        }
        String playerName = currentPlayerName();
        String savedToken = isAccountMode() ? accountConnectionToken : connectionToken;
        if (playerName == null || savedToken == null || savedToken.isBlank())
        {
            return;
        }

        UUID eventId = UUID.randomUUID();
        long session = characterSession.get();
        String occurredAt = Instant.now().toString();
        boolean chatHidden = hideScreenshotWidget(config.hideChatInScreenshots(), InterfaceID.Chatbox.CHATAREA);
        boolean privateMessagesHidden = hideScreenshotWidget(config.hideChatInScreenshots(), InterfaceID.PmChat.CONTAINER);
        drawManager.requestNextFrameListener(frame ->
        {
            restoreScreenshotWidget(chatHidden, InterfaceID.Chatbox.CHATAREA);
            restoreScreenshotWidget(privateMessagesHidden, InterfaceID.PmChat.CONTAINER);
            clientThread.invokeLater(() ->
            {
                if (session == characterSession.get() && canCollectCurrentWorld()
                    && namesMatch(playerName, currentPlayerName()))
                {
                    uploadScreenshotAsync(savedToken, playerName, eventId, category, caption, occurredAt, frame);
                }
            });
        });
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

    private void uploadScreenshotAsync(
        String token,
        String playerName,
        UUID eventId,
        String category,
        String caption,
        String occurredAt,
        Image frame
    )
    {
        screenshotExecutor.submit(() ->
        {
            try
            {
                byte[] jpeg = RuneFolioScreenshotEncoder.encode(frame);
                if (jpeg.length > SCREENSHOT_MAX_BYTES)
                {
                    log.warn("RuneFolio screenshot was too large to upload: {} bytes", jpeg.length);
                    return;
                }
                long[] retryDelays = {0L, 1_000L, 3_000L};
                for (int attempt = 0; attempt < retryDelays.length; attempt++)
                {
                    if (retryDelays[attempt] > 0)
                    {
                        Thread.sleep(retryDelays[attempt]);
                    }
                    try
                    {
                        RuneFolioApiClient.uploadScreenshot(
                            token,
                            playerName,
                            eventId,
                            category,
                            caption,
                            occurredAt,
                            jpeg
                        );
                        return;
                    }
                    catch (java.io.IOException exception)
                    {
                        if (attempt == retryDelays.length - 1)
                        {
                            log.warn("RuneFolio screenshot upload failed after retries", exception);
                        }
                    }
                }
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
            catch (java.io.IOException exception)
            {
                log.warn("RuneFolio could not compress a screenshot", exception);
            }
        });
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
            JsonObject diaryState = RuneFolioProgressCollector.diaries(
                client,
                diaryTaskTracker.taskAreasForCurrentCharacter()
            );
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
            syncExecutor.submit(this::flushQueue);
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
            || (!accountMode && !namesMatch(playerName, connectedCharacterName)))
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

    private boolean requestDiaryTaskSync(String areaName, JsonArray taskAreas)
    {
        String playerName = currentPlayerName();
        if (playerName == null || playerName.isBlank())
        {
            return false;
        }

        JsonObject diaryState = RuneFolioProgressCollector.diaries(client, taskAreas);
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
            syncExecutor.submit(this::flushQueue);
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
        syncExecutor.submit(this::flushQueue);
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
            || (!accountMode && !namesMatch(playerName, connectedCharacterName)))
        {
            return false;
        }

        if (!syncQueue.enqueue(event))
        {
            SwingUtilities.invokeLater(() ->
                panel.setStatus("The local sync queue is full. RuneFolio kept the existing events.")
            );
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

        syncExecutor.submit(() -> flushQueueWithToken(
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
            if (skill == Skill.OVERALL)
            {
                continue;
            }

            skills.add(new RuneFolioApiClient.SkillSnapshot(
                skill.getName(),
                client.getRealSkillLevel(skill),
                client.getSkillExperience(skill)
            ));
        }
        return skills;
    }

    private void refreshPanel()
    {
        String playerName = currentPlayerName();
        panel.setCharacterName(playerName);
        panel.setAccountConnected(isAccountMode());

        if (playerName == null || playerName.isBlank())
        {
            panel.setStatus(isAccountMode()
                ? "Connected to your RuneFolio account. Log in to a character to sync."
                : "Log in to RuneLite, then connect your RuneFolio account or enter a temporary code.");
            return;
        }

        if (isAccountMode())
        {
            panel.setStatus("Checking your RuneFolio account connection...");
            return;
        }

        if (connectionToken == null || connectionToken.isBlank())
        {
            panel.setStatus(playerName + " is not connected to RuneFolio. Enter a temporary code for this character.");
            return;
        }

        showConnectionStatus(playerName, connectedCharacterName);
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
        SwingUtilities.invokeLater(panel::hideCharacterSetup);
        lastKnownPlayerName = null;
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
        SwingUtilities.invokeLater(panel::hideCharacterSetup);
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
        return first != null && second != null && normaliseName(first).equals(normaliseName(second));
    }

    private static String normaliseName(String value)
    {
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
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
        int pendingEvents = queue == null ? 0 : queue.size();
        SwingUtilities.invokeLater(() ->
            panel.setSyncState(lastSuccessfulSyncAtMillis, pendingEvents)
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
