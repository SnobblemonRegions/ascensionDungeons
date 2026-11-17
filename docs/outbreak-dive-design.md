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

## What a player meets (`/dive`, or `/dungeon type <type>` for an operator)

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
- **The Depths.** A tunnel cut through a wall of the entrance room becomes a stairway of half-slab steps (no jumping) that descends
  eight blocks into a procedural maze. Tunnel, stairway and the maze's passages are five wide and at least six high, so the
  largest Pokémon in the pools fit. The maze is about 37 rooms on a 7 x 7 grid, joined with loops, in eight shapes (entrance hall, hall, pillars, cross, ring, aisles, dais,
  vault; dead ends are usually vaults), built from the dungeon's own blocks and different every run.
  `docs/outbreak-depths-preview.png` shows two. Most of the run's chests are down there. It is one connected place: players walk
  down and back, and so can enemies. Nothing teleports. The tunnel is in the same spot every run, signposted in the entrance room,
  and it is the only way out of the maze; the portal is upstairs.
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
| `outbreak/FeaturePlanner` | where a run's chests, altars and portal go, from the seed (and the stairway pads of detached Depths) |
| `outbreak/ChestRarity`, `ChestLook` | the rarity ladder, colours, loot tables, pack model ids |
| `outbreak/DiveSettlement` | groups players into the fewest `settleDungeonRun` calls |
| `outbreak/Aggro` | six styles: who an enemy attacks, when it goes home |
| `outbreak/Spell`, `Spell.Cast` | a telegraphed attack and its timing |
| `gen/depths/Depths` | the procedural maze: grid, spanning tree plus loops, room shapes, lights, standing cells, a doorway in its near wall, a reachability proof |
| `gen/depths/DepthsLink` | how the maze is joined to a hand-built dungeon: finds a wall of the entrance room where a tunnel can go straight out, builds the tunnel and stairway, turns the maze to face the dungeon, converts between the two sets of coordinates |

Thin game layer in `com.ascensiondungeons.mc`:

- `OutbreakDive`: one per run. Boss bars, waves, spells, chests and altars, the signposts at the tunnel, noticing who is down in
  the Depths, the extraction window and gatekeeper, settlement. It applies decisions from the pure classes and makes none of its own.
- `TollMenu`: the extraction window. A plain six-row chest menu (no client mod); every click is answered and nothing moves.
- `AuthoredLayout`: `outbreak()`, `link()` (once per dungeon), and per run seed `depths(seed)`, `rooms(seed)`, `features(seed)`,
  `passable(seed, x, y, z)`, `depthsPalette()`.
- `AuthoredChunkGenerator`: cuts the tunnel, builds the stairway and the Depths at the end of it, places feature blocks, and sizes
  the world border to hold all of it.
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
| Skirmisher | fights from range and keeps its distance (see Tactics) |
| Pack | ignores distant targets until it or a packmate is provoked; then the pack wakes |

Exact for Pokémon (real-time combat asks the dive); approximated for vanilla fallback mobs by steering their own AI.

Aggro decides *whether* an enemy fights. Tactics decide how.

## Tactics and difficulty

Before this, every fight ended as "walk at the nearest target and hit it". A Skirmisher's distance was computed and never used,
and a Pokémon whose moves are all special still walked into melee.

**Difficulty.** A dive fights at one of four difficulties: Casual, Normal (the default), Hard, Nightmare. It is set by
`diveDifficulty` in `config/ascensiondungeons.json` for now; a per-run choice (a difficulty screen at `/dive`) only has to pick
one. Every knob below comes from a profile (`Tactics.profile(difficulty, band, stage)`), and within a difficulty the Deep band
and a late Outbreak are sharper than the first rooms, so a first dive's opening fights stay readable even on Normal.

| Knob | Casual | Normal (Outer → Deep) | Hard | Nightmare |
|---|---|---|---|---|
| Enemies of a wave pressing one target at once | 2 | 2 → 3 | 3 | 4 |
| Waiting enemies flank from their own side | no | from the Middle band, or stage 2 | yes | yes |
| Ranged fighters' distance | 6 | 7 | 7.5 | 8 |
| Focus on whoever hurts them | low | moderate | high | highest |
| Focus on the hurt | none | none → some | strong | strongest |
| Chance a badly hurt enemy falls back once | 0 | 15% → 25% | 35% | 50% |
| Help a boss calls at two thirds | 1 | 2 → 4 | 3 → 5 | 4 → 6 |
| Boss charges | never | every 12 s (faster with the stage) | every 9 s | every 7 s |
| Charge warning on the ground | | 1.5 s | 1.3 s | 1.1 s (never under 1 s) |
| Enraged spell speed-up | none | ×1.4 | ×1.7 | ×2 |

Damage and health are not on this list on purpose: difficulty here changes how enemies *fight*, not how much they hurt, so a
harder dive asks for better play rather than a bigger team. If a difficulty screen should also scale rewards, health or the
toll, that is an economy decision for you.

**What each behaviour is.**

- **Spacing.** A Pokémon whose strongest damaging move is special keeps about 7 blocks out, and so does any Skirmisher. Too
  close, it backs off; too far, it comes to the ring; at range, it circles while its moves come ready. A Pokémon that must touch
  its target still closes in.
- **Taking turns.** A wave forms a group. Each member has its own side of the target. Only the profile's number press in at
  once; the rest hold a ring 6 blocks out, circling slowly, and step in when a presser falls or falls back. A player is never
  mobbed by a whole wave at once.
- **Flanking.** Where the profile flanks, those pressing come in from their own side rather than all down the same line.
- **Choosing a target.** Instead of always the nearest: a score of distance, how much each candidate has hurt it lately (fading
  by half every 5 seconds), and how hurt each candidate is, held for 2 to 4 seconds once chosen so enemies do not flicker.
  Sentries and Guardians keep what their post allows.
- **Falling back.** An ordinary enemy that drops below the profile's line may, once, fall back toward its packmates for four
  seconds, then come again. Elites and bosses stand.
- **Boss phases** (Alphas and gatekeepers). Below two thirds of its health a boss calls help; below a third it is enraged and its
  spell comes faster. Once it has called help it charges: it stops, a red line shows on the ground where it will run (12 blocks,
  past its target), "about to charge! Step off the line." shows on screen, then it rushes along it and hits and throws anyone
  still on it. Stepping back is not enough; stepping aside is. Casual bosses never charge.

Rules: `outbreak/Tactics` (unit tested). The game side: `OutbreakDive.moveIntent` (where an enemy should be), `pickTarget`,
`hurt` (who hit whom), `bossesTick`; real-time combat asks `moveIntent` whenever a Pokémon has no move ready
(`AuthoredBridges.Movement`), and vanilla fallback mobs are steered the same way in `enemiesTick`.

## Deeper is richer: bands and set pieces

The first Depths were flat: every room as dangerous and as rewarding as the next, so going further was only a longer walk
back. Now distance is the axis of risk.

**Bands.** Each Depths room has a depth: how many rooms a party passes through to reach it from the maze's doorway, by the
shortest way. The maze is cut into thirds by depth: Outer, Middle and Deep. Each dungeon names its own (Water: the Sunken
Halls, the Drowned Galleries, the Abyssal Cistern; Ghost: the Whispering Halls, the Ossuary, the Veil). Crossing into a band
puts its name across the screen with a line on what it means, and a deeper band plays a low cave note.

| | Outer | Middle | Deep |
|---|---|---|---|
| Chest luck (rarity roll) | +0 | +1 | +3 |
| Rooms offered when chests are spread | ×1 | ×2 | ×3 |
| Extra enemies per Outbreak wave | 0 | +1 | +2 |
| Elite chance | +0 | +5% | +12% |
| Enemy level | +0 | +3 | +7 |

Over 20 seeds of all 17 dungeons that gives about 0.21 chests per Outer room, 0.44 per Middle and 0.82 per Deep, with the
mean rarity rising from 0.77 to 0.85 to 1.20 (Common is 0, Epic 3). Strongboxes, timer chests and outbreak chests are spread
the same way. The hand-built dungeon above counts as Outer.

**Set pieces.** Each run places, from its seed, in rooms of their own:

- **Two Outbreak Nests** (Middle or Deep). The nest's heart is a block of reinforced deepslate that nothing breaks. It is
  dormant until a player comes within 14 blocks, then wakes ("The Nest wakes" across the screen) and sends waves: two in the
  Middle band, three Deep, each a little bigger than the last and bigger again for each extra player. The next wave comes when
  the last is down. If everyone walks away for 30 seconds it goes back to sleep and its enemies are released; it starts over
  from the first wave. Cleared, it unseals its **Nest Cache** (Rare, Epic when Deep) and every player near it earns a win.
- **Two Alphas** (Deep, vaults first). A creature from the dungeon's stronger `trial` pool appears at its post when a player
  comes within 18 blocks: glowing, named "Alpha …", four to eight times as tough (more for each extra player and each stage),
  hitting harder, casting an elite spell, and holding its ground (Guardian aggro). It is never let go, however far the party
  goes. Its own boss bar shows to anyone within 28 blocks. Slain, it unseals its **Alpha's Hoard** (Epic) and pays a win.
- **Three hazard rooms** (Middle or Deep). While anyone is in one, the room casts one of the dungeon's own spells at a player
  inside, with the usual warning ring, every eight seconds, quicker as the Outbreak rises (never quicker than two). Each holds
  a chest rolled with extra luck.

A sealed chest says so when clicked and on its label, and the Guide bar never points at one. The counts are
`setPieces: {nests, alphas, hazards}` in a dungeon's `outbreak.json` (defaults 2, 2, 3). New lessons explain nests, Alphas,
hazard rooms and the bands the first time a player meets them, and the recap names the deepest band reached and how many nests
and Alphas the player helped beat. Rules: `outbreak/DepthBands`, `outbreak/SetPieces` (with the nest's state machine), the
band-weighted `FeaturePlanner`, and room depth in `gen/depths/Depths`; all unit tested. `OutbreakDive` runs them.

**Stage titles.** Each time the Outbreak rises, "Stage 3: Swarming" crosses everyone's screen in the stage's colour.

## How the Depths are joined

`DepthsLink.find` runs once per dungeon (not per run, so the way down never moves). From the entry cell and every standing cell
of the entrance room it walks straight to each of the four walls and asks whether a tunnel can go from there to the edge of the
schematic. `findWidest` asks for five wide and six high first, then five high, four, three, and only then three wide, and takes
the first size that has a safe place. A tunnel is refused if it or the ring of blocks around it would:

- touch air that is not the entrance room's own (another room or a passage);
- run beside water, lava, or anything that falls (sand, gravel);
- cut a chest, lever, door or any other mechanism, a seal, or a cell a hazard or mechanic uses;
- need more than 24 blocks of wall.

Where the room's wall slopes or bulges (most of the entrance rooms are domes), the first six blocks may run beside the room's
own air. Among the tunnels that are allowed, the shortest wins, with a penalty for starting on a ledge above the floor the party
lands on or within six blocks of where it lands. Outside the schematic the stairway builds its own shell from the Depths'
blocks: as wide as the tunnel and seven high (five high for a three-wide one), level for two blocks, then one block down every two, eight down in all over 28 blocks, with a
light every four. Each drop is split by a slab, so the walk is half a block at a time and nobody has to jump, up or down. The
slab is the first that exists of: `depths.step` in `outbreak.json`, the slab forms of the four Depths blocks, the slabs the
dungeon itself uses most, and smooth stone. The maze is turned so that its doorway faces the stairway. An accent frame marks the tunnel in the entrance room, and a floating label
names it.

All seventeen type dungeons get the full size this way, five wide and six high: five to ten blocks long, out of the north or
west wall of the entrance room.
If a future dungeon had no safe place, `find` falls back to **detached** Depths with a teleporting stairway (two pads), which is
how the first version worked; `"link": "pad"` under `depths` in `outbreak.json` forces that.

Enemies need nothing special: waves spawn in whichever room the party is in, Depths rooms included, and the tunnel and stairway
are ordinary walkable ground for them. An enemy sees 26 blocks, so one left in the entrance room follows a player down only
while it can still see them.

**Why five by six.** Of the 327 species in the dungeons' pools, the widest hitbox is 2.7 blocks (Mamoswine, Salamence) and the
tallest 5.1 (Golurk; Tropius is 5.0, twelve more are between 3 and 4). The first tunnel was three wide and three high where it
left the entrance room, which shut out fourteen species by height and left no room to turn for the fourteen over two blocks
wide. The maze's own passages were three by four and had the same problem, so they were widened too, and each room shape now
keeps a five-wide lane clear: pillars stand off the middle lanes, the ring room's walkway is five wide, the aisle room's walls
have five-wide gaps. The stairway is a block higher than the passages because a long body coming down still rests on the tread
behind it while its head passes under the roof of the tread in front. `"passage": 3` under `depths` in `outbreak.json` gives
the old narrow maze and tunnel back.

## The first-dive tutorial

A player who has never dived is taught by doing, not by a wall of text at the door. Nothing needs a client mod: lessons are
chat lines with a chime, the big moments also put a title on the screen, the goals are a boss bar, and the notes are a chest
window.

- **Lessons.** Twenty-three, each told once per player, the first time the thing happens to them: the welcome, the Outbreak,
  enemies, spells, chests, unbanked loot, checkpoints, a stage rising, the tunnel, the Depths, each special chest, the altar, low
  health, time running out, the portal (once they carry something they could lose), the gatekeeper, a knock-out, and leaving.
  They are paced: eight seconds apart, two for something happening right now (a spell ring, low health). A lesson about a place
  the player has walked away from is dropped and told at the next one.
- **The Guide bar.** A pink boss bar, only for a player who has not yet extracted: *Open a loot chest*, *Take the tunnel down
  to the Depths*, *Loot a chest in the Depths*, *Get back to the extraction portal and leave*, each with an arrow relative to
  the way the player faces, the distance, and "above you" or "below you". It points at a chest that player has not opened, and
  from the Depths it points to the doorway first, then the tunnel's mouth, then the portal.
- **The first extraction is free.** The first time a player leaves through the portal carrying unbanked loot, the toll takes
  nothing: no items, no wins, no hidden loot. The window still shows exactly what the toll would have taken, so they learn the
  price without paying it, and the button reads "Leave free: your first extraction". It is one per player, for good:
  `/dungeon tutorial reset` teaches again from the beginning but does not give it back, and it does not depend on the tutorial
  being on. Leaving with nothing unbanked does not use it. `"firstExtractionFree": false` under
  `portal` in a dungeon's `outbreak.json` turns it off there.
- **The first knock-out is softer.** The first time a knock-out would cost a player something, they keep half of the loot and
  wins they had not banked, as well as everything banked. The half is split by kind the way the toll splits it, and every
  rounding goes the player's way: of seven items four stay, and a single item or a single win is kept. What stays is theirs at
  once (unmarked, the wins paid). Loot tucked into a bundle, a box, the ender chest or onto a Pokémon is lost in full, as with
  the toll, so hiding never pays. Like the free extraction it is one per player for good, is not given back by
  `/dungeon tutorial reset`, does not depend on the tutorial being on, and is not used up by a knock-out that costs nothing.
  The low-health lesson says it is still to come, the gatekeeper's button in the extraction window says so too, and the
  knock-out's own lesson and recap say what happened and that the next one takes everything. `"firstKeepsHalf": false` under
  `knockOut` in a dungeon's `outbreak.json` turns it off there.
- **Graduating.** Leaving through the portal once with something at stake, free, by the toll or past the gatekeeper, finishes the tutorial. A knock-out does
  not: the next dive keeps the Guide bar but repeats nothing already told. A graduate gets the short briefing at the door and is
  still told about anything they have never met (a strongbox, say).
- **Per player.** Everything is sent to one player, so a veteran in the same party is not lectured.
- **`/dungeon guide`** opens the notes for anyone, anywhere: one item per lesson to hover over, marked met or not yet met.
  **`/dungeon tutorial`** shows a player's own state; `off`, `on` and `reset` change it.
- **Kept** in the world's saved data (`data/ascensiondungeons_onboarding.dat` in the overworld), written with the world.

`outbreak/Onboarding` holds the rules (pure, unit tested): the words, the pacing, the goals, the arrow. `mc/DiveCoach` delivers
them inside one dive, `mc/OnboardingStore` keeps them, `mc/GuideMenu` is the window, and `OutbreakDive` tells the coach what
each player is near or going through.

## Getting in

Before this there was no way in for a player: `/dungeon type` is for operators, and `/dungeon start` opens the outdated
generator. Now:

- **`/dive`** opens the picker, a chest window (no client mod): the type dungeons, gentlest first (by tier, then gatekeeper
  level, then name). Each shows its type and tier, the levels of its ordinary enemies and of its gatekeepers, and its length.
- **The check at the door** is written on every dungeon in the picker, for this player: no Pokémon with them, every Pokémon
  fainted, the strongest below the level the dungeon's enemies start at, fewer than six free inventory slots. It names what
  would spoil the dive; it never refuses. A dungeon with something to say warns in chat on the first click and goes in on the
  second. One with nothing to say goes in on the first.
- **A first dive is steered, not locked.** For a player who has never extracted, the gentlest dungeon (Normal, tier 1) is marked
  "Recommended for your first dive", and any higher tier counts as something to warn about.
- **Parties are by invitation.** `/dive invite <player>` asks; the guest joins with `/dive accept` (or the button in chat)
  within 60 seconds; `/dive leave` leaves, and a leader leaving ends the party; `/dive party` lists it. The leader picks the
  dungeon and everyone goes in together, up to `maxPartySize`. Each guest's own concerns are shown to the leader by name.
  Nobody is taken into a dungeon they did not accept, and logging off leaves the party.
- **Config** (`config/ascensiondungeons.json`): `playerDives: false` makes `/dive` operator-only, for a server that opens it
  from an NPC; `partyInviteSeconds` sets how long an invitation stands.

`outbreak/Readiness` and `outbreak/PartyBook` hold the rules (pure, unit tested); `mc/DiveEntry` and `mc/DiveMenu` show and
apply them; a dungeon's card comes from its own `layout.json` and `outbreak.json` (`AuthoredLayout.card()`).

**Difficulty is the leader's choice.** The picker's bottom row has Casual, Normal, Hard and Nightmare. The leader's choice
shines, applies to their party's next dive, and is written on every dungeon in the picker; each button says how enemies fight
and which Pokémon chests can hold. Guests are told the difficulty as they go in, and the dive says it at the door. A first dive
on Hard or Nightmare is one more thing the check at the door mentions (it never refuses). Until a leader chooses, the server's
`diveDifficulty` applies.

## Pokémon in chests

A dive chest now and then holds a **capsule**: a Pokémon, carried as an item until it is safe. This is what ties dives to the
Cobblemon game.

**How often.** Per chest, for each player (Lootr rolls a chest per player, so each finds their own): Common 0.3%, Uncommon
0.6%, Rare 1.2%, Epic 2.5%, Nest Cache 5%, Alpha's Hoard 8%. With the depth bands putting the best chests deep, a typical dive
of about twenty chests gives one Pokémon every three to six dives.

**Which tier, by difficulty** (percent of finds):

| Tier | Casual | Normal | Hard | Nightmare | Nightmare, Deep band |
|---|---|---|---|---|---|
| Standard (the dungeon's own kinds) | 100 | 90 | 75 | 67 | 65 |
| Starter (of the type; partner picks where a type has none) | | 10 | 20 | 23 | 23 |
| Pseudo-legendary (or a signature pick) | | | 5 | 10 | 10 |
| Legendary | | | | | 1.8 |
| Mythical | | | | | 0.2 |

Legendary and mythical Pokémon come only from the Deep band on Nightmare (your call): about one legendary in a few hundred
Nightmare dives that reach the Deep, and a mythical about a tenth as often. A tier a dungeon has no species for gives its
share to the tier below, so a find is never nothing.

**Which species.** `dungeons/<type>/finds.json`, written by `tools/outbreak/make_finds.py` from Cobblemon's own species data
(its starter, pseudo-legendary, legendary and mythical labels, implemented species only, first stages) and the dungeon's pools.
A line that belongs to a higher tier is never a standard find (no Dratini at standard odds in Dragon). Restricted box
legendaries (Mewtwo, Kyogre and the like) are left out. Types without a starter or a pseudo-legendary get hand-picked partners
and signatures (Water: Lapras and Feebas; Ice: Lapras). Several types have no implemented legendary or mythical in this
Cobblemon version; as Cobblemon adds them, re-running the tool picks them up.

**How good.** On release, AscensionLib grades the Pokémon as a wild one of the dive's zone tier would be (the tier rises with
the Outbreak stage), but never below the difficulty's floor: Casual Common, Normal Uncommon, Hard Rare, Nightmare Epic. This uses
a new AscensionLib grant, `grantWithRarityFloorAtTier`; an older AscensionLib falls back to the floor alone; without
AscensionLib the Pokémon is Cobblemon's own.

**The rules of a capsule.**

- It is dive loot: marked with the run until banked or carried out, so a knock-out costs it and the toll can take it (your call:
  the extraction window shows it, and fighting the gatekeeper keeps it).
- It is bound to its finder (your call): only they can release it; it can be handed over, but not opened by anyone else.
- It is used, not thrown: right-clicking releases the Pokémon to the party, or the PC when the party is full.
- A pseudo-legendary find is announced to the party; a legendary or mythical to the whole server.
- The recap lists each Pokémon found and whether it was kept or lost.

**How a capsule looks** (design page: the "Capsule Design" artifact).

- **Two kinds of rare, never mixed up.** The find tier (what kind of Pokémon) has stars and its own name colours: Standard
  white, Starter green, Pseudo-legendary orange, Legendary a pale champagne gradient, Mythical a pastel gradient, the top two
  bold and coloured letter by letter. None of them is near an AscensionLib rarity colour (a test checks it). The Ascension
  rarity only appears in AscensionLib's colours: a row of six diamonds filled up to the difficulty's floor ("Rare or better").
- **Sealed while at risk.** Cobblemon's ball for the tier (Poké, Great, Ultra, Master, Cherish) with the glint and custom model
  data 9201-9205. With the resource pack it is a lab tank of tier-coloured fluid with only a dark shape inside: our own art,
  drawn by `tools/outbreak/make_capsule_art.py`, no Pokémon depicted. Without the pack it is the ball.
- **Clear once safe.** Banking or carrying it out turns it into Cobblemon's `pokemon_model` item, lightly tinted by tier, which
  each player's own Cobblemon draws as the real Pokémon. This mod ships no Pokémon art: the server sells ranks, and Cobblemon's
  models are licensed non-commercial (owner decision, 2026-11-17). Without Cobblemon the capsule keeps its item.
- **Tooltip**: name and level; stars, tier and "Pokémon Capsule"; types in Cobblemon's colours; the rarity meter and "Graded when
  released."; where it was found (band, dungeon, difficulty and Outbreak stage); then **⚠ At risk** while sealed or **▶ Right-click
  to release** once clear; "Bound to <finder> · found <date>". The same for every viewer: a server cannot tailor item text.
- **All dive loot shows it is at risk.** Every looted item gets two tooltip lines ("⚠ Dive loot · at risk", "Bank it at a checkpoint
  or carry it out.") while it is marked; banking takes them off again, so banked loot stacks with ordinary items.
- **Testing:** `/dungeon capsule give <species> [tier] [difficulty]` (operators) gives a sealed test capsule, at risk;
  `/dungeon capsule bank` banks it so it clears. Like any dive loot, one still at risk is lost at the next login.
- **Still to do:** opening a capsule should play AscensionLib's reveal card (needs an AscensionLib addition that reports the
  rarity; its card shows Cobblemon Cards pictures, whose licence also needs checking for a commercial server).

Rules: `outbreak/PokemonFinds` (unit tested, including the per-dive odds) and `outbreak/CapsuleLook` (unit tested). Game side: `RunLoot.ExtraLoot` (Lootr fills the chest
with the capsule in it), `OutbreakDive.findsFor`, `mc/Capsules` (the item and its use), `mc/CapsuleRelease` (Cobblemon and
AscensionLib).

## When a dive ends: the recap

Every player is told, in chat, as they leave: how it ended (and what knocked them out, when something did), the Outbreak
stage and how long they were inside, how many chests they opened and whether they reached the Depths, the looted items and
wins they kept and lost, and one tip chosen from what actually happened: a spell knock-out is told about the ring, a
knock-out with nothing banked about checkpoints, an early toll that the gatekeeper was at its weakest, a clean exit that
never went down about the Depths. `outbreak/Recap` holds the wording and the rules for the tip (pure, unit tested).

## The way out, for everyone

The portal's boss bar is each player's own. It carries the same news for all (idle, the gatekeeper's health, open and for
how long) and, for that player, an arrow, a distance and "above you" or "below you" to the way out from where they stand:
the portal when they are upstairs; from the Depths, the maze's doorway first, then the tunnel's mouth. It stays after the
tutorial is finished, so nobody loses a haul to being lost in the maze.

## Chest models

`tools/outbreak/make_chest_pack.py resourcepack [preview.png]` draws eleven original models and writes `resourcepack/`;
`docs/outbreak-chests-preview.png` is its preview. In the world a model is an `item_display` of `minecraft:popped_chorus_fruit`
with a custom-model number, over the real Lootr chest. **Off by default** (`features.models`): a player without the pack would
see a floating chorus fruit. A chest is always identifiable without it (label, sparkles, floor block).

The same `resourcepack/` holds the sealed capsule tanks (`tools/outbreak/make_capsule_art.py resourcepack`). They need no switch:
without the pack a sealed capsule is simply Cobblemon's ball. A server sends one pack, so merge `resourcepack/` with
AscensionLib's pack before hosting it.

## Verification

| What | How | Result |
|---|---|---|
| Rules and data | `./gradlew test` | 287 tests pass. Includes Pokémon finds (tier odds per difficulty, legendaries only on Nightmare in the Deep band, about 1.8% and 0.2% of finds there, a missing tier giving way to the one below, per-dive odds) and every dungeon's find lists. Includes the tactics: every difficulty at least as hard as the one before at every knob, Normal plainer in the Outer rooms than the Deep, spacing, a wave taking turns, target choice with threat, hurt and a lock, threat fading, falling back once, boss phases only going forward, and the charge hitting only what is on its line. Includes the depth bands (thirds by depth, each step richer and fiercer, chests rarer over 20,000 rolls), set pieces (one per room, rewards away from the heart, alphas deep and in vaults, a nest's waves, sleep and clearing), and, over 20 seeds of all 17 dungeons, two nests, two Alphas in the Deep band and three hazard rooms each, with chests per room and mean rarity rising band by band. Includes 300 seeds of Depths (every standing cell reachable, sealed shell, loops, lighting); a body three blocks square and six tall reaching every room of the maze over 60 seeds; the link (never breaks into another room, never beside water or sand, never through a mechanism, sloped walls, half steps, the widest size the wall allows); the check at the door (each concern, their order, steering a first dive without locking it), the party book (nobody joins without accepting, invitations run out, one party at a time, a leader leaving ends it), the recap (every way out, the tip for each situation); the tutorial's rules (each lesson once, pacing, urgent lessons first, goals, graduation, the free first extraction and the softer first knock-out and that starting over restores neither, the softened split (half, rounded the player's way), off and reset, the arrow); and, for all 17 type dungeons: valid settings, full feature placement on sound ground for 40 seeds each, a five-by-six tunnel found, a walk from the entry cell through the tunnel to the maze's first room, to its farthest room and back, and the big body making the same trip |
| Every type dungeon, whole mod set | `DUNGEONSPIKE_DIVE=all ./gradlew runServer` (`spike/DiveE2E`): a stand-in player joins through the normal login path and plays a short dive in each (its free first extraction set aside, so the toll is checked in all seventeen) | 155/155. In each dungeon the Depths hold two nests with their hearts built in the world, two Alphas in the Deep band and three hazard rooms. In each dungeon a wave of that type's Pokémon spawns; a walk is found through the blocks that are really in the world from where the player lands, through the tunnel, down eight half-slab steps and into the Depths, and the player is moved along it; the dive notices them arrive; a chest down there gives marked loot; the walk back up to the entrance room exists and is followed; the toll is shown and paying it lets the player out |
| One long dive, whole mod set | `DUNGEONSPIKE_DIVE=1 ./gradlew runServer` (water) | 88/88. **Difficulty and finds:** the picker showed four difficulty buttons with Normal chosen, choosing Hard wrote "Difficulty: Hard" on every dungeon and added the first-dive warning; a run requested on Hard said so at the door; a chest held a Capsule: Lapras (an Ultra Ball, pseudo-legendary tier, floor Rare, zone tier 3), marked as run loot and bound to its finder, and the party heard of it; it would not open while at risk; once banked it released Lapras into the party (2 to 3 Pokémon); another player could not release a capsule bound to someone else. **Tactics:** a Skirmisher Pokémon put two blocks from the player backed off and held 4.5 to 11.5 blocks in 11 of 12 samples; a wave of six never had more than one in reach at once with the rest holding the ring; an Alpha worn to 60% called four for help, at 30% was enraged, wound up a charge and hit the player on its line; an Alpha removed from the world was replaced by a new one. (The water pools are mostly special attackers, so this wave mostly kept range; pressing and flanking by melee enemies is covered by unit tests, not yet seen live.) **Depths:** crossing into the Deep band put "The Abyssal Cistern" on screen; the Alpha's hoard refused to open, an Alpha Seaking (a Cobblemon Pokémon, eight times as tough) took its post as the player came near, and slaying it opened an Epic hoard; a nest woke, sent three waves of five, six and seven, and its cache opened on clearing; a hazard room cast a spell at the player inside; the recap read "Deepest: The Abyssal Cistern. Nests and Alphas beaten: 2". **Softer first knock-out:** with 18 banked and 8 unbanked items the player came out with 22 (four of the eight kept, unmarked), lost the item hidden on a Pokémon, and was told it was their first; a later knock-out took all 7 unbanked items and said so. **Getting in:** `/dive` lists the 17 dungeons gentlest first with Normal recommended; Water warns about its tier and the missing team, the first click only warns and the second goes in; after a dive with two level-5 Pokémon the picker says the team is under-levelled. **Recap:** after a knock-out, the free extraction, the toll (it counts exactly what the toll took) and the gatekeeper. **Way out:** the portal bar reads "↓ 4m" at the entrance and "↙ 102m" from the Depths. **Two players:** one invites, the other accepts, the guest is told the leader chooses, the leader's picker shows the guest's concerns by name, both go into the same dive, the newcomer is welcomed and given a Guide bar while the graduate gets only the briefing, each has their own portal bar, both walk out with their own recap, and the leader leaving ends the party. Tutorial: a first-time player gets the welcome in chat and as a title instead of the briefing, and a Guide bar pointing at a chest; the Outbreak and enemies are explained one at a time; the Depths and unbanked loot are explained and the Guide bar turns to getting out; the knock-out is explained and the player stays a rookie; the guide window has a page per lesson and nothing can be taken; progress survives a save and reload; the second dive has the ordinary briefing, repeats nothing and keeps the Guide bar. **Free first extraction: the window showed 7 of 19 unbanked items and a "Leave free" button, and leaving took nothing, not even the item hidden on a Pokémon; it graduated the player and was used up; starting the tutorial over did not give it back; the next extraction showed 8 of 23 and took exactly those.** Also: enemies are uncatchable, unbattleable Pokémon that hunt and hurt the player; the walk down and back; a Pokémon that spawned in the entrance room chased the player down the stairway to its foot; checkpoint; knock-out; loot hidden on a party Pokémon is banked, stripped or forfeit; beating the gatekeeper opens the portal |
| The same over the real protocol | `tools/outbreak/dive_bot.js`: a mineflayer bot with its own client physics and pathfinding, server without Cobblemon | 49/49 on the last run. Over real packets (with the depth pass in place, so every run had nests, Alphas and hazard rooms): the first knock-out left 2 of 4 unbanked items, unmarked, with its own lesson and recap, and `/dungeon tutorial` then read "First knock-out: used"; a second knock-out took everything; `/dive` opens a window titled "Choose a dungeon" with 17 dungeons, the gentlest first and recommended; clicking Water warns in chat and starts nothing; the second click goes in; the portal bar reads "Extraction portal ↓ 4m: your first extraction is free"; every exit is followed by its recap in chat, and the toll's recap counts exactly what was taken; the welcome arrives as chat and as a screen title with its subtitle; the Guide bar arrives as a boss bar reading "Open a loot chest" with an arrow and a distance, and later "Get back to the extraction portal and leave"; lessons were told as their subjects were met, none twice; `/dungeon guide` opens a window with a page per lesson and nothing can be taken. **The first extraction's window is titled "your first is free", shows what the toll would take, and leaving takes nothing; `/dungeon tutorial` then reads "First extraction: used"; the second extraction's window asks for the toll and takes exactly what it showed; starting the tutorial over does not give the free one back.** The bot **walked** from the tunnel's mouth down the stairway into the maze's first room (46 blocks, 8 down) and back up, first try each way, and the server did not correct its position once. Marking, banking, altar, timer chest, outbreak chest, strongbox, knock-out, walk-out, the login sweep and the gatekeeper all pass |
| Chest pack | `ChestLookTest` | ids in the pack match the code; every model and texture exists |

Use `-Dascensiondungeons.outbreakSpeed=12` for the live checks or they take many minutes.

The bot runs the Outbreak at twelve times speed, which makes it a harsher place than the game: by the time it walks to the
Depths it is three or four stages up, with a dozen enemies casting. Before the bot was hardened it missed one of its two walk checks
in about half its runs (36 of 37); since, it has scored 45 of 46 and then 46 of 46. What went wrong
along the way, and what was done:

- A stage rose a second after the extraction window opened, and the dive rightly showed the window again rather than take
  something other than what was shown. The bot now confirms a re-shown window, and a free extraction no longer re-shows at
  all, since nothing is taken.
- A spell threw the bot into the air as it set off down the tunnel, and its pathfinder gave up three blocks in. The walk checks
  now clear the enemies around the bot first and try again if a walk stops short.
- In one run the bot spent minutes being put back by the server, then got through on a later try. With the place recorded
  the cause was plain: it was being put back exactly where a `/tp` had just placed it. The bot library goes on sending its old
  position for a while after a teleport and the server answers each one ("moved too quickly" in the server log, hundreds of
  them, none of them in a walk). The bot now waits for that to stop before it walks. Walking the stairway alone, up and down
  on each of its five lanes (fourteen walks, normal speed, no enemies), the server did not correct the bot once.
- With the depth pass, a walk to a chest deep in the maze became a fight: at twelve times speed the bot met woken nests, hazard
  rooms and a stage-4 Outbreak, and one run timed out. The walk now proves what it is for, the tunnel and the stairway, by
  going to the maze's first room; the Depths themselves are covered by the harness.
- The pathfinder sometimes stops partway with no correction at all and is sent on again; the last run's way down took three
  goes. A walk is judged on the attempt that arrives, with no correction that moves the bot two blocks or more, and where the
  earlier goes stopped is printed.

Another thing the bot found, earlier: with full-block steps (the first version of the stairway) it stuck on the top step coming
up. Client and server agreed on every block there; the bot's physics library has a rounding fault when pressed against a block
face at coordinate -2 and walked itself into the block, which the server refused. That is the library, not the stairway, but
jumping eight times up a corridor is clumsy for anyone, so the steps became half slabs and the fault no longer arises.

**Not verified.** A party over the real protocol (the two-player check uses stand-in players inside the server; the protocol
bot is one player). More than two players. How any of it looks and feels in a real client, the tunnel and stairway included: they are checked by walking
the real blocks from code and by a protocol bot, not by eye. The same goes for the tutorial: that the words arrive is checked,
whether they read well at the pace they come is not, and the arrows in the Guide bar have not been seen in the game's font. The
portal lesson and the time-running-out lesson were not reached in a live run (their triggers are a line each; the words and
pacing are unit tested). Whether a Pokémon as large as a Golurk really paths through the five-wide tunnel: the size is checked
against Cobblemon's hitbox data and by a block model, not with the creature. A live AscensionLib (its jar needs a local
`webgui-dev.jar` and cannot be built here), so payouts are covered only by unit tests of the grouping and by log lines. The
authored boss flow inside a dive. Whether a gatekeeper is beatable but hard at each stage: its numbers are guesses and need play.
Performance with four players in the Depths at stage 5. The chest models in a client.

## Design calls made under delegated authority (overrule freely)

1. **Nobody dies in a dive.** A lethal hit is a knock-out; a real death would drop the player's own gear.
2. **What earns wins:** each Outbreak stage survived, each trial, each curse altar, the boss.
3. **The Depths are joined to the dungeon by a real tunnel and stairway** (your call: "connect the maze physically"). The schematics
   are not edited: the generator cuts the tunnel when it builds the run. How the place is chosen is described below.
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
15. **A player's first extraction is free, and their first knock-out keeps half** (your calls). Both are once per player and
    cannot be earned back. A softened knock-out keeps half rather than everything so that it still stings enough to teach
    banking, and it loses hidden loot in full so it cannot be gamed. Everything else about a first dive is the same as any
    other.
16. **Graduation is the first extraction**, not a number of dives: the loop is learned when it has been closed once.
17. **Leaving with nothing unbanked is not an extraction for the tutorial**: it neither graduates the player nor uses up the
    free one. Otherwise walking in and straight out would spend it.
18. **Finds use AscensionLib's zone tier as well as the difficulty floor** (your call), so staying longer in a dive also grades
    its Pokémon better.
19. **Restricted box legendaries are never found**, and **found Pokémon are first stages** at level 10 (starters, pseudos) or
    40 (legendary, mythical); standard finds take the dungeon's own levels.
20. **The check at the door warns and never refuses**, a high tier on a first dive included. A lock would also lock a new
    player out of a friend's party; a warning and a second click leaves the choice with them.
21. **Parties are made by invitation and acceptance**, kept in memory, and end when the leader leaves or logs off. There was
    no party system to reuse (CobbleTowers has its own lobby). If the server already groups players some other way, this is
    the piece to swap.
22. **`/dive` is open to every player by default.** `playerDives: false` restricts it.
23. **The portal is explained only once the player carries loot.** It stands in the entrance room, and explaining the toll to
    someone with nothing to lose is noise.

## Known gaps and next steps

1. **Play it**, alone and then in a party, with the real client and mod set.
2. **Fire has no type dungeon.** Cinderforge was the fire dungeon and is outdated; a `fire` authored dungeon is needed to complete the set.
3. **The Depths:** bands, nests, Alphas and hazard rooms are in (see "Deeper is richer"). Still one storey on a regular grid with
   one way in. Good next steps: a second, deeper level behind a stairway in the Deep band; larger landmark rooms spanning two
   cells; type-specific set pieces (a flooding room for Water, a collapsing one for Ground); secret rooms behind cracked walls;
   and tuning the numbers above with real players.
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
