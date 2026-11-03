package com.ascensiondungeons.gen.landmark;

/**
 * Frostheart Arch: the Glacial Vault navigation landmark. One thick blue-ice arch standing on two broad snow banks, with a
 * 9-wide, 14-high opening a party can walk through. It stands on the Rootheart Grove socket (21 x 21 footprint).
 *
 * <p>Module coordinates: x, z in [0, 20]; y in [0, 23]; y=0 is the floor row. The arch lies in the x-y plane, centred on
 * x=10, 7 blocks thick at the base and 5 at the crown. The opening is carved explicitly.
 */
public final class FrostheartArch {
    private FrostheartArch() {}

    public static final String ID = "glacial_frostheart_arch";
    public static final int SIZE_XZ = 21;
    public static final int SIZE_Y = 24;
    public static final int CENTER = 10;
    public static final int OPENING_HALF = 4;     // x 6..14 is 9 wide
    public static final int OPENING_HEIGHT = 14;

    private static final String BLUE = "minecraft:blue_ice";
    private static final String PACKED = "minecraft:packed_ice";
    private static final String SNOW = "minecraft:snow_block";
    private static final String CALCITE = "minecraft:calcite";
    private static final String LANTERN = "minecraft:sea_lantern";

    /** The arch with its module corner at template (x, 4, z); the walking path runs through along z at x+6..x+14. */
    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, RootheartGrove.ORIGIN_Y, z, false, 0);
    }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static boolean inOuter(int x, int y) {
        double dx = (x - CENTER) / 10.4, dy = y / 22.5;
        return dx * dx + dy * dy <= 1;
    }

    private static boolean inOpening(int x, int y) {
        double dx = (x - CENTER) / 4.6, dy = y / 14.5;
        return dx * dx + dy * dy <= 1;
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_XZ, SIZE_Y, SIZE_XZ);
        // Banks first, so the arch overrides them where they meet.
        for (int z = 2; z <= 18; z++) {
            for (int x = 0; x <= 20; x++) {
                if (Math.abs(x - CENTER) <= OPENING_HALF) continue;
                double edge = Math.max(0, Math.abs(z - CENTER) - 2.5) * 0.4 + Math.max(0, Math.abs(x - CENTER) - 8.5) * 0.5;
                int height = (int) Math.floor(3.4 - edge);
                for (int y = 0; y <= height; y++) {
                    double r = hash(x, y, z);
                    v.set(x, y, z, y == height ? SNOW : r < 0.3 ? CALCITE : SNOW);
                }
            }
        }
        // The arch.
        for (int y = 0; y < SIZE_Y; y++) {
            int half = y < 10 ? 3 : 2;
            for (int x = 0; x <= 20; x++) {
                if (!inOuter(x, y) || inOpening(x, y)) continue;
                double dx = (x - CENTER) / 10.4, dy = y / 22.5;
                int band = (int) Math.floor(Math.sqrt(dx * dx + dy * dy) * 9);
                String block = band % 3 == 0 ? BLUE : PACKED;
                for (int z = CENTER - half; z <= CENTER + half; z++) v.set(x, y, z, block);
            }
        }
        // The path is clear: the whole 9-wide width up to head height, and the arched opening above it.
        for (int y = 1; y <= OPENING_HEIGHT; y++) {
            for (int x = CENTER - OPENING_HALF; x <= CENTER + OPENING_HALF; x++) {
                if (y > 4 && !inOpening(x, y)) continue;
                for (int z = 0; z < SIZE_XZ; z++) v.set(x, y, z, Voxels.AIR);
            }
        }
        // Two lights set into the legs' inner faces.
        v.set(CENTER - OPENING_HALF - 1, 5, CENTER, LANTERN);
        v.set(CENTER + OPENING_HALF + 1, 5, CENTER, LANTERN);
        return v;
    }
}
