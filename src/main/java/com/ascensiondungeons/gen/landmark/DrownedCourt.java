package com.ascensiondungeons.gen.landmark;

/**
 * Drowned Court: the Flooded Ruins raid arena. The Warden's Ring layout in prismarine and mossy stone: dry and flat in the
 * middle, with a copper-inlaid ring, and the rear colonnade of connected arches. The layout puts shallow water round the
 * outside of the chamber wall, never inside it.
 */
public final class DrownedCourt {
    private DrownedCourt() {}

    public static final String ID = "flooded_drowned_court";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:oxidized_cut_copper",
            "minecraft:mossy_stone_bricks", "minecraft:prismarine_brick_slab[type=bottom,waterlogged=false]",
            "minecraft:mossy_stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:prismarine_bricks",
            "minecraft:sea_lantern");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return WardensRing.landmarkAt(ID, STYLE, cx, cz, doorDeg);
    }

    public static Voxels build() { return WardensRing.build(STYLE, WardensRing.DOOR_ANGLE); }

    public static WardensRing.Style style() { return STYLE; }
}
