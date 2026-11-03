package com.ascensiondungeons.gen.landmark;

/**
 * Terrace of Roots: the Sunken Garden navigation landmark. Three broad masonry terraces rise one block each from a shallow
 * court of two reflecting pools to a tall retaining wall; a nine-wide stair climbs the middle and a three-wide stair either
 * side, flowering hedges edge every terrace, and a cornice at the top of the back wall lets rooted-dirt roots hang over the
 * highest terrace. No tree, no centrepiece.
 *
 * <p>Module coordinates: x in [0, 28], z in [0, 24] (z=0 the back wall, z=24 the court's front), y in [0, 19]; y=0 is the
 * floor row. Terrace tops are 1, 2 and 3 above the floor; every rise is a stair, so no step is higher than one block.
 */
public final class TerraceOfRoots {
    private TerraceOfRoots() {}

    public static final String ID = "sunken_terrace_of_roots";
    public static final int SIZE_X = 29, SIZE_Y = 20, SIZE_Z = 25;
    public static final int ORIGIN_Y = 4;
    /** First z of each terrace, front to back, and its top height. */
    public static final int[] TIER_Z = {15, 10, 5};
    public static final int[] TIER_H = {1, 2, 3};
    public static final int CENTRE_X0 = 10, CENTRE_X1 = 18;

    private static final String MOSSY = "minecraft:mossy_stone_bricks";
    private static final String QUARTZ = "minecraft:quartz_bricks";
    private static final String CALCITE = "minecraft:calcite";
    private static final String MOSS = "minecraft:moss_block";
    private static final String LEAF = "minecraft:azalea_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String FLOWER = "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String ROOT = "minecraft:rooted_dirt";
    private static final String MANGROVE = "minecraft:mangrove_roots[waterlogged=false]";
    private static final String ROOT_TIP = "minecraft:hanging_roots[waterlogged=false]";
    private static final String LANTERN = "minecraft:sea_lantern";
    private static final String WATER = "minecraft:water[level=0]";
    private static final String STAIR = "minecraft:quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";

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

    private static String masonry(int x, int y, int z) {
        double r = hash(x, y, z);
        return r < 0.18 ? MOSSY : r < 0.5 ? CALCITE : QUARTZ;
    }

    /** True for the columns a stair occupies: the broad middle one and a narrower one at each side. */
    static boolean stairColumn(int x) {
        return (x >= CENTRE_X0 && x <= CENTRE_X1) || x <= 3 && x >= 1 || x >= SIZE_X - 4 && x <= SIZE_X - 2;
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        // Back wall, tall, with a cornice from which the roots hang.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z <= 4; z++) for (int y = 1; y <= 9; y++) v.set(x, y, z, y == 9 ? CALCITE : masonry(x, y, z));
            for (int z = 0; z <= 1; z++) for (int y = 10; y <= 13; y++) v.set(x, y, z, masonry(x, y, z));
            for (int z = 0; z <= 6; z++) v.set(x, 14, z, z >= 5 || x % 4 == 0 ? CALCITE : QUARTZ);
        }
        // Roots hanging from the cornice: thick trunks two blocks square that narrow to one, with a pale tip, and a
        // clump of blossoming azalea at the top of every other one. None hangs below y=8, well above head height.
        for (int x = 2; x <= 25; x += 3) {
            int len = 3 + (int) (hash(x, 2, 2) * 4);
            for (int d = 0; d < len; d++) {
                int y = 13 - d;
                if (d < 2) {
                    for (int dz = 5; dz <= 6; dz++) for (int dx = 0; dx <= 1; dx++) v.set(x + dx, y, dz, hash(x + dx, y, dz) < 0.5 ? ROOT : MANGROVE);
                } else {
                    v.set(x, y, 5, hash(x, y, 5) < 0.5 ? ROOT : MANGROVE);
                }
            }
            v.set(x, 13 - len, 5, ROOT_TIP);
            if ((x / 3) % 2 == 0) {
                for (int dx = -1; dx <= 2; dx++) {
                    if (x + dx >= 0 && x + dx < SIZE_X) v.set(x + dx, 13, 7, hash(x + dx, 13, 7) < 0.5 ? FLOWER : LEAF);
                }
            }
        }
        // The three terraces.
        for (int t = 0; t < 3; t++) {
            int z0 = TIER_Z[t], h = TIER_H[t];
            for (int z = z0; z <= z0 + 4; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    for (int y = 1; y < h; y++) v.set(x, y, z, masonry(x, y, z));
                    boolean centre = x >= CENTRE_X0 && x <= CENTRE_X1;
                    v.set(x, h, z, centre ? (hash(x, h, z) < 0.5 ? QUARTZ : CALCITE) : hash(x, h, z) < 0.7 ? MOSS : ROOT);
                }
            }
            int front = z0 + 4;
            for (int x = 0; x < SIZE_X; x++) {
                if (stairColumn(x)) {
                    v.set(x, h, front, STAIR);
                } else {
                    v.set(x, h + 1, front, hash(x, h, 9) < 0.35 ? FLOWER : LEAF);       // a low hedge along the edge
                    if (x == 6 || x == 22) v.set(x, h - 1 >= 1 ? h - 1 : h, front, LANTERN); // recessed lights
                }
            }
        }
        // The court: two reflecting pools either side of the dry walkway.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 20; z < SIZE_Z; z++) {
                boolean pool = z >= 21 && (x >= 2 && x <= 8 || x >= 20 && x <= 26);
                v.set(x, 0, z, pool ? WATER : (x + z) % 3 == 0 ? QUARTZ : CALCITE);
            }
        }
        for (int x : new int[] {1, 9, 19, 27}) for (int z = 21; z < SIZE_Z; z++) v.set(x, 1, z, x == 1 || x == 27 ? MOSS : QUARTZ);
        return v;
    }
}
