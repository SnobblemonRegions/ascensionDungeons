package com.ascensiondungeons.gen.landmark;

/**
 * Tidewheel: the Flooded Ruins navigation landmark. A squat aqueduct wall carries a sealed trough of still water along its
 * top; in front of it stands a stationary stone waterwheel, a thick ring with eight spokes whose lowest point rests on the
 * floor. Two glass streams fall from spouts at either end into a shallow basin, with a low curb and a dry viewing path
 * along its front.
 *
 * <p>Module coordinates: x in [0, 28], z in [0, 14] (z=0 is the wall's back), y in [0, 26]; y=0 is the floor row. The
 * whole module is one connected piece of masonry resting on the floor: the wheel touches the wall and the floor, the
 * spouts touch the wall, the streams hang from the spouts.
 */
public final class Tidewheel {
    private Tidewheel() {}

    public static final String ID = "flooded_tidewheel";
    public static final int SIZE_X = 29;
    public static final int SIZE_Y = 27;
    public static final int SIZE_Z = 15;
    public static final int ORIGIN_Y = 4;
    /** Wheel centre in module coordinates, and its outer and inner radius. */
    public static final int CENTER_X = 14, CENTER_Y = 13;
    public static final double R_OUT = 13.0, R_IN = 10.0;
    /** The wheel stands in z 5..7, against the wall (z 0..4). */
    public static final int WHEEL_Z0 = 5, WHEEL_Z1 = 7;
    /** The basin water is at y=0 in z 8..12; the dry viewing path is z 13..14. */
    public static final int BASIN_Z0 = 8, BASIN_Z1 = 12;

    private static final String PRIS = "minecraft:prismarine_bricks";
    private static final String DARK = "minecraft:dark_prismarine";
    private static final String BRICK = "minecraft:stone_bricks";
    private static final String MOSSY = "minecraft:mossy_stone_bricks";
    private static final String CRACKED = "minecraft:cracked_stone_bricks";
    private static final String COPPER = "minecraft:oxidized_cut_copper";
    private static final String LANTERN = "minecraft:sea_lantern";
    private static final String GLASS = "minecraft:light_blue_stained_glass";
    private static final String WATER = "minecraft:water[level=0]";
    private static final String SLAB = "minecraft:stone_brick_slab[type=bottom,waterlogged=false]";

    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, ORIGIN_Y, z, false, 0);
    }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static String wall(int x, int y, int z) {
        double r = hash(x, y, z);
        return r < 0.35 ? MOSSY : r < 0.47 ? CRACKED : r < 0.57 ? PRIS : BRICK;
    }

    /** The wheel is mostly plain stone brick so its silhouette reads against the busier walls around it. */
    private static String wheelBlock(int x, int y, int z) {
        double r = hash(x, y, z);
        return r < 0.10 ? MOSSY : r < 0.22 ? CRACKED : r < 0.30 ? DARK : BRICK;
    }

    /** True where the wheel (ring, spokes or hub) has a block at module (x, y). */
    static boolean wheelCell(int x, int y) {
        double dx = x - CENTER_X, dy = y - CENTER_Y;
        double d = Math.hypot(dx, dy);
        if (d <= R_OUT && d >= R_IN) return true;
        if (d > R_IN) return false;
        if (d <= 2.3) return true;                                                   // hub
        double ax = Math.abs(dx), ay = Math.abs(dy);
        return ax < 0.5 || ay < 0.5 || Math.abs(ax - ay) <= 1.0;                     // axis and diagonal spokes
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        // Aqueduct wall, 5 thick and 8 high.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z <= 4; z++) {
                for (int y = 0; y <= 8; y++) v.set(x, y, z, y == 8 ? (x % 4 == 0 ? COPPER : DARK) : wall(x, y, z));
            }
        }
        // Sealed trough on top: rim all round, still water inside, a lantern at each corner.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean rim = x == 0 || x == SIZE_X - 1 || z == 0 || z == 4;
                v.set(x, 9, z, rim ? wall(x, 9, z) : WATER);
            }
        }
        for (int x : new int[] {0, SIZE_X - 1}) for (int z : new int[] {0, 4}) v.set(x, 10, z, LANTERN);
        // The wheel.
        for (int y = 0; y < SIZE_Y; y++) {
            for (int x = 0; x < SIZE_X; x++) {
                if (!wheelCell(x, y)) continue;
                for (int z = WHEEL_Z0; z <= WHEEL_Z1; z++) {
                    boolean eye = Math.hypot(x - CENTER_X, y - CENTER_Y) <= 0.8 && z == 6;
                    boolean hub = Math.hypot(x - CENTER_X, y - CENTER_Y) <= 2.3;
                    v.set(x, y, z, eye ? LANTERN : hub ? COPPER : wheelBlock(x, y, z));
                }
            }
        }
        // Spouts at either end of the wall, streams down into the basin.
        for (int x : new int[] {1, SIZE_X - 2}) {
            for (int z = 5; z <= 9; z++) v.set(x, 8, z, BRICK);
            for (int y = 1; y <= 7; y++) v.set(x, y, 9, GLASS);
        }
        // Basin: still water flush with the floor, a low curb, and a dry path in front.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = BASIN_Z0; z <= BASIN_Z1; z++) v.set(x, 0, z, WATER);
            v.set(x, 1, 13, SLAB);
        }
        for (int z = BASIN_Z0; z <= BASIN_Z1; z++) {
            v.set(0, 1, z, SLAB);
            v.set(SIZE_X - 1, 1, z, SLAB);
        }
        for (int z = 13; z <= 14; z++) for (int x = 0; x < SIZE_X; x++) v.set(x, 0, z, (x + z) % 3 == 0 ? PRIS : wall(x, 0, z));
        return v;
    }
}
