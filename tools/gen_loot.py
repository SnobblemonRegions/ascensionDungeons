"""Regenerates the placeholder loot tables under src/main/resources/data/ascensiondungeons/loot_table.

Run from the repo root: python tools/gen_loot.py
The generated files are the source of truth once you start editing them by hand; re-running overwrites them, so only use
this to bootstrap or to reset. Structure: chests/<biome> (what a chest rolls) -> tiers/* (shared pools) + biome/<biome>
(flavour) + custom/* (server-supplied items). raid/<biome> is reserved for dungeon-owned raid rewards.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data/ascensiondungeons/loot_table"
NS = "ascensiondungeons"

# biome -> (flavour stone, flavour apricorn). Placeholders: swap for whatever each biome should give.
BIOMES = {
    "verdant_canopy": ("leaf_stone", "green_apricorn"),
    "ashen_caldera": ("fire_stone", "red_apricorn"),
    "glacial_vault": ("ice_stone", "white_apricorn"),
    "crystal_cavern": ("shiny_stone", "pink_apricorn"),
    "flooded_ruins": ("water_stone", "blue_apricorn"),
    "fungal_grotto": ("moon_stone", "black_apricorn"),
    "sunken_garden": ("sun_stone", "yellow_apricorn"),
    "ancient_foundry": ("thunder_stone", "red_apricorn"),
    "sunscorched_necropolis": ("dusk_stone", "yellow_apricorn"),
    "painted_badlands": ("sun_stone", "red_apricorn"),
    "cherry_grove": ("dawn_stone", "pink_apricorn"),
    "lapis_observatory": ("moon_stone", "blue_apricorn"),
    "crimson_cathedral": ("dusk_stone", "black_apricorn"),
    "gilded_library": ("shiny_stone", "yellow_apricorn"),
    "warped_hollow": ("water_stone", "green_apricorn"),
    "end_spire": ("dawn_stone", "white_apricorn"),
}


def item(name, weight=1, count=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    if count:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}}]
    return e


def table_ref(path, weight=1, conditions=None):
    e = {"type": "minecraft:loot_table", "value": f"{path}", "weight": weight}
    if conditions:
        e["conditions"] = conditions
    return e


def chest(pools):
    return {"type": "minecraft:chest", "pools": pools}


def write(rel, data):
    p = ROOT / f"{rel}.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    # ---- shared tiers (placeholders: replace the entries, keep the files) ---------------------------------------
    write("tiers/common", chest([{"rolls": 1, "entries": [
        item("cobblemon:poke_ball", 30, (2, 5)), item("cobblemon:potion", 25, (1, 3)),
        item("cobblemon:black_apricorn", 10, (1, 3)), item("minecraft:bread", 10, (2, 4))]}]))
    write("tiers/uncommon", chest([{"rolls": 1, "entries": [
        item("cobblemon:great_ball", 25, (1, 4)), item("cobblemon:super_potion", 25, (1, 2)),
        item("cobblemon:revive", 15), item("cobblemon:exp_candy_m", 15, (1, 2))]}]))
    write("tiers/rare", chest([{"rolls": 1, "entries": [
        item("cobblemon:ultra_ball", 20, (1, 3)), item("cobblemon:max_revive", 10),
        item("cobblemon:rare_candy", 12), item("cobblemon:ability_capsule", 4),
        table_ref(f"{NS}:custom/dungeon_token", 6)]}]))
    write("tiers/epic", chest([{"rolls": 1, "entries": [
        item("cobblemon:master_ball", 2), item("cobblemon:lucky_egg", 4), item("cobblemon:leftovers", 4),
        item("cobblemon:link_cable", 4),
        table_ref(f"{NS}:custom/raid_key", 4)]}]))

    # ---- server-supplied custom items ---------------------------------------------------------------------------
    # A vanilla item re-skinned with components: needs no client mod (custom_model_data needs a resource pack to look
    # different). custom_data is how server code recognises it later.
    write("custom/dungeon_token", chest([{"rolls": 1, "entries": [{
        "type": "minecraft:item", "name": "minecraft:paper",
        "functions": [{"function": "minecraft:set_components", "components": {
            # Text components are JSON *strings* in the 1.21.1 component codecs, not nested objects.
            "minecraft:custom_name": json.dumps({"text": "Dungeon Token", "italic": False, "color": "gold"}),
            "minecraft:lore": [json.dumps({"text": "PLACEHOLDER custom item", "italic": False, "color": "gray"})],
            "minecraft:custom_model_data": 1001,
            "minecraft:custom_data": {"ascension": {"id": "dungeon_token"}}}}]}]}]))
    # A table owned by another mod, pulled in by reference. fabric:load_conditions is only valid at the top of a table
    # file (not on an entry), so the guard lives here; a reference to a skipped table just rolls nothing.
    write("custom/raid_key", {
        "fabric:load_conditions": [{"condition": "fabric:all_mods_loaded", "values": ["cobbleraids"]}],
        "type": "minecraft:chest",
        "pools": [{"rolls": 1, "entries": [table_ref("cobbleraids:keys/starter")]}]})

    # ---- per biome ----------------------------------------------------------------------------------------------
    for biome, (stone, apricorn) in BIOMES.items():
        write(f"biome/{biome}", chest([{"rolls": 1, "entries": [
            item(f"cobblemon:{stone}", 6), item(f"cobblemon:{apricorn}", 10, (2, 5)),
            table_ref(f"{NS}:custom/dungeon_token", 2)]}]))
        write(f"chests/{biome}", chest([
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
                table_ref(f"{NS}:tiers/common", 60), table_ref(f"{NS}:tiers/uncommon", 28),
                table_ref(f"{NS}:tiers/rare", 10), table_ref(f"{NS}:tiers/epic", 2)]},
            {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 2}, "entries": [
                table_ref(f"{NS}:biome/{biome}", 1)]}]))
        # Reserved: dungeon-owned raid reward for this biome's arena (not wired to anything yet).
        write(f"raid/{biome}", chest([
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 3}, "entries": [
                table_ref(f"{NS}:tiers/uncommon", 30), table_ref(f"{NS}:tiers/rare", 50), table_ref(f"{NS}:tiers/epic", 20)]},
            {"rolls": 1, "entries": [table_ref(f"{NS}:biome/{biome}", 1)]}]))


if __name__ == "__main__":
    main()
