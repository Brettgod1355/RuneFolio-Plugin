# RuneFolio Sync

## Manual verification checklist

Unchecked items are implemented but still await maintainer confirmation in a real RuneLite session. CI success does not mark them complete. Report an ID as **passed**, **failed**, or **partly working**, plus the displayed plugin version and any useful screenshot. Keep confirmed entries checked and add newly released behavior during each change.

Rebuild/reinstall and restart RuneLite before testing; website deployments do not update the installed plugin. Identity/rename checks below require **0.3.23 and the matching backend release**. Earlier collector, isolation, pet, and manifest improvements are already in 0.3.22. Test rare drops and name changes naturally; no need to spend resources just to finish this list.

### Connection and isolation

- [ ] **P01 — Installed version.** After rebuilding/restarting, confirm the sidebar displays the intended version and ordinary Sync now works.
- [ ] **P02 — Character switching.** Sync one character, log out, and log into another. Skills, diary rows, Collection Log observations, pet captions, and delayed screenshots must remain attached to the correct character.
- [ ] **P03 — World/profile boundaries.** If naturally using a separate-progress mode such as quest speedrunning, verify its progress does not enter the normal character portfolio. A normal-world hop should keep the correct connection.
- [ ] **P04 — Temporary connections.** Connect a spare character with a valid temporary code. Verify it syncs only that character and stops after revocation/archive; reconnect with fresh authorization afterward.
- [ ] **P05 — Automatic account-mode rename.** Sync the existing name successfully with 0.3.23 first. When an in-game name change is already planned, log back into the same character. It should remain connected automatically and retain one website character with its old history.
- [ ] **P06 — Rename with a temporary connection.** After binding the old name with 0.3.23, verify the same temporary connection is recovered after a planned rename and restart. A revoked connection or destination-name conflict must not rename/merge anything.
- [ ] **P07 — Queued events across a rename.** If identity-tagged events are pending during a planned rename, let the connection recover. They should upload once to the same character and the queue should drain. Events captured before identity support do not contain a stable identity; report any that remain waiting.

### Collectors and synchronization

- [ ] **P08 — POH Collection Log ownership.** View another player's POH Adventure Log/Collection Log, then your own. The host's items must not reach your portfolio; returning to your own log must not finish an old host capture.
- [ ] **P09 — Individual diary capture.** Manually open an area with completed tasks and sync. Completed task names should match the website's canonical rows. Repeat for another area; automatic totals and opened-area identities must remain distinct.
- [ ] **P10 — Collection Log updates.** Visit several categories and use the full-sync action. Check obtained items and totals on the website; the success indication should follow an accepted upload.
- [ ] **P11 — Quests and Combat Achievements.** Compare completed quests/miniquests and CA totals/tasks with the game. Complete one naturally and verify its update; report mismatched entries by name.
- [ ] **P12 — Loot capture.** With RuneLite's native Loot Tracker enabled, obtain ordinary monster/boss loot and a reward chest/clue naturally. Verify source, item quantities, timestamps, values, and correct character, without duplicate drops.
- [ ] **P13 — Scheduled and logout syncing.** Gain XP, allow the ten-minute snapshot and normal upload cycle, then log out normally. Check latest skills/source/time and that pending events eventually drain.
- [ ] **P14 — Retry persistence.** After an actual temporary upload failure, verify pending retryable events survive a restart and upload once when service returns. Non-retryable rejected events are a separate case; report their message.
- [ ] **P15 — Collector manifest.** With 0.3.22 or later, perform an ordinary successful sync and compare quest/CA counts. If the manifest endpoint is unavailable during normal use, collection should keep its last valid or compiled fallback values. Invalid-manifest cases also have automated coverage.

### Optional screenshots

Enable the Screenshots master switch for these tests. It defaults OFF. Category switches should only act while that master switch is enabled.

- [ ] **P16 — Screenshot settings and privacy.** Confirm the collapsed settings section, master switch, and individual category switches. With default privacy enabled, chat/private-message overlays should be hidden in the uploaded image and restored in the client afterward.
- [ ] **P17 — Quest screenshot.** Complete a quest naturally; expect one correctly labeled screenshot on the correct character.
- [ ] **P18 — Diary-task screenshot.** Complete a diary task naturally; expect one screenshot from that event.
- [ ] **P19 — Combat Achievement screenshot.** Complete a CA naturally; expect a corresponding screenshot.
- [ ] **P20 — Collection Log screenshot.** Obtain a new unlock naturally; verify its caption and image.
- [ ] **P21 — Pet identification.** When a pet event occurs, verify a recognized pet is named correctly. Duplicate/backpack messages should have appropriate labels; an unknown pet should use the fallback rather than invent a name. Duplicate pets must not become fabricated new Collection Log unlocks.
- [ ] **P22 — Valuable/untradeable drop screenshots.** Test the configured value threshold and an untradeable drop naturally. A single drop qualifying for both should produce one high-value screenshot.
- [ ] **P23 — Screenshot retry deduplication.** If an upload retries, verify one stored screenshot for the event rather than multiple copies. This can be checked opportunistically after a real service interruption.

### Confirmed in normal use

- [x] **C01 — Automatic diary aggregate counts/tier distribution** matched the game with 0.3.18. Confirmed 2026-09-07; P09's individual task names still need confirmation.
- [x] **C02 — A real level-up screenshot uploaded and appeared on the website.** Confirmed 2026-09-07; P16–P23 cover separate unconfirmed behavior.

Bank/wealth capture and richer boss/raid, clue, and Slayer history are still unfinished integrations. They are **not released test cases**. The Settings shortcut has an unresolved historical report; do not describe it as a confirmed fix without reproducing it.

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
5. Bank and wealth snapshots
6. Minigame totals
7. Optional post-event screenshots — **implemented**

