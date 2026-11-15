# Outbreak dives: a push-or-extract loop for authored dungeons

Status: **design plus a tested pure core, nothing wired in, nothing seen in a live run.** Every number is provisional.
Decisions (owner, 2026-11-15): loss is **haul-only**; the timed escalation is called the **Outbreak**; the first dungeon is **water**
(Cinderforge is outdated and is not part of this).

## Why

Authored dungeons pay once, on a win at the end (`AuthoredDirector.settleRewards` fires when the completion boss dies). There is no
decision in the middle of a run. This adds the loop that makes an extraction dungeon fun: the longer you stay the worse it gets and
the more it pays, and you choose when to leave. The idea is borrowed from Runebind's design (escalating difficulty, timed chests,
a charged exit portal); names, lore and assets are not.

## Vocabulary (add to a `CONTEXT.md` when this repo gets one)

| Term | Meaning | Not |
|---|---|---|
| **Threat** | The band chosen at the door (AscensionLib `threat_1..7`, Towers 0-VII). Already exists. | the time-driven rise |
| **Outbreak** | How worked-up a dungeon is, rising with time spent and with curses taken. Stage `0..maxStage`. Drives spawn pressure, elite chance and zone tier. Pokémon's *Mass Outbreak*: wild spawns surge. | Threat, the run deadline |
| **Haul** | What a player has earned in a run and not yet secured: arena wins, chests, the boss. | wallet materials, which are paid once at settlement |
| **Checkpoint** | A moment that **banks** the player's haul. | a respawn point |
| **Extraction** | Leaving through the portal with the whole haul. The portal is charged by kills, opens for a short window, and needs more charge each time it is missed. | `/dungeon leave` |

## The loop

Enter at a Threat band -> the Outbreak rises -> haul accumulates (unbanked) -> bank at checkpoints, **or** extract -> one AscensionLib
settlement per player -> a death, timeout or walk-out pays **only what was banked**. Pokémon, gear and the wallet are never at risk
(AscensionLib: reward, never remove).

Pay follows difficulty, not loitering: the Outbreak raises the zone tier and spawn pressure, and the payout tier is the highest zone
tier reached. The daily full-reward budget and the rule that dungeons never pay Ascension Cores still apply.

## What is built (`com.ascensiondungeons.outbreak`, Minecraft-free, 23 tests)

| Class | Job |
|---|---|
| `OutbreakClock` | ticks since start plus curse penalties -> stage; spawn scale, elite chance, zone tier per stage; `pollNewStage` for announcements. Nothing ever lowers it. |
| `Haul` | per-player ledger of `ARENA_WIN`, `CHEST`, `BOSS`; `bank`; `settle(player, Exit)` returns what to pay (extracted keeps all, died/timed out/left keeps banked only). |
| `ExtractionPortal` | `CHARGING -> OPEN -> CHARGING`, kills charge it, a window of `windowTicks`, `growthPercent` more charge after each miss. |

## Mapping to the existing AscensionLib contract (no change needed for v1)

`settleDungeonRun(runId, "VICTORY", tier, arenaWins, bossDefeated, players)` already pays by `arenaWins` and `bossDefeated`, and the
dungeon decides what counts as a victory. So per player: `Haul.settle(...)` gives `arenaWins` and `bossDefeated`; `tier` is the highest
`OutbreakClock.Params.zoneTier(baseTier, stage)` reached. Group players by their settlement and call once per distinct outcome
(arena wins is a run-wide argument today, so players with different banked wins need separate calls or a contract addition: open question 3).
`Haul.Settlement.chests` has **no wallet payout** in the contract; it only decides physical chest loot (open question 1).

## Water (`dungeons/water`, base tier 3)

Facts from `layout.json`: biome lukewarm ocean, six rooms, mechanics include a tide canal, a three-lever sluice, `trial` mechanics that
today count as arena wins, a boss (`tide_warden`) whose death ends the run after `completion.delaySeconds` (45), chests of kind `loot`
(table `ascensiondungeons:chests/flooded_ruins`, Lootr when installed).

Proposed, not yet checked against play:
- **Checkpoints:** each completed `trial`, and opening the Archive (Court) lock.
- **Outbreak effects:** the existing `encounters` and the Pokémon pools (`wild_low` and up) scale by `spawnScale`; elites replace some spawns;
  the zone tier rises by one per two stages (`zoneTier`).
- **Curses:** an offering-style altar that adds penalty ticks in exchange for a larger haul entry.
- **Extraction portal:** in the Reservoir Grotto entry room, charged by kills anywhere, so it is reachable from the start.
- **Timer chests and strongboxes:** a chest marker with an unlock stage; a strongbox that spawns a wave when opened.

## Open questions and spikes (do these before wiring)

1. **Chest loot is physical: spiked 2026-11-15, the tagging half works.** See "Lootr spike" below. Still open: stripping unbanked
   items on a failed exit, clearing the tag on bank or extraction, the no-Lootr fallback, and the smuggling routes listed there.
2. **Mid-run tier change.** The contract says `openZone` can change a zone's tier; confirm in a live run that wild ratings follow, and that
   rarity stops improving at tier 4 (so spawn pressure, not rarity, carries stages 4 and up).
3. **Per-player arena wins.** If banked wins differ per player, `settleDungeonRun` needs one call per distinct value, or a new method that
   takes per-player wins (add a method; never change the existing one).
4. **Dependence on the reconstructed `run` package.** `RunManager`, `RunHost` and friends were rebuilt from call sites, not recovered.
   Reconcile with the original before wiring anything into the run lifecycle.
5. **No client mod:** show the Outbreak stage and portal charge with a boss bar or sidebar; no resource pack.

## Build order

1. (done) pure core and tests.
2. Spike open question 1, then 2.
3. Wire one dungeon: `OutbreakClock` + announcements in `AuthoredDirector`, then `Haul` at the existing settlement point, then the portal.
4. A bot test (the headless pattern from the earlier session) for enter, outbreak stages, extraction and a failed exit.
5. A real-client playtest, which nothing here replaces.

## Lootr spike (2026-11-15): run loot can be tagged at the source

**Result: proven on a live dev server.** A bot player opened two Lootr chests placed with `/dungeon chest`. The one in the overworld gave
8 untagged items. The one inside a `water` run gave 7 items, every one carrying `custom_data {ascension_run: "<run id>"}`, and the server
logged `[run-loot] tagged 7 stack(s) for RunBot in run <id>`. Run without Cobblemon (the bot library cannot parse its packets), with the
vanilla `simple_dungeon` table, so the dungeon's own tables and a real client are still untested.

How it works (read from Lootr 1.11.38.127 with `javap`, signatures and bytecode, not decompiled source):
- `ILootrFilter.mutate(ObjectArrayList<ItemStack>, LootFiller.LootFillerState, LootContext, RandomSource)` is called for every registered
  filter from Lootr's `MixinLootTable`, injected into `LootTable.fill` just before `shuffleAndSplitItems`. Returning `true` stops later filters.
- It runs for **every** `fill`, and the state is null unless Lootr is doing a per-player roll, so the filter must null-check.
- The state gives `player()` and `provider().getInfoDimension()` / `getInfoPos()`: enough to find the run.
- Filters are discovered with `ServiceLoader` (`LootrServiceRegistry`), so registration is a
  `META-INF/services/noobanidus.mods.lootr.common.api.filter.ILootrFilterProvider` file. The class is only loaded when Lootr is, so Lootr stays optional.

Built (off unless `-Dascensiondungeons.runLootTag=true`, because nothing clears the tag yet):
- `mc/RunLoot`: `tag`, `runOf(stack)`, `clear`, and a dimension-to-run lookup set while the server runs.
- `mc/lootr/RunLootFilters`: the filter, with a fault barrier so a bug can never break chest loot.
- `FantasyRunHost.runOfDimension`.

Consequences to design around:
- A tagged stack does not merge with an untagged one, or with one from another run. Banking and extraction must `clear` the tag.
- **Smuggling routes** a strip-on-failure must cover or accept: a tagged item held by a Pokémon, inside a bundle or shulker box, dropped
  for a teammate who extracts, crafted or smelted into something untagged, or eaten/used. Simplest rule: a failed exit removes tagged
  stacks from the inventory, armour, offhand and party held items; anything consumed or converted is the player's to keep.
- **Without Lootr** the dungeon places plain shared chests and this hook does not exist. Either require Lootr for Outbreak dives or add a
  second tagging point (to be checked against Fabric API 0.116.6: whether a loot-drops event exists there).
- Mob drops and Pokémon captures are not chest loot and are untouched by this.
