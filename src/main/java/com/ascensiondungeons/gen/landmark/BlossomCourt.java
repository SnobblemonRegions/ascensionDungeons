package com.ascensiondungeons.gen.landmark;

/**
 * Blossom Court: the Sunken Garden raid arena. The Warden's Ring layout in pale quartz and calcite, flat and dry in the
 * middle with subtle sea-lantern markers set flush in the floor, a rear garden arch of pilasters under a lintel of flowering
 * azalea, and no fountain or other obstacle on the fighting floor. The layout puts low flowering hedges outside the wall.
 */
public final class BlossomCourt {
    private BlossomCourt() {}

    public static final String ID = "sunken_blossom_court";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:quartz_bricks", "minecraft:chiseled_quartz_block", "minecraft:sea_lantern", "minecraft:calcite",
            "minecraft:quartz_slab[type=bottom,waterlogged=false]", "minecraft:calcite", "minecraft:quartz_bricks",
            "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]", "minecraft:sea_lantern");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return WardensRing.landmarkAt(ID, STYLE, cx, cz, doorDeg);
    }

    public static Voxels build() { return WardensRing.build(STYLE, WardensRing.DOOR_ANGLE); }

    public static WardensRing.Style style() { return STYLE; }
}
