package com.ascensiondungeons.gen.landmark;

/**
 * Warden's Ring: the Verdant Canopy raid arena (landmark atlas, entry 04). The existing round blackstone chamber keeps
 * its walls and its south-western doorway. This module adds an inlaid floor, two tiers of seating around the walls, and
 * a crescent of pilasters joined by a lintel on the wall opposite the door.
 *
 * <p>The centre disc (radius 13) is left flat and clear: it is the boss anchor and the combat space. The strip from the
 * door to the ring is the staging area. Module coordinates are the template's, offset by (171, 4, 54); the chamber
 * centre is module (23, 23), template (194, 77). Raid markers are runtime concepts: {@link #ANCHOR}, {@link #ACTIVATION}
 * and {@link #STAGING} give their template positions.
 */
public final class WardensRing {
    private WardensRing() {}

    public static final String ID = "verdant_wardens_ring";
    public static final int SIZE = 47;
    public static final int SIZE_Y = 17;
    public static final int ORIGIN_X = 171;
    public static final int ORIGIN_Y = 4;
    public static final int ORIGIN_Z = 54;

    public static final int CENTER_X = 194;
    public static final int CENTER_Z = 77;
    /** Combat radius that is kept flat and unobstructed. */
    public static final int COMBAT_RADIUS = 13;
    /** Direction from the centre to the doorway, in degrees (x east, z south). */
    public static final double DOOR_ANGLE = Math.toDegrees(Math.atan2(14, -16.5));

    /** Template positions {x, y, z} for runtime markers. */
    public static final int[] ANCHOR = {CENTER_X, 5, CENTER_Z};
    public static final int[] ACTIVATION = markerAt(DOOR_ANGLE, 15);
    public static final int[] STAGING = markerAt(DOOR_ANGLE, 17);

    private static final String BRICKS = "minecraft:polished_blackstone_bricks";
    private static final String CHISELED = "minecraft:chiseled_polished_blackstone";
    private static final String TUFF_BRICKS = "minecraft:tuff_bricks";
    private static final String SLAB = "minecraft:stone_brick_slab[type=bottom,waterlogged=false]";
    private static final String STEP = "minecraft:tuff_bricks";
    private static final String LANTERN = "minecraft:sea_lantern";

    private static int[] markerAt(double angle, double r) {
        double a = Math.toRadians(angle);
        return new int[] {(int) Math.round(CENTER_X + r * Math.cos(a)), 5, (int) Math.round(CENTER_Z + r * Math.sin(a))};
    }

    /** Materials for one biome's version of the arena. */
    public record Style(String bricks, String chiseled, String spoke, String strip, String slab, String step,
                        String lintelLow, String lintelHigh, String light) {}

    private static final Style VERDANT = new Style(BRICKS, CHISELED, CHISELED, TUFF_BRICKS, SLAB, STEP, CHISELED, BRICKS, LANTERN);

    public static Landmark landmark() {
        return landmark(ID, VERDANT);
    }

    /** The Verdant arena at chamber centre (cx, cz), its doorway facing {@code doorDeg}. */
    public static Landmark verdantAt(int cx, int cz, double doorDeg) {
        return landmarkAt(ID, VERDANT, cx, cz, doorDeg);
    }

    public static Landmark landmark(String id, Style style) {
        return new Landmark(id, build(style, DOOR_ANGLE), ORIGIN_X, ORIGIN_Y, ORIGIN_Z, false, 0);
    }

    /** The arena at any chamber centre, with its doorway facing {@code doorDeg} (degrees; x east, z south). */
    public static Landmark landmarkAt(String id, Style style, int cx, int cz, double doorDeg) {
        return new Landmark(id, build(style, doorDeg), cx - SIZE / 2, ORIGIN_Y, cz - SIZE / 2, false, 0);
    }

    /** Template position of a point {@code r} blocks from the chamber centre toward the door. */
    public static int[] pointToward(int cx, int cz, double doorDeg, double r) {
        return markerFrom(cx, cz, doorDeg, r);
    }

    private static int[] markerFrom(int cx, int cz, double angle, double r) {
        double a = Math.toRadians(angle);
        return new int[] {(int) Math.round(cx + r * Math.cos(a)), 5, (int) Math.round(cz + r * Math.sin(a))};
    }

    private static double diff(double a, double b) {
        double d = (a - b) % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    public static Voxels build() {
        return build(VERDANT, DOOR_ANGLE);
    }

    public static Voxels build(Style st, double doorAngle) {
        Voxels v = new Voxels(SIZE, SIZE_Y, SIZE);
        double rear = doorAngle + 180;
        int c = SIZE / 2;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = x - c, dz = z - c;
                double r = Math.hypot(dx, dz);
                double theta = Math.toDegrees(Math.atan2(dz, dx));
                floor(st, v, x, z, dx, dz, r, theta, doorAngle);
                seating(st, v, x, z, r, theta, doorAngle);
                crescent(st, v, x, z, r, theta, rear);
            }
        }
        return v;
    }

    private static void floor(Style st, Voxels v, int x, int z, double dx, double dz, double r, double theta,
                              double doorAngle) {
        if (r <= 2.5) {
            v.set(x, 0, z, st.chiseled());
        } else if (r >= 12.0 && r < 13.5) {
            boolean spoke = false;
            for (int k = 0; k < 8; k++) if (Math.abs(Math.sin(Math.toRadians(diff(theta, k * 45.0)))) * r < 0.9
                    && Math.abs(diff(theta, k * 45.0)) < 90) spoke = true;
            v.set(x, 0, z, spoke ? st.spoke() : st.bricks());
        } else if (r >= 13.5 && r <= 19.5) {
            double a = Math.toRadians(doorAngle);
            double along = dx * Math.cos(a) + dz * Math.sin(a);
            double lateral = -dx * Math.sin(a) + dz * Math.cos(a);
            if (along > 0 && Math.abs(lateral) <= 2.0) v.set(x, 0, z, st.strip());      // staging strip to the door
        }
    }

    private static void seating(Style st, Voxels v, int x, int z, double r, double theta, double doorAngle) {
        if (Math.abs(diff(theta, doorAngle)) <= 30) return;                              // the doorway stays clear
        if (r >= 15.5 && r < 17.5) v.set(x, 1, z, st.slab());
        else if (r >= 17.5 && r <= 20.0) v.set(x, 1, z, st.step());
    }

    private static void crescent(Style st, Voxels v, int x, int z, double r, double theta, double rear) {
        if (r < 17.0 || r > 20.6) return;
        double d = diff(theta, rear);
        if (Math.abs(d) > 55) return;
        int top = 10 + (int) Math.round(5 * Math.cos(Math.PI / 2 * d / 55));
        // Pilasters: five, 22.5 degrees apart, about three blocks wide.
        boolean pilaster = false;
        double centre = 0;
        for (int k = -2; k <= 2; k++) {
            double off = diff(theta, rear + k * 22.5);
            if (Math.abs(Math.sin(Math.toRadians(off)) * r) <= 1.2 && Math.abs(off) < 30) {
                pilaster = true;
                if (k == 0 || Math.abs(off) < 2.5) centre = 1;
            }
        }
        if (pilaster) {
            for (int y = 2; y <= top - 2; y++) v.set(x, y, z, st.bricks());
        }
        if (r >= 17.0 && r < 17.6) return;
        // Lintel: two rows, joined to every pilaster and to the wall behind.
        v.set(x, top - 1, z, centre > 0 && r < 18.2 ? st.light() : st.lintelLow());
        v.set(x, top, z, st.lintelHigh());
    }
}
