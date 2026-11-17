# Outbreak dive playtest checklist

The things only real players in a real client can tell us. Everything here already passes the automated checks (unit tests,
the headless dive harness, a scripted bot); none of it has been played by a person. Tick what works, and for anything that
doesn't, note **what you did, what you saw, and the time** so it can be found in the server log.

Plan for about two hours with 2 to 3 players. Section 1 is setup; sections 2 to 9 are in the order a new player meets them.

---

## 1. Setup

**Server mods** (Fabric 1.21.1, Fabric Loader 0.17.2 or newer, Java 21):

- [ ] ascensionDungeons, built from PR #1's branch (`claude/ascension-dungeons-state-tlk7oo`). Fantasy is bundled inside it.
- [ ] AscensionLib, built from PR #17's branch (same branch name). Without it dives still run, but nothing is graded or paid out.
- [ ] Cobblemon 1.8.1 (what the mod is built and tested against) and Fabric Language Kotlin.
- [ ] Lootr 1.11.38.127 and Cloth Config (Lootr needs it).
- [ ] Fabric API 0.116.6+1.21.1.
- [ ] CobbleRaids, if you want raid bosses in the dungeons that use them (optional).

**Client mods:** Cobblemon and Lootr (with Cloth Config), plus Fabric API. Lootr adds its own blocks, so a client without it
may not be able to join. **Note whether a client without Lootr can join**: it decides what goes in the players' pack.

**Accounts:**

- [ ] **At least one fresh player** who has never dived on this world. The free first extraction and the softer first
  knock-out happen once per player, ever. `/dungeon tutorial reset` replays the lessons, not those two. To start everyone
  over, stop the server and delete `world/data/ascensiondungeons_onboarding.dat`.
- [ ] One operator, for the shortcut commands below. Operators play as normal players too; the commands are only shortcuts.

**Each player brings** a team of six Pokémon at about the dungeon's enemy level (the picker shows it) and some free inventory
space. One test below deliberately breaks this.

**Operator shortcuts** (all need op):

| Command | What it does |
|---|---|
| `/dungeon dive` | Inside a dive: the dive's state, and where every chest, nest, Alpha and hazard room is |
| `/dungeon dive find <tier>` | Your next unopened dive chest holds a Pokémon: `standard`, `starter`, `pseudo`, `legendary` or `mythical`. Nothing appears if this dungeon has none of that tier (see section 8) |
| `/dungeon dive bank` | Banks everyone's haul now, as a checkpoint would |
| `/dungeon goto cell <x> <z>` | Teleports you to a maze cell (-4 to 4) |
| `/dungeon list`, `/dungeon end <run>` | See all runs; end a stuck one |

**Faster Outbreak (optional):** starting the server with `-Dascensiondungeons.outbreakSpeed=4` makes the Outbreak rise
four times faster (a stage every 90 seconds instead of 6 minutes), so late stages, bosses and the gatekeeper's growth can be
seen in one session. Play sections 2 to 4 at normal speed first, because pacing is part of what we're testing.

**Server log:** keep `logs/latest.log`. Lines starting `[dive]` record finds, extractions and knock-outs.

---

## 2. Getting in (fresh player)

- [ ] `/dive` opens the picker as a chest window. Dungeons are listed gentlest first, each with type, tier, enemy levels and
  length. One is marked **Recommended for your first dive**.
- [ ] The bottom row has **Casual, Normal, Hard, Nightmare**; the chosen one shines and is written on every dungeon. Each
  button explains how enemies fight and which Pokémon chests can hold. *Is that text readable and useful?*
- [ ] **Check at the door:** put all Pokémon in the PC, click a dungeon. It warns in chat on the first click and goes in on
  the second. Try also: every Pokémon fainted, a full inventory, and choosing Hard on a first dive. *Are the warnings clear?
  Too many?*
- [ ] Going in says the dungeon, its type, tier and difficulty, e.g. "(Water, tier 3, Normal)".

**Party:**

- [ ] Leader: `/dive invite <friend>`. The friend gets a chat message with a clickable accept button; clicking it (or
  `/dive accept`) joins. `/dive party` lists everyone.
- [ ] Let one invitation run out (60 seconds). Invite a player who's already in another party.
- [ ] The leader's picker shows each guest's own door warnings by name.
- [ ] The leader picks the difficulty and the dungeon; **everyone** goes in together, and guests are told the difficulty.
- [ ] A guest uses `/dive leave` before going in; then the leader leaves, and the party should end.
- [ ] Someone logs off while in a party, and should be dropped from it.

---

## 3. The first dive: tutorial and guidance (fresh player)

- [ ] A **Guide** bar at the top tells you what to do next and points the way (arrow and distance). *Does it ever point
  somewhere wrong or go stale?*
- [ ] Tips show up as you meet things for the first time: waves, elites, spell rings, chests, the toll, the Depths, nests,
  Alphas, hazard rooms. *Too chatty? Too little? Anything confusing?*
- [ ] `/dungeon guide` opens the notes as a chest window of pages; hovering a page shows its text.
- [ ] `/dungeon tutorial` shows your progress; `off` hides the bar and tips, `on` brings them back.
- [ ] The **way-out bar** (the portal's boss bar) shows an arrow, a distance and "above you"/"below you" toward the exit:
  the portal when you're upstairs; from the Depths, the maze doorway, then the tunnel. *Follow it from deep in the maze:
  does it get you out?*

---

## 4. The dungeon and the Depths

- [ ] Find the **tunnel** from the entrance room (it's signposted) and walk the stairway down. The steps are half-slabs,
  so no jumping is needed. *Any spot where you get stuck, or a Pokémon does?*
- [ ] **Width:** send out your largest Pokémon in the tunnel, the stairway and the maze passages (5 wide, 6+ high). Note
  any species that can't fit or gets stuck in a corner.
- [ ] Walk from the Outer to the Middle to the Deep band. Each crossing shows a title naming the band (for example in
  Water: "The Sunken Halls", "The Drowned Galleries", "The Abyssal Cistern"). *Does deeper **feel** harder and richer?*
- [ ] **Chests:** open a few of each kind. Each player gets their own loot from the same chest (Lootr). Note anything that
  looks odd in a chest window.
- [ ] **Curse altar:** touch one. The Outbreak jumps forward and everyone's haul gains a win.
- [ ] **Checkpoint:** finish the dungeon's first gate, trial or offering lock. Everyone's haul is banked.
- [ ] *Did anyone get lost? How long did it take to find the way back up?*

---

## 5. Enemies and difficulty

Play at least one dive on **Normal** and one on **Hard** (Nightmare if you're feeling brave). Casual is worth ten
minutes to judge whether it suits a relaxed player.

- [ ] **Ranged enemies** keep their distance and circle you rather than walking into melee.
- [ ] **Melee enemies take turns:** some close in, the rest hold a ring around you and wait. *Not verified live yet:
  please watch for this specifically.* On Casual at most two close in at once; on Nightmare up to four.
- [ ] **Flanking** (Normal deep, Hard, Nightmare): enemies closing in come from different sides. *Also not verified live.*
- [ ] Enemies turn on **whoever hurts them**, and on Hard and up they pick on the **hurt** player. *Does it feel
  fair, or like being ganged up on?*
- [ ] On Hard and up, a badly hurt enemy sometimes **falls back** to its pack, then returns.
- [ ] **Elites** glow and cast spells. Spell **rings** appear on the ground before the hit; stepping out dodges it.
- [ ] **Bosses** (the gatekeeper, Alphas): call help at two thirds of their health, cast faster below a third, and
  **charge**: a line appears on the ground, then they rush along it. Stepping aside is safe; backing away isn't. *Is the
  warning long enough to react? (Normal about 1.5 s, Nightmare about 1.1 s.)*
- [ ] *For each difficulty: too easy, right, or too hard? Where did it spike?*

---

## 6. Set pieces in the Depths

Use `/dungeon dive` to find them if needed.

- [ ] **Outbreak Nest:** a reinforced-deepslate floor. Stepping in starts waves; clearing them all opens the **Nest
  Cache** (chest on a copper floor).
- [ ] **Alpha:** a big named enemy guarding the **Alpha's Hoard** (chest on a netherite floor), sealed until it falls.
- [ ] **Hazard room:** a recurring hazard pulses through the room. *Can you tell what's happening and how to avoid it?*
- [ ] *Was each set piece worth the effort for what it guarded?*

---

## 7. Getting out

- [ ] Step on the **extraction portal**: a window shows **exactly** the items the toll would take.
- [ ] **Fresh player:** the first extraction is **free**; the window says so, and nothing is taken.
- [ ] **Pay the toll** (35%): the items shown are the items lost; banked loot is untouched.
- [ ] **Challenge the gatekeeper:** beat it, the portal opens for 30 s, and **everyone** leaves with everything. Then
  lose to one on purpose.
- [ ] **Knock-out:** go down. You keep only what was banked. A fresh player's **first** knock-out is softer and keeps part
  of the unbanked haul (the message says what). The second one isn't softened.
- [ ] Also leave by: running out of time (use the faster Outbreak), `/dungeon leave`, and logging off mid-dive.
- [ ] **Recap** in chat as each player leaves: how it ended, Outbreak stage, time, chests, deepest band reached, nests and
  Alphas beaten, loot kept and lost, Pokémon found, and one tip. *Is it useful or too long?*
- [ ] **AscensionLib payout:** each player is settled once. Note what they received and any `settleDungeonRun` errors in
  the log. *Never tested live.*

---

## 8. Pokémon in chests

Natural finds are rare (about one every three to six dives), so use `/dungeon dive find <tier>` before opening a chest
you haven't opened. Not every dungeon has every tier:

| Tier | Dungeons that have it |
|---|---|
| Standard, Starter, Pseudo-legendary | all 17 types |
| Legendary | Dragon, Electric, Flying, Ice, Normal, Psychic, Rock, Steel (Fire has no dungeon yet) |
| Mythical | Dark, Grass, Psychic |

- [ ] The chest holds a **capsule**: a Poké/Great/Ultra/Master/Cherish ball by tier, named "Capsule: <species>", with a
  description. *Does it look good in the hand and in a chest window?*
- [ ] A **pseudo-legendary** find is announced to the party; a **legendary** or **mythical** to the whole server.
- [ ] Right-clicking it **inside the dive** is refused (it's still at risk).
- [ ] Hand it to another player: they **can't** release it (it's bound to the finder).
- [ ] Bring it out, then right-click: the Pokémon joins your party, or the PC if the party is full.
- [ ] **AscensionLib grading:** check the released Pokémon's rarity. It should never be below the difficulty's floor
  (Casual Common, Normal Uncommon, Hard Rare, Nightmare Epic). *Never tested live: note what you get.*
- [ ] Lose a capsule to a knock-out, and to the toll. The recap says "lost".
- [ ] *Does finding a Pokémon feel special enough? Are the rates right?*

---

## 9. Overall

- [ ] *How long was a typical dive? Too long or too short?*
- [ ] *Was the choice "pay the toll or fight the gatekeeper" interesting?*
- [ ] *What was the best moment? The most frustrating one?*
- [ ] *Would you dive again tomorrow? What would bring you back?*
- [ ] Server performance: note TPS (`/tick query`) with 2 to 3 players in deep rooms during big waves.

**Send back:** this list with ticks and notes, `logs/latest.log`, and screenshots of anything that looked wrong.
