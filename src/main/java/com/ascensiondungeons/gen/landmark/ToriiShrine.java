package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;
import static com.ascensiondungeons.gen.landmark.Prims.hash;

/**
 * Torii Shrine: the Cherry Grove loot landmark. The Rootbound Cache alcove in cherry wood and pink terracotta, fronted by a
 * vermilion torii gate (two posts, a tie beam and a black cap) across the entrance, and a roof of cherry leaves that sheds
 * petals. The chest stands on its ledge in line with the arch.
 */
public final class ToriiShrine {
    private ToriiShrine() {}

    public static final String ID = "cherry_torii_shrine";

    private static final String RED = "minecraft:red_concrete";
    private static final String BLACK = "minecraft:blackstone";
    private static final String LEAVES = "minecraft:cherry_leaves[distance=1,persistent=true,waterlogged=false]";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:cherry_planks", "minecraft:pink_terracotta", "minecraft:stripped_cherry_wood",
            "minecraft:white_terracotta", "minecraft:pearlescent_froglight[axis=y]", null, ToriiShrine::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // The torii: posts at x=3 and x=11 across the front face, tie beam at y=7 and a black cap at y=8.
        for (int x : new int[] {3, 11}) for (int y = 1; y <= 7; y++) box(v, x, y, 10, x, y, 10, RED);
        box(v, 2, 7, 10, 12, 7, 10, RED);
        box(v, 0, 8, 10, 14, 8, 10, BLACK);
        // Cherry leaves over the roof: they shed pink petals.
        for (int z = 0; z <= 9; z++) {
            for (int x = 1; x <= 13; x++) {
                int roof = RootboundCache.roofHeight(x);
                if (roof + 1 < v.sizeY() && hash(x, 0, z, 31) < 0.55) v.set(x, roof + 1, z, LEAVES);
            }
        }
    }
}
