package com.ascensiondungeons.gen.landmark;

/**
 * Kiln Cache: the Ashen Caldera loot landmark. The Rootbound Cache alcove in dark brick, with a chimney mass above the
 * rear and two furnace recesses sealed behind glass in the front piers. The chest stands on a dry ledge in line with the
 * arch, as in the Verdant version.
 */
public final class KilnCache {
    private KilnCache() {}

    public static final String ID = "ashen_kiln_cache";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:polished_blackstone_bricks", "minecraft:blackstone", "minecraft:cracked_polished_blackstone_bricks",
            "minecraft:basalt[axis=y]", "minecraft:ochre_froglight[axis=y]", null, KilnCache::details);

    public static Landmark landmark() { return RootboundCache.landmark(ID, STYLE); }

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Chimney: a hollow 3 x 3 stack on the roof ridge at the back.
        for (int x = 6; x <= 8; x++) {
            for (int z = 1; z <= 3; z++) {
                boolean flue = x == 7 && z == 2;
                v.set(x, 11, z, flue ? Voxels.AIR : "minecraft:deepslate_bricks");
            }
        }
        // Furnace recesses: glowing blocks one layer behind glass, in the front piers.
        for (int x : new int[] {1, 13}) {
            for (int y = 2; y <= 3; y++) {
                v.set(x, y, 10, "minecraft:tinted_glass");
                v.set(x, y, 9, "minecraft:magma_block");
            }
        }
    }
}
