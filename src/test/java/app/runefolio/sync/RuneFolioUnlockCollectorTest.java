package app.runefolio.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuneFolioUnlockCollectorTest
{
    @SuppressWarnings("unchecked") private static <T> T fake(Class<T> type, InvocationHandler handler)
    { return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler); }

    @Test public void unknownFlagsStayUnknownAndPartialEvidenceNeverSupersedesAnotherEvent()
    {
        assertNull(RuneFolioUnlockCollector.binaryFlag(-1)); assertNull(RuneFolioUnlockCollector.binaryFlag(2));
        assertEquals(Boolean.FALSE, RuneFolioUnlockCollector.binaryFlag(0)); assertEquals(Boolean.TRUE, RuneFolioUnlockCollector.binaryFlag(1));
        assertFalse(new RuneFolioConfig(){}.syncAccountUnlocks());
        JsonObject payload = new JsonObject(); payload.add("observations", new JsonArray());
        RuneFolioSyncEvent a = RuneFolioSyncEvent.historyEvent("unlocks.snapshot", "Example", payload);
        RuneFolioSyncEvent b = RuneFolioSyncEvent.historyEvent("unlocks.snapshot", "Example", payload);
        assertFalse(a.supersedes(b)); assertFalse(b.supersedes(a));
        assertEquals(a.toJson(), RuneFolioSyncEvent.fromJson(a.toJson()).toJson());
    }

    @Test public void briefItemsQueueImmediatelyWithConsentVisibleBankAndCharacterIsolation()
    {
        boolean[] enabled={false}, hidden={true}, accept={true}; long[] hash={1};
        GameState[] state={GameState.LOGGED_IN}; EnumSet<WorldType> world=EnumSet.noneOf(WorldType.class);
        Item[][] contents={new Item[]{new Item(12954,1)}};
        ItemContainer container=fake(ItemContainer.class,(p,m,a)->m.getName().equals("getItems")?contents[0]:null);
        Widget widget=fake(Widget.class,(p,m,a)->m.getName().equals("isHidden")?hidden[0]:null);
        Player player=fake(Player.class,(p,m,a)->null);
        Client client=fake(Client.class,(p,m,a)->{
            switch(m.getName()) {
                case "getGameState": return state[0]; case "getAccountHash": return hash[0];
                case "getLocalPlayer": return player; case "getWorldType": return world;
                case "getWidget": return widget; case "getItemContainer": return container;
                case "getVarbit": return null; case "getVarbitValue": return 0;
                case "getVarpValue": return 0; default: return null;
            }
        });
        RuneFolioConfig config=new RuneFolioConfig(){@Override public boolean syncAccountUnlocks(){return enabled[0];}};
        RuneFolioUnlockCollector collector=new RuneFolioUnlockCollector(client,config,new EventBus(),new Gson());
        List<JsonObject> sent=new ArrayList<>();
        collector.startUp(payload->{if(!accept[0]) return false; sent.add(payload.deepCopy());return true;});
        ItemContainerChanged bank=new ItemContainerChanged(InventoryID.BANK.getId(),container);
        try {
            collector.onItemContainerChanged(bank); assertTrue(sent.isEmpty()); enabled[0]=true;
            collector.onItemContainerChanged(bank); assertTrue(sent.isEmpty()); hidden[0]=false;
            collector.onItemContainerChanged(bank); assertEquals(1,sent.size());
            assertEquals("item", sent.get(0).getAsJsonArray("observations").get(0).getAsJsonObject().get("evidence").getAsString());
            assertEquals(1,sent.get(0).getAsJsonArray("observations").size());
            collector.onItemContainerChanged(bank); assertEquals(1,sent.size());
            // A rejected enqueue must retain a sighting even after the item disappears.
            contents[0]=new Item[]{new Item(27627,1)};accept[0]=false;collector.onItemContainerChanged(bank);
            contents[0]=new Item[0];accept[0]=true;for(int i=0;i<3;i++)collector.onGameTick(null);
            assertTrue(sent.get(sent.size()-1).toString().contains("item.ancient_icon"));
            hash[0]=2;collector.syncNow(); assertFalse(sent.get(sent.size()-1).toString().contains("item.ancient_icon"));
            assertFalse(sent.get(sent.size()-1).toString().contains("item.dragon_defender"));
            int before=sent.size();world.add(WorldType.DEADMAN);collector.syncNow();assertEquals(before,sent.size());
            world.clear();state[0]=GameState.LOGIN_SCREEN;collector.syncNow();assertEquals(before,sent.size());
        } finally {collector.shutDown();}
    }
}
