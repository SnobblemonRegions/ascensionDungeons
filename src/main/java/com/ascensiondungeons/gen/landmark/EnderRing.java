package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Ender Ring: the End Spire raid arena. The Warden's Ring layout in end stone brick and purpur, with an obsidian ring and
 * a purpur eight-pointed star inlaid flush in the floor, and four caged pylons on the seating tiers either side of the
 * doorway: an obsidian shaft carrying a cage of iron bars round a glowing violet core, like the pillars of the dragon's
 * island. The combat disc stays flat and clear.
 */
public final class EnderRing {
    private EnderRing() {}

    public static final String ID = "end_ender_ring";

    private static final String OBSIDIAN = "minecraft:obsidian";
    private static final String PURPUR = "minecraft:purpur_block";
    private static final String BARS = "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:end_stone_bricks", "minecraft:purpur_pillar[axis=y]", PURPUR, "minecraft:end_stone",
            "minecraft:purpur_slab[type=bottom,waterlogged=false]", "minecraft:end_stone_bricks",
            "minecraft:purpur_pillar[axis=y]", OBSIDIAN, "minecraft:end_rod[facing=down]");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return new Landmark(ID, build(doorDeg), cx - WardensRing.SIZE / 2, WardensRing.ORIGIN_Y, cz - WardensRing.SIZE / 2, false, 0);
    }

    public static Voxels build(double doorDeg) {
        Voxels v = WardensRing.build(STYLE, doorDeg);
        int c = WardensRing.SIZE / 2;
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                double dx = x - c, dz = z - c, r = Math.hypot(dx, dz);
                if (v.get(x, 0, z) != null || r < 3 || r > 11.6) continue;
                double theta = Math.toDegrees(Math.atan2(dz, dx));
                double off = ((theta % 45) + 45) % 45;
                boolean ray = Math.min(off, 45 - off) * Math.PI / 180 * r < 0.7;
                if (Math.abs(r - 10) < 0.6) v.set(x, 0, z, OBSIDIAN);
                else if (ray && r < 8.8) v.set(x, 0, z, PURPUR);
            }
        }
        for (double off : new double[] {-102, -72, 72, 102}) {
            double a = Math.toRadians(doorDeg + off);
            int px = (int) Math.round(c + 18.6 * Math.cos(a)), pz = (int) Math.round(c + 18.6 * Math.sin(a));
            box(v, px, 2, pz, px, 9, pz, OBSIDIAN);
            v.setClipped(px, 10, pz, "minecraft:magenta_stained_glass");
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
                for (int y = 10; y <= 11; y++) v.setClipped(px + d[0], y, pz + d[1], BARS);
            }
            v.setClipped(px, 11, pz, "minecraft:end_rod[facing=up]");
        }
        return v;
    }
}
