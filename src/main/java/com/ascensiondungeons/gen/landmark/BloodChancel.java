package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Blood Chancel: the Crimson Cathedral raid arena. The Warden's Ring layout in nether brick and blackstone, with a gilded
 * cross inlaid flush in the floor inside a ring of nether wart, and four candelabra on the seating tiers either side of the
 * doorway: a blackstone stem carrying four lit red candles. The combat disc stays flat and clear.
 */
public final class BloodChancel {
    private BloodChancel() {}

    public static final String ID = "crimson_blood_chancel";

    private static final String GILDED = "minecraft:gilded_blackstone";
    private static final String WART = "minecraft:nether_wart_block";
    private static final String CANDLE = "minecraft:red_candle[candles=4,lit=true,waterlogged=false]";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:red_nether_bricks", "minecraft:chiseled_nether_bricks", "minecraft:crimson_planks",
            "minecraft:polished_blackstone", "minecraft:red_nether_brick_slab[type=bottom,waterlogged=false]",
            "minecraft:nether_bricks", "minecraft:chiseled_nether_bricks", "minecraft:polished_blackstone_bricks",
            "minecraft:shroomlight");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return new Landmark(ID, build(doorDeg), cx - WardensRing.SIZE / 2, WardensRing.ORIGIN_Y, cz - WardensRing.SIZE / 2, false, 0);
    }

    public static Voxels build(double doorDeg) {
        Voxels v = WardensRing.build(STYLE, doorDeg);
        int c = WardensRing.SIZE / 2;
        double a0 = Math.toRadians(doorDeg);
        // A cross in gilded blackstone, long arm toward the door, inside a ring of nether wart; flush with the floor.
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                double dx = x - c, dz = z - c, r = Math.hypot(dx, dz);
                if (v.get(x, 0, z) != null || r < 3 || r > 11.6) continue;
                double along = dx * Math.cos(a0) + dz * Math.sin(a0), lateral = -dx * Math.sin(a0) + dz * Math.cos(a0);
                boolean longArm = Math.abs(lateral) < 0.8 && along > -6.5 && along < 9.5;
                boolean crossArm = Math.abs(along) < 0.8 && Math.abs(lateral) < 5.5;
                if (Math.abs(r - 10.2) < 0.6) v.set(x, 0, z, WART);
                else if (longArm || crossArm) v.set(x, 0, z, GILDED);
            }
        }
        // Candelabra on the seating tiers.
        for (double off : new double[] {-100, -70, 70, 100}) {
            double a = Math.toRadians(doorDeg + off);
            int px = (int) Math.round(c + 18.6 * Math.cos(a)), pz = (int) Math.round(c + 18.6 * Math.sin(a));
            box(v, px, 2, pz, px, 6, pz, "minecraft:polished_blackstone_bricks");
            v.setClipped(px, 7, pz, "minecraft:polished_blackstone");
            v.setClipped(px, 8, pz, CANDLE);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                v.setClipped(px + d[0], 7, pz + d[1], "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]");
                v.setClipped(px + d[0], 8, pz + d[1], CANDLE);
            }
        }
        return v;
    }
}
