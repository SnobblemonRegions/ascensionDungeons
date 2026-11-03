package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;
import static com.ascensiondungeons.gen.landmark.Prims.hash;
import static com.ascensiondungeons.gen.landmark.Prims.pick;

/**
 * Titan Stem: the Warped Hollow navigation landmark. A colossal warped fungus: a five-wide stem that flares at the foot and
 * narrows as it climbs, four buttress roots lying out over a nylium platform, and a broad domed cap of warped wart block
 * with shroomlights inlaid in its crown and hung in a ring under its rim, and weeping rings of twisting vines round the stem.
 *
 * <p>Module coordinates: x, z in [0, 24], y in [0, 29]; y=0 is the floor row; the stem stands on (12, 12).
 */
public final class TitanStem {
    private TitanStem() {}

    public static final String ID = "warped_titan_stem";
    public static final int SIZE = 25, SIZE_Y = 30;
    public static final int ORIGIN_Y = 4;

    private static final String STEM = "minecraft:warped_stem[axis=y]";
    private static final String STRIPPED = "minecraft:stripped_warped_stem[axis=y]";
    private static final String WART = "minecraft:warped_wart_block";
    private static final String NYLIUM = "minecraft:warped_nylium";
    private static final String SHROOM = "minecraft:shroomlight";
    private static final String SOUL = "minecraft:soul_soil";

    public static Landmark landmarkAt(int cx, int cz) {
        return new Landmark(ID, build(), cx - SIZE / 2, ORIGIN_Y, cz - SIZE / 2, false, 0);
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE, SIZE_Y, SIZE);
        // The platform: nylium with patches of soul soil.
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                if (Math.hypot(x - 12, z - 12) <= 12.4) v.set(x, 0, z, hash(x, 0, z, 51) < 0.2 ? SOUL : NYLIUM);
            }
        }
        // The stem: 7 wide at the foot (y 1..2), 5 wide to y 10, 3 wide to y 17, with stripped courses every fifth block.
        for (int y = 1; y <= 17; y++) {
            int half = y <= 2 ? 3 : y <= 10 ? 2 : 1;
            boolean band = y % 5 == 0;
            for (int z = 12 - half; z <= 12 + half; z++) {
                for (int x = 12 - half; x <= 12 + half; x++) {
                    if (half == 3 && Math.abs(x - 12) == 3 && Math.abs(z - 12) == 3) continue;       // round off the foot
                    v.set(x, y, z, band ? STRIPPED : STEM);
                }
            }
        }
        // Four buttress roots lying out over the platform.
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int s = 4; s <= 8; s++) {
                int h = s <= 5 ? 2 : 1;
                for (int y = 1; y <= h; y++) {
                    v.set(12 + d[0] * s, y, 12 + d[1] * s, d[0] != 0 ? "minecraft:warped_stem[axis=x]" : "minecraft:warped_stem[axis=z]");
                }
            }
        }
        // The cap: a dome of wart block, hollowed underneath, over the top of the stem.
        for (int y = 15; y <= 24; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    double dx = (x - 12) / 12.0, dz = (z - 12) / 12.0, dy = (y - 16.5) / 8.0;
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d > 1 || y < 16 && d < 0.55 || v.get(x, y, z) != null) continue;
                    if (y < 18 && d < 0.78) continue;                                   // open under the cap
                    v.set(x, y, z, hash(x, y, z, 52) < 0.06 && y >= 22 ? SHROOM : WART);
                }
            }
        }
        // A ring of shroomlights hung under the rim, and strands of twisting vines in rings round the foot.
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20;
            int x = (int) Math.round(12 + 9.6 * Math.cos(a)), z = (int) Math.round(12 + 9.6 * Math.sin(a));
            if (v.get(x, 16, z) != null && v.get(x, 15, z) == null) v.set(x, 15, z, SHROOM);
            if (v.get(x, 16, z) != null && i % 2 == 0) {
                for (int y = 15; y >= 12 + (int) (hash(x, 1, z, 53) * 3); y--) if (v.get(x, y, z) == null) v.set(x, y, z, y == 15 ? SHROOM : "minecraft:weeping_vines_plant");
            }
        }
        for (int i = 0; i < 14; i++) {
            double a = Math.PI * 2 * i / 14 + 0.2;
            int x = (int) Math.round(12 + 7 * Math.cos(a)), z = (int) Math.round(12 + 7 * Math.sin(a));
            int h = 2 + (int) (hash(x, 2, z, 54) * 4);
            if (v.get(x, 1, z) != null) continue;
            for (int y = 1; y < 1 + h; y++) v.set(x, y, z, y == h ? "minecraft:twisting_vines[age=25]" : "minecraft:twisting_vines_plant");
        }
        // Soul lanterns on stems at the platform edge.
        for (int[] c : new int[][] {{3, 12}, {21, 12}, {12, 3}, {12, 21}}) {
            box(v, c[0], 1, c[1], c[0], 3, c[1], pick(c[0], 1, c[1], 55, STEM, 1.0));
            v.set(c[0], 4, c[1], "minecraft:soul_lantern[hanging=false,waterlogged=false]");
        }
        return v;
    }
}
