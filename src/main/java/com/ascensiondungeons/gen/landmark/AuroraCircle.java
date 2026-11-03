package com.ascensiondungeons.gen.landmark;

/**
 * Aurora Circle: the Glacial Vault raid arena. The Warden's Ring layout in quartz and pale stone, dry and flat, with the
 * blue glass and packed ice confined to the rear arch.
 */
public final class AuroraCircle {
    private AuroraCircle() {}

    public static final String ID = "glacial_aurora_circle";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:quartz_bricks", "minecraft:chiseled_quartz_block", "minecraft:sea_lantern",
            "minecraft:polished_diorite", "minecraft:quartz_slab[type=bottom,waterlogged=false]", "minecraft:calcite",
            "minecraft:packed_ice", "minecraft:light_blue_stained_glass", "minecraft:sea_lantern");

    public static Landmark landmark() { return WardensRing.landmark(ID, STYLE); }

    public static Voxels build() { return WardensRing.build(STYLE, WardensRing.DOOR_ANGLE); }

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return WardensRing.landmarkAt(ID, STYLE, cx, cz, doorDeg);
    }

    public static WardensRing.Style style() { return STYLE; }
}
