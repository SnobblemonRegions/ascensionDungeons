package com.ascensiondungeons.outbreak;

import java.util.Random;

/**
 * The rarity of a dungeon loot chest. Colours follow AscensionLib's {@code design/rarity-colors.json} accents so a rarity is the
 * same colour everywhere. {@code modelId} is the custom-model number a resource pack maps to that rarity's chest model; without
 * the pack the chest is told apart by its label, its floor block and its particles.
 */
public enum ChestRarity {
    COMMON("Common", 0xC1C5CF, 50, "minecraft:iron_block", 9101),
    UNCOMMON("Uncommon", 0x1BD7E8, 30, "minecraft:diamond_block", 9102),
    RARE("Rare", 0x38CCEF, 15, "minecraft:lapis_block", 9103),
    EPIC("Epic", 0xAF69FF, 5, "minecraft:amethyst_block", 9104);

    private final String label;
    private final int colour;
    private final int weight;
    private final String floorBlock;
    private final int modelId;

    ChestRarity(String label, int colour, int weight, String floorBlock, int modelId) {
        this.label = label;
        this.colour = colour;
        this.weight = weight;
        this.floorBlock = floorBlock;
        this.modelId = modelId;
    }

    public String label() { return label; }
    /** 0xRRGGBB. */
    public int colour() { return colour; }
    public String floorBlock() { return floorBlock; }
    public int modelId() { return modelId; }
    public String lootTable() { return "ascensiondungeons:chests/rarity/" + name().toLowerCase(java.util.Locale.ROOT); }

    /** One rarity better, or this one at the top. */
    public ChestRarity upgraded() { return this == EPIC ? EPIC : values()[ordinal() + 1]; }

    /** This rarity raised {@code steps} times. */
    public ChestRarity upgraded(int steps) {
        ChestRarity r = this;
        for (int i = 0; i < steps; i++) r = r.upgraded();
        return r;
    }

    /** A weighted roll. Each point of {@code luck} moves 6 weight from Common to the better rarities, so luck never removes Common entirely. */
    public static ChestRarity roll(Random rng, int luck) {
        int shift = Math.min(40, 6 * Math.max(0, luck));
        int[] w = {COMMON.weight - shift, UNCOMMON.weight + shift / 2, RARE.weight + shift / 3, EPIC.weight + shift - shift / 2 - shift / 3};
        int total = 0;
        for (int x : w) total += x;
        int roll = rng.nextInt(total);
        for (int i = 0; i < w.length; i++) {
            if (roll < w[i]) return values()[i];
            roll -= w[i];
        }
        return COMMON;
    }
}
