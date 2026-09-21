package app.runefolio.sync;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(RuneFolioPlugin.class);
        RuneLite.main(args);
    }

    @Test
    public void pvpLootHidesTheDefeatedPlayerUntilPvpHistoryIsOptedIn()
    {
        Assert.assertEquals("PvP opponent", RuneFolioPlugin.lootSourceName("Opponent", "player", false));
        Assert.assertEquals(0, RuneFolioPlugin.lootSourceCombatLevel(126, "player", false));
        Assert.assertEquals("Opponent", RuneFolioPlugin.lootSourceName("Opponent", "player", true));
        Assert.assertEquals(126, RuneFolioPlugin.lootSourceCombatLevel(126, "player", true));
    }

    @Test
    public void npcAndEventLootSourcesAreUnaffectedByThePvpOptIn()
    {
        Assert.assertEquals("Vorkath", RuneFolioPlugin.lootSourceName("Vorkath", "npc", false));
        Assert.assertEquals(392, RuneFolioPlugin.lootSourceCombatLevel(392, "npc", false));
        Assert.assertEquals("Loot Chest", RuneFolioPlugin.lootSourceName("Loot Chest", "event", false));
        Assert.assertEquals("Unknown loot source", RuneFolioPlugin.lootSourceName(" ", "npc", false));
        Assert.assertEquals("Unknown loot source", RuneFolioPlugin.lootSourceName(null, "unknown", true));
        Assert.assertEquals(0, RuneFolioPlugin.lootSourceCombatLevel(-5, "npc", true));
    }

    @Test
    public void onlyCountsPvpKillsWhereLootCanActuallyDrop()
    {
        EnumSet<WorldType> normal = EnumSet.of(WorldType.MEMBERS);

        // The Wilderness is the usual case: loot drops, so the kill counts.
        Assert.assertTrue(RuneFolioPlugin.pvpKillCanDropLoot(normal, 1));

        // Clan Wars, Castle Wars, Soul Wars, the Fight Pits and Emir's Arena on a normal
        // world are all safe - nothing is dropped, so a finishing blow is a minigame score
        // rather than account progress.
        Assert.assertFalse(RuneFolioPlugin.pvpKillCanDropLoot(normal, 0));

        // A PvP world is dangerous everywhere, including outside the Wilderness.
        Assert.assertTrue(RuneFolioPlugin.pvpKillCanDropLoot(EnumSet.of(WorldType.PVP), 0));
        Assert.assertTrue(RuneFolioPlugin.pvpKillCanDropLoot(EnumSet.of(WorldType.HIGH_RISK), 0));

        // Fails closed rather than open when the world state is unreadable.
        Assert.assertFalse(RuneFolioPlugin.pvpKillCanDropLoot(null, 0));
        Assert.assertTrue(RuneFolioPlugin.pvpKillCanDropLoot(null, 1));
        // A varbit that is neither 0 nor 1 is not a claim that we are in the Wilderness.
        Assert.assertFalse(RuneFolioPlugin.pvpKillCanDropLoot(normal, -1));
    }
}
