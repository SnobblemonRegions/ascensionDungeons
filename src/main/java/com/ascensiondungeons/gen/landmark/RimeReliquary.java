package com.ascensiondungeons.gen.landmark;

/**
 * Rime Reliquary: the Glacial Vault loot landmark. The Rootbound Cache alcove in calcite, quartz and packed ice, capped
 * with snow, lit by two recessed pale lights instead of four.
 */
public final class RimeReliquary {
    private RimeReliquary() {}

    public static final String ID = "glacial_rime_reliquary";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:calcite", "minecraft:quartz_bricks", "minecraft:packed_ice", "minecraft:snow_block",
            "minecraft:sea_lantern", null, RimeReliquary::details);

    public static Landmark landmark() { return RootboundCache.landmark(ID, STYLE); }

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Two lights, not four: the rear pair is plain wall.
        v.set(4, 5, 7, "minecraft:calcite");
        v.set(10, 5, 7, "minecraft:calcite");
        // Snow over the roof.
        for (int x = 1; x <= 13; x++) {
            for (int z = 2; z <= 9; z++) {
                if (hash(x, z) < 0.65) v.set(x, RootboundCache.roofHeight(x) + 1, z, "minecraft:snow_block");
            }
        }
    }

    private static double hash(int a, int b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
