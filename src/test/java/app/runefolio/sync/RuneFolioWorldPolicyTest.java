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
            WorldType.FRESH_START_WORLD, WorldType.QUEST_SPEEDRUNNING, WorldType.PVP_ARENA,
            WorldType.LAST_MAN_STANDING })
        {
            Assert.assertFalse(type.name(), RuneFolioWorldPolicy.supports(EnumSet.of(WorldType.MEMBERS, type)));
        }
        Assert.assertFalse(RuneFolioWorldPolicy.supports(null));
    }

    /**
     * FRESH_START_WORLD was missing for a long time, so pin the whole enum: a world type added
     * upstream forces a deliberate decision here rather than silently defaulting to "synced".
     */
    @Test
    public void everyWorldTypeIsClassifiedDeliberately()
    {
        EnumSet<WorldType> mainAccountProgress = EnumSet.of(
            WorldType.MEMBERS, WorldType.PVP, WorldType.BOUNTY, WorldType.SKILL_TOTAL,
            WorldType.HIGH_RISK, WorldType.LEGACY_ONLY, WorldType.EOC_ONLY);
        EnumSet<WorldType> separateProgress = EnumSet.of(
            WorldType.BETA_WORLD, WorldType.NOSAVE_MODE, WorldType.TOURNAMENT_WORLD,
            WorldType.DEADMAN, WorldType.SEASONAL, WorldType.FRESH_START_WORLD,
            WorldType.QUEST_SPEEDRUNNING, WorldType.PVP_ARENA, WorldType.LAST_MAN_STANDING);

        EnumSet<WorldType> classified = EnumSet.copyOf(mainAccountProgress);
        classified.addAll(separateProgress);
        Assert.assertEquals("a new WorldType needs a decision in RuneFolioWorldPolicy",
            EnumSet.allOf(WorldType.class), classified);

        for (WorldType type : mainAccountProgress)
        {
            Assert.assertTrue(type.name(), RuneFolioWorldPolicy.supports(EnumSet.of(type)));
        }
        for (WorldType type : separateProgress)
        {
            Assert.assertFalse(type.name(), RuneFolioWorldPolicy.supports(EnumSet.of(type)));
        }
    }
}
