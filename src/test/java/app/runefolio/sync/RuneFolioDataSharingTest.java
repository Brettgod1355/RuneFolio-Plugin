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
