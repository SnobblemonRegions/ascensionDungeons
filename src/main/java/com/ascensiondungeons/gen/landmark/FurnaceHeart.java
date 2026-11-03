package com.ascensiondungeons.gen.landmark;

/**
 * Furnace Heart: the Ancient Foundry navigation landmark. A massive squat deepslate smelter body with a stepped top, a
 * large round furnace mouth in its front face (a copper ring round a grating, amber froglight behind), thick ducts rising
 * from the body into a broad copper chimney that is built into the back wall, and copper bands round the body. A static
 * block sculpture: nothing moves and there is no fire.
 *
 * <p>Module coordinates: x in [0, 26], z in [0, 18] (z=0 against the back wall, z=18 the front), y in [0, 25]; y=0 is the
 * floor row. The front face is z=18; the apron in front of it is left to the layout.
 */
public final class FurnaceHeart {
    private FurnaceHeart() {}

    public static final String ID = "foundry_furnace_heart";
    public static final int SIZE_X = 27, SIZE_Y = 26, SIZE_Z = 19;
    public static final int ORIGIN_Y = 4;
    /** Furnace mouth centre on the front face and its outer radius. */
    public static final int MOUTH_X = 13, MOUTH_Y = 6;
    public static final double MOUTH_R = 5.2;

    private static final String DEEP = "minecraft:deepslate_bricks";
    private static final String TILES = "minecraft:deepslate_tiles";
    private static final String CRACKED = "minecraft:cracked_deepslate_bricks";
    private static final String ANDESITE = "minecraft:polished_andesite";
    private static final String COPPER = "minecraft:cut_copper";
    private static final String OXIDIZED = "minecraft:oxidized_cut_copper";
    private static final String IRON = "minecraft:iron_block";
    private static final String BARS = "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]";
    private static final String GLOW = "minecraft:ochre_froglight[axis=y]";
    public static final String CAMPFIRE = "minecraft:campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]";
    public static final String HAY = "minecraft:hay_block[axis=y]";

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

    private static String body(int x, int y, int z) {
        double r = hash(x, y, z);
        return r < 0.14 ? CRACKED : r < 0.30 ? TILES : DEEP;
    }

    /** Height of the body's top at module x: stepped, tallest in the middle. */
    static int bodyTop(int x) {
        int e = Math.min(x - 2, 24 - x);
        return e < 3 ? 9 : e < 6 ? 11 : 13;
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        // The body: x 2..24, z 5..18, stepped top, copper bands at y = 4 and 8.
        for (int x = 2; x <= 24; x++) {
            for (int z = 5; z <= 18; z++) {
                for (int y = 0; y <= bodyTop(x); y++) {
                    boolean band = (y == 4 || y == 8) && (x == 2 || x == 24 || z == 18 || z == 5);
                    v.set(x, y, z, band ? COPPER : y == 0 ? TILES : body(x, y, z));
                }
            }
        }
        // The chimney: a broad copper stack against the back wall, hollow at the top, from the floor to y=25.
        for (int x = 9; x <= 17; x++) {
            for (int z = 0; z <= 6; z++) {
                for (int y = 0; y <= 25; y++) {
                    boolean rim = x == 9 || x == 17 || z == 0 || z == 6;
                    boolean hollow = y >= 24 && !rim;
                    if (hollow) continue;
                    boolean ring = y % 6 == 5;
                    v.set(x, y, z, ring ? COPPER : (x + y) % 3 == 0 ? OXIDIZED : COPPER);
                }
            }
        }
        // The stack's smoke: a lit signal campfire on a hay bale at the bottom of the hollow top.
        v.set(13, 24, 3, HAY);
        v.set(13, 25, 3, CAMPFIRE);
        // Ducts: thick horizontal runs from the body top into the chimney on both sides, braced by an iron collar.
        for (int side : new int[] {-1, 1}) {
            int x0 = side < 0 ? 3 : 18, x1 = side < 0 ? 8 : 23;
            for (int x = x0; x <= x1; x++) {
                for (int z = 4; z <= 7; z++) {
                    for (int y = 14; y <= 17; y++) {
                        boolean edge = z == 4 || z == 7 || y == 14 || y == 17;
                        v.set(x, y, z, edge ? (x % 3 == 0 ? IRON : DEEP) : GLOW);
                    }
                }
            }
        }
        // The furnace mouth: a round opening in the front face, a copper ring, a grating and amber glow behind it.
        for (int x = 0; x < SIZE_X; x++) {
            for (int y = 0; y < SIZE_Y; y++) {
                double d = Math.hypot(x - MOUTH_X, y - MOUTH_Y);
                if (d > MOUTH_R) continue;
                for (int z = 14; z <= 18; z++) {
                    if (d >= MOUTH_R - 1.1) v.set(x, y, z, z >= 17 ? COPPER : DEEP);
                    else if (z == 18) v.set(x, y, z, BARS);
                    else v.set(x, y, z, z <= 16 ? GLOW : Voxels.AIR);
                }
            }
        }
        // Flanking pilasters on the front face and glowing slits in the side faces.
        for (int x : new int[] {3, 4, 22, 23}) for (int y = 1; y <= bodyTop(x); y++) v.set(x, y, 18, y % 4 == 0 ? COPPER : ANDESITE);
        for (int y : new int[] {6, 7}) {
            v.set(2, y, 11, GLOW);
            v.set(24, y, 11, GLOW);
        }
        return v;
    }
}
