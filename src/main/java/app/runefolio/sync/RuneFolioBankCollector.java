package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;

@Singleton
final class RuneFolioBankCollector
{
    private final Client client;
    private final ItemManager itemManager;
    private final RuneFolioConfig config;
    private final EventBus eventBus;
    private Predicate<JsonObject> publish;
    private boolean bankObserved;
    private int changedTick = -1;
    private int lastSentTick = -100;
    private JsonObject lastSent;
    private String lastSentDay;

    @Inject RuneFolioBankCollector(Client client, ItemManager itemManager, RuneFolioConfig config, EventBus eventBus)
    {
        this.client = client; this.itemManager = itemManager; this.config = config; this.eventBus = eventBus;
    }

    void startUp(Predicate<JsonObject> publish)
    {
        this.publish = publish;
        eventBus.register(this);
    }

    void shutDown()
    {
        eventBus.unregister(this);
        reset();
        publish = null;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!config.syncBankWealth() || !supported()) return;
        int id = event.getContainerId();
        if (id == InventoryID.BANK) bankObserved = true;
        if (id == InventoryID.BANK || id == InventoryID.INV || id == InventoryID.WORN) changedTick = client.getTickCount();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!config.syncBankWealth()) { reset(); return; }
        if (!supported() || !bankObserved || changedTick < 0
            || client.getTickCount() - changedTick < 2 || client.getTickCount() - lastSentTick < 100) return;
        // Only capture while the ordinary bank is visible, never an unseen cached container.
        Widget bankRoot = client.getWidget(InterfaceID.BANKMAIN, 0);
        if (bankRoot == null || bankRoot.isHidden()) return;
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        if (bank == null || inventory == null || equipment == null) return;
        if (bank.getItems().length > 1200 || inventory.getItems().length > 28 || equipment.getItems().length > 14) return;
        JsonObject state = new JsonObject();
        state.add("bank", items(bank));
        state.add("inventory", items(inventory));
        state.add("equipment", items(equipment));
        long ge = 0, ha = 0;
        for (String container : new String[] {"bank", "inventory", "equipment"})
        {
            for (JsonElement value : state.getAsJsonArray(container))
            {
                JsonObject item = value.getAsJsonObject();
                ge = add(ge, item.get("geValue").getAsLong());
                ha = add(ha, item.get("haValue").getAsLong());
            }
        }
        state.addProperty("totalGeValue", ge);
        state.addProperty("totalHaValue", ha);
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        if (state.equals(lastSent) && day.equals(lastSentDay)) { changedTick = -1; return; }
        if (publish != null && publish.test(state))
        {
            lastSent = state.deepCopy();
            lastSentDay = day;
            lastSentTick = client.getTickCount();
            changedTick = -1;
        }
    }

    private JsonArray items(ItemContainer container)
    {
        JsonArray values = new JsonArray();
        for (Item item : container.getItems())
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
            ItemComposition definition = itemManager.getItemComposition(item.getId());
            if (definition.getPlaceholderTemplateId() != -1) continue;
            int id = itemManager.canonicalize(item.getId());
            ItemComposition canonical = itemManager.getItemComposition(id);
            JsonObject value = new JsonObject();
            value.addProperty("itemId", id);
            value.addProperty("itemName", canonical.getName());
            value.addProperty("quantity", item.getQuantity());
            value.addProperty("geValue", value(item.getQuantity(), Math.max(0, itemManager.getItemPrice(id))));
            value.addProperty("haValue", value(item.getQuantity(), alchemyUnitValue(id, canonical.getHaPrice())));
            values.add(value);
        }
        return values;
    }

    // Currency is counted at face value, not as an item to cast High Alchemy on.
    static int alchemyUnitValue(int itemId, int itemAlchemyValue)
    {
        if (itemId == ItemID.COINS) return 1;
        if (itemId == ItemID.PLATINUM) return 1000;
        return Math.max(0, itemAlchemyValue);
    }

    static long value(int quantity, int price)
    {
        return Math.min(9_007_199_254_740_991L, (long) Math.max(0, quantity) * Math.max(0, price));
    }

    static long add(long left, long right)
    {
        return Math.min(9_007_199_254_740_991L, left + right);
    }

    private boolean supported()
    {
        return client.getGameState() == GameState.LOGGED_IN && RuneFolioWorldPolicy.supports(client.getWorldType());
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        // LOADING is a scene rebuild for the same character; keep the dedup state.
        if (event.getGameState() == GameState.LOADING) return;
        if (event.getGameState() != GameState.LOGGED_IN) reset();
    }
    @Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) { reset(); }
    private void reset() { bankObserved = false; changedTick = -1; lastSentTick = -100; lastSent = null; lastSentDay = null; }
}
