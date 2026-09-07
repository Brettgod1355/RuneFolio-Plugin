package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioDiaryTaskFlagsTest
{
    private static final String[] TIERS = {"easy", "medium", "hard", "elite"};

    private JsonArray counts(String area, int[] totals, int... completed)
    {
        JsonObject result = new JsonObject(); result.addProperty("area", area);
        JsonArray tiers = new JsonArray();
        for (int i = 0; i < 4; i++)
        {
            JsonObject tier = new JsonObject(); tier.addProperty("tier", TIERS[i]);
            tier.addProperty("totalCount", totals[i]); tier.addProperty("completedCount", completed[i]);
            tiers.add(tier);
        }
        result.add("tiers", tiers); JsonArray areas = new JsonArray(); areas.add(result); return areas;
    }

    private String indices(JsonArray areas, int tier)
    {
        Assert.assertEquals(1, areas.size());
        return areas.get(0).getAsJsonObject().getAsJsonArray("tiers").get(tier)
            .getAsJsonObject().getAsJsonArray("completedTaskIndices").toString();
    }

    @Test public void reportedArdougneAndWildernessTasksUseTheirActualBits()
    {
        JsonArray ardy = RuneFolioDiaryTaskFlags.collect(
            id -> id == VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY ? (1 << 1) | (1 << 4) | (1 << 9) | (1 << 29) : 0,
            id -> 0, counts("ardougne", new int[]{10,12,12,8}, 3,0,1,0));
        Assert.assertEquals("[2,4,8]", indices(ardy, 0));
        Assert.assertEquals("[4]", indices(ardy, 2));
        JsonArray wild = RuneFolioDiaryTaskFlags.collect(
            id -> id == VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY ? (1 << 2) | (1 << 3) | (1 << 5) | (1 << 6) : 0,
            id -> 0, counts("wilderness", new int[]{12,11,10,7}, 4,0,0,0));
        Assert.assertEquals("[2,3,5,6]", indices(wild, 0));
    }

    @Test public void catalogOrderIsNotAlwaysBitOrderAndSignBitIsSupported()
    {
        JsonArray ardy = RuneFolioDiaryTaskFlags.collect(
            id -> id == VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY2 ? 1 << 9 : 0,
            id -> 0, counts("ardougne", new int[]{10,12,12,8}, 0,0,0,1));
        Assert.assertEquals("[3]", indices(ardy, 3));
        JsonArray kourend = RuneFolioDiaryTaskFlags.collect(
            id -> id == VarPlayerID.KOUREND_ACHIEVEMENT_DIARY ? (1 << 25) | (1 << 21) | (1 << 20) | (1 << 31) : 0,
            id -> 0, counts("kourend_kebos", new int[]{12,13,10,8}, 0,3,1,0));
        Assert.assertEquals("[1,5,11]", indices(kourend, 1));
        Assert.assertEquals("[5]", indices(kourend, 2));
    }

    @Test public void karamjaCountersRequireFiveAndCharterTasksHaveExplicitOrder()
    {
        for (int value = 0; value <= 5; value++)
        {
            final int amount = value;
            JsonArray areas = RuneFolioDiaryTaskFlags.collect(id -> 0,
                id -> id == VarbitID.ATJUN_EASY_BANANA || id == VarbitID.ATJUN_EASY_SEAWEED
                    || id == VarbitID.ATJUN_HARD_PALM ? amount : 0,
                counts("karamja", new int[]{10,19,10,5}, value == 5 ? 2 : 0,0,value == 5 ? 1 : 0,0));
            Assert.assertEquals(value == 5 ? "[1,8]" : "[]", indices(areas,0));
            Assert.assertEquals(value == 5 ? "[8]" : "[]", indices(areas,2));
        }
        JsonArray charter = RuneFolioDiaryTaskFlags.collect(id -> 0,
            id -> id == VarbitID.ATJUN_MED_KHAZARD || id == VarbitID.ATJUN_MED_CHARTER ? 1 : 0,
            counts("karamja", new int[]{10,19,10,5},0,2,0,0));
        Assert.assertEquals("[7,18]", indices(charter,1));
    }

    @Test public void missingOrDisagreeingCountersWithholdIdentities()
    {
        IntUnaryOperator zero = id -> 0;
        Assert.assertEquals(0, RuneFolioDiaryTaskFlags.collect(zero,zero,new JsonArray()).size());
        Assert.assertEquals(0, RuneFolioDiaryTaskFlags.collect(zero,zero,
            counts("ardougne", new int[]{10,12,12,8},1,0,0,0)).size());
        Assert.assertEquals(0, RuneFolioDiaryTaskFlags.collect(zero,zero,
            counts("ardougne", new int[]{11,12,12,8},0,0,0,0)).size());
        Assert.assertEquals("[]", indices(RuneFolioDiaryTaskFlags.collect(zero,zero,
            counts("ardougne", new int[]{10,12,12,8},0,0,0,0)),0));
    }

    @Test public void allTiersSupportCompleteAndEmptyAccountsWithoutRetainedState()
    {
        String[] areas = {"ardougne","desert","falador","fremennik","kandarin","karamja",
            "kourend_kebos","lumbridge_draynor","morytania","varrock","western_provinces","wilderness"};
        int[][] totals = {{10,12,12,8},{11,12,10,6},{11,14,11,6},{10,9,9,6},{11,14,11,7},{10,19,10,5},
            {12,13,10,8},{12,12,11,6},{11,11,10,6},{14,13,10,5},{11,13,13,7},{12,11,10,7}};
        int completed = 0;
        for (int a = 0; a < areas.length; a++)
        {
            JsonArray full = RuneFolioDiaryTaskFlags.collect(id -> -1,id -> 5,counts(areas[a],totals[a],totals[a]));
            Assert.assertEquals(1,full.size());
            for (int t = 0; t < 4; t++)
            {
                JsonArray tasks = full.get(0).getAsJsonObject().getAsJsonArray("tiers").get(t)
                    .getAsJsonObject().getAsJsonArray("completedTaskIndices");
                Assert.assertEquals(totals[a][t],tasks.size()); completed += tasks.size();
            }
            JsonArray empty = RuneFolioDiaryTaskFlags.collect(id -> 0,id -> 0,counts(areas[a],totals[a],0,0,0,0));
            for (int t = 0; t < 4; t++) Assert.assertEquals("[]", indices(empty,t));
        }
        Assert.assertEquals(492,completed);
    }
}
