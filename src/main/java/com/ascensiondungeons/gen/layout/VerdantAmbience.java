package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;

import com.ascensiondungeons.gen.SpongeSchematic;

/**
 * Drifting pollen for the Verdant forest: a spore blossom hangs under a few of the leaf blocks of the canopy, which makes
 * the blossom's soft green particles fall through the forest. Placed as blocks, so nothing depends on a block update; a
 * blossom that loses its leaf above simply pops off, which is harmless.
 */
public final class VerdantAmbience {
    private VerdantAmbience() {}

    private static final int MIN_Y = 9, MAX_Y = 34;

    public static SpongeSchematic apply(SpongeSchematic base) {
        Canvas c = Canvas.of(base);
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    if (!c.get(x, y, z).contains("_leaves[") || !c.isAir(x, y - 1, z)) continue;
                    if (hash(x, y, z, 98) < 0.0009) c.set(x, y - 1, z, "minecraft:spore_blossom");
                }
            }
        }
        return c.toSchematic();
    }
}
