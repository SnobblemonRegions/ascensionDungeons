package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.ArchivistsVault;
import com.ascensiondungeons.gen.landmark.DrownedCourt;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.SluiceWalk;
import com.ascensiondungeons.gen.landmark.Tidewheel;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Flooded Ruins: a drowned town under a flat coffered roof. The room is a full square. A promenade runs along the north
 * shore of a flooded basin with an island, the Tidewheel above it; the spine road crosses the basin to the island and on to
 * the south gate, and a link road joins the east gate. The rest of the quarter is a lattice of 16-block lots: about a third
 * are under water, the rest carry ruined halls, broken gateways and column rings. The Drowned Court stands in the
 * south-west behind a moat, the Archivist's Dry Vault in the north-east, and the Sluice Walk runs west of the basin.
 *
 * <p>Water occupies y 2..3 over a clay bed, with the surface one block below the dry ground (the floor row, y=4).
 * Nothing that can be walked on is flooded; every route and every landmark entrance is dry.
 */
public final class FloodedLayout {
    private FloodedLayout() {}

    public static final int LO = 20, HI = 161, WALL = 11;
    /** The built-up core: lots inside it are never flooded. */
    public static final int CORE_X0 = 45, CORE_X1 = 130, CORE_Z0 = 28, CORE_Z1 = 122;
    public static final int TIDE_X = 96, TIDE_Z = 36;
    public static final int CACHE_X = 134, CACHE_Z = 24;
    public static final int SLUICE_X = 22, SLUICE_Z = 81;
    public static final int ARENA_X = 46, ARENA_Z = 137;
    public static final double ARENA_DOOR = 0;
    public static final int ISLE_X = 88, ISLE_Z = 88;
    public static final int PROM_Z = 56;
    public static final int POOL_X0 = 62, POOL_X1 = 114, POOL_Z0 = 62, POOL_Z1 = 114;

    private static final String WATER = "minecraft:water[level=0]";
    private static final String PRIS = "minecraft:prismarine_bricks";
    private static final String DARK = "minecraft:dark_prismarine";
    private static final String BRICK = "minecraft:stone_bricks";
    private static final String MOSSY = "minecraft:mossy_stone_bricks";
    private static final String CRACKED = "minecraft:cracked_stone_bricks";
    private static final String COPPER = "minecraft:oxidized_cut_copper";
    private static final String LANTERN = "minecraft:sea_lantern";

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return x >= LO && x <= HI && z >= LO && z <= HI; }

        @Override public boolean outer(int x, int z) {
            return x >= LO - WALL && x <= HI + WALL && z >= LO - WALL && z <= HI + WALL;
        }

        @Override public int ceiling(int x, int z) {
            double d = Math.max(Math.abs(x - 90.5), Math.abs(z - 90.5)) / 71.0;
            return (int) (44 + 14 * (1 - d * d));
        }
    };

    private static String floor(int x, int z) {
        return pick(x, 4, z, 41, BRICK, .35, MOSSY, .30, CRACKED, .15, PRIS, .10, DARK, .10);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 42, BRICK, .55, MOSSY, .20, PRIS, .15, CRACKED, .10);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 43, PRIS, .35, BRICK, .25, MOSSY, .20, DARK, .20);
    }

    private static String floorArena(int x, int z) {
        return pick(x, 4, z, 44, PRIS, .45, BRICK, .30, DARK, .25);
    }

    private static String ruinBlock(int x, int y, int z) {
        String s = pick(x, y, z, 45, PRIS, .28, BRICK, .24, MOSSY, .22, CRACKED, .14, DARK, .12);
        return y <= 6 && hash(x, y, z, 46) < 0.04 ? LANTERN : s;
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(FloodedLayout::floor, "minecraft:stone", "minecraft:stone", FloodedLayout::wall);
        k.gates(BRICK, DARK);
        ceilingDetail(k);

        // Causeways: the spine gate to gate (over the basin to the island), the promenade, and the east link.
        int last = Template.LAST;
        k.roadSeg(88, 0, 88, PROM_Z, FloodedLayout::road, DARK);
        k.roadSeg(54, PROM_Z, 141, PROM_Z, FloodedLayout::road, DARK);
        k.roadSeg(122, PROM_Z, 122, 88, FloodedLayout::road, DARK);
        k.roadSeg(122, 88, last, 88, FloodedLayout::road, DARK);

        // Drowned Court: SW, door east, a road along its door line to the south spur.
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, FloodedLayout::floorArena, FloodedLayout::wall, PRIS, BRICK);
        k.roadSeg(ARENA_X + 20, ARENA_Z, 88, ARENA_Z, FloodedLayout::road, DARK);

        // Archivist's Dry Vault: NE, a road from its arch to the ring's north-east corner.
        k.reserveRect(CACHE_X - 2, CACHE_Z - 2, CACHE_X + 16, CACHE_Z + 12, 0);
        int cacheMid = CACHE_X + 7;
        k.roadSeg(cacheMid, CACHE_Z + 12, cacheMid, PROM_Z, FloodedLayout::road, DARK);

        // Sluice Walk: west off the ring, along the line of its walkway.
        k.reserveRect(SLUICE_X - 1, SLUICE_Z - 1, SLUICE_X + 29, SLUICE_Z + 15, 0);
        k.roadSeg(SLUICE_X + 28, SLUICE_Z + 7, 54, SLUICE_Z + 7, FloodedLayout::road, DARK);
        k.roadSeg(54, PROM_Z, 54, SLUICE_Z + 7, FloodedLayout::road, DARK);

        // Tidewheel on the north shore of the basin, above the promenade.
        k.reserveRect(TIDE_X - 1, TIDE_Z - 1, TIDE_X + 29, TIDE_Z + 15, 0);

        // The basin's island: a causeway from the south ring, a paved disc, a broken colonnade and a beacon pillar.
        k.reserveCircle(ISLE_X, ISLE_Z, 12);
        k.roadSeg(ISLE_X, ISLE_Z + 4, ISLE_X, last, FloodedLayout::road, DARK);
        island(k);

        boolean[][] fixed = new boolean[Canvas.SIZE][];
        for (int i = 0; i < Canvas.SIZE; i++) fixed[i] = k.reserved[i].clone();

        // Water: the basin, the moat round the court, then the lattice of lots outside the ring.
        boolean[][] wet = new boolean[Canvas.SIZE][Canvas.SIZE];
        for (int z = POOL_Z0; z <= POOL_Z1; z++) {
            for (int x = POOL_X0; x <= POOL_X1; x++) {
                if (roundedOut(x, z, POOL_X0, POOL_Z0, POOL_X1, POOL_Z1, 9)) continue;
                if (!k.reserved[x][z]) wet[x][z] = true;
            }
        }
        for (int z = LO; z <= HI; z++) {
            for (int x = LO; x <= HI; x++) {
                double r = Math.hypot(x - ARENA_X, z - ARENA_Z);
                if (r >= 24.6 && r <= 28 && !nearReserved(k, x, z, 1)) wet[x][z] = true;
            }
        }
        lattice(k, wet);
        for (int z = SLUICE_Z; z < SLUICE_Z + 15; z++) {
            for (int x = SLUICE_X; x < SLUICE_X + 29; x++) {
                if (z < SLUICE_Z + SluiceWalk.WALK_Z0 || z > SLUICE_Z + SluiceWalk.WALK_Z1) wet[x][z] = true;
            }
        }
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                if (!wet[x][z] || !SHAPE.inner(x, z)) continue;
                k.canvas.set(x, 4, z, Canvas.AIR);
                k.canvas.set(x, 3, z, WATER);
                k.canvas.set(x, 2, z, WATER);
                k.canvas.set(x, 1, z, "minecraft:clay");
                k.reserved[x][z] = true;
            }
        }
        poolStumps(k, fixed);
        roadLamps(k, fixed);
        ruins(k, wet);
        streetWalls(k);
        giantColumns(k, fixed);
        bubbleVents(k);
        waterLife(k);

        // Rubble on the dry ground that is left (flooded cells are reserved, so nothing floats).
        k.scatter(47, 12, 0.45, 2, 4.5, (x, z) -> pick(x, 5, z, 48, BRICK, .35, MOSSY, .35, PRIS, .30));
        mossCarpet(k);
        k.lights();

        List<Landmark> landmarks = List.of(Tidewheel.landmarkAt(TIDE_X, TIDE_Z), ArchivistsVault.landmarkAt(CACHE_X, CACHE_Z),
                SluiceWalk.landmarkAt(SLUICE_X, SLUICE_Z), DrownedCourt.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {TIDE_X + 14, PROM_Z},
                "loot", new int[] {cacheMid, CACHE_Z + 13},
                "explore", new int[] {SLUICE_X + 14, SLUICE_Z + 7},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    private static boolean roundedOut(int x, int z, int x0, int z0, int x1, int z1, int r) {
        int cx = x < x0 + r ? x0 + r : x > x1 - r ? x1 - r : x;
        int cz = z < z0 + r ? z0 + r : z > z1 - r ? z1 - r : z;
        return Math.hypot(x - cx, z - cz) > r;
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

    private static boolean nearFixed(boolean[][] fixed, int x, int z, int m) {
        for (int dz = -m; dz <= m; dz++) {
            for (int dx = -m; dx <= m; dx++) if (fixed[x + dx][z + dz]) return true;
        }
        return false;
    }

    private static boolean footprintClear(DistrictKit k, int x0, int z0, int x1, int z1, int m) {
        for (int z = z0 - m; z <= z1 + m; z++) {
            for (int x = x0 - m; x <= x1 + m; x++) {
                if (x < 0 || z < 0 || x >= Canvas.SIZE || z >= Canvas.SIZE || k.reserved[x][z] || !SHAPE.inner(x, z)) return false;
            }
        }
        return true;
    }

    private static boolean inRingBox(int x0, int z0, int x1, int z1) {
        return x1 >= CORE_X0 && x0 <= CORE_X1 && z1 >= CORE_Z0 && z0 <= CORE_Z1;
    }

    // ---- the island --------------------------------------------------------------------------------------------

    private static void island(DistrictKit k) {
        for (int z = ISLE_Z - 11; z <= ISLE_Z + 11; z++) {
            for (int x = ISLE_X - 11; x <= ISLE_X + 11; x++) {
                double r = Math.hypot(x - ISLE_X, z - ISLE_Z);
                if (r > 10.5) continue;
                String s = r <= 3.2 ? "minecraft:chiseled_stone_bricks" : r >= 9 ? DARK
                        : pick(x, 4, z, 49, PRIS, .45, BRICK, .30, MOSSY, .25);
                k.canvas.set(x, 4, z, s);
            }
        }
        // Beacon pillar: a 3 x 3 shaft with a lantern crown.
        for (int y = 5; y <= 12; y++) k.canvas.fill(ISLE_X - 1, y, ISLE_Z - 1, ISLE_X + 1, y, ISLE_Z + 1, y == 12 ? COPPER : PRIS);
        k.canvas.set(ISLE_X, 13, ISLE_Z, LANTERN);
        // A broken colonnade: eight 2 x 2 stumps of uneven height, never on the causeway.
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(22.5 + 45 * i);
            int px = (int) Math.round(ISLE_X + 7 * Math.cos(a)), pz = (int) Math.round(ISLE_Z + 7 * Math.sin(a));
            if (Math.abs(px - ISLE_X) <= 6 && pz > ISLE_Z) continue;
            int h = 3 + (int) (hash(px, 0, pz, 50) * 7);
            for (int y = 5; y < 5 + h; y++) k.canvas.fill(px, y, pz, px + 1, y, pz + 1, ruinBlock(px, y, pz));
        }
    }

    // ---- lattice of lots -----------------------------------------------------------------------------------------

    private static void lattice(DistrictKit k, boolean[][] wet) {
        for (int cz = 0; cz < 9; cz++) {
            for (int cx = 0; cx < 9; cx++) {
                int x0 = 24 + cx * 16, z0 = 24 + cz * 16;
                if (x0 + 15 > HI - 3 || z0 + 15 > HI - 3 || inRingBox(x0, z0, x0 + 15, z0 + 15)) continue;
                if (hash(cx, cz, 0, 71) >= 0.35) continue;
                for (int z = z0; z < z0 + 16; z++) {
                    for (int x = x0; x < x0 + 16; x++) if (!nearReserved(k, x, z, 2)) wet[x][z] = true;
                }
            }
        }
    }

    private static void ruins(DistrictKit k, boolean[][] wet) {
        for (int cz = 0; cz < 9; cz++) {
            for (int cx = 0; cx < 9; cx++) {
                int x0 = 24 + cx * 16, z0 = 24 + cz * 16;
                if (x0 + 15 > HI - 3 || z0 + 15 > HI - 3) continue;
                boolean inside = inRingBox(x0, z0, x0 + 15, z0 + 15);          // inside the ring, the lots are never flooded
                if (!inside && hash(cx, cz, 0, 71) < 0.35) continue;           // a flooded lot
                if (!footprintClear(k, x0 + 2, z0 + 2, x0 + 13, z0 + 13, 0)) continue;
                double t = hash(cx, cz, 1, 72);
                if (t < 0.30) hall(k, x0, z0, cx, cz);
                else if (t < 0.50) gateway(k, x0, z0, cx, cz);
                else if (t < 0.65) columns(k, x0, z0, cx, cz);
                else if (t < 0.85) tower(k, x0, z0, cx, cz);
                else aqueduct(k, x0, z0, cx, cz);
                k.reserveRect(x0 + 2, z0 + 2, x0 + 13, z0 + 13, 0);
            }
        }
    }

    /** A roofless hall: broken walls of uneven height, a doorway, corner pillars and a paved floor. */
    private static void hall(DistrictKit k, int x0, int z0, int cx, int cz) {
        int w = 9 + (int) (hash(cx, cz, 2, 73) * 4), l = 9 + (int) (hash(cx, cz, 3, 73) * 4);
        int ox = x0 + 2, oz = z0 + 2;
        for (int z = oz; z < oz + l; z++) {
            for (int x = ox; x < ox + w; x++) {
                k.canvas.set(x, 4, z, PRIS.equals(floor(x, z)) ? DARK : floor(x, z));
                boolean edge = x == ox || x == ox + w - 1 || z == oz || z == oz + l - 1;
                if (!edge) continue;
                boolean door = z == oz + l - 1 && Math.abs(x - (ox + w / 2)) <= 1;
                if (door) continue;
                int h = 3 + (int) (hash(x, 0, z, 74) * 7);
                for (int y = 5; y < 5 + h; y++) k.canvas.set(x, y, z, ruinBlock(x, y, z));
            }
        }
        for (int[] c : new int[][] {{ox, oz}, {ox + w - 1, oz}, {ox, oz + l - 1}, {ox + w - 1, oz + l - 1}}) {
            int h = 6 + (int) (hash(c[0], 1, c[1], 75) * 4);
            for (int y = 5; y < 5 + h; y++) k.canvas.set(c[0], y, c[1], y == 4 + h ? COPPER : ruinBlock(c[0], y, c[1]));
        }
        k.canvas.set(ox + w / 2, 5, oz + l / 2, LANTERN);
    }

    /** A hollow square tower with window slits, a doorway, a copper rim and a lantern inside. */
    private static void tower(DistrictKit k, int x0, int z0, int cx, int cz) {
        int tx = x0 + 5, tz = z0 + 5, h = 12 + (int) (hash(cx, cz, 6, 81) * 5);
        for (int z = tz; z <= tz + 5; z++) {
            for (int x = tx; x <= tx + 5; x++) {
                k.canvas.set(x, 4, z, DARK);
                boolean edge = x == tx || x == tx + 5 || z == tz || z == tz + 5;
                if (!edge) continue;
                boolean door = z == tz + 5 && x >= tx + 2 && x <= tx + 3;
                boolean corner = (x == tx || x == tx + 5) && (z == tz || z == tz + 5);
                for (int y = 5; y <= 4 + h; y++) {
                    if (door && y <= 8) continue;
                    boolean slit = !corner && y >= 9 && y <= 10 && ((x + z) % 2 == 0);
                    if (!slit) k.canvas.set(x, y, z, ruinBlock(x, y, z));
                }
                k.canvas.set(x, 5 + h, z, corner ? LANTERN : COPPER);
            }
        }
        k.canvas.set(tx + 2, 6, tz + 2, LANTERN);
    }

    /** A three-thick aqueduct wall across the lot on stout piers, open arches between and a copper cap. */
    private static void aqueduct(DistrictKit k, int x0, int z0, int cx, int cz) {
        int z = z0 + 6;
        for (int x = x0 + 2; x <= x0 + 13; x++) {
            int u = (x - x0 - 2) % 6;                                    // 0..2 pier, 3..5 gap
            boolean pier = u <= 2;
            boolean broken = hash(x / 6, cx, cz, 82) < 0.35 && x - x0 > 8;
            for (int zz = z; zz <= z + 2; zz++) {
                for (int y = 5; y <= 13; y++) {
                    boolean solid = pier || y >= 10 - (u == 3 || u == 5 ? 1 : 0);
                    if (broken && y > 8) solid = false;
                    if (solid) k.canvas.set(x, y, zz, y == 13 ? COPPER : ruinBlock(x, y, zz));
                }
            }
        }
    }

    /** Two thick pillars and a lintel, broken in the middle half the time. */
    private static void gateway(DistrictKit k, int x0, int z0, int cx, int cz) {
        int pz = z0 + 7;
        for (int px : new int[] {x0 + 3, x0 + 11}) {
            for (int y = 5; y <= 12; y++) k.canvas.fill(px, y, pz, px + 1, y, pz + 1, ruinBlock(px, y, pz));
        }
        for (int x = x0 + 5; x <= x0 + 10; x++) {
            if (hash(cx, cz, 4, 76) < 0.6 && x >= x0 + 7 && x <= x0 + 8) continue;   // fallen section
            for (int y = 11; y <= 12; y++) k.canvas.fill(x, y, pz, x, y, pz + 1, y == 12 ? COPPER : ruinBlock(x, y, pz));
        }
        for (int z = pz - 2; z <= pz + 3; z++) {
            for (int x = x0 + 3; x <= x0 + 12; x++) if (hash(x, 0, z, 77) < 0.5) k.canvas.set(x, 4, z, DARK);
        }
    }

    /** A paved dais ringed by stumps of uneven height. */
    private static void columns(DistrictKit k, int x0, int z0, int cx, int cz) {
        int mx = x0 + 8, mz = z0 + 8;
        for (int z = mz - 6; z <= mz + 6; z++) {
            for (int x = mx - 6; x <= mx + 6; x++) {
                double r = Math.hypot(x - mx, z - mz);
                if (r <= 6) k.canvas.set(x, 4, z, r <= 2 ? "minecraft:chiseled_stone_bricks" : PRIS);
            }
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i + 30 * hash(cx, cz, 5, 78));
            int px = (int) Math.round(mx + 5 * Math.cos(a)), pz = (int) Math.round(mz + 5 * Math.sin(a));
            int h = 3 + (int) (hash(px, 2, pz, 79) * 7);
            for (int y = 5; y < 5 + h; y++) k.canvas.set(px, y, pz, ruinBlock(px, y, pz));
        }
        k.canvas.set(mx, 5, mz, LANTERN);
    }

    /** Stout 3 x 3 columns up to the roof on a coarse grid, wherever there is room, wet or dry. */
    private static void giantColumns(DistrictKit k, boolean[][] fixed) {
        for (int z = 40; z <= 150; z += 36) {
            for (int x = 40; x <= 150; x += 36) {
                if (inRingBox(x - 2, z - 2, x + 2, z + 2) || nearFixed(fixed, x, z, 4)) continue;
                boolean free = true;
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    for (int y = 5; y <= 12; y++) if (!k.canvas.isAir(x + dx, y, z + dz)) free = false;
                }
                if (!free) continue;
                int ceil = SHAPE.ceiling(x, z);
                for (int y = 2; y < ceil; y++) {
                    k.canvas.fill(x - 1, y, z - 1, x + 1, y, z + 1, y % 9 == 0 ? COPPER : y % 3 == 0 ? DARK : PRIS);
                }
                k.reserveRect(x - 1, z - 1, x + 1, z + 1, 1);
            }
        }
    }

    /** Coffered ceiling: dark beams every 24 blocks each way, with a chain and lantern hanging at every crossing. */
    private static void ceilingDetail(DistrictKit k) {
        for (int z = LO; z <= HI; z++) {
            for (int x = LO; x <= HI; x++) {
                int ceil = SHAPE.ceiling(x, z), u = (x - LO) % 24, v = (z - LO) % 24;
                if (u < 3 || v < 3) k.canvas.fill(x, ceil - 3, z, x, ceil - 1, z, u < 3 && v < 3 ? COPPER : hash(x, 0, z, 51) < 0.5 ? DARK : PRIS);
                if (u == 1 && v == 1) {
                    for (int y = ceil - 8; y < ceil - 3; y++) k.canvas.set(x, y, z, "minecraft:chain[axis=y,waterlogged=false]");
                    k.canvas.set(x, ceil - 9, z, LANTERN);
                }
            }
        }
    }

    /** The old lot boundaries as broken low walls on any dry ground that nothing else uses; routes are reserved and untouched. */
    private static void streetWalls(DistrictKit k) {
        for (int z = LO + 4; z <= HI - 3; z++) {
            for (int x = LO + 4; x <= HI - 3; x++) {
                if ((x - 24) % 16 != 0 && (z - 24) % 16 != 0) continue;
                if (k.reserved[x][z] || hash(x / 4, z / 4, 0, 84) > 0.5) continue;
                int h = 1 + (int) (hash(x, 0, z, 85) * 4);
                for (int y = 5; y < 5 + h; y++) k.canvas.set(x, y, z, ruinBlock(x, y, z));
            }
        }
    }

    /** Lantern posts every 12 blocks along the dry edge of the routes, with a low broken wall between them. */
    private static void roadLamps(DistrictKit k, boolean[][] fixed) {
        for (int z = LO + 1; z < HI; z++) {
            for (int x = LO + 1; x < HI; x++) {
                if (k.road[x][z] || fixed[x][z] && !k.road[x][z] || !SHAPE.inner(x, z)) continue;
                if (!(k.road[x + 1][z] || k.road[x - 1][z] || k.road[x][z + 1] || k.road[x][z - 1])) continue;
                if (!k.canvas.isAir(x, 5, z) || k.canvas.isAir(x, 4, z)) continue;
                boolean post = (x + z) % 12 == 0;
                int h = post ? 5 : (int) (hash(x, 0, z, 83) * 3);
                for (int y = 5; y < 5 + h; y++) k.canvas.set(x, y, z, post && y == 4 + h ? LANTERN : ruinBlock(x, y, z));
            }
        }
    }

    /** Broken colonnades standing in the basin: 2 x 2 stumps of uneven height up from the bed. */
    private static void poolStumps(DistrictKit k, boolean[][] fixed) {
        for (int z = POOL_Z0 + 8; z <= POOL_Z1 - 6; z += 10) {
            for (int x : new int[] {POOL_X0 + 8, POOL_X1 - 9}) {
                if (nearFixed(fixed, x, z, 2)) continue;
                int h = 3 + (int) (hash(x, 0, z, 52) * 7);
                for (int y = 2; y < 5 + h; y++) k.canvas.fill(x, y, z, x + 1, y, z + 1, ruinBlock(x, y, z));
            }
        }
    }

    /** Moss on the open dry ground, kept three blocks off everything reserved so it never touches a route or a doorway. */
    private static void mossCarpet(DistrictKit k) {
        for (int z = LO; z <= HI; z++) {
            for (int x = LO; x <= HI; x++) {
                if (hash(x, 1, z, 53) >= 0.12 || nearReserved(k, x, z, 3)) continue;
                if (k.canvas.isAir(x, 5, z) && !k.canvas.isAir(x, 4, z)) k.canvas.set(x, 5, z, "minecraft:moss_carpet");
            }
        }
    }

    /**
     * Bubble vents: soul sand on the bed with bubble-column blocks in the two water cells above it, so a stream of bubbles
     * rises to the surface. They are placed as blocks, not left to form on the first block update, and are kept out of the
     * Sluice Walk channels and the moat round the court.
     */
    private static void bubbleVents(DistrictKit k) {
        for (int z = LO; z <= HI; z++) {
            for (int x = LO; x <= HI; x++) {
                if (!k.canvas.isAir(x, 4, z) || !k.canvas.get(x, 3, z).startsWith("minecraft:water")) continue;
                if (hash(x, 0, z, 96) > 0.015) continue;
                if (x >= SLUICE_X - 2 && x <= SLUICE_X + 31 && z >= SLUICE_Z - 2 && z <= SLUICE_Z + 17) continue;
                double r = Math.hypot(x - ARENA_X, z - ARENA_Z);
                if (r >= 23 && r <= 30) continue;
                k.canvas.set(x, 1, z, "minecraft:soul_sand");
                k.canvas.set(x, 2, z, "minecraft:bubble_column[drag=false]");
                k.canvas.set(x, 3, z, "minecraft:bubble_column[drag=false]");
            }
        }
    }

    /** Lily pads on the surface, seagrass and sea pickles on the bed. */
    private static void waterLife(DistrictKit k) {
        for (int z = LO; z <= HI; z++) {
            for (int x = LO; x <= HI; x++) {
                if (!k.canvas.isAir(x, 4, z) || !k.canvas.get(x, 3, z).startsWith("minecraft:water")) continue;
                double h = hash(x, 0, z, 80);
                if (h < 0.10) k.canvas.set(x, 4, z, "minecraft:lily_pad");
                else if (h < 0.34) k.canvas.set(x, 2, z, "minecraft:seagrass");
                else if (h < 0.38) k.canvas.set(x, 2, z, "minecraft:sea_pickle[pickles=3,waterlogged=true]");
                else if (h < 0.395) {
                    String[] coral = {"tube", "brain", "bubble", "fire", "horn"};
                    String c = coral[(int) (hash(x, 2, z, 54) * coral.length)];
                    k.canvas.set(x, 2, z, "minecraft:" + c + "_coral_block");
                    k.canvas.set(x, 3, z, "minecraft:" + c + "_coral_fan[waterlogged=true]");
                }
            }
        }
    }
}
