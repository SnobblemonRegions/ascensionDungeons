package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.band;
import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Hoodoo Cache: the Painted Badlands loot landmark. The Rootbound Cache alcove cut into a banded rock: every layer of the
 * mass takes the colour of its stratum, two hoodoos with flat caps stand at the back corners, and a red-sand drift runs
 * across the front. The chest stands on its ledge in line with the arch.
 */
public final class HoodooCache {
    private HoodooCache() {}

    public static final String ID = "badlands_hoodoo_cache";

    private static final String LAMP = "minecraft:redstone_lamp[lit=true]";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:orange_terracotta", "minecraft:terracotta", "minecraft:yellow_terracotta", "minecraft:white_terracotta",
            LAMP, null, HoodooCache::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Repaint the masonry in strata.
        for (int y = 0; y < v.sizeY(); y++) {
            for (int z = 0; z < v.sizeZ(); z++) {
                for (int x = 0; x < v.sizeX(); x++) {
                    String s = v.get(x, y, z);
                    if (s != null && s.endsWith("terracotta")) v.set(x, y, z, band(y));
                }
            }
        }
        // Hoodoos at the back corners, with flat caps.
        for (int x : new int[] {0, 12}) {
            for (int y = 1; y <= 10; y++) box(v, x, y, 0, x + 2, y, 2, band(y + 2));
            box(v, x - 1, 11, 0, x + 3, 11, 3, "minecraft:red_terracotta");
        }
        // A red-sand drift on the front.
        for (int x = 0; x <= 14; x++) if (x < 4 || x > 10) v.set(x, 0, 10, "minecraft:red_sand");
    }
}
