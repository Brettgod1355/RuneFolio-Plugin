# Third-party notices

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
