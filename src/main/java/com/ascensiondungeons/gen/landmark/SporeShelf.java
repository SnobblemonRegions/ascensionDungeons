package com.ascensiondungeons.gen.landmark;

/**
 * Spore Shelf: the Fungal Grotto exploration landmark. A bank of connected moss and mycelium terraces on both sides of a
 * broad, winding, ground-level path, stepping up three levels away from it, with mushrooms of several heights on the
 * terraces. The path needs no jumping: the lowest terrace is a shallow step you can walk up, and every cap is far above
 * head height.
 *
 * <p>Module coordinates: x in [0, 26] along the path, z in [0, 16], y in [0, 14]; y=0 is the floor row. The path is
 * {@link #PATH_HALF}*2+1 wide, centred on {@link #pathZ(int)}.
 */
public final class SporeShelf {
    private SporeShelf() {}

    public static final String ID = "fungal_spore_shelf";
    public static final int SIZE_X = 27, SIZE_Y = 15, SIZE_Z = 17;
    public static final int ORIGIN_Y = 4;
    public static final int PATH_HALF = 3;

    private static final String STEM = "minecraft:mushroom_stem";
    private static final String RED = "minecraft:red_mushroom_block";
    private static final String BROWN = "minecraft:brown_mushroom_block";
    private static final String RED_UNDER = "minecraft:red_mushroom_block[down=false,up=true,north=true,south=true,east=true,west=true]";
    private static final String BROWN_UNDER = "minecraft:brown_mushroom_block[down=false,up=true,north=true,south=true,east=true,west=true]";
    private static final String MYCELIUM = "minecraft:mycelium";
    private static final String MOSS = "minecraft:moss_block";
    private static final String MUD = "minecraft:packed_mud";
    private static final String MUD_BRICKS = "minecraft:mud_bricks";
    private static final String LIGHT = "minecraft:shroomlight";

    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, ORIGIN_Y, z, false, 0);
    }

    /** Centre line of the path at module x. */
    public static int pathZ(int x) {
        return (int) Math.round(8 + 2.6 * Math.sin(x * 2 * Math.PI / 20.0));
    }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Terrace height (blocks above the floor) at distance {@code dd} beyond the path edge. */
    static int terrace(int dd) {
        return dd < 2 ? 0 : dd < 4 ? 1 : dd < 6 ? 2 : 3;
    }

    private static void mushroom(Voxels v, int x, int z, int base, int height, int radius, boolean red) {
        for (int y = base + 1; y <= base + height; y++) v.setClipped(x, y, z, STEM);
        int top = base + height;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double d = Math.hypot(dx, dz);
                if (d > radius + 0.3) continue;
                boolean rim = d > radius - 1.1;
                v.setClipped(x + dx, top + 1, z + dz, rim ? (red ? RED_UNDER : BROWN_UNDER) : (dx + dz) % 4 == 0 ? STEM : red ? RED : BROWN);
                if (!rim) v.setClipped(x + dx, top + 2, z + dz, red ? RED : BROWN);
            }
        }
        v.setClipped(x, top + 1, z, STEM);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        int[] height = new int[SIZE_X * SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            int c = pathZ(x);
            for (int z = 0; z < SIZE_Z; z++) {
                int off = Math.abs(z - c);
                boolean path = off <= PATH_HALF;
                int h = path ? 0 : terrace(off - PATH_HALF - 1);
                height[x + z * SIZE_X] = h;
                v.set(x, 0, z, path ? (hash(x, 0, z) < 0.55 ? MUD : MUD_BRICKS) : MYCELIUM);
                for (int y = 1; y <= h; y++) v.set(x, y, z, y == h ? (hash(x, y, z) < 0.5 ? MOSS : MYCELIUM) : MOSS);
            }
        }
        // Mushrooms of several heights on the terraces, none within reach of the path.
        int[][] spots = {{2, 1}, {7, 0}, {13, 1}, {19, 0}, {24, 2}, {4, 15}, {10, 16}, {16, 15}, {22, 16}, {26, 14},
                {1, 13}, {12, 13}, {18, 2}, {8, 3}};
        for (int i = 0; i < spots.length; i++) {
            int x = spots[i][0], z = spots[i][1];
            int off = Math.abs(z - pathZ(x));
            if (off <= PATH_HALF + 3) continue;
            int base = height[x + z * SIZE_X];
            int h = 4 + (int) (hash(x, 1, z) * 5);
            mushroom(v, x, z, base, h, 1 + (int) (hash(x, 2, z) * 3), hash(x, 3, z) < 0.5);
        }
        // Amber lights recessed in the highest terrace faces.
        for (int x = 3; x < SIZE_X; x += 6) {
            for (int z : new int[] {0, SIZE_Z - 1}) if (height[x + z * SIZE_X] >= 3) v.set(x, 3, z, LIGHT);
        }
        return v;
    }
}
