package com.ascensiondungeons.gen.landmark;

/**
 * Cinder Crown: the Ashen Caldera navigation landmark (landmark atlas). A squat blackstone cone with a recessed ember bowl
 * at its top and a flat viewing apron around its foot. It stands on the Rootheart Grove socket, so it keeps that
 * socket's 21 x 21 footprint (the atlas target of 25 x 23 would not clear the routes there).
 *
 * <p>Module coordinates: x, z in [0, 20] with the axis on (10, 10); y in [0, 19], y=0 the floor row. The cone is solid, so
 * nothing in it is unsupported, and the ember bowl is sealed in the cone's top where no one can step in.
 */
public final class CinderCrown {
    private CinderCrown() {}

    public static final String ID = "ashen_cinder_crown";
    public static final int SIZE_XZ = 21;
    public static final int SIZE_Y = 20;
    public static final int CENTER = 10;
    public static final int HEIGHT = 16;
    public static final double BASE_RADIUS = 8.8;
    public static final double APRON_RADIUS = 10.4;

    private static final String BLACKSTONE = "minecraft:blackstone";
    private static final String BASALT = "minecraft:basalt[axis=y]";
    private static final String SMOOTH = "minecraft:smooth_basalt";
    private static final String BANDING = "minecraft:polished_blackstone_bricks";
    private static final String CRACKED = "minecraft:cracked_polished_blackstone_bricks";
    private static final String EMBER = "minecraft:magma_block";
    private static final String GLOW = "minecraft:ochre_froglight[axis=y]";

    /** The cone with its axis on template (cx, cz). */
    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - CENTER, RootheartGrove.ORIGIN_Y, cz - CENTER, false, 0);
    }

    static double radiusAt(int h) { return BASE_RADIUS - 0.275 * h; }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_XZ, SIZE_Y, SIZE_XZ);
        for (int z = 0; z < SIZE_XZ; z++) {
            for (int x = 0; x < SIZE_XZ; x++) {
                double r = Math.hypot(x - CENTER, z - CENTER);
                if (r <= APRON_RADIUS) v.set(x, 0, z, BANDING);                         // flush viewing apron
                for (int h = 0; h <= HEIGHT; h++) {
                    if (r > radiusAt(h)) continue;
                    v.set(x, h, z, surface(x, h, z));
                }
                if (r <= 2.8) {
                    for (int h = HEIGHT - 2; h <= HEIGHT; h++) v.set(x, h, z, Voxels.AIR);   // the bowl
                    v.set(x, HEIGHT - 3, z, hash(x, 0, z) < 0.25 ? GLOW : EMBER);
                }
            }
        }
        return v;
    }

    private static String surface(int x, int h, int z) {
        if (h % 5 == 4) return BANDING;
        double r = hash(x, h, z);
        return r < 0.55 ? BLACKSTONE : r < 0.72 ? BASALT : r < 0.90 ? SMOOTH : CRACKED;
    }
}
