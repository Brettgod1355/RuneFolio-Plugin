package app.runefolio.sync;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuneFolioDataSharingTest
{
    @Test
    public void localScreenshotDeletionRequiresSeparateConfirmation() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean accepted = new AtomicBoolean();
            AtomicInteger clears = new AtomicInteger();
            RuneFolioPanel panel = new RuneFolioPanel(() -> true, accepted::get);
            panel.setClearScreenshotsAction(clears::incrementAndGet);
            JButton clear = (JButton) find(panel, JButton.class, "Clear screenshot queue");
            assertNotNull(clear);
            clear.doClick();
            assertEquals(0, clears.get());
            accepted.set(true);
            clear.doClick();
            assertEquals(1, clears.get());
        });
        String limits = RuneFolioScreenshotSpool.MAX_FILES + " pictures / " + (RuneFolioScreenshotSpool.MAX_BYTES >> 20) + " MiB";
        assertEquals(limits, RuneFolioDataSharing.SPOOL_LIMITS);
        assertTrue(RuneFolioDataSharing.SCREENSHOTS.contains(limits));
        assertTrue(RuneFolioDataSharing.CONNECTION.contains(limits));
        assertTrue(RuneFolioConfig.class.getMethod("uploadScreenshots").getAnnotation(ConfigItem.class).description().contains(limits));
        assertTrue(RuneFolioDataSharing.SCREENSHOTS.contains("unencrypted"));
    }

    @Test
    public void uploadControlsDeclareNativeWarningsAndRetainOptInDefaults() throws Exception
    {
        for (String method : new String[]{"syncBankWealth", "uploadScreenshots", "syncLootDrops", "syncCompletionHistory"})
        {
            ConfigItem item = RuneFolioConfig.class.getMethod(method).getAnnotation(ConfigItem.class);
            assertNotNull(item);
            assertTrue(item.warning().contains("https://runefolio.app"));
            assertTrue(item.warning().contains("temporary-code owner"));
        }
        RuneFolioConfig config = new RuneFolioConfig() { };
        assertFalse(config.syncBankWealth());
        assertFalse(config.uploadScreenshots());
        assertFalse(config.syncPvpHistory());
        assertTrue(config.syncAccountUnlocks());
        assertTrue(RuneFolioDataSharing.CONNECTION.contains("Bank/inventory/equipment snapshots, PvP history and screenshots are optional and off by default."));
        assertTrue(RuneFolioDataSharing.CONNECTION.contains("Account-unlock observations (supported game flags plus sightings of selected checklist items) are on by default"));
        assertFalse(RuneFolioDataSharing.CONNECTION.contains("Account-unlock observations, bank/inventory/equipment snapshots, PvP history and screenshots are optional and off by default"));
        assertTrue(RuneFolioDataSharing.CONNECTION.contains("RuneLite profile cloud sync"));
        assertTrue(RuneFolioDataSharing.BANK.contains("RuneLite profile cloud sync"));
        // The master uploadScreenshots switch is opt-in; once a user turns it on,
        // every capture sub-category (including these reward screens) is on by default.
        assertTrue(config.screenshotClueRewards());
        assertTrue(config.screenshotRaidChestRewards());
        assertTrue(config.screenshotPvpKills());
        assertTrue(config.screenshotLootKeys());
        ConfigItem pvp = RuneFolioConfig.class.getMethod("syncPvpHistory").getAnnotation(ConfigItem.class);
        assertTrue(pvp.warning().contains("https://runefolio.app"));
        assertTrue(pvp.warning().contains("defeated player names"));
        assertTrue(pvp.warning().contains("temporary-code issuer"));
    }

    @Test
    public void cancelledConnectionsDoNotInvokeAccountOrTemporaryActions() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean accepted = new AtomicBoolean(false);
            AtomicInteger accountCalls = new AtomicInteger();
            AtomicInteger temporaryCalls = new AtomicInteger();
            RuneFolioPanel panel = new RuneFolioPanel(accepted::get);
            panel.setAccountConnectAction(accountCalls::incrementAndGet);
            panel.setTemporaryConnectAction(code -> temporaryCalls.incrementAndGet());
            ((JTextField) find(panel, JTextField.class, null)).setText("RF-EXAMPLE");
            JButton account = (JButton) find(panel, JButton.class, "Log in to RuneFolio");
            JButton temporary = (JButton) find(panel, JButton.class, "Connect character");
            account.doClick(); temporary.doClick();
            assertEquals(0, accountCalls.get()); assertEquals(0, temporaryCalls.get());
            accepted.set(true);
            account.doClick(); temporary.doClick();
            assertEquals(1, accountCalls.get()); assertEquals(1, temporaryCalls.get());
        });
    }

    private static Component find(Container root, Class<?> type, String text)
    {
        for (Component child : root.getComponents())
        {
            if (type.isInstance(child) && (text == null || text.equals(((JButton) child).getText()))) return child;
            if (child instanceof Container)
            {
                Component result = find((Container) child, type, text);
                if (result != null) return result;
            }
        }
        return null;
    }
}
