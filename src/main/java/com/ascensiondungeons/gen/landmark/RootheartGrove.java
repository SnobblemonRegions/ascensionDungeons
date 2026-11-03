package com.ascensiondungeons.gen.landmark;

/**
 * Rootheart Grove: the Verdant Canopy navigation landmark (landmark atlas, entry 01). An oversized oak on a shallow
 * mossy stone foundation, four low roots, a branching crown under the vault and a root recess holding the optional
 * loot chest. Authored in code so it is exactly reproducible; see {@code RootheartGroveTest} for the rules it keeps.
 *
 * <p>Module coordinates: x, z in [0, 20] with the trunk on (10, 10); y in [0, 29] with y=0 the floor row. The root base
 * is 13 x 13, the crown 21 x 21, the whole tree 29 above the floor. The chest recess faces south (+z), toward the
 * explorable side of the habitat.
 */
public final class RootheartGrove {
    private RootheartGrove() {}

    public static final String ID = "verdant_rootheart_grove";
    public static final int SIZE_XZ = 21;
    public static final int SIZE_Y = 30;
    public static final int CENTER = 10;
    /** Template position of the module's (0, 0, 0): the trunk lands on template (111, 4, 107). */
    public static final int ORIGIN_X = 101;
    public static final int ORIGIN_Y = 4;
    public static final int ORIGIN_Z = 97;

    public static final String CHEST = "minecraft:chest[facing=south,type=single,waterlogged=false]";
    public static final String LEAVES = "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String LOG_Y = "minecraft:oak_log[axis=y]";
    private static final String LOG_X = "minecraft:oak_log[axis=x]";
    private static final String LOG_Z = "minecraft:oak_log[axis=z]";
    private static final String STRIPPED_X = "minecraft:stripped_oak_log[axis=x]";
    private static final String STRIPPED_Z = "minecraft:stripped_oak_log[axis=z]";
    private static final String MOSSY = "minecraft:mossy_stone_bricks";
    private static final String BRICK = "minecraft:stone_bricks";
    private static final String CRACKED = "minecraft:cracked_stone_bricks";
    private static final String SLAB = "minecraft:mossy_stone_brick_slab[type=bottom,waterlogged=false]";
    private static final String FROGLIGHT = "minecraft:ochre_froglight[axis=y]";

    public static Landmark landmark() {
        return landmarkAt(ORIGIN_X, ORIGIN_Z);
    }

    /** The grove with its module corner at template (x, 4, z). */
    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, ORIGIN_Y, z, true, 8);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_XZ, SIZE_Y, SIZE_XZ);
        foundation(v);
        trunk(v);
        roots(v);
        chestRecess(v);
        branches(v);
        crown(v);
        return v;
    }

    private static void put(Voxels v, int u, int h, int w, String state) {
        v.set(CENTER + u, h, CENTER + w, state);
    }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    // ---- foundation -----------------------------------------------------------------------------------------

    private static void foundation(Voxels v) {
        for (int u = -6; u <= 6; u++) {
            for (int w = -6; w <= 6; w++) {
                if (Math.abs(u) + Math.abs(w) > 10) continue;
                double r = hash(u, w, 1);
                put(v, u, 0, w, r < 0.70 ? MOSSY : r < 0.90 ? BRICK : CRACKED);
                boolean rim = Math.max(Math.abs(u), Math.abs(w)) == 6 && Math.abs(u) + Math.abs(w) <= 10;
                boolean gap = Math.abs(u) <= 1 || Math.abs(w) <= 1;       // open on the four axes
                if (rim && !gap) put(v, u, 1, w, SLAB);
            }
        }
    }

    // ---- trunk ----------------------------------------------------------------------------------------------

    private static void trunk(Voxels v) {
        for (int h = 1; h <= 14; h++) {
            int r2 = h <= 4 ? 7 : h <= 9 ? 4 : 2;
            for (int u = -2; u <= 2; u++) {
                for (int w = -2; w <= 2; w++) {
                    if (u * u + w * w <= r2) put(v, u, h, w, LOG_Y);
                }
            }
        }
        // The leader keeps climbing, slimmer, through the middle of the crown.
        for (int h = 15; h <= 19; h++) {
            put(v, 0, h, 0, LOG_Y);
            put(v, 1, h, 0, LOG_Y);
            put(v, -1, h, 0, LOG_Y);
            put(v, 0, h, 1, LOG_Y);
            put(v, 0, h, -1, LOG_Y);
        }
        for (int h = 20; h <= 26; h++) put(v, 0, h, 0, LOG_Y);
        // Crown limbs: short logs reaching out from the top so no leaf is far from wood.
        for (int k = 1; k <= 3; k++) {
            put(v, k, 25, 0, LOG_X);
            put(v, -k, 25, 0, LOG_X);
            put(v, 0, 25, k, LOG_Z);
            put(v, 0, 25, -k, LOG_Z);
        }
    }

    // ---- roots ----------------------------------------------------------------------------------------------

    /** Cells of one diagonal root in (a, b) = (|u|, |w|) space, each reached from the previous by one step. */
    private static final int[][] ROOT = {{3, 1}, {3, 2}, {4, 2}, {4, 3}, {5, 3}, {5, 4}, {5, 5}};

    private static void roots(Voxels v) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int i = 0; i < ROOT.length; i++) {
                    int a = ROOT[i][0], b = ROOT[i][1];
                    boolean alongA = i == 0 || ROOT[i][0] != ROOT[i - 1][0];
                    String log = alongA ? LOG_X : LOG_Z;
                    if (i == ROOT.length - 1) log = alongA ? STRIPPED_X : STRIPPED_Z;
                    put(v, sx * a, 1, sz * b, log);
                    if (i < 4) put(v, sx * a, 2, sz * b, log);        // thick near the trunk, low at the tip
                }
            }
        }
    }

    // ---- chest recess ---------------------------------------------------------------------------------------

    private static void chestRecess(Voxels v) {
        for (int w = 3; w <= 5; w++) {
            for (int h = 1; h <= 2; h++) {
                put(v, -1, h, w, LOG_Z);
                put(v, 1, h, w, LOG_Z);
            }
            put(v, -1, 3, w, LOG_Z);
            put(v, 0, 3, w, LOG_Z);
            put(v, 1, 3, w, LOG_Z);
        }
        put(v, 0, 1, 3, CHEST);
        for (int w = 3; w <= 5; w++) {
            for (int h = 1; h <= 2; h++) {
                if (!(w == 3 && h == 1)) put(v, 0, h, w, Voxels.AIR);
            }
        }
    }

    // ---- branches -------------------------------------------------------------------------------------------

    private enum Move { A, B, UP }

    private static void branches(Voxels v) {
        Move[] diagonal = {Move.A, Move.UP, Move.B, Move.UP};
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int a = 1, b = 1, h = 13;
                int froglight = 0;
                for (int i = 0; i < 17; i++) {
                    Move m = diagonal[i % 4];
                    String log;
                    switch (m) {
                        case A -> { a++; log = LOG_X; }
                        case B -> { b++; log = LOG_Z; }
                        default -> { h++; log = LOG_Y; }
                    }
                    put(v, sx * a, h, sz * b, log);
                    if (i < 8) put(v, sx * a, h - 1, sz * b, log);   // two thick near the trunk
                    if (i == 10 && froglight++ == 0) put(v, sx * a, h - 1, sz * b, FROGLIGHT);
                }
            }
        }
        // Four shorter limbs along the axes from the leader.
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int a = 1, h = 17;
            String log = d[0] != 0 ? LOG_X : LOG_Z;
            for (int i = 0; i < 7; i++) {
                if (i == 2 || i == 5) {
                    h++;
                    put(v, d[0] * a, h, d[1] * a, LOG_Y);
                } else {
                    a++;
                    put(v, d[0] * a, h, d[1] * a, log);
                }
            }
        }
    }

    // ---- crown ----------------------------------------------------------------------------------------------

    private static void crown(Voxels v) {
        blob(v, 0, 27, 0, 4.4, 3.2, 11);
        // Diagonal lobes at alternating heights leave the main limbs readable under the crown.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                boolean high = (sx + sz) == 0;
                blob(v, sx * 6, high ? 24 : 22, sz * 5, 3.6, 3.0, 12 + sx + 2 * sz);
            }
        }
        int[][] tips = {{5, 0}, {-5, 0}, {0, 5}, {0, -5}};
        for (int i = 0; i < tips.length; i++) blob(v, tips[i][0], i % 2 == 0 ? 21 : 20, tips[i][1], 3.2, 2.8, 30 + i);
    }

    /** An irregular ellipsoid of persistent leaves; never overwrites logs. */
    private static void blob(Voxels v, int cu, int ch, int cw, double ru, double rh, int salt) {
        int ext = (int) Math.ceil(Math.max(ru, rh));
        for (int du = -ext; du <= ext; du++) {
            for (int dh = -ext; dh <= ext; dh++) {
                for (int dw = -ext; dw <= ext; dw++) {
                    double d = (du * du) / (ru * ru) + (dh * dh) / (rh * rh) + (dw * dw) / (ru * ru);
                    if (d > 1.0) continue;
                    if (d > 0.72 && hash(cu + du, ch + dh + salt, cw + dw) < 0.18) continue;   // ragged edge
                    int x = CENTER + cu + du, y = ch + dh, z = CENTER + cw + dw;
                    if (!v.inBounds(x, y, z) || v.has(x, y, z)) continue;
                    v.set(x, y, z, LEAVES);
                }
            }
        }
    }
}
