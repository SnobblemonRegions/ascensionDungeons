package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.SporeCourt;
import com.ascensiondungeons.gen.landmark.TitanStem;
import com.ascensiondungeons.gen.landmark.WardensRing;
import com.ascensiondungeons.gen.landmark.WartNest;
import java.util.List;
import java.util.Map;

/**
 * Warped Hollow: a hexagonal hollow of warped fungus. The room is a flat-topped hexagon, its east and west points cut off by
 * the walls. Three spokes meet at a central plaza where the Titan Stem stands; the Spore Court arena fills the western
 * point, its door onto the plaza, and the Wart Nest stands in the north-east. Honeycomb fields of hexagonal columns of
 * different heights, giant fungi, twisting vines, soul lanterns and warped roots fill the rest, and stalactites of stem hang
 * from the roof with shroomlights at their tips.
 */
public final class WarpedLayout {
    private WarpedLayout() {}

    public static final int WALL = 11;
    public static final int CX = 88, CZ = 88;
    public static final double APOTHEM = 69;
    public static final int CACHE_X = 117, CACHE_Z = 38;
    public static final int ARENA_X = 47, ARENA_Z = 88;
    public static final double ARENA_DOOR = 0;

    private static double hexNorm(int x, int z) {
        double dx = x - CX, dz = z - CZ;
        double s = Math.sqrt(3) / 2;
        return Math.max(Math.abs(dz), Math.max(Math.abs(dx * s + dz * 0.5), Math.abs(dx * s - dz * 0.5)));
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return x >= 20 && x <= 161 && hexNorm(x, z) <= APOTHEM; }

        @Override public boolean outer(int x, int z) { return hexNorm(x, z) <= APOTHEM + WALL && x >= 0 && x <= 172; }

        @Override public int ceiling(int x, int z) {
            double d = Math.min(1, Math.hypot(x - CX, z - CZ) / 80.0);
            return (int) (52 + 16 * (1 - d * d));
        }
    };

    private static final String NYLIUM = "minecraft:warped_nylium";
    private static final String WART = "minecraft:warped_wart_block";
    private static final String PLANKS = "minecraft:warped_planks";
    private static final String STEM = "minecraft:warped_stem[axis=y]";
    private static final String STRIPPED = "minecraft:stripped_warped_stem[axis=y]";
    private static final String SOUL = "minecraft:soul_soil";
    private static final String SHROOM = "minecraft:shroomlight";
    private static final String LANTERN = "minecraft:soul_lantern[hanging=false,waterlogged=false]";

    private static String floor(int x, int z) {
        return pick(x, 4, z, 201, NYLIUM, .56, SOUL, .16, WART, .08, "minecraft:cyan_terracotta", .08, "minecraft:blackstone", .06, PLANKS, .06);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 202, PLANKS, .55, STRIPPED, .30, "minecraft:cyan_terracotta", .15);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 203, WART, .26, PLANKS, .20, STEM, .14, STRIPPED, .10, "minecraft:cyan_terracotta", .12, "minecraft:blackstone", .10, SOUL, .08);
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 204, PLANKS, .45, NYLIUM, .30, STRIPPED, .25);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(WarpedLayout::floor, "minecraft:stone", "minecraft:stone", WarpedLayout::wall);
        k.gates(PLANKS, WART);
        int last = Template.LAST;

        k.roadSeg(88, 0, 88, last, WarpedLayout::road, WART);
        k.roadSeg(88, 88, last, 88, WarpedLayout::road, WART);
        k.roadRing(CX, CZ, 17, WarpedLayout::road, WART);
        k.reserveCircle(CX, CZ, 14);
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], 71, 88, WarpedLayout::road, WART);
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 12, 0);
        k.roadSeg(CACHE_X + 7, CACHE_Z + 15, 100, 72, WarpedLayout::road, WART);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, WarpedLayout::arenaFloor, WarpedLayout::wall, STRIPPED, PLANKS);

        honeycomb(k);
        giantFungi(k);
        vinesAndRoots(k);
        stalactites(k);
        posts(k);
        k.lights();

        List<Landmark> landmarks = List.of(TitanStem.landmarkAt(CX, CZ), WartNest.landmarkAt(CACHE_X, CACHE_Z),
                SporeCourt.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {88, 110},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 15},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    /** Fields of hexagonal columns of different heights: stepping stones with nylium tops, in honeycomb order. */
    private static void honeycomb(DistrictKit k) {
        double r = 3.0, w = r * Math.sqrt(3);
        for (double[] field : new double[][] {{58, 128, 16}, {122, 118, 18}, {118, 58, 12}, {60, 54, 14}}) {
            for (int row = -8; row <= 8; row++) {
                for (int col = -8; col <= 8; col++) {
                    double cx = field[0] + col * w + (row % 2 != 0 ? w / 2 : 0), cz = field[1] + row * r * 1.5;
                    if (Math.hypot(cx - field[0], cz - field[1]) > field[2]) continue;
                    int icx = (int) Math.round(cx), icz = (int) Math.round(cz);
                    if (k.nearBlocked(icx, icz, 3)) continue;
                    int h = 1 + (int) (hash(icx, 0, icz, 205) * 6);
                    for (int dz = -3; dz <= 3; dz++) {
                        for (int dx = -3; dx <= 3; dx++) {
                            if (hexNormLocal(dx, dz) > 2.3) continue;
                            for (int y = 5; y < 5 + h; y++) {
                                k.canvas.set(icx + dx, y, icz + dz, y == 4 + h ? NYLIUM : y % 3 == 0 ? STRIPPED : STEM);
                            }
                        }
                    }
                    if (h >= 4 && hash(icx, 1, icz, 205) < 0.35) k.canvas.set(icx, 5 + h, icz, LANTERN);
                }
            }
        }
    }

    /** Distance measure for a pointy-topped hexagon: 2.6 at the middle of a side when the corner radius is 3. */
    private static double hexNormLocal(double dx, double dz) {
        double s = Math.sqrt(3) / 2;
        return Math.max(Math.abs(dx), Math.max(Math.abs(0.5 * dx + s * dz), Math.abs(0.5 * dx - s * dz)));
    }

    /** Giant fungi: a stem, a flat wart-block cap and shroomlights hung under it. */
    private static void giantFungi(DistrictKit k) {
        for (int z = 26; z <= 150; z += 12) {
            for (int x = 26; x <= 158; x += 12) {
                if (hash(x, 0, z, 206) > 0.55) continue;
                int cx = x + (int) (hash(x, 1, z, 206) * 5) - 2, cz = z + (int) (hash(x, 2, z, 206) * 5) - 2;
                int h = 7 + (int) (hash(x, 3, z, 206) * 10), r = 4 + (int) (hash(x, 4, z, 206) * 4);
                if (k.nearBlocked(cx, cz, r + 1)) continue;
                for (int y = 5; y < 5 + h; y++) k.canvas.fill(cx, y, cz, cx + (h > 11 ? 1 : 0), y, cz + (h > 11 ? 1 : 0), y % 4 == 0 ? STRIPPED : STEM);
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        double d = Math.hypot(dx, dz) / r;
                        if (d > 1) continue;
                        int top = 5 + h + (int) Math.round(2 * (1 - d * d));
                        for (int y = 5 + h; y <= top; y++) k.canvas.set(cx + dx, y, cz + dz, WART);
                        if (d > 0.3 && hash(cx + dx, 9, cz + dz, 206) < 0.08) k.canvas.set(cx + dx, 4 + h, cz + dz, SHROOM);
                    }
                }
                k.reserveCircle(cx, cz, 2.5);
            }
        }
    }

    /** Twisting vines, warped roots, sprouts and fungi on the open nylium. */
    private static void vinesAndRoots(DistrictKit k) {
        for (int z = 21; z <= 160; z++) {
            for (int x = 21; x <= 160; x++) {
                if (!SHAPE.inner(x, z) || !k.canvas.get(x, 4, z).equals(NYLIUM) || !k.canvas.isAir(x, 5, z) || k.road[x][z] || k.reserved[x][z]) continue;
                double h = hash(x, 0, z, 207);
                if (h < 0.012 && !k.nearBlocked(x, z, 1)) {
                    int len = 3 + (int) (hash(x, 1, z, 207) * 7);
                    for (int y = 5; y < 5 + len; y++) k.canvas.set(x, y, z, y == 4 + len ? "minecraft:twisting_vines[age=25]" : "minecraft:twisting_vines_plant");
                } else if (h < 0.10) k.canvas.set(x, 5, z, "minecraft:warped_roots");
                else if (h < 0.16) k.canvas.set(x, 5, z, "minecraft:nether_sprouts");
                else if (h < 0.17) k.canvas.set(x, 5, z, "minecraft:warped_fungus");
            }
        }
    }

    /** Stem stalactites hanging from the roof, each ending in a shroomlight. */
    private static void stalactites(DistrictKit k) {
        for (int z = 22; z <= 158; z += 6) {
            for (int x = 22; x <= 158; x += 6) {
                if (hash(x, 0, z, 208) > 0.5) continue;
                int cx = x + (int) (hash(x, 1, z, 208) * 5) - 2, cz = z + (int) (hash(x, 2, z, 208) * 5) - 2;
                if (!SHAPE.inner(cx, cz)) continue;
                int ceil = SHAPE.ceiling(cx, cz), len = 4 + (int) (hash(x, 3, z, 208) * 11);
                for (int j = 0; j <= len; j++) {
                    double rad = 2.3 * (1 - (double) j / len);
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (Math.hypot(dx, dz) > rad + 0.2) continue;
                            k.canvas.set(cx + dx, ceil - 1 - j, cz + dz, j == len ? SHROOM : j % 4 == 0 ? STRIPPED : STEM);
                        }
                    }
                }
            }
        }
    }

    /** Soul lantern posts every twelve blocks along the spokes. */
    private static void posts(DistrictKit k) {
        for (int t = 14; t <= 160; t += 12) {
            for (int s : new int[] {-6, 6}) {
                post(k, 88 + s, t);
                if (t >= 106) post(k, t, 88 + s);
            }
        }
    }

    private static void post(DistrictKit k, int x, int z) {
        if (k.nearBlocked(x, z, 1) || k.road[x][z]) return;
        k.canvas.fill(x, 5, z, x, 6, z, STEM);
        k.canvas.set(x, 7, z, LANTERN);
    }
}
