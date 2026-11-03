package com.ascensiondungeons.gen.landmark;

/**
 * Geode Vault: the Crystal Cavern loot landmark. A cracked-open geode: concentric shells of dark basalt, white calcite and
 * purple amethyst round a dry cavity, opened by an arched doorway on the +z side. The chest stands on a calcite plinth in
 * line with the doorway.
 *
 * <p>Module: 15 x 12 x 13; y=0 is the floor row; the doorway faces +z (south in template space). Chest at module
 * (7, 2, 3) facing south.
 */
public final class GeodeVault {
    private GeodeVault() {}

    public static final String ID = "crystal_geode_vault";
    public static final int SIZE_X = 15, SIZE_Y = 12, SIZE_Z = 13;
    public static final int CHEST_X = 7, CHEST_Y = 2, CHEST_Z = 3;
    public static final String CHEST = "minecraft:chest[facing=south,type=single,waterlogged=false]";

    private static final double RX = 7.4, RY = 11.4, RZ = 6.6;
    private static final double CZ_ = 5.0;

    private static final String BASALT = "minecraft:smooth_basalt";
    private static final String DARK = "minecraft:blackstone";
    private static final String CALCITE = "minecraft:calcite";
    private static final String AMETHYST = "minecraft:amethyst_block";
    private static final String BUDDING = "minecraft:budding_amethyst";
    private static final String LANTERN = "minecraft:sea_lantern";

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Normalised distance from the geode's centre: <1 is inside the outer shell. */
    private static double d(int x, int y, int z) {
        double dx = (x - 7) / RX, dy = y / RY, dz = (z - CZ_) / RZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, 4, z, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    double d = d(x, y, z);
                    if (d > 1.0) continue;
                    String b;
                    if (d < 0.62) b = Voxels.AIR;
                    else if (d < 0.75) b = hash(x, y, z) < 0.10 ? BUDDING : AMETHYST;
                    else if (d < 0.88) b = CALCITE;
                    else b = hash(x, y, z) < 0.5 ? BASALT : DARK;
                    if (y == 0) b = d < 0.62 ? CALCITE : b;
                    v.set(x, y, z, b);
                }
            }
        }
        // Doorway on the +z side: 5 wide, arched, 6 high, through every shell.
        for (int z = 8; z < SIZE_Z; z++) {
            for (int y = 1; y <= 6; y++) {
                int half = y <= 4 ? 2 : y == 5 ? 1 : 0;
                for (int x = 7 - half; x <= 7 + half; x++) v.set(x, y, z, Voxels.AIR);
            }
            for (int x = 5; x <= 9; x++) v.set(x, 0, z, hash(x, 0, z) < 0.5 ? CALCITE : BASALT);
        }
        // Plinth and chest, dry and in line with the door.
        for (int x = 6; x <= 8; x++) for (int z = 2; z <= 4; z++) v.set(x, 1, z, CALCITE);
        v.set(CHEST_X, CHEST_Y, CHEST_Z, CHEST);
        // Light: flush lanterns in the amethyst shell.
        for (int[] p : new int[][] {{7, 7, 5}, {3, 3, 5}, {11, 3, 5}, {4, 5, 2}, {10, 5, 2}}) {
            if (v.get(p[0], p[1], p[2]) != null && !v.get(p[0], p[1], p[2]).equals(Voxels.AIR)) v.set(p[0], p[1], p[2], LANTERN);
        }
        // Crystals growing inward from the amethyst shell: a cluster on any cavity face, never in front of the chest or door.
        String[][] dirs = {{"1", "0", "0", "east"}, {"-1", "0", "0", "west"}, {"0", "0", "1", "south"}, {"0", "0", "-1", "north"},
                {"0", "1", "0", "up"}, {"0", "-1", "0", "down"}};
        java.util.List<Object[]> growth = new java.util.ArrayList<>();
        for (int y = 1; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    if (!Voxels.AIR.equals(v.get(x, y, z))) continue;
                    if (z >= 8 || (x >= 6 && x <= 8 && z <= 5 && y <= 4)) continue;
                    for (String[] dir : dirs) {
                        int nx = x + Integer.parseInt(dir[0]), ny = y + Integer.parseInt(dir[1]), nz = z + Integer.parseInt(dir[2]);
                        String n = v.get(nx, ny, nz);
                        if (n == null || n.equals(Voxels.AIR)) continue;
                        // The cluster grows away from the solid block it sits on.
                        if (hash(x, y, z) < 0.30 && (ny != y || y > 0)) {
                            String facing = dir[3].equals("east") ? "west" : dir[3].equals("west") ? "east"
                                    : dir[3].equals("south") ? "north" : dir[3].equals("north") ? "south"
                                    : dir[3].equals("up") ? "down" : "up";
                            String kind = hash(x, y + 7, z) < 0.5 ? "amethyst_cluster" : "large_amethyst_bud";
                            growth.add(new Object[] {x, y, z, "minecraft:" + kind + "[facing=" + facing + ",waterlogged=false]"});
                        }
                        break;
                    }
                }
            }
        }
        for (Object[] g : growth) v.set((int) g[0], (int) g[1], (int) g[2], (String) g[3]);
        return v;
    }
}
