package com.ascensiondungeons.gen.landmark;

/**
 * Crucible Ring: the Ashen Caldera raid arena. The Warden's Ring layout in blackstone, with flush amber insets in the
 * floor spokes and the rear lintel. There is no lava in the arena; the furnace arch is solid.
 */
public final class CrucibleRing {
    private CrucibleRing() {}

    public static final String ID = "ashen_crucible_ring";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:polished_blackstone_bricks", "minecraft:chiseled_polished_blackstone",
            "minecraft:ochre_froglight[axis=y]", "minecraft:polished_blackstone",
            "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]", "minecraft:blackstone",
            "minecraft:chiseled_polished_blackstone", "minecraft:polished_blackstone_bricks",
            "minecraft:ochre_froglight[axis=y]");

    public static Landmark landmark() { return WardensRing.landmark(ID, STYLE); }

    public static Voxels build() { return WardensRing.build(STYLE, WardensRing.DOOR_ANGLE); }

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return WardensRing.landmarkAt(ID, STYLE, cx, cz, doorDeg);
    }

    public static WardensRing.Style style() { return STYLE; }
}
