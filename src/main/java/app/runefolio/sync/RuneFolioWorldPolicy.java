package app.runefolio.sync;

import java.util.Collection;
import java.util.EnumSet;
import net.runelite.api.WorldType;

final class RuneFolioWorldPolicy
{
    /**
     * World types whose progress belongs to a separate character, not the main account.
     * SEASONAL covers Leagues; FRESH_START_WORLD is its own temporary game mode and was
     * missing here, so Fresh Start progress was being synced into the main character.
     */
    private static final EnumSet<WorldType> SEPARATE_PROGRESS = EnumSet.of(
        WorldType.BETA_WORLD, WorldType.NOSAVE_MODE, WorldType.TOURNAMENT_WORLD,
        WorldType.DEADMAN, WorldType.SEASONAL, WorldType.FRESH_START_WORLD,
        WorldType.QUEST_SPEEDRUNNING, WorldType.PVP_ARENA, WorldType.LAST_MAN_STANDING
    );

    static boolean supports(Collection<WorldType> types)
    {
        return types != null && types.stream().noneMatch(SEPARATE_PROGRESS::contains);
    }
}
