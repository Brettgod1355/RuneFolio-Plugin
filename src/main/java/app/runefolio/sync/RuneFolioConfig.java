package app.runefolio.sync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("runefolio")
public interface RuneFolioConfig extends Config
{
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
}
