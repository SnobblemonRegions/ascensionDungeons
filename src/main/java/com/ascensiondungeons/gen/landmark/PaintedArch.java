package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.band;
import static com.ascensiondungeons.gen.landmark.Prims.hash;

/**
 * Painted Arch: the Painted Badlands navigation landmark. A natural rock arch of banded terracotta strata spans the canyon
 * path: two tapering buttresses join over a round-topped opening, with a few hoodoo spires standing on the crown. The
 * opening is 13 wide and the path under it is clear to 8 blocks.
 *
 * <p>Module coordinates: x in [0, 24], z in [0, 12], y in [0, 17]; y=0 is the floor row. The opening runs along z.
 */
public final class PaintedArch {
    private PaintedArch() {}

    public static final String ID = "badlands_painted_arch";
    public static final int SIZE_X = 25, SIZE_Y = 18, SIZE_Z = 13;
    public static final int ORIGIN_Y = 4;
    private static final String SAND = "minecraft:red_sand";
    private static final String WHITE = "minecraft:white_terracotta";

    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - SIZE_X / 2, ORIGIN_Y, cz - SIZE_Z / 2, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        for (int y = 1; y <= 16; y++) {
            double half = 12 - y * 0.30;                       // the silhouette narrows toward the crown
            double depth = 6 - y * 0.16 + 0.8 * Math.sin(y * 0.9);
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    double dx = Math.abs(x - 12), dz = Math.abs(z - 6);
                    if (dx > half + 0.5 * Math.sin(z * 1.7 + y) || dz > depth) continue;
                    // The opening: a round-topped passage 13 wide.
                    double open = dx / 6.5;
                    if (open < 1 && y <= 1 + 11 * Math.sqrt(1 - open * open)) continue;
                    boolean rim = y > 1 && Math.abs(dx - 6.5) < 0.9 && hash(x, y, z, 5) < 0.6;
                    v.set(x, y, z, rim ? WHITE : band(y));
                }
            }
        }
        // Sand drifted against the buttress feet.
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                double dx = Math.abs(x - 12);
                if (dx >= 6.5 && dx < 12.5 && hash(x, 0, z, 6) < 0.5) v.set(x, 0, z, SAND);
            }
        }
        // Hoodoo spires on the crown.
        for (int[] s : new int[][] {{9, 6, 3}, {15, 6, 4}, {12, 4, 2}}) {
            int top = Math.min(SIZE_Y - 1, 12 + s[2] + 1);
            for (int y = 12; y <= top; y++) {
                if (v.get(s[0], y - 1, s[1]) == null && y > 12) break;
                v.setClipped(s[0], y, s[1], band(y + 3));
            }
        }
        return v;
    }
}
