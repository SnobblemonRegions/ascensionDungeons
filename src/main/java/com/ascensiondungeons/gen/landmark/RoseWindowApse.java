package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;
import static com.ascensiondungeons.gen.landmark.Prims.hash;

/**
 * Rose Window Apse: the Crimson Cathedral navigation landmark. The sanctuary at the end of a chapel: a three-thick back
 * wall of nether brick with a great rose window (a circle of stained glass in red, orange and yellow, divided by eight
 * spokes of blackstone tracery, backlit by shroomlights), two tall lancet windows either side, buttress pillars, and an
 * altar on a two-step dais with lit red candles. The window faces east (+x).
 *
 * <p>Module coordinates: x in [0, 13] (x=0 the back), z in [0, 28], y in [0, 24]; y=0 is the floor row.
 */
public final class RoseWindowApse {
    private RoseWindowApse() {}

    public static final String ID = "crimson_rose_window_apse";
    public static final int SIZE_X = 14, SIZE_Y = 25, SIZE_Z = 29;
    public static final int ORIGIN_Y = 4;

    private static final String BRICKS = "minecraft:red_nether_bricks";
    private static final String BLACK = "minecraft:polished_blackstone_bricks";
    private static final String GILDED = "minecraft:gilded_blackstone";
    private static final String GLOW = "minecraft:shroomlight";
    private static final String CANDLE = "minecraft:red_candle[candles=4,lit=true,waterlogged=false]";

    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, ORIGIN_Y, z, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        // The back wall, three thick, 24 high.
        for (int z = 0; z < SIZE_Z; z++) {
            for (int y = 1; y <= 24; y++) {
                for (int x = 0; x <= 2; x++) v.set(x, y, z, hash(x, y, z, 41) < 0.2 ? BLACK : BRICKS);
            }
        }
        // Rose window: centre z=14, y=15, radius 8; glass at x=1, backlight at x=0, tracery in the spokes and rings.
        for (int z = 0; z < SIZE_Z; z++) {
            for (int y = 1; y <= 24; y++) {
                double dz = z - 14, dy = y - 15, r = Math.hypot(dz, dy);
                if (r > 8.2) continue;
                double theta = Math.toDegrees(Math.atan2(dy, dz));
                double off = Math.abs(((theta % 45) + 45) % 45);
                boolean spoke = Math.min(off, 45 - off) * Math.PI / 180 * r < 0.75 && r > 1.8;
                boolean ring = Math.abs(r - 4.6) < 0.55 || r > 7.3;
                boolean hub = r < 1.9;
                v.set(0, y, z, GLOW);
                v.set(2, y, z, spoke || ring ? GILDED : Voxels.AIR);               // tracery stands proud, the panes sit behind it
                if (spoke || ring) v.set(1, y, z, GILDED);
                else if (hub) v.set(1, y, z, "minecraft:yellow_stained_glass");
                else v.set(1, y, z, r < 4.6 ? "minecraft:orange_stained_glass" : "minecraft:red_stained_glass");
            }
        }
        // Two lancets either side of the rose: 2 wide, 11 tall, with a pointed top.
        for (int z0 : new int[] {4, 23}) {
            for (int y = 3; y <= 14; y++) {
                for (int z = z0; z <= z0 + 1; z++) {
                    v.set(0, y, z, GLOW);
                    v.set(2, y, z, Voxels.AIR);
                    v.set(1, y, z, y % 2 == 0 ? "minecraft:red_stained_glass" : "minecraft:orange_stained_glass");
                }
            }
            v.set(0, 15, z0, GLOW);
            v.set(2, 15, z0, Voxels.AIR);
            v.set(1, 15, z0, "minecraft:red_stained_glass");
        }
        // Buttress pillars standing out from the wall between the windows.
        for (int z : new int[] {1, 26}) for (int y = 1; y <= 23; y++) box(v, 3, y, z, 4, y, z + 1, y % 8 == 0 ? GILDED : BRICKS);
        for (int z : new int[] {9, 19}) for (int y = 1; y <= 12; y++) box(v, 3, y, z, 3, y, z, y % 4 == 0 ? GILDED : BLACK);
        // The dais and the altar.
        box(v, 4, 0, 8, 12, 0, 20, "minecraft:polished_blackstone");
        box(v, 5, 1, 10, 9, 1, 18, "minecraft:polished_blackstone_bricks");
        box(v, 6, 2, 12, 8, 2, 16, BLACK);
        v.set(7, 3, 14, CANDLE);
        v.set(7, 3, 12, CANDLE);
        v.set(7, 3, 16, CANDLE);
        for (int z : new int[] {9, 19}) v.set(3, 13, z, GLOW);
        return v;
    }
}
