package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Star Archive: the Lapis Observatory loot landmark. The Rootbound Cache alcove in dark deepslate tile and lapis, its front
 * piers lined with bookshelves, a gold constellation inlaid over the arch with a sea-lantern star, and a lapis crest along
 * the ridge. The chest stands on its ledge in line with the arch.
 */
public final class StarArchive {
    private StarArchive() {}

    public static final String ID = "lapis_star_archive";

    private static final String GOLD = "minecraft:gold_block";
    private static final String LAPIS = "minecraft:lapis_block";
    private static final String LANTERN = "minecraft:sea_lantern";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:deepslate_tiles", "minecraft:polished_deepslate", "minecraft:blue_terracotta", LAPIS, LANTERN, null,
            StarArchive::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Bookshelves in the front piers either side of the arch.
        for (int x : new int[] {1, 2, 12, 13}) for (int y = 1; y <= 4; y++) v.set(x, y, 10, "minecraft:bookshelf");
        // A gold constellation over the arch, with a lantern star.
        for (int[] s : new int[][] {{5, 7}, {7, 8}, {9, 7}, {6, 6}, {8, 6}}) v.set(s[0], s[1], 10, GOLD);
        v.set(7, 6, 10, LANTERN);
        // A lapis crest along the ridge.
        for (int z = 1; z <= 9; z += 2) box(v, 6, RootboundCache.roofHeight(6), z, 8, RootboundCache.roofHeight(8), z, LAPIS);
    }
}
