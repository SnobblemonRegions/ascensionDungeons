# Type dungeons: one authored dungeon per Cobblemon type

Goal (owner, 2026-10-16): a dungeon for each of the 18 Cobblemon types. Fire is `cinderforge` (done, hand-coded runtime).
The other 17 use a data-driven runtime so each is a bespoke layout + declared mechanics, not a reskin (owner rejected
material-swap reskins for the districts, and the same applies here).

## Contract (from `type_catalogue.json`)
Each type has 5 rooms and a named boss arena: **Habitat/Refuge** (safe start) -> **Gallery** (first encounter) -> **Crossing /
Ravine / Chasm** (hazard traversal) -> **Court** (hub, puzzle, locked door) -> **Vault** (treasure, key) -> **Arena** (boss).
15 landmarks per type: 5 focal (set pieces), 5 interactive (mechanics), 5 discovery (chests / secrets). Vanilla blocks only.

## Pieces
- Python kit `tools/dungeonkit/`: grid, palette roles, shape primitives, checks (liquid containment, exposure, enclosure,
  walkability), schematic + `layout.json` export, auto playthrough planner, E2E driver.
- Java engine (package `gen/authored`, `mc/Authored*`): `AuthoredLayout`, `AuthoredChunkGenerator`, `AuthoredDirector`,
  `AuthoredEvents`. A type is just `data/ascensiondungeons/dungeons/<id>/{dungeon.schem,layout.json}` plus an entry in
  `dungeons/index.json`; `/dungeon type <id>` starts it.
- Mechanics DSL (layout.json): `lever_sequence`, `offering` (key items), `toggle_pattern`, `sequence` (memory pedestals), `trial`
  (stand on a plate, fight waves), `switch`; effects `clear`, `fill`, `say`, `sound`, `particles`, `spawn`, `hazard`, `pad`, `give`,
  `delay`; hazards `vent`, `jet`, `zone`, `rise`, `lightning`, `collapse`, `gust`; `encounters`; `bosses` (phases, strike-target
  shield, adds, on-death effects); `pads` (teleport); `completion`.

## Status
See the table at the bottom (updated as each dungeon is finished and verified).

| type | arena | concept (layout + hazard + puzzle + boss) | status |
|---|---|---|---|
| fire | Crucible of the First Flame | Cinderforge Bastion, 8 rooms, hand-coded runtime | done (separate runtime) |
| water | Drowned Crown Basin | The Drowned Reservoir: tide canal (flood/drain, undertow, dry niches), 3-lever sluice gantry -> flooded tunnel -> Archive key; pearl trial + offering basin; Tide Warden | verified |
| grass | Elderroot Sanctuary | Elderroot Conservatory: plank bridge over a thorn ravine, lever-pattern pruning gate, flower-order pollination, seed shrine -> vault; Elderroot Warden | verified |
| electric | Stormheart Reactor | Stormheart Works: lightning on the cable beam (grounding switch), relay-bulb lever pattern, colour-order routing console, capacitor offering; Stormheart | verified |
| ice | Crown of Winter | Glacier Crown: cracking ice bridge + freezing blasts, thermal-balance shrine, thaw valves, aurora colour order, frost offering; Winter Warden | verified |
| flying | Eye of the Gale | Gale Eyrie: winch-lowered skybridge, gusts, levitation updraft to a ring gallery with vane levers, feather offering; Gale Warden (breeze) | verified |
| psychic | Mindstar Observatory | Mindstar Observatory: folded-stair hall of floating platforms joined by teleport pads, memory sequence, constellation order, focus offering; Mindstar (evoker) | verified |
| ghost | Hollow Bell Sanctum | Hollow Bell Crypt: dark whispering nave (wither bolts) with a night-vision shrine and a seal switch, memorial sequence, lantern offering; Hollow Bell (vex) | verified |
| dragon | Elder Wyrm Crucible | Elder Wyrm Roost: round pit with crumbling vertebra spans and fire-breath vents, scale-alignment levers, hoard offering; Elder Wyrm | verified |
| bug | Monarch Brood Chamber | Monarch's Hive: ovoid-chain chambers, cobweb pit with a winch-lowered silk span, stingers, nectar offering; Monarch (spider) | verified |
| steel | Sovereign Forge | Sovereign Foundry: two-level gantry with a lift pad gated by the control levers, hydraulic presses, assembly-alignment, forge offering; Iron Sovereign (golem) | verified |
| fairy | Moonlit Promise Sanctum | Moonlit Promise: ribbon ledge round a pit with dazzle bursts, storybook colour sequence, petal alignment, wish offering; Promise-Keeper (witch) | verified |
| dark | Throne of the Eclipse | Eclipse Throne: generated alley maze with a hidden lever and false wall, blackwater causeway under a searchlight tower, mask offering, smuggler exchange; the Eclipse (enderman) | verified |
| rock | Titan's Cairn | Titan's Cairn: S-curved boulder gorge (falling boulders), terraced quarry pit with an underpass, resonance trial, mineral offering, geode vault; Titan (ravager) | verified |
| ground | Worldroot Fault | Worldroot Fault: V-cut fault with crumbling tiles and tremors, bucket-wheel excavator, dune court with a buried colossus, earth offering; Worldroot Warden (husk) | verified |
| poison | Venomheart Chamber | Venomheart Marsh: zigzag causeway over a sludge pit with gas bursts (ventilation switch), lab valves, reagent offering; Venomheart (cave spider) | verified |
| fighting | Grandmaster Circle | Grandmaster's Dojo: beam over practice pillars with swinging logs, rolling-log stair climb to an elevated court, belt-order board, discipline offering; Grandmaster (piglin brute) | verified |
| normal | Hearthwarden Hall | Hearthwarden Village: contained millrace with surges, market token exchange, signpost route order, orchard lever pattern; Hearthwarden (vindicator) | verified |

"verified" = kit checks (water containment in every state, enclosure, AUTO playthrough with lock ordering, harmful ground, 5/5/5 landmark contract), vanilla world diff with 0 mismatches over 3000 ticks, and the mod E2E with a real bot (levers, locks, trials, sequences, ward strikes, boss kill, completion). None has been seen by a human or walked with real player physics; the jar is not deployed.
`python tools/dungeons/regress.py` re-runs the E2E for every dungeon; `verify.py <id>` runs the whole pipeline for one.

## How to build the next type
`tools/dungeons/<id>.py` exposes `build()` returning a `kit.Dungeon` (see `water.py`). `python tools/dungeons/run.py <id> --write` runs the
checks (water containment in every state, enclosure, AUTO playthrough with ordering rules, breath, harmful ground, 5/5/5 landmark contract)
and installs `dungeons/<id>/{dungeon.schem,layout.json}` + `index.json`. Then `gradlew build`, `python tools/cinderforge/vanilla_world_test.py <schem> <scratch> overworld 3000`,
and `python tools/dungeons/e2e.py <jar> <id> <scratch>` (bot clicks levers, locks, trial, wards, boss). Run with JDK 21 and
`GRADLE_USER_HOME=/l/gradle-home-ascensiondungeons` set in the same command.
Engine DSL implemented (all used by the dungeons above): mechanics `lever_sequence`, `lock` (key or `requires`), `trial`; hazards `tide`, `zone`; effects clear/fill/say/sound/particles/spawn/effect/give/mark/delay;
bosses with phases + strike-target shield; completion. Still to add when a dungeon needs it: `sequence`, `toggle_pattern`, `jet/vent/rise/lightning/collapse/gust`, `pads`, wet spawn cells.
Fluid rule learned: water must never touch open air, and waterlogged blocks (ladders, stairs, lights) left behind after a drain become sources: keep them out of drained boxes; every dry exit from a flooding basin must climb above the water line.
