# Custom items in dungeon loot (datapack methods)

Everything below is plain datapack JSON under `src/main/resources/data/ascensiondungeons/loot_table/`, loaded with the mod.
No Java is needed to add or change items. `tools/gen_loot.py` bootstraps the placeholder files; once you edit by hand, treat the JSON as the source and do not re-run it.

## Structure

| Table | Role |
|---|---|
| `chests/<biome>` | What a dungeon chest in that biome rolls. 3-5 rolls over the shared tiers, plus 1-2 rolls of the biome flavour. Wired to the Lootr chests in `DungeonChunkGenerator.placeChest`. |
| `tiers/common`, `uncommon`, `rare`, `epic` | Shared pools. Edit these to change what every biome can give. |
| `biome/<biome>` | Flavour items only that biome gives. |
| `custom/*` | Server-supplied items, referenced from tiers and biome tables. |
| `raid/<biome>` | Reserved for dungeon-owned raid rewards. Nothing reads it yet. |

## Methods to get a custom item into a table (verified on 1.21.1 unless noted)

1. **Another mod's item by id.** `{"type":"minecraft:item","name":"cobblemon:rare_candy"}`. All placeholder items are Cobblemon items; ids were checked against the 1.8.1 jar.
2. **A vanilla item turned into a custom one with components.** `minecraft:set_components` on an entry. Used in `custom/dungeon_token` (paper with name, lore, `custom_model_data`, `custom_data`). Needs no client mod. A different look needs a resource pack that maps `custom_model_data`. `custom_data` is how server code recognises the item later (`stack.get(DataComponents.CUSTOM_DATA)`).
   - Gotcha found in testing: in 1.21.1 `custom_name` and `lore` take text as a JSON **string** (`"{\"text\":\"...\"}"`), not a nested object. The nested form fails to parse and silently drops the whole table.
3. **Pull in another mod's table by reference.** `{"type":"minecraft:loot_table","value":"cobbleraids:keys/starter"}`. Used in `custom/raid_key`. `fabric:load_conditions` works only at the top level of a table file, not on an entry; if the mod is missing the file is skipped and references to it roll nothing (the log shows `Unknown loot table`, harmless).
4. **Replace or extend a vanilla/mod table.** Fabric loads `data/<ns>/loot_table/...` from any datapack, and a same-id file in a world datapack overrides the mod's. Cobblemon ships a `LootInjector` class for appending pools to vanilla tables (not inspected in detail; Fabric's `LootTableEvents.MODIFY` is the standard hook for this and would let our mod add pools to tables it does not own).
5. **Custom behaviour in Java (only if JSON is not enough).** Register a loot function or condition type, for example one that resolves an item from server config or AscensionLib, then use it from JSON like any other. Not needed yet.

## Where tables are resolved

Lootr stores the table id on the chest block entity and rolls it per player when opened, so any table id works and each player gets their own roll. Run levels share the server's loot tables (they are global), so editing a table and running `/reload` (or restarting) takes effect for new chests' next opens; already-placed chests only store the id.

`lootrmon` (installed) adds a Cobblemon-styled "gilded" Lootr chest. It could be used for a higher tier chest later by changing the block in `placeChest`.

## Raid rewards (undecided)

Two ways to pay out an arena victory, and the tables are ready for either:
- **CobbleRaids pays** (`raidRewards: true` in `ascensiondungeons.json`): its own tier tables (`cobbleraids:base/*`, `general/*`, `keys/*`, `specialty/boss/*`) and reward GUI apply. Nothing to build.
- **The dungeon pays**: on VICTORY, `RaidArenas` would roll `ascensiondungeons:raid/<biome>` into each participant. A small addition, about 20 lines, once ownership is decided.

## Checking a change

Run the headless check: `DUNGEONSPIKE_E2E=1 gradlew runServer`. It rolls every biome's chest table 200 times and fails if the table did not load, rolled nothing, or never produced the custom token. Parse errors also show in the log as `Couldn't parse element minecraft:loot_table/...`.
