package app.runefolio.sync;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioCollectorManifestTest
{
    private JsonObject valid()
    {
        return new JsonParser().parse("{\"schemaVersion\":1,\"revision\":\"test-1\",\"combatTaskCount\":655,\"questCapeExclusions\":[\"DADDYS_HOME\"]}").getAsJsonObject();
    }
    @Test public void acceptsOnlyBoundedDataConfiguration()
    {
        Assert.assertNotNull(RuneFolioCollectorManifest.parse(valid()));
        JsonObject json = valid(); json.addProperty("script", 1234);
        Assert.assertNull(RuneFolioCollectorManifest.parse(json));
        json = valid(); json.addProperty("combatTaskCount", 673);
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
    @Test public void unavailableManifestRetainsBuiltInFallback()
    {
        RuneFolioCollectorManifest.install(null);
        Assert.assertEquals(655, RuneFolioCollectorManifest.combatTaskCount(655));
        Assert.assertTrue(RuneFolioCollectorManifest.isSupplemental(net.runelite.api.Quest.DADDYS_HOME, true));
    }
}
