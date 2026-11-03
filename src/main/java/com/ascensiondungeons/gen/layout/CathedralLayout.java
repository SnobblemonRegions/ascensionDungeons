package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.BloodChancel;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.ReliquaryCrypt;
import com.ascensiondungeons.gen.landmark.RoseWindowApse;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Crimson Cathedral: a long nave with chapels off both sides. The nave runs from the north gate to the south gate under a
 * pointed vault; three chapels open to the west, two to the east and a round chancel to the south-east. A red carpet runs
 * the length of the nave between rows of pews, columns line the arcades, chandeliers hang from the vault and backlit
 * stained-glass windows stand in the walls. The Rose Window Apse ends the west middle chapel, the Reliquary Crypt stands in
 * the south-west chapel, and the Blood Chancel arena is the round chapel in the south-east, its door onto the nave.
 */
public final class CathedralLayout {
    private CathedralLayout() {}

    public static final int WALL = 11;
    public static final int APSE_X = 20, APSE_Z = 73;
    public static final int CACHE_X = 30, CACHE_Z = 122;
    public static final int ARENA_X = 134, ARENA_Z = 128;
    public static final double ARENA_DOOR = 180;
    private static final double ARENA_ROOM = 24.4;

    private record Rect(int x0, int z0, int x1, int z1) {}

    private static final Rect NAVE = new Rect(62, 20, 114, 161);
    private static final Rect[] CHAPELS = {
            new Rect(20, 26, 62, 56), new Rect(20, 73, 62, 103), new Rect(20, 120, 62, 150),
            new Rect(114, 26, 161, 56), new Rect(114, 73, 161, 103)};

    private static boolean inRect(int x, int z, Rect r, int m) {
        return x >= r.x0() - m && x <= r.x1() + m && z >= r.z0() - m && z <= r.z1() + m;
    }

    private static boolean in(int x, int z, int m) {
        if (inRect(x, z, NAVE, m)) return true;
        for (Rect r : CHAPELS) if (inRect(x, z, r, m)) return true;
        return Math.hypot(x - ARENA_X, z - ARENA_Z) <= ARENA_ROOM + m;
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return in(x, z, 0); }

        @Override public boolean outer(int x, int z) { return in(x, z, WALL) && x >= 0 && z >= 0 && x <= 175 && z <= 175; }

        @Override public int ceiling(int x, int z) {
            if (inRect(x, z, NAVE, 0)) return 56 + (int) Math.round(16 * (1 - Math.min(1, Math.abs(x - 88) / 31.0)));
            double d = Math.hypot(x - ARENA_X, z - ARENA_Z);
            if (d <= ARENA_ROOM) return 50 + (int) Math.round(14 * (1 - (d / ARENA_ROOM) * (d / ARENA_ROOM)));
            return 46 + (int) Math.round(3 * hash(x / 5, 0, z / 5, 171));
        }
    };

    private static final String NETHER = "minecraft:nether_bricks";
    private static final String RED_BRICKS = "minecraft:red_nether_bricks";
    private static final String BLACK = "minecraft:polished_blackstone_bricks";
    private static final String POLISHED = "minecraft:polished_blackstone";
    private static final String PLANKS = "minecraft:crimson_planks";
    private static final String STEM = "minecraft:crimson_stem[axis=y]";
    private static final String WART = "minecraft:nether_wart_block";
    private static final String GILDED = "minecraft:gilded_blackstone";
    private static final String GLOW = "minecraft:shroomlight";
    private static final String CHAIN = "minecraft:chain[axis=y,waterlogged=false]";
    private static final String LANTERN = "minecraft:lantern[hanging=true,waterlogged=false]";
    private static final String PEW = "minecraft:crimson_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]";

    private static String floor(int x, int z) {
        return pick(x, 4, z, 181, RED_BRICKS, .32, POLISHED, .26, NETHER, .14, "minecraft:blackstone", .14, "minecraft:crimson_nylium", .06,
                BLACK, .08);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 182, PLANKS, .55, "minecraft:crimson_planks", .25, RED_BRICKS, .20);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 183, RED_BRICKS, .30, WART, .14, PLANKS, .14, STEM, .10, BLACK, .16, NETHER, .10, GILDED, .06);
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 184, RED_BRICKS, .40, POLISHED, .35, NETHER, .25);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(CathedralLayout::floor, "minecraft:stone", "minecraft:stone", CathedralLayout::wall);
        k.gates(RED_BRICKS, GILDED);
        int last = Template.LAST;

        k.roadSeg(88, 0, 88, last, CathedralLayout::road, GILDED);
        k.roadSeg(114, 88, last, 88, CathedralLayout::road, GILDED);
        k.roadSeg(APSE_X + 16, 88, 62, 88, CathedralLayout::road, GILDED);
        k.roadSeg(CACHE_X + 7, CACHE_Z + 15, 88, CACHE_Z + 15, CathedralLayout::road, GILDED);
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], 88, door[2], CathedralLayout::road, GILDED);
        k.reserveRect(APSE_X, APSE_Z, APSE_X + 14, APSE_Z + 29, 0);
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 12, 0);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, CathedralLayout::arenaFloor, CathedralLayout::wall, "minecraft:chiseled_nether_bricks", POLISHED);

        pews(k);
        arcade(k);
        windows(k);
        chandeliers(k);
        vines(k);
        candles(k);
        k.lights();

        List<Landmark> landmarks = List.of(RoseWindowApse.landmarkAt(APSE_X, APSE_Z), ReliquaryCrypt.landmarkAt(CACHE_X, CACHE_Z),
                BloodChancel.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {APSE_X + 18, 88},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 15},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    /** Pew rows either side of the carpet, a seat of stairs with an end-post of blackstone. */
    private static void pews(DistrictKit k) {
        for (int z = 28; z <= 156; z += 4) {
            for (int side : new int[] {-1, 1}) {
                int x0 = side < 0 ? 66 : 97, x1 = side < 0 ? 80 : 111;
                boolean blocked = false;
                for (int x = x0; x <= x1 && !blocked; x++) blocked = k.reserved[x][z] || !SHAPE.inner(x, z);
                if (blocked || z > 78 && z < 98) continue;
                String seat = String.format(PEW, side < 0 ? "east" : "west");
                for (int x = x0; x <= x1; x++) k.canvas.set(x, 5, z, seat);
                k.canvas.set(side < 0 ? x0 - 1 : x1 + 1, 5, z, POLISHED);
            }
        }
    }

    /** Columns along both arcades of the nave, blackstone-footed and gilded under the capital. */
    private static void arcade(DistrictKit k) {
        for (int z = 26; z <= 152; z += 14) {
            for (int x : new int[] {63, 111}) {
                if (k.reserved[x][z] || !SHAPE.inner(x, z)) continue;
                int top = SHAPE.ceiling(x, z) - 1;
                for (int y = 5; y <= top; y++) {
                    String s = y <= 7 ? BLACK : y == top - 5 ? GILDED : y >= top - 3 ? BLACK : y % 6 == 0 ? STEM : RED_BRICKS;
                    k.canvas.fill(x, y, z, x + 1, y, z + 1, s);
                }
                k.reserveRect(x, z, x + 1, z + 1, 0);
            }
        }
    }

    /** Backlit stained glass in the outer walls: 3 wide, 10 tall, with shroomlight behind. */
    private static void windows(DistrictKit k) {
        String[] glass = {"minecraft:red_stained_glass", "minecraft:orange_stained_glass", "minecraft:red_stained_glass"};
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                int[] n = null;
                if (!SHAPE.inner(x - 1, z)) n = new int[] {-1, 0, z};
                else if (!SHAPE.inner(x + 1, z)) n = new int[] {1, 0, z};
                else if (!SHAPE.inner(x, z - 1)) n = new int[] {0, -1, x};
                else if (!SHAPE.inner(x, z + 1)) n = new int[] {0, 1, x};
                if (n == null || n[2] % 12 > 2 || Math.hypot(x - ARENA_X, z - ARENA_Z) < ARENA_ROOM + 3) continue;
                for (int y = 12; y <= 21; y++) {
                    k.canvas.set(x, y, z, glass[(y + n[2]) % 3]);
                    k.canvas.set(x + n[0], y, z + n[1], GLOW);
                }
            }
        }
    }

    /** Chandeliers down the nave: a chain from the vault to a cross of chain arms, a lantern hanging at each tip. */
    private static void chandeliers(DistrictKit k) {
        for (int z = 30; z <= 152; z += 22) {
            int ceil = SHAPE.ceiling(88, z), y = ceil - 12;
            for (int yy = y; yy < ceil; yy++) k.canvas.set(88, yy, z, CHAIN);
            k.canvas.set(88, y - 1, z, LANTERN);
            for (int s = 1; s <= 3; s++) {
                k.canvas.set(88 + s, y, z, "minecraft:chain[axis=x,waterlogged=false]");
                k.canvas.set(88 - s, y, z, "minecraft:chain[axis=x,waterlogged=false]");
                k.canvas.set(88, y, z + s, "minecraft:chain[axis=z,waterlogged=false]");
                k.canvas.set(88, y, z - s, "minecraft:chain[axis=z,waterlogged=false]");
            }
            for (int[] d : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) k.canvas.set(88 + d[0], y - 1, z + d[1], LANTERN);
            for (int[] d : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) k.canvas.set(88 + d[0], y, z + d[1], GILDED);
        }
    }

    /** Weeping vines hanging from the vault ribs. */
    private static void vines(DistrictKit k) {
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z) || hash(x, 0, z, 185) > 0.018) continue;
                int ceil = SHAPE.ceiling(x, z), len = 3 + (int) (hash(x, 1, z, 185) * 8);
                if (k.canvas.get(x, ceil - 1, z).startsWith("minecraft:chain")) continue;
                for (int j = 0; j < len; j++) {
                    if (!k.canvas.isAir(x, ceil - 1 - j, z)) break;
                    k.canvas.set(x, ceil - 1 - j, z, j == len - 1 ? "minecraft:weeping_vines[age=25]" : "minecraft:weeping_vines_plant");
                }
            }
        }
    }

    /** Red candles: clusters lit on the pew ends and in the chapels. */
    private static void candles(DistrictKit k) {
        for (int z = 22; z <= 158; z += 9) {
            for (int x = 22; x <= 158; x += 9) {
                if (hash(x, 0, z, 186) > 0.22 || k.nearBlocked(x, z, 2) || !k.canvas.isAir(x, 5, z)) continue;
                k.canvas.set(x, 5, z, "minecraft:red_candle[candles=" + (1 + (int) (hash(x, 1, z, 186) * 4)) + ",lit=true,waterlogged=false]");
            }
        }
    }
}
