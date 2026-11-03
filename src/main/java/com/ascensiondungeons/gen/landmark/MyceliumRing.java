package com.ascensiondungeons.gen.landmark;

/**
 * Mycelium Ring: the Fungal Grotto raid arena. The Warden's Ring layout in earthy mud brick: a flat packed-mud centre
 * disc, tiers of mud-brick seating, a rear crescent of pilasters under a lintel of mushroom stem and red cap, and amber
 * shroomlights set flush in the floor spokes. There are no mushrooms on the combat floor.
 */
public final class MyceliumRing {
    private MyceliumRing() {}

    public static final String ID = "fungal_mycelium_ring";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:mud_bricks", "minecraft:packed_mud", "minecraft:shroomlight", "minecraft:mycelium",
            "minecraft:mud_brick_slab[type=bottom,waterlogged=false]", "minecraft:mud_bricks", "minecraft:mushroom_stem",
            "minecraft:red_mushroom_block", "minecraft:shroomlight");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return WardensRing.landmarkAt(ID, STYLE, cx, cz, doorDeg);
    }

    public static Voxels build() { return WardensRing.build(STYLE, WardensRing.DOOR_ANGLE); }

    public static WardensRing.Style style() { return STYLE; }
}
