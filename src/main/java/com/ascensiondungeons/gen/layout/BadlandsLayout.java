package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.HoodooCache;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.MesaAmphitheatre;
import com.ascensiondungeons.gen.landmark.PaintedArch;
import com.ascensiondungeons.gen.landmark.Prims;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Painted Badlands: a winding canyon of banded terracotta under a high rock roof. The canyon runs from the north gate in
 * an S-curve to the south gate, with the east gate on a straight reach of it. The Painted Arch spans the first straight, the
 * Hoodoo Cache hides in a pocket off the north-east wall, and the Mesa Amphitheatre fills a round bay in the west reached by
 * a side passage. Free-standing buttes, hoodoo spires, red-sand dunes, cactus, banded stalactites and a few camp fires fill
 * the floor.
 */
public final class BadlandsLayout {
    private BadlandsLayout() {}

    public static final int WALL = 11;
    public static final int ARCH_X = 88, ARCH_Z = 48;
    public static final int CACHE_X = 119, CACHE_Z = 26;
    public static final int ARENA_X = 44, ARENA_Z = 102;
    public static final double ARENA_DOOR = 25;

    private record Cap(double ax, double az, double bx, double bz, double r) {}

    private record Lobe(double x, double z, double r, double phase) {}

    private record Rect(int x0, int z0, int x1, int z1) {}

    private static final Cap[] PATH = {
            new Cap(88, 36, 88, 60, 16), new Cap(88, 60, 120, 76, 16), new Cap(120, 76, 120, 104, 16),
            new Cap(120, 104, 88, 124, 16), new Cap(88, 124, 88, 145, 16),
            new Cap(44, 102, 88, 124, 12), new Cap(88, 44, 126, 40, 8)};
    private static final Lobe[] LOBES = {new Lobe(ARENA_X, ARENA_Z, 24, 0.7), new Lobe(126, 40, 18, 2.1)};
    private static final Rect[] HALLS = {new Rect(79, 20, 97, 40), new Rect(79, 140, 97, 161), new Rect(120, 79, 161, 97),
            new Rect(24, 80, 34, 96)};

    private static final float[][] SDF = new float[Canvas.SIZE][Canvas.SIZE];

    static {
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                double best = 1e9;
                for (Cap c : PATH) best = Math.min(best, DistrictKit.distToSegment(x, z, c.ax(), c.az(), c.bx(), c.bz()) - c.r());
                for (Lobe l : LOBES) {
                    double dx = x - l.x(), dz = z - l.z(), th = Math.atan2(dz, dx);
                    best = Math.min(best, Math.hypot(dx, dz) - l.r() * (1 + 0.04 * Math.sin(3 * th + l.phase())));
                }
                for (Rect r : HALLS) best = Math.min(best, Math.max(Math.max(r.x0() - x, x - r.x1()), Math.max(r.z0() - z, z - r.z1())));
                SDF[x][z] = (float) best;
            }
        }
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return x >= 20 && x <= 161 && z >= 20 && z <= 161 && SDF[x][z] <= 0; }

        @Override public boolean outer(int x, int z) { return SDF[x][z] <= WALL; }

        @Override public int ceiling(int x, int z) {
            double depth = Math.max(0, -SDF[x][z]);
            return (int) (54 + Math.min(14, depth * 0.9) + 3 * (hash(x / 4, 0, z / 4, 131) - 0.5));
        }
    };

    private static final String RED_SAND = "minecraft:red_sand";
    private static final String RED_SANDSTONE = "minecraft:red_sandstone";
    private static final String SMOOTH = "minecraft:smooth_red_sandstone";
    private static final String CUT = "minecraft:cut_red_sandstone";
    private static final String WHITE = "minecraft:white_terracotta";

    private static String floor(int x, int z) {
        return pick(x, 4, z, 132, RED_SAND, .42, RED_SANDSTONE, .20, "minecraft:terracotta", .14, "minecraft:orange_terracotta", .10,
                SMOOTH, .14);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 133, SMOOTH, .50, CUT, .30, RED_SANDSTONE, .20);
    }

    private static String wall(int[] p, int y) {
        return hash(p[0], y, p[1], 134) < 0.08 ? RED_SANDSTONE : Prims.band(y);
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 135, SMOOTH, .45, CUT, .30, WHITE, .10, RED_SANDSTONE, .15);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(BadlandsLayout::floor, RED_SANDSTONE, RED_SANDSTONE, BadlandsLayout::wall);
        k.gates(SMOOTH, WHITE);

        int last = Template.LAST;
        k.roadSeg(88, 0, 88, 60, BadlandsLayout::road, WHITE);
        k.roadSeg(88, 60, 120, 76, BadlandsLayout::road, WHITE);
        k.roadSeg(120, 76, 120, 104, BadlandsLayout::road, WHITE);
        k.roadSeg(120, 104, 88, 124, BadlandsLayout::road, WHITE);
        k.roadSeg(88, 124, 88, last, BadlandsLayout::road, WHITE);
        k.roadSeg(120, 88, last, 88, BadlandsLayout::road, WHITE);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, BadlandsLayout::arenaFloor, BadlandsLayout::wall, "minecraft:brown_terracotta", SMOOTH);
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], 88, 124, BadlandsLayout::road, WHITE);
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 12, 0);
        k.roadSeg(CACHE_X + 7, CACHE_Z + 15, 92, 46, BadlandsLayout::road, WHITE);
        k.reserveRect(ARCH_X - 13, ARCH_Z - 7, ARCH_X + 13, ARCH_Z + 7, 0);

        k.scatter(136, 12, 0.5, 3, 6, (x, z) -> pick(x, 5, z, 137, RED_SAND, .7, RED_SANDSTONE, .3));
        buttes(k);
        stalactites(k);
        campFires(k);
        plants(k);
        skylights(k);
        k.lights();

        List<Landmark> landmarks = List.of(PaintedArch.landmarkAt(ARCH_X, ARCH_Z), HoodooCache.landmarkAt(CACHE_X, CACHE_Z),
                MesaAmphitheatre.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {ARCH_X, ARCH_Z + 12},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 15},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    /** Free-standing buttes: banded stacks of rounded layers narrowing toward the top, and thin hoodoo spires. */
    private static void buttes(DistrictKit k) {
        for (int z = 24; z <= 158; z += 9) {
            for (int x = 24; x <= 158; x += 9) {
                if (hash(x, 0, z, 138) > 0.62) continue;
                int cx = x + (int) (hash(x, 1, z, 138) * 7) - 3, cz = z + (int) (hash(x, 2, z, 138) * 7) - 3;
                boolean spire = hash(x, 3, z, 138) < 0.4;
                double r0 = spire ? 1.3 + hash(x, 4, z, 138) : 3.2 + hash(x, 4, z, 138) * 2.8;
                int h = spire ? 9 + (int) (hash(x, 5, z, 138) * 12) : 6 + (int) (hash(x, 5, z, 138) * 8);
                if (k.nearBlocked(cx, cz, (int) Math.ceil(r0) + 2) || SHAPE.ceiling(cx, cz) - 6 < 5 + h) continue;
                for (int y = 5; y < 5 + h; y++) {
                    double r = spire ? r0 * (1 - 0.35 * (y - 5.0) / h) : r0 * (1 - 0.30 * (y - 5.0) / h) + (y % 4 == 0 ? 0.6 : 0);
                    // The hoodoo cap: a wider slab on top of each spire.
                    if (spire && y >= 4 + h - 1) r += 1.2;
                    for (int dz = -7; dz <= 7; dz++) {
                        for (int dx = -7; dx <= 7; dx++) {
                            if (Math.hypot(dx, dz) <= r) k.canvas.set(cx + dx, y, cz + dz, Prims.band(y));
                        }
                    }
                }
                k.reserveCircle(cx, cz, r0 + 3);
            }
        }
    }

    /** Banded stalactites hanging from the roof. */
    private static void stalactites(DistrictKit k) {
        for (int z = 22; z <= 158; z += 6) {
            for (int x = 22; x <= 158; x += 6) {
                if (hash(x, 0, z, 139) > 0.45) continue;
                int cx = x + (int) (hash(x, 1, z, 139) * 5) - 2, cz = z + (int) (hash(x, 2, z, 139) * 5) - 2;
                if (!SHAPE.inner(cx, cz)) continue;
                int ceil = SHAPE.ceiling(cx, cz), len = 4 + (int) (hash(x, 3, z, 139) * 11);
                for (int j = 0; j <= len; j++) {
                    double rad = 2.6 * (1 - (double) j / len);
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (Math.hypot(dx, dz) <= rad + 0.2) k.canvas.set(cx + dx, ceil - 1 - j, cz + dz, Prims.band(ceil - 1 - j));
                        }
                    }
                }
            }
        }
    }

    /** Camp fires: a lit signal campfire on a hay bale in a ring of red sandstone, off the routes. */
    private static void campFires(DistrictKit k) {
        int placed = 0;
        for (int z = 28; z <= 156 && placed < 8; z += 13) {
            for (int x = 28; x <= 156 && placed < 8; x += 13) {
                if (hash(x, 0, z, 140) > 0.35) continue;
                int cx = x + (int) (hash(x, 1, z, 140) * 5) - 2, cz = z + (int) (hash(x, 2, z, 140) * 5) - 2;
                if (k.nearBlocked(cx, cz, 5)) continue;
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        double r = Math.hypot(dx, dz);
                        if (r < 0.5) {
                            k.canvas.set(cx, 3, cz, AshenLayout.HAY);
                            k.canvas.set(cx, 4, cz, AshenLayout.CAMPFIRE);
                        } else if (r <= 1.6) {
                            k.canvas.set(cx + dx, 4, cz + dz, RED_SANDSTONE);
                            k.canvas.set(cx + dx, 5, cz + dz, CUT);
                        } else if (r <= 2.3) {
                            k.canvas.set(cx + dx, 4, cz + dz, SMOOTH);
                        }
                    }
                }
                k.reserveCircle(cx, cz, 4);
                placed++;
            }
        }
    }

    private static void plants(DistrictKit k) {
        for (int z = 21; z <= 160; z++) {
            for (int x = 21; x <= 160; x++) {
                if (!SHAPE.inner(x, z) || !k.canvas.get(x, 4, z).equals(RED_SAND) || !k.canvas.isAir(x, 5, z) || k.nearBlocked(x, z, 2)) continue;
                double h = hash(x, 0, z, 141);
                if (h < 0.008) {
                    int len = 1 + (int) (hash(x, 1, z, 141) * 3);
                    for (int y = 5; y < 5 + len; y++) k.canvas.set(x, y, z, "minecraft:cactus[age=0]");
                } else if (h < 0.028) {
                    k.canvas.set(x, 5, z, "minecraft:dead_bush");
                }
            }
        }
    }

    /** Glowstone set into the roof so it carries points of warm light. */
    private static void skylights(DistrictKit k) {
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (SHAPE.inner(x, z) && hash(x, 0, z, 142) < 0.006) k.canvas.set(x, SHAPE.ceiling(x, z) - 1, z, "minecraft:glowstone");
            }
        }
    }
}
