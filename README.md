# RuneFolio Sync

RuneFolio Sync connects RuneLite to your [RuneFolio](https://runefolio.app) account. It runs quietly in the background, tracking your Old School RuneScape progress and sending it to RuneFolio, where it's organized into a browsable profile — skills, quests, diaries, Combat Achievements, Collection Log, loot, boss kills, and more.

**This plugin doesn't display your history itself.** It's a data connector, not a viewer. Connect once, then open your profile at [runefolio.app](https://runefolio.app) to see everything it's synced.

## Getting started

1. Install the plugin and open the RuneFolio panel in the sidebar (look for the gold "R").
2. Choose how to connect:
   - **Log in to RuneFolio** (recommended) — opens your browser to sign in or create an account once. Every character you log into afterward syncs automatically.
   - **Connect character** — enter a temporary code generated on the website to link a single character without creating an account.
3. Log in to your character. RuneFolio Sync sends an initial snapshot automatically and keeps syncing as you play.
4. Open [runefolio.app](https://runefolio.app) to see your synced progress.

The panel shows **Data sharing information** before you connect, explaining exactly what's sent and to whom — worth a read first.

## What it tracks

**Synced automatically once connected:**

- Skill levels and XP, refreshed on login, every 10 minutes, and on logout
- Quest completion state and quest points
- Achievement Diary progress — individual task completion across all 492 catalog tasks, read directly from game state so you never have to open a diary journal
- Combat Achievement completion, by tier and by individual task
- Collection Log category snapshots, captured as you browse the log — a **Sync Collection Log** button is also added to the in-game interface for an on-demand full update
- Loot drops, recorded through RuneLite's own Loot Tracker
- Completed boss/raid kills, clue scrolls, and Slayer tasks, including kill time, personal bests, and party size when the game reports them

**Optional, off by default:**

- Bank, inventory, and equipment snapshots with estimated GE/high-alchemy value, captured when you open your bank
- Account unlocks — a large catalog spanning bank space purchases, quest- and diary-gated content, area access, equipment, and more
- PvP history — your own observed finishing blows and any linked loot only; never opponent equipment or location, and never combat assistance
- Screenshots of level-ups, quest completions, diary tasks, Combat Achievements, Collection Log unlocks, pets, and valuable or untradeable drops

Every optional category has its own toggle in the plugin settings, and RuneLite shows a confirmation dialog explaining exactly what it sends before you turn one on.

## Where your data goes

RuneFolio Sync sends data only to `https://runefolio.app`, over HTTPS. It never asks for or stores your Jagex or RuneScape credentials — connecting happens entirely through your browser or a revocable one-time character code. The RuneFolio account you connect (or whoever issued a temporary code) controls how that data is shared on the website; nothing is public by default.

Updates are queued locally and retried automatically if your connection drops, so nothing is lost. When screenshots are enabled, they're compressed and held in your RuneLite settings folder until they've uploaded successfully.

## Requirements

- RuneLite's built-in **Loot Tracker** plugin must stay enabled for loot and completion tracking to work.
- A RuneFolio account (free to create) or a temporary connection code from one.

## License and credits

RuneFolio Sync's original source is licensed under [BSD-2-Clause](LICENSE). See [COPYRIGHT.md](COPYRIGHT.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for full attribution, including referenced behavior from [RuneProfile](https://github.com/ReinhardtR/runeprofile-plugin) and Quest Helper's Achievement Diary task mapping.

## Feedback

Found a bug or have a suggestion? Join the [Discord](https://discord.gg/Ar96ueFUuj) or [open an issue](https://github.com/Brettgod1355/RuneFolio-Plugin/issues).

## Contributing

Clone the repository and open it as a Gradle project (JDK 17, Java 11 target). The `run` Gradle task launches a development RuneLite client with the plugin preloaded. Every pull request runs the test suite and the official RuneLite Plugin Hub packager as a pre-submission check.
