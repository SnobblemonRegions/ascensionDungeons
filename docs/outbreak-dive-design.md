# Outbreak dives: a push-or-extract loop for authored dungeons

Status (2026-11-16): **built and playable in the water dungeon; checked by unit tests, a protocol bot and a headless harness with
Cobblemon loaded. Not yet seen by a person in a real client, not tried with more than one player, and never run against a live
AscensionLib.** Every number is provisional. Vocabulary is in `CONTEXT.md`.

Owner decisions (2026-11-15): loss is **haul-only**; the timed escalation is the **Outbreak**; **water** first (Cinderforge is
outdated and untouched); **Lootr is required**; a failed exit **strips marked loot** from inventory, armour, offhand and party held
items, and anything already consumed or converted stays; loot chests get **our own models, one per rarity**; dungeon mobs are
**enemy Cobblemon** with spells and aggro styles, and the dungeon's type decides the pool. After that the owner handed over design
authority; the calls made under it are listed in "Design calls" below so they are easy to overrule.

## Why

An authored dungeon used to pay once, on a win at the end, with no decision in the middle. An Outbreak dive adds the loop that makes
an extraction dungeon fun: the longer the party stays, the worse it gets and the more it carries, and the party chooses when to
leave. The idea is borrowed from Runebind's design (escalating difficulty, timed chests, a charged exit portal); names, lore and
art are not.

## The loop

Enter -> the Outbreak rises -> haul accumulates, unbanked -> bank at checkpoints, or extract through the portal -> one AscensionLib
settlement per player -> a knock-out, timeout, walk-out or disconnect pays **only what was banked**. Pokémon, gear and the wallet
are never at risk (AscensionLib: reward, never remove). Pay follows difficulty reached, not loitering: the Outbreak raises the zone
tier and the payout uses the tier reached; the daily budget and the no-Cores rule for dungeons are AscensionLib's and still apply.

## What a player meets (water, `/dungeon type water`)

- **Outbreak bar.** Stage 0 to 5: Calm, Restless, Agitated, Swarming, Rampage, Mass Outbreak. One stage per 3 minutes. Each stage
  survived adds one win to everyone's haul and is announced. The zone tier rises one per two stages (3, 4, 4, 5, 5 for water).
- **Waves.** Every 40 s (faster at higher stages) enemies arrive in the room the party is in: 2 at stage 0, +1 per stage, +1 per
  extra player, never more than 14 alive. Enemies the whole party has left 56 blocks behind are let go.
- **Enemies.** With Cobblemon: Pokémon from the dungeon's pools (`wild_low`, then `wild_mid`), fighting in real time, uncatchable
  and not battleable turn-based. Without it: the vanilla mob the spawn names. Each spawn has an **aggro style** (below). Elites
  (6 percent per stage, at most 30) glow, have double health, hit 1.4 times as hard and cast a spell.
- **Spells.** A cyan ring on the ground, then the hit; leave the ring to dodge. Water has Whirlpool (pulls in), Water Spout
  (throws up) and Bubble Beam (slows). Killing the caster cancels the cast.
- **Chests**, placed fresh each run from its seed, on open ground away from mechanics, hazards and the boss room:
  rarity chests (Common to Epic, 5), outbreak chests (2; their rarity grows one step per two stages until opened), a timer chest
  (sealed until stage 2, then free) and a strongbox (Epic; opening it sends six guardians). Each has a coloured label, rarity
  sparkles, a rarity floor block, its own loot table (`chests/rarity/*`) and, with the pack, its own model.
- **Curse altars** (2). Touch one: the Outbreak jumps two minutes ahead and everyone's haul gains a win.
- **Checkpoints.** Finishing `sluice`, `pearl_trial` or `offering` banks everyone's haul. The trial is also a win.
- **Extraction portal**, a few blocks from the entrance. Kills within 24 blocks charge it (an elite counts three, ten needed). Full, it
  opens for 20 s; step on it to leave with everything. Missed, it closes and needs 50 percent more.
- **Leaving.** Extracted: keep everything. Boss defeated: everyone inside keeps everything. Knocked out, out of time, `/dungeon leave`
  or disconnected: keep what was banked. **Nobody dies in a dive:** a lethal hit heals the player, takes the unbanked haul and sends
  them home.

## How it is built

Pure logic, Minecraft-free, in `com.ascensiondungeons.outbreak` (unit tested):

| Class | Job |
|---|---|
| `OutbreakClock` | time + curse penalties -> stage, progress, spawn scale, elite chance, zone tier. Nothing lowers it. |
| `Haul` | per-player ledger of wins, chests, the boss; `bank`; what each way of leaving keeps. |
| `ExtractionPortal` | charging -> open -> charging, a window, growing cost. |
| `WavePlanner` | how many enemies a wave adds. |
| `FeaturePlanner` | where a run's chests, altars and portal go, from the seed, with spacing rules. |
| `ChestRarity`, `ChestLook` | the rarity ladder, colours, loot tables, and which pack model a chest shows. |
| `DiveSettlement` | groups players into the fewest `settleDungeonRun` calls. |
| `Aggro` | the six styles: who an enemy attacks, when it goes home. |
| `Spell`, `Spell.Cast` | a telegraphed attack and its timing. |

Thin game layer in `com.ascensiondungeons.mc`:

- `OutbreakDive`: one per run; boss bars, waves, spells' particles and hits, chest and altar behaviour, the portal, settlement. It
  applies decisions from the pure classes and makes none of its own.
- `AuthoredDirector.Session` creates the dive when the dungeon has an `outbreak.json` and hands it the session's events.
- `AuthoredChunkGenerator` places the feature blocks; `AuthoredLayout.features(seed)` is the shared, pure plan.
- `RunLoot` + `lootr/RunLootFilters`: marks chest loot at the source (Lootr's fill hook), clears or strips it, sweeps at login.
  `AuthoredPartyItems` extends that to party held items when Cobblemon is installed.
- `AuthoredCombat` asks a `Targeting` hook for each hostile's target, so aggro styles apply to dungeon Pokémon.
- `AuthoredEvents`: the knock-out (`ALLOW_DEATH`), the login sweep, and chest/altar clicks.

A dungeon becomes an Outbreak dive by adding `dungeons/<id>/outbreak.json` (clock, portal, waves, elite, guardians, checkpoints,
features, spells). The dungeon kit does not write that file, so regenerating `layout.json` leaves it alone. The other sixteen
dungeons have none and behave as before.

## Aggro styles

| Style | Behaviour |
|---|---|
| Hunter | chases the nearest target in sight (26 blocks) |
| Sentry | fights whatever comes within 10 blocks of its post, then walks back |
| Guardian | fights within 6 blocks of its spot and never leaves it |
| Ambusher | still until someone comes within 5 blocks or hits it, then hunts |
| Skirmisher | fights from range (asks to stay 8 blocks away) |
| Pack | ignores distant targets until it or a packmate is provoked; then the pack wakes |

For Pokémon the decision is exact (real-time combat asks the dive). For vanilla fallback mobs it is approximated by steering their
own AI (follow range, a frozen state for sleeping ambushers and packs).

## Chest models

`tools/outbreak/make_chest_pack.py resourcepack [preview.png]` draws eleven original models procedurally and writes `resourcepack/`;
`docs/outbreak-chests-preview.png` is its own preview. Common is a plank chest with iron straps; Uncommon adds cyan caps and a gem
latch; Rare is blue steel with a stepped lid and gold; Epic is violet and gold with pillars and crystals; the timer chest is gold
with a clock dial, chained while sealed; the strongbox is a squat safe with a red eye; the outbreak chest is stone with growths in
the colour of the rarity it has reached, more of them the higher it is. Colours follow AscensionLib's `rarity-colors.json`.

In the world a model is an `item_display` of `minecraft:popped_chorus_fruit` with a custom-model number (`ChestLook`), sitting over
the real Lootr chest. It is **off by default** (`features.models` in `outbreak.json`): a player without the pack would see a floating
chorus fruit. Turn it on once the pack is in the server's resource pack. Without it a chest is told apart by its label, its
sparkles and its floor block, so nothing depends on the pack.

## Verification

| What | How | Result |
|---|---|---|
| Rules | `./gradlew test`: 177 tests, 64 of them for the outbreak package and the water data (200 seeds of feature placement on the real layout) | pass |
| A full dive without Cobblemon | `tools/outbreak/dive_bot.js`: a protocol bot on a dev server: marking, banking, altar, timer chest, outbreak chest, strongbox, portal extraction, knock-out, walk-out, disconnect and login sweep | 19/19 on the final code |
| A dive with the whole mod set | `DUNGEONSPIKE_DIVE=1 ./gradlew runServer` (`spike/DiveE2E`): a stand-in player joins through the normal login path and is driven from code | 17/17: wave enemies are uncatchable, unbattleable Pokémon from the water pools; they hunt and hurt the player; rarity tables roll Cobblemon items; checkpoint, knock-out and extraction behave; loot hidden as a party held item is banked or stripped; also 17/17 with model displays on |
| Chest pack | `ChestLookTest`: ids in the pack match the code, every model and texture exists | pass |

Use `-Dascensiondungeons.outbreakSpeed=6` for both live checks or they take many minutes.

**Not verified.** How any of it looks and feels in a real client. More than one player (shared banking, a second player opening an
outbreak chest after it grew, party-size scaling). A live AscensionLib: its jar cannot be built here (it needs a local
`webgui-dev.jar`), so the per-player `settleDungeonRun` calls and mid-run `openZone` tier changes are covered only by unit tests of
the grouping and by log lines. The boss flow inside a dive. Spells against Pokémon-owned targets. Performance at stage 5 with four
players. Balance.

## Design calls made under delegated authority (overrule freely)

1. **Nobody dies in a dive.** A lethal hit is a knock-out. It is what makes haul-only loss true: a real death would drop the player's own gear.
2. **What earns haul:** each Outbreak stage survived, each trial, each curse altar, the boss. Chests are counted and reported but are their own reward (the items).
3. **Checkpoints bank, they do not heal or save position.** Water's are `sluice`, `pearl_trial` and `offering`.
4. **The portal charges only from kills near it**, so extracting means holding the exit, and a party deep in the dungeon cannot open it by accident.
5. **Features are placed per run from the seed** rather than authored, so every dive is laid out differently without touching a schematic.
6. **Real-time combat is always on in a dive** (it stays behind `authoredCombat` elsewhere). Without it dive enemies were passive.
7. **Dive enemies are uncatchable and unbattleable.** The dungeon contract's catchable wild Pokémon are a separate thing and can be added as a calm pool later.
8. **Without Lootr a dungeon with Outbreak settings runs as an ordinary dungeon**, with a warning, rather than half-working.
9. **Loot left by a crashed or abandoned run is removed at next login.** A crash therefore costs unbanked loot, never banked loot (banking clears the mark at once).
10. **Chest models are opt-in per dungeon** until the pack is hosted.

## Known gaps and next steps

1. **Play it.** One person, then a party, with the real client and the full mod set. Nothing here replaces that.
2. **Is Lootr in the players' client pack?** Lootr registers its own blocks; a client without it may not be able to join. Confirm before relying on "Lootr is required".
3. **Reconcile the `run` package.** `RunManager` and friends are a reconstruction (the originals were never committed because `.gitignore` hid them). The dive leans on `RunManager.leave`, member presence and the release order.
4. **Run against AscensionLib** on the test server and read the `settleDungeonRun` statuses and `ledger` telemetry (`dungeon_run`).
5. **Smuggling.** Covered: inventory, armour, offhand, cursor, ender chest, bundles, shulker boxes, party held items. Not covered: the Cobblemon PC if a player can open it inside a dive; items handed to another player who then extracts (allowed on purpose).
6. **The boss is still the vanilla Tide Warden** unless `authoredRealtimeBoss` is on. Making bosses Pokémon everywhere is its own piece of work.
7. **Spells are generic**, declared per spawn or per elite list, not tied to a species' real moves.
8. **The other sixteen dungeons** need an `outbreak.json` each (data only) and their own spells.
9. **Mid-run tier and rarity.** AscensionLib's wild rarity stops improving at tier 4; stages 3 to 5 are carried by spawn pressure and elites.
10. **Polymer.** Per-player model displays (hide the model from players without the pack) would need Polymer here, as Towers and Raids use it.

## Appendix: the Lootr hook (spiked 2026-11-15)

Read from Lootr 1.11.38.127 with `javap`. `ILootrFilter.mutate(ObjectArrayList<ItemStack>, LootFiller.LootFillerState, LootContext,
RandomSource)` is called for every registered filter from Lootr's `MixinLootTable`, inside `LootTable.fill` just before
`shuffleAndSplitItems`. It runs for every fill, and the state is null unless Lootr is doing a per-player roll. The state gives the
player and the chest's dimension and position. Filters are found with `ServiceLoader`, so registration is a
`META-INF/services/noobanidus.mods.lootr.common.api.filter.ILootrFilterProvider` file and the class only loads when Lootr does.
Returning `true` stops later filters, so ours returns `false`.
