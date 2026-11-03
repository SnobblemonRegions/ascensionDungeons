package com.ascensiondungeons.gen.landmark;

/**
 * Sluice Walk: the Flooded Ruins exploration landmark. A nine-block stone walkway runs east to west between two water
 * channels (carved by the layout, three wide each), lined by an arcade of small sluice arches on stout piers with a
 * continuous low parapet between them. The water stays below the route; every arch is whole.
 *
 * <p>Module coordinates: x in [0, 28], z in [0, 14], y in [0, 7]; y=0 is the floor row. The channels are z 0..2 and
 * z 12..14 and are left untouched (null) so the layout's water shows through; the walkway is z 3..11.
 */
public final class SluiceWalk {
    private SluiceWalk() {}

    public static final String ID = "flooded_sluice_walk";
    public static final int SIZE_X = 29;
    public static final int SIZE_Y = 8;
    public static final int SIZE_Z = 15;
    public static final int ORIGIN_Y = 4;
    public static final int WALK_Z0 = 3, WALK_Z1 = 11;

    private static final String BRICK = "minecraft:stone_bricks";
    private static final String MOSSY = "minecraft:mossy_stone_bricks";
    private static final String PRIS = "minecraft:prismarine_bricks";
    private static final String DARK = "minecraft:dark_prismarine";
    private static final String COPPER = "minecraft:oxidized_cut_copper";
    private static final String LANTERN = "minecraft:sea_lantern";
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

    private static String masonry(int x, int y, int z) {
        double r = hash(x, y, z);
        return r < 0.30 ? MOSSY : r < 0.42 ? PRIS : BRICK;
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        // The walkway: stone with a prismarine inlay down the middle and dark edges.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = WALK_Z0; z <= WALK_Z1; z++) {
                String s = z == WALK_Z0 || z == WALK_Z1 ? DARK : z == 7 && x % 2 == 0 ? PRIS : masonry(x, 0, z);
                v.set(x, 0, z, s);
            }
        }
        for (int z : new int[] {WALK_Z0, WALK_Z1}) {
            // Continuous low parapet.
            for (int x = 0; x < SIZE_X; x++) v.set(x, 1, z, SLAB);
            // Piers every seven blocks, with a copper cap and a lantern, and an arch spanning each bay.
            for (int px = 0; px < SIZE_X; px += 7) {
                for (int y = 1; y <= 4; y++) v.set(px, y, z, masonry(px, y, z));
                v.set(px, 5, z, COPPER);
                v.set(px, 6, z, LANTERN);
                if (px + 7 >= SIZE_X) continue;
                for (int dx = 1; dx <= 6; dx++) {
                    v.set(px + dx, 4, z, masonry(px + dx, 4, z));                       // lintel
                    if (dx == 1 || dx == 6) v.set(px + dx, 3, z, masonry(px + dx, 3, z)); // stepped springers
                    if (dx >= 2 && dx <= 5) v.set(px + dx, 5, z, dx == 2 || dx == 5 ? DARK : COPPER); // crown
                }
            }
        }
        return v;
    }
}
