package app.runefolio.sync;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.api.Quest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioCollectorManifestTest
{
    @After public void resetManifest()
    {
        RuneFolioCollectorManifest.reset();
    }
    private JsonObject valid()
    {
        return new JsonParser().parse("{\"schemaVersion\":1,\"revision\":\"test-1\",\"combatTaskCount\":655,\"questCapeExclusions\":[\"DADDYS_HOME\"]}").getAsJsonObject();
    }
    @Test public void acceptsOnlyBoundedDataConfiguration()
    {
        Assert.assertNotNull(RuneFolioCollectorManifest.parse(valid()));
        JsonObject json = valid(); json.addProperty("script", 1234);
        Assert.assertNull(RuneFolioCollectorManifest.parse(json));
        json = valid(); json.addProperty("combatTaskCount", RuneFolioCombatTaskCatalog.MAX_TASK_COUNT + 1);
        Assert.assertNull(RuneFolioCollectorManifest.parse(json));
        json = valid(); json.addProperty("combatTaskCount", RuneFolioCombatTaskCatalog.MAX_TASK_COUNT);
        Assert.assertNotNull(RuneFolioCollectorManifest.parse(json));
        json = valid(); json.addProperty("combatTaskCount", 0);
        Assert.assertNull(RuneFolioCollectorManifest.parse(json));
        json = valid(); json.addProperty("combatTaskCount", 655.1);
        Assert.assertNull(RuneFolioCollectorManifest.parse(json));
        json = valid(); json.addProperty("schemaVersion", 2);
        Assert.assertNull(RuneFolioCollectorManifest.parse(json));
    }
    @Test public void rejectsUnknownQuestsDuplicatesAndMissingFields()
    {
        JsonObject json = valid(); json.getAsJsonArray("questCapeExclusions").add("NOT_A_REAL_QUEST");
        Assert.assertNull(RuneFolioCollectorManifest.parse(json));
        json = valid(); json.getAsJsonArray("questCapeExclusions").add("DADDYS_HOME");
        Assert.assertNull(RuneFolioCollectorManifest.parse(json));
        json = valid(); json.remove("revision");
        Assert.assertNull(RuneFolioCollectorManifest.parse(json));
        Assert.assertNull(RuneFolioCollectorManifest.parse(null));
    }
    @Test public void installedManifestOverridesFallbacksUntilReset()
    {
        RuneFolioCollectorManifest.install(RuneFolioCollectorManifest.parse(valid()));
        Assert.assertEquals(655, RuneFolioCollectorManifest.combatTaskCount(1));
        Assert.assertTrue(RuneFolioCollectorManifest.isSupplemental(Quest.DADDYS_HOME, false));
        Assert.assertFalse(RuneFolioCollectorManifest.isSupplemental(Quest.COOKS_ASSISTANT, true));
        Assert.assertEquals(1, RuneFolioCollectorManifest.supplementalCount(0));
        RuneFolioCollectorManifest.install(null);
        Assert.assertEquals("a missing manifest never replaces an installed one", 655, RuneFolioCollectorManifest.combatTaskCount(1));
        RuneFolioCollectorManifest.reset();
        Assert.assertEquals(1, RuneFolioCollectorManifest.combatTaskCount(1));
        Assert.assertTrue(RuneFolioCollectorManifest.isSupplemental(Quest.COOKS_ASSISTANT, true));
        Assert.assertEquals(0, RuneFolioCollectorManifest.supplementalCount(0));
    }
    @Test public void unavailableManifestRetainsBuiltInFallback()
    {
        RuneFolioCollectorManifest.reset();
        Assert.assertEquals(655, RuneFolioCollectorManifest.combatTaskCount(655));
        Assert.assertTrue(RuneFolioCollectorManifest.isSupplemental(Quest.DADDYS_HOME, true));
        Assert.assertTrue(RuneFolioCombatTaskCatalog.trackedTaskCount() <= RuneFolioCombatTaskCatalog.MAX_TASK_COUNT);
    }
}
