package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.GreatPyramid;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.SarcophagusVault;
import com.ascensiondungeons.gen.landmark.SunCourt;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Sunscorched Necropolis: a gold-and-sand city of the dead under a coffered sandstone vault. Two courtyards share one
 * spine road: a round northern court holds the Great Pyramid on its west side and the Sarcophagus Vault in its north-east
 * wall; a wide southern court holds the Sun Court arena in its west half and the arrival point to the east of it. A hall
 * runs east to the east gate. Obelisk avenues line the spine, papyrus columns stand through both courts, dunes of sand drift
 * between them, and gold-ribbed pilasters rise up the walls.
 */
public final class NecropolisLayout {
    private NecropolisLayout() {}

    public static final int WALL = 11;
    public static final int NORTH_X = 88, NORTH_Z = 60, NORTH_R = 40;
    public static final int PYRAMID_X = 64, PYRAMID_Z = 58;
    public static final int CACHE_X = 104, CACHE_Z = 26;
    public static final int ARENA_X = 60, ARENA_Z = 130;
    public static final double ARENA_DOOR = 0;

    private static final String SAND = "minecraft:sand";
    private static final String SMOOTH = "minecraft:smooth_sandstone";
    private static final String CUT = "minecraft:cut_sandstone";
    private static final String SANDSTONE = "minecraft:sandstone";
    private static final String CHISELED = "minecraft:chiseled_sandstone";
    private static final String GOLD = "minecraft:gold_block";
    private static final String TERRA = "minecraft:orange_terracotta";
    private static final String GLOW = "minecraft:glowstone";
    private static final String CHAIN = "minecraft:chain[axis=y,waterlogged=false]";
    private static final String LANTERN = "minecraft:lantern[hanging=true,waterlogged=false]";

    private static boolean in(int x, int z, int m) {
        if (Math.hypot(x - NORTH_X, z - NORTH_Z) <= NORTH_R + m) return true;
        if (x >= 26 - m && x <= 150 + m && z >= 96 - m && z <= 161 + m) return true;       // the southern court
        if (x >= 110 - m && x <= 161 + m && z >= 79 - m && z <= 97 + m) return true;       // the hall to the east gate
        return x >= 25 - m && x <= 50 + m && z >= 79 - m && z <= 97 + m;                   // a west nook that keeps the west wall solid
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return in(x, z, 0); }

        @Override public boolean outer(int x, int z) { return in(x, z, WALL) && x >= 0 && z >= 0 && x <= 175 && z <= 175; }

        @Override public int ceiling(int x, int z) {
            double d = Math.min(1, Math.hypot(x - 88, z - 92) / 85.0);
            return (int) (46 + 12 * (1 - d * d));
        }
    };

    private static String floor(int x, int z) {
        return pick(x, 4, z, 111, SAND, .42, SMOOTH, .22, CUT, .20, SANDSTONE, .12, TERRA, .04);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 112, SMOOTH, .50, CUT, .30, SANDSTONE, .20);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 113, SANDSTONE, .35, CUT, .25, SMOOTH, .15, CHISELED, .10, TERRA, .10, GOLD, .05);
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 114, SMOOTH, .50, CUT, .30, SANDSTONE, .20);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(NecropolisLayout::floor, SANDSTONE, SANDSTONE, NecropolisLayout::wall);
        k.gates(CUT, CHISELED);

        int last = Template.LAST;
        k.roadSeg(88, 0, 88, last, NecropolisLayout::road, TERRA);
        k.roadSeg(88, 88, last, 88, NecropolisLayout::road, TERRA);
        k.roadSeg(PYRAMID_X, PYRAMID_Z + 14, 88, PYRAMID_Z + 14, NecropolisLayout::road, TERRA);
        k.roadSeg(CACHE_X + 7, CACHE_Z + 15, 88, CACHE_Z + 15, NecropolisLayout::road, TERRA);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, NecropolisLayout::arenaFloor, NecropolisLayout::wall, CHISELED, CUT);
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], 88, door[2], NecropolisLayout::road, TERRA);
        k.reserveRect(PYRAMID_X - 11, PYRAMID_Z - 11, PYRAMID_X + 11, PYRAMID_Z + 11, 1);
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 12, 0);

        k.scatter(115, 12, 0.5, 3, 6, (x, z) -> pick(x, 5, z, 116, SAND, .75, SMOOTH, .25));
        obeliskAvenue(k);
        columns(k);
        pilasters(k);
        torchPosts(k);
        sarcophagi(k);
        sphinxes(k);
        desertPlants(k);
        coffers(k);
        k.lights();

        List<Landmark> landmarks = List.of(GreatPyramid.landmarkAt(PYRAMID_X, PYRAMID_Z),
                SarcophagusVault.landmarkAt(CACHE_X, CACHE_Z), SunCourt.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {PYRAMID_X, PYRAMID_Z + 14},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 15},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    /** Pairs of gold-capped obelisks either side of the spine road, every 18 blocks. */
    private static void obeliskAvenue(DistrictKit k) {
        for (int z = 22; z <= 152; z += 18) {
            for (int sx : new int[] {-9, 8}) {
                int x = 88 + sx;
                if (k.nearBlocked(x, z, 2) || k.nearBlocked(x + 1, z + 1, 2)) continue;
                for (int y = 5; y <= 12; y++) k.canvas.fill(x, y, z, x + 1, y, z + 1, y == 5 ? CHISELED : y % 4 == 0 ? TERRA : SMOOTH);
                k.canvas.fill(x, 13, z, x + 1, 13, z + 1, GOLD);
                k.canvas.set(x, 14, z, GLOW);
                k.reserveRect(x, z, x + 1, z + 1, 1);
            }
        }
    }

    /** Papyrus columns: 2 x 2 shafts up to the vault with chiseled collars and a gold band under the capital. */
    private static void columns(DistrictKit k) {
        for (int z = 26; z <= 156; z += 20) {
            for (int x = 28; x <= 156; x += 20) {
                int cx = x + (int) (hash(x, 1, z, 117) * 5) - 2, cz = z + (int) (hash(x, 2, z, 117) * 5) - 2;
                if (hash(x, 0, z, 117) > 0.7 || k.nearBlocked(cx, cz, 4)) continue;
                int top = SHAPE.ceiling(cx, cz) - 1;
                for (int y = 5; y <= top; y++) {
                    boolean collar = y <= 6 || y >= top - 3;
                    String s = y == top - 4 ? GOLD : collar ? CHISELED : y % 5 == 0 ? CUT : SMOOTH;
                    k.canvas.fill(cx, y, cz, cx + 1, y, cz + 1, s);
                }
                k.reserveRect(cx, cz, cx + 1, cz + 1, 2);
            }
        }
    }

    /** Pilasters up the walls every few blocks, gold-banded. */
    private static void pilasters(DistrictKit k) {
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                boolean edge = !SHAPE.inner(x + 1, z) || !SHAPE.inner(x - 1, z) || !SHAPE.inner(x, z + 1) || !SHAPE.inner(x, z - 1);
                if (!edge || hash(x, 0, z, 118) > 0.14) continue;
                int top = SHAPE.ceiling(x, z) - 4;
                for (int y = 5; y < top; y++) k.canvas.set(x, y, z, y % 7 == 0 ? GOLD : y % 3 == 0 ? CHISELED : SMOOTH);
            }
        }
    }

    /** Sarcophagi: a long chiseled sandstone chest with a gold lid, lying in rows in the courts. */
    private static void sarcophagi(DistrictKit k) {
        for (int z = 24; z <= 158; z += 9) {
            for (int x = 24; x <= 150; x += 11) {
                if (hash(x, 0, z, 121) > 0.35) continue;
                int cx = x + (int) (hash(x, 1, z, 121) * 3), cz = z + (int) (hash(x, 2, z, 121) * 3);
                boolean alongX = hash(x, 3, z, 121) < 0.5;
                int ex = alongX ? 4 : 1, ez = alongX ? 1 : 4;
                if (k.nearBlocked(cx, cz, 2) || k.nearBlocked(cx + ex, cz + ez, 2)) continue;
                k.canvas.fill(cx, 5, cz, cx + ex, 5, cz + ez, CHISELED);
                k.canvas.fill(cx, 6, cz, cx + ex, 6, cz + ez, SMOOTH);
                k.canvas.fill(cx + (alongX ? 1 : 0), 7, cz + (alongX ? 0 : 1), cx + (alongX ? ex - 1 : 1), 7, cz + (alongX ? 1 : ez - 1), GOLD);
                k.reserveRect(cx, cz, cx + ex, cz + ez, 1);
            }
        }
    }

    /** Two sphinxes guarding the spine: a long body, a raised chest and a head with gold headdress, facing the road. */
    private static void sphinxes(DistrictKit k) {
        for (int[] sp : new int[][] {{70, 112, 1}, {106, 112, -1}, {70, 36, 1}}) {
            int x0 = sp[0], z0 = sp[1], face = sp[2];                // the body lies along z, the head toward +z; face mirrors it in x
            if (k.nearBlocked(x0, z0, 1) || k.nearBlocked(x0 + 3, z0 + 10, 1)) continue;
            for (int z = 0; z <= 10; z++) {
                int h = z < 7 ? 2 : z < 9 ? 4 : 3;
                for (int x = 0; x <= 3; x++) {
                    for (int y = 5; y < 5 + h; y++) {
                        if (z >= 9 && (x == 0 || x == 3) && y > 6) continue;
                        k.canvas.set(x0 + x, y, z0 + z, y == 4 + h && z >= 9 ? GOLD : (x + y + z) % 5 == 0 ? CUT : SMOOTH);
                    }
                }
            }
            k.canvas.set(x0 + 1, 8, z0 + 9, GOLD);
            k.canvas.set(x0 + 2, 8, z0 + 9, GOLD);
            k.reserveRect(x0, z0, x0 + 3, z0 + 10, 2);
        }
    }

    /** Short chiseled posts with a torch on top, either side of the spine and the east road, every 12 blocks. */
    private static void torchPosts(DistrictKit k) {
        for (int t = 18; t <= 156; t += 12) {
            post(k, 88 - 6, t);
            post(k, 88 + 6, t);
            if (t >= 100) {
                post(k, t, 88 - 6);
                post(k, t, 88 + 6);
            }
        }
    }

    private static void post(DistrictKit k, int x, int z) {
        if (k.nearBlocked(x, z, 1) || k.road[x][z]) return;
        k.canvas.fill(x, 5, z, x, 7, z, CHISELED);
        k.canvas.set(x, 8, z, "minecraft:torch");
    }

    /** Cactus and dead bushes on open sand. */
    private static void desertPlants(DistrictKit k) {
        for (int z = 21; z <= 160; z++) {
            for (int x = 21; x <= 160; x++) {
                if (!SHAPE.inner(x, z) || !k.canvas.get(x, 4, z).equals(SAND) || !k.canvas.isAir(x, 5, z) || k.nearBlocked(x, z, 2)) continue;
                double h = hash(x, 0, z, 119);
                if (h < 0.010) {
                    int len = 1 + (int) (hash(x, 1, z, 119) * 3);
                    for (int y = 5; y < 5 + len; y++) k.canvas.set(x, y, z, "minecraft:cactus[age=0]");
                } else if (h < 0.030) {
                    k.canvas.set(x, 5, z, "minecraft:dead_bush");
                }
            }
        }
    }

    /** Coffered ceiling: sandstone beams every 24 blocks each way, gold at the crossings with a hanging lantern. */
    private static void coffers(DistrictKit k) {
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z)) continue;
                int ceil = SHAPE.ceiling(x, z), u = (x - 20) % 24, v = (z - 20) % 24;
                if (u < 2 || v < 2) k.canvas.fill(x, ceil - 3, z, x, ceil - 1, z, u < 2 && v < 2 ? GOLD : hash(x, 0, z, 120) < 0.5 ? CUT : CHISELED);
                if (u == 0 && v == 0) {
                    for (int y = ceil - 8; y < ceil - 3; y++) k.canvas.set(x, y, z, CHAIN);
                    k.canvas.set(x, ceil - 9, z, LANTERN);
                }
            }
        }
    }
}
