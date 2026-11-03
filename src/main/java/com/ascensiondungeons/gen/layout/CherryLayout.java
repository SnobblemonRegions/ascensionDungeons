package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.PetalCourt;
import com.ascensiondungeons.gen.landmark.SakuraHeartwood;
import com.ascensiondungeons.gen.landmark.ToriiShrine;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Cherry Grove: a room shaped like a cherry blossom. Five round petals (north, north-east, south-east, south-west and
 * west-north-west) open off a central sacred pond with an island, on which the Sakura Heartwood stands; bridges run to it
 * from the north and south. The Petal Court arena fills the west petal, facing the pond, and the Torii Shrine stands in the
 * north-east petal. The south and east gates open off halls between the petals. Cherry trees, fallen petals, bamboo, koi
 * ponds, stone lanterns and red torii gates fill the rest; the roof is a lattice of cherry beams hung with lanterns.
 */
public final class CherryLayout {
    private CherryLayout() {}

    public static final int WALL = 11;
    public static final int CX = 88, CZ = 88;
    public static final double CORE_R = 31, PETAL_R = 27, PETAL_AT = 42;
    public static final double LAKE_R = 19;
    public static final int CACHE_X = 121, CACHE_Z = 52;
    public static final int ARENA_X = 48, ARENA_Z = 75;
    public static final double ARENA_DOOR = 18;

    /** Petal centres at 270, 342, 54, 126 and 198 degrees round the centre. */
    private static final double[][] PETALS = new double[5][2];

    static {
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(270 + 72 * i);
            PETALS[i][0] = CX + PETAL_AT * Math.cos(a);
            PETALS[i][1] = CZ + PETAL_AT * Math.sin(a);
        }
    }

    private static double sdf(int x, int z) {
        double best = Math.hypot(x - CX, z - CZ) - CORE_R;
        for (double[] p : PETALS) best = Math.min(best, Math.hypot(x - p[0], z - p[1]) - PETAL_R);
        // The south and east gate halls, and a west nook that keeps the west wall solid.
        best = Math.min(best, rect(x, z, 79, 110, 97, 161));
        best = Math.min(best, rect(x, z, 125, 79, 161, 97));
        best = Math.min(best, rect(x, z, 24, 82, 34, 94));
        return best;
    }

    private static double rect(int x, int z, int x0, int z0, int x1, int z1) {
        return Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1));
    }

    private static final float[][] SDF = new float[Canvas.SIZE][Canvas.SIZE];

    static {
        for (int z = 0; z < Canvas.SIZE; z++) for (int x = 0; x < Canvas.SIZE; x++) SDF[x][z] = (float) sdf(x, z);
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return x >= 20 && x <= 161 && z >= 19 && z <= 161 && SDF[x][z] <= 0; }

        @Override public boolean outer(int x, int z) { return SDF[x][z] <= WALL; }

        @Override public int ceiling(int x, int z) {
            double d = Math.min(1, Math.hypot(x - CX, z - CZ) / 80.0);
            return (int) (50 + 14 * (1 - d * d));
        }
    };

    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String MOSS = "minecraft:moss_block";
    private static final String CHERRY = "minecraft:cherry_planks";
    private static final String STRIPPED = "minecraft:stripped_cherry_wood";
    private static final String PINK = "minecraft:pink_terracotta";
    private static final String WHITE = "minecraft:white_terracotta";
    private static final String LEAVES = "minecraft:cherry_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String LOG = "minecraft:cherry_log[axis=y]";
    private static final String LANTERN = "minecraft:lantern[hanging=false,waterlogged=false]";
    private static final String HANG = "minecraft:lantern[hanging=true,waterlogged=false]";
    private static final String CHAIN = "minecraft:chain[axis=y,waterlogged=false]";
    private static final String RED = "minecraft:red_concrete";
    private static final String BLACK = "minecraft:blackstone";

    private static String floor(int x, int z) {
        return pick(x, 4, z, 151, GRASS, .56, MOSS, .16, "minecraft:dirt_path", .08, "minecraft:rooted_dirt", .05, PINK, .03,
                "minecraft:podzol[snowy=false]", .12);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 152, CHERRY, .55, STRIPPED, .30, "minecraft:cherry_planks", .15);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 153, PINK, .30, CHERRY, .25, STRIPPED, .15, "minecraft:cherry_log[axis=y]", .10, WHITE, .12,
                "minecraft:mud_bricks", .08);
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 154, CHERRY, .45, STRIPPED, .30, WHITE, .25);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(CherryLayout::floor, "minecraft:dirt", "minecraft:stone", CherryLayout::wall);
        k.gates(CHERRY, PINK);
        int last = Template.LAST;

        // The sacred pond, its island, and the promenade round it.
        for (int z = 60; z <= 116; z++) {
            for (int x = 60; x <= 116; x++) {
                double r = Math.hypot(x - CX, z - CZ);
                if (r <= LAKE_R) {
                    k.canvas.set(x, 3, z, "minecraft:clay");
                    k.canvas.set(x, 4, z, "minecraft:water[level=0]");
                    k.reserved[x][z] = true;
                }
            }
        }
        k.roadRing(CX, CZ, 25.5, CherryLayout::road, PINK);
        k.reserveCircle(CX, CZ, LAKE_R + 1);
        // Bridges to the island and the three spines.
        k.roadSeg(88, 0, 88, 63, CherryLayout::road, PINK);
        k.roadSeg(88, 63, 88, 82, CherryLayout::road, PINK);
        k.roadSeg(88, 94, 88, 113, CherryLayout::road, PINK);
        k.roadSeg(88, 113, 88, last, CherryLayout::road, PINK);
        k.roadSeg(113, 88, last, 88, CherryLayout::road, PINK);
        // Island: a disc of grass, then the tree module goes on top.
        for (int z = 76; z <= 100; z++) {
            for (int x = 76; x <= 100; x++) {
                if (Math.hypot(x - CX, z - CZ) <= 9.6) {
                    k.canvas.set(x, 4, z, GRASS);
                    k.reserved[x][z] = true;
                }
            }
        }

        // Shrine in the north-east petal, a road from its steps to the promenade.
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 12, 0);
        k.roadSeg(CACHE_X + 7, CACHE_Z + 15, 108, 72, CherryLayout::road, PINK);
        // Petal Court in the west petal, door toward the pond.
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], 66, 86, CherryLayout::road, PINK);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, CherryLayout::arenaFloor, CherryLayout::wall, "minecraft:stripped_cherry_wood", CHERRY);

        torii(k);
        lanternPosts(k);
        kois(k);
        trees(k);
        bamboo(k);
        petalCarpet(k);
        lattice(k);
        k.lights();

        List<Landmark> landmarks = List.of(SakuraHeartwood.landmarkAt(CX, CZ), ToriiShrine.landmarkAt(CACHE_X, CACHE_Z),
                PetalCourt.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {88, 70},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 15},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    /** Vermilion torii gates over the north, south and east spines. */
    private static void torii(DistrictKit k) {
        for (int[] g : new int[][] {{88, 34, 0}, {88, 128, 0}, {140, 88, 1}}) {
            for (int s : new int[] {-6, 6}) {
                int x = g[2] == 0 ? g[0] + s : g[0], z = g[2] == 0 ? g[1] : g[1] + s;
                for (int y = 5; y <= 12; y++) k.canvas.set(x, y, z, RED);
                k.canvas.set(x, 13, z, BLACK);
            }
            for (int s = -8; s <= 8; s++) {
                int x = g[2] == 0 ? g[0] + s : g[0], z = g[2] == 0 ? g[1] : g[1] + s;
                if (Math.abs(s) <= 6) k.canvas.set(x, 11, z, RED);
                k.canvas.set(x, 13, z, BLACK);
                if (Math.abs(s) <= 6) k.canvas.set(x, 14, z, BLACK);
            }
            k.reserveRect(g[0] - 7, g[1] - 7, g[0] + 7, g[1] + 7, 0);
        }
    }

    /** Stone lantern posts along the spines every ten blocks. */
    private static void lanternPosts(DistrictKit k) {
        for (int t = 10; t <= 165; t += 10) {
            for (int s : new int[] {-6, 6}) {
                post(k, 88 + s, t);
                if (t >= 115) post(k, t, 88 + s);
            }
        }
    }

    private static void post(DistrictKit k, int x, int z) {
        if (k.nearBlocked(x, z, 1) || k.road[x][z]) return;
        k.canvas.set(x, 5, z, "minecraft:stone_bricks");
        k.canvas.set(x, 6, z, "minecraft:stone_bricks");
        k.canvas.set(x, 7, z, LANTERN);
    }

    /** Two koi ponds in the south petals. */
    private static void kois(DistrictKit k) {
        for (double[] p : new double[][] {{PETALS[2][0], PETALS[2][1] + 4}, {PETALS[3][0], PETALS[3][1] + 4}}) {
            for (int z = (int) p[1] - 8; z <= p[1] + 8; z++) {
                for (int x = (int) p[0] - 8; x <= p[0] + 8; x++) {
                    double r = Math.hypot(x - p[0], z - p[1]);
                    if (r > 7 || k.road[x][z]) continue;
                    if (r > 5.6) {
                        k.canvas.set(x, 4, z, "minecraft:stone_bricks");
                    } else {
                        k.canvas.set(x, 3, z, "minecraft:clay");
                        k.canvas.set(x, 4, z, "minecraft:water[level=0]");
                        if (hash(x, 0, z, 155) < 0.25) k.canvas.set(x, 5, z, "minecraft:lily_pad");
                    }
                    k.reserved[x][z] = true;
                }
            }
        }
    }

    /** Cherry trees: a leaning trunk and a rounded crown of persistent leaves, off the routes. */
    private static void trees(DistrictKit k) {
        for (int z = 22; z <= 158; z += 8) {
            for (int x = 22; x <= 158; x += 8) {
                if (hash(x, 0, z, 156) > 0.62) continue;
                int cx = x + (int) (hash(x, 1, z, 156) * 5) - 2, cz = z + (int) (hash(x, 2, z, 156) * 5) - 2;
                if (k.nearBlocked(cx, cz, 4)) continue;
                int h = 6 + (int) (hash(x, 3, z, 156) * 5);
                double r = 3.4 + hash(x, 4, z, 156) * 1.6;
                for (int y = 5; y <= 4 + h; y++) k.canvas.set(cx, y, cz, LOG);
                int top = 4 + h;
                for (int y = top - 2; y <= top + 3; y++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        for (int dx = -5; dx <= 5; dx++) {
                            double rr = Math.hypot(dx, dz) / r, ry = (y - (top + 0.5)) / 2.8;
                            if (rr * rr + ry * ry > 1 || hash(cx + dx, y, cz + dz, 157) > 0.9 || !k.canvas.isAir(cx + dx, y, cz + dz)) continue;
                            k.canvas.set(cx + dx, y, cz + dz, LEAVES);
                        }
                    }
                }
                k.reserveCircle(cx, cz, 3);
            }
        }
    }

    /** Bamboo clumps. */
    private static void bamboo(DistrictKit k) {
        for (int z = 24; z <= 158; z += 6) {
            for (int x = 24; x <= 158; x += 6) {
                if (hash(x, 0, z, 158) > 0.22) continue;
                int cx = x + (int) (hash(x, 1, z, 158) * 3), cz = z + (int) (hash(x, 2, z, 158) * 3);
                if (k.nearBlocked(cx, cz, 2) || !k.canvas.get(cx, 4, cz).contains("grass")) continue;
                int h = 5 + (int) (hash(x, 3, z, 158) * 7);
                for (int y = 5; y < 5 + h; y++) {
                    k.canvas.set(cx, y, cz, y >= 4 + h - 1 ? "minecraft:bamboo[age=1,leaves=large,stage=0]"
                            : y >= 4 + h - 3 ? "minecraft:bamboo[age=1,leaves=small,stage=0]" : "minecraft:bamboo[age=1,leaves=none,stage=0]");
                }
                k.reserved[cx][cz] = true;
            }
        }
    }

    /** Fallen petals across the grass. */
    private static void petalCarpet(DistrictKit k) {
        String[] facing = {"north", "east", "south", "west"};
        for (int z = 21; z <= 160; z++) {
            for (int x = 21; x <= 160; x++) {
                if (!SHAPE.inner(x, z) || !k.canvas.get(x, 4, z).contains("grass") || !k.canvas.isAir(x, 5, z)) continue;
                if (hash(x, 0, z, 159) > 0.22 || k.road[x][z]) continue;
                String f = facing[(int) (hash(x, 1, z, 159) * 4)];
                int n = 1 + (int) (hash(x, 2, z, 159) * 4);
                k.canvas.set(x, 5, z, "minecraft:pink_petals[facing=" + f + ",flower_amount=" + n + "]");
            }
        }
    }

    /** A lattice of cherry beams across the roof every 20 blocks, with a chain and lantern at each crossing. */
    private static void lattice(DistrictKit k) {
        for (int z = 19; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z)) continue;
                int ceil = SHAPE.ceiling(x, z), u = (x - 20) % 20, v = (z - 19) % 20;
                if (u < 2 || v < 2) k.canvas.fill(x, ceil - 2, z, x, ceil - 1, z, u < 2 && v < 2 ? LOG : STRIPPED);
                if (u == 0 && v == 0) {
                    for (int y = ceil - 7; y < ceil - 2; y++) k.canvas.set(x, y, z, CHAIN);
                    k.canvas.set(x, ceil - 8, z, HANG);
                }
            }
        }
    }
}
