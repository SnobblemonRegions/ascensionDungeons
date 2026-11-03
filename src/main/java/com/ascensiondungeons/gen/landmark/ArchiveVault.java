package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Archive Vault: the Gilded Library loot landmark. The Rootbound Cache alcove in dark oak and spruce with bookshelf courses
 * in its walls, a gold-leaf lintel over the arch and a row of lit candles along the ridge. The chest stands on its ledge in line with the arch.
 */
public final class ArchiveVault {
    private ArchiveVault() {}

    public static final String ID = "library_archive_vault";

    private static final String GOLD = "minecraft:gold_block";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:dark_oak_planks", "minecraft:spruce_planks", "minecraft:bookshelf", "minecraft:stripped_dark_oak_log[axis=y]",
            "minecraft:glowstone", null, ArchiveVault::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // A gold-leaf lintel over the arch and bookshelf courses either side of it.
        box(v, 4, 6, 10, 10, 6, 10, GOLD);
        for (int x : new int[] {1, 2, 3, 11, 12, 13}) box(v, x, 1, 10, x, 4, 10, "minecraft:bookshelf");
        // Lit candles along the ridge.
        for (int z = 1; z <= 9; z += 2) {
            v.set(7, RootboundCache.roofHeight(7) + 1, z, "minecraft:candle[candles=3,lit=true,waterlogged=false]");
        }
    }
}
