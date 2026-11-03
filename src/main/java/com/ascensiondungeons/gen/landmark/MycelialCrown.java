package com.ascensiondungeons.gen.landmark;

/**
 * Mycelial Crown: the Fungal Grotto navigation landmark. One enormous, low, broad red-and-brown cap on a stout stem that
 * flares into a terraced root base, with a colony of smaller capped mushrooms of different heights stepping down round it.
 * Shroomlights are tucked under the great cap.
 *
 * <p>Module coordinates: x in [0, 26], z in [0, 24], y in [0, 24]; y=0 is the floor row. Everything is one connected piece:
 * the stem rises from the terraces, the great cap rests on the stem top, and every small mushroom stands on the floor or a
 * terrace. The cap is a hollow shell, open underneath.
 */
public final class MycelialCrown {
    private MycelialCrown() {}

    public static final String ID = "fungal_mycelial_crown";
    public static final int SIZE_X = 27, SIZE_Y = 25, SIZE_Z = 25;
    public static final int ORIGIN_Y = 4;
    public static final int CENTER_X = 13, CENTER_Z = 12;
    /** Cap: a half ellipsoid, semi-axes {@code CAP_A} (horizontal) and {@code CAP_B} (vertical) from y = {@code CAP_Y}. */
    public static final int CAP_Y = 14;
    public static final double CAP_A = 13, CAP_B = 6, CAP_SHELL = 2;

    private static final String RED = "minecraft:red_mushroom_block";
    private static final String BROWN = "minecraft:brown_mushroom_block";
    private static final String STEM = "minecraft:mushroom_stem";
    private static final String RED_UNDER = "minecraft:red_mushroom_block[down=false,up=true,north=true,south=true,east=true,west=true]";
    private static final String BROWN_UNDER = "minecraft:brown_mushroom_block[down=false,up=true,north=true,south=true,east=true,west=true]";
    private static final String LIGHT = "minecraft:shroomlight";
    private static final String MYCELIUM = "minecraft:mycelium";
    private static final String MOSS = "minecraft:moss_block";
    private static final String PODZOL = "minecraft:podzol";

    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - CENTER_X, ORIGIN_Y, cz - CENTER_Z, false, 0);
    }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** A small capped mushroom: 1-wide stem and a flat dome, as module positions; the stem stands on {@code base}. */
    private static void small(Voxels v, int x, int z, int base, int height, int radius, boolean red) {
        for (int y = base + 1; y <= base + height; y++) v.setClipped(x, y, z, STEM);
        int top = base + height;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double d = Math.hypot(dx, dz);
                if (d > radius + 0.3) continue;
                int y = top + 1;
                boolean rim = d > radius - 1.1;
                String block = (dx + dz + x) % 4 == 0 && !rim ? STEM : red ? RED : BROWN;
                v.setClipped(x + dx, y, z + dz, rim ? (red ? RED_UNDER : BROWN_UNDER) : block);
                if (!rim && d <= radius - 1.1) v.setClipped(x + dx, y + 1, z + dz, red ? RED : BROWN);
            }
        }
        v.setClipped(x, top, z, STEM);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        // Terraced root base: two rings of mycelium, then podzol, round the stem.
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                double r = Math.hypot(x - CENTER_X, z - CENTER_Z);
                if (r <= 10.5) v.set(x, 0, z, r <= 7 ? PODZOL : MYCELIUM);
                if (r <= 10.5) v.set(x, 1, z, r <= 8 ? MOSS : MYCELIUM);
                if (r <= 8) v.set(x, 2, z, r <= 5.5 ? PODZOL : MYCELIUM);
            }
        }
        // Stem: stout, flaring toward the roots, from the top terrace to the cap shell.
        for (int y = 3; y <= CAP_Y + 3; y++) {
            double rad = 3.0 + Math.max(0, (7 - y) * 0.35);
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    if (Math.hypot(x - CENTER_X, z - CENTER_Z) <= rad) v.set(x, y, z, STEM);
                }
            }
        }
        // Roots: four short buttresses running out along the diagonals over the terraces.
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + 90 * k);
            for (int s = 3; s <= 9; s++) {
                int x = (int) Math.round(CENTER_X + s * Math.cos(a)), z = (int) Math.round(CENTER_Z + s * Math.sin(a));
                for (int y = 3; y <= Math.max(3, 8 - s); y++) v.set(x, y, z, BROWN);
            }
        }
        // The great cap: a hollow half-ellipsoid shell, red with brown patches and white spots, open beneath.
        for (int y = CAP_Y; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    double dx = (x - CENTER_X) / CAP_A, dz = (z - CENTER_Z) / CAP_A, dy = (y - CAP_Y + 0.5) / CAP_B;
                    double dxi = (x - CENTER_X) / (CAP_A - CAP_SHELL), dzi = (z - CENTER_Z) / (CAP_A - CAP_SHELL),
                            dyi = (y - CAP_Y + 0.5) / (CAP_B - CAP_SHELL);
                    boolean outer = dx * dx + dy * dy + dz * dz <= 1.0;
                    boolean inner = dxi * dxi + dyi * dyi + dzi * dzi <= 1.0;
                    if (!outer || inner) continue;
                    double h = hash(x, y, z);
                    String s = hash(x / 3, y, z / 3) < 0.28 ? BROWN : RED;
                    boolean top = !(((x - CENTER_X) * (x - CENTER_X) + (z - CENTER_Z) * (z - CENTER_Z)) < 4 && y > CAP_Y + 3);
                    if (h < 0.07 && top) s = STEM;
                    v.set(x, y, z, y == CAP_Y ? (s.equals(BROWN) ? BROWN_UNDER : RED_UNDER) : s);
                }
            }
        }
        // Shroomlights tucked under the shell.
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                for (int y = CAP_Y + 1; y < SIZE_Y - 1; y++) {
                    if (v.has(x, y, z) && !v.has(x, y - 1, z) && !v.get(x, y, z).equals(STEM) && hash(x, y, z + 77) < 0.14
                            && Math.hypot(x - CENTER_X, z - CENTER_Z) > 5) {
                        v.set(x, y - 1, z, LIGHT);
                    }
                    if (v.has(x, y, z) && !v.has(x, y - 1, z)) break;
                }
            }
        }
        // The colony: smaller mushrooms of different heights stepping down on the terraces and the floor beyond.
        small(v, 3, 3, 0, 5, 3, true);
        small(v, 22, 4, 0, 7, 3, false);
        small(v, 4, 20, 0, 4, 2, false);
        small(v, 23, 21, 0, 6, 3, true);
        small(v, 13, 22, 0, 3, 2, true);
        small(v, 6, 12, 0, 5, 2, false);
        small(v, 21, 11, 0, 4, 2, true);
        return v;
    }
}
