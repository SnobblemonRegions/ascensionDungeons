package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Purpur Vault: the End Spire loot landmark. The Rootbound Cache alcove in purpur and end stone brick with obsidian
 * accents, purpur pillars either side of the arch, a crying-obsidian keystone that weeps violet tears, and a chorus
 * plant with a grown flower on each end of the roof. The chest stands on its ledge in line with the arch.
 */
public final class PurpurVault {
    private PurpurVault() {}

    public static final String ID = "end_purpur_vault";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:purpur_block", "minecraft:end_stone_bricks", "minecraft:purpur_pillar[axis=y]", "minecraft:obsidian",
            "minecraft:end_rod[facing=down]", null, PurpurVault::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Pillars either side of the arch, a crying-obsidian keystone, an obsidian lintel course.
        for (int x : new int[] {2, 12}) box(v, x, 1, 10, x, 9, 10, "minecraft:purpur_pillar[axis=y]");
        box(v, 4, 6, 10, 10, 6, 10, "minecraft:obsidian");
        v.set(7, 7, 10, "minecraft:crying_obsidian");
        // A chorus plant at each end of the ridge.
        for (int x : new int[] {5, 9}) {
            int y = RootboundCache.roofHeight(x);
            v.set(x, y, 2, "minecraft:chorus_plant[down=true,east=false,north=false,south=false,up=true,west=false]");
            v.set(x, y + 1, 2, "minecraft:chorus_flower[age=5]");
        }
    }
}
