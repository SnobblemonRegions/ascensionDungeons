package com.ascensiondungeons.gen.layout;

import com.ascensiondungeons.gen.BiomeKind;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The base layout and landmark set of each biome that has one. Built once and cached; immutable afterwards. */
public final class Layouts {
    private Layouts() {}

    private static final Map<BiomeKind, Layout> CACHE = new EnumMap<>(BiomeKind.class);

    /** Biomes with a layout. */
    public static List<BiomeKind> available() {
        return List.of(BiomeKind.VERDANT_CANOPY, BiomeKind.ASHEN_CALDERA, BiomeKind.GLACIAL_VAULT,
                BiomeKind.CRYSTAL_CAVERN, BiomeKind.FLOODED_RUINS,
                BiomeKind.FUNGAL_GROTTO, BiomeKind.SUNKEN_GARDEN, BiomeKind.ANCIENT_FOUNDRY,
                BiomeKind.SUNSCORCHED_NECROPOLIS, BiomeKind.PAINTED_BADLANDS, BiomeKind.CHERRY_GROVE, BiomeKind.LAPIS_OBSERVATORY, BiomeKind.CRIMSON_CATHEDRAL, BiomeKind.GILDED_LIBRARY, BiomeKind.WARPED_HOLLOW, BiomeKind.END_SPIRE);
    }

    public static synchronized Layout of(BiomeKind biome) {
        return CACHE.computeIfAbsent(biome, Layouts::create);
    }

    private static Layout create(BiomeKind biome) {
        return switch (biome) {
            case VERDANT_CANOPY -> VerdantLayout.build();
            case ASHEN_CALDERA -> AshenLayout.build();
            case GLACIAL_VAULT -> GlacialLayout.build();
            case CRYSTAL_CAVERN -> CrystalLayout.build();
            case FLOODED_RUINS -> FloodedLayout.build();
            case FUNGAL_GROTTO -> FungalLayout.build();
            case SUNKEN_GARDEN -> SunkenLayout.build();
            case ANCIENT_FOUNDRY -> FoundryLayout.build();
            case SUNSCORCHED_NECROPOLIS -> NecropolisLayout.build();
            case PAINTED_BADLANDS -> BadlandsLayout.build();
            case CHERRY_GROVE -> CherryLayout.build();
            case LAPIS_OBSERVATORY -> ObservatoryLayout.build();
            case CRIMSON_CATHEDRAL -> CathedralLayout.build();
            case GILDED_LIBRARY -> LibraryLayout.build();
            case WARPED_HOLLOW -> WarpedLayout.build();
            case END_SPIRE -> EndSpireLayout.build();
            default -> throw new IllegalArgumentException("No layout for " + biome);
        };
    }
}
