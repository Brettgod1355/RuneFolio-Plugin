package app.runefolio.sync;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioBankCollectorTest {
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
