package com.ascensiondungeons.gen.landmark;

/**
 * Lotus Steps: the Sunken Garden exploration landmark. A complete nine-block dry stone walkway runs east to west between
 * two rows of three shallow rectangular pools, each row separated by planted terraces of moss and flowering azalea, with
 * lily pads on the water. Pools are flush with the floor and sealed by their rims.
 *
 * <p>Module coordinates: x in [0, 26], z in [0, 18], y in [0, 11]; y=0 is the floor row. Walkway z 5..13; pools z 1..3
 * and z 15..17 in bays x 1..7, 10..16 and 19..25.
 */
public final class LotusSteps {
    private LotusSteps() {}

    public static final String ID = "sunken_lotus_steps";
    public static final int SIZE_X = 27, SIZE_Y = 12, SIZE_Z = 19;
    public static final int ORIGIN_Y = 4;
    public static final int WALK_Z0 = 5, WALK_Z1 = 13;

    private static final String QUARTZ = "minecraft:quartz_bricks";
    private static final String CALCITE = "minecraft:calcite";
    private static final String MOSSY = "minecraft:mossy_stone_bricks";
    private static final String MOSS = "minecraft:moss_block";
    private static final String WATER = "minecraft:water[level=0]";
    private static final String FLOWER = "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String LEAF = "minecraft:azalea_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String LANTERN = "minecraft:sea_lantern";

    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, ORIGIN_Y, z, false, 0);
    }

    private static boolean inBay(int x) {
        return x >= 1 && x <= 7 || x >= 10 && x <= 16 || x >= 19 && x <= 25;
    }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                boolean walk = z >= WALK_Z0 && z <= WALK_Z1;
                boolean pool = (z >= 1 && z <= 3 || z >= 15 && z <= 17) && inBay(x);
                if (walk) {
                    String s = z == WALK_Z0 || z == WALK_Z1 ? MOSSY : z == 9 && x % 2 == 0 ? QUARTZ : hash(x, 0, z) < 0.5 ? CALCITE : QUARTZ;
                    v.set(x, 0, z, s);
                } else if (pool) {
                    v.set(x, 0, z, WATER);
                    if (hash(x, 1, z) < 0.3) v.set(x, 1, z, "minecraft:lily_pad");
                } else {
                    v.set(x, 0, z, QUARTZ);                                    // rim
                }
            }
        }
        // Planted terraces between the bays and at both ends, either side of the walkway.
        for (int x = 0; x < SIZE_X; x++) {
            if (inBay(x)) continue;
            for (int z : new int[] {0, 1, 2, 3, 4, 14, 15, 16, 17, 18}) {
                v.set(x, 1, z, MOSS);
                if (z != 4 && z != 14) v.set(x, 2, z, hash(x, 2, z) < 0.45 ? FLOWER : LEAF);
            }
        }
        // Rim kerb along the walkway side of each pool row, and recessed lights in the terraces.
        for (int x = 0; x < SIZE_X; x++) {
            if (!inBay(x)) continue;
            v.set(x, 1, 4, x % 3 == 0 ? CALCITE : QUARTZ);
            v.set(x, 1, 14, x % 3 == 0 ? CALCITE : QUARTZ);
        }
        for (int x : new int[] {8, 17}) for (int z : new int[] {4, 14}) v.set(x, 1, z, LANTERN);
        return v;
    }
}
