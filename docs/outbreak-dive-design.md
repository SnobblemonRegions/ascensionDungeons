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

1. **Chest loot is physical.** Lootr hands items straight to the player, so haul-only loss needs a way to tag or intercept it. Spike against
   the decompiled Lootr jar; fallbacks: tag items with run `custom_data` and strip unbanked ones on a failed exit, or credit chests as
   wallet value instead of items.
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
