# RuneFolio Sync

RuneFolio Sync connects RuneLite to your [RuneFolio](https://runefolio.app) account. It runs quietly in the background, tracking your Old School RuneScape progress and sending it to RuneFolio, where it's organized into a browsable profile — skills, quests, diaries, Combat Achievements, Collection Log, loot, boss kills, and more.

**This plugin doesn't display your history itself.** It's a data connector, not a viewer. Connect once, then open your profile at [runefolio.app](https://runefolio.app) to see everything it's synced.

## Getting started

1. Install the plugin and open the RuneFolio panel in the sidebar (look for the gold "R").
2. Choose how to connect:
   - **Log in to RuneFolio** (recommended) — opens your browser to sign in or create an account once. Each character you log into afterward is added to your account with a one-time confirmation, then syncs automatically.
   - **Connect character** — generate a one-time code from your RuneFolio account on the website, then enter it here to sync just that one character. You still need a RuneFolio account to generate the code; this just avoids linking a whole RuneLite installation to your account, which is useful on a computer you don't own or don't want every character on it syncing from — such as a friend's.
3. Log in to your character. If it isn't on your RuneFolio account yet (account-login mode only), the plugin reminds you in the chat box and, once per character, opens the RuneFolio character-setup page in your browser — this is on by default and can be turned off with **Open setup for new characters** in the plugin settings. Once the character is added, RuneFolio Sync sends an initial snapshot automatically and keeps syncing as you play.
4. Open [runefolio.app](https://runefolio.app) to see your synced progress.

The panel shows **Data sharing information** before you connect, explaining exactly what's sent and to whom — worth a read first.

## What it tracks

**Always synced once connected** (no toggle — this is the plugin's core purpose):

- Skill levels and XP, refreshed on login, every 10 minutes, and on logout
- Quest completion state and quest points
- Achievement Diary progress — individual task completion across all 492 catalog tasks, read directly from game state so you never have to open a diary journal
- Combat Achievement completion, by tier and by individual task
- Collection Log category snapshots, captured as you browse the log — a **Sync Collection Log** button is also added to the in-game interface for an on-demand full update

**On by default, with an off switch:**

- Loot drops, recorded through RuneLite's own Loot Tracker
- Completed boss/raid kills, clue scrolls, and Slayer tasks, including kill time, personal bests, and party size when the game reports them
- Account unlocks — a large catalog spanning bank space purchases, quest- and diary-gated content, area access, equipment, and more (checklist flags only, not full bank contents)

**Off by default, opt-in:**

- Bank, inventory, and equipment snapshots with estimated GE/high-alchemy value, captured when you open your bank
- PvP history — your own observed finishing blows and any linked loot only; never opponent equipment or location, and never combat assistance
- Screenshots of level-ups, quest completions, diary tasks, Combat Achievements, Collection Log unlocks, pets, valuable or untradeable drops, clue / raid / chest reward screens, Wilderness loot-key screens, and your own PvP finishing blows (the screenshot only; PvP history is a separate opt-in). Each moment has its own toggle under Screenshots, and the chat area is hidden while capturing by default.

Every category with a toggle shows a RuneLite confirmation dialog explaining exactly what it sends before you turn it on.

## Where your data goes

RuneFolio Sync sends data only to `https://runefolio.app`, over HTTPS. It never asks for or stores your Jagex or RuneScape credentials — connecting happens entirely through your browser or a revocable one-time character code. The RuneFolio account you connect (or whoever issued a temporary code) controls how that data is shared on the website; nothing is public by default.

Updates are queued locally (up to 1,000 pending events) and retried automatically if your connection drops. If the queue fills during a long outage, newer updates are skipped until it drains; your skills and progress are re-sent by the next scheduled full sync, but individual drops, completions and PvP results skipped while it was full are not. When screenshots are enabled, they're compressed and held in your RuneLite settings folder (up to 500 images / 256 MiB) until they've uploaded successfully; new captures may be skipped when that space is full.

## Requirements

- RuneLite's built-in **Loot Tracker** plugin must stay enabled for loot and completion tracking to work.
- A RuneFolio account (free to create) — either logged into directly, or used to generate a one-time code for connecting a single character without a full account login.

## License and credits

RuneFolio Sync's original source is licensed under [BSD-2-Clause](LICENSE). See [COPYRIGHT.md](COPYRIGHT.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for full attribution, including referenced behavior from [RuneProfile](https://github.com/ReinhardtR/runeprofile-plugin) and Quest Helper's Achievement Diary task mapping.

## Feedback

Found a bug or have a suggestion? Join the [Discord](https://discord.gg/Ar96ueFUuj) or [open an issue](https://github.com/Brettgod1355/RuneFolio-Plugin/issues).

## Contributing

Clone the repository and open it as a Gradle project with JDK 11 or newer (CI uses Temurin 11 and Gradle 8.10.2; the build targets Java 11 via `--release 11`). The `run` Gradle task launches a development RuneLite client with the plugin preloaded. Every pull request runs the test suite and the official RuneLite Plugin Hub packager as a pre-submission check.
