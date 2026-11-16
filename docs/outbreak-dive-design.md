# Outbreak dives: a push-or-extract loop for the type dungeons

Status (2026-11-16): **built for all seventeen type dungeons; checked by unit tests, a protocol bot and a headless harness with
Cobblemon loaded. Not yet seen by a person in a real client, not tried with more than one player, and never run against a live
AscensionLib.** Every number is provisional. Vocabulary is in `CONTEXT.md`.

## Owner decisions

- 2026-11-15: loss is **haul-only**; the timed escalation is the **Outbreak**; **Lootr is required**; a failed exit **strips marked
  loot** (inventory, armour, offhand, party held items; anything consumed or converted stays); chests get **our own models, one per
  rarity**; dungeon mobs are **enemy Cobblemon** with spells and aggro styles, and the dungeon's type decides the pool.
- 2026-11-16: **every type dungeon is a dive. Any dungeon not named after a Pokémon type is outdated** (cinderforge, the Halloween
  graveyard, the old districts): they are left as they are and get nothing new. Dungeons must be **larger, more complex and
  procedural**, so players explore for a long time. **Extracting costs something: fight a boss, or lose 35% of your loot, and the
  loot you would lose is shown** so a player knows exactly what they are giving up and must judge the right moment to leave.
- Design authority for the rest was delegated; those calls are listed under "Design calls" so they are easy to overrule.

## The loop

Enter -> the Outbreak rises -> explore the dungeon and the Depths below it, gathering a haul -> bank at checkpoints -> climb back
to the portal -> **beat the gatekeeper** (leave with everything) **or pay the toll** (leave with 65% of what is unbanked) -> one
AscensionLib settlement per player. A knock-out, timeout, walk-out or disconnect pays only what was banked. Pokémon, gear and the
wallet are never at risk. The gatekeeper grows with the Outbreak, so the longer a party stays, the more it carries and the harder
it is to keep all of it: that is the decision the whole run builds up to.

## What a player meets (`/dungeon type <type>`)

- **Outbreak bar.** Stage 0 to 5: Calm, Restless, Agitated, Swarming, Rampage, Mass Outbreak. One stage per 6 minutes; a run lasts
  45. Each stage survived adds a win to everyone's haul. The zone tier rises one per two stages.
- **Waves** every 45 s (faster at higher stages) in whatever room the party is in, upstairs or down: 2 enemies, +1 per stage, +1 per
  extra player, at most 14 alive. Enemies the whole party left 56 blocks behind are let go.
- **Enemies.** With Cobblemon: Pokémon from the dungeon's pools, fighting in real time, uncatchable and not battleable turn-based.
  Without it: the vanilla mobs the dungeon's own encounters use. Each has an **aggro style**. **Elites** (6% per stage, at most 30%)
  glow, are twice as tough, hit 1.4 times as hard and cast a spell.
- **Spells.** A ring on the ground in the type's colour, then the hit; leave the ring to dodge. Three per type, named after moves,
  with twelve effects between them (pull, push, knock-up, slow, freeze, burn, poison, wither, blind, levitate, weaken, plain damage).
  Killing the caster cancels the cast.
- **The Depths.** A stairway beside the entrance leads down to a procedural maze: about 37 rooms on a 7 x 7 grid, joined with loops,
  in eight shapes (entrance hall, hall, pillars, cross, ring, aisles, dais, vault; dead ends are usually vaults), built from the
  dungeon's own blocks and different every run. `docs/outbreak-depths-preview.png` shows two. Most of the run's chests are down
  there. The spot where you arrive is the way back up; the portal is upstairs.
- **Chests**, placed fresh each run: 16 rarity chests (Common to Epic), 5 outbreak chests (their rarity grows a step per two stages
  until opened), 2 timer chests (sealed until stage 2), 3 strongboxes (Epic; opening one sends six guardians). Each has a coloured
  label, sparkles, a rarity floor block, its own loot table and, with the pack, its own model.
- **Curse altars** (5). Touch one: the Outbreak jumps three minutes ahead and everyone's haul gains a win.
- **Checkpoints.** Finishing the dungeon's first gate, its trial, or its offering lock banks everyone's haul.
- **The extraction portal**, a few blocks from the entrance. Step on it and a chest window opens:
  - the top rows show **exactly the items the toll would take**;
  - **Pay the toll and leave**: lose those items and 35% of unbanked wins, keep the rest;
  - **Challenge the gatekeeper**: a boss from the dungeon's `boss` pool appears. It is six times as tough as a normal enemy, plus
    half again per Outbreak stage, hits a fifth harder per stage, gains three levels per stage and casts the type's spells. When it
    falls the portal opens for 30 s and **everyone** can step through with everything. Each gatekeeper beaten makes the next 25%
    stronger. Knocked out during the fight: the usual knock-out.
  - **Keep exploring**: close the window; step off and on to see it again.
- **Leaving otherwise.** Dungeon boss defeated: everyone inside keeps everything. Knocked out, out of time, `/dungeon leave` or
  disconnected: keep what was banked. **Nobody dies in a dive.**

### The toll, precisely

Loot is counted in items and grouped by kind, so splitting a stack into many small ones changes nothing. Each kind loses 35% of
its count rounded down; the items still owed (the total is 35% of all unbanked items, at least one) come one each from the kinds
with the largest remainders, ties broken by a shuffle seeded by the run and the player. The same loot therefore always shows, and
costs, the same items; if a player's loot changes while the window is open they are shown it again before anything is taken.
Unbanked wins lose 35%, rounded to the nearest (one win loses nothing). Marked loot tucked into a bundle, a shulker box, the ender
chest, the cursor or a party Pokémon's held item is forfeit in full, and the window says how many such stacks there are. Banked
loot is never touched.

## How it is built

Pure logic, Minecraft-free (unit tested):

| Class | Job |
|---|---|
| `outbreak/OutbreakClock` | time + curse penalties -> stage, progress, spawn scale, elite chance, zone tier |
| `outbreak/Haul` | per-player ledger of wins, chests, the boss; `bank`; what each way of leaving keeps; forfeiting wins to the toll |
| `outbreak/ExtractionPortal` | idle -> fight -> open -> idle; the window; gatekeeper strength |
| `outbreak/Toll` | which items and how many wins the toll takes |
| `outbreak/WavePlanner` | how many enemies a wave adds |
| `outbreak/FeaturePlanner` | where a run's chests, altars, portal and stairway go, from the seed |
| `outbreak/ChestRarity`, `ChestLook` | the rarity ladder, colours, loot tables, pack model ids |
| `outbreak/DiveSettlement` | groups players into the fewest `settleDungeonRun` calls |
| `outbreak/Aggro` | six styles: who an enemy attacks, when it goes home |
| `outbreak/Spell`, `Spell.Cast` | a telegraphed attack and its timing |
| `gen/depths/Depths` | the procedural maze: grid, spanning tree plus loops, room shapes, lights, standing cells, a reachability proof |

Thin game layer in `com.ascensiondungeons.mc`:

- `OutbreakDive`: one per run. Boss bars, waves, spells, chests and altars, the stairway, the extraction window and gatekeeper,
  settlement. It applies decisions from the pure classes and makes none of its own.
- `TollMenu`: the extraction window. A plain six-row chest menu (no client mod); every click is answered and nothing moves.
- `AuthoredLayout`: `outbreak()`, and per run seed `depths(seed)`, `rooms(seed)`, `features(seed)`, `depthsPalette()`.
- `AuthoredChunkGenerator`: builds the Depths beside the authored dungeon and places feature blocks; sizes the world border.
- `RunLoot` + `lootr/RunLootFilters`: marks chest loot at the source, clears or strips it, sweeps at login, finds hidden loot.
  `AuthoredPartyItems` covers party held items when Cobblemon is installed.
- `AuthoredCombat` asks a `Targeting` hook for each hostile's target, so aggro styles apply to dungeon Pokémon.
- `AuthoredEvents`: the knock-out, the login sweep, toughness (below), and chest/altar clicks.

**Toughness.** A vanilla creature is made tougher through its health attribute. A Pokémon cannot be: Cobblemon keeps the entity's
health in step with its stats and resets a raised maximum within ticks. So an elite or gatekeeper Pokémon takes a share of each
hit instead (six times as tough means a sixth of every hit), for any damage source.

**Settings.** `dungeons/<type>/outbreak.json`, written by `tools/outbreak/make_outbreak_settings.py` from the dungeon's own
`layout.json` (pools, trial, locks, the fallback mobs its encounters use) plus a per-type table in the script: three spells, a
colour, enemy names, and a Depths palette where the dungeon's own blocks do not suit. Edit the table and re-run; do not hand-edit
the output. The dungeon kit does not write this file, so regenerating `layout.json` leaves it alone.

**The Depths' blocks** are the four building blocks the dungeon's schematic uses most (wall, floor, accent, pillar), after a strict
filter that drops anything not a plain, full, inert block. Ice is hand-picked (its own blocks are slippery).

## Aggro styles

| Style | Behaviour |
|---|---|
| Hunter | chases the nearest target in sight (26 blocks) |
| Sentry | fights whatever comes within 10 blocks of its post, then walks back |
| Guardian | fights within 6 blocks of its spot and never leaves it |
| Ambusher | still until someone comes within 5 blocks or hits it, then hunts |
| Skirmisher | fights from range |
| Pack | ignores distant targets until it or a packmate is provoked; then the pack wakes |

Exact for Pokémon (real-time combat asks the dive); approximated for vanilla fallback mobs by steering their own AI.

## Chest models

`tools/outbreak/make_chest_pack.py resourcepack [preview.png]` draws eleven original models and writes `resourcepack/`;
`docs/outbreak-chests-preview.png` is its preview. In the world a model is an `item_display` of `minecraft:popped_chorus_fruit`
with a custom-model number, over the real Lootr chest. **Off by default** (`features.models`): a player without the pack would
see a floating chorus fruit. A chest is always identifiable without it (label, sparkles, floor block).

## Verification

| What | How | Result |
|---|---|---|
| Rules and data | `./gradlew test` | 198 tests pass. Includes 300 seeds of Depths (every standing cell reachable, sealed shell, loops, lighting) and, for all 17 type dungeons, valid settings and full feature placement on sound ground for 40 seeds each |
| Every type dungeon, whole mod set | `DUNGEONSPIKE_DIVE=all ./gradlew runServer` (`spike/DiveE2E`): a stand-in player joins through the normal login path and plays a short dive in each | 120/120: each dungeon starts, spawns Pokémon of its type, has Depths built from its palette, gives marked loot from a chest down there, shows the toll and lets the player out on paying it |
| One long dive, whole mod set | `DUNGEONSPIKE_DIVE=1 ./gradlew runServer` (water) | 30/30: enemies are uncatchable, unbattleable Pokémon that hunt and hurt the player; stairway down and back; checkpoint; knock-out; loot hidden on a party Pokémon is banked, stripped or forfeit; the toll window showed 7 of 19 items and paying took exactly those; a 12-point hit cost a stage-0 gatekeeper 2; beating it opened the portal |
| The same over the real protocol | `tools/outbreak/dive_bot.js`: a mineflayer bot, server without Cobblemon | 25/26 on the last full run. Over real packets the extraction window opened as a chest screen, showed 7 of 21 unbanked items, a click on a shown item moved nothing, paying took exactly those 7 and one win, and beating the gatekeeper let the player out with everything; marking, banking, altar, timer chest, outbreak chest, strongbox, the stairway both ways, knock-out, walk-out and the login sweep all pass. The one failure was the bot's own check on the Depths chest: it counted marked stacks, and the new loot merged into stacks it already held (the server log shows that chest opened and its loot marked). The check now counts items; the script has not been rerun since that one-line fix |
| Chest pack | `ChestLookTest` | ids in the pack match the code; every model and texture exists |

Use `-Dascensiondungeons.outbreakSpeed=12` for the live checks or they take many minutes.

**Not verified.** How any of it looks and feels in a real client. More than one player. A live AscensionLib (its jar needs a local
`webgui-dev.jar` and cannot be built here), so payouts are covered only by unit tests of the grouping and by log lines. The
authored boss flow inside a dive. Whether a gatekeeper is beatable but hard at each stage: its numbers are guesses and need play.
Performance with four players in the Depths at stage 5. The chest models in a client.

## Design calls made under delegated authority (overrule freely)

1. **Nobody dies in a dive.** A lethal hit is a knock-out; a real death would drop the player's own gear.
2. **What earns wins:** each Outbreak stage survived, each trial, each curse altar, the boss.
3. **The Depths are a separate area reached by a stairway**, not an extension cut into each schematic: it needs no edits to
   seventeen hand-built dungeons, and it makes the trek back to the portal part of the risk.
4. **The portal stays upstairs**, so leaving means climbing out through whatever the Outbreak has become.
5. **The toll is counted in items by kind and seeded**, so it cannot be gamed by splitting stacks and never surprises.
6. **Hidden loot is forfeit to the toll**, so tucking loot away never pays.
7. **The toll also takes 35% of unbanked wins.** "Loot" could be read as items only; wins are part of what a player carries out.
8. **The gatekeeper comes from the dungeon's boss pool and its window is shared**: one party member's fight lets everyone out.
9. **Kill-charging the portal was removed** in favour of the fight-or-pay choice.
10. **Dives last 45 minutes with a stage every 6**, so there is time to explore before Mass Outbreak.
11. **Real-time combat is always on in a dive**; dive enemies are **uncatchable and unbattleable**.
12. **Without Lootr a dive dungeon runs as an ordinary dungeon**, with a warning.
13. **Loot left by a crashed or abandoned run is removed at next login.**
14. **Chest models are opt-in per dungeon** until the pack is hosted.

## Known gaps and next steps

1. **Play it**, alone and then in a party, with the real client and mod set.
2. **Fire has no type dungeon.** Cinderforge was the fire dungeon and is outdated; a `fire` authored dungeon is needed to complete the set.
3. **The Depths are a first version:** one storey, rooms on a regular grid, no hazards or mechanics of their own, no type-specific
   set pieces. Good next steps: stairs and a second level, larger landmark rooms, per-type hazards from the authored DSL, secret rooms.
4. **Is Lootr in the players' client pack?** It registers its own blocks.
5. **Reconcile the `run` package**: it is a reconstruction; the dive leans on `RunManager.leave`, member presence and release order.
6. **Run against AscensionLib** and read the `settleDungeonRun` statuses.
7. **Smuggling not covered:** the Cobblemon PC, if a player can open it inside a dive.
8. **The authored final boss is still a vanilla mob** unless `authoredRealtimeBoss` is on.
9. **Outdated dungeons are still registered** (`/dungeon type cinderforge`, `halloween_graveyard`, `/dungeon start`). Nothing was
   removed; say the word and they can be hidden or deleted.
10. **Polymer** would allow per-player chest models (hidden from players without the pack).

## Appendix: the Lootr hook (spiked 2026-11-15)

Read from Lootr 1.11.38.127 with `javap`. `ILootrFilter.mutate(ObjectArrayList<ItemStack>, LootFiller.LootFillerState, LootContext,
RandomSource)` is called for every registered filter from Lootr's `MixinLootTable`, inside `LootTable.fill` just before
`shuffleAndSplitItems`. It runs for every fill, and the state is null unless Lootr is doing a per-player roll. The state gives the
player and the chest's dimension and position. Filters are found with `ServiceLoader`, so registration is a
`META-INF/services/noobanidus.mods.lootr.common.api.filter.ILootrFilterProvider` file and the class only loads when Lootr does.
Returning `true` stops later filters, so ours returns `false`.
