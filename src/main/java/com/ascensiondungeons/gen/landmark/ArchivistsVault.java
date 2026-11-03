package com.ascensiondungeons.gen.landmark;

/**
 * Archivist's Dry Vault: the Flooded Ruins loot landmark. The Rootbound Cache alcove in prismarine brick and mossy stone,
 * with oxidized copper trim framing the arch and capping the roof. The chest stands on its dry ledge in line with the
 * arch; the layout leaves a dry road to the front and no water on the approach.
 */
public final class ArchivistsVault {
    private ArchivistsVault() {}

    public static final String ID = "flooded_archivists_dry_vault";

    private static final String COPPER = "minecraft:oxidized_cut_copper";
    private static final String COPPER_SLAB = "minecraft:oxidized_cut_copper_slab[type=bottom,waterlogged=false]";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:prismarine_bricks", "minecraft:mossy_stone_bricks", "minecraft:dark_prismarine", COPPER,
            "minecraft:sea_lantern", null, ArchivistsVault::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Copper pilasters framing the arch on its front face, and a copper slab cap along the front edge of the roof.
        for (int y = 1; y <= 4; y++) {
            v.set(3, y, 10, COPPER);
            v.set(11, y, 10, COPPER);
        }
        for (int x = 0; x < RootboundCache.SIZE_X; x++) v.set(x, RootboundCache.roofHeight(x) + 1, 10, COPPER_SLAB);
    }
}
