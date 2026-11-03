package com.ascensiondungeons.gen.landmark;

import static com.ascensiondungeons.gen.landmark.Prims.box;

/**
 * Reading Rotunda: the Gilded Library raid arena. The Warden's Ring layout in dark oak and spruce, with gold floor spokes,
 * concentric spruce rings and a gold compass rose inlaid flush in the floor, and four book towers on the seating tiers
 * either side of the doorway: two-by-two stacks of bookshelves topped with a lantern. The combat disc stays flat and clear.
 */
public final class ReadingRotunda {
    private ReadingRotunda() {}

    public static final String ID = "library_reading_rotunda";

    private static final String GOLD = "minecraft:gold_block";
    private static final String SPRUCE = "minecraft:spruce_planks";

    private static final WardensRing.Style STYLE = new WardensRing.Style(
            "minecraft:dark_oak_planks", "minecraft:stripped_dark_oak_log[axis=y]", GOLD, SPRUCE,
            "minecraft:dark_oak_slab[type=bottom,waterlogged=false]", "minecraft:dark_oak_planks",
            "minecraft:bookshelf", "minecraft:stripped_dark_oak_log[axis=y]", "minecraft:glowstone");

    public static Landmark landmarkAt(int cx, int cz, double doorDeg) {
        return new Landmark(ID, build(doorDeg), cx - WardensRing.SIZE / 2, WardensRing.ORIGIN_Y, cz - WardensRing.SIZE / 2, false, 0);
    }

    public static Voxels build(double doorDeg) {
        Voxels v = WardensRing.build(STYLE, doorDeg);
        int c = WardensRing.SIZE / 2;
        // Spruce rings and a four-pointed compass rose in gold, flush with the floor.
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                double dx = x - c, dz = z - c, r = Math.hypot(dx, dz);
                if (v.get(x, 0, z) != null || r < 3 || r > 11.6) continue;
                double ang = Math.atan2(dz, dx) - Math.toRadians(doorDeg);
                double arm = Math.min(Math.abs(Math.sin(ang)), Math.abs(Math.cos(ang))) * r;
                if (Math.abs(r - 5) < 0.5 || Math.abs(r - 9.2) < 0.5) v.set(x, 0, z, SPRUCE);
                else if (arm < 0.8 && r < 8.6) v.set(x, 0, z, GOLD);
            }
        }
        // Book towers on the seating tiers.
        for (double off : new double[] {-100, -70, 70, 100}) {
            double a = Math.toRadians(doorDeg + off);
            int px = (int) Math.round(c + 18.6 * Math.cos(a)), pz = (int) Math.round(c + 18.6 * Math.sin(a));
            box(v, px, 2, pz, px + 1, 7, pz + 1, "minecraft:bookshelf");
            box(v, px, 8, pz, px + 1, 8, pz + 1, GOLD);
            v.setClipped(px, 9, pz, "minecraft:lantern[hanging=false,waterlogged=false]");
        }
        return v;
    }
}
