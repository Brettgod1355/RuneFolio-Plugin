package app.runefolio.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;

/** Passive, bounded evidence from the current character. No game actions or raw containers leave this collector. */
@Singleton
final class RuneFolioUnlockCollector
{
    private final Client client;
    private final RuneFolioConfig config;
    private final EventBus eventBus;
    private final JsonArray catalog;
    private final Map<String, JsonObject> observedItems = new LinkedHashMap<>();
    private Predicate<JsonObject> publish;
    private JsonObject lastSent;
    private long characterHash;
    private int ticks;
    private boolean itemChanged;

    @Inject RuneFolioUnlockCollector(Client client, RuneFolioConfig config, EventBus eventBus, Gson gson)
    {
        this.client = client; this.config = config; this.eventBus = eventBus;
        try (InputStream stream = RuneFolioUnlockCollector.class.getResourceAsStream("/unlocks-catalog.json"))
        {
            if (stream == null) throw new IllegalStateException("Unlock catalog is missing");
            catalog = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonArray.class);
            if (catalog == null || catalog.size() > 200) throw new IllegalStateException("Invalid unlock catalog");
        }
        catch (java.io.IOException exception) { throw new IllegalStateException("Unlock catalog could not be read", exception); }
    }

    void startUp(Predicate<JsonObject> publish) { this.publish = publish; eventBus.register(this); }
    void shutDown() { eventBus.unregister(this); reset(); publish = null; }
    private boolean supported()
    {
        return config.syncAccountUnlocks() && client.getGameState() == GameState.LOGGED_IN
            && client.getLocalPlayer() != null && client.getAccountHash() != 0
            && RuneFolioWorldPolicy.supports(client.getWorldType());
    }
    private void bindCharacter()
    {
        long current = client.getAccountHash();
        if (characterHash != current) { reset(); characterHash = current; }
    }

    @Subscribe public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!supported()) return;
        bindCharacter();
        int id = event.getContainerId();
        if (id == InventoryID.BANK.getId())
        {
            Widget root = client.getWidget(12, 0);
            if (root == null || root.isHidden()) return;
        }
        else if (id != InventoryID.INVENTORY.getId() && id != InventoryID.EQUIPMENT.getId()) return;
        observeItems(event.getItemContainer());
        // Persist brief item sightings immediately, before an item can be consumed or logout occurs.
        if (itemChanged && publish != null)
        {
            JsonArray observations = new JsonArray();
            for (JsonObject item : observedItems.values()) observations.add(item.deepCopy());
            JsonObject payload = new JsonObject(); payload.add("observations", observations);
            if (publish.test(payload)) itemChanged = false;
        }
    }

    private void observeItems(ItemContainer container)
    {
        if (container == null || container.getItems().length > 1200) return;
        for (JsonElement value : catalog)
        {
            JsonObject entry = value.getAsJsonObject();
            if (entry.get("rule").isJsonNull()) continue;
            JsonObject rule = entry.getAsJsonObject("rule");
            if (!"item".equals(rule.get("kind").getAsString())) continue;
            String id = entry.get("id").getAsString();
            if (observedItems.containsKey(id)) continue;
            for (Item item : container.getItems())
            {
                if (item.getQuantity() <= 0 || !containsItem(rule.getAsJsonArray("ids"), item.getId())) continue;
                observedItems.put(id, observation(id, true, "item")); itemChanged = true; break;
            }
        }
    }

    static boolean containsItem(JsonArray ids, int id)
    {
        for (JsonElement value : ids) if (value.getAsInt() == id) return true;
        return false;
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        if (!supported()) { reset(); return; }
        bindCharacter(); ticks++;
        // Initial login state settles first. Items seen briefly are held until accepted by the durable queue.
        if (ticks < 3 || (!itemChanged && ticks != 3 && ticks % 100 != 0)) return;
        capture(false);
    }

    void syncNow() { if (supported()) { bindCharacter(); capture(true); } }

    private void capture(boolean force)
    {
        observeItems(client.getItemContainer(InventoryID.INVENTORY));
        observeItems(client.getItemContainer(InventoryID.EQUIPMENT));
        JsonArray observations = new JsonArray();
        for (JsonElement value : catalog)
        {
            JsonObject entry = value.getAsJsonObject();
            if (entry.get("rule").isJsonNull()) continue;
            JsonObject rule = entry.getAsJsonObject("rule");
            String id = entry.get("id").getAsString(), kind = rule.get("kind").getAsString();
            Boolean unlocked = null; String evidence = null;
            try
            {
                if ("flag".equals(kind))
                {
                    int varbit = rule.get("id").getAsInt();
                    if (client.getVarbit(varbit) == null) continue;
                    unlocked = binaryFlag(client.getVarbitValue(varbit)); evidence = "game_flag";
                    // Item rewards only establish that an item was obtained; absence is not an inventory audit.
                    if ("item".equals(entry.get("kind").getAsString()) && Boolean.FALSE.equals(unlocked)) unlocked = null;
                }
                else if ("quest".equals(kind))
                {
                    Quest quest = Quest.valueOf(rule.get("name").getAsString());
                    QuestState state = quest.getState(client);
                    if (state != null) unlocked = state == QuestState.FINISHED;
                    if (rule.has("positiveOnly") && rule.get("positiveOnly").getAsBoolean() && Boolean.FALSE.equals(unlocked)) unlocked = null;
                    evidence = "quest";
                }
                else if ("available".equals(kind)) { unlocked = true; evidence = "default"; }
            }
            catch (RuntimeException ignored) { /* An unavailable game value stays unknown. */ }
            if (unlocked != null) observations.add(observation(id, unlocked, evidence));
        }
        for (JsonObject item : observedItems.values()) observations.add(item.deepCopy());
        JsonObject payload = new JsonObject(); payload.add("observations", observations);
        if (observations.size() == 0 || (!force && payload.equals(lastSent))) { itemChanged = false; return; }
        if (publish != null && publish.test(payload)) { lastSent = payload.deepCopy(); itemChanged = false; }
    }

    static Boolean binaryFlag(int value) { return value == 0 ? Boolean.FALSE : value == 1 ? Boolean.TRUE : null; }
    static JsonObject observation(String id, boolean unlocked, String evidence)
    {
        JsonObject row = new JsonObject(); row.addProperty("id", id); row.addProperty("unlocked", unlocked); row.addProperty("evidence", evidence); return row;
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOADING) { ticks = 0; return; }
        if (event.getGameState() != GameState.LOGGED_IN) reset();
    }
    @Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) { reset(); }
    @Subscribe public void onConfigChanged(ConfigChanged event) { if ("runefolio".equals(event.getGroup()) && "syncAccountUnlocks".equals(event.getKey())) reset(); }
    private void reset() { observedItems.clear(); lastSent = null; ticks = 0; characterHash = 0; itemChanged = false; }
}
