package com.ascensiondungeons.gen.landmark;

/**
 * Glowcap Spring: the Verdant Canopy exploration landmark (landmark atlas, entry 03). The existing round pool gains a
 * tuff outcrop on its east shore with a contained waterfall, and three giant brown mushrooms of different heights on
 * the north shore. A dry recess at the south end of the outcrop holds the optional chest.
 *
 * <p>Module coordinates: x in [0, 19], z in [0, 17], y in [0, 15]; y=0 is the floor row. The pool centre is at module
 * (6, 12), template (193, 176). The approach (west shore) and the south shore are left untouched.
 */
public final class GlowcapSpring {
    private GlowcapSpring() {}

    public static final String ID = "verdant_glowcap_spring";
    public static final int SIZE_X = 20;
    public static final int SIZE_Y = 15;
    public static final int SIZE_Z = 18;
    public static final int ORIGIN_X = 187;
    public static final int ORIGIN_Y = 4;
    public static final int ORIGIN_Z = 164;

    public static final String CHEST = "minecraft:chest[facing=west,type=single,waterlogged=false]";
    private static final String SOURCE = "minecraft:water[level=0]";
    private static final String FALLING = "minecraft:water[level=8]";
    private static final String SPILL = "minecraft:water[level=1]";
    private static final String TUFF = "minecraft:tuff";
    private static final String TUFF_BRICKS = "minecraft:tuff_bricks";
    private static final String MOSS = "minecraft:moss_block";
    private static final String MOSSY_COBBLE = "minecraft:mossy_cobblestone";
    private static final String LANTERN = "minecraft:sea_lantern";
    private static final String STEM = "minecraft:mushroom_stem[down=true,east=true,north=true,south=true,up=true,west=true]";

    /** Module x of the falling water and the trough below it. */
    private static final int FALL_X = 15;
    private static final int Z_FROM = 11;
    private static final int Z_TO = 13;

    public static Landmark landmark() {
        return landmarkAt(ORIGIN_X, ORIGIN_Z);
    }

    /** The spring with its module corner at template (x, 4, z); the pool centre is module (6, 12). */
    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, ORIGIN_Y, z, true, 12);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        outcrop(v);
        waterfall(v);
        chestRecess(v);
        mushroom(v, 7, 3, 3, 12);
        mushroom(v, 12, 4, 3, 9);
        mushroom(v, 3, 4, 2, 6);
        return v;
    }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    // ---- outcrop --------------------------------------------------------------------------------------------

    /** Rows of z that the outcrop occupies at a height: wide at the foot, narrower toward the top. */
    static int[] zRange(int y) {
        if (y <= 2) return new int[] {7, 17};
        if (y <= 4) return new int[] {8, 16};
        if (y <= 6) return new int[] {9, 15};
        return new int[] {10, 14};
    }

    private static void outcrop(Voxels v) {
        for (int y = 1; y <= 7; y++) {
            int[] z = zRange(y);
            for (int x = FALL_X + 1; x <= 19; x++) {
                for (int zz = z[0]; zz <= z[1]; zz++) {
                    double r = hash(x, y, zz);
                    boolean top = y == 7;
                    String block = top ? (r < 0.6 ? MOSS : TUFF) : r < 0.12 ? MOSSY_COBBLE : r < 0.30 ? TUFF_BRICKS : TUFF;
                    v.set(x, y, zz, block);
                }
            }
        }
        // Lanterns flush with the face, either side of the falling water.
        v.set(FALL_X + 1, 4, 9, LANTERN);
        v.set(FALL_X + 1, 4, 15, LANTERN);
    }

    // ---- water ----------------------------------------------------------------------------------------------

    private static void waterfall(Voxels v) {
        for (int z = Z_FROM; z <= Z_TO; z++) {
            for (int x = FALL_X + 1; x <= FALL_X + 3; x++) v.set(x, 7, z, SOURCE);       // basin on top
            v.set(FALL_X, 7, z, SPILL);                                                 // lip it pours over
            for (int y = 1; y <= 6; y++) v.set(FALL_X, y, z, FALLING);                    // the fall
            for (int x = FALL_X - 2; x <= FALL_X; x++) v.set(x, 0, z, SOURCE);           // trough, joined to the pool
        }
        // Rim of the basin and of the lip.
        for (int x = FALL_X; x <= 19; x++) {
            v.set(x, 7, 10, TUFF_BRICKS);
            v.set(x, 7, 14, TUFF_BRICKS);
        }
        // The spill cells stand on the rim; they must not reach past it.
        v.set(FALL_X, 6, 10, TUFF);
        v.set(FALL_X, 6, 14, TUFF);
    }

    // ---- chest ----------------------------------------------------------------------------------------------

    /** A dry recess in the south end of the outcrop, open to the west. */
    private static void chestRecess(Voxels v) {
        v.set(FALL_X + 1, 1, 16, CHEST);
        v.set(FALL_X + 1, 2, 16, Voxels.AIR);
        v.set(FALL_X, 1, 16, Voxels.AIR);
        v.set(FALL_X, 2, 16, Voxels.AIR);
        v.set(FALL_X, 0, 16, TUFF_BRICKS);
    }

    // ---- mushrooms ------------------------------------------------------------------------------------------

    /** A giant brown mushroom: stem from the floor and a flat cap of the given radius, {@code height} above the floor. */
    private static void mushroom(Voxels v, int cx, int cz, int radius, int height) {
        int plate = height;
        for (int y = 1; y <= plate - 1; y++) v.set(cx, y, cz, STEM);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius + 1) continue;
                boolean edgeE = !inside(dx + 1, dz, radius), edgeW = !inside(dx - 1, dz, radius);
                boolean edgeS = !inside(dx, dz + 1, radius), edgeN = !inside(dx, dz - 1, radius);
                v.set(cx + dx, plate, cz + dz, cap(true, edgeE, edgeN, edgeS, edgeW));
                if ((edgeE || edgeW || edgeS || edgeN) && !(dx == 0 && dz == 0)) {
                    v.set(cx + dx, plate - 1, cz + dz, cap(false, edgeE, edgeN, edgeS, edgeW));
                }
            }
        }
    }

    private static boolean inside(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius + 1;
    }

    private static String cap(boolean up, boolean east, boolean north, boolean south, boolean west) {
        return "minecraft:brown_mushroom_block[down=false,east=" + east + ",north=" + north + ",south=" + south
                + ",up=" + up + ",west=" + west + "]";
    }
}
