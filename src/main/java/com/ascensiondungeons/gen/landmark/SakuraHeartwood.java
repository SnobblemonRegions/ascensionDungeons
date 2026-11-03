package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;
import static com.ascensiondungeons.gen.landmark.Prims.hash;

/**
 * Sakura Heartwood: the Cherry Grove navigation landmark. One enormous cherry tree on the island in the sacred pond: a
 * thick trunk that narrows as it climbs, four low spreading limbs, a wide domed crown of persistent cherry leaves with
 * curtains of leaves weeping from its rim, a carpet of fallen petals under it, and four stone lanterns round the roots.
 * The crown's leaves shed pink petals in the real game.
 *
 * <p>Module coordinates: x, z in [0, 20], y in [0, 23]; y=0 is the floor row; the trunk stands on (10, 10).
 */
public final class SakuraHeartwood {
    private SakuraHeartwood() {}

    public static final String ID = "cherry_sakura_heartwood";
    public static final int SIZE = 21, SIZE_Y = 24;
    public static final int ORIGIN_Y = 4;

    private static final String LOG_Y = "minecraft:cherry_log[axis=y]";
    private static final String LOG_X = "minecraft:cherry_log[axis=x]";
    private static final String LOG_Z = "minecraft:cherry_log[axis=z]";
    private static final String LEAVES = "minecraft:cherry_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String PETALS = "minecraft:pink_petals[facing=north,flower_amount=4]";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";

    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - SIZE / 2, ORIGIN_Y, cz - SIZE / 2, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE, SIZE_Y, SIZE);
        // The island floor: a disc of grass under the whole tree.
        Prims.disc(v, 10, 10, 9.6, 0, GRASS);
        // Trunk: 3 x 3 for the first four blocks, 2 x 2 to y=10, then a single log carrying up into the crown.
        for (int y = 1; y <= 17; y++) {
            if (y <= 4) box(v, 9, y, 9, 11, y, 11, LOG_Y);
            else if (y <= 10) box(v, 9, y, 9, 10, y, 10, LOG_Y);
            else v.set(10, y, 10, LOG_Y);
        }
        // Root flares: four short logs lying out from the base.
        for (int i = 1; i <= 3; i++) {
            v.set(10 + 1 + i, 1, 10, LOG_X);
            v.set(10 - 1 - i, 1, 10, LOG_X);
            v.set(10, 1, 10 + 1 + i, LOG_Z);
            v.set(10, 1, 10 - 1 - i, LOG_Z);
        }
        // Four limbs rising diagonally from the upper trunk, each ending in a leaf clump.
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            for (int s = 1; s <= 6; s++) {
                int y = 9 + s / 2 + 1;
                v.setClipped(10 + d[0] * s, y, 10 + d[1] * s, d[0] != 0 ? LOG_X : LOG_Z);
            }
        }
        // The crown: a wide dome of leaves with ragged holes.
        for (int y = 9; y <= 22; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    double dx = (x - 10) / 10.0, dz = (z - 10) / 10.0, dy = (y - 14.5) / 8.0;
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d > 1 || hash(x, y, z, 21) > 0.93 - 0.30 * d || v.get(x, y, z) != null) continue;
                    if (y < 12 && Math.hypot(x - 10, z - 10) < 3.5) continue;          // keep head room round the trunk
                    v.set(x, y, z, LEAVES);
                }
            }
        }
        // Leaves weeping from the rim of the crown, never lower than y=5.
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                double r = Math.hypot(x - 10, z - 10);
                if (r < 6.5 || r > 9.5 || hash(x, 0, z, 22) > 0.45) continue;
                int len = 3 + (int) (hash(x, 1, z, 22) * 5);
                for (int y = 11; y > 11 - len; y--) if (v.get(x, y, z) == null && y >= 6) v.set(x, y, z, LEAVES);
            }
        }
        // Fallen petals, and four stone lanterns round the roots.
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                if (Math.hypot(x - 10, z - 10) < 9 && v.get(x, 1, z) == null && hash(x, 1, z, 23) < 0.35) v.set(x, 1, z, PETALS);
            }
        }
        for (int[] c : new int[][] {{6, 6}, {14, 6}, {6, 14}, {14, 14}}) {
            v.set(c[0], 1, c[1], "minecraft:stone_bricks");
            v.set(c[0], 2, c[1], "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]");
            v.set(c[0], 3, c[1], "minecraft:lantern[hanging=false,waterlogged=false]");
        }
        return v;
    }
}
