package com.ascensiondungeons.gen.landmark;

/**
 * Resonant Gallery: the Crystal Cavern exploration landmark. A walk-through calcite barrel vault with three arch ribs, purple
 * glass windows in the side walls, and chunky amethyst niches behind the glass. The walkway runs along x, 9 wide with the
 * springline at head height, and both ends are open.
 *
 * <p>Module: 27 x 15 x 13 (x, y, z); y=0 is the floor row. The clear lane is z 2..10.
 */
public final class ResonantGallery {
    private ResonantGallery() {}

    public static final String ID = "crystal_resonant_gallery";
    public static final int SIZE_X = 27, SIZE_Y = 15, SIZE_Z = 13;
    public static final int LANE_FROM = 2, LANE_TO = 10;
    public static final int[] RIBS = {3, 12, 21};          // first x of each 3-wide rib

    private static final String CALCITE = "minecraft:calcite";
    private static final String BRICKS = "minecraft:polished_tuff";
    private static final String CHISELED = "minecraft:chiseled_tuff";
    private static final String BASALT = "minecraft:smooth_basalt";
    private static final String AMETHYST = "minecraft:amethyst_block";
    private static final String GLASS = "minecraft:purple_stained_glass";
    private static final String LANTERN = "minecraft:sea_lantern";
    private static final double SPRING_Y = 7, MID_Z = 6;

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The gallery with its module corner at template (x, 4, z). The lane then runs at template z .. z+10. */
    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, 4, z, false, 0);
    }

    private static boolean rib(int x) {
        for (int r : RIBS) if (x >= r && x < r + 3) return true;
        return false;
    }

    private static String shell(int x, int y, int z) {
        double q = hash(x, y, z);
        return q < 0.55 ? CALCITE : q < 0.80 ? BRICKS : q < 0.9 ? BASALT : CHISELED;
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        for (int x = 0; x < SIZE_X; x++) {
            // Floor: a polished lane with calcite edges.
            for (int z = 0; z < SIZE_Z; z++) v.set(x, 0, z, z < LANE_FROM || z > LANE_TO ? BRICKS : hash(x, 0, z) < 0.7 ? "minecraft:polished_diorite" : CALCITE);
            for (int z = 0; z < SIZE_Z; z++) {
                for (int y = 1; y < SIZE_Y; y++) {
                    double dz = z - MID_Z, dy = y - SPRING_Y;
                    boolean wall = y <= SPRING_Y && (z < LANE_FROM || z > LANE_TO);
                    double r = Math.hypot(dz, dy);
                    // Vault: a half-round shell above the springline, 2 thick, thickened to 3 at the ribs.
                    double inner = rib(x) ? 3.7 : 4.6;
                    boolean arch = y > SPRING_Y && r >= inner && r <= 6.6;
                    if (wall || arch) v.set(x, y, z, shell(x, y, z));
                    else if (y <= SPRING_Y || r < inner) v.set(x, y, z, Voxels.AIR);   // the lane; beyond the shell is rock
                }
            }
            // Cap each rib's crown with a chiselled keystone band.
            if (rib(x)) {
                for (int z = 4; z <= 8; z++) v.set(x, 12, z, CHISELED);
            }
        }
        // Windows: purple glass in the inner wall layer between the ribs, a chunky amethyst niche behind each.
        for (int x = 0; x < SIZE_X; x++) {
            if (!((x >= 7 && x <= 10) || (x >= 16 && x <= 19))) continue;
            for (int y = 2; y <= 6; y++) {
                if ((y == 2 || y == 6) && (x == 7 || x == 10 || x == 16 || x == 19)) continue;   // rounded corners
                v.set(x, y, 1, GLASS);
                v.set(x, y, SIZE_Z - 2, GLASS);
                v.set(x, y, 0, hash(x, y, 0) < 0.12 ? "minecraft:budding_amethyst" : AMETHYST);
                v.set(x, y, SIZE_Z - 1, hash(x, y, 12) < 0.12 ? "minecraft:budding_amethyst" : AMETHYST);
            }
        }
        // Lights: flush lanterns in each rib crown, and low in the piers.
        for (int r : RIBS) {
            v.set(r + 1, 11, 6, LANTERN);
            v.set(r + 1, 3, 1, LANTERN);
            v.set(r + 1, 3, SIZE_Z - 2, LANTERN);
        }
        return v;
    }
}
