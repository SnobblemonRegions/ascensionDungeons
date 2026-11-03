package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;
import static com.ascensiondungeons.gen.landmark.Prims.hash;

/**
 * Chorus Spire: the End Spire navigation landmark. A tapering tower of end stone brick in four tiers (15, 11, 7 and 3 wide)
 * with purpur pillars at every tier corner, an obsidian band between tiers, a purpur-and-end-rod crown, and chorus trees
 * growing from the ledges of the lower tiers with grown chorus flowers at their tips.
 *
 * <p>Module coordinates: x, z in [0, 18], y in [0, 33]; y=0 is the floor row; the tower is centred on (9, 9). The chorus
 * plants carry explicit connection states, so they look right without a block update.
 */
public final class ChorusSpire {
    private ChorusSpire() {}

    public static final String ID = "end_chorus_spire";
    public static final int SIZE = 19, SIZE_Y = 34;
    public static final int ORIGIN_Y = 4;

    private static final String BRICKS = "minecraft:end_stone_bricks";
    private static final String PILLAR = "minecraft:purpur_pillar[axis=y]";
    private static final String PURPUR = "minecraft:purpur_block";
    private static final String OBSIDIAN = "minecraft:obsidian";
    private static final String ROD = "minecraft:end_rod[facing=up]";

    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - SIZE / 2, ORIGIN_Y, cz - SIZE / 2, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE, SIZE_Y, SIZE);
        // The platform and the four tiers.
        box(v, 0, 0, 0, 18, 0, 18, "minecraft:end_stone");
        int[][] tiers = {{15, 1, 9}, {11, 10, 17}, {7, 18, 24}, {3, 25, 29}};      // width, first y, last y
        for (int[] t : tiers) {
            int half = t[0] / 2;
            for (int y = t[1]; y <= t[2]; y++) {
                for (int z = 9 - half; z <= 9 + half; z++) {
                    for (int x = 9 - half; x <= 9 + half; x++) {
                        boolean corner = Math.abs(x - 9) == half && Math.abs(z - 9) == half;
                        boolean edge = Math.abs(x - 9) == half || Math.abs(z - 9) == half;
                        if (!edge && y < t[2]) {                       // hollow inside, closed by the tier's top layer
                            v.set(x, y, z, BRICKS);
                            continue;
                        }
                        v.set(x, y, z, corner ? PILLAR : y == t[1] ? OBSIDIAN : y % 4 == 0 ? PURPUR : BRICKS);
                    }
                }
            }
        }
        // Chorus trees on the ledges of the first two tiers, each a stem with two side branches and a grown flower.
        int[][] roots = {{3, 3, 10}, {15, 3, 10}, {3, 15, 10}, {15, 15, 10}, {5, 5, 18}, {13, 5, 18}, {5, 13, 18}, {13, 13, 18}};
        for (int[] r : roots) chorus(v, r[0], r[2], r[1], (int) (hash(r[0], r[2], r[1], 71) * 3));
        // Crown: a purpur cap with a spire of end rods.
        box(v, 8, 30, 8, 10, 30, 10, PURPUR);
        for (int y = 31; y <= 33; y++) v.set(9, y, 9, y == 33 ? ROD : "minecraft:purpur_pillar[axis=y]");
        return v;
    }

    /** A chorus tree standing on (x, y-1, z): a stem of 3-5, two side branches, and flowers at the tips. */
    private static void chorus(Voxels v, int x, int y, int z, int variant) {
        int h = 3 + variant;
        for (int i = 0; i < h; i++) v.setClipped(x, y + i, z, "minecraft:chorus_plant");
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] d1 = dirs[variant], d2 = dirs[(variant + 2) % 4];
        for (int[] d : new int[][] {d1, d2}) {
            int by = y + h - 2;
            v.setClipped(x + d[0], by, z + d[1], "minecraft:chorus_plant");
            v.setClipped(x + d[0], by + 1, z + d[1], "minecraft:chorus_plant");
            v.setClipped(x + d[0], by + 2, z + d[1], "minecraft:chorus_flower[age=5]");
        }
        v.setClipped(x, y + h, z, "minecraft:chorus_flower[age=5]");
        connect(v, x - 2, y, z - 2, x + 2, y + h + 3, z + 2);
    }

    /** Sets the connection properties of every chorus plant in the box from its neighbours. */
    static void connect(Voxels v, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (!"minecraft:chorus_plant".equals(v.get(x, y, z))) continue;
                    v.set(x, y, z, "minecraft:chorus_plant[down=" + link(v, x, y - 1, z, true) + ",east=" + link(v, x + 1, y, z, false)
                            + ",north=" + link(v, x, y, z - 1, false) + ",south=" + link(v, x, y, z + 1, false) + ",up=" + link(v, x, y + 1, z, false)
                            + ",west=" + link(v, x - 1, y, z, false) + "]");
                }
            }
        }
    }

    private static boolean link(Voxels v, int x, int y, int z, boolean ground) {
        String s = v.get(x, y, z);
        if (s == null) return false;
        return s.startsWith("minecraft:chorus_") || ground && (s.equals("minecraft:end_stone") || s.equals(BRICKS) || s.equals(OBSIDIAN)
                || s.equals(PURPUR) || s.equals("minecraft:purpur_pillar[axis=y]"));
    }
}
