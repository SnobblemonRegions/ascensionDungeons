package com.ascensiondungeons.gen.landmark;

/**
 * Anvil Court: the Ancient Foundry raid arena. The Warden's Ring layout in polished andesite: a flat clear centre with a
 * copper-inlaid ring and eight copper rivet spokes, seating tiers of deepslate and stone, a rear crescent of pilasters
 * under an iron-block lintel, and ochre froglights set flush in the floor. The monumental anvil relief is cut into the
 * rear wall above the pilasters by the layout, out of the combat floor's reach; no machine stands on the floor.
 */
public final class AnvilCourt {
    private AnvilCourt() {}

    public static final String ID = "foundry_anvil_court";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:polished_andesite", "minecraft:chiseled_copper", "minecraft:cut_copper", "minecraft:stone_bricks",
            "minecraft:stone_brick_slab[type=bottom,waterlogged=false]", "minecraft:deepslate_bricks",
            "minecraft:deepslate_bricks", "minecraft:iron_block", "minecraft:ochre_froglight[axis=y]");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return WardensRing.landmarkAt(ID, STYLE, cx, cz, doorDeg);
    }

    public static Voxels build() { return WardensRing.build(STYLE, WardensRing.DOOR_ANGLE); }

    public static WardensRing.Style style() { return STYLE; }
}
