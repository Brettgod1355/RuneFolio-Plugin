package app.runefolio.sync;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import org.junit.Test;

import static org.junit.Assert.*;

public class RuneFolioPanelStateTest
{
    @Test
    public void accountConnectionLocksTemporaryCodesUntilDisconnected() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            RuneFolioPanel panel = new RuneFolioPanel(() -> true);
            JTextField code = (JTextField) find(panel, JTextField.class, null);
            JButton temporary = (JButton) find(panel, JButton.class, "Connect character");
            JButton login = (JButton) find(panel, JButton.class, "Log in to RuneFolio");
            JButton disconnect = (JButton) find(panel, JButton.class, "Disconnect account");
            JButton setup = (JButton) find(panel, JButton.class, "Add to RuneFolio");
            assertTrue(login.isVisible()); assertFalse(disconnect.isVisible()); assertFalse(setup.isVisible());
            assertTrue(code.isEnabled()); assertTrue(temporary.isEnabled());

            panel.setAccountConnected(true);
            assertFalse(code.isEnabled()); assertFalse(temporary.isEnabled());
            assertFalse(login.isVisible()); assertTrue(disconnect.isVisible());
            panel.setConnecting(true);
            panel.setConnecting(false);
            assertFalse("temporary code stays locked while an account is connected", temporary.isEnabled());
            assertFalse(code.isEnabled());

            panel.setAccountConnecting(true);
            assertEquals("Waiting for browser...", login.getText());
            assertFalse(login.isEnabled());
            assertFalse(disconnect.isEnabled());
            panel.setAccountConnecting(false);
            assertEquals("Log in to RuneFolio", login.getText());
            assertTrue(login.isEnabled());
            assertTrue(disconnect.isEnabled());

            panel.showCharacterSetup();
            assertTrue(setup.isVisible());
            panel.hideCharacterSetup();
            assertFalse(setup.isVisible());

            panel.setAccountConnected(false);
            assertTrue(code.isEnabled()); assertTrue(temporary.isEnabled());
            assertTrue(login.isVisible()); assertFalse(disconnect.isVisible());
            panel.setConnecting(true);
            assertEquals("Connecting...", temporary.getText());
            assertFalse(code.isEnabled()); assertFalse(temporary.isEnabled());
            panel.setConnecting(false);
            assertEquals("Connect character", temporary.getText());
            assertTrue(code.isEnabled()); assertTrue(temporary.isEnabled());
        });
    }

    @Test
    public void statusShowsRawTextAndColoursByOutcome() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            RuneFolioPanel panel = new RuneFolioPanel(() -> true);
            panel.setStatus("Connection revoked & <expired>");
            JTextArea status = findTextArea(panel, "revoked");
            assertNotNull(status);
            assertEquals("Connection revoked & <expired>", status.getText());
            assertEquals(RuneFolioPanel.ERROR_TEXT, status.getForeground());

            panel.setStatus("Connected to Zezima. Automatic sync is ready.");
            assertEquals("Connected to Zezima. Automatic sync is ready.", status.getText());
            assertEquals(RuneFolioPanel.SUCCESS_TEXT, status.getForeground());

            panel.setStatus("Checking the RuneFolio connection...");
            assertEquals(RuneFolioPanel.PRIMARY_TEXT, status.getForeground());

            panel.setStatus(null);
            assertEquals("", status.getText());
        });
    }

    @Test
    public void invalidLinksAreReportedInsteadOfThrown() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            RuneFolioPanel panel = new RuneFolioPanel(() -> true);
            assertFalse(panel.openBrowser("javascript:alert(1)"));
            assertFalse(panel.openBrowser("/verify?code=1 2"));
            assertFalse(panel.openBrowser(null));
            JTextArea status = findTextArea(panel, "invalid link");
            assertNotNull(status);
            assertEquals("RuneFolio returned an invalid link; open runefolio.app manually.", status.getText());
        });
    }

    @Test
    public void settingsPageMirrorsEveryConfigItem() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                Map<String, Object> recorded = new LinkedHashMap<>();
                RuneFolioPanel panel = new RuneFolioPanel(() -> true);
                panel.configure(recorded::put);
                RuneFolioConfig config = new RuneFolioConfig() { };

                Set<String> booleanKeys = new LinkedHashSet<>();
                for (Method method : RuneFolioConfig.class.getMethods())
                {
                    ConfigItem item = method.getAnnotation(ConfigItem.class);
                    if (item == null || method.getReturnType() != boolean.class)
                    {
                        continue;
                    }
                    booleanKeys.add(item.keyName());
                    JCheckBox box = panel.toggleBoxes.get(item.keyName());
                    assertNotNull(item.keyName(), box);
                    assertEquals(item.keyName(), item.name(), box.getText());
                    assertEquals(item.keyName(), item.description(), box.getToolTipText());
                }
                assertEquals(booleanKeys, panel.toggleBoxes.keySet());

                panel.syncSettings(config);
                for (Method method : RuneFolioConfig.class.getMethods())
                {
                    ConfigItem item = method.getAnnotation(ConfigItem.class);
                    if (item != null && method.getReturnType() == boolean.class)
                    {
                        assertEquals(item.keyName(), method.invoke(config), panel.toggleBoxes.get(item.keyName()).isSelected());
                    }
                }
                assertTrue(recorded.isEmpty());

                Method threshold = RuneFolioConfig.class.getMethod("screenshotValuableDropThreshold");
                JSpinner spinner = (JSpinner) find(panel, JSpinner.class, null);
                assertNotNull(spinner);
                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                assertEquals(threshold.getAnnotation(Range.class).min(), model.getMinimum());
                assertEquals(RuneFolioPanel.MIN_VALUABLE_DROP_THRESHOLD, model.getMinimum());
                assertEquals(config.screenshotValuableDropThreshold(), spinner.getValue());

                spinner.setValue(0);
                assertEquals(RuneFolioPanel.MIN_VALUABLE_DROP_THRESHOLD, spinner.getValue());
                assertEquals(RuneFolioPanel.MIN_VALUABLE_DROP_THRESHOLD, recorded.get("screenshotValuableDropThreshold"));

                spinner.setValue(2_500_000);
                assertEquals(2_500_000, recorded.get("screenshotValuableDropThreshold"));
            }
            catch (ReflectiveOperationException e)
            {
                throw new AssertionError(e);
            }
        });
    }

    private static JTextArea findTextArea(Container root, String containing)
    {
        for (Component child : root.getComponents())
        {
            if (child instanceof JTextArea && ((JTextArea) child).getText().contains(containing))
            {
                return (JTextArea) child;
            }
            if (child instanceof Container)
            {
                JTextArea result = findTextArea((Container) child, containing);
                if (result != null)
                {
                    return result;
                }
            }
        }
        return null;
    }

    private static Component find(Container root, Class<?> type, String text)
    {
        for (Component child : root.getComponents())
        {
            if (type.isInstance(child)
                && (text == null || (child instanceof JButton && text.equals(((JButton) child).getText()))))
            {
                return child;
            }
            if (child instanceof Container)
            {
                Component result = find((Container) child, type, text);
                if (result != null)
                {
                    return result;
                }
            }
        }
        return null;
    }

    @Test
    public void onlyAbsoluteHttpLinksAreOpenable()
    {
        assertTrue(RuneFolioPanel.isOpenableLink("https://runefolio.app/plugin-auth/verify?code=abc"));
        assertTrue(RuneFolioPanel.isOpenableLink("http://localhost:3000/verify"));
        assertFalse(RuneFolioPanel.isOpenableLink(null));
        assertFalse(RuneFolioPanel.isOpenableLink(" "));
        assertFalse(RuneFolioPanel.isOpenableLink("/verify?code=abc"));
        assertFalse(RuneFolioPanel.isOpenableLink("javascript:alert(1)"));
        assertFalse(RuneFolioPanel.isOpenableLink("file:///etc/passwd"));
        assertFalse(RuneFolioPanel.isOpenableLink("https://runefolio.app/verify?code=1 2"));
    }
}
