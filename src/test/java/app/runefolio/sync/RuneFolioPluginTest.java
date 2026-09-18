package app.runefolio.sync;

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
}
