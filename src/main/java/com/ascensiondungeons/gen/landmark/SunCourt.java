package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Sun Court: the Sunscorched Necropolis raid arena. The Warden's Ring layout in cut sandstone with gold floor spokes, plus a
 * gold sun disc inlaid flush in the floor (a ring of gold at radius 8 with sixteen rays), and four gold-capped obelisks
 * standing on the seating tiers either side of the doorway. The combat disc stays flat and clear.
 */
public final class SunCourt {
    private SunCourt() {}

    public static final String ID = "necropolis_sun_court";

    private static final String GOLD = "minecraft:gold_block";
    private static final String CHISELED = "minecraft:chiseled_sandstone";
    private static final String SMOOTH = "minecraft:smooth_sandstone";
    private static final String GLOW = "minecraft:glowstone";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:cut_sandstone", CHISELED, GOLD, SMOOTH, "minecraft:cut_sandstone_slab[type=bottom,waterlogged=false]",
            "minecraft:sandstone", CHISELED, SMOOTH, GLOW);

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return new Landmark(ID, build(doorDeg), cx - WardensRing.SIZE / 2, WardensRing.ORIGIN_Y, cz - WardensRing.SIZE / 2, false, 0);
    }

    public static Voxels build(double doorDeg) {
        Voxels v = WardensRing.build(STYLE, doorDeg);
        int c = WardensRing.SIZE / 2;
        // Sun disc: a gold ring at radius 8 and sixteen thin rays, flush with the floor, only where the base left it alone.
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                double dx = x - c, dz = z - c, r = Math.hypot(dx, dz);
                if (v.get(x, 0, z) != null || r < 3 || r > 11.5) continue;
                double theta = Math.toDegrees(Math.atan2(dz, dx));
                double off = Math.abs(((theta % 22.5) + 22.5) % 22.5);
                boolean ray = Math.min(off, 22.5 - off) * Math.PI / 180 * r < 0.55;
                if (Math.abs(r - 8) < 0.6 || ray && r > 3.5 && r < 7.4) v.set(x, 0, z, GOLD);
            }
        }
        // Obelisks on the seating tiers either side of the doorway.
        for (double off : new double[] {-100, -70, 70, 100}) {
            double a = Math.toRadians(doorDeg + off);
            int ox = (int) Math.round(c + 18.6 * Math.cos(a)), oz = (int) Math.round(c + 18.6 * Math.sin(a));
            for (int y = 2; y <= 9; y++) box(v, ox, y, oz, ox + 1, y, oz + 1, y == 2 ? CHISELED : SMOOTH);
            box(v, ox, 10, oz, ox + 1, 10, oz + 1, GOLD);
            v.setClipped(ox, 11, oz, GLOW);
        }
        return v;
    }
}
