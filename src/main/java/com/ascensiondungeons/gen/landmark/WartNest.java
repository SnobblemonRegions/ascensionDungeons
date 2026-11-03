package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;
import static com.ascensiondungeons.gen.landmark.Prims.hash;

/**
 * Wart Nest: the Warped Hollow loot landmark. The Rootbound Cache alcove grown from warped wart block and planks, studded
 * with shroomlights, flanked by two warped-stem pillars with wart-block crowns, and overgrown on the roof with warped
 * fungus and nether sprouts. The chest stands on its ledge in line with the arch.
 */
public final class WartNest {
    private WartNest() {}

    public static final String ID = "warped_wart_nest";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:warped_wart_block", "minecraft:warped_planks", "minecraft:stripped_warped_stem[axis=y]", "minecraft:shroomlight",
            "minecraft:shroomlight", null, WartNest::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Stem pillars either side of the arch with wart-block crowns.
        for (int x : new int[] {1, 12}) {
            for (int y = 1; y <= 9; y++) box(v, x, y, 9, x + 1, y, 10, y % 4 == 0 ? "minecraft:stripped_warped_stem[axis=y]" : "minecraft:warped_stem[axis=y]");
            box(v, x - 1, 10, 8, x + 2, 10, 10, "minecraft:warped_wart_block");
            v.set(x, 11, 9, "minecraft:shroomlight");
        }
        // Fungus and sprouts over the roof.
        for (int z = 1; z <= 9; z++) {
            for (int x = 3; x <= 11; x++) {
                int y = RootboundCache.roofHeight(x) + 1;
                double h = hash(x, 0, z, 61);
                if (y < v.sizeY() && h < 0.25) v.set(x, y, z, h < 0.08 ? "minecraft:warped_fungus" : "minecraft:nether_sprouts");
            }
        }
    }
}
