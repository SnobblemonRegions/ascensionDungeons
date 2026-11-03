package com.ascensiondungeons.gen.landmark;

/**
 * Riveter's Cache: the Ancient Foundry loot landmark. The Rootbound Cache alcove as a workshop bay in deepslate brick and
 * stone brick, with a complete copper-trimmed arch, riveted copper bands round the walls, iron-bar tool racks flanking
 * the doorway, warm froglight lamps recessed in the ceiling, and the chest on its low bench. No entities.
 */
public final class RiveterCache {
    private RiveterCache() {}

    public static final String ID = "foundry_riveters_cache";

    private static final String COPPER = "minecraft:cut_copper";
    private static final String BARS = "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]";
    private static final String IRON = "minecraft:iron_block";

    private static final RootboundCache.Style STYLE = new RootboundCache.Style(
            "minecraft:deepslate_bricks", "minecraft:stone_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:polished_andesite",
            "minecraft:ochre_froglight[axis=y]", null, RiveterCache::details);

    public static Landmark landmarkAt(int x, int z) { return RootboundCache.landmarkAt(ID, STYLE, x, z); }

    public static Voxels build() { return RootboundCache.build(STYLE); }

    private static void details(Voxels v) {
        // Copper pilasters framing the arch on the front face.
        for (int y = 1; y <= 4; y++) {
            v.set(3, y, 10, COPPER);
            v.set(11, y, 10, COPPER);
        }
        // Riveted bands: copper along the front face at the base and the eaves.
        for (int x = 0; x < RootboundCache.SIZE_X; x++) {
            v.set(x, 1, 10, x == 3 || x == 11 ? COPPER : v.get(x, 1, 10));
            if (x % 2 == 0) v.set(x, RootboundCache.roofHeight(x), 10, COPPER);
        }
        // Tool racks: iron bars standing against the front face either side of the pilasters, with an iron block anvil base.
        for (int x : new int[] {1, 13}) {
            v.set(x, 1, 10, IRON);
            v.set(x, 2, 10, BARS);
            v.set(x, 3, 10, BARS);
        }
        // A copper cap along the ridge.
        for (int x = 4; x <= 10; x++) v.set(x, RootboundCache.roofHeight(x) + 1, 5, COPPER);
    }
}
