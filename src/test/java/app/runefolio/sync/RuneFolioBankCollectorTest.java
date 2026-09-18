package app.runefolio.sync;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioBankCollectorTest {
 @Test public void alchemyEstimateCountsCurrencyAtFaceValue() {
  Assert.assertEquals(1,RuneFolioBankCollector.alchemyUnitValue(net.runelite.api.gameval.ItemID.COINS,0));
  Assert.assertEquals(1000,RuneFolioBankCollector.alchemyUnitValue(net.runelite.api.gameval.ItemID.PLATINUM,0));
  Assert.assertEquals(120,RuneFolioBankCollector.alchemyUnitValue(100,120));
  Assert.assertEquals(0,RuneFolioBankCollector.alchemyUnitValue(100,-1));
  Assert.assertEquals(5000,RuneFolioBankCollector.value(5,RuneFolioBankCollector.alchemyUnitValue(net.runelite.api.gameval.ItemID.PLATINUM,0)));
 }
 @SuppressWarnings("unchecked")
 private static <T> T fake(Class<T> type, java.lang.reflect.InvocationHandler handler) {
  return (T)java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
 }
 @Test public void onlyVisibleObservedBanksPublishAndProfileResetDropsCachedState() {
  int[] tick={1},sent={0}; boolean[] hidden={true},enabled={true};
  net.runelite.api.ItemContainer container=fake(net.runelite.api.ItemContainer.class,(p,m,a)->m.getName().equals("getItems")?new net.runelite.api.Item[0]:null);
  net.runelite.api.widgets.Widget widget=fake(net.runelite.api.widgets.Widget.class,(p,m,a)->m.getName().equals("isHidden")?hidden[0]:null);
  net.runelite.api.Client client=fake(net.runelite.api.Client.class,(p,m,a)->{
   switch(m.getName()){
    case "getGameState":return net.runelite.api.GameState.LOGGED_IN;
    case "getWorldType":return java.util.EnumSet.noneOf(net.runelite.api.WorldType.class);
    case "getTickCount":return tick[0];
    case "getWidget":return widget;
    case "getItemContainer":return container;
    default:return null;
   }
  });
  RuneFolioConfig config=new RuneFolioConfig(){@Override public boolean syncBankWealth(){return enabled[0];}};
  RuneFolioBankCollector collector=new RuneFolioBankCollector(client,null,config,new net.runelite.client.eventbus.EventBus());
  collector.startUp(state->{sent[0]++;return true;});
  try {
   collector.onGameTick(null);Assert.assertEquals(0,sent[0]);
   collector.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(net.runelite.api.gameval.InventoryID.BANK,container));
   tick[0]=4;collector.onGameTick(null);Assert.assertEquals(0,sent[0]);
   hidden[0]=false;collector.onGameTick(null);Assert.assertEquals(1,sent[0]);
   collector.onGameTick(null);Assert.assertEquals(1,sent[0]);
   collector.onRuneScapeProfileChanged(null);tick[0]=200;collector.onGameTick(null);Assert.assertEquals(1,sent[0]);
   enabled[0]=false;collector.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(net.runelite.api.gameval.InventoryID.BANK,container));
   collector.onGameTick(null);Assert.assertEquals(1,sent[0]);
  } finally {collector.shutDown();}
 }
 @Test public void sceneLoadsKeepTheLastSentSnapshotButLoginScreenDropsIt() {
  int[] tick={1},sent={0};
  net.runelite.api.ItemContainer container=fake(net.runelite.api.ItemContainer.class,(p,m,a)->m.getName().equals("getItems")?new net.runelite.api.Item[0]:null);
  net.runelite.api.widgets.Widget widget=fake(net.runelite.api.widgets.Widget.class,(p,m,a)->m.getName().equals("isHidden")?false:null);
  net.runelite.api.Client client=fake(net.runelite.api.Client.class,(p,m,a)->{
   switch(m.getName()){
    case "getGameState":return net.runelite.api.GameState.LOGGED_IN;
    case "getWorldType":return java.util.EnumSet.noneOf(net.runelite.api.WorldType.class);
    case "getTickCount":return tick[0];
    case "getWidget":return widget;
    case "getItemContainer":return container;
    default:return null;
   }
  });
  RuneFolioConfig config=new RuneFolioConfig(){@Override public boolean syncBankWealth(){return true;}};
  RuneFolioBankCollector collector=new RuneFolioBankCollector(client,null,config,new net.runelite.client.eventbus.EventBus());
  collector.startUp(state->{sent[0]++;return true;});
  net.runelite.api.events.ItemContainerChanged bank=new net.runelite.api.events.ItemContainerChanged(net.runelite.api.gameval.InventoryID.BANK,container);
  try {
   collector.onItemContainerChanged(bank);tick[0]=4;collector.onGameTick(null);Assert.assertEquals(1,sent[0]);
   net.runelite.api.events.GameStateChanged loading=new net.runelite.api.events.GameStateChanged();loading.setGameState(net.runelite.api.GameState.LOADING);
   collector.onGameStateChanged(loading);
   collector.onItemContainerChanged(bank);tick[0]=300;collector.onGameTick(null);Assert.assertEquals(1,sent[0]);
   net.runelite.api.events.GameStateChanged login=new net.runelite.api.events.GameStateChanged();login.setGameState(net.runelite.api.GameState.LOGIN_SCREEN);
   collector.onGameStateChanged(login);
   tick[0]=600;collector.onGameTick(null);Assert.assertEquals(1,sent[0]);
   collector.onItemContainerChanged(bank);tick[0]=900;collector.onGameTick(null);Assert.assertEquals(2,sent[0]);
  } finally {collector.shutDown();}
 }
 @Test public void valuesCannotOverflow() {
  Assert.assertEquals(0,RuneFolioBankCollector.value(-1,100));
  Assert.assertEquals(200,RuneFolioBankCollector.value(2,100));
  Assert.assertEquals(9_007_199_254_740_991L,RuneFolioBankCollector.value(Integer.MAX_VALUE,Integer.MAX_VALUE));
 }
 @Test public void dailyBankSnapshotsOnlyReplaceSameCharacterAndDay() {
  JsonObject first=RuneFolioSyncEvent.historyEvent("bank.snapshot","Example",new JsonObject()).withIdentityKey("a".repeat(64)).toJson();
  first.addProperty("occurredAt","2026-01-01T10:00:00Z");
  JsonObject second=first.deepCopy();second.addProperty("occurredAt","2026-01-01T11:00:00Z");
  Assert.assertTrue(RuneFolioSyncEvent.fromJson(second).supersedes(RuneFolioSyncEvent.fromJson(first)));
  Assert.assertFalse(RuneFolioSyncEvent.fromJson(first).supersedes(RuneFolioSyncEvent.fromJson(second)));
  second.addProperty("occurredAt","2026-01-02T10:00:00Z");
  Assert.assertFalse(RuneFolioSyncEvent.fromJson(second).supersedes(RuneFolioSyncEvent.fromJson(first)));
  second.addProperty("occurredAt","2026-01-01T11:00:00Z");second.addProperty("identityKey","b".repeat(64));
  Assert.assertFalse(RuneFolioSyncEvent.fromJson(second).supersedes(RuneFolioSyncEvent.fromJson(first)));
 }
 @Test public void bankCaptureIsOptIn() { Assert.assertFalse(new RuneFolioConfig(){}.syncBankWealth()); }
}
