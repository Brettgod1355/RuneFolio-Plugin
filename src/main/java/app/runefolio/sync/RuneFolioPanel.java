package app.runefolio.sync;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;

@Slf4j
class RuneFolioPanel extends PluginPanel
{
    private static final String RUNE_FOLIO_URL = "https://runefolio.app";
    private static final String DISCORD_URL = "https://discord.gg/Ar96ueFUuj";
    private static final String GITHUB_URL = "https://github.com/Brettgod1355/RuneFolio-Plugin";
    private static final Color GOLD = new Color(217, 184, 97);
    static final Color PRIMARY_TEXT = new Color(232, 228, 216);
    private static final Color MUTED_TEXT = new Color(190, 184, 166);
    static final Color SUCCESS_TEXT = new Color(131, 194, 113);
    static final Color ERROR_TEXT = new Color(230, 119, 107);
    static final int MIN_VALUABLE_DROP_THRESHOLD = 500_000;
    // PANEL_WIDTH minus this panel's 12px left/right padding. A maximum-size cap, not a
    // guarantee: metric rows (see createMetricRow) still need short enough text to fit.
    private static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 24;
    private static final DateTimeFormatter SYNC_TIME_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final ImageIcon DISCORD_ICON;
    private static final ImageIcon GITHUB_ICON;

    static
    {
        DISCORD_ICON = new ImageIcon(ImageUtil.resizeImage(ImageUtil.loadImageResource(RuneFolioPanel.class, "/discord.png"), 16, 16));
        GITHUB_ICON = new ImageIcon(ImageUtil.resizeImage(ImageUtil.loadImageResource(RuneFolioPanel.class, "/github.png"), 16, 16));
    }

    private final JLabel characterValue = new JLabel("Log in to RuneLite");
    private final JTextArea statusValue = new JTextArea();
    private final JTextField codeField = new JTextField();
    private final JTextArea temporaryModeNotice = new JTextArea();
    private final JPanel accountDetails = new JPanel();
    private final JPanel temporaryDetails = new JPanel();
    private final JButton accountSectionButton = new JButton();
    private final JButton accountSectionChevron = new JButton();
    private final JButton temporarySectionButton = new JButton();
    private final JButton temporarySectionChevron = new JButton();
    private final JButton temporaryConnectButton = new JButton("Connect character");
    private final JButton accountConnectButton = new JButton("Log in to RuneFolio");
    private final JButton accountDisconnectButton = new JButton("Disconnect account");
    private final JButton characterSetupButton = new JButton("Add to RuneFolio");
    private final JButton syncNowButton = new JButton("Sync now");
    private final JLabel lastSyncValue = metricValue("Never");
    private final JLabel pendingEventsValue = metricValue("0");
    private final JLabel screenshotQueueValue = metricValue("0 saved");
    private Runnable clearScreenshotsAction;
    private Consumer<String> temporaryConnectAction;
    private Runnable accountConnectAction;
    private Runnable accountDisconnectAction;
    private Runnable characterSetupAction;
    private Runnable manualSyncAction;
    private boolean accountConnected;
    private boolean accountExpanded = true;
    private boolean temporaryExpanded;
    private boolean temporaryConnecting;
    private final BooleanSupplier confirmConnection;
    private final BooleanSupplier confirmClearScreenshots;
    private final CardLayout cards = new CardLayout();
    // CardLayout's own getPreferredSize() is the max of every card, including whichever
    // one is currently hidden - report only the visible card's size instead, so switching
    // to the (much longer) settings card doesn't inflate the main card's reported height.
    private final JPanel body = new JPanel(cards)
    {
        @Override
        public Dimension getPreferredSize()
        {
            for (Component child : getComponents())
            {
                if (child.isVisible())
                {
                    return child.getPreferredSize();
                }
            }
            return super.getPreferredSize();
        }
    };
    private final JButton gearButton = new JButton("⚙");
    final Map<String, JCheckBox> toggleBoxes = new LinkedHashMap<>();
    private JSpinner thresholdSpinner;
    private boolean settingsOpen;
    private boolean syncingSettings;

    RuneFolioPanel()
    {
        this(null);
    }

    RuneFolioPanel(BooleanSupplier confirmation)
    {
        this(confirmation, null);
    }

    RuneFolioPanel(BooleanSupplier confirmation, BooleanSupplier clearConfirmation)
    {
        super();
        confirmConnection = confirmation == null ? this::showConnectionDisclosure : confirmation;
        confirmClearScreenshots = clearConfirmation == null ? this::showClearScreenshotConfirmation : clearConfirmation;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(14, 12, 0, 12));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brand.setOpaque(false);
        JLabel brandIcon = new JLabel(new ImageIcon(RuneFolioBrand.createIcon(28)));
        brandIcon.setToolTipText("RuneFolio");
        brand.add(brandIcon);

        JLabel title = new JLabel("RuneFolio");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(GOLD);
        brand.add(title);
        header.add(brand, BorderLayout.CENTER);

        gearButton.setFont(gearButton.getFont().deriveFont(Font.PLAIN, 18f));
        gearButton.setMargin(new Insets(0, 0, 0, 0));
        gearButton.setPreferredSize(new Dimension(28, 28));
        SwingUtil.removeButtonDecorations(gearButton);
        gearButton.setUI(new BasicButtonUI());
        gearButton.setForeground(GOLD);
        gearButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        gearButton.setFocusPainted(false);
        gearButton.setToolTipText("RuneFolio settings");
        gearButton.getAccessibleContext().setAccessibleName("RuneFolio settings");
        gearButton.addActionListener(event ->
        {
            settingsOpen = !settingsOpen;
            showCard();
        });
        header.add(gearButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(BorderFactory.createEmptyBorder(6, 12, 14, 12));

        JLabel subtitle = new JLabel("v" + RuneFolioApiClient.CLIENT_VERSION + " · Sync client");
        subtitle.setForeground(MUTED_TEXT);
        subtitle.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        content.add(subtitle);
        content.add(Box.createRigidArea(new Dimension(0, 14)));

        content.add(createSectionHeader(
            "RUNEFOLIO ACCOUNT",
            accountSectionButton,
            accountSectionChevron,
            () -> setAccountExpanded(!accountExpanded)
        ));

        configureDetailsPanel(accountDetails);
        JTextArea accountNotice = new JTextArea(
            "Recommended. Connect once in your browser to sync every character on your RuneFolio account."
        );
        configureWrappedText(accountNotice, MUTED_TEXT, 72);
        accountNotice.setBorder(BorderFactory.createEmptyBorder(5, 0, 8, 0));
        accountDetails.add(accountNotice);

        accountConnectButton.setAlignmentX(LEFT_ALIGNMENT);
        accountConnectButton.addActionListener(event ->
        {
            if (accountConnectAction != null && confirmConnection.getAsBoolean())
            {
                accountConnectAction.run();
            }
        });
        accountDetails.add(accountConnectButton);

        accountDisconnectButton.setAlignmentX(LEFT_ALIGNMENT);
        accountDisconnectButton.setVisible(false);
        accountDisconnectButton.addActionListener(event ->
        {
            if (accountDisconnectAction != null)
            {
                accountDisconnectAction.run();
            }
        });
        accountDetails.add(accountDisconnectButton);
        content.add(accountDetails);
        content.add(Box.createRigidArea(new Dimension(0, 16)));

        content.add(sectionLabel("RUNESCAPE CHARACTER"));
        characterValue.setForeground(PRIMARY_TEXT);
        characterValue.setBorder(BorderFactory.createEmptyBorder(5, 0, 16, 0));
        content.add(characterValue);

        content.add(sectionLabel("CONNECTION STATUS"));
        configureWrappedText(statusValue, PRIMARY_TEXT, 100);
        statusValue.setBorder(BorderFactory.createEmptyBorder(6, 0, 16, 0));
        content.add(statusValue);
        setStatus("Log in to RuneFolio, or use a temporary code for one character.");

        characterSetupButton.setAlignmentX(LEFT_ALIGNMENT);
        characterSetupButton.setVisible(false);
        characterSetupButton.addActionListener(event ->
        {
            if (characterSetupAction != null)
            {
                characterSetupAction.run();
            }
        });
        content.add(characterSetupButton);

        content.add(sectionLabel("SYNC ACTIVITY"));
        JPanel syncActivity = new JPanel();
        syncActivity.setLayout(new BoxLayout(syncActivity, BoxLayout.Y_AXIS));
        syncActivity.setOpaque(false);
        syncActivity.setAlignmentX(LEFT_ALIGNMENT);
        syncActivity.setMaximumSize(new Dimension(CONTENT_WIDTH, 110));
        syncActivity.add(createMetricRow("Last sync", "Last successful sync", lastSyncValue));
        syncActivity.add(createMetricRow("Pending", "Pending events waiting to sync", pendingEventsValue));
        syncActivity.add(createMetricRow("Screenshots", "Local screenshots saved, waiting to upload", screenshotQueueValue));
        syncActivity.setBorder(BorderFactory.createEmptyBorder(5, 0, 8, 0));
        content.add(syncActivity);

        syncNowButton.setAlignmentX(LEFT_ALIGNMENT);
        syncNowButton.addActionListener(event ->
        {
            if (manualSyncAction != null)
            {
                manualSyncAction.run();
            }
        });
        content.add(syncNowButton);
        content.add(Box.createRigidArea(new Dimension(0, 8)));

        JButton openRuneFolio = new JButton("Open RuneFolio");
        openRuneFolio.setAlignmentX(LEFT_ALIGNMENT);
        openRuneFolio.addActionListener(event -> openBrowser(RUNE_FOLIO_URL));
        content.add(openRuneFolio);
        JButton sharingInfo = new JButton("Data sharing information");
        sharingInfo.setAlignmentX(LEFT_ALIGNMENT);
        sharingInfo.addActionListener(event -> JOptionPane.showMessageDialog(this, disclosureText(),
            "RuneFolio data sharing", JOptionPane.INFORMATION_MESSAGE));
        content.add(sharingInfo);
        JButton clearScreenshots = new JButton("Clear screenshot queue");
        clearScreenshots.setToolTipText("Deletes screenshots saved locally on this computer, waiting to upload. Website images are unchanged.");
        clearScreenshots.setAlignmentX(LEFT_ALIGNMENT);
        clearScreenshots.addActionListener(event -> {
            if (clearScreenshotsAction != null && confirmClearScreenshots.getAsBoolean())
                clearScreenshotsAction.run();
        });
        content.add(clearScreenshots);
        content.add(Box.createRigidArea(new Dimension(0, 16)));

        content.add(createSectionHeader(
            "TEMPORARY CODE",
            temporarySectionButton,
            temporarySectionChevron,
            () -> setTemporaryExpanded(!temporaryExpanded)
        ));

        configureDetailsPanel(temporaryDetails);
        configureWrappedText(temporaryModeNotice, MUTED_TEXT, 66);
        temporaryModeNotice.setText(
            "Disconnect the RuneFolio account above to use a temporary code. You can stay signed in on the RuneFolio website."
        );
        temporaryModeNotice.setBorder(BorderFactory.createEmptyBorder(5, 0, 8, 0));
        temporaryModeNotice.setVisible(false);
        temporaryDetails.add(temporaryModeNotice);

        codeField.setMaximumSize(new Dimension(CONTENT_WIDTH, 30));
        codeField.setAlignmentX(LEFT_ALIGNMENT);
        codeField.setBackground(new Color(31, 31, 31));
        codeField.setForeground(PRIMARY_TEXT);
        codeField.setCaretColor(GOLD);
        codeField.setSelectionColor(new Color(100, 79, 36));
        codeField.setSelectedTextColor(Color.WHITE);
        codeField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        codeField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(125, 125, 125)),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        codeField.setToolTipText("Paste the one-time RF-XXXX code from RuneFolio settings");
        temporaryDetails.add(codeField);
        temporaryDetails.add(Box.createRigidArea(new Dimension(0, 8)));

        temporaryConnectButton.setAlignmentX(LEFT_ALIGNMENT);
        temporaryConnectButton.addActionListener(event -> submitCode());
        temporaryDetails.add(temporaryConnectButton);
        temporaryDetails.add(Box.createRigidArea(new Dimension(0, 12)));

        JTextArea notice = new JTextArea(
            "Temporary codes are designed for shared computers. Each connects one character, and you can revoke access online from RuneFolio settings."
        );
        configureWrappedText(notice, MUTED_TEXT, 100);
        temporaryDetails.add(notice);
        content.add(temporaryDetails);
        content.add(Box.createRigidArea(new Dimension(0, 16)));

        JPanel links = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        links.setOpaque(false);
        links.setAlignmentX(LEFT_ALIGNMENT);
        links.setMaximumSize(new Dimension(CONTENT_WIDTH, 32));
        links.add(createLinkIconButton(DISCORD_ICON, "Join the RuneFolio Discord", DISCORD_URL));
        links.add(createLinkIconButton(GITHUB_ICON, "RuneFolio plugin on GitHub", GITHUB_URL));
        content.add(links);

        setAccountExpanded(true);
        setTemporaryExpanded(false);

        // Scrolling comes from PluginPanel's default wrapping in super(); this panel adds
        // no scrollbar of its own.
        body.setOpaque(false);
        body.add(content, "main");
        add(body, BorderLayout.CENTER);
    }

    private void showCard()
    {
        cards.show(body, settingsOpen ? "settings" : "main");
        gearButton.setText(settingsOpen ? "⬅" : "⚙");
        gearButton.setToolTipText(settingsOpen ? "Back to RuneFolio" : "RuneFolio settings");
    }

    /**
     * Builds the mirrored settings page. There is no public RuneLite API to open a plugin's
     * real config panel from a sidebar button (the class that owns it, TopLevelConfigPanel,
     * is package-private), so this reproduces the same toggles here and writes through the
     * same {@link net.runelite.client.config.ConfigManager} keys the real panel uses.
     */
    void configure(BiConsumer<String, Object> setting)
    {
        JPanel settings = new JPanel();
        settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
        settings.setBackground(ColorScheme.DARK_GRAY_COLOR);
        settings.setBorder(BorderFactory.createEmptyBorder(10, 12, 14, 12));

        JTextArea settingsNotice = new JTextArea(
            "Mirrors this plugin's real RuneLite settings (sidebar wrench icon → RuneFolio). Changing either one updates the other."
        );
        configureWrappedText(settingsNotice, MUTED_TEXT, 46);
        settingsNotice.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        settings.add(settingsNotice);

        settings.add(sectionLabel("SYNC"));
        settings.add(Box.createRigidArea(new Dimension(0, 6)));
        addToggle(settings, setting, "autoOpenCharacterSetup", "Open setup for new characters",
            "Automatically open RuneFolio in your browser when an unrecognized character logs in", null);
        addToggle(settings, setting, "showCollectionLogSyncButton", "Collection Log sync button",
            "Show a smart RuneFolio sync button in an unused bottom-right area of the in-game Collection Log", null);
        addToggle(settings, setting, "syncLootDrops", "Sync loot drops",
            "Automatically send loot recorded by RuneLite's enabled Loot Tracker to your RuneFolio history", RuneFolioDataSharing.LOOT);
        addToggle(settings, setting, "hideSidePanel", "Hide RuneFolio side panel",
            "Hide the RuneFolio button from the RuneLite side panel without disabling background syncing", null);
        addToggle(settings, setting, "syncBankWealth", "Sync bank and wealth",
            "Opt-in: send bank, inventory and equipment items and estimated values while your bank is open. Pending snapshots are saved locally for retries.", RuneFolioDataSharing.BANK);
        addToggle(settings, setting, "syncCompletionHistory", "Sync completion history",
            "Send observed boss/raid, clue and Slayer completions and available result details to RuneFolio. Clue rewards use the enabled native Loot Tracker.", RuneFolioDataSharing.COMPLETIONS);
        addToggle(settings, setting, "syncPvpHistory", "Sync PvP history",
            "Opt-in: send your observed finishing blows, opponent names, timestamps and observed loot. Native Loot Tracker supplies unassigned loot-key contents. No opponent gear or location is collected.", RuneFolioDataSharing.PVP);
        addToggle(settings, setting, "syncAccountUnlocks", "Sync account unlocks",
            "Send supported account unlock flags and observations of checklist items, including Sea Charting task completion. No full bank contents are sent by this setting. Unknown entries can be confirmed on the website.", RuneFolioDataSharing.UNLOCKS);

        settings.add(Box.createRigidArea(new Dimension(0, 10)));
        settings.add(sectionLabel("SCREENSHOTS"));
        settings.add(Box.createRigidArea(new Dimension(0, 6)));
        addToggle(settings, setting, "uploadScreenshots", "Upload screenshots",
            "Save compressed screenshots locally for staggered upload (" + RuneFolioDataSharing.SPOOL_LIMITS + "). Images may contain personal information. Turning off stops new captures, not pending uploads. Disabled by default.", RuneFolioDataSharing.SCREENSHOTS);
        addToggle(settings, setting, "hideChatInScreenshots", "Hide chat and private messages",
            "Temporarily hide the chat area and private-message overlay while RuneFolio captures a frame.", null);
        addToggle(settings, setting, "screenshotLevelUps", "Level ups",
            "Upload screenshots of level-up interfaces.", null);
        addToggle(settings, setting, "screenshotQuestCompletions", "Quest completions",
            "Upload screenshots of quest-completion interfaces.", null);
        addToggle(settings, setting, "screenshotDiaryCompletions", "Diary tasks",
            "Upload screenshots when an Achievement Diary task is completed.", null);
        addToggle(settings, setting, "screenshotCombatAchievements", "Combat Achievements",
            "Upload screenshots when a Combat Achievement is completed.", null);
        addToggle(settings, setting, "screenshotCollectionLogUnlocks", "Collection Log unlocks",
            "Upload screenshots when a new Collection Log item is announced.", null);
        addToggle(settings, setting, "screenshotPets", "Pets",
            "Upload screenshots when the game announces a newly received pet.", null);
        addToggle(settings, setting, "screenshotValuableDrops", "High-value drops",
            "Upload a screenshot when a RuneLite loot event reaches the configured GE value.", null);

        JLabel thresholdLabel = new JLabel("High-value drops above (GP)");
        thresholdLabel.setForeground(PRIMARY_TEXT);
        thresholdLabel.setAlignmentX(LEFT_ALIGNMENT);
        thresholdLabel.setToolTipText("Minimum total GE value of a loot event to upload a screenshot. Minimum 500,000.");
        thresholdLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        settings.add(thresholdLabel);

        thresholdSpinner = new JSpinner(new SpinnerNumberModel(1_000_000, MIN_VALUABLE_DROP_THRESHOLD, Integer.MAX_VALUE, 100_000));
        thresholdSpinner.setAlignmentX(LEFT_ALIGNMENT);
        thresholdSpinner.setMaximumSize(new Dimension(120, thresholdSpinner.getPreferredSize().height));
        ((JSpinner.DefaultEditor) thresholdSpinner.getEditor()).getTextField().setColumns(8);
        thresholdSpinner.addChangeListener(event ->
        {
            if (syncingSettings)
            {
                return;
            }
            int value = Math.max(MIN_VALUABLE_DROP_THRESHOLD, (Integer) thresholdSpinner.getValue());
            if (!Integer.valueOf(value).equals(thresholdSpinner.getValue()))
            {
                syncingSettings = true;
                try
                {
                    thresholdSpinner.setValue(value);
                }
                finally
                {
                    syncingSettings = false;
                }
            }
            setting.accept("screenshotValuableDropThreshold", value);
        });
        settings.add(thresholdSpinner);
        settings.add(Box.createRigidArea(new Dimension(0, 6)));

        addToggle(settings, setting, "screenshotUntradeableDrops", "Untradeable drops",
            "Upload a screenshot when a RuneLite loot event contains an untradeable item.", null);
        addToggle(settings, setting, "screenshotClueRewards", "Clue reward screens",
            "Capture new clue rewards reported by native Loot Tracker. Enable completion history to link them to clue results.", null);
        addToggle(settings, setting, "screenshotRaidChestRewards", "Raid and chest rewards",
            "Capture supported raid/chest rewards reported by native Loot Tracker.", null);
        addToggle(settings, setting, "screenshotPvpKills", "PvP kills",
            "Capture your observed finishing blows. PvP history is a separate opt-in.", null);
        addToggle(settings, setting, "screenshotLootKeys", "Wilderness loot-key screens",
            "Capture the visible loot-key reward screen reported by native Loot Tracker. A screen can include several keys and does not identify a defeated player.", null);

        JPanel settingsPage = new JPanel(new BorderLayout());
        settingsPage.setBackground(ColorScheme.DARK_GRAY_COLOR);
        settingsPage.add(settings, BorderLayout.NORTH);
        body.add(settingsPage, "settings");
    }

    private void addToggle(JPanel parent, BiConsumer<String, Object> setting, String key, String label,
        String description, String warning)
    {
        JCheckBox box = new JCheckBox(label);
        box.setOpaque(false);
        box.setForeground(PRIMARY_TEXT);
        box.setAlignmentX(LEFT_ALIGNMENT);
        box.setToolTipText(description);
        box.addActionListener(event ->
        {
            if (syncingSettings)
            {
                return;
            }
            boolean newValue = box.isSelected();
            if (warning != null && JOptionPane.showOptionDialog(this, warning, "Are you sure?",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                new String[]{"Yes", "No"}, "No") != JOptionPane.YES_OPTION)
            {
                syncingSettings = true;
                try
                {
                    box.setSelected(!newValue);
                }
                finally
                {
                    syncingSettings = false;
                }
                return;
            }
            setting.accept(key, newValue);
        });
        toggleBoxes.put(key, box);
        parent.add(box);
        parent.add(Box.createRigidArea(new Dimension(0, 2)));
    }

    void syncSettings(RuneFolioConfig config)
    {
        if (toggleBoxes.isEmpty())
        {
            return;
        }
        syncingSettings = true;
        try
        {
            setToggle("autoOpenCharacterSetup", config.autoOpenCharacterSetup());
            setToggle("showCollectionLogSyncButton", config.showCollectionLogSyncButton());
            setToggle("syncLootDrops", config.syncLootDrops());
            setToggle("hideSidePanel", config.hideSidePanel());
            setToggle("syncBankWealth", config.syncBankWealth());
            setToggle("syncCompletionHistory", config.syncCompletionHistory());
            setToggle("syncPvpHistory", config.syncPvpHistory());
            setToggle("syncAccountUnlocks", config.syncAccountUnlocks());
            setToggle("uploadScreenshots", config.uploadScreenshots());
            setToggle("hideChatInScreenshots", config.hideChatInScreenshots());
            setToggle("screenshotLevelUps", config.screenshotLevelUps());
            setToggle("screenshotQuestCompletions", config.screenshotQuestCompletions());
            setToggle("screenshotDiaryCompletions", config.screenshotDiaryCompletions());
            setToggle("screenshotCombatAchievements", config.screenshotCombatAchievements());
            setToggle("screenshotCollectionLogUnlocks", config.screenshotCollectionLogUnlocks());
            setToggle("screenshotPets", config.screenshotPets());
            setToggle("screenshotValuableDrops", config.screenshotValuableDrops());
            setToggle("screenshotUntradeableDrops", config.screenshotUntradeableDrops());
            setToggle("screenshotClueRewards", config.screenshotClueRewards());
            setToggle("screenshotRaidChestRewards", config.screenshotRaidChestRewards());
            setToggle("screenshotPvpKills", config.screenshotPvpKills());
            setToggle("screenshotLootKeys", config.screenshotLootKeys());
            if (thresholdSpinner != null)
            {
                thresholdSpinner.setValue(config.screenshotValuableDropThreshold());
            }
        }
        finally
        {
            syncingSettings = false;
        }
    }

    private void setToggle(String key, boolean value)
    {
        JCheckBox box = toggleBoxes.get(key);
        if (box != null)
        {
            box.setSelected(value);
        }
    }

    void setTemporaryConnectAction(Consumer<String> action)
    {
        temporaryConnectAction = action;
    }

    void setAccountConnectAction(Runnable action)
    {
        accountConnectAction = action;
    }

    void setAccountDisconnectAction(Runnable action)
    {
        accountDisconnectAction = action;
    }

    void setCharacterSetupAction(Runnable action)
    {
        characterSetupAction = action;
    }

    void setManualSyncAction(Runnable action)
    {
        manualSyncAction = action;
    }

    void setClearScreenshotsAction(Runnable action) { clearScreenshotsAction = action; }

    private boolean showClearScreenshotConfirmation()
    {
        return JOptionPane.showConfirmDialog(this,
            "Delete all pictures saved in the local screenshot queue? This cannot be undone.\n"
            + "This queue is shared by clients using this RuneLite settings folder.\n"
            + "Website images are unchanged. New captures may still be saved while enabled.",
            "Clear local screenshot queue", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    void setScreenshotQueueState(String status)
    {
        screenshotQueueValue.setText(status);
        screenshotQueueValue.setToolTipText("Saved locally until uploaded. Held files were rejected permanently or are unreadable and are never retried. Clear screenshot queue deletes both saved and held files.");
        revalidate();
        repaint();
    }

    void setSyncState(long lastSuccessfulSyncAtMillis, int pendingEvents)
    {
        lastSyncValue.setText(lastSuccessfulSyncAtMillis > 0
            ? SYNC_TIME_FORMAT.format(Instant.ofEpochMilli(lastSuccessfulSyncAtMillis))
            : "Never");
        pendingEventsValue.setText(Integer.toString(Math.max(0, pendingEvents)));
        revalidate();
        repaint();
    }

    void showCharacterSetup()
    {
        characterSetupButton.setVisible(true);
        revalidate();
        repaint();
    }

    void hideCharacterSetup()
    {
        characterSetupButton.setVisible(false);
        revalidate();
        repaint();
    }

    void setAccountConnected(boolean connected)
    {
        boolean connectionChanged = accountConnected != connected;
        accountConnected = connected;
        accountConnectButton.setVisible(!connected);
        accountDisconnectButton.setVisible(connected);
        temporaryModeNotice.setVisible(connected);
        codeField.setEnabled(!connected && !temporaryConnecting);
        temporaryConnectButton.setEnabled(!connected && !temporaryConnecting);

        if (connectionChanged)
        {
            setAccountExpanded(!connected);
        }
        else
        {
            revalidate();
            repaint();
        }
    }

    void setAccountConnecting(boolean connecting)
    {
        accountConnectButton.setEnabled(!connecting);
        accountConnectButton.setText(connecting ? "Waiting for browser..." : "Log in to RuneFolio");
        accountDisconnectButton.setEnabled(!connecting);
    }

    void setCharacterName(String characterName)
    {
        characterValue.setText(characterName == null || characterName.isBlank() ? "Log in to RuneLite" : characterName);
    }

    void setStatus(String status)
    {
        String safeStatus = status == null ? "" : status;
        String statusLower = safeStatus.toLowerCase();
        statusValue.setForeground((statusLower.startsWith("connected") || statusLower.contains("sync sent"))
            ? SUCCESS_TEXT
            : (statusLower.contains("failed") || statusLower.contains("revoked") || statusLower.contains("expired")
                || statusLower.contains("mismatch") || statusLower.contains("not connected")
                || statusLower.contains("already connected to another runefolio account")
                ? ERROR_TEXT
                : PRIMARY_TEXT));
        statusValue.setText(safeStatus);
        statusValue.revalidate();
        statusValue.repaint();
    }

    void setConnecting(boolean connecting)
    {
        temporaryConnecting = connecting;
        temporaryConnectButton.setEnabled(!connecting && !accountConnected);
        codeField.setEnabled(!connecting && !accountConnected);
        temporaryConnectButton.setText(connecting ? "Connecting..." : "Connect character");
    }

    void clearCode()
    {
        codeField.setText("");
    }

    boolean openBrowser(String url)
    {
        try
        {
            if (url == null || url.isBlank())
            {
                throw new IllegalArgumentException("empty url");
            }
            LinkBrowser.browse(url);
            return true;
        }
        catch (IllegalArgumentException e)
        {
            log.warn("Refusing to open invalid link {}", url, e);
            setStatus("RuneFolio returned an invalid link; open runefolio.app manually.");
            return false;
        }
    }

    private void setAccountExpanded(boolean expanded)
    {
        accountExpanded = expanded;
        accountDetails.setVisible(expanded);
        accountSectionChevron.setText(expanded ? "\u25bc" : "\u25b6");
        accountSectionChevron.setToolTipText(expanded ? "Collapse RuneFolio account" : "Expand RuneFolio account");
        revalidate();
        repaint();
    }

    private void setTemporaryExpanded(boolean expanded)
    {
        temporaryExpanded = expanded;
        temporaryDetails.setVisible(expanded);
        temporarySectionChevron.setText(expanded ? "\u25bc" : "\u25b6");
        temporarySectionChevron.setToolTipText(expanded ? "Collapse temporary code" : "Expand temporary code");
        revalidate();
        repaint();
    }

    private void configureDetailsPanel(JPanel details)
    {
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);
        details.setAlignmentX(LEFT_ALIGNMENT);
        details.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));
    }

    private JPanel createMetricRow(String labelText, String tooltip, JLabel value)
    {
        return createMetricRow(labelText, tooltip, value, 21);
    }

    private JPanel createMetricRow(String labelText, String tooltip, JLabel value, int height)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(CONTENT_WIDTH, height));
        JLabel label = new JLabel(labelText);
        label.setForeground(MUTED_TEXT);
        label.setToolTipText(tooltip);
        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private static JLabel metricValue(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(PRIMARY_TEXT);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    private JButton createLinkIconButton(ImageIcon icon, String tooltip, String url)
    {
        JButton button = new JButton(icon);
        SwingUtil.removeButtonDecorations(button);
        button.setUI(new BasicButtonUI());
        button.setToolTipText(tooltip);
        button.setBackground(ColorScheme.DARK_GRAY_COLOR);
        button.addActionListener(event -> LinkBrowser.browse(url));
        return button;
    }

    private JPanel createSectionHeader(
        String title,
        JButton titleButton,
        JButton chevronButton,
        Runnable toggleAction
    )
    {
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(new Color(46, 46, 46));
        header.setBorder(BorderFactory.createLineBorder(new Color(76, 76, 76)));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(CONTENT_WIDTH, 32));
        header.setPreferredSize(new Dimension(CONTENT_WIDTH, 32));

        configureSectionToggle(titleButton);
        titleButton.setText(title);
        titleButton.addActionListener(event -> toggleAction.run());

        Dimension chevronSize = new Dimension(30, 30);
        chevronButton.setPreferredSize(chevronSize);
        chevronButton.setMinimumSize(chevronSize);
        chevronButton.setMaximumSize(chevronSize);
        chevronButton.setFont(chevronButton.getFont().deriveFont(Font.BOLD, 16f));
        chevronButton.setForeground(GOLD);
        chevronButton.setBackground(new Color(72, 62, 39));
        chevronButton.setBorder(BorderFactory.createLineBorder(new Color(139, 113, 55)));
        chevronButton.setMargin(new Insets(0, 0, 0, 0));
        chevronButton.setContentAreaFilled(true);
        chevronButton.setOpaque(true);
        chevronButton.setFocusPainted(false);
        chevronButton.addActionListener(event -> toggleAction.run());

        header.add(titleButton, BorderLayout.CENTER);
        header.add(chevronButton, BorderLayout.EAST);
        return header;
    }

    private void configureSectionToggle(JButton button)
    {
        // The same font RuneLite's own ConfigPanel uses for its section names - the
        // bitmap-derived RuneScape font renders noticeably smaller/cramped when scaled
        // to an arbitrary literal size instead of its own designed 16pt.
        button.setFont(FontManager.getRunescapeBoldFont());
        button.setForeground(GOLD);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
    }

    private void submitCode()
    {
        String code = codeField.getText().trim();
        if (code.isEmpty())
        {
            setStatus("Paste a temporary code from RuneFolio settings first.");
            return;
        }
        if (temporaryConnectAction != null && confirmConnection.getAsBoolean())
        {
            temporaryConnectAction.accept(code);
        }
    }

    private JScrollPane disclosureText()
    {
        JTextArea text = new JTextArea(RuneFolioDataSharing.CONNECTION, 20, 42);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setCaretPosition(0);
        return new JScrollPane(text);
    }

    private boolean showConnectionDisclosure()
    {
        return JOptionPane.showOptionDialog(this, disclosureText(), "Connect to RuneFolio",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            new String[]{"Continue", "Cancel"}, "Cancel") == JOptionPane.YES_OPTION;
    }

    private void configureWrappedText(JTextArea textArea, Color color, int maximumHeight)
    {
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setOpaque(false);
        textArea.setForeground(color);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setColumns(25);
        textArea.setMaximumSize(new Dimension(CONTENT_WIDTH, maximumHeight));
        textArea.setAlignmentX(LEFT_ALIGNMENT);
    }

    private JLabel sectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(GOLD);
        return label;
    }
}
