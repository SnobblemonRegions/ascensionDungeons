package com.ascensiondungeons.gen.landmark;


/**
 * Spore Court: the Warped Hollow raid arena. The Warden's Ring layout in warped planks and stripped stem, with wart-block
 * floor spokes, a soul-soil ring and a nylium inner ring inlaid flush in the floor, and four giant mushroom lamps on the
 * seating tiers either side of the doorway: a warped stem carrying a wart-block cap with a shroomlight under it. The combat
 * disc stays flat and clear.
 */
public final class SporeCourt {
    private SporeCourt() {}

    public static final String ID = "warped_spore_court";

    private static final String WART = "minecraft:warped_wart_block";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:warped_planks", "minecraft:stripped_warped_stem[axis=y]", WART, "minecraft:warped_nylium",
            "minecraft:warped_slab[type=bottom,waterlogged=false]", "minecraft:warped_planks",
            "minecraft:stripped_warped_stem[axis=y]", "minecraft:warped_planks", "minecraft:shroomlight");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return new Landmark(ID, build(doorDeg), cx - WardensRing.SIZE / 2, WardensRing.ORIGIN_Y, cz - WardensRing.SIZE / 2, false, 0);
    }

    public static Voxels build(double doorDeg) {
        Voxels v = WardensRing.build(STYLE, doorDeg);
        int c = WardensRing.SIZE / 2;
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                double r = Math.hypot(x - c, z - c);
                if (v.get(x, 0, z) != null) continue;
                if (Math.abs(r - 5.5) < 0.6) v.set(x, 0, z, "minecraft:soul_soil");
                else if (Math.abs(r - 9.0) < 0.6) v.set(x, 0, z, "minecraft:warped_nylium");
            }
        }
        // Mushroom lamps on the seating tiers.
        for (double off : new double[] {-102, -72, 72, 102}) {
            double a = Math.toRadians(doorDeg + off);
            int px = (int) Math.round(c + 18.6 * Math.cos(a)), pz = (int) Math.round(c + 18.6 * Math.sin(a));
            for (int y = 2; y <= 6; y++) v.setClipped(px, y, pz, "minecraft:warped_stem[axis=y]");
            v.setClipped(px, 6, pz, "minecraft:shroomlight");
            // The cap: a small dome of wart block over the lamp.
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    double d = Math.hypot(dx, dz);
                    if (d > 2.3) continue;
                    v.setClipped(px + dx, 7, pz + dz, WART);
                    if (d < 1.3) v.setClipped(px + dx, 8, pz + dz, WART);
                }
            }
        }
        return v;
    }
}
