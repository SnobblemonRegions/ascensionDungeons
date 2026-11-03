package com.ascensiondungeons.gen.landmark;


/**
 * Petal Court: the Cherry Grove raid arena. The Warden's Ring layout in cherry wood, with pink and white terracotta rings
 * inlaid flush in the floor, four stone lanterns on the seating tiers and four cherry-leaf canopies spread over
 * the side seating (clear of the combat disc and the doorway), which shed petals into the ring. The combat disc stays flat and clear.
 */
public final class PetalCourt {
    private PetalCourt() {}

    public static final String ID = "cherry_petal_court";

    private static final String LEAVES = "minecraft:cherry_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String LOG = "minecraft:cherry_log[axis=y]";
    private static final String PINK = "minecraft:pink_terracotta";
    private static final String WHITE = "minecraft:white_terracotta";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:cherry_planks", "minecraft:stripped_cherry_log[axis=y]", PINK, WHITE,
            "minecraft:cherry_slab[type=bottom,waterlogged=false]", "minecraft:cherry_planks",
            "minecraft:stripped_cherry_wood", "minecraft:cherry_planks", "minecraft:pearlescent_froglight[axis=y]");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return new Landmark(ID, build(doorDeg), cx - WardensRing.SIZE / 2, WardensRing.ORIGIN_Y, cz - WardensRing.SIZE / 2, false, 0);
    }

    public static Voxels build(double doorDeg) {
        Voxels v = WardensRing.build(STYLE, doorDeg);
        int c = WardensRing.SIZE / 2;
        // Concentric rings of pink and white in the floor, flush, only where the base arena left it alone.
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                double r = Math.hypot(x - c, z - c);
                if (v.get(x, 0, z) != null) continue;
                if (Math.abs(r - 5) < 0.6 || Math.abs(r - 9.5) < 0.6) v.set(x, 0, z, PINK);
                else if (Math.abs(r - 7.2) < 0.45) v.set(x, 0, z, WHITE);
            }
        }
        // Four stone lanterns on the seating tiers either side of the doorway.
        for (double off : new double[] {-58, -42, 42, 58}) {
            double a = Math.toRadians(doorDeg + off);
            int x = (int) Math.round(c + 18.8 * Math.cos(a)), z = (int) Math.round(c + 18.8 * Math.sin(a));
            v.setClipped(x, 2, z, "minecraft:stone_bricks");
            v.setClipped(x, 3, z, "minecraft:lantern[hanging=false,waterlogged=false]");
        }
        // Cherry canopies over the back of the seating: a trunk on the tier and a leaf crown above head height.
        for (double off : new double[] {-105, -80, 80, 105}) {
            double a = Math.toRadians(doorDeg + off);
            int tx = (int) Math.round(c + 19 * Math.cos(a)), tz = (int) Math.round(c + 19 * Math.sin(a));
            for (int y = 2; y <= 8; y++) v.setClipped(tx, y, tz, LOG);
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    if (Math.hypot(dx, dz) > 3.2) continue;
                    double rr = Math.hypot(tx + dx - c, tz + dz - c);
                    if (rr < 15.5) continue;                       // the crown never reaches over the combat floor
                    v.setClipped(tx + dx, 9, tz + dz, LEAVES);
                    if (Math.hypot(dx, dz) < 2.2) v.setClipped(tx + dx, 10, tz + dz, LEAVES);
                }
            }
        }
        return v;
    }
}
