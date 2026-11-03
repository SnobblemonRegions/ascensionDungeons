package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Sarcophagus Vault: the Sunscorched Necropolis loot landmark. The Rootbound Cache alcove in sandstone, with a gold lintel
 * course over the arch, two gold-capped obelisks flanking the entrance, a stepped gold cap on the ridge and a band of
 * chiseled hieroglyph blocks across the front. The chest stands on its ledge in line with the arch.
 */
public final class SarcophagusVault {
    private SarcophagusVault() {}

    public static final String ID = "necropolis_sarcophagus_vault";

    private static final String GOLD = "minecraft:gold_block";
    private static final String CHISELED = "minecraft:chiseled_sandstone";
    private static final String SMOOTH = "minecraft:smooth_sandstone";
    private static final String GLOW = "minecraft:glowstone";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:sandstone", "minecraft:cut_sandstone", SMOOTH, CHISELED, GLOW, null, SarcophagusVault::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Gold lintel course over the arch and a hieroglyph band across the front face.
        for (int x = 5; x <= 9; x++) v.set(x, 6, 10, GOLD);
        for (int x = 1; x <= 13; x++) if (x < 4 || x > 10) v.set(x, 3, 10, CHISELED);
        // Obelisks flanking the entrance.
        for (int x : new int[] {0, 13}) {
            for (int y = 1; y <= 9; y++) box(v, x, y, 9, x + 1, y, 10, y == 1 || y == 5 ? CHISELED : SMOOTH);
            box(v, x, 10, 9, x + 1, 10, 10, GOLD);
            v.set(x, 11, 9, GLOW);
        }
        // Stepped gold cap along the ridge.
        for (int z = 1; z <= 3; z++) {
            v.set(6, RootboundCache.roofHeight(6), z, GOLD);
            v.set(8, RootboundCache.roofHeight(8), z, GOLD);
            v.set(7, RootboundCache.roofHeight(7), z, GOLD);
            v.set(7, RootboundCache.roofHeight(7) + 1, z, GLOW);
        }
    }
}
