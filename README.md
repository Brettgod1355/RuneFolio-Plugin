# RuneFolio Sync

Private-alpha RuneLite plugin that securely syncs an Old School RuneScape character with [RuneFolio](https://runefolio.app).

## Current milestone

Version 0.3 includes:

- Browser-based RuneFolio account login.
- Optional temporary, character-specific connection codes.
- Automatic skill, quest, diary-tier, and combat-achievement snapshots on login, every 10 minutes, and on logout.
- Full individual combat-task completion flags, plus named combat-task and Collection Log unlock events as they happen.
- Collection Log category snapshots whenever the player opens or changes a category in the log.
- Automatic loot history from RuneLite's enabled native Loot Tracker, preserving each reward's source, items, quantities, and values.
- Completed boss activity derived from recognized native Loot Tracker rewards, with retry-safe KC updates and milestone history.
- A manual **Sync now** control.
- A versioned universal event uploader with UUID-based idempotency.
- A persistent local retry queue that uploads batches every 30 seconds.
- A final queued upload attempt when the plugin shuts down.
- Server controls for revoking a connection or requesting a fresh full snapshot.
- Sidebar visibility for the connected character, last successful sync, and pending events, with an optional hide-side-panel setting.
- A RuneFolio brand mark beside the sidebar title.
- Optional post-event screenshots for level ups, quest completions, diary tasks, Combat Achievements, Collection Log unlocks, pets, high-value loot, and untradeable loot.

Older RuneFolio skill endpoints remain available so previous private-alpha builds continue to work.

## Local development

1. Install IntelliJ IDEA Community and a JDK 17.
2. Clone this repository.
3. Open it as a Gradle project in IntelliJ.
4. In the Gradle tool window, run the `run` task.

The build targets Java 11 for RuneLite compatibility and follows RuneLite's standard external-plugin structure.

## Privacy and compliance guardrails

- The plugin never asks for or stores Jagex or RuneScape credentials.
- All RuneFolio authorization happens in the user's browser or through a revocable temporary character code.
- Data is sent only outbound to `https://runefolio.app` over HTTPS.
- Loot syncing subscribes to RuneLite's public `LootReceived` event. RuneLite's built-in Loot Tracker must remain enabled; RuneFolio does not read RuneLite account credentials or private website data.
- Screenshot uploading is disabled by default. When enabled, the selected post-event categories are captured, compressed to JPEG on the player's computer, and uploaded to the player's private RuneFolio screenshot archive.
- Chat and private-message overlays are hidden from uploaded screenshots by default and can be included only by changing the screenshot privacy setting.
- Collectors record completed gameplay results only. They must not automate actions or provide live combat, prayer, projectile, or safe-tile assistance.
- Optional categories such as bank data will be separately disclosed and opt-in before they are added.
- The plugin remains private during alpha. A public RuneLite Plugin Hub release will require a public repository, license, manifest, and RuneLite review.

## Planned collectors

1. Skills — **implemented**
2. Quests, diary tiers, combat achievements, and Collection Log — **implemented**
3. Loot history — **implemented**
4. Completed boss activity — **implemented**
5. Bank and wealth snapshots
6. Minigame totals
7. Optional post-event screenshots — **implemented**
