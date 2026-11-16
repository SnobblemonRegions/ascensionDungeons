# Ascension Dungeons: domain language

Timed dungeon runs for a Cobblemon server, each in its own temporary level. Server-side only: players need no client mod of ours.
Terms shared with the sibling mods keep the sibling's meaning; see AscensionLib's and CobbleRaids' own `CONTEXT.md`.

## Runs

**Run**: One party's visit to one dungeon, in its own level, from request to deletion. Owned by `RunManager`.
_Avoid_: instance, session (a session is the mechanics state of one authored run).

**Authored dungeon**: A hand-designed dungeon: a schematic plus a `layout.json` declaring rooms, mechanics, hazards, encounters and bosses. One per Pokémon type.
_Avoid_: type dungeon when the district generator's "type pilot" is meant.

**Mechanic**: A declared puzzle or gate in an authored dungeon: a lever sequence, a lock, a trial.

**Trial**: A mechanic where the party stands on a plate and fights waves. Not AscensionLib's Trial (a Towers run); say "dungeon trial" when both are in play.

## Outbreak dives

**Outbreak dive**: An authored dungeon run with the push-or-extract loop: an Outbreak, a haul, checkpoints and an extraction portal. A dungeon is one when it has an `outbreak.json`.
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

**Extraction**: Leaving through the portal, keeping the whole haul.
_Avoid_: escape, cash out (Towers' word for its own vote).

**Extraction portal**: The exit near the entrance. Kills near it charge it; full, it opens for a short window; missed, it costs more.

**Knock-out**: What a lethal hit does in a dive: the player is healed, loses their unbanked haul and is sent home. Nobody dies in a dive.
_Avoid_: death, wipe.

**Run mark**: The custom-data tag (`ascension_run`) on an item looted in a dive that has not been banked. A failed exit removes marked items.
_Avoid_: soulbound, bound (AscensionLib uses "bound" for untradable wallet materials).

## Enemies

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
