package com.ascensiondungeons.gen.landmark;

/**
 * Sporekeeper's Cache: the Fungal Grotto loot landmark. The Rootbound Cache alcove in mud brick, moss and mushroom stem,
 * with a thick red cap over the roof, cream stem pilasters framing the arch and dark-oak posts either side. The chest
 * stands on its low shelf in line with the arch.
 */
public final class SporekeepersCache {
    private SporekeepersCache() {}

    public static final String ID = "fungal_sporekeepers_cache";

    private static final String STEM = "minecraft:mushroom_stem";
    private static final String RED = "minecraft:red_mushroom_block";
    private static final String OAK = "minecraft:dark_oak_log[axis=y]";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:mud_bricks", "minecraft:moss_block", STEM, "minecraft:dark_oak_planks", "minecraft:shroomlight", null,
            SporekeepersCache::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Cream stem pilasters framing the arch, dark-oak posts beyond them up to the roof.
        for (int y = 1; y <= 4; y++) {
            v.set(3, y, 10, STEM);
            v.set(11, y, 10, STEM);
        }
        for (int x : new int[] {2, 12}) for (int y = 1; y <= RootboundCache.roofHeight(x); y++) v.set(x, y, 10, OAK);
        // A thick cap over the roof: one layer everywhere and a second where the module is tall enough to hold it.
        for (int x = 0; x < RootboundCache.SIZE_X; x++) {
            for (int z = 0; z < RootboundCache.SIZE_Z; z++) {
                int top = RootboundCache.roofHeight(x);
                v.set(x, top + 1, z, RED);
                if (x >= 1 && x <= 13 && z >= 1 && z <= 9) v.setClipped(x, top + 2, z, (x + z) % 5 == 0 ? STEM : RED);
            }
        }
    }
}
