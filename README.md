# RuneFolio Sync

## Data sharing (0.3.29)

Before account login or temporary-code connection, the side panel explains the receiving service, data categories, optional uploads, local retries and receiving-account sharing controls. Cancel stops that connection action. **Data sharing information** reopens the disclosure at any time. Existing authorized connections and saved settings are retained; this update does not silently clear data or reset preferences.

RuneLite's native settings confirmation appears when changing loot, completion-history, bank/wealth or screenshot sharing. Bank/wealth and screenshots remain off by default. Turning a collector off prevents new capture, but does not erase previously queued uploads or website history. Disconnect/revoke connections to stop authorized uploads. Screenshots can contain personal information visible in other interfaces even with chat hiding enabled. Temporary codes send data to the RuneFolio account that issued the code.

Plugin Hub submission disclosure to include with the listing: “This plugin communicates with runefolio.app to upload linked character identity and progress, enabled loot/completion results, and optional bank/inventory/equipment snapshots and screenshots. The receiving RuneFolio account controls sharing. Pending events are saved locally for retries.” The installation warning is configured in the Plugin Hub submission; this repository is not itself a published or approved Plugin Hub listing.

The confirmations use RuneLite's `ConfigItem.warning` API and original Swing UI code. No upstream implementation is copied. API reference: https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/config/ConfigItem.java .

Optional bank/wealth capture (0.3.24): disabled by default. When enabled, opening the ordinary bank sends bank, inventory and equipment item IDs, names, quantities and estimated GE/high-alchemy values to RuneFolio. It does not inspect unopened storage. The server keeps the latest item list and compact daily wealth estimates. Values are estimates, not a complete account valuation.

Pending events are persisted through RuneLite configuration for retries, with a 1,000-event / 4 MiB serialized-event budget for new queue writes and bounded upload batches. A newer bank observation can replace a pending observation for the same character and UTC day; different days are retained. Successful acknowledgements remove queued events. Turning capture off prevents new captures; it does not delete already queued events or server history. Legacy diary caches (no longer read for task detection) and RuneFolio connection/name bindings also use RuneLite configuration. Connection tokens are sensitive: do not share configuration files. Screenshots currently retry in memory, not in the persistent event queue.

Private-alpha RuneLite plugin that securely syncs an Old School RuneScape character with [RuneFolio](https://runefolio.app).

## Current milestone

Version 0.3 includes:

- Browser-based RuneFolio account login.
- Optional temporary, character-specific connection codes.
- Automatic skill, quest, all-area diary task-count, and combat-achievement snapshots on login, every 10 minutes, and on logout.
- Individual Achievement Diary completions use game flags mapped to all 492 catalog tasks; no diary window needs to be opened.
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

Original RuneFolio source code is licensed under [BSD-2-Clause](LICENSE). Reuse and modifications, including commercial use, are permitted with the required notices retained. See [copyright and reuse](COPYRIGHT.md) and [third-party notices](THIRD_PARTY_NOTICES.md) for scope and upstream credits. The JAR includes the license and both notices under `META-INF/`.

## Individual diary completion (0.3.27)

Individual diary tasks use game completion flags for all 492 catalog tasks, synchronized on login, task changes and Sync now. No diary window needs to be opened. Area identities are withheld if the flag results disagree with the game's tier counters. Requires the matching website task-index support. Rebuild/restart the plugin before testing; website deployments do not update an installed client.

Quest Helper mapping attribution and RuneProfile collector-reference credit, with full BSD notices, are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and distributed in the JAR. Older clients' text observations remain supported by the website.

## Completion history (0.3.28)

The Sync completion history setting sends observed boss/raid, clue and Slayer completions through the existing durable retry queue. It defaults on and can be disabled independently of loot. Only local game messages and exposed result values are read; no gameplay or interface navigation is automated. Boss records include available duration/personal-best and raid details; unobserved details remain absent. Clue item details require RuneLite Loot Tracker and an observed matching reward event; a completion can be recorded without rewards. Slayer records can contain a task streak without a monster name if the game did not provide one.

Website history is available in Bosses, Clue Scrolls, and Activity & Achievements (Slayer). These records do not add loot or replace Hiscores totals. Existing historical completions are not reconstructed. Disabling collection prevents new captures, but already queued events can still upload. Rebuild and restart to install an updated development plugin.
