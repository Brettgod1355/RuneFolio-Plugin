package app.runefolio.sync;

import java.util.Collection;
import java.util.EnumSet;
import net.runelite.api.WorldType;

final class RuneFolioWorldPolicy
{
    /**
     * World types whose progress belongs to a separate character, not the main account.
     *
     * <p>The test for membership is not "is this a temporary game mode" but "does progress
     * earned here end up on the account's permanent main-game save". Those are different
     * questions, and FRESH_START_WORLD is why:
     *
     * <ul>
     *   <li>SEASONAL (Leagues) and DEADMAN are played on a <em>separate save</em>. The XP is
     *       discarded when the mode ends, so syncing it would corrupt the main character.</li>
     *   <li>FRESH_START_WORLD is <em>not</em> in this set. Fresh Start requires a brand new
     *       account that has never played before, and at the end of the event those accounts
     *       are transferred to the standard worlds keeping their stats and items. So Fresh
     *       Start progress is that account's real, permanent progress — it is the main save,
     *       just before it has finished migrating. Excluding it would leave the player with
     *       nothing tracked for the whole season and then a history that begins mid-account.
     *       Because Fresh Start forbids pre-existing accounts, it can never overwrite the
     *       progress of a main character that already exists.</li>
     * </ul>
     *
     * <p>A new temporary mode therefore needs that question answered before it is added here;
     * RuneFolioWorldPolicyTest pins every WorldType so the build fails until it is.
     */
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
