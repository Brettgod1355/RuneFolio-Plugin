package app.runefolio.sync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("runefolio")
public interface RuneFolioConfig extends Config
{
    @ConfigItem(keyName = "syncAccountUnlocks", name = "Sync account unlocks", warning = RuneFolioDataSharing.UNLOCKS,
        description = "Send supported account unlock flags and observations of checklist items, including Sea Charting task completion. No full bank contents are sent by this setting. Unknown entries can be confirmed on the website.", position = 7)
    default boolean syncAccountUnlocks() { return true; }

    @ConfigItem(keyName = "syncPvpHistory", name = "Sync PvP history", warning = RuneFolioDataSharing.PVP,
        description = "Opt-in: send your observed finishing blows, opponent names, timestamps and observed loot. Native Loot Tracker supplies unassigned loot-key contents. No opponent gear or location is collected.", position = 6)
    default boolean syncPvpHistory() { return false; }

    @ConfigItem(keyName = "screenshotClueRewards", name = "Clue reward screens", description = "Capture new clue rewards reported by native Loot Tracker. Enable completion history to link them to clue results.", position = 11, section = screenshotsSection)
    default boolean screenshotClueRewards() { return false; }
    @ConfigItem(keyName = "screenshotRaidChestRewards", name = "Raid and chest rewards", description = "Capture supported raid/chest rewards reported by native Loot Tracker.", position = 12, section = screenshotsSection)
    default boolean screenshotRaidChestRewards() { return false; }
    @ConfigItem(keyName = "screenshotPvpKills", name = "PvP kills", description = "Capture your observed finishing blows. PvP history is a separate opt-in.", position = 13, section = screenshotsSection)
    default boolean screenshotPvpKills() { return false; }
    @ConfigItem(keyName = "screenshotLootKeys", name = "Wilderness loot-key screens", description = "Capture the visible loot-key reward screen reported by native Loot Tracker. A screen can include several keys and does not identify a defeated player.", position = 14, section = screenshotsSection)
    default boolean screenshotLootKeys() { return false; }

    @ConfigItem(keyName = "syncCompletionHistory", name = "Sync completion history",
        warning = RuneFolioDataSharing.COMPLETIONS,
        description = "Send observed boss/raid, clue and Slayer completions and available result details to RuneFolio. Clue rewards use the enabled native Loot Tracker.", position = 5)
    default boolean syncCompletionHistory() { return true; }

    @ConfigItem(keyName = "syncBankWealth", name = "Sync bank and wealth",
        warning = RuneFolioDataSharing.BANK,
        description = "Opt-in: send bank, inventory and equipment items and estimated values while your bank is open. Pending snapshots are saved locally for retries.", position = 4)
    default boolean syncBankWealth() { return false; }

    @ConfigSection(
        name = "Screenshots",
        description = "Choose which in-game moments RuneFolio may capture and upload.",
        position = 10,
        closedByDefault = true
    )
    String screenshotsSection = "screenshots";

    @ConfigItem(
        keyName = "autoOpenCharacterSetup",
        name = "Open setup for new characters",
        description = "Automatically open RuneFolio in your browser when an unrecognized character logs in",
        position = 0
    )
    default boolean autoOpenCharacterSetup()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showCollectionLogSyncButton",
        name = "Collection Log sync button",
        description = "Show a smart RuneFolio sync button in an unused bottom-right area of the in-game Collection Log",
        position = 1
    )
    default boolean showCollectionLogSyncButton()
    {
        return true;
    }

    @ConfigItem(
        keyName = "syncLootDrops",
        warning = RuneFolioDataSharing.LOOT,
        name = "Sync loot drops",
        description = "Automatically send loot recorded by RuneLite's enabled Loot Tracker to your RuneFolio history",
        position = 2
    )
    default boolean syncLootDrops()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hideSidePanel",
        name = "Hide RuneFolio side panel",
        description = "Hide the RuneFolio button from the RuneLite side panel without disabling background syncing",
        position = 3
    )
    default boolean hideSidePanel()
    {
        return false;
    }

    @ConfigItem(
        keyName = "uploadScreenshots",
        warning = RuneFolioDataSharing.SCREENSHOTS,
        name = "Upload screenshots",
        description = "Save compressed screenshots locally for staggered upload (500 pictures / 256 MiB). Images may contain personal information. Turning off stops new captures, not pending uploads. Disabled by default.",
        position = 0,
        section = screenshotsSection
    )
    default boolean uploadScreenshots()
    {
        return false;
    }

    @ConfigItem(
        keyName = "hideChatInScreenshots",
        name = "Hide chat and private messages",
        description = "Temporarily hide the chat area and private-message overlay while RuneFolio captures a frame.",
        position = 1,
        section = screenshotsSection
    )
    default boolean hideChatInScreenshots()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotLevelUps",
        name = "Level ups",
        description = "Upload screenshots of level-up interfaces.",
        position = 2,
        section = screenshotsSection
    )
    default boolean screenshotLevelUps()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotQuestCompletions",
        name = "Quest completions",
        description = "Upload screenshots of quest-completion interfaces.",
        position = 3,
        section = screenshotsSection
    )
    default boolean screenshotQuestCompletions()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotDiaryCompletions",
        name = "Diary tasks",
        description = "Upload screenshots when an Achievement Diary task is completed.",
        position = 4,
        section = screenshotsSection
    )
    default boolean screenshotDiaryCompletions()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotCombatAchievements",
        name = "Combat Achievements",
        description = "Upload screenshots when a Combat Achievement is completed.",
        position = 5,
        section = screenshotsSection
    )
    default boolean screenshotCombatAchievements()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotCollectionLogUnlocks",
        name = "Collection Log unlocks",
        description = "Upload screenshots when a new Collection Log item is announced.",
        position = 6,
        section = screenshotsSection
    )
    default boolean screenshotCollectionLogUnlocks()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotPets",
        name = "Pets",
        description = "Upload screenshots when the game announces a newly received pet.",
        position = 7,
        section = screenshotsSection
    )
    default boolean screenshotPets()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotValuableDrops",
        name = "High-value drops",
        description = "Upload a screenshot when a RuneLite loot event reaches the configured GE value.",
        position = 8,
        section = screenshotsSection
    )
    default boolean screenshotValuableDrops()
    {
        return true;
    }

    @Range(min = 0, max = 2_147_483_647)
    @ConfigItem(
        keyName = "screenshotValuableDropThreshold",
        name = "High-value threshold",
        description = "Minimum total GE value of a loot event to upload a screenshot.",
        position = 9,
        section = screenshotsSection
    )
    default int screenshotValuableDropThreshold()
    {
        return 1_000_000;
    }

    @ConfigItem(
        keyName = "screenshotUntradeableDrops",
        name = "Untradeable drops",
        description = "Upload a screenshot when a RuneLite loot event contains an untradeable item.",
        position = 10,
        section = screenshotsSection
    )
    default boolean screenshotUntradeableDrops()
    {
        return true;
    }
}
