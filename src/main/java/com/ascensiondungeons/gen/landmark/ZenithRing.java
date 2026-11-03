package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Zenith Ring: the Lapis Observatory raid arena. The Warden's Ring layout in deepslate tile with lapis floor spokes, an
 * eight-pointed gold star and a lapis ring inlaid flush in the floor, and four pylons on the seating tiers either side of
 * the doorway: a broad deepslate foot, a slender gold shaft and a star of end rods. The combat disc stays flat and clear.
 */
public final class ZenithRing {
    private ZenithRing() {}

    public static final String ID = "lapis_zenith_ring";

    private static final String GOLD = "minecraft:gold_block";
    private static final String LAPIS = "minecraft:lapis_block";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:deepslate_tiles", "minecraft:chiseled_deepslate", LAPIS, "minecraft:polished_deepslate",
            "minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]", "minecraft:polished_deepslate",
            "minecraft:chiseled_deepslate", LAPIS, "minecraft:sea_lantern");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return new Landmark(ID, build(doorDeg), cx - WardensRing.SIZE / 2, WardensRing.ORIGIN_Y, cz - WardensRing.SIZE / 2, false, 0);
    }

    public static Voxels build(double doorDeg) {
        Voxels v = WardensRing.build(STYLE, doorDeg);
        int c = WardensRing.SIZE / 2;
        // An eight-pointed star of gold rays and a lapis ring, flush, where the base left the floor alone.
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                double dx = x - c, dz = z - c, r = Math.hypot(dx, dz);
                if (v.get(x, 0, z) != null || r < 3 || r > 11.6) continue;
                double theta = Math.toDegrees(Math.atan2(dz, dx));
                double off = ((theta % 45) + 45) % 45;
                boolean ray = Math.min(off, 45 - off) * Math.PI / 180 * r < 0.7;
                if (Math.abs(r - 9.4) < 0.6) v.set(x, 0, z, LAPIS);
                else if (ray && r < 8.6) v.set(x, 0, z, GOLD);
            }
        }
        // Pylons on the seating tiers.
        for (double off : new double[] {-100, -70, 70, 100}) {
            double a = Math.toRadians(doorDeg + off);
            int px = (int) Math.round(c + 18.6 * Math.cos(a)), pz = (int) Math.round(c + 18.6 * Math.sin(a));
            box(v, px, 2, pz, px + 1, 3, pz + 1, "minecraft:deepslate_tiles");
            for (int y = 4; y <= 9; y++) v.setClipped(px, y, pz, GOLD);
            v.setClipped(px, 10, pz, "minecraft:end_rod[facing=up]");
            v.setClipped(px - 1, 8, pz, "minecraft:end_rod[facing=west]");
            v.setClipped(px + 1, 8, pz, "minecraft:end_rod[facing=east]");
            v.setClipped(px, 8, pz - 1, "minecraft:end_rod[facing=north]");
            v.setClipped(px, 8, pz + 1, "minecraft:end_rod[facing=south]");
        }
        return v;
    }
}
