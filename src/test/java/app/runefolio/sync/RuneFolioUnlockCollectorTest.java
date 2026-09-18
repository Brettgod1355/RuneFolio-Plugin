package app.runefolio.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
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
        assertTrue(new RuneFolioConfig(){}.syncAccountUnlocks());
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
        ItemContainerChanged bank=new ItemContainerChanged(InventoryID.BANK,container);
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

    @Test public void largeCatalogChunksRetryOnlyUnacceptedObservationsAndUsePurchasedBankBlocks()
    {
        Gson gson=new Gson();
        JsonArray catalog=gson.fromJson(new InputStreamReader(getClass().getResourceAsStream("/unlocks-catalog.json"), StandardCharsets.UTF_8),JsonArray.class);
        List<Item> items=new ArrayList<>(); Set<String> expected=new HashSet<>(), quests=new HashSet<>();
        for(JsonElement value:catalog) {
            JsonObject entry=value.getAsJsonObject(); if(entry.get("rule").isJsonNull())continue;
            expected.add(entry.get("id").getAsString()); JsonObject rule=entry.getAsJsonObject("rule");
            if("item".equals(rule.get("kind").getAsString()))items.add(new Item(rule.getAsJsonArray("ids").get(0).getAsInt(),1));
            if("quest".equals(rule.get("kind").getAsString()))quests.add(rule.get("name").getAsString());
        }
        assertTrue(expected.size()>200);
        int[] blocks={3}, scripts={0};
        ItemContainer container=fake(ItemContainer.class,(p,m,a)->m.getName().equals("getItems")?items.toArray(new Item[0]):null);
        Player player=fake(Player.class,(p,m,a)->null);
        VarbitComposition varbit=fake(VarbitComposition.class,(p,m,a)->0);
        Client client=fake(Client.class,(p,m,a)->{
            switch(m.getName()) {
                case "getGameState":return GameState.LOGGED_IN; case "getAccountHash":return 1L;
                case "getLocalPlayer":return player; case "getWorldType":return EnumSet.noneOf(WorldType.class);
                case "getItemContainer":return container; case "getVarbit":return varbit;
                case "getVarbitValue":return ((Integer)a[0])==13119?blocks[0]:1;
                case "runScript":scripts[0]++;return null; case "getIntStack":return new int[]{2};
                default:return null;
            }
        });
        RuneFolioUnlockCollector collector=new RuneFolioUnlockCollector(client,new RuneFolioConfig(){@Override public boolean syncAccountUnlocks(){return true;}},new EventBus(),gson);
        List<JsonObject> sent=new ArrayList<>(); int[] attempts={0};
        collector.startUp(payload->{attempts[0]++;if(attempts[0]==2)return false;sent.add(payload.deepCopy());return true;});
        try {
            collector.syncNow();assertEquals(1,sent.size());assertEquals(200,sent.get(0).getAsJsonArray("observations").size());
            assertEquals(quests.size(),scripts[0]); // One script per distinct quest, not one per catalog entry.
            for(int i=0;i<3;i++)collector.onGameTick(null);
            Set<String> actual=new HashSet<>();
            for(JsonObject payload:sent) {
                JsonArray observations=payload.getAsJsonArray("observations");assertTrue(observations.size()<=200);
                for(JsonElement value:observations) {
                    JsonObject row=value.getAsJsonObject();String id=row.get("id").getAsString();assertTrue("Duplicate accepted observation: "+id,actual.add(id));
                    if(id.startsWith("bank.space_tier_"))assertEquals(Integer.parseInt(id.substring(id.lastIndexOf('_')+1))<=3,row.get("unlocked").getAsBoolean());
                }
            }
            assertEquals(expected,actual);
            int before=sent.size();for(int i=0;i<100;i++)collector.onGameTick(null);assertEquals(before,sent.size());
            blocks[0]=10;collector.syncNow();
            for(int i=before;i<sent.size();i++)assertFalse(sent.get(i).toString().contains("bank.space_tier_"));
            before=sent.size();blocks[0]=4;collector.syncNow();
            assertTrue(sent.size()>before); // Explicit sync can resend already accepted state.
        } finally {collector.shutDown();}
        assertNull(RuneFolioUnlockCollector.thresholdFlag(-1,1,9));assertNull(RuneFolioUnlockCollector.thresholdFlag(10,1,9));
        assertEquals(Boolean.FALSE,RuneFolioUnlockCollector.thresholdFlag(0,1,9));assertEquals(Boolean.TRUE,RuneFolioUnlockCollector.thresholdFlag(9,9,9));
    }

    @Test public void visibleExpandedBankCollectsItemsBeyondTheOldContainerLimit()
    {
        Item[] bank=new Item[1410];bank[1400]=new Item(12954,1);
        ItemContainer container=fake(ItemContainer.class,(p,m,a)->m.getName().equals("getItems")?bank:null);
        Player player=fake(Player.class,(p,m,a)->null);Widget widget=fake(Widget.class,(p,m,a)->false);
        Client client=fake(Client.class,(p,m,a)->{
            switch(m.getName()) {
                case "getGameState":return GameState.LOGGED_IN;case "getAccountHash":return 1L;
                case "getLocalPlayer":return player;case "getWorldType":return EnumSet.noneOf(WorldType.class);
                case "getWidget":return widget;default:return null;
            }
        });
        RuneFolioUnlockCollector collector=new RuneFolioUnlockCollector(client,new RuneFolioConfig(){@Override public boolean syncAccountUnlocks(){return true;}},new EventBus(),new Gson());
        List<JsonObject> sent=new ArrayList<>();collector.startUp(payload->{sent.add(payload);return true;});
        try {collector.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK,container));assertEquals(1,sent.size());assertTrue(sent.get(0).toString().contains("item.dragon_defender"));}
        finally {collector.shutDown();}
    }
}
