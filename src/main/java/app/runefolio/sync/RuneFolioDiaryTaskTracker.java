package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
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
import net.runelite.client.ui.overlay.OverlayManager;

@Singleton
final class RuneFolioDiaryTaskTracker
{
    private static final String CONFIG_GROUP = "runefolio";
    private static final String TASK_CACHE_PREFIX = "diaryTasks.";
    private static final int CAPTURE_ATTEMPTS = 10;
    private static final int OVERLAY_TOP_OFFSET = 8;
    private static final int OVERLAY_WIDTH = 380;

    private final Client client;
    private final ClientThread clientThread;
    private final EventBus eventBus;
    private final ConfigManager configManager;
    private final OverlayManager overlayManager;
    private final RuneFolioDiarySyncOverlay syncOverlay;
    private BiFunction<String, JsonArray, Boolean> syncAction;
    private RuneFolioDiaryTaskParser.Area currentArea;
    private int captureAttemptsRemaining;
    private boolean capturedForOpenDiary;

    @Inject
    RuneFolioDiaryTaskTracker(
        Client client,
        ClientThread clientThread,
        EventBus eventBus,
        ConfigManager configManager,
        OverlayManager overlayManager,
        RuneFolioDiarySyncOverlay syncOverlay
    )
    {
        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
        this.configManager = configManager;
        this.overlayManager = overlayManager;
        this.syncOverlay = syncOverlay;
    }

    void startUp(BiFunction<String, JsonArray, Boolean> action)
    {
        syncAction = action;
        overlayManager.add(syncOverlay);
        eventBus.register(this);
        captureAttemptsRemaining = CAPTURE_ATTEMPTS;
        clientThread.invokeLater(this::setupForOpenDiary);
    }

    void shutDown()
    {
        eventBus.unregister(this);
        overlayManager.remove(syncOverlay);
        syncOverlay.clear();
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
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.JOURNALSCROLL)
        {
            currentArea = null;
            capturedForOpenDiary = false;
            captureAttemptsRemaining = CAPTURE_ATTEMPTS;
            clientThread.invokeLater(() -> clientThread.invokeLater(this::setupForOpenDiary));
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event.getGroupId() == InterfaceID.JOURNALSCROLL)
        {
            syncOverlay.clear();
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

    void markSynced(String areaName)
    {
        addChatMessage("<col=64cd58>" + areaName + " diary has been synced successfully.</col>");
        if (currentArea != null && currentArea.getName().equals(areaName))
        {
            syncOverlay.showSuccess(areaName);
        }
    }

    void markSyncFailed(String areaName)
    {
        addChatMessage("<col=d67966>" + areaName + " diary could not be synced yet. RuneFolio will retry automatically.</col>");
        if (currentArea != null && currentArea.getName().equals(areaName))
        {
            syncOverlay.showFailure(areaName);
        }
    }

    private void setupForOpenDiary()
    {
        if (capturedForOpenDiary)
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
        Rectangle bounds = (title.getParent() == null ? textLayer : title.getParent()).getBounds();
        int overlayX = Math.max(bounds.x + 12, bounds.x + bounds.width - OVERLAY_WIDTH - 12);
        syncOverlay.showSyncing(new Point(overlayX, bounds.y + OVERLAY_TOP_OFFSET), area.getName());
        captureCurrentArea(textLayer);
    }

    private void captureCurrentArea(Widget textLayer)
    {
        JsonObject areaState = RuneFolioDiaryTaskParser.parse(currentArea, textLayer.getStaticChildren());
        if (areaState == null)
        {
            if (captureAttemptsRemaining == 0 && currentArea != null)
            {
                syncOverlay.showFailure(currentArea.getName());
                addChatMessage(
                    "<col=d67966>RuneFolio could not read the " + currentArea.getName()
                        + " diary after its tasks appeared. Close and reopen this diary to try again.</col>"
                );
            }
            return;
        }

        capturedForOpenDiary = true;
        captureAttemptsRemaining = 0;
        JsonArray taskAreas = readTaskAreas();
        if (replaceArea(taskAreas, areaState))
        {
            writeTaskAreas(taskAreas);
        }

        boolean queued = syncAction != null
            && Boolean.TRUE.equals(syncAction.apply(currentArea.getName(), taskAreas.deepCopy()));
        if (!queued)
        {
            syncOverlay.showFailure(currentArea.getName());
            addChatMessage(
                "<col=d67966>" + currentArea.getName()
                    + " diary was saved locally but could not be queued. Connect RuneFolio or use Sync now.</col>"
            );
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

    private void addChatMessage(String message)
    {
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            "<col=d9b861>RuneFolio:</col> " + message,
            null
        );
    }
}
