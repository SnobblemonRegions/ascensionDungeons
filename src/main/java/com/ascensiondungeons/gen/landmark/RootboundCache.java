package com.ascensiondungeons.gen.landmark;

/**
 * Rootbound Cache: the Verdant Canopy loot landmark (landmark atlas, entry 02). A masonry alcove built against the north
 * wall of the habitat, with a stepped arch facing south and a chamber behind it. The chest stands on a one-block ledge
 * in line with the arch so it can be seen from the approach. Oak roots wrap the roof.
 *
 * <p>Module coordinates: x in [0, 14], z in [0, 10] (z=0 is the north end, against the wall), y in [0, 11]; y=0 is the
 * floor row. The module replaces the old chest platform at template (47..49, 5, 86..88) and its chest marker.
 */
public final class RootboundCache {
    private RootboundCache() {}

    public static final String ID = "verdant_rootbound_cache";
    public static final int SIZE_X = 15;
    public static final int SIZE_Y = 12;
    public static final int SIZE_Z = 11;
    public static final int ORIGIN_X = 45;
    public static final int ORIGIN_Y = 4;
    public static final int ORIGIN_Z = 78;

    public static final String CHEST = "minecraft:chest[facing=south,type=single,waterlogged=false]";
    private static final String BRICK = "minecraft:stone_bricks";
    private static final String MOSSY = "minecraft:mossy_stone_bricks";
    private static final String CRACKED = "minecraft:cracked_stone_bricks";
    private static final String TUFF = "minecraft:tuff";
    private static final String LANTERN = "minecraft:sea_lantern";
    private static final String ROOT = "minecraft:oak_log[axis=z]";

    /** Chamber interior: x 3..11, z 3..8, y 1..4. The chest sits at x=7, z=4, y=2 on a ledge. */
    private static final int CH_X0 = 3, CH_X1 = 11, CH_Z0 = 3, CH_Z1 = 8, CH_H = 4;

    /**
     * Materials and finishing for one biome's version of the alcove. {@code root} is laid over the roof (null for none);
     * {@code extras} adds biome-specific details after the shell is built.
     */
    public record Style(String brick, String mossy, String cracked, String accent, String light, String root,
                        java.util.function.Consumer<Voxels> extras) {}

    private static final Style VERDANT = new Style(BRICK, MOSSY, CRACKED, TUFF, LANTERN, ROOT, null);

    public static Landmark landmark() {
        return landmark(ID, VERDANT);
    }

    /** The Verdant cache with its north-west corner at template (x, 4, z). */
    public static Landmark verdantAt(int x, int z) {
        return landmarkAt(ID, VERDANT, x, z);
    }

    public static Landmark landmark(String id, Style style) {
        return landmarkAt(id, style, ORIGIN_X, ORIGIN_Z);
    }

    /** The alcove with its north-west corner at template (x, 4, z). */
    public static Landmark landmarkAt(String id, Style style, int x, int z) {
        return new Landmark(id, build(style), x, ORIGIN_Y, z, false, 0);
    }

    /** Height of the stepped roof at module x. */
    static int roofHeight(int x) {
        return 6 + Math.min(Math.min(x, SIZE_X - 1 - x), 4);
    }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static String wall(Style s, int x, int y, int z) {
        double r = hash(x, y, z);
        return r < 0.40 ? s.mossy() : r < 0.52 ? s.cracked() : r < 0.62 ? s.accent() : s.brick();
    }

    public static Voxels build() {
        return build(VERDANT);
    }

    public static Voxels build(Style s) {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        // Floor of the whole footprint front half, then the solid mass with its stepped roof.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                v.set(x, 0, z, wall(s, x, 0, z));
                for (int y = 1; y <= roofHeight(x); y++) v.set(x, y, z, wall(s, x, y, z));
            }
        }
        // Chamber.
        for (int x = CH_X0; x <= CH_X1; x++) {
            for (int z = CH_Z0; z <= CH_Z1; z++) {
                for (int y = 1; y <= CH_H; y++) v.set(x, y, z, Voxels.AIR);
            }
        }
        // Arch opening through the two front layers (z 9 and 10): 7 wide at the base, stepping in toward the top.
        for (int z = 9; z <= 10; z++) {
            for (int x = 4; x <= 10; x++) for (int y = 1; y <= 3; y++) v.set(x, y, z, Voxels.AIR);
            for (int x = 5; x <= 9; x++) v.set(x, 4, z, Voxels.AIR);
            for (int x = 6; x <= 8; x++) v.set(x, 5, z, Voxels.AIR);
        }
        // The opening's top step is a blind notch in the chamber's front wall; the chamber ceiling stays at y=5.
        for (int x = 3; x <= 11; x++) for (int z = CH_Z0; z <= CH_Z1; z++) v.set(x, 5, z, wall(s, x, 5, z));
        for (int x = 6; x <= 8; x++) for (int z = 9; z <= 10; z++) v.set(x, 5, z, Voxels.AIR);
        // Chest ledge and chest.
        for (int x = 6; x <= 8; x++) for (int z = 3; z <= 4; z++) v.set(x, 1, z, s.brick());
        v.set(7, 2, 4, CHEST);
        // Recessed lanterns in the chamber ceiling.
        v.set(4, 5, 4, s.light());
        v.set(10, 5, 4, s.light());
        v.set(4, 5, 7, s.light());
        v.set(10, 5, 7, s.light());
        // Roots over the roof, running front to back, and a few draped down the end walls.
        if (s.root() != null) {
            for (int x : new int[] {2, 5, 9, 12}) {
                for (int z = 0; z < SIZE_Z; z++) v.set(x, roofHeight(x) + 1, z, s.root());
            }
        }
        if (s.extras() != null) s.extras().accept(v);
        // The old chest platform was within the footprint; its chest marker is replaced by the new chest above.
        return v;
    }
}
