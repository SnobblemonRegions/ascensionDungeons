package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Reliquary Crypt: the Crimson Cathedral loot landmark. The Rootbound Cache alcove in red nether brick and blackstone, with
 * a gilded keystone over the arch, two crying-obsidian gargoyle heads weeping either side of it, and a pinnacle of nether
 * brick walls tipped with a shroomlight at each front corner. The chest stands on its ledge in line with the arch.
 */
public final class ReliquaryCrypt {
    private ReliquaryCrypt() {}

    public static final String ID = "crimson_reliquary_crypt";

    private static final String WALL = "minecraft:red_nether_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:red_nether_bricks", "minecraft:nether_bricks", "minecraft:polished_blackstone_bricks", "minecraft:gilded_blackstone",
            "minecraft:shroomlight", null, ReliquaryCrypt::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Gilded keystone and crying-obsidian gargoyles on the front face.
        box(v, 6, 6, 10, 8, 6, 10, "minecraft:gilded_blackstone");
        v.set(7, 7, 10, "minecraft:gilded_blackstone");
        v.set(3, 4, 10, "minecraft:crying_obsidian");
        v.set(11, 4, 10, "minecraft:crying_obsidian");
        // Pinnacles at the front corners.
        for (int x : new int[] {0, 14}) {
            for (int y = 1; y <= 6; y++) v.set(x, y, 10, y <= 3 ? "minecraft:polished_blackstone_bricks" : "minecraft:red_nether_bricks");
            for (int y = 7; y <= 10; y++) v.set(x, y, 10, WALL);
            v.set(x, 11, 10, "minecraft:shroomlight");
        }
    }
}
