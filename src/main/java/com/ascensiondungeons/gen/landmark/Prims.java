package com.ascensiondungeons.gen.landmark;

/**
 * Small building primitives for authoring landmark modules in code: boxes, discs, rings, cones and a seeded block
 * picker. Everything clips to the module, so shapes may be drawn up to its edge without bounds checks.
 */
public final class Prims {
    private Prims() {}

    public static double hash(int a, int b, int c, int salt) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** One of the weighted options, chosen by position: {@code pick(x, y, z, salt, "a", .6, "b", .4)}. */
    public static String pick(int x, int y, int z, int salt, Object... weighted) {
        double r = hash(x, y, z, salt), acc = 0;
        for (int i = 0; i < weighted.length; i += 2) {
            acc += (Double) weighted[i + 1];
            if (r < acc) return (String) weighted[i];
        }
        return (String) weighted[weighted.length - 2];
    }

    /** Fills the inclusive box with one state. */
    public static void box(Voxels v, int x0, int y0, int z0, int x1, int y1, int z1, String state) {
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) v.setClipped(x, y, z, state);
    }

    /** Fills the inclusive box with a position-picked state. */
    public static void boxPick(Voxels v, int x0, int y0, int z0, int x1, int y1, int z1, int salt, Object... weighted) {
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            v.setClipped(x, y, z, pick(x, y, z, salt, weighted));
        }
    }

    /** A solid disc (cylinder one layer high) of radius {@code r} centred on (cx, cz) at height y. */
    public static void disc(Voxels v, double cx, double cz, double r, int y, String state) {
        for (int z = (int) Math.floor(cz - r); z <= Math.ceil(cz + r); z++) {
            for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
                if (Math.hypot(x - cx, z - cz) <= r) v.setClipped(x, y, z, state);
            }
        }
    }

    /** A ring: cells whose distance from the centre lies in [r0, r1]. */
    public static void ring(Voxels v, double cx, double cz, double r0, double r1, int y, String state) {
        for (int z = (int) Math.floor(cz - r1); z <= Math.ceil(cz + r1); z++) {
            for (int x = (int) Math.floor(cx - r1); x <= Math.ceil(cx + r1); x++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d >= r0 && d <= r1) v.setClipped(x, y, z, state);
            }
        }
    }

    /** A vertical cylinder wall (a ring stacked from y0 to y1). */
    public static void tube(Voxels v, double cx, double cz, double r0, double r1, int y0, int y1, String state) {
        for (int y = y0; y <= y1; y++) ring(v, cx, cz, r0, r1, y, state);
    }

    /** A solid cone: radius shrinks linearly from {@code r0} at y0 to nothing above y1. */
    public static void cone(Voxels v, double cx, double cz, double r0, int y0, int y1, Object... weighted) {
        for (int y = y0; y <= y1; y++) {
            double r = r0 * (1 - (double) (y - y0) / (y1 - y0 + 1));
            for (int z = (int) Math.floor(cz - r); z <= Math.ceil(cz + r); z++) {
                for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
                    if (Math.hypot(x - cx, z - cz) <= r) v.setClipped(x, y, z, pick(x, y, z, 7, weighted));
                }
            }
        }
    }

    /** A vertical line of one state. */
    public static void column(Voxels v, int x, int y0, int y1, int z, String state) {
        for (int y = y0; y <= y1; y++) v.setClipped(x, y, z, state);
    }

    /** A straight line in the xz plane at height y (Bresenham-style stepping). */
    public static void line(Voxels v, int x0, int z0, int x1, int z1, int y, String state) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            v.setClipped((int) Math.round(x0 + (x1 - x0) * t), y, (int) Math.round(z0 + (z1 - z0) * t), state);
        }
    }

    private static final String[] BANDS = {"minecraft:terracotta", "minecraft:terracotta", "minecraft:orange_terracotta",
            "minecraft:orange_terracotta", "minecraft:white_terracotta", "minecraft:yellow_terracotta", "minecraft:yellow_terracotta",
            "minecraft:red_terracotta", "minecraft:red_terracotta", "minecraft:brown_terracotta", "minecraft:brown_terracotta",
            "minecraft:orange_terracotta"};

    /** The painted-desert strata: the terracotta colour of a rock layer at height y (a repeating twelve-block sequence). */
    public static String band(int y) {
        return BANDS[Math.floorMod(y, BANDS.length)];
    }

    /** True where the module has no block (null) or air. */
    public static boolean empty(Voxels v, int x, int y, int z) {
        String s = v.get(x, y, z);
        return s == null || s.equals(Voxels.AIR);
    }
}
