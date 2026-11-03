package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.MycelialCrown;
import com.ascensiondungeons.gen.landmark.MyceliumRing;
import com.ascensiondungeons.gen.landmark.SporeShelf;
import com.ascensiondungeons.gen.landmark.SporekeepersCache;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Fungal Grotto: one big irregular cavern. Five lobes with wobbling walls merge into a single hall, with straight halls
 * leading to the three gates and square alcoves for the cache and the shelf. The Mycelial Crown stands in the middle
 * with a causeway ring round it, the Sporekeeper's Cache is set into the north wall of the western alcove, the Spore
 * Shelf winds off the north spur, and the Mycelium Ring fills the south-west lobe. Giant mushrooms with broad caps,
 * clusters of small ones, natural rock columns and hanging stalactites tipped with shroomlight fill the rest.
 */
public final class FungalLayout {
    private FungalLayout() {}

    public static final int WALL = 11;
    public static final int CROWN_X = 88, CROWN_Z = 88;
    public static final double RING_R = 19;
    public static final int CACHE_X = 33, CACHE_Z = 36;
    public static final int SHELF_X = 127, SHELF_Z = 36;
    public static final int ARENA_X = 48, ARENA_Z = 128;
    public static final double ARENA_DOOR = 0;

    private record Lobe(double x, double z, double r, double phase) {}

    private record Rect(int x0, int z0, int x1, int z1) {}

    private static final Lobe[] LOBES = {
            new Lobe(88, 88, 44, 0.4), new Lobe(48, 46, 28, 1.7), new Lobe(128, 48, 28, 2.9),
            new Lobe(130, 126, 30, 0.9), new Lobe(48, 128, 32, 2.2)};

    private static final Rect[] RECTS = {
            new Rect(79, 20, 97, 60),         // north hall and spur
            new Rect(79, 116, 97, 161),       // south hall and spur
            new Rect(120, 79, 161, 97),       // east hall and spur
            new Rect(22, 36, 58, 66),         // the cache alcove
            new Rect(118, 30, 158, 64),       // the shelf alcove
            new Rect(20, 76, 44, 100)};       // a dead-end nook in the west, which keeps the west wall solid

    private static double reff(Lobe l, double dx, double dz) {
        double th = Math.atan2(dz, dx);
        return l.r * (1 + 0.10 * Math.sin(3 * th + l.phase) + 0.05 * Math.sin(7 * th + 2 * l.phase));
    }

    private static boolean within(int x, int z, int m) {
        for (Lobe l : LOBES) {
            double dx = x - l.x, dz = z - l.z;
            if (Math.hypot(dx, dz) <= reff(l, dx, dz) + m) return true;
        }
        for (Rect r : RECTS) if (x >= r.x0 - m && x <= r.x1 + m && z >= r.z0 - m && z <= r.z1 + m) return true;
        return false;
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) {
            return x >= 20 && x <= 161 && z >= 20 && z <= 161 && within(x, z, 0);
        }

        @Override public boolean outer(int x, int z) {
            return x >= 6 && x <= 172 && z >= 6 && z <= 172 && within(x, z, WALL);
        }

        @Override public int ceiling(int x, int z) {
            double d = Math.min(1, Math.hypot(x - 88, z - 88) / 80.0);
            return (int) (40 + 22 * (1 - d * d) + 6 * (hash(x / 5, 0, z / 5, 61) - 0.5));
        }
    };

    private static String floor(int x, int z) {
        return pick(x, 4, z, 62, "minecraft:mycelium", .50, "minecraft:moss_block", .18, "minecraft:podzol", .12,
                "minecraft:coarse_dirt", .08, "minecraft:packed_mud", .12);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 63, "minecraft:packed_mud", .50, "minecraft:mud_bricks", .35, "minecraft:coarse_dirt", .15);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 64, "minecraft:tuff", .32, "minecraft:stone", .25, "minecraft:moss_block", .15,
                "minecraft:mud_bricks", .16, "minecraft:brown_mushroom_block", .12);
    }

    private static String floorArena(int x, int z) {
        return pick(x, 4, z, 65, "minecraft:packed_mud", .45, "minecraft:mud_bricks", .30, "minecraft:coarse_dirt", .25);
    }

    private static final String EDGE = "minecraft:dark_oak_planks";
    private static final String STEM = "minecraft:mushroom_stem";
    private static final String LIGHT = "minecraft:shroomlight";

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(FungalLayout::floor, "minecraft:stone", "minecraft:stone", FungalLayout::wall);
        k.gates("minecraft:packed_mud", EDGE);

        // Causeways: the three spurs, the ring round the Crown, and a road to each landmark.
        int last = com.ascensiondungeons.gen.Template.LAST;
        k.roadSeg(88, last, 88, CROWN_Z + RING_R, FungalLayout::road, EDGE);
        k.roadSeg(88, 0, 88, CROWN_Z - RING_R, FungalLayout::road, EDGE);
        k.roadSeg(last, 88, CROWN_X + RING_R, 88, FungalLayout::road, EDGE);
        k.roadRing(CROWN_X, CROWN_Z, RING_R, FungalLayout::road, EDGE);
        k.reserveCircle(CROWN_X, CROWN_Z, RING_R - 3);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, FungalLayout::floorArena, FungalLayout::wall, STEM, "minecraft:packed_mud");
        k.roadSeg(ARENA_X + 20, ARENA_Z, 88, ARENA_Z, FungalLayout::road, EDGE);
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 17, 0);
        k.roadSeg(CACHE_X + 7, CACHE_Z + 13, 60, 72, FungalLayout::road, EDGE);
        k.roadSeg(60, 72, CROWN_X - RING_R, CROWN_Z, FungalLayout::road, EDGE);
        k.reserveRect(SHELF_X - 1, SHELF_Z - 1, SHELF_X + 27, SHELF_Z + 17, 0);
        k.roadSeg(88, SHELF_Z + 8, SHELF_X, SHELF_Z + 8, FungalLayout::road, EDGE);

        rockColumns(k);
        giantMushrooms(k);
        smallMushrooms(k);
        stalactites(k);
        ceilingGlow(k);
        // Falling spores: a spore blossom under some stalactite tips, but not over the Spore Shelf (no particle clouds there).
        for (int[] t : k.hangingTips(4, 26)) {
            if (hash(t[0], t[1], t[2], 79) < 0.08 && Math.hypot(t[0] - (SHELF_X + 13), t[2] - (SHELF_Z + 8)) > 34) {
                k.canvas.set(t[0], t[1], t[2], "minecraft:spore_blossom");
            }
        }
        k.scatter(66, 12, 0.5, 2, 6, (x, z) -> pick(x, 5, z, 67, "minecraft:moss_block", .4, "minecraft:mycelium", .4,
                "minecraft:podzol", .2));
        k.lights();

        List<Landmark> landmarks = List.of(MycelialCrown.landmarkAt(CROWN_X, CROWN_Z),
                SporekeepersCache.landmarkAt(CACHE_X, CACHE_Z), SporeShelf.landmarkAt(SHELF_X, SHELF_Z),
                MyceliumRing.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {CROWN_X, (int) (CROWN_Z + RING_R)},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 13},
                "explore", new int[] {SHELF_X + 2, SHELF_Z + 8},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    private static boolean nearReserved(DistrictKit k, int x, int z, int m) {
        for (int dz = -m; dz <= m; dz++) {
            for (int dx = -m; dx <= m; dx++) {
                int a = x + dx, b = z + dz;
                if (a < 0 || b < 0 || a >= Canvas.SIZE || b >= Canvas.SIZE || k.reserved[a][b]) return true;
            }
        }
        return false;
    }

    private static boolean roomFor(DistrictKit k, int cx, int cz, int radius) {
        if (nearReserved(k, cx, cz, radius)) return false;
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * i / 6;
            if (!SHAPE.inner((int) Math.round(cx + (radius + 1) * Math.cos(a)), (int) Math.round(cz + (radius + 1) * Math.sin(a)))) return false;
        }
        return true;
    }

    private static String cap(boolean red, boolean underside, boolean spot) {
        if (spot) return STEM;
        String base = red ? "minecraft:red_mushroom_block" : "minecraft:brown_mushroom_block";
        return underside ? base + "[down=false,up=true,north=true,south=true,east=true,west=true]" : base;
    }

    /** A capped mushroom: a stem of the given half-width, a lens-shaped cap {@code thick} deep with a drooping rim. */
    private static void mushroom(DistrictKit k, int cx, int cz, int stemHalf, int height, int radius, int thick, boolean red) {
        int y0 = DistrictKit.FLOOR + height + 1;
        for (int y = DistrictKit.FLOOR + 1; y <= y0; y++) {
            for (int dz = -stemHalf; dz <= stemHalf + (stemHalf == 0 ? 1 : 0); dz++) {
                for (int dx = -stemHalf; dx <= stemHalf + (stemHalf == 0 ? 1 : 0); dx++) k.canvas.set(cx + dx, y, cz + dz, STEM);
            }
        }
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double d = Math.hypot(dx, dz) / radius;
                if (d > 1) continue;
                int top = y0 + (int) Math.round(thick * (1 - d * d));
                boolean rim = d > 0.82;
                for (int y = y0; y <= top; y++) {
                    boolean spot = y == top && hash(cx + dx, y, cz + dz, 68) < 0.07;
                    k.canvas.set(cx + dx, y, cz + dz, cap(hash(cx + dx / 3, 0, cz + dz / 3, 69) < 0.8 ? red : !red, y == y0, spot));
                }
                if (rim) k.canvas.set(cx + dx, y0 - 1, cz + dz, cap(red, false, false));
                else if (d > 0.25 && hash(cx + dx, 1, cz + dz, 70) < 0.05) k.canvas.set(cx + dx, y0 - 1, cz + dz, LIGHT);
            }
        }
        k.reserveCircle(cx, cz, stemHalf + 1.5);
    }

    private static void giantMushrooms(DistrictKit k) {
        for (int z = 24; z <= 158; z += 11) {
            for (int x = 24; x <= 158; x += 11) {
                if (hash(x, 0, z, 71) > 0.8) continue;
                int cx = x + (int) (hash(x, 1, z, 71) * 9) - 4, cz = z + (int) (hash(x, 2, z, 71) * 9) - 4;
                double t = hash(x, 3, z, 71);
                boolean red = hash(x, 4, z, 71) < 0.55;
                int stem, h, r, thick;
                if (t < 0.3) { stem = 0; h = 10 + (int) (hash(x, 5, z, 71) * 7); r = 6 + (int) (hash(x, 6, z, 71) * 4); thick = 3; }
                else if (t < 0.6) { stem = 1; h = 6 + (int) (hash(x, 5, z, 71) * 5); r = 7 + (int) (hash(x, 6, z, 71) * 4); thick = 2; }
                else { stem = 0; h = 3 + (int) (hash(x, 5, z, 71) * 3); r = 3 + (int) (hash(x, 6, z, 71) * 2); thick = 2; }
                if (!roomFor(k, cx, cz, r)) continue;
                mushroom(k, cx, cz, stem, h, r, thick, red);
            }
        }
    }

    private static void smallMushrooms(DistrictKit k) {
        for (int z = 24; z <= 158; z += 7) {
            for (int x = 24; x <= 158; x += 7) {
                if (hash(x, 0, z, 72) > 0.75) continue;
                int cx = x + (int) (hash(x, 1, z, 72) * 5) - 2, cz = z + (int) (hash(x, 2, z, 72) * 5) - 2;
                if (nearReserved(k, cx, cz, 3) || !SHAPE.inner(cx, cz)) continue;
                boolean cluster = hash(x, 3, z, 72) < 0.5;
                k.canvas.set(cx, 5, cz, hash(x, 4, z, 72) < 0.5 ? "minecraft:red_mushroom" : "minecraft:brown_mushroom");
                if (cluster) {
                    k.canvas.set(cx + 1, 5, cz, "minecraft:brown_mushroom");
                    k.canvas.set(cx, 5, cz + 1, "minecraft:red_mushroom");
                }
            }
        }
    }

    /** Natural rock columns, floor to roof, with irregular outlines. */
    private static void rockColumns(DistrictKit k) {
        for (int z = 36; z <= 140; z += 30) {
            for (int x = 36; x <= 140; x += 30) {
                int cx = x + (int) (hash(x, 1, z, 73) * 15) - 7, cz = z + (int) (hash(x, 2, z, 73) * 15) - 7;
                if (hash(x, 0, z, 73) > 0.7 || !roomFor(k, cx, cz, 5)) continue;
                int ceil = SHAPE.ceiling(cx, cz);
                for (int dz = -5; dz <= 5; dz++) {
                    for (int dx = -5; dx <= 5; dx++) {
                        double r = Math.hypot(dx, dz), lim = 3.2 + 1.2 * Math.sin(Math.atan2(dz, dx) * 3 + x);
                        if (r > lim) continue;
                        for (int y = 5; y < ceil; y++) {
                            double flare = y < 9 || y > ceil - 5 ? 1.0 : 0;
                            if (r > lim - 0.8 * (1 - flare) && hash(cx + dx, y, cz + dz, 74) < 0.2) continue;
                            k.canvas.set(cx + dx, y, cz + dz, pick(cx + dx, y, cz + dz, 75, "minecraft:tuff", .5, "minecraft:stone", .3,
                                    "minecraft:moss_block", .2));
                        }
                    }
                }
                k.reserveCircle(cx, cz, 5);
            }
        }
    }

    /** Shroomlights set into the roof here and there, so the dark ceiling has points of amber in it. */
    private static void ceilingGlow(DistrictKit k) {
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (SHAPE.inner(x, z) && hash(x, 0, z, 78) < 0.006) k.canvas.set(x, SHAPE.ceiling(x, z) - 1, z, LIGHT);
            }
        }
    }

    /** Stalactites hanging from the roof, a few tipped with shroomlight. */
    private static void stalactites(DistrictKit k) {
        for (int z = 22; z <= 158; z += 6) {
            for (int x = 22; x <= 158; x += 6) {
                if (hash(x, 0, z, 76) > 0.5) continue;
                int cx = x + (int) (hash(x, 1, z, 76) * 5) - 2, cz = z + (int) (hash(x, 2, z, 76) * 5) - 2;
                if (!SHAPE.inner(cx, cz)) continue;
                int ceil = SHAPE.ceiling(cx, cz), len = 4 + (int) (hash(x, 3, z, 76) * 10);
                for (int j = 0; j <= len; j++) {
                    double rad = 2.4 * (1 - (double) j / len);
                    int y = ceil - 1 - j;
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (Math.hypot(dx, dz) > rad + 0.2) continue;
                            boolean tip = j == len;
                            k.canvas.set(cx + dx, y, cz + dz, tip && hash(cx, 5, cz, 76) < 0.5 ? LIGHT
                                    : pick(cx + dx, y, cz + dz, 77, "minecraft:tuff", .6, "minecraft:stone", .4));
                        }
                    }
                }
            }
        }
    }
}
