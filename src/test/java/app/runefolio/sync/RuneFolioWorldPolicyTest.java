package app.runefolio.sync;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioWorldPolicyTest
{
    @Test
    public void acceptsNormalWorldsIncludingPvp()
    {
        Assert.assertTrue(RuneFolioWorldPolicy.supports(EnumSet.noneOf(WorldType.class)));
        Assert.assertTrue(RuneFolioWorldPolicy.supports(EnumSet.of(WorldType.MEMBERS, WorldType.PVP)));
    }

    @Test
    public void rejectsSeparateProgressAndUnknownWorldState()
    {
        for (WorldType type : new WorldType[] { WorldType.BETA_WORLD, WorldType.NOSAVE_MODE,
            WorldType.TOURNAMENT_WORLD, WorldType.DEADMAN, WorldType.SEASONAL,
            WorldType.QUEST_SPEEDRUNNING, WorldType.PVP_ARENA, WorldType.LAST_MAN_STANDING })
        {
            Assert.assertFalse(type.name(), RuneFolioWorldPolicy.supports(EnumSet.of(WorldType.MEMBERS, type)));
        }
        Assert.assertFalse(RuneFolioWorldPolicy.supports(null));
    }
}
