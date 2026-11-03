package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Orrery: the Lapis Observatory navigation landmark. A great armillary sphere on a stepped deepslate pedestal: three
 * gold rings of radius 8 (an equator and two meridians at right angles) cross at the top and the foot of the sphere, which
 * is a lapis shell round a sea-lantern core. Sea lanterns ride the rings as small worlds, and a finial of end rods crowns
 * the top. Every ring touches the pedestal column, so the whole piece is one connected mass.
 *
 * <p>Module coordinates: x, z in [0, 20], y in [0, 23]; y=0 is the floor row; the centre is (10, 11, 10).
 */
public final class Orrery {
    private Orrery() {}

    public static final String ID = "lapis_orrery";
    public static final int SIZE = 21, SIZE_Y = 24;
    public static final int ORIGIN_Y = 4;
    public static final int CENTRE_Y = 11;

    private static final String GOLD = "minecraft:gold_block";
    private static final String LAPIS = "minecraft:lapis_block";
    private static final String LANTERN = "minecraft:sea_lantern";
    private static final String TILES = "minecraft:deepslate_tiles";
    private static final String POLISHED = "minecraft:polished_deepslate";

    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - SIZE / 2, ORIGIN_Y, cz - SIZE / 2, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE, SIZE_Y, SIZE);
        // Pedestal: a disc with a lapis ring and a gold ring inlaid, then two steps.
        Prims.disc(v, 10, 10, 10.4, 0, TILES);
        Prims.ring(v, 10, 10, 8.6, 9.6, 0, LAPIS);
        Prims.ring(v, 10, 10, 3.6, 4.6, 0, GOLD);
        Prims.disc(v, 10, 10, 6.5, 1, POLISHED);
        Prims.ring(v, 10, 10, 5.4, 6.5, 1, LAPIS);
        Prims.disc(v, 10, 10, 3.2, 2, TILES);
        // The pedestal column from the steps to the foot of the rings.
        box(v, 10, 3, 10, 10, 3, 10, GOLD);
        // The sphere: a lapis shell round a sea-lantern core.
        for (int y = CENTRE_Y - 3; y <= CENTRE_Y + 3; y++) {
            for (int z = 7; z <= 13; z++) {
                for (int x = 7; x <= 13; x++) {
                    double d = Math.sqrt((x - 10) * (x - 10) + (y - CENTRE_Y) * (y - CENTRE_Y) + (z - 10) * (z - 10));
                    if (d <= 1.6) v.set(x, y, z, LANTERN);
                    else if (d <= 3.1) v.set(x, y, z, LAPIS);
                }
            }
        }
        // Three rings of radius 8. Two are meridians that meet the pedestal column, one is the equator.
        for (int i = 0; i < 160; i++) {
            double a = Math.PI * 2 * i / 160;
            int dx = (int) Math.round(8 * Math.cos(a)), dy = (int) Math.round(8 * Math.sin(a));
            v.setClipped(10 + dx, CENTRE_Y + dy, 10, GOLD);                  // meridian in the x-y plane
            v.setClipped(10, CENTRE_Y + dy, 10 + dx, GOLD);                  // meridian in the z-y plane
            v.setClipped(10 + dx, CENTRE_Y, 10 + dy, GOLD);                  // the equator
        }
        // The rings join the sphere with a short spoke at the equator and the poles, so nothing floats.
        for (int s = 4; s <= 7; s++) {
            for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}}) {
                v.setClipped(10 + d[0] * s, CENTRE_Y + d[1] * s, 10 + d[2] * s, GOLD);
            }
        }
        // Small worlds riding the rings.
        for (int[] w : new int[][] {{18, CENTRE_Y, 10}, {2, CENTRE_Y, 10}, {10, CENTRE_Y, 18}, {10, CENTRE_Y, 2},
                {10 + 6, CENTRE_Y + 6, 10}, {10 - 6, CENTRE_Y - 6, 10}, {10, CENTRE_Y + 6, 10 + 6}}) {
            v.setClipped(w[0], w[1], w[2], LANTERN);
        }
        // Finial.
        for (int y = CENTRE_Y + 9; y <= CENTRE_Y + 12; y++) v.setClipped(10, y, 10, y == CENTRE_Y + 12 ? "minecraft:end_rod[facing=up]" : GOLD);
        return v;
    }
}
