package com.ascensiondungeons.gen;

/** Smooth 2D value noise in [0, 1), a pure function of (seed, x, z). */
public final class Noise {
    private Noise() {}

    public static double value(long seed, double x, double z) {
        long xi = (long) Math.floor(x);
        long zi = (long) Math.floor(z);
        double fx = smooth(x - xi);
        double fz = smooth(z - zi);
        double a = corner(seed, xi, zi);
        double b = corner(seed, xi + 1, zi);
        double c = corner(seed, xi, zi + 1);
        double d = corner(seed, xi + 1, zi + 1);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double corner(long seed, long x, long z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
