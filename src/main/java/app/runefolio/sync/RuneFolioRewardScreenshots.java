package app.runefolio.sync;

import java.util.Locale;
import java.util.Set;

/** Classifies the enabled native Loot Tracker's observed reward events, not NPC deaths. */
final class RuneFolioRewardScreenshots
{
    static String category(String source, String type)
    {
        if (source == null || !"event".equals(type)) return null;
        String name = source.toLowerCase(Locale.ROOT);
        if (name.matches("clue scroll \\((beginner|easy|medium|hard|elite|master)\\)")) return "clue_reward";
        if (name.equals("loot chest")) return "loot_key";
        if (name.contains("chest") || Set.of("barrows", "moons of peril", "chambers of xeric", "theatre of blood",
            "tombs of amascut", "the gauntlet", "the corrupted gauntlet", "fortis colosseum", "doom of mokhaiotl").contains(name)) return "raid_chest_reward";
        return null;
    }
}
