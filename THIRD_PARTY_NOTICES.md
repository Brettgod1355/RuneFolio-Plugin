# Third-party notices

These credits do not grant a project-wide license to RuneFolio's original code. Dependencies and any adapted third-party portions retain their own terms. This is a targeted current-source inventory, not an exhaustive historical provenance audit or dependency bill of materials.

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
