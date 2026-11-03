package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.Orrery;
import com.ascensiondungeons.gen.landmark.StarArchive;
import com.ascensiondungeons.gen.landmark.WardensRing;
import com.ascensiondungeons.gen.landmark.ZenithRing;
import java.util.List;
import java.util.Map;

/**
 * Lapis Observatory: a quatrefoil of domed chambers. A round hub holds the Orrery on its pedestal, ringed by a promenade
 * and four tall lapis pillars; four round chambers open off it to the north, east, south and west. The three gates open
 * into the north, east and south chambers; the Zenith Ring arena fills the west chamber, its door onto the hub, and the Star
 * Archive stands against the wall of the north chamber. Each chamber has its own dome, so the roof climbs to ninety blocks
 * over the hub and is thick with end-rod stars; study nooks of enchanting tables ringed with bookshelves stand in the
 * side chambers.
 */
public final class ObservatoryLayout {
    private ObservatoryLayout() {}

    public static final int WALL = 11;
    public static final int CX = 88, CZ = 88;
    public static final double HUB_R = 26, SAT_R = 24;
    public static final int CACHE_X = 66, CACHE_Z = 30;
    public static final int ARENA_X = 43, ARENA_Z = 88;
    public static final double ARENA_DOOR = 0;

    private static final int[][] SATS = {{88, 43}, {133, 88}, {88, 133}, {43, 88}};

    private static boolean in(int x, int z, int m) {
        if (Math.hypot(x - CX, z - CZ) <= HUB_R + m) return true;
        for (int[] s : SATS) if (Math.hypot(x - s[0], z - s[1]) <= SAT_R + m) return true;
        return false;
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return x >= 20 && z >= 19 && x <= 161 && z <= 161 && in(x, z, 0); }

        @Override public boolean outer(int x, int z) { return in(x, z, WALL); }

        @Override public int ceiling(int x, int z) {
            double hub = Math.hypot(x - CX, z - CZ) / HUB_R;
            double best = 62 + 28 * (1 - Math.min(1, hub * hub));
            for (int[] s : SATS) {
                double d = Math.hypot(x - s[0], z - s[1]) / SAT_R;
                best = Math.max(best, 56 + 18 * (1 - Math.min(1, d * d)));
            }
            return (int) best;
        }
    };

    private static final String LAPIS = "minecraft:lapis_block";
    private static final String TILES = "minecraft:deepslate_tiles";
    private static final String POLISHED = "minecraft:polished_deepslate";
    private static final String BLUE_T = "minecraft:blue_terracotta";
    private static final String BLUE_C = "minecraft:blue_concrete";
    private static final String GOLD = "minecraft:gold_block";
    private static final String LANTERN = "minecraft:sea_lantern";
    private static final String ROD_DOWN = "minecraft:end_rod[facing=down]";

    private static String floor(int x, int z) {
        return pick(x, 4, z, 161, TILES, .40, POLISHED, .26, BLUE_T, .14, LAPIS, .10, "minecraft:blue_glazed_terracotta[facing=north]", .04,
                "minecraft:cyan_terracotta", .06);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 162, TILES, .45, POLISHED, .30, BLUE_T, .15, "minecraft:blue_glazed_terracotta[facing=north]", .10);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 163, LAPIS, .20, BLUE_T, .22, TILES, .22, POLISHED, .16, BLUE_C, .12, GOLD, .03, "minecraft:cyan_terracotta", .05);
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 164, TILES, .45, POLISHED, .35, BLUE_T, .20);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(ObservatoryLayout::floor, "minecraft:deepslate", "minecraft:stone", ObservatoryLayout::wall);
        k.gates(POLISHED, LAPIS);
        int last = Template.LAST;

        k.roadRing(CX, CZ, 18, ObservatoryLayout::road, LAPIS);
        k.roadSeg(88, 0, 88, 70, ObservatoryLayout::road, LAPIS);
        k.roadSeg(88, 106, 88, last, ObservatoryLayout::road, LAPIS);
        k.roadSeg(106, 88, last, 88, ObservatoryLayout::road, LAPIS);
        k.roadSeg(CACHE_X + 7, CACHE_Z + 15, 88, 50, ObservatoryLayout::road, LAPIS);
        k.reserveCircle(CX, CZ, 12);
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 12, 0);
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], 70, 88, ObservatoryLayout::road, LAPIS);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, ObservatoryLayout::arenaFloor, ObservatoryLayout::wall, "minecraft:chiseled_deepslate", POLISHED);

        pillars(k);
        nooks(k);
        columns(k);
        stars(k);
        k.lights();

        List<Landmark> landmarks = List.of(Orrery.landmarkAt(CX, CZ), StarArchive.landmarkAt(CACHE_X, CACHE_Z),
                ZenithRing.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {88, 66},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 15},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    /** Four tall lapis pillars at the rim of the hub, gold-banded, rising into the dome. */
    private static void pillars(DistrictKit k) {
        for (int[] p : new int[][] {{-17, -17}, {17, -17}, {-17, 17}, {17, 17}}) {
            int cx = CX + p[0], cz = CZ + p[1];
            int top = SHAPE.ceiling(cx, cz) - 1;
            for (int y = 5; y <= top; y++) {
                boolean band = y % 9 == 0 || y <= 6 || y >= top - 2;
                k.canvas.fill(cx - 1, y, cz - 1, cx + 1, y, cz + 1, band ? GOLD : y % 3 == 0 ? BLUE_C : LAPIS);
            }
            k.reserveRect(cx - 2, cz - 2, cx + 2, cz + 2, 1);
        }
    }

    /** Study nooks: an enchanting table with a ring of bookshelves two blocks away, so its glyphs stream in. */
    private static void nooks(DistrictKit k) {
        int[][] spots = {{70, 125}, {106, 125}, {142, 72}, {142, 104}, {108, 34}, {118, 52}};
        int[][] shelves = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-2, -1}, {-2, 1}, {2, -1}, {2, 1}, {-1, -2}, {1, -2}, {-1, 2}, {1, 2}};
        for (int[] s : spots) {
            if (k.nearBlocked(s[0], s[1], 3)) continue;
            k.canvas.set(s[0], 5, s[1], "minecraft:enchanting_table");
            for (int[] d : shelves) {
                k.canvas.set(s[0] + d[0], 5, s[1] + d[1], "minecraft:bookshelf");
                k.canvas.set(s[0] + d[0], 6, s[1] + d[1], "minecraft:bookshelf");
            }
            k.reserveRect(s[0] - 3, s[1] - 3, s[0] + 3, s[1] + 3, 0);
        }
    }

    /** Slender 2 x 2 columns in the four chambers, deepslate with lapis collars. */
    private static void columns(DistrictKit k) {
        for (int[] s : SATS) {
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(30 + 60 * i);
                int cx = (int) Math.round(s[0] + 17 * Math.cos(a)), cz = (int) Math.round(s[1] + 17 * Math.sin(a));
                if (k.nearBlocked(cx, cz, 3) || k.nearBlocked(cx + 1, cz + 1, 3)) continue;
                int top = SHAPE.ceiling(cx, cz) - 1;
                for (int y = 5; y <= top; y++) {
                    k.canvas.fill(cx, y, cz, cx + 1, y, cz + 1, y <= 6 || y >= top - 1 || y % 8 == 0 ? LAPIS : TILES);
                }
                k.reserveRect(cx, cz, cx + 1, cz + 1, 2);
            }
        }
    }

    /** The star ceiling: end rods hanging from the dome, with sea-lantern stars and a lapis lining. */
    private static void stars(DistrictKit k) {
        for (int z = 19; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z)) continue;
                int c = SHAPE.ceiling(x, z);
                double h = hash(x, 0, z, 165);
                k.canvas.set(x, c, z, h < 0.12 ? LAPIS : h < 0.40 ? BLUE_T : TILES);
                double s = hash(x, 1, z, 165);
                if (s < 0.005) k.canvas.set(x, c - 1, z, ROD_DOWN);
                else if (s < 0.0075) k.canvas.set(x, c - 1, z, LANTERN);
            }
        }
    }

}
