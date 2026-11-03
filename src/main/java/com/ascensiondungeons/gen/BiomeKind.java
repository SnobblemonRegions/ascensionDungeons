package com.ascensiondungeons.gen;

/**
 * The district biomes: the eight from the landmark atlas plus eight more, each with its own palette and room shape. Only those with a district template can be generated; see
 * {@code DungeonTemplates}.
 */
public enum BiomeKind {
    VERDANT_CANOPY("Verdant Canopy"),
    ASHEN_CALDERA("Ashen Caldera"),
    GLACIAL_VAULT("Glacial Vault"),
    CRYSTAL_CAVERN("Crystal Cavern"),
    FLOODED_RUINS("Flooded Ruins"),
    FUNGAL_GROTTO("Fungal Grotto"),
    SUNKEN_GARDEN("Sunken Garden"),
    ANCIENT_FOUNDRY("Ancient Foundry"),
    SUNSCORCHED_NECROPOLIS("Sunscorched Necropolis"),
    PAINTED_BADLANDS("Painted Badlands"),
    CHERRY_GROVE("Cherry Grove"),
    LAPIS_OBSERVATORY("Lapis Observatory"),
    CRIMSON_CATHEDRAL("Crimson Cathedral"),
    GILDED_LIBRARY("Gilded Library"),
    WARPED_HOLLOW("Warped Hollow"),
    END_SPIRE("End Spire");

    private final String displayName;

    BiomeKind(String displayName) { this.displayName = displayName; }

    public String displayName() { return displayName; }
}
