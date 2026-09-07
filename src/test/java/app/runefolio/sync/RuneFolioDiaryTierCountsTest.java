package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioDiaryTierCountsTest
{
    @Test
    public void mapsGameScriptTriplesToAllFourTierCounts()
    {
        JsonObject area = RuneFolioProgressCollector.diaryTierTaskCounts(
            "falador",
            new int[] {6, 11, 0, 2, 14, 0, 1, 11, 0, 0, 6, 0}
        );

        Assert.assertNotNull(area);
        Assert.assertEquals("falador", area.get("area").getAsString());
        JsonArray tiers = area.getAsJsonArray("tiers");
        Assert.assertEquals(4, tiers.size());
        Assert.assertEquals("easy", tiers.get(0).getAsJsonObject().get("tier").getAsString());
        Assert.assertEquals(6, tiers.get(0).getAsJsonObject().get("completedCount").getAsInt());
        Assert.assertEquals(11, tiers.get(0).getAsJsonObject().get("totalCount").getAsInt());
        Assert.assertEquals("elite", tiers.get(3).getAsJsonObject().get("tier").getAsString());
        Assert.assertEquals(6, tiers.get(3).getAsJsonObject().get("totalCount").getAsInt());
    }

    @Test
    public void rejectsAnIncompleteScriptResult()
    {
        Assert.assertNull(RuneFolioProgressCollector.diaryTierTaskCounts(
            "falador",
            new int[] {6, 11, 0}
        ));
    }
}
