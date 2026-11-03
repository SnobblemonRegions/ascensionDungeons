# Cinderforge Bastion: runtime (mod 0.0.7-cinderforge)

The hand-built fire dungeon (see `cinderforge-bastion-blueprint.md`) as a playable run type. Server-side only: vanilla
blocks, mobs, boss bars, sounds and particles, so players need no client install. Cobblemon (wild Pokemon) and Lootr (chest
loot) are optional.

## Playing it
- `/dungeon type cinderforge [party]` (op level 2, same as the Fire pilot). The run opens a temporary level, teleports the party
  to the Ashen Gatehouse facing +Z, and the usual run clock applies: `cinderforgeDurationTicks` in
  `config/ascensiondungeons.json` (default 25 minutes). `/dungeon leave`, `status`, `list`, `end <run>` work as for every run.
- Winning ends the run early (`RunManager.complete`). There is no automatic boss phase: the timer simply ends the run.
- Players cannot break or place blocks, use buckets, flint, fire charges or beds inside the dungeon. The only block interactions
  that work are the ones below, plus chests and levers.

## Mechanics (class `CinderforgeDirector`, one `Session` per run)
| Mechanic | Trigger | Effect |
|---|---|---|
| Cistern drain | Three levers in the Magma Cistern, in order 1-2-3. A wrong lever is refused ("jammed" / "already thrown"). | Lava layers sink (top 2, next 2, the rest), one layer per 8 ticks. After lever 3: the tunnel plug and the descent-stair gate open, then the Causeway's trench drains and its two drain plugs and hidden doors open. Lava is removed with block flag 2 so it never spawns flowing lava. |
| Forge Key | The Reliquary chest holds exactly one key (a renamed, enchant-glinted tripwire hook tagged `ascension_key=forge_key`). It is a plain chest, not Lootr, so it is one key per run. | Right-click the sealed Barracks door with the key: the door opens, the key is consumed. Without it: a message. |
| Geysers | Eight flush magma vents on the Causeway deck, active when a player is within 40 blocks. | 120-tick cycle: smoke warning, then a 14-tick eruption. One hit per eruption: 3 damage, 2 s of fire, knocked up. |
| Furnaces | While the Forge Overseer lives. | The furnace for the Overseer's current phase (0-2) flames for 50 of every 100 ticks: 1.5 damage per 5 ticks to players in front of the mouth. |
| Rising heat | First player entering the Obsidian Spiral. | After a 10 s head start the "level" rises one block per 30 ticks. Players below it take 2 damage and 4 s of fire every 10 ticks. Damage and particles only: no lava is placed. |
| Forge Overseer | First player entering the Smelting Works. | Wither skeleton, scale 1.6, netherite gear, about 8 per hit, health 9x a normal wither skeleton (scaled by party size), a red boss bar. A magma cube pair joins at each phase change. Its death opens the sealed slag gate to the Spiral and drops two golden apples. |
| Forge Heart | First player entering the Forge Heart. | Blaze, scale 3, invulnerable ("shielded"). Strike the glowing heat vents (left-click) to cool them: each becomes blackstone. Three cooled vents shatter the shield for 18 s ("exposed", glowing), then the vents reheat and a blaze and a wither skeleton join. Death: the crucible lava becomes a lift pad, the crucible rim opens. |
| Lift | After the Heart dies. | Standing on the pad for 3 s teleports a player to the Barracks lift cell (its doorway opens too). The Bastion "collapses" 45 s after the Heart dies: `RunManager.complete`. |
| Encounters | First player to enter a room. | R2: 3 blazes + 3 wither skeletons. R3: 4 piglin brutes + 3 wither skeletons. R4: 3 blazes + 4 magma cubes. R5: 4 wither skeletons. R7: 3 blazes. Spawned on exported standing cells, away from players, persistent. Skipped entirely on peaceful difficulty (the Spiral heat still runs). |
| Wild Pokemon | Cobblemon installed. | `CinderforgePopulation`: same `ashen_caldera` spawn table as the Fire pilot, up to 6 per room and 48 per run, in rooms the party is in, never in R1, R7 or R8. |
| Level rules | Set through Fantasy's level config, so they belong to the run level only. | No natural mob spawning, no fire tick, no mob griefing, no day cycle or weather. The level mirrors the server's difficulty (Fantasy's default would pin it at Normal); a test proves the overworld's own rules are untouched. |

Health scales with the party size at the start of the run: `1 + 0.6 * (players - 1)`. Every number above is provisional and lives
as a constant at the top of `CinderforgeDirector.Session` or in `CinderforgePopulation`.

## Code map
- `gen/cinderforge/CinderforgeLayout` loads `data/ascensiondungeons/cinderforge/dungeon.schem` and `layout.json`. No Minecraft classes.
- `mc/CinderforgeChunkGenerator` renders the schematic into chunks at world origin (0, 20, 0), adds 617 invisible `light` blocks
  (level 10), seals the Overseer gate, and places the chests (Lootr when installed) and the key.
- `mc/FantasyRunHost.openCinderforge` creates the level; `RunManager` accepts type `cinderforge`.
- `mc/CinderforgeDirector`, `CinderforgeEvents` (block-use, attack, break, death hooks), `CinderforgeItems`, `CinderforgePopulation`.
- `[cf-debug]` lines (damage source and place) are logged only with `-Dascensiondungeons.debug=true`.

## Regenerating the data
`tools/cinderforge/build_rooms38.py <dir>` rebuilds the schematic, the checks and `cinderforge_runtime.json` from the generator
(rooms 1-2 come from `build_rooms12.py`). Copy `cinderforge_full_dungeon.schem` to `dungeon.schem` and
`cinderforge_runtime.json` to `layout.json` in `src/main/resources/data/ascensiondungeons/cinderforge/`. `CinderforgeLayoutTest`
fails if the two drift apart.

## Verification
| What | How | Result |
|---|---|---|
| Schematic geometry | `build_rooms38.py` checks: lava containment, no exposed lava, sealed, walk scenarios | pass |
| Block fidelity and lava stability | `vanilla_world_test.py` on vanilla 1.21.1, overworld and Nether, 3000 ticks, full block diff | pass; the leak control fails as it should |
| Walkability | `bot_walk_test.py`: a mineflayer bot with real physics, 7 routes | pass |
| The mod | `mod_e2e.py` + `plan_mod.py`: a bot types the real command on a Fabric server running the jar | M1 progression, M2 full playthrough to completion, M3 geysers, M4/M5 rising heat, M6 furnaces: pass |
| Unit tests | `./gradlew test` | 103 pass (new: `CinderforgeLayoutTest`, run-type tests in `RunManagerTest`) |
| With the whole mod set | `CF_FULL_MODS=1`: Cobblemon 1.8.1, Lootr, AscensionLib, WorldEdit... | M1 passes; M7 (wild Pokemon) passes; no errors from other directors |
| Nothing else broke | `legacy_smoke.py` | the district `/dungeon start` still opens a run; the Fire pilot gate message is unchanged |

Tooling notes: use `GRADLE_USER_HOME` pointing at a private copy of the caches when another process holds the shared Loom jar;
the bot scripts need Node and the `mineflayer` install in `L:\claude-cobbleraids-work\bot\node_modules`.

## Not done
- Rewards: no AscensionLib `settleDungeonRun` call and no run-level zone yet (see the AscensionLib contract note).
- The two bosses are vanilla mobs. A CobbleRaids raid boss for the Forge Heart (as the Fire pilot does) is possible later.
- Chest loot reuses the Ashen Caldera table; no Cinderforge-specific table.
- Balance, mob counts and timings are untested with real players.
- Runs are not persisted across a restart (as with every run).

## Deployment
The built jar is `build/libs/AscensionDungeons-0.0.7-cinderforge.jar`, staged at `testserver/mods-staged/`. The test server
(port 25582) still runs the old 0.0.5 jar: swapping it needs the server stopped, so run
`tools/cinderforge/deploy_to_testserver.ps1` when nobody is connected (it backs up the old jar to `mods-replaced-20261016` and
restarts the server). The config needs no change: the new `cinderforgeDurationTicks` falls back to its default.
