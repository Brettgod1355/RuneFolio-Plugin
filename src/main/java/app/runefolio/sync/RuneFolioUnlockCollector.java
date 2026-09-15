package app.runefolio.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    static final int MAX_OBSERVATIONS = 200;
    private final Client client;
    private final RuneFolioConfig config;
    private final EventBus eventBus;
    private final JsonArray catalog;
    private final Map<String, JsonObject> observedItems = new LinkedHashMap<>();
    private final Map<Integer, List<String>> itemUnlocks = new HashMap<>();
    private final Map<String, JsonObject> accepted = new HashMap<>();
    private Predicate<JsonObject> publish;
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
            if (catalog == null || catalog.size() == 0 || catalog.size() > 1000) throw new IllegalStateException("Invalid unlock catalog");
            for (JsonElement value : catalog)
            {
                JsonObject entry = value.getAsJsonObject();
                if (entry.get("rule").isJsonNull()) continue;
                JsonObject rule = entry.getAsJsonObject("rule");
                if (!"item".equals(rule.get("kind").getAsString())) continue;
                for (JsonElement item : rule.getAsJsonArray("ids"))
                    itemUnlocks.computeIfAbsent(item.getAsInt(), ignored -> new ArrayList<>()).add(entry.get("id").getAsString());
            }
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
            if (publishObservations(observations, false)) itemChanged = false;
        }
    }

    private void observeItems(ItemContainer container)
    {
        if (container == null) return;
        Item[] items = container.getItems();
        if (items == null || items.length > 2000) return;
        // One pass through the visible container; no catalog-by-bank nested scan.
        for (Item item : items)
        {
            if (item == null || item.getQuantity() <= 0) continue;
            List<String> ids = itemUnlocks.get(item.getId());
            if (ids == null) continue;
            for (String id : ids)
            {
                if (observedItems.containsKey(id)) continue;
                observedItems.put(id, observation(id, true, "item")); itemChanged = true;
            }
        }
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
        Map<String, QuestState> questStates = new HashMap<>();
        for (JsonElement value : catalog)
        {
            JsonObject entry = value.getAsJsonObject();
            if (entry.get("rule").isJsonNull()) continue;
            JsonObject rule = entry.getAsJsonObject("rule");
            String id = entry.get("id").getAsString(), kind = rule.get("kind").getAsString();
            Boolean unlocked = null; String evidence = null;
            try
            {
                if ("flag".equals(kind) || "threshold".equals(kind))
                {
                    int varbit = rule.get("id").getAsInt();
                    if (client.getVarbit(varbit) == null) continue;
                    int raw = client.getVarbitValue(varbit);
                    unlocked = "threshold".equals(kind)
                        ? thresholdFlag(raw, rule.get("minimum").getAsInt(), rule.get("maximum").getAsInt())
                        : binaryFlag(raw);
                    evidence = "game_flag";
                    // Item rewards only establish that an item was obtained; absence is not an inventory audit.
                    if ("item".equals(entry.get("kind").getAsString()) && Boolean.FALSE.equals(unlocked)) unlocked = null;
                }
                else if ("quest".equals(kind))
                {
                    String name = rule.get("name").getAsString();
                    if (!questStates.containsKey(name)) questStates.put(name, Quest.valueOf(name).getState(client));
                    QuestState state = questStates.get(name);
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
        if (publishObservations(observations, force)) itemChanged = false;
    }

    private boolean publishObservations(JsonArray observations, boolean force)
    {
        if (publish == null) return false;
        JsonArray batch = new JsonArray();
        for (JsonElement value : observations)
        {
            JsonObject row = value.getAsJsonObject();
            if (!force && row.equals(accepted.get(row.get("id").getAsString()))) continue;
            batch.add(row.deepCopy());
            if (batch.size() == MAX_OBSERVATIONS)
            {
                if (!publishBatch(batch)) return false;
                batch = new JsonArray();
            }
        }
        return batch.size() == 0 || publishBatch(batch);
    }

    private boolean publishBatch(JsonArray batch)
    {
        JsonObject payload = new JsonObject(); payload.add("observations", batch);
        if (!publish.test(payload)) return false;
        // Only accepted chunks become deduplicated. A later rejected chunk stays pending.
        for (JsonElement value : batch)
        {
            JsonObject row = value.getAsJsonObject();
            accepted.put(row.get("id").getAsString(), row.deepCopy());
        }
        return true;
    }

    static Boolean thresholdFlag(int value, int minimum, int maximum)
    { return value < 0 || value > maximum || minimum < 1 || minimum > maximum ? null : value >= minimum; }
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
    private void reset() { observedItems.clear(); accepted.clear(); ticks = 0; characterHash = 0; itemChanged = false; }
}
