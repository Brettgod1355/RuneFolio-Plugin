# Third-party notices

## Expanded unlock catalog — 2026-09-15

Factual coverage source: OSRS Wiki contributors, https://oldschool.runescape.wiki/w/Unlockable_content?oldid=15326045 (revision 15326045, retrieved 2026-09-15; wikitext SHA-256 `57a886f1d21e7eec2ede86c2c90278cc15b80508a778f9252f4b07b34005f1f5`). The overview labels itself incomplete; the catalog covers its 335 listed rows, not a claim to every possible game unlock. `lib/unlocks-source-coverage.json` (in the separate RuneFolio website repository, not shipped with the plugin) records only row numbers and independently assigned catalog IDs. Both catalog JSON files expand grouped facts into individual purchases, facilities, destinations and equipment pieces, with independently written explanations and per-entry source links. No article prose, artwork or raw source tables are distributed.

Detailed factual cross-checks (retrieved 2026-09-15): [Bank](https://oldschool.runescape.wiki/w/Bank#Bank_space), [Museum Camp](https://oldschool.runescape.wiki/w/Museum_Camp), [Island amenity](https://oldschool.runescape.wiki/w/Island_amenity), [Fire pit](https://oldschool.runescape.wiki/w/Fire_pit), [Ring of shadows](https://oldschool.runescape.wiki/w/Ring_of_shadows), [Camulet](https://oldschool.runescape.wiki/w/Camulet), [Motherlode Mine](https://oldschool.runescape.wiki/w/Motherlode_Mine), [Stronghold of Security](https://oldschool.runescape.wiki/w/Stronghold_of_Security), [Doomsayer](https://oldschool.runescape.wiki/w/Doomsayer), [Graceful outfit](https://oldschool.runescape.wiki/w/Graceful_outfit), [Balloon transport system](https://oldschool.runescape.wiki/w/Balloon_transport_system), [Eagle transport system](https://oldschool.runescape.wiki/w/Eagle_transport_system), [Enchanted lyre(i)](https://oldschool.runescape.wiki/w/Enchanted_lyre(i)), [Pristine spider silk](https://oldschool.runescape.wiki/w/Pristine_spider_silk), and [Rocks (Viyeldi caves)](https://oldschool.runescape.wiki/w/Rocks_(Viyeldi_caves)). The Bank article supersedes the older overview's slot counts: nine purchased blocks of 50 slots. Wiki article text is CC BY-NC-SA 3.0 with additional terms; [copyright policy](https://oldschool.runescape.wiki/w/RuneScape:Copyrights). This catalog uses game facts and links, not copied/adapted article text.

RuneLite identifier/API references remain pinned to `bc408c5a53a5dea3e586327002f1d3b59a9fbc28`: `runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java`, `runelite-api/src/main/java/net/runelite/api/ItemID.java`, and `runelite-api/src/main/java/net/runelite/api/Quest.java`. Applicable root BSD-2-Clause terms and source headers were reviewed. Local uses: matching catalog rules and the independently implemented `RuneFolioUnlockCollector.java`. New behavior evaluates purchased-block thresholds, indexes item IDs, caches quest results per capture and splits observations into bounded events. No upstream implementation was copied. Keep the existing RuneLite, OSRS Wiki and Jagex notices; historical provenance review remains open.

## Account unlock catalog and passive collector — 2026-09-15

Wyrmscraig shortcut requirement facts were checked against https://oldschool.runescape.wiki/w/Rocks_(Wyrmscraig) on 2026-09-15. The catalog explanation is independently worded; no article prose or artwork is reproduced.

`lib/unlocks-catalog.json` (website) and `src/main/resources/unlocks-catalog.json` (plugin) share a bounded checklist of game names and API identifiers. Identifier references: RuneLite commit `bc408c5a53a5dea3e586327002f1d3b59a9fbc28`, `runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java`, `ItemID.java`, `Quest.java`, and the public Client/ItemContainer APIs: https://github.com/runelite/runelite/tree/bc408c5a53a5dea3e586327002f1d3b59a9fbc28/runelite-api/src/main/java/net/runelite/api . RuneLite's root BSD-2-Clause terms and applicable source headers were inspected. Catalog descriptions, evidence protocol, collector and interface are independently implemented; no RuneLite implementation or wiki article prose/assets are copied. Game names and identifiers belong to their applicable rights holders. Credit: RuneLite contributors and Jagex.

RuneLite's FarmingWorld and Quest Helper's InAidOfTheMyreque quest definition were inspected as behavior references. Region-dependent tree state is deliberately not treated as a globally readable unlock. No code or task mapping was copied from those references for this feature. Unsupported automatic mappings are left to explicit manual confirmation. This entry does not close the historical provenance review above.



## Reward and PvP capture references (0.3.33)

RuneLite's ScreenshotPlugin, LootTrackerPlugin, PlayerLootReceived, LootManager and public Hitsplat/Actor APIs were inspected for event timing and behavior. Reward classification and the bounded result correlator are implemented in RuneFolioRewardScreenshots.java, RuneFolioPvpTracker.java and RuneFolioPlugin.java. They consume observed results only; no upstream screenshot/loot implementation is bundled. The native Loot Tracker's Loot Chest event contains tab rewards without an opponent identity. Referenced LootTrackerPlugin blob: 9f9c009122946575d437bb4695ef9fa79e4354a5.

Dink's PlayerKillNotifier was inspected as a behavior/API reference for local-player hitsplat ownership and observed death signals, not copied or translated. RuneFolio uses its own bounded correlation and result schema, excludes opponent gear/location capture and does not provide combat assistance. Upstream: https://github.com/pajlads/DinkPlugin/blob/master/src/main/java/dinkplugin/notifiers/PlayerKillNotifier.java (reviewed blob 1c9ef0602376ba13b829a3d9fb4d70754a3a4f4b). This reference is not official approval of RuneFolio. Dink's BSD notice is retained below as attribution:

BSD 2-Clause License

Copyright (c) 2022, Jake Barter
All rights reserved.

Copyright (c) 2022, pajlads

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

RuneFolio's original source code is licensed under BSD-2-Clause; see LICENSE. This document preserves the separate terms for third-party material. Dependencies and any adapted third-party portions retain their own terms. This is a targeted current-source inventory, not an exhaustive historical provenance audit or dependency bill of materials.

## RuneLite — APIs and behavior references

Upstream: https://github.com/runelite/runelite

RuneFolio uses RuneLite's public APIs, event bus, configuration, item prices and UI framework. The bank collector maintains its own bounded snapshots; it does not depend on the Bank plugin being enabled, scrape its title, or copy its bank-interface controls.

Currency valuation conventions were checked against `runelite-client/src/main/java/net/runelite/client/plugins/bank/BankPlugin.java` (reviewed blob `015ee729117e662591fc198e33338ef206e51fbb`). Credit to that file's contributors below. RuneFolio implements its own face-value helper and tests. Retaining the reference notices does not claim that the complete upstream plugin is bundled.

### RuneLite root license

BSD 2-Clause License

Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

### BankPlugin reference notice

Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
Copyright (c) 2018, Jeremy Plsek <https://github.com/jplsek>
Copyright (c) 2019, Hydrox6 <ikada@protonmail.ch>
Copyright (c) 2024, PhraZier <https://github.com/phrazier>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Other dependencies and game content

Java dependencies declared in `build.gradle`, including their transitive dependencies, retain their own licenses and notices. The normal plugin JAR does not shade these runtime dependencies. If packaging changes, re-audit the actual included classes/resources and carry their required notices into the distribution.

Old School RuneScape, its game content and associated marks belong to their applicable rights holders, including Jagex. RuneFolio is not affiliated with or endorsed by Jagex or RuneLite. OSRS Wiki catalog/source references credit its contributors: https://oldschool.runescape.wiki/ . Game/wiki content is not covered by a RuneFolio project notice; verify the specific applicable terms before copying or redistributing it.

## Historical review limitations

Current sources and known upstream references were reviewed in September 2026. Exact provenance is not recorded for every older helper, scaffold or catalog. Do not infer from absent headers that code was independently authored or cleared for reuse. Trace those origins before public release, preserve any discovered file-specific notices, and keep remaining dependencies/assets in the review scope. This credits inventory is not official RuneLite approval.

## Quest Helper — adapted individual diary completion mappings

Upstream: https://github.com/Zoinkwiz/quest-helper
Revision: `94acc617e99fe8029ea62f9b8bceaf1ece0a75ea` (Plugin Hub reference reviewed 2026-09-07).
Destination: `src/main/java/app/runefolio/sync/RuneFolioDiaryTaskFlags.java`.

The 492 task-to-varp-bit/varbit-threshold mappings are adapted from the following files. RuneFolio supplies its own snapshot collector, count validation, task-index wire format and lifecycle handling; no Quest Helper dependency, route guidance or gameplay actions are included. Mapping order is explicitly reconciled with RuneFolio's catalog, including Ardougne Elite, Kourend Medium/Hard and Karamja Medium exceptions. Karamja five-item counters require >= 5. `requirements/var/VarplayerRequirement.java` was read to verify bit semantics; its implementation was not copied.

Source files (relative to upstream root):

- `src/main/java/com/questhelper/helpers/achievementdiaries/ardougne/ArdougneEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/ardougne/ArdougneElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/ardougne/ArdougneHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/ardougne/ArdougneMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/desert/DesertEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/desert/DesertElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/desert/DesertHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/desert/DesertMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/falador/FaladorEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/falador/FaladorElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/falador/FaladorHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/falador/FaladorMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/fremennik/FremennikEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/fremennik/FremennikElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/fremennik/FremennikHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/fremennik/FremennikMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/kandarin/KandarinEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/kandarin/KandarinElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/kandarin/KandarinHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/kandarin/KandarinMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/karamja/KaramjaEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/karamja/KaramjaElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/karamja/KaramjaHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/karamja/KaramjaMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/kourend/KourendEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/kourend/KourendElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/kourend/KourendHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/kourend/KourendMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/lumbridgeanddraynor/LumbridgeEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/lumbridgeanddraynor/LumbridgeElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/lumbridgeanddraynor/LumbridgeHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/lumbridgeanddraynor/LumbridgeMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/morytania/MorytaniaEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/morytania/MorytaniaElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/morytania/MorytaniaHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/morytania/MorytaniaMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/varrock/VarrockEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/varrock/VarrockElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/varrock/VarrockHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/varrock/VarrockMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternMedium.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/wilderness/WildernessEasy.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/wilderness/WildernessElite.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/wilderness/WildernessHard.java`
- `src/main/java/com/questhelper/helpers/achievementdiaries/wilderness/WildernessMedium.java`

### Root license

BSD 2-Clause License

Copyright (c) 2020, Zoinkwiz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

### Preserved source-file notices

/*
 * Copyright (c) 2021, Kerpackie <https://github.com/Kerpackie/>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Copyright (c) 2021, Obasill <https://github.com/Obasill>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Copyright (c) 2021, Zoinkwiz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * Copyright (c) 2021, Obasill <https://github.com/Obasill>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Copyright (c) 2022, Obasill <https://github.com/Obasill>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Copyright (c) 2022, rileyyy <https://github.com/rileyyy/> and Obasill <https://github.com/obasill/>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

Both source-file headers and this document retain the applicable notices. This document is shipped inside the plugin JAR at `META-INF/THIRD_PARTY_NOTICES.md`; BSD source and binary redistribution obligations apply to the adapted portions.

## RuneProfile — adapted button definitions, collector references and retrospective audit

Upstream: https://github.com/ReinhardtR/runeprofile-plugin
Revision: `2da51cd7a8dcf6a5ed0e827a2df2bb985d0e0550` (Plugin Hub reference reviewed 2026-09-07).

References: `src/main/java/com/runeprofile/data/AchievementDiary.java`, `src/main/java/com/runeprofile/autosync/PlayerDataService.java`, and `src/main/java/com/runeprofile/autosync/CollectionLogWidgetSubscriber.java`, and `src/main/java/com/runeprofile/ui/ManualUpdateButtonManager.java`.
Local related implementations: `RuneFolioProgressCollector.java`, `RuneFolioCombatTaskCatalog.java`, `RuneFolioCollectorManifest.java`, `RuneFolioPlugin.java` and `RuneFolioCollectionLogButton.java` under `src/main/java/app/runefolio/sync/`.

RuneProfile informed the diary script 2200 area IDs/stack layout and the POH Collection Log ownership guard. RuneFolio implements bounded JSON snapshots, compiled bitmap allowlists, retries and character isolation differently; it does not bundle RuneProfile classes. Other shared RuneLite API patterns (quest enumeration, CA varp reading, collection widgets and navigation) do not alone establish copied source. Retain this credit and full license for the reference-derived collector work, including any adapted portions whose exact historical origin is uncertain.

A targeted comparison covered all 67 Java files at the above RuneProfile revision. Exact long-line scanning found generic API overlaps; a comment/import-stripped 35-token comparison additionally identified matching active/inactive nine-sprite button definitions in RuneFolioCollectionLogButton.java and ManualUpdateButtonManager.java. These definitions are treated as adapted material, with RuneProfile copyright/license added to the local source. RuneFolio uses a separate button with different layout, collision visibility and sync callback handling; it does not import the upstream replacement-search/button manager. The manual collector comparison identified the behavior references above. This is not proof that every historical version was independently authored, nor an audit of RuneProfile's separate website or all assets/dependencies. RuneProfile contains file-specific third-party notices in its model exporter and utilities; those files were inspected, not imported into RuneFolio. Recheck their particular notices before any future reuse.

### RuneProfile license

BSD 2-Clause License

Copyright (c) 2022, Reinhardt Rijna
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Completion-history API and message references

The original completion-history implementation uses RuneLite's public ChatMessage, LootReceived and game-value APIs. Result message formats and exposed raid fields were checked against RuneLite's `plugins/chatcommands/ChatCommandsPlugin.java`, `plugins/loottracker/LootTrackerPlugin.java`, `plugins/raids/RaidsPlugin.java` and `plugins/slayer/SlayerPlugin.java` on 2026-09-08. These were behavior/API references, not vendored implementations; the independent trackers use their own bounded correlation and output protocol. RuneLite's retained BSD terms are above.

## Data-sharing confirmations — 2026-09-10

The original RuneFolio disclosure UI uses Java Swing and RuneLite's ConfigItem.warning API. API behavior was checked in https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/config/ConfigItem.java and https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/config/ConfigPanel.java . RuneLite's BSD-2-Clause source headers were inspected; no implementation code was copied or adapted for this change. Existing upstream notices remain applicable and unchanged.


## Sea charting task catalog — 2026-09-16

Upstream: https://github.com/JaredEzz/sea-charting-quest-helper (BSD-2-Clause; commit `b4ac85ceef6d44db89e5a11cd4491fbf6a1b44a5`, found via the RuneLite Plugin Hub manifest).

`lib/unlocks-catalog.json` (website) and `src/main/resources/unlocks-catalog.json` (plugin) gained 358 new "Sea Charting" entries (`sea_charting.0`-`sea_charting.357`), each keyed to a Jagex `VarbitID.SAILING_CHARTING_*_COMPLETE` game-data constant (per the RuneLite identifier reference already pinned above, `runelite-api/.../gameval/VarbitID.java`) and to a wiki-sourced sea/ocean and task-name label. That task-to-varbit and task-to-sea/ocean mapping was compiled by cross-referencing `SeaChartTask.java` and `SeaChartRegion.java` from the repository above, itself explicitly documented there as "mechanically compiled from the public sea-chart-task table in the 'Sailing' RuneLite plugin by LlemonDuck (https://github.com/LlemonDuck/sailing, BSD-2-Clause)" for the underlying varbit/level/type data, and independently cross-checked there against the OSRS Wiki's own `sea=`/`ocean=` task tags for the sea/ocean grouping. As with that source's own stated position, these are Jagex's public gameval identifiers and wiki-sourced facts, not creative expression belonging to either upstream plugin -- no source code, task-description prose or UI/collector implementation from `sea-charting-quest-helper` or `LlemonDuck/sailing` is included; RuneFolio's own catalog descriptions, evidence protocol and `RuneFolioUnlockCollector.java` polling are the existing independently-implemented ones already covered above, reused unmodified since these entries use the catalog's existing "flag" rule kind. Task display names and per-task level requirements were cross-checked against a full copy of the OSRS Wiki's "Sea charting" task table supplied directly by the account owner (retrieved by them from https://oldschool.runescape.wiki/w/Sea_charting on 2026-09-16); wiki article text is CC BY-NC-SA 3.0 with additional terms, and only game facts (level, tool, XP, sea/ocean grouping), not article prose, are used. `sea-charting-quest-helper`'s BSD notice is retained below.

BSD 2-Clause License

Copyright (c) 2026, JaredEzz and NicolasLaurent321

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Sidebar Discord and GitHub link icons (0.3.40)

`src/main/resources/discord.png` and `src/main/resources/github.png` are the official Discord and GitHub logomarks, used to link to the RuneFolio Discord server and this plugin's GitHub repository — the use case each brand's own guidelines explicitly cover. They are vector-traced reproductions of those marks, retrieved from Simple Icons (https://github.com/simple-icons/simple-icons, commit `f2365d33171bd1897a41aaae6c0b6e795bcc0483`, project itself CC0-1.0-licensed at `icons/discord.svg` and `icons/github.svg`). Simple Icons' own disclaimer notes that CC0 covers the project, not necessarily every individual brand mark it traces, and directs users to each brand's own guidelines, linked from its per-icon data: Discord at https://discord.com/branding, GitHub at https://github.com/logos. Both were reviewed 2026-09-17.

Modification: recolored (Discord to its brand blurple `#5865F2`; GitHub to white, matching each brand's own light-on-dark variant, for readability against the plugin's dark panel) and rasterized from SVG to a 64x64 PNG, scaled down to 16x16 at load time via RuneLite's `ImageUtil.resizeImage`. No other alteration. Icon placement/loading pattern (`ImageUtil.loadImageResource` + `resizeImage`, `SwingUtil.removeButtonDecorations`, `LinkBrowser.browse`) follows the same convention used by Quest Helper's `com.questhelper.panel.QuestHelperPanel` (https://github.com/Zoinkwiz/quest-helper, BSD-2-Clause; behavior/API reference only, no Quest Helper code or its own bundled icon files are included).

## Plugin Hub preflight tooling

CI invokes the unmodified RuneLite plugin-hub-tooling v3 release bundle (SHA-256 eb0961b7cd0a1e4a351fb0f684731be1a9049167bfb49a2d91a760fea2933fd2). Sources: https://github.com/runelite/plugin-hub-tooling/tree/v3 and https://github.com/runelite/plugin-hub/blob/master/.github/workflows/build.yml . The upstream root BSD-2-Clause license and applicable Abex file headers were inspected. The bundle is downloaded only for CI, retains its upstream files, and is not vendored or distributed with the plugin. RuneFolio's wrapper is original orchestration; no upstream implementation is copied. Existing BSD notices above remain intact.
