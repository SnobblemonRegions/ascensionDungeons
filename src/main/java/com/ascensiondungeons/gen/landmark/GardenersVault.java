package com.ascensiondungeons.gen.landmark;

/**
 * Gardener's Vault: the Sunken Garden loot landmark. The Rootbound Cache alcove in pale quartz and calcite, strictly
 * symmetrical, with a roof of flowering azalea and two recessed lights instead of four.
 */
public final class GardenersVault {
    private GardenersVault() {}

    public static final String ID = "sunken_gardeners_vault";

    private static final String FLOWER = "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String LEAF = "minecraft:azalea_leaves[distance=1,persistent=true,waterlogged=false]";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:quartz_bricks", "minecraft:calcite", "minecraft:smooth_quartz", "minecraft:mossy_stone_bricks",
            "minecraft:sea_lantern", null, GardenersVault::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Two lights, not four: the rear pair is plain wall.
        v.set(4, 5, 7, "minecraft:calcite");
        v.set(10, 5, 7, "minecraft:calcite");
        // The roof is a flowering hedge, symmetrical about the arch.
        for (int x = 0; x < RootboundCache.SIZE_X; x++) {
            for (int z = 0; z < RootboundCache.SIZE_Z; z++) {
                int mirror = Math.min(x, RootboundCache.SIZE_X - 1 - x);
                v.set(x, RootboundCache.roofHeight(x) + 1, z, (mirror + z) % 3 == 0 ? FLOWER : LEAF);
            }
        }
    }
}
