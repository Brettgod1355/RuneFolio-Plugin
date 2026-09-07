# RuneFolio Sync

Optional bank/wealth capture (0.3.24): disabled by default. When enabled, opening the ordinary bank sends bank, inventory and equipment item IDs, names, quantities and estimated GE/high-alchemy values to RuneFolio. It does not inspect unopened storage. The server keeps the latest item list and compact daily wealth estimates. Values are estimates, not a complete account valuation.

Pending events are persisted through RuneLite configuration for retries, with a 1,000-event / 4 MiB serialized-event budget for new queue writes and bounded upload batches. A newer bank observation can replace a pending observation for the same character and UTC day; different days are retained. Successful acknowledgements remove queued events. Turning capture off prevents new captures; it does not delete already queued events or server history. Diary caches and RuneFolio connection/name bindings also use RuneLite configuration. Connection tokens are sensitive: do not share configuration files. Screenshots currently retry in memory, not in the persistent event queue.

Private-alpha RuneLite plugin that securely syncs an Old School RuneScape character with [RuneFolio](https://runefolio.app).

## Current milestone

Version 0.3 includes:

- Browser-based RuneFolio account login.
- Optional temporary, character-specific connection codes.
- Automatic skill, quest, all-area diary task-count, and combat-achievement snapshots on login, every 10 minutes, and on logout.
- Completed Achievement Diary task names are collected silently from diary areas opened in game; RuneFolio owns the current OSRS Wiki task catalog and ignores unrelated interface text.
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

## Automatic character names

After a connected character has been observed once, RuneFolio can recognize a later in-game name change and keep its existing history. The plugin sends a SHA-256-derived identifier from RuneLite’s local account hash with authorized heartbeats and events. The raw account hash is never stored or sent. RuneFolio additionally scopes its stored digest to the owner. This identifier is correlation evidence, not proof of ownership or a login credential. An active authorized connection and a prior matching name are required; name conflicts and archived characters are never merged automatically. Changing owners requires a fresh authorized connection.

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
5. Bank and wealth snapshots — **implemented, opt-in**
6. Minigame totals
7. Optional post-event screenshots — **implemented**

## Independent bank valuation (0.3.25)

Bank capture uses RuneFolio's own collector and RuneLite's item-price API. RuneLite's Bank plugin may be disabled. Opening an ordinary bank observes its full container, not just a selected tab/search or the rounded header. Bank, inventory and equipment are separate containers; combined wealth includes all three and excludes other storage. Coins and platinum tokens count at face value in the alchemy estimate. A fresh snapshot from 0.3.25 is required for corrected currency values; retained older observations are unchanged.

## Copyright and credits

See [copyright and reuse](COPYRIGHT.md) and [third-party notices](THIRD_PARTY_NOTICES.md). These preserve third-party rights and do not choose a project-wide license for RuneFolio. The JAR includes these notices under `META-INF/`.

## Diary capture formatting (0.3.26)

Completed diary rows preserve word boundaries across line breaks and support leading color formatting. Met requirements alone never count as a completed task. Rebuild/restart the plugin, manually reopen affected diary areas, and sync to refresh cached observations. The website also recovers unambiguous joined-word observations from older builds and retains bounded unmatched task names for future diagnosis; it does not infer individual completions from aggregate counts.
