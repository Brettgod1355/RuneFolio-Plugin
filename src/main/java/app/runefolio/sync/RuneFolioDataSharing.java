package app.runefolio.sync;

final class RuneFolioDataSharing
{
    private RuneFolioDataSharing() { }

    static final String CONNECTION = "RuneFolio sends data to https://runefolio.app, a third-party service independent of RuneLite and Jagex.\n\n"
        + "Connecting enables upload of character names, a derived character identity, skill XP/levels, quest and diary progress, combat achievements, Collection Log progress, and sync timestamps. Enabled loot and completion-history settings also send rewards and boss/raid, clue and Slayer results.\n\n"
        + "Bank/inventory/equipment snapshots and screenshots are optional and off by default. Existing enabled settings continue to apply when reconnecting. Screenshots may contain visible personal information; chat hiding does not mask every interface.\n\n"
        + "Account login sends data to the connected RuneFolio account. A temporary code sends that character's data to the account that issued the code. The receiving account controls website sharing.\n\n"
        + "Events are queued locally for retries. Turning a collector off stops new capture, but queued uploads and server history remain. Disconnect or revoke the connection to stop authorized uploads. RuneFolio does not request your Jagex password.\n\n"
        + "Continue to connect?";

    static final String BANK = "<html><body style='width:360px'>Change bank and wealth sharing?<br><br>When enabled, this sends bank, inventory and equipment item names, IDs, quantities and estimated values to https://runefolio.app when you open your bank.<br><br>"
        + "Data goes to the connected account (or temporary-code owner's account). Pending snapshots are saved locally for retries. Turning this off stops new capture; queued uploads and server history remain.</body></html>";
    static final String SCREENSHOTS = "<html><body style='width:360px'>Change screenshot sharing?<br><br>When enabled, this captures selected in-game moments and uploads compressed screenshots to https://runefolio.app.<br><br>"
        + "Images go to the connected account (or temporary-code owner's account) and follow its website sharing settings. Images can contain visible personal information. Chat hiding does not mask every interface. Existing uploaded images remain when this is turned off.</body></html>";
    static final String LOOT = "<html><body style='width:360px'>Change loot sharing?<br><br>When enabled, this sends observed loot sources, item names/IDs, quantities, values and timestamps from RuneLite's Loot Tracker to https://runefolio.app.<br><br>"
        + "Data goes to the connected account (or temporary-code owner's account). Turning this off stops new loot capture; queued uploads and server history remain.</body></html>";
    static final String COMPLETIONS = "<html><body style='width:360px'>Change completion-history sharing?<br><br>When enabled, this sends observed boss/raid, clue and Slayer completions, timestamps and available counts, times, party sizes, points and rewards to https://runefolio.app.<br><br>"
        + "Data goes to the connected account (or temporary-code owner's account). Turning this off stops new completion capture; queued uploads and server history remain.</body></html>";
}
