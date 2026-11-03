package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.ArchiveVault;
import com.ascensiondungeons.gen.landmark.AtlasTower;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.ReadingRotunda;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Gilded Library: nine reading rooms in a three-by-three grid, joined by doorways. The north, east and south gates open into
 * the middle rooms of those sides; the Atlas Tower fills the central atrium; the Archive Vault stands in the north-east room
 * and the Reading Rotunda arena in the south-east. Every other room is its own library: rows of bookshelves in aisles of
 * different directions, with red runners between the doors, chandeliers on chains and lit candles on the shelf tops.
 */
public final class LibraryLayout {
    private LibraryLayout() {}

    public static final int WALL = 11;
    public static final int CACHE_X = 130, CACHE_Z = 22;
    public static final int ARENA_X = 137, ARENA_Z = 137;
    public static final double ARENA_DOOR = 180;
    public static final int TOWER_X = 87, TOWER_Z = 87;

    private static final int[][] COLS = {{20, 60}, {66, 108}, {114, 161}};
    private static final int[][] ROWS = {{20, 60}, {66, 108}, {114, 161}};
    /** Doorways: {col, row, direction} where direction 0 is east (to col+1) and 1 is south (to row+1). */
    private static final int[][] DOORS = {
            {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0}, {0, 2, 0}, {1, 2, 0},
            {0, 0, 1}, {1, 0, 1}, {1, 1, 1}};

    private static int centre(int[] r) { return (r[0] + r[1]) / 2; }

    private static boolean in(int x, int z, int m) {
        for (int[] c : COLS) for (int[] r : ROWS) {
            if (x >= c[0] - m && x <= c[1] + m && z >= r[0] - m && z <= r[1] + m) return true;
        }
        for (int[] d : DOORS) {
            int[] c = COLS[d[0]], r = ROWS[d[1]];
            if (d[2] == 0) {
                int zc = centre(r);
                if (x >= c[1] - m && x <= COLS[d[0] + 1][0] + m && z >= zc - 4 - m && z <= zc + 4 + m) return true;
            } else {
                int xc = centre(c);
                if (z >= r[1] - m && z <= ROWS[d[1] + 1][0] + m && x >= xc - 4 - m && x <= xc + 4 + m) return true;
            }
        }
        return false;
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return in(x, z, 0); }

        @Override public boolean outer(int x, int z) { return in(x, z, WALL) && x >= 0 && z >= 0 && x <= 175 && z <= 175; }

        @Override public int ceiling(int x, int z) {
            if (x >= 66 && x <= 108 && z >= 66 && z <= 108) {
                double d = Math.min(1, Math.hypot(x - 87, z - 87) / 30.0);
                return 56 + (int) Math.round(12 * (1 - d * d));
            }
            return 44 + (int) Math.round(4 * hash(x / 6, 0, z / 6, 191));
        }
    };

    private static final String DARK = "minecraft:dark_oak_planks";
    private static final String SPRUCE = "minecraft:spruce_planks";
    private static final String SHELF = "minecraft:bookshelf";
    private static final String LOG = "minecraft:stripped_dark_oak_log[axis=y]";
    private static final String GOLD = "minecraft:gold_block";
    private static final String RUG = "minecraft:red_wool";
    private static final String LANTERN = "minecraft:lantern[hanging=true,waterlogged=false]";
    private static final String CHAIN = "minecraft:chain[axis=y,waterlogged=false]";

    private static String floor(int x, int z) {
        return pick(x, 4, z, 192, DARK, .36, SPRUCE, .36, "minecraft:brown_terracotta", .10, "minecraft:stripped_spruce_log[axis=y]", .12,
                "minecraft:stripped_dark_oak_log[axis=y]", .06);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 193, RUG, .90, "minecraft:red_terracotta", .10);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 194, SHELF, .32, DARK, .26, SPRUCE, .14, LOG, .14, "minecraft:dark_oak_log[axis=y]", .10, GOLD, .04);
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 195, DARK, .45, SPRUCE, .40, "minecraft:brown_terracotta", .15);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(LibraryLayout::floor, "minecraft:stone", "minecraft:stone", LibraryLayout::wall);
        k.gates(DARK, GOLD);
        int last = Template.LAST;

        // Runners: gate spurs, and a runner through each doorway joining the centres of the rooms.
        k.roadSeg(88, 0, 88, centre(ROWS[0]), LibraryLayout::road, SPRUCE);
        k.roadSeg(88, centre(ROWS[2]), 88, last, LibraryLayout::road, SPRUCE);
        k.roadSeg(centre(COLS[2]), 88, last, 88, LibraryLayout::road, SPRUCE);
        for (int[] d : DOORS) {
            int x0 = centre(COLS[d[0]]), z0 = centre(ROWS[d[1]]);
            int x1 = d[2] == 0 ? centre(COLS[d[0] + 1]) : x0, z1 = d[2] == 1 ? centre(ROWS[d[1] + 1]) : z0;
            k.roadSeg(x0, z0, x1, z1, LibraryLayout::road, SPRUCE);
        }
        k.reserveRect(TOWER_X - 12, TOWER_Z - 12, TOWER_X + 12, TOWER_Z + 12, 0);
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 12, 0);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, LibraryLayout::arenaFloor, LibraryLayout::wall, "minecraft:bookshelf", DARK);

        stacks(k);
        chandeliers(k);
        candles(k);
        k.lights();

        List<Landmark> landmarks = List.of(AtlasTower.landmarkAt(TOWER_X + 1, TOWER_Z + 1), ArchiveVault.landmarkAt(CACHE_X, CACHE_Z),
                ReadingRotunda.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {TOWER_X + 1, TOWER_Z + 16},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 15},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    /** Bookshelf stacks in aisles, direction alternating by room: two thick, six high, with a lantern on every other top. */
    private static void stacks(DistrictKit k) {
        for (int ci = 0; ci < 3; ci++) {
            for (int ri = 0; ri < 3; ri++) {
                if (ci == 1 && ri == 1 || ci == 2 && ri == 2) continue;          // the atrium and the arena room
                int[] c = COLS[ci], r = ROWS[ri];
                boolean alongX = (ci + ri) % 2 == 0;
                int span = alongX ? r[1] - r[0] : c[1] - c[0];
                for (int off = 6; off < span - 5; off += 6) {
                    int fixed = (alongX ? r[0] : c[0]) + off;
                    int a0 = (alongX ? c[0] : r[0]) + 3, a1 = (alongX ? c[1] : r[1]) - 3;
                    for (int t = a0; t <= a1; t++) {
                        int x = alongX ? t : fixed, z = alongX ? fixed : t;
                        if (!clear(k, x, z) || !clear(k, alongX ? x : x + 1, alongX ? z + 1 : z)) continue;
                        // Gaps every ten blocks so the aisles can be crossed.
                        if ((t - a0) % 10 >= 8) continue;
                        for (int y = 5; y <= 10; y++) {
                            k.canvas.set(x, y, z, y == 10 && (t + off) % 4 == 0 ? GOLD : SHELF);
                            k.canvas.set(alongX ? x : x + 1, y, alongX ? z + 1 : z, y == 10 && (t + off) % 4 == 2 ? GOLD : SHELF);
                        }
                        if ((t + off) % 6 == 0) k.canvas.set(x, 11, z, "minecraft:lantern[hanging=false,waterlogged=false]");
                        else if ((t + off) % 6 == 3) k.canvas.set(x, 11, z, "minecraft:candle[candles=3,lit=true,waterlogged=false]");
                    }
                }
            }
        }
    }

    private static boolean clear(DistrictKit k, int x, int z) {
        for (int dz = -5; dz <= 5; dz++) {
            for (int dx = -5; dx <= 5; dx++) {
                int a = x + dx, b = z + dz;
                if (a < 0 || b < 0 || a > 175 || b > 175) return false;
            }
        }
        return SHAPE.inner(x, z) && !k.reserved[x][z] && !k.road[x][z] && !nearRoad(k, x, z);
    }

    private static boolean nearRoad(DistrictKit k, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (k.road[x + dx][z + dz] || k.reserved[x + dx][z + dz]) return true;
        return false;
    }

    /** A chandelier at the centre of each room. */
    private static void chandeliers(DistrictKit k) {
        for (int[] c : COLS) {
            for (int[] r : ROWS) {
                int x = centre(c), z = centre(r);
                if (k.reserved[x][z] && !k.road[x][z]) continue;
                int ceil = SHAPE.ceiling(x, z);
                for (int y = ceil - 10; y < ceil; y++) k.canvas.set(x, y, z, CHAIN);
                k.canvas.set(x, ceil - 11, z, LANTERN);
                for (int[] d : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                    for (int s = 1; s <= 3; s++) {
                        k.canvas.set(x + d[0] * s / 3, ceil - 10, z + d[1] * s / 3,
                                d[0] != 0 ? "minecraft:chain[axis=x,waterlogged=false]" : "minecraft:chain[axis=z,waterlogged=false]");
                    }
                    k.canvas.set(x + d[0], ceil - 11, z + d[1], LANTERN);
                }
            }
        }
    }

    /** Lit candle clusters on the floor of the rooms, off the runners. */
    private static void candles(DistrictKit k) {
        for (int z = 22; z <= 158; z += 7) {
            for (int x = 22; x <= 158; x += 7) {
                if (hash(x, 0, z, 196) > 0.20 || k.nearBlocked(x, z, 2) || !k.canvas.isAir(x, 5, z)) continue;
                k.canvas.set(x, 5, z, "minecraft:candle[candles=" + (1 + (int) (hash(x, 1, z, 196) * 4)) + ",lit=true,waterlogged=false]");
            }
        }
    }
}
