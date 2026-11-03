package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;
import static com.ascensiondungeons.gen.landmark.Prims.pick;

/**
 * Great Pyramid: the Sunscorched Necropolis navigation landmark. A stepped pyramid of smooth sandstone on a paved platform,
 * banded with cut and chiseled courses, capped by a gold block, with a doorway in its south face that leads to a small
 * antechamber. Gold-capped obelisks stand at the four corners of the platform.
 *
 * <p>Module coordinates: x, z in [0, 20], y in [0, 17]; y=0 is the floor row; the doorway faces south (+z).
 */
public final class GreatPyramid {
    private GreatPyramid() {}

    public static final String ID = "necropolis_great_pyramid";
    public static final int SIZE = 21, SIZE_Y = 18;
    public static final int ORIGIN_Y = 4;

    private static final String SMOOTH = "minecraft:smooth_sandstone";
    private static final String CUT = "minecraft:cut_sandstone";
    private static final String CHISELED = "minecraft:chiseled_sandstone";
    private static final String SAND = "minecraft:sandstone";
    private static final String GOLD = "minecraft:gold_block";
    private static final String GLOW = "minecraft:glowstone";
    private static final String TERRA = "minecraft:orange_terracotta";

    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - SIZE / 2, ORIGIN_Y, cz - SIZE / 2, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE, SIZE_Y, SIZE);
        // The platform: a one-block paved apron round the whole pyramid.
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) v.set(x, 0, z, pick(x, 0, z, 1, CUT, .5, SMOOTH, .3, SAND, .2));
        // Pyramid body: eleven courses inset one block per layer from a 19-wide base.
        for (int y = 1; y <= 10; y++) {
            int inset = y;                                   // layer y spans [inset, SIZE-1-inset]
            for (int z = inset; z <= SIZE - 1 - inset; z++) {
                for (int x = inset; x <= SIZE - 1 - inset; x++) {
                    boolean edge = x == inset || x == SIZE - 1 - inset || z == inset || z == SIZE - 1 - inset;
                    String s = y % 3 == 0 ? (edge ? CHISELED : CUT) : edge ? SMOOTH : pick(x, y, z, 2, SAND, .7, SMOOTH, .3);
                    v.set(x, y, z, s);
                }
            }
        }
        v.set(10, 11, 10, GOLD);
        v.set(10, 12, 10, GLOW);
        // Doorway: 3 wide, 3 tall, cut 6 deep into the south face, with a gold lintel.
        for (int z = 14; z <= 19; z++) {
            for (int x = 9; x <= 11; x++) for (int y = 1; y <= 3; y++) v.set(x, y, z, Voxels.AIR);
        }
        for (int x = 8; x <= 12; x++) v.set(x, 4, 17, GOLD);
        for (int y = 1; y <= 3; y++) {
            v.set(8, y, 18, CHISELED);
            v.set(12, y, 18, CHISELED);
        }
        // Antechamber: a gold altar and a glowstone in the back wall.
        v.set(10, 1, 14, GOLD);
        v.set(10, 3, 13, GLOW);
        // Obelisks at the four corners of the platform.
        for (int[] c : new int[][] {{1, 1}, {18, 1}, {1, 18}, {18, 18}}) {
            for (int y = 1; y <= 8; y++) box(v, c[0], y, c[1], c[0] + 1, y, c[1] + 1, y == 1 ? CHISELED : y % 3 == 0 ? TERRA : SMOOTH);
            box(v, c[0], 9, c[1], c[0] + 1, 9, c[1] + 1, GOLD);
            v.set(c[0], 10, c[1], GLOW);
        }
        return v;
    }
}
