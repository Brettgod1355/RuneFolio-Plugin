package app.runefolio.sync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RuneFolioPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(RuneFolioPlugin.class);
        RuneLite.main(args);
    }
}
