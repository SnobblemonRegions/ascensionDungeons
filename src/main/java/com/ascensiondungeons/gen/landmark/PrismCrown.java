package com.ascensiondungeons.gen.landmark;

/**
 * Prism Crown: the Crystal Cavern navigation landmark. A fan of thick, faceted amethyst wedges growing together from one
 * calcite base. Every wedge starts inside the base and tapers to a point, so the whole thing is a single connected mass.
 * The layout runs a road ring round the base.
 *
 * <p>Module: 29 x 26 x 17. The base ellipse is centred on module (14, 8); y=0 is the floor row.
 */
public final class PrismCrown {
    private PrismCrown() {}

    public static final String ID = "crystal_prism_crown";
    public static final int SIZE_X = 29, SIZE_Y = 26, SIZE_Z = 17;
    public static final int CX = 14, CZ = 8;

    private static final String AMETHYST = "minecraft:amethyst_block";
    private static final String BUDDING = "minecraft:budding_amethyst";
    private static final String CALCITE = "minecraft:calcite";
    private static final String BASALT = "minecraft:smooth_basalt";
    private static final String LANTERN = "minecraft:sea_lantern";
    private static final String CLUSTER_UP = "minecraft:amethyst_cluster[facing=up,waterlogged=false]";
    private static final String LARGE_BUD = "minecraft:large_amethyst_bud[facing=up,waterlogged=false]";

    /** Wedge: base x, base z, height, base radius, x-lean per block of height, z-lean per block of height. */
    private record Wedge(double bx, double bz, int h, double r, double lx, double lz) {}

    private static final Wedge[] WEDGES = {
            new Wedge(14, 8, 25, 3.4, 0.00, -0.04),
            new Wedge(10, 8, 20, 3.0, -0.22, 0.02),
            new Wedge(18, 8, 20, 3.0, 0.22, 0.02),
            new Wedge(7, 9, 15, 2.6, -0.34, 0.05),
            new Wedge(21, 9, 15, 2.6, 0.34, 0.05),
            new Wedge(4, 8, 9, 2.0, -0.38, 0.0),
            new Wedge(24, 8, 9, 2.0, 0.38, 0.0),
            new Wedge(12, 5, 11, 2.2, -0.08, -0.22),
            new Wedge(16, 5, 11, 2.2, 0.08, -0.22),
            new Wedge(14, 11, 13, 2.4, 0.0, 0.12),
    };

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - CX, 4, cz - CZ, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        // Base: a low calcite mound with a dark basalt skirt.
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                double d = Math.hypot((x - CX) / 14.0, (z - CZ) / 8.0);
                if (d > 1) continue;
                v.set(x, 0, z, d > 0.82 ? BASALT : CALCITE);
                if (d <= 0.82) v.set(x, 1, z, hash(x, 1, z) < 0.5 ? CALCITE : BASALT);
                if (d <= 0.55) v.set(x, 2, z, CALCITE);
            }
        }
        // Wedges: round-ish prisms that shrink with height.
        for (Wedge w : WEDGES) {
            for (int y = 1; y <= w.h(); y++) {
                double t = (double) y / w.h();
                double r = Math.max(0.55, w.r() * (1 - t * t * 0.9) * (1 - 0.35 * t));
                double cx = w.bx() + w.lx() * y, cz = w.bz() + w.lz() * y;
                int x0 = (int) Math.floor(cx - r - 1), x1 = (int) Math.ceil(cx + r + 1);
                int z0 = (int) Math.floor(cz - r - 1), z1 = (int) Math.ceil(cz + r + 1);
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        // Octagonal section: faceted rather than round.
                        double dx = Math.abs(x - cx), dz = Math.abs(z - cz);
                        if (Math.max(dx, dz) > r || dx + dz > r * 1.45) continue;
                        double q = hash(x, y, z);
                        String block = q < 0.07 ? BUDDING : q < 0.10 && y > 4 && y < w.h() - 1 ? LANTERN : AMETHYST;
                        v.setClipped(x, y, z, block);
                    }
                }
                // The spine never breaks: step through the previous layer's column on the way to this layer's.
                double pcx = w.bx() + w.lx() * (y - 1), pcz = w.bz() + w.lz() * (y - 1);
                v.setClipped((int) Math.round(pcx), y, (int) Math.round(pcz), AMETHYST);
                v.setClipped((int) Math.round(cx), y, (int) Math.round(pcz), AMETHYST);
                v.setClipped((int) Math.round(cx), y, (int) Math.round(cz), AMETHYST);
            }
            // Pointed tip.
            int ty = w.h() + 1;
            int tx = (int) Math.round(w.bx() + w.lx() * w.h()), tz = (int) Math.round(w.bz() + w.lz() * w.h());
            if (v.inBounds(tx, ty, tz)) v.set(tx, ty, tz, AMETHYST);
        }
        // Small buds and clusters growing on the base ledge, so the root of the fan reads as a geode.
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                double d = Math.hypot((x - CX) / 14.0, (z - CZ) / 8.0);
                if (d < 0.5 || d > 0.95) continue;
                int top = d <= 0.82 ? 1 : 0;
                if (v.has(x, top + 1, z) || !v.has(x, top, z)) continue;
                double q = hash(x, 9, z);
                if (q < 0.22) v.set(x, top + 1, z, CLUSTER_UP);
                else if (q < 0.30) v.set(x, top + 1, z, LARGE_BUD);
            }
        }
        return v;
    }
}
