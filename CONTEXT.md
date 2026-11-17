# Ascension Dungeons: domain language

Timed dungeon runs for a Cobblemon server, each in its own temporary level. Server-side only: players need no client mod of ours.
Terms shared with the sibling mods keep the sibling's meaning; see AscensionLib's and CobbleRaids' own `CONTEXT.md`.

## Runs

**Run**: One party's visit to one dungeon, in its own level, from request to deletion. Owned by `RunManager`.
_Avoid_: instance, session (a session is the mechanics state of one authored run).

**Authored dungeon**: A hand-designed dungeon: a schematic plus a `layout.json` declaring rooms, mechanics, hazards, encounters and bosses.

**Type dungeon**: An authored dungeon named after a Pokémon type (`water`, `ghost`, ...). These are the current dungeons, and every one is an Outbreak dive. Seventeen exist; Fire does not yet.
_Avoid_: using it for the district generator's "type pilot".

**Outdated dungeon**: Any dungeon not named after a Pokémon type: Cinderforge, the Halloween graveyard, the biome districts and the type pilot. Left as they are; nothing new is built on them.

**Mechanic**: A declared puzzle or gate in an authored dungeon: a lever sequence, a lock, a trial.

**Trial**: A mechanic where the party stands on a plate and fights waves. Not AscensionLib's Trial (a Towers run); say "dungeon trial" when both are in play.

## Outbreak dives

**Outbreak dive**: A type dungeon run with the push-or-extract loop: an Outbreak, a haul, checkpoints, the Depths and an extraction portal. A dungeon is one when it has an `outbreak.json`.
_Avoid_: extraction mode, roguelike mode.

**Outbreak**: How worked-up the dungeon has become, rising with time spent and with curses taken. It has a **stage** and never falls. Named for Pokémon's Mass Outbreak.
_Avoid_: threat, heat, difficulty timer.

**Stage**: The Outbreak's level, 0 to 5: Calm, Restless, Agitated, Swarming, Rampage, Mass Outbreak.
_Avoid_: tier (a tier is AscensionLib's zone tier), level.

**Threat**: AscensionLib's band chosen at the door (`threat_1..7`). Not the Outbreak.

**Zone tier**: AscensionLib's rating tier for the run level (1 to 10). A dive raises it as the stage rises.

**Haul**: What a player has earned in a dive and not yet been paid: wins, chests opened, the boss. Part of it may be banked.
_Avoid_: loot (loot is items), rewards (rewards are what AscensionLib pays).

**Win**: One unit of haul that AscensionLib pays as an arena win: a stage survived, a trial, a curse altar.

**Bank**: To secure a player's haul so that no way of leaving can lose it. Banking also clears the run mark from the items they carry.

**Checkpoint**: A moment that banks everyone's haul: a named mechanic finished, or the boss defeated.
_Avoid_: save point, respawn point.

**Extraction**: Leaving through the portal. It always costs something: beat the gatekeeper and keep everything, or pay the toll.
_Avoid_: escape, cash out (Towers' word for its own vote).

**Extraction portal**: The exit near the entrance, always there. Stepping on it opens the extraction window.

**Extraction window**: The chest-style screen at the portal. It shows exactly the items the toll would take, and offers paying it, challenging the gatekeeper, or staying.

**Toll**: The price of leaving without beating the gatekeeper: 35% of a player's unbanked loot (counted in items, by kind) and 35% of their unbanked wins. Loot hidden in a bundle, a box, the ender chest or on a Pokémon is forfeit in full.
_Avoid_: tax, fee.

**Gatekeeper**: The boss a player calls out at the portal, drawn from the dungeon's `boss` pool. It grows with the Outbreak stage and with every gatekeeper already beaten. When it falls the portal opens free for everyone for a short window.
_Avoid_: boss (the dungeon's own final boss is "the boss"), guardian (a strongbox has guardians).

**The Depths**: The procedural maze under a dive, different every run, built from the dungeon's own blocks and joined to the entrance room by a tunnel and a stairway. Most of a run's chests are there.
_Avoid_: basement, lower level, procedural wing.

**Tunnel**: The opening cut through a wall of the entrance room that leads to the stairway. In the same place every run.

**Band**: How deep a room of the Depths is: Outer, Middle or Deep, by how many rooms lie between it and the doorway. Each dungeon names its own. Deeper bands hold more and better chests and fiercer enemies.
_Avoid_: level, floor, tier (a tier is AscensionLib's zone tier).

**Set piece**: A placed encounter in the Depths: an Outbreak Nest, an Alpha, or a hazard room.
_Avoid_: event, encounter (an authored dungeon's encounters are its own).

**Outbreak Nest**: A set piece that wakes when a player comes near, sends waves until cleared, and opens its sealed Nest Cache.
_Avoid_: spawner (nothing spawns from the block itself).

**Alpha**: A champion set piece guarding a sealed Alpha's Hoard. Not the gatekeeper and not an elite.
_Avoid_: boss, champion, mini-boss.

**Hazard room**: A set piece where the room itself casts the dungeon's spells at players inside.

**Stairway**: The stairs from the tunnel down to the Depths' doorway. Players and enemies walk it; nothing teleports.
_Avoid_: using it for the teleporting pads of **detached Depths**, the fallback when a dungeon has no safe wall to cut.

**Knock-out**: What a lethal hit does in a dive: the player is healed, loses their unbanked haul and is sent home. Nobody dies in a dive. A player's first is a **softer knock-out**.
_Avoid_: death, wipe.

**Run mark**: The custom-data tag (`ascension_run`) on an item looted in a dive that has not been banked. A failed exit removes marked items.
_Avoid_: soulbound, bound (AscensionLib uses "bound" for untradable wallet materials).

## Getting in

**Picker**: The window `/dive` opens: the type dungeons, gentlest first, each with what it asks and what this player is not ready for.
_Avoid_: lobby (CobbleTowers' word), menu.

**Check at the door**: What the picker says a player lacks for a dungeon: a team, a strong enough team, room for loot, experience. It warns; it never refuses.
_Avoid_: requirement, gate, lock.

**Party**: The players who go into a dive together: a leader and the guests who accepted their invitation. Also Cobblemon's word for a player's six Pokémon; say "team" for that here.

**Recap**: What a player is told as a dive ends: how it ended, what they kept and lost, and a tip.
_Avoid_: summary, results screen.

## The tutorial

**Tutorial**: What a player who has never extracted is taught during their dives: lessons and the Guide bar. Per player.
_Avoid_: onboarding in player-facing text.

**Lesson**: One thing the tutorial explains, told once per player the first time it happens to them.
_Avoid_: tip for the opening ones, hint.

**Guide bar**: The pink bar that shows a new player's next goal, which way it is and how far.

**Goal**: What the Guide bar asks for next: open a chest, go down, loot in the Depths, get out.
_Avoid_: quest, objective, mission.

**Graduate**: A player who has left through the portal at least once. They have no Guide bar.
_Avoid_: veteran in code (it says nothing about how).

**Free first extraction**: A player's first extraction with unbanked loot costs nothing. One per player, never given back.
_Avoid_: grace, mercy, free dive.

**Softer knock-out**: A player's first knock-out that would cost them something: they keep half of what was unbanked. One per player, never given back.
_Avoid_: mercy, second chance, forgiven knock-out (half is still lost).

**Dive guide**: The window `/dungeon guide` opens: every lesson as an item to read.

## Enemies

**Difficulty**: How hard a dive fights: Casual, Normal, Hard or Nightmare. It changes enemy tactics, not damage or rewards.
_Avoid_: threat (AscensionLib's band), tier.

**Tactics**: How enemies fight once aggro says they will: spacing, taking turns, flanking, choosing a target, falling back, boss phases.

**Pressing**: An enemy of a wave that is closing in on its target; the others hold a ring and wait their turn.

**Charge**: A boss's rush along a line it first shows on the ground.

**Enemy**: A hostile creature the dive spawned. With Cobblemon it is a Pokémon from the dungeon's pools, fighting in real time, uncatchable and not battleable turn-based.
_Avoid_: mob in player-facing text, wild Pokémon (a wild Pokémon can be caught).

**Pool**: A named list of species and level ranges in a dungeon's `layout.json` (`wild_low`, `wild_mid`, `trial`, `boss`). The dungeon's type decides its pools.

**Wave**: A group of enemies the Outbreak sends at the party.

**Elite**: An enemy with more health, more damage and a spell. More common at higher stages.
_Avoid_: boss, champion.

**Aggro style**: How an enemy picks a fight: Hunter, Sentry, Guardian, Ambusher, Skirmisher or Pack.

**Spell**: A telegraphed attack: a warning ring, then the hit. Shown to players under a move's name.
_Avoid_: ability (a Pokémon ability, and AscensionLib avoids it too), skill.

## Features

**Feature**: Something a dive places for one run from its seed: a chest, an altar, the portal.

**Rarity chest**: A loot chest of a rarity: Common, Uncommon, Rare, Epic. Colours follow AscensionLib's rarity palette. Not an Ascension rarity of a Pokémon.

**Outbreak chest**: A chest whose rarity grows with the stage until it is opened.

**Timer chest**: A chest sealed until a stage is reached, then free to open.

**Strongbox**: A high-rarity chest that sends guardians when opened.

**Curse altar**: Touch it to push the Outbreak ahead in exchange for a win.

**Chest model**: The resource-pack model shown over a chest; one per rarity and kind. Optional: a chest is always identifiable without it.
