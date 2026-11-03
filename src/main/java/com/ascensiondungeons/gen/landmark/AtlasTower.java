package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;
import static com.ascensiondungeons.gen.landmark.Prims.disc;
import static com.ascensiondungeons.gen.landmark.Prims.ring;
import static com.ascensiondungeons.gen.landmark.Prims.tube;

/**
 * Atlas Tower: the Gilded Library navigation landmark. A round tower of bookshelves, twenty-one blocks tall, with four gold
 * ribs, four dark-oak buttresses topped with lanterns, a doorway in the south face, two inner balconies, a domed cap with a
 * gold finial, and inside it an enchanting table ringed with bookshelves under a hanging lantern. The tower stands on a
 * round parquet platform.
 *
 * <p>Module coordinates: x, z in [0, 20], y in [0, 29]; y=0 is the floor row; the tower is centred on (10, 10).
 */
public final class AtlasTower {
    private AtlasTower() {}

    public static final String ID = "library_atlas_tower";
    public static final int SIZE = 21, SIZE_Y = 30;
    public static final int ORIGIN_Y = 4;

    private static final String SHELF = "minecraft:bookshelf";
    private static final String GOLD = "minecraft:gold_block";
    private static final String DARK = "minecraft:dark_oak_planks";
    private static final String SPRUCE = "minecraft:spruce_planks";
    private static final String LOG = "minecraft:stripped_dark_oak_log[axis=y]";
    private static final String LANTERN = "minecraft:lantern[hanging=false,waterlogged=false]";
    private static final String HANGING = "minecraft:lantern[hanging=true,waterlogged=false]";

    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - SIZE / 2, ORIGIN_Y, cz - SIZE / 2, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE, SIZE_Y, SIZE);
        disc(v, 10, 10, 10.4, 0, SPRUCE);
        ring(v, 10, 10, 8.4, 9.4, 0, DARK);
        disc(v, 10, 10, 7.4, 0, SPRUCE);
        // The shaft: bookshelves, 21 high, with gold bands every sixth course.
        for (int y = 1; y <= 21; y++) tube(v, 10, 10, 7.6, 9.4, y, y, y % 6 == 0 ? GOLD : SHELF);
        // Four gold ribs up the shaft at the diagonals.
        for (int[] d : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            int rx = 10 + d[0] * 7, rz = 10 + d[1] * 7;
            for (int y = 1; y <= 21; y++) v.set(rx, y, rz, GOLD);
        }
        // Doorway in the south face: 5 wide, 4 tall, framed with logs and a gold lintel.
        for (int z = 15; z <= 20; z++) for (int x = 8; x <= 12; x++) for (int y = 1; y <= 4; y++) v.set(x, y, z, Voxels.AIR);
        for (int y = 1; y <= 5; y++) for (int x : new int[] {7, 13}) for (int z = 16; z <= 18; z++) v.set(x, y, z, LOG);
        for (int x = 7; x <= 13; x++) for (int z = 16; z <= 18; z++) v.set(x, 5, z, GOLD);
        // Inside: parquet floor, an enchanting table, a ring of bookshelves two blocks out with the cardinal gaps left open.
        v.set(10, 1, 10, "minecraft:enchanting_table");
        for (int z = 6; z <= 14; z++) {
            for (int x = 6; x <= 14; x++) {
                double r = Math.hypot(x - 10, z - 10);
                boolean gap = Math.abs(x - 10) <= 1 || Math.abs(z - 10) <= 1;
                if (r >= 2.0 && r <= 3.2 && !gap) {
                    v.set(x, 1, z, SHELF);
                    v.set(x, 2, z, SHELF);
                }
            }
        }
        // Two balconies hung from the shaft wall, and a lantern on a chain in the middle.
        for (int y : new int[] {10, 17}) ring(v, 10, 10, 4.6 + (y > 12 ? 0.8 : 0), 7.5, y, DARK);
        // Roof disc, then a stepped dome and a finial.
        disc(v, 10, 10, 8.6, 22, DARK);
        double[] rad = {8.0, 6.6, 5.0, 3.2, 1.6};
        for (int i = 0; i < rad.length; i++) disc(v, 10, 10, rad[i], 23 + i, i % 2 == 0 ? SPRUCE : DARK);
        for (int y = 28; y <= 29; y++) v.setClipped(10, y, 10, y == 29 ? LANTERN : GOLD);
        for (int y = 15; y <= 21; y++) v.set(10, y, 10, "minecraft:chain[axis=y,waterlogged=false]");
        v.set(10, 14, 10, HANGING);
        // Buttresses at the diagonals standing clear of the shaft, with lanterns on top.
        for (int[] d : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            int bx = 10 + d[0] * 9 - (d[0] > 0 ? 0 : 1), bz = 10 + d[1] * 9 - (d[1] > 0 ? 0 : 1);
            for (int y = 1; y <= 11; y++) box(v, bx, y, bz, bx + 1, y, bz + 1, y % 5 == 0 ? GOLD : LOG);
            v.setClipped(bx, 12, bz, LANTERN);
        }
        return v;
    }
}
