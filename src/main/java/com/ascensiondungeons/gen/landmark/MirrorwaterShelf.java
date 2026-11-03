package com.ascensiondungeons.gen.landmark;

/**
 * Mirrorwater Shelf: the Glacial Vault exploration landmark. On the Glowcap Spring socket the still pool keeps its water
 * (it is level with the floor) and gains a stepped packed-ice basin edge, snow banks, a level stone viewing walkway round
 * the north and east, and a small frozen cascade down the east outcrop. Everything is solid; there is no flowing water.
 *
 * <p>Module coordinates are the Glowcap Spring module's: pool centre at (6, 12), floor row y=0. The west shore, where the
 * route arrives, stays open.
 */
public final class MirrorwaterShelf {
    private MirrorwaterShelf() {}

    public static final String ID = "glacial_mirrorwater_shelf";
    public static final int POOL_X = 6, POOL_Z = 12;

    private static final String PACKED = "minecraft:packed_ice";
    private static final String BLUE = "minecraft:blue_ice";
    private static final String SNOW = "minecraft:snow_block";
    private static final String CALCITE = "minecraft:calcite";
    private static final String WALK = "minecraft:polished_diorite";
    private static final String LANTERN = "minecraft:sea_lantern";

    /** The shelf around a pool whose centre is template (poolX, poolZ); the base layout provides the pool itself. */
    public static Landmark landmarkAt(int poolX, int poolZ) {
        return new Landmark(ID, build(), poolX - POOL_X, GlowcapSpring.ORIGIN_Y, poolZ - POOL_Z, false, 0);
    }

    private static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    public static Voxels build() {
        Voxels v = new Voxels(GlowcapSpring.SIZE_X, GlowcapSpring.SIZE_Y, GlowcapSpring.SIZE_Z);
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                double dx = x - POOL_X, dz = z - POOL_Z;
                double r = Math.hypot(dx, dz);
                double angle = Math.toDegrees(Math.atan2(dz, dx));            // 180 = west, -90 = north
                boolean west = Math.abs(Math.abs(angle) - 180) < 35;
                if (west) continue;
                if (r >= 6.6 && r < 7.6) v.set(x, 1, z, PACKED);                // basin lip
                else if (r >= 7.6 && r < 9.0) v.set(x, 0, z, SNOW);             // snow bank
                else if (r >= 9.0 && r < 11.8 && angle > -165 && angle < 75) v.set(x, 0, z, WALK);   // viewing walkway
            }
        }
        // Frozen cascade down the east outcrop, in place of the Verdant waterfall.
        for (int y = 1; y <= 7; y++) {
            int[] z = GlowcapSpring.zRange(y);
            for (int x = 16; x <= 19; x++) {
                for (int zz = z[0]; zz <= z[1]; zz++) {
                    v.set(x, y, zz, y == 7 ? SNOW : hash(x, y, zz) < 0.3 ? PACKED : CALCITE);
                }
            }
        }
        for (int zz = 11; zz <= 13; zz++) {
            for (int y = 1; y <= 7; y++) v.set(15, y, zz, y % 2 == 0 ? PACKED : BLUE);
            v.set(16, 7, zz, BLUE);
            v.set(17, 7, zz, PACKED);
        }
        v.set(16, 4, 9, LANTERN);
        v.set(16, 4, 15, LANTERN);
        return v;
    }
}
