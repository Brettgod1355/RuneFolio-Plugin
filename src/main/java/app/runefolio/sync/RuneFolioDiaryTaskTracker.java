package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Singleton
final class RuneFolioDiaryTaskTracker
{
    private static final String CONFIG_GROUP = "runefolio";
    private static final String TASK_CACHE_PREFIX = "diaryTasks.";
    private static final int CAPTURE_ATTEMPTS = 10;

    private final Client client;
    private final ClientThread clientThread;
    private final EventBus eventBus;
    private final ConfigManager configManager;
    private BiFunction<String, JsonArray, Boolean> syncAction;
    private RuneFolioDiaryTaskParser.Area currentArea;
    private int captureAttemptsRemaining;
    private boolean capturedForOpenDiary;
    private long captureSession;

    @Inject
    RuneFolioDiaryTaskTracker(
        Client client,
        ClientThread clientThread,
        EventBus eventBus,
        ConfigManager configManager
    )
    {
        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
        this.configManager = configManager;
    }

    void startUp(BiFunction<String, JsonArray, Boolean> action)
    {
        syncAction = action;
        eventBus.register(this);
        captureAttemptsRemaining = CAPTURE_ATTEMPTS;
        clientThread.invokeLater(this::setupForOpenDiary);
    }

    void shutDown()
    {
        eventBus.unregister(this);
        syncAction = null;
        currentArea = null;
        captureAttemptsRemaining = 0;
        capturedForOpenDiary = false;
    }

    JsonArray taskAreasForCurrentCharacter()
    {
        return readTaskAreas();
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        resetCapture();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            resetCapture();
        }
    }

    private void resetCapture()
    {
        captureSession++;
        currentArea = null;
        captureAttemptsRemaining = 0;
        capturedForOpenDiary = false;
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.JOURNALSCROLL)
        {
            currentArea = null;
            capturedForOpenDiary = false;
            captureAttemptsRemaining = CAPTURE_ATTEMPTS;
            long session = captureSession;
            clientThread.invokeLater(() -> clientThread.invokeLater(() ->
            {
                if (session == captureSession)
                {
                    setupForOpenDiary();
                }
            }));
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event.getGroupId() == InterfaceID.JOURNALSCROLL)
        {
            currentArea = null;
            captureAttemptsRemaining = 0;
            capturedForOpenDiary = false;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!capturedForOpenDiary && captureAttemptsRemaining > 0)
        {
            captureAttemptsRemaining--;
            setupForOpenDiary();
        }
    }

    private void setupForOpenDiary()
    {
        if (client.getGameState() != GameState.LOGGED_IN || capturedForOpenDiary)
        {
            return;
        }

        Widget title = client.getWidget(InterfaceID.Journalscroll.TITLE);
        Widget textLayer = client.getWidget(InterfaceID.Journalscroll.TEXTLAYER);
        RuneFolioDiaryTaskParser.Area area = title == null
            ? null
            : RuneFolioDiaryTaskParser.areaFromTitle(title.getText());
        if (area == null || textLayer == null || textLayer.isHidden())
        {
            return;
        }

        currentArea = area;
        captureCurrentArea(textLayer);
    }

    private void captureCurrentArea(Widget textLayer)
    {
        JsonObject areaState = RuneFolioDiaryTaskParser.parse(currentArea, textLayer.getStaticChildren());
        if (areaState == null)
        {
            return;
        }

        capturedForOpenDiary = true;
        captureAttemptsRemaining = 0;
        JsonArray taskAreas = readTaskAreas();
        if (replaceArea(taskAreas, areaState))
        {
            writeTaskAreas(taskAreas);
        }

        if (syncAction != null)
        {
            syncAction.apply(currentArea.getName(), taskAreas.deepCopy());
        }
    }

    private static boolean replaceArea(JsonArray taskAreas, JsonObject areaState)
    {
        String areaKey = areaState.get("area").getAsString();
        for (int index = 0; index < taskAreas.size(); index++)
        {
            JsonElement existing = taskAreas.get(index);
            if (existing.isJsonObject()
                && existing.getAsJsonObject().has("area")
                && areaKey.equals(existing.getAsJsonObject().get("area").getAsString()))
            {
                if (existing.equals(areaState))
                {
                    return false;
                }
                taskAreas.set(index, areaState);
                return true;
            }
        }
        taskAreas.add(areaState);
        return true;
    }

    private JsonArray readTaskAreas()
    {
        String key = taskCacheKey();
        if (key == null)
        {
            return new JsonArray();
        }
        String saved = configManager.getConfiguration(CONFIG_GROUP, key);
        if (saved == null || saved.isBlank())
        {
            return new JsonArray();
        }
        try
        {
            JsonElement parsed = new JsonParser().parse(saved);
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        }
        catch (RuntimeException ignored)
        {
            return new JsonArray();
        }
    }

    private void writeTaskAreas(JsonArray taskAreas)
    {
        String key = taskCacheKey();
        if (key != null)
        {
            configManager.setConfiguration(CONFIG_GROUP, key, taskAreas.toString());
        }
    }

    private String taskCacheKey()
    {
        Player localPlayer = client.getLocalPlayer();
        String name = localPlayer == null ? null : localPlayer.getName();
        if (name == null || name.isBlank())
        {
            return null;
        }
        String normalized = name.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        String encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
        return TASK_CACHE_PREFIX + encoded;
    }

}
