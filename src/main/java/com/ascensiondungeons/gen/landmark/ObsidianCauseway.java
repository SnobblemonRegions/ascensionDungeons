package com.ascensiondungeons.gen.landmark;

/**
 * Obsidian Causeway: the Ashen Caldera exploration landmark. A level obsidian bridge with a 9-wide lane and a stout
 * parapet each side crosses the central lava lake, and a continuous curb fences the rest of the lake so nobody can walk
 * or fall in. The base layout supplies the lake itself.
 *
 * <p>The module is centred on the lake: 49 x 37 in plan with the lake centre at module (24, 18). The deck replaces the
 * floor row, so its top is level with the ground. The lane runs along x through module z 14..22.
 */
public final class ObsidianCauseway {
    private ObsidianCauseway() {}

    public static final String ID = "ashen_obsidian_causeway";
    public static final int SIZE_X = 49, SIZE_Y = 6, SIZE_Z = 37;
    public static final int LAKE_X = 24, LAKE_Z = 18;
    public static final int LANE_FROM = LAKE_Z - 4, LANE_TO = LAKE_Z + 4;
    public static final int PARAPET_NORTH = LANE_FROM - 1, PARAPET_SOUTH = LANE_TO + 1;
    public static final double LAKE_RADIUS = 16.2;

    private static final String DECK = "minecraft:obsidian";
    private static final String PARAPET = "minecraft:polished_blackstone_bricks";
    private static final String CURB = "minecraft:blackstone";
    private static final String CURB_ALT = "minecraft:basalt[axis=y]";

    /** The causeway across a lake whose centre is template (cx, cz). */
    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - LAKE_X, 4, cz - LAKE_Z, false, 0);
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
            for (int z = PARAPET_NORTH; z <= PARAPET_SOUTH; z++) {
                v.set(x, 0, z, DECK);
                if (z == PARAPET_NORTH || z == PARAPET_SOUTH) {
                    v.set(x, 1, z, PARAPET);
                    v.set(x, 2, z, PARAPET);
                } else {
                    for (int y = 1; y <= 3; y++) v.set(x, y, z, Voxels.AIR);
                }
            }
        }
        // Curb round the lake, just outside its edge, open only where the deck crosses.
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                double r = Math.hypot(x - LAKE_X, z - LAKE_Z);
                if (r <= LAKE_RADIUS || r >= LAKE_RADIUS + 2.2) continue;
                if (z >= PARAPET_NORTH && z <= PARAPET_SOUTH) continue;
                int height = hash(x, 1, z) < 0.35 ? 3 : 2;
                for (int y = 1; y <= height; y++) v.set(x, y, z, hash(x, y, z) < 0.3 ? CURB_ALT : CURB);
            }
        }
        return v;
    }
}
