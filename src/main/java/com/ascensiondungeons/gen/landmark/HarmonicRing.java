package com.ascensiondungeons.gen.landmark;

/**
 * Harmonic Ring: the Crystal Cavern raid arena. The Warden's Ring layout in pale tuff and calcite with thin concentric
 * calcite rings inlaid in the floor, amethyst seating edges and a geode crescent of crystals along the back wall. The
 * 26-block combat disc stays flat and clear.
 */
public final class HarmonicRing {
    private HarmonicRing() {}

    public static final String ID = "crystal_harmonic_ring";

    private static final String AMETHYST = "minecraft:amethyst_block";
    private static final String CLUSTER = "minecraft:amethyst_cluster[facing=up,waterlogged=false]";
    private static final String BUD = "minecraft:large_amethyst_bud[facing=up,waterlogged=false]";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:polished_tuff", "minecraft:chiseled_tuff", AMETHYST, "minecraft:calcite",
            "minecraft:polished_tuff_slab[type=bottom,waterlogged=false]", "minecraft:tuff_bricks",
            "minecraft:chiseled_tuff", "minecraft:polished_tuff", "minecraft:sea_lantern");

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return new Landmark(ID, build(doorDeg), cx - WardensRing.SIZE / 2, WardensRing.ORIGIN_Y, cz - WardensRing.SIZE / 2, false, 0);
    }

    public static Voxels build(double doorDeg) {
        Voxels v = WardensRing.build(STYLE, doorDeg);
        int c = WardensRing.SIZE / 2;
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                double r = Math.hypot(x - c, z - c);
                // Thin concentric rings in the floor, flush, only where the base arena left the floor alone.
                if ((Math.abs(r - 6.0) < 0.55 || Math.abs(r - 9.0) < 0.55) && v.get(x, 0, z) == null) {
                    v.set(x, 0, z, "minecraft:calcite");
                }
                // Amethyst along the outer seating edge.
                if (r > 19.0 && r <= 20.0 && STYLE.step().equals(v.get(x, 1, z)) && hash(x, 1, z) < 0.45) v.set(x, 1, z, AMETHYST);
            }
        }
        // Crystals growing along the crescent's lintel.
        for (int y = 1; y < v.sizeY() - 1; y++) {
            for (int z = 0; z < v.sizeZ(); z++) {
                for (int x = 0; x < v.sizeX(); x++) {
                    if (!STYLE.lintelHigh().equals(v.get(x, y, z)) || y < 8 || v.has(x, y + 1, z)) continue;
                    double q = hash(x, y, z);
                    if (q < 0.55) v.set(x, y + 1, z, q < 0.25 ? CLUSTER : BUD);
                }
            }
        }
        return v;
    }
}
