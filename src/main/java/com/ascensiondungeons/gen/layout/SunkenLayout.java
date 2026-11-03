package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.BlossomCourt;
import com.ascensiondungeons.gen.landmark.GardenersVault;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.LotusSteps;
import com.ascensiondungeons.gen.landmark.TerraceOfRoots;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Sunken Garden: a formal garden in a chamfered square hall. Two axes cross at a paved plaza. Round it lies a lattice of
 * 22-block garden rooms, each a hedged enclosure with a gap in the middle of every side and its own planting: flower beds,
 * a reflecting pool, a topiary, or a pergola. Beyond the rooms a promenade with lantern posts runs round the foot of a
 * raised planted terrace on the west, south and east, which is what makes the garden sunken. The Terrace of Roots rises
 * against the north wall, the Gardener's Vault is set into the same wall, the Lotus Steps lie along the east axis, and the
 * Blossom Court fills the south-west.
 */
public final class SunkenLayout {
    private SunkenLayout() {}

    public static final int WALL = 11;
    public static final int ROOF_BASE = 46;
    public static final int VAULT_X = 52, VAULT_Z = 20;
    public static final int TERRACE_X = 100, TERRACE_Z = 21;
    public static final int LOTUS_X = 120, LOTUS_Z = 79;
    public static final int ARENA_X = 46, ARENA_Z = 128;
    public static final double ARENA_DOOR = 0;
    public static final int CACHE_X = VAULT_X, CACHE_Z = VAULT_Z;

    private static final String QUARTZ = "minecraft:quartz_bricks";
    private static final String CALCITE = "minecraft:calcite";
    private static final String MOSSY = "minecraft:mossy_stone_bricks";
    private static final String MOSS = "minecraft:moss_block";
    private static final String ROOTED = "minecraft:rooted_dirt";
    private static final String LANTERN = "minecraft:sea_lantern";
    private static final String LEAF = "minecraft:azalea_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String FLOWER = "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final String PILLAR = "minecraft:quartz_pillar[axis=y]";
    private static final String SLAB = "minecraft:quartz_slab[type=bottom,waterlogged=false]";

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) {
            return x >= 20 && x <= 161 && z >= 20 && z <= 161 && Math.abs(x - 88) + Math.abs(z - 88) <= 120;
        }

        @Override public boolean outer(int x, int z) {
            return x >= 9 && x <= 172 && z >= 9 && z <= 172 && Math.abs(x - 88) + Math.abs(z - 88) <= 136;
        }

        @Override public int ceiling(int x, int z) {
            double d = Math.max(Math.abs(x - 88), Math.abs(z - 88)) / 71.0;
            return (int) (ROOF_BASE + 8 * (1 - d * d));
        }
    };

    private static String floor(int x, int z) {
        return pick(x, 4, z, 81, MOSS, .45, ROOTED, .12, MOSSY, .20, CALCITE, .15, QUARTZ, .08);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 82, CALCITE, .45, QUARTZ, .30, "minecraft:smooth_quartz", .25);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 83, MOSSY, .30, CALCITE, .25, QUARTZ, .20, "minecraft:stone_bricks", .15, ROOTED, .10);
    }

    private static String floorArena(int x, int z) {
        return pick(x, 4, z, 84, CALCITE, .45, QUARTZ, .35, "minecraft:smooth_quartz", .20);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(SunkenLayout::floor, "minecraft:stone", "minecraft:stone", SunkenLayout::wall);
        k.gates(CALCITE, MOSSY);

        // The two axes and the plaza where they cross.
        k.roadSeg(88, 0, 88, Template.LAST, SunkenLayout::road, MOSSY);
        k.roadSeg(Template.LAST, 88, 40, 88, SunkenLayout::road, MOSSY);
        for (int z = 74; z <= 102; z++) {
            for (int x = 74; x <= 102; x++) {
                double r = Math.hypot(x - 88, z - 88);
                if (r > 10 || !SHAPE.inner(x, z)) continue;
                k.canvas.set(x, 4, z, r < 2.5 ? LANTERN : r >= 5.5 && r < 7 ? "minecraft:chiseled_quartz_block" : r > 8.5 ? MOSSY : road(x, z));
                k.road[x][z] = true;
                k.reserved[x][z] = true;
            }
        }

        // Blossom Court in the south-west, its door on a road to the south axis.
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, SunkenLayout::floorArena, SunkenLayout::wall, QUARTZ, CALCITE);
        k.roadSeg(ARENA_X + 20, ARENA_Z, 88, ARENA_Z, SunkenLayout::road, MOSSY);

        // Gardener's Vault set into the north wall, and a road from its arch to the north axis.
        k.reserveRect(VAULT_X - 2, VAULT_Z, VAULT_X + 16, VAULT_Z + 17, 0);
        k.roadSeg(VAULT_X + 7, VAULT_Z + 14, 88, VAULT_Z + 14, SunkenLayout::road, MOSSY);

        // Terrace of Roots against the north wall, with a road to the foot of its stair.
        k.reserveRect(TERRACE_X - 1, TERRACE_Z, TERRACE_X + 29, TERRACE_Z + 30, 0);
        k.roadSeg(TERRACE_X + 14, TERRACE_Z + 29, 88, TERRACE_Z + 29, SunkenLayout::road, MOSSY);
        k.roadSeg(TERRACE_X + 14, TERRACE_Z + 29, TERRACE_X + 14, TERRACE_Z + 24, SunkenLayout::road, MOSSY);

        // Lotus Steps across the east axis.
        k.reserveRect(LOTUS_X - 1, LOTUS_Z - 1, LOTUS_X + 27, LOTUS_Z + 18, 0);

        promenade(k);
        rooms(k);
        terrace(k);
        roots(k);
        k.lights();

        List<Landmark> landmarks = List.of(TerraceOfRoots.landmarkAt(TERRACE_X, TERRACE_Z),
                GardenersVault.landmarkAt(VAULT_X, VAULT_Z), LotusSteps.landmarkAt(LOTUS_X, LOTUS_Z),
                BlossomCourt.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {TERRACE_X, TERRACE_Z + 29},
                "loot", new int[] {VAULT_X + 7, VAULT_Z + 14},
                "explore", new int[] {LOTUS_X + 5, 88},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    private static boolean footprintClear(DistrictKit k, int x0, int z0, int x1, int z1) {
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                if (x < 0 || z < 0 || x >= Canvas.SIZE || z >= Canvas.SIZE || k.reserved[x][z] || !SHAPE.inner(x, z)) return false;
            }
        }
        return true;
    }

    // ---- garden rooms -------------------------------------------------------------------------------------------

    private static void rooms(DistrictKit k) {
        int[] offs = {-47, -21, 21, 47};
        for (int oz : offs) {
            for (int ox : offs) {
                int cx = 88 + ox, cz = 88 + oz;
                if (!footprintClear(k, cx - 11, cz - 11, cx + 10, cz + 10)) continue;
                room(k, cx, cz, (int) (hash(cx, 0, cz, 85) * 4));
                k.reserveRect(cx - 11, cz - 11, cx + 10, cz + 10, 0);
            }
        }
    }

    private static void hedge(DistrictKit k, int x, int z) {
        k.canvas.set(x, 5, z, hash(x, 5, z, 86) < 0.25 ? FLOWER : LEAF);
        k.canvas.set(x, 6, z, hash(x, 6, z, 86) < 0.40 ? FLOWER : LEAF);
    }

    private static void room(DistrictKit k, int cx, int cz, int type) {
        // Hedge outline with a five-wide gap in the middle of each side, and a lantern post at each corner.
        for (int d = -11; d <= 10; d++) {
            boolean gap = Math.abs(d) <= 2 || d == -3 && false;
            if (!gap) {
                hedge(k, cx + d, cz - 11);
                hedge(k, cx + d, cz + 10);
                hedge(k, cx - 11, cz + d);
                hedge(k, cx + 10, cz + d);
            }
        }
        for (int[] c : new int[][] {{cx - 11, cz - 11}, {cx + 10, cz - 11}, {cx - 11, cz + 10}, {cx + 10, cz + 10}}) {
            for (int y = 5; y <= 7; y++) k.canvas.set(c[0], y, c[1], PILLAR);
            k.canvas.set(c[0], 8, c[1], LANTERN);
        }
        // A garden arch over each gap: stout posts either side, a quartz lintel and a blossoming crest.
        for (int[] side : new int[][] {{0, -11, 1, 0}, {0, 10, 1, 0}, {-11, 0, 0, 1}, {10, 0, 0, 1}}) {
            for (int d = -3; d <= 3; d++) {
                int x = cx + side[0] + d * side[2], z = cz + side[1] + d * side[3];
                if (Math.abs(d) == 3) for (int y = 5; y <= 8; y++) k.canvas.set(x, y, z, PILLAR);
                else if (Math.abs(d) <= 2) {
                    k.canvas.set(x, 8, z, QUARTZ);
                    k.canvas.set(x, 9, z, hash(x, 9, z, 94) < 0.5 ? FLOWER : LEAF);
                }
            }
        }
        // Paved cross between the four gaps.
        for (int d = -10; d <= 9; d++) {
            for (int w = -1; w <= 1; w++) {
                k.canvas.set(cx + d, 4, cz + w, hash(cx + d, 0, cz + w, 87) < 0.5 ? CALCITE : QUARTZ);
                k.canvas.set(cx + w, 4, cz + d, hash(cx + w, 0, cz + d, 87) < 0.5 ? CALCITE : QUARTZ);
            }
        }
        switch (type) {
            case 0 -> flowerBeds(k, cx, cz);
            case 1 -> pool(k, cx, cz);
            case 2 -> topiary(k, cx, cz);
            default -> pergola(k, cx, cz);
        }
    }

    /** Four raised beds, one per quarter, planted with flowering azalea. */
    private static void flowerBeds(DistrictKit k, int cx, int cz) {
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                int x0 = sx < 0 ? cx - 9 : cx + 3, z0 = sz < 0 ? cz - 9 : cz + 3;
                for (int z = z0; z < z0 + 6; z++) {
                    for (int x = x0; x < x0 + 6; x++) {
                        boolean edge = x == x0 || x == x0 + 5 || z == z0 || z == z0 + 5;
                        k.canvas.set(x, 5, z, edge ? QUARTZ : MOSS);
                        if (!edge) k.canvas.set(x, 6, z, hash(x, 6, z, 88) < 0.55 ? FLOWER : LEAF);
                    }
                }
            }
        }
    }

    /** A shallow reflecting pool, flush with the floor and ringed by quartz, with a lantern at its middle edge. */
    private static void pool(DistrictKit k, int cx, int cz) {
        for (int z = cz - 5; z <= cz + 4; z++) {
            for (int x = cx - 5; x <= cx + 4; x++) {
                boolean rim = x == cx - 5 || x == cx + 4 || z == cz - 5 || z == cz + 4;
                if (Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1 || Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1) {
                    // the paved cross keeps its walkway over the pool's edge: leave it
                    if (rim) continue;
                }
                if (rim) {
                    k.canvas.set(x, 4, z, QUARTZ);
                    if ((x + z) % 3 == 0 && hash(x, 5, z, 95) < 0.5) {
                        k.canvas.set(x, 5, z, "minecraft:candle[candles=3,lit=true,waterlogged=false]");   // small steady flames
                    }
                } else if (!(Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1)) {
                    k.canvas.set(x, 4, z, "minecraft:water[level=0]");
                    if (hash(x, 4, z, 89) < 0.3) k.canvas.set(x, 5, z, "minecraft:lily_pad");
                }
            }
        }
        k.canvas.set(cx, 5, cz, LANTERN);
    }

    /** A stepped hedge cone on a quartz plinth with a lantern on top. */
    private static void topiary(DistrictKit k, int cx, int cz) {
        for (int z = cz - 2; z <= cz + 1; z++) for (int x = cx - 2; x <= cx + 1; x++) k.canvas.set(x, 5, z, QUARTZ);
        for (int y = 6; y <= 7; y++) {
            for (int z = cz - 1; z <= cz; z++) for (int x = cx - 1; x <= cx; x++) k.canvas.set(x, y, z, y == 6 ? FLOWER : LEAF);
        }
        k.canvas.set(cx, 8, cz, LEAF);
        k.canvas.set(cx, 9, cz, LANTERN);
        for (int[] c : new int[][] {{-8, -8}, {7, -8}, {-8, 7}, {7, 7}}) {
            for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) k.canvas.set(cx + c[0] + x, 5, cz + c[1] + z, hash(cx + x, 5, cz + z, 90) < 0.5 ? FLOWER : LEAF);
        }
    }

    /** Four quartz posts carrying a slab roof, with blossom hanging under it. */
    private static void pergola(DistrictKit k, int cx, int cz) {
        for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            for (int y = 5; y <= 8; y++) k.canvas.set(cx + c[0], y, cz + c[1], PILLAR);
        }
        for (int z = cz - 4; z <= cz + 4; z++) {
            for (int x = cx - 4; x <= cx + 4; x++) {
                k.canvas.set(x, 9, z, SLAB);
                boolean beam = x == cx - 4 || x == cx + 4 || z == cz - 4 || z == cz + 4;
                if (!beam && hash(x, 8, z, 91) < 0.35) k.canvas.set(x, 8, z, FLOWER);
                if (!beam && hash(x, 8, z, 97) < 0.10) k.canvas.set(x, 8, z, "minecraft:spore_blossom");   // drifting pollen
            }
        }
    }

    // ---- promenade, raised terrace, ceiling ------------------------------------------------------------------------

    /** Lantern posts every 16 blocks round the garden at 88 from the centre, kept off everything reserved. */
    private static void promenade(DistrictKit k) {
        for (int i = -60; i <= 60; i += 12) {
            for (int[] p : new int[][] {{88 + i, 88 - 60}, {88 + i, 88 + 60}, {88 - 60, 88 + i}, {88 + 60, 88 + i}}) {
                int x = p[0], z = p[1];
                if (!SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                for (int y = 5; y <= 7; y++) k.canvas.set(x, y, z, PILLAR);
                k.canvas.set(x, 8, z, LANTERN);
            }
        }
    }

    /** The raised planted terrace along the west, south and east walls, 2 high with a mossy retaining face. */
    private static void terrace(DistrictKit k) {
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                int u = x - 88, v = z - 88;
                boolean band = Math.abs(u) > 62 || v > 62;
                if (!band) continue;
                boolean face = Math.abs(u) == 63 || v == 63;
                for (int y = 5; y <= 6; y++) k.canvas.set(x, y, z, face ? (hash(x, y, z, 92) < 0.5 ? MOSSY : CALCITE) : y == 6 ? MOSS : ROOTED);
                if (!face && hash(x, 7, z, 92) < 0.5) k.canvas.set(x, 7, z, hash(x, 8, z, 92) < 0.4 ? FLOWER : LEAF);
            }
        }
    }

    private static final String MANGROVE = "minecraft:mangrove_roots[waterlogged=false]";
    private static final String ROOT_TIP = "minecraft:hanging_roots[waterlogged=false]";
    private static final String CHAIN = "minecraft:chain[axis=y,waterlogged=false]";

    private static String rootBlock(int x, int y, int z) {
        return pick(x, y, z, 99, ROOTED, .45, MANGROVE, .35, MOSS, .20);
    }

    /**
     * The overhead root canopy. Woven ribs of rooted dirt, mangrove root and moss, two blocks wide and three deep, run under
     * the roof along the 24-block grid. From the ribs hang root clusters: a thick trunk (two blocks square) that narrows to
     * one, tendrils that leave it sideways and drop again, a clump of blossoming azalea at the top of some, and a pale
     * hanging-root tip on every strand. Lanterns hang on chains from the rib crossings. Nothing hangs lower than y=36, so
     * the garden below stays clear.
     */
    private static void roots(DistrictKit k) {
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z)) continue;
                int u = Math.floorMod(x - 88, 24), v = Math.floorMod(z - 88, 24);
                if (u > 1 && v > 1) continue;
                int ceil = SHAPE.ceiling(x, z);
                for (int y = ceil - 3; y < ceil; y++) k.canvas.set(x, y, z, rootBlock(x, y, z));
                if (u <= 1 && v <= 1) {
                    if (u == 0 && v == 0) {
                        for (int y = ceil - 8; y < ceil - 3; y++) k.canvas.set(x, y, z, CHAIN);
                        k.canvas.set(x, ceil - 9, z, LANTERN);
                    }
                } else if (hash(x, 0, z, 93) < 0.05) {
                    cluster(k, x, z, ceil - 4);
                }
            }
        }
    }

    /** One hanging root cluster: its top is the block just under the rib at (x, top, z). */
    private static void cluster(DistrictKit k, int x, int z, int top) {
        int len = Math.min(4 + (int) (hash(x, 1, z, 93) * 9), top - 32);
        if (len < 3) return;
        for (int d = 0; d < len; d++) {
            int y = top - d;
            if (d < 3) {
                for (int dz = 0; dz <= 1; dz++) for (int dx = 0; dx <= 1; dx++) k.canvas.set(x + dx, y, z + dz, rootBlock(x + dx, y, z + dz));
            } else {
                k.canvas.set(x, y, z, rootBlock(x, y, z));
            }
        }
        k.canvas.set(x, top - len, z, ROOT_TIP);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < 4; i++) {
            if (hash(x, 10 + i, z, 93) > 0.5) continue;
            int d0 = 1 + (int) (hash(x, 14 + i, z, 93) * 3), run = 1 + (int) (hash(x, 18 + i, z, 93) * 3), drop = 2 + (int) (hash(x, 22 + i, z, 93) * 4);
            int hx = x + dirs[i][0] * (run + 1), hz = z + dirs[i][1] * (run + 1), hy = top - d0;
            for (int s = 1; s <= run + 1; s++) k.canvas.set(x + dirs[i][0] * s, hy, z + dirs[i][1] * s, rootBlock(x + s, hy, z));
            for (int j = 1; j <= drop && hy - j > 32; j++) k.canvas.set(hx, hy - j, hz, rootBlock(hx, hy - j, hz));
            k.canvas.set(hx, hy - drop - 1, hz, ROOT_TIP);
        }
        if (hash(x, 30, z, 93) < 0.3) {
            for (int dz = -1; dz <= 2; dz++) {
                for (int dx = -1; dx <= 2; dx++) {
                    if (k.canvas.isAir(x + dx, top, z + dz)) k.canvas.set(x + dx, top, z + dz, hash(x + dx, 1, z + dz, 94) < 0.5 ? FLOWER : LEAF);
                }
            }
        }
    }
}
