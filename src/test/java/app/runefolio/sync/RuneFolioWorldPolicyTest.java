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

    /**
     * Fresh Start accounts transfer to the standard worlds keeping their stats and items, so
     * their progress is the account's permanent progress and must be tracked while it is being
     * earned. Leagues and Deadman are the contrast: a separate save that is discarded.
     */
    @Test
    public void tracksFreshStartWorldsBecauseThatProgressBecomesTheMainAccount()
    {
        Assert.assertTrue(RuneFolioWorldPolicy.supports(EnumSet.of(WorldType.FRESH_START_WORLD)));
        Assert.assertTrue(RuneFolioWorldPolicy.supports(EnumSet.of(WorldType.MEMBERS, WorldType.FRESH_START_WORLD)));
        Assert.assertFalse(RuneFolioWorldPolicy.supports(EnumSet.of(WorldType.SEASONAL)));
        Assert.assertFalse(RuneFolioWorldPolicy.supports(EnumSet.of(WorldType.DEADMAN)));
    }

    /**
     * Pin the whole enum: a world type added upstream forces a deliberate decision here rather
     * than silently defaulting to "synced". The question each new type has to answer is whether
     * progress earned there lands on the account's permanent main-game save.
     */
    @Test
    public void everyWorldTypeIsClassifiedDeliberately()
    {
        EnumSet<WorldType> mainAccountProgress = EnumSet.of(
            WorldType.MEMBERS, WorldType.PVP, WorldType.BOUNTY, WorldType.SKILL_TOTAL,
            WorldType.HIGH_RISK, WorldType.LEGACY_ONLY, WorldType.EOC_ONLY,
            WorldType.FRESH_START_WORLD);
        EnumSet<WorldType> separateProgress = EnumSet.of(
            WorldType.BETA_WORLD, WorldType.NOSAVE_MODE, WorldType.TOURNAMENT_WORLD,
            WorldType.DEADMAN, WorldType.SEASONAL, WorldType.QUEST_SPEEDRUNNING,
            WorldType.PVP_ARENA, WorldType.LAST_MAN_STANDING);

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
