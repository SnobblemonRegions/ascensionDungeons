package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.band;
import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Mesa Amphitheatre: the Painted Badlands raid arena. The Warden's Ring layout in red terracotta and red sandstone, with a
 * white terracotta floor ring and spokes, seating tiers in painted strata, and four stepped buttes of banded rock standing
 * on the tiers either side of the doorway. The combat disc stays flat and clear.
 */
public final class MesaAmphitheatre {
    private MesaAmphitheatre() {}

    public static final String ID = "badlands_mesa_amphitheatre";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:red_terracotta", "minecraft:chiseled_red_sandstone", "minecraft:white_terracotta",
            "minecraft:cut_red_sandstone", "minecraft:cut_red_sandstone_slab[type=bottom,waterlogged=false]",
            "minecraft:orange_terracotta", "minecraft:brown_terracotta", "minecraft:yellow_terracotta",
            "minecraft:redstone_lamp[lit=true]");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return new Landmark(ID, build(doorDeg), cx - WardensRing.SIZE / 2, WardensRing.ORIGIN_Y, cz - WardensRing.SIZE / 2, false, 0);
    }

    public static Voxels build(double doorDeg) {
        Voxels v = WardensRing.build(STYLE, doorDeg);
        int c = WardensRing.SIZE / 2;
        // Four stepped buttes on the seating tiers: 3 x 3 for the first five layers, then a 1 x 1 cap.
        for (double off : new double[] {-105, -72, 72, 105}) {
            double a = Math.toRadians(doorDeg + off);
            int bx = (int) Math.round(c + 18.2 * Math.cos(a)), bz = (int) Math.round(c + 18.2 * Math.sin(a));
            for (int y = 2; y <= 8; y++) {
                if (y <= 6) box(v, bx - 1, y, bz - 1, bx + 1, y, bz + 1, band(y));
                else box(v, bx, y, bz, bx, y, bz, band(y));
            }
            v.setClipped(bx, 9, bz, "minecraft:redstone_lamp[lit=true]");
        }
        return v;
    }
}
