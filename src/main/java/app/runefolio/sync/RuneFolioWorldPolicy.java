package app.runefolio.sync;

import java.util.Collection;
import java.util.EnumSet;
import net.runelite.api.WorldType;

final class RuneFolioWorldPolicy
{
    private static final EnumSet<WorldType> SEPARATE_PROGRESS = EnumSet.of(
        WorldType.BETA_WORLD, WorldType.NOSAVE_MODE, WorldType.TOURNAMENT_WORLD,
        WorldType.DEADMAN, WorldType.SEASONAL, WorldType.QUEST_SPEEDRUNNING,
        WorldType.PVP_ARENA, WorldType.LAST_MAN_STANDING
    );

    static boolean supports(Collection<WorldType> types)
    {
        return types != null && types.stream().noneMatch(SEPARATE_PROGRESS::contains);
    }
}
