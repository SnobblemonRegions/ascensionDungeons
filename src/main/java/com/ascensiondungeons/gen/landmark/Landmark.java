package com.ascensiondungeons.gen.landmark;

import java.util.function.Predicate;

/**
 * A landmark module and where it goes in a district template.
 *
 * @param id        stable name
 * @param voxels    the authored blocks
 * @param originX   template x of the module's x=0
 * @param originY   template y of the module's y=0 (the floor row)
 * @param originZ   template z of the module's z=0
 * @param clearTrees whether ordinary template trees touching the module's footprint are removed first
 * @param clearPlantsRadius floor plants within this many blocks of the module centre are cleared (0 = none)
 */
public record Landmark(String id, Voxels voxels, int originX, int originY, int originZ, boolean clearTrees,
                       int clearPlantsRadius) {

    /** Block names that count as ordinary trees in the district template. */
    public static final Predicate<String> TREE_BLOCK = s -> s.startsWith("minecraft:oak_log")
            || s.startsWith("minecraft:oak_leaves");

    /** Block names that count as floor plants. */
    public static final Predicate<String> PLANT_BLOCK = s -> s.equals("minecraft:short_grass")
            || s.equals("minecraft:fern") || s.startsWith("minecraft:flowering_azalea") || s.equals("minecraft:azure_bluet")
            || s.equals("minecraft:allium") || s.equals("minecraft:cornflower");
}
