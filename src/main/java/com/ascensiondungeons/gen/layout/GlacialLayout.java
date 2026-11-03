package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.AuroraCircle;
import com.ascensiondungeons.gen.landmark.FrostheartArch;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.MirrorwaterShelf;
import com.ascensiondungeons.gen.landmark.RimeReliquary;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Glacial Vault: a cross-shaped hall. A nave runs north to south and a transept crosses it east to west; the avenues
 * follow their axes between the gates, colonnades line the nave, and everything is square and vaulted. The Frostheart Arch
 * spans the nave avenue north of the crossing, the Rime Reliquary is cut into the north wall of the west arm, the
 * Mirrorwater Shelf fills the north-east corner of the east arm, and the Aurora Circle is a round chamber built into the
 * south-west corner, its door opening north onto the west arm.
 */
public final class GlacialLayout {
    private GlacialLayout() {}

    public static final int NAVE_X0 = 56, NAVE_X1 = 120, NAVE_Z0 = 20, NAVE_Z1 = 161;
    public static final int TRANSEPT_X0 = 20, TRANSEPT_X1 = 161, TRANSEPT_Z0 = 56, TRANSEPT_Z1 = 120;
    public static final int WALL = 11;
    public static final int ARCH_X = 78, ARCH_Z = 34;
    public static final int CACHE_X = 28, CACHE_Z = 56;
    public static final int POOL_X = 141, POOL_Z = 72;
    public static final int ARENA_X = 44, ARENA_Z = 134;
    public static final double ARENA_DOOR = 270;
    private static final double ARENA_ROOM = 23.5;

    private static boolean inRect(int x, int z, int x0, int z0, int x1, int z1, int m) {
        return x >= x0 - m && x <= x1 + m && z >= z0 - m && z <= z1 + m;
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) {
            return inRect(x, z, NAVE_X0, NAVE_Z0, NAVE_X1, NAVE_Z1, 0) || inRect(x, z, TRANSEPT_X0, TRANSEPT_Z0, TRANSEPT_X1, TRANSEPT_Z1, 0)
                    || Math.hypot(x - ARENA_X, z - ARENA_Z) <= ARENA_ROOM;
        }

        @Override public boolean outer(int x, int z) {
            return inRect(x, z, NAVE_X0, NAVE_Z0, NAVE_X1, NAVE_Z1, WALL) || inRect(x, z, TRANSEPT_X0, TRANSEPT_Z0, TRANSEPT_X1, TRANSEPT_Z1, WALL)
                    || Math.hypot(x - ARENA_X, z - ARENA_Z) <= ARENA_ROOM + WALL;
        }

        @Override public int ceiling(int x, int z) {
            double best = 0;
            if (Math.hypot(x - ARENA_X, z - ARENA_Z) <= ARENA_ROOM) best = Math.max(best, 0.5);
            if (inRect(x, z, NAVE_X0, NAVE_Z0, NAVE_X1, NAVE_Z1, 0)) {
                double d = (x - 88) / 36.0;
                best = Math.max(best, 1 - d * d);
            }
            if (inRect(x, z, TRANSEPT_X0, TRANSEPT_Z0, TRANSEPT_X1, TRANSEPT_Z1, 0)) {
                double d = (z - 88) / 36.0;
                best = Math.max(best, 1 - d * d);
            }
            return (int) (40 + 28 * best);
        }
    };

    private static String floor(int x, int z) {
        return pick(x, 4, z, 21, "minecraft:snow_block", .55, "minecraft:calcite", .20, "minecraft:diorite", .15,
                "minecraft:polished_diorite", .10);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 22, "minecraft:polished_diorite", .70, "minecraft:quartz_bricks", .20, "minecraft:calcite", .10);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 23, "minecraft:quartz_bricks", .30, "minecraft:calcite", .30, "minecraft:packed_ice", .25,
                "minecraft:snow_block", .15);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(GlacialLayout::floor, "minecraft:stone", "minecraft:stone", GlacialLayout::wall);
        k.gates("minecraft:polished_diorite", "minecraft:quartz_bricks");

        // The two avenues, and the plaza where they cross.
        k.roadSeg(88, 0, 88, Template.LAST, GlacialLayout::road, "minecraft:quartz_bricks");
        k.roadSeg(TRANSEPT_X0 + 4, 88, Template.LAST, 88, GlacialLayout::road, "minecraft:quartz_bricks");
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                double r = Math.hypot(x - 88, z - 88);
                if (r > 12 || !SHAPE.inner(x, z)) continue;
                k.canvas.set(x, 4, z, r > 10.5 ? "minecraft:quartz_bricks" : r < 2.5 ? "minecraft:chiseled_quartz_block" : road(x, z));
                k.road[x][z] = true;
                k.reserved[x][z] = true;
            }
        }

        // Raid chamber in the south-west corner, door north onto the west arm.
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, GlacialLayout::arenaFloor, GlacialLayout::wall,
                "minecraft:quartz_bricks", "minecraft:polished_diorite");
        k.roadSeg(ARENA_X, ARENA_Z - 20, ARENA_X, 88, GlacialLayout::road, "minecraft:quartz_bricks");

        // Reliquary: a road from its front down to the avenue.
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 11, 0);
        int cacheMid = CACHE_X + 7;
        k.roadSeg(cacheMid, CACHE_Z + 11, cacheMid, 88, GlacialLayout::road, "minecraft:quartz_bricks");

        // Mirrorwater shelf: the pool, then a road from the east avenue to its west shore.
        for (int z = POOL_Z - 8; z <= POOL_Z + 8; z++) {
            for (int x = POOL_X - 8; x <= POOL_X + 8; x++) {
                double r = Math.hypot(x - POOL_X, z - POOL_Z);
                if (r <= 6.2) {
                    k.canvas.set(x, 3, z, "minecraft:clay");
                    k.canvas.set(x, 4, z, "minecraft:water[level=0]");
                } else if (r <= 7.2) {
                    k.canvas.set(x, 4, z, "minecraft:gravel");
                }
            }
        }
        k.reserveRect(POOL_X - 8, POOL_Z - 12, POOL_X + 14, POOL_Z + 10, 0);
        k.roadSeg(125, 84, 125, POOL_Z, GlacialLayout::road, "minecraft:quartz_bricks");
        k.roadSeg(125, POOL_Z, POOL_X - 9, POOL_Z, GlacialLayout::road, "minecraft:quartz_bricks");

        k.reserveRect(ARCH_X, ARCH_Z, ARCH_X + 20, ARCH_Z + 20, 1);

        // Colonnade: stout 3 x 3 quartz piers up to the vault, either side of the north-south avenue.
        for (int z = 24; z <= 156; z += 18) {
            for (int x : new int[] {76, 98}) {
                if (overlapsReserved(k, x - 1, z - 1, x + 3, z + 3)) continue;
                int ceil = SHAPE.ceiling(x, z);
                k.canvas.fill(x, 5, z, x + 2, ceil - 1, z + 2, "minecraft:quartz_pillar[axis=y]");
                k.reserveRect(x, z, x + 2, z + 2, 1);
            }
        }

        // Terrain scatter: drifts of snow and a few stout frozen boulders, and flush patches of blue ice off the roads.
        k.scatter(31, 12, 0.5, 2, 6, (x, z) -> pick(x, 5, z, 24, "minecraft:snow_block", .7, "minecraft:calcite", .3));
        k.scatter(32, 20, 0.3, 3, 3.4, (x, z) -> pick(x, 6, z, 25, "minecraft:packed_ice", .5, "minecraft:calcite", .3,
                "minecraft:snow_block", .2));
        ribs(k);
        pilasters(k);
        buttresses(k);
        spires(k);
        icePatches(k);
        icicles(k);
        // Diamond dust: an end rod at the tip of some icicles throws pale glints into the air round it.
        for (int[] t : k.hangingTips(6, 32)) {
            if (hash(t[0], t[1], t[2], 61) < 0.04) k.canvas.set(t[0], t[1], t[2], "minecraft:end_rod[facing=down]");
        }
        k.lights();

        List<Landmark> landmarks = List.of(FrostheartArch.landmarkAt(ARCH_X, ARCH_Z), MirrorwaterShelf.landmarkAt(POOL_X, POOL_Z),
                RimeReliquary.landmarkAt(CACHE_X, CACHE_Z), AuroraCircle.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {88, ARCH_Z + 26},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 13},
                "explore", new int[] {POOL_X - 12, POOL_Z},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    private static final String PACKED = "minecraft:packed_ice";
    private static final String BLUE = "minecraft:blue_ice";
    private static final String QUARTZ = "minecraft:quartz_bricks";
    private static final String PEARL = "minecraft:pearlescent_froglight[axis=y]";
    private static final String CHAIN = "minecraft:chain[axis=y,waterlogged=false]";

    private static boolean nearReserved(DistrictKit k, int x, int z, int m) {
        for (int dz = -m; dz <= m; dz++) {
            for (int dx = -m; dx <= m; dx++) {
                int a = x + dx, b = z + dz;
                if (a < 0 || b < 0 || a >= Canvas.SIZE || b >= Canvas.SIZE || k.reserved[a][b] || !SHAPE.inner(a, b)) return true;
            }
        }
        return false;
    }

    /** Vault ribs: bands of quartz and calcite under the ceiling every 24 blocks, across the nave and across the transept. */
    private static void ribs(DistrictKit k) {
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                if (!SHAPE.inner(x, z)) continue;
                boolean nave = inRect(x, z, NAVE_X0, NAVE_Z0, NAVE_X1, NAVE_Z1, 0), transept = inRect(x, z, TRANSEPT_X0, TRANSEPT_Z0, TRANSEPT_X1, TRANSEPT_Z1, 0);
                boolean rib = nave && (z - NAVE_Z0) % 24 < 2 || transept && !nave && (x - TRANSEPT_X0) % 24 < 2;
                if (!rib) continue;
                int ceil = SHAPE.ceiling(x, z);
                for (int y = ceil - 3; y < ceil; y++) k.canvas.set(x, y, z, hash(x, y, z, 51) < 0.5 ? QUARTZ : "minecraft:calcite");
                if (x == 88 && (z - NAVE_Z0) % 24 == 0 && nave) {
                    for (int y = ceil - 9; y < ceil - 3; y++) k.canvas.set(x, y, z, CHAIN);
                    k.canvas.set(x, ceil - 10, z, PEARL);
                }
            }
        }
    }

    /** Pilasters: 2-wide quartz pillars standing against the walls every 12 blocks, up to the vault. */
    private static void pilasters(DistrictKit k) {
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                if (!SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                boolean eastWest = !SHAPE.inner(x + 1, z) || !SHAPE.inner(x - 1, z), northSouth = !SHAPE.inner(x, z + 1) || !SHAPE.inner(x, z - 1);
                if (eastWest == northSouth) continue;
                int along = eastWest ? z : x;
                if (along % 12 >= 2) continue;
                int ceil = SHAPE.ceiling(x, z);
                for (int y = 5; y < ceil; y++) k.canvas.set(x, y, z, y % 9 == 0 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_pillar[axis=y]");
            }
        }
    }

    /** Ice buttresses: blobs of blue ice grown up the wall from the floor, with a pale light at the foot of some. */
    private static void buttresses(DistrictKit k) {
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                if (!SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                boolean edge = !SHAPE.inner(x + 1, z) || !SHAPE.inner(x - 1, z) || !SHAPE.inner(x, z + 1) || !SHAPE.inner(x, z - 1);
                if (!edge || hash(x, 0, z, 52) > 0.035 || nearReserved(k, x, z, 1)) continue;
                int h = 8 + (int) (hash(x, 1, z, 52) * 14);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int px = x + dx, pz = z + dz;
                        if (!SHAPE.inner(px, pz) || k.reserved[px][pz]) continue;
                        int ph = h - 3 * (Math.abs(dx) + Math.abs(dz));
                        for (int y = 5; y < 5 + ph; y++) k.canvas.set(px, y, pz, pick(px, y, pz, 53, BLUE, .5, PACKED, .4, "minecraft:snow_block", .1));
                    }
                }
                k.canvas.set(x, 5, z, PEARL);
            }
        }
    }

    /** Clusters of ice spires on the floor, kept off every route and landmark. */
    private static void spires(DistrictKit k) {
        for (int z = 26; z <= 156; z += 14) {
            for (int x = 26; x <= 156; x += 14) {
                if (hash(x, 0, z, 54) > 0.5) continue;
                int cx = x + (int) (hash(x, 1, z, 54) * 7) - 3, cz = z + (int) (hash(x, 2, z, 54) * 7) - 3;
                if (nearReserved(k, cx, cz, 5)) continue;
                int n = 2 + (int) (hash(x, 3, z, 54) * 4);
                for (int i = 0; i < n; i++) {
                    int px = cx + (int) (hash(x, 10 + i, z, 54) * 7) - 3, pz = cz + (int) (hash(x, 20 + i, z, 54) * 7) - 3;
                    int h = 4 + (int) (hash(px, 4, pz, 55) * 8);
                    double r0 = 1.4 + hash(px, 5, pz, 55) * 1.4;
                    for (int j = 0; j < h; j++) {
                        double rad = r0 * (1 - (double) j / h);
                        for (int dz = -2; dz <= 2; dz++) {
                            for (int dx = -2; dx <= 2; dx++) {
                                if (Math.hypot(dx, dz) > rad + 0.2) continue;
                                k.canvas.set(px + dx, 5 + j, pz + dz, pick(px + dx, 5 + j, pz + dz, 56, BLUE, .6, PACKED, .4));
                            }
                        }
                    }
                }
                k.reserveCircle(cx, cz, 5);
            }
        }
    }

    /** Flush patches of packed and blue ice, off the routes: cold ground, not a crossing. */
    private static void icePatches(DistrictKit k) {
        for (int z = 28; z <= 154; z += 12) {
            for (int x = 28; x <= 154; x += 12) {
                if (hash(x, 0, z, 57) > 0.35) continue;
                int cx = x + (int) (hash(x, 1, z, 57) * 5) - 2, cz = z + (int) (hash(x, 2, z, 57) * 5) - 2;
                double r = 2.5 + hash(x, 3, z, 57) * 3;
                if (nearReserved(k, cx, cz, (int) r + 2)) continue;
                for (int dz = -7; dz <= 7; dz++) {
                    for (int dx = -7; dx <= 7; dx++) {
                        if (Math.hypot(dx, dz) <= r && k.canvas.isAir(cx + dx, 5, cz + dz)) {
                            k.canvas.set(cx + dx, 4, cz + dz, hash(cx + dx, 4, cz + dz, 58) < 0.5 ? PACKED : BLUE);
                        }
                    }
                }
            }
        }
    }

    /**
     * Ice stalactites hanging from the vault: long tapering cones of packed and blue ice with calcite streaks, a few
     * monumental ones over the nave and transept, some tipped with a pale light. Their tips stay above y=32, clear of every
     * landmark and the arena walls beneath.
     */
    private static void icicles(DistrictKit k) {
        for (int z = 22; z <= 156; z += 5) {
            for (int x = 22; x <= 156; x += 5) {
                if (hash(x, 0, z, 59) > 0.55 || !SHAPE.inner(x, z)) continue;
                int cx = x + (int) (hash(x, 1, z, 59) * 5) - 2, cz = z + (int) (hash(x, 2, z, 59) * 5) - 2;
                if (!SHAPE.inner(cx, cz)) continue;
                boolean giant = hash(x, 6, z, 59) < 0.14;
                int ceil = SHAPE.ceiling(cx, cz);
                int want = giant ? 14 + (int) (hash(x, 3, z, 59) * 16) : 6 + (int) (hash(x, 3, z, 59) * 14);
                int len = Math.min(want, ceil - 1 - 32);
                if (len < 4) continue;
                double r0 = giant ? 3.2 + hash(x, 7, z, 59) * 1.4 : 1.5 + hash(x, 7, z, 59) * 1.5;
                int reach = (int) Math.ceil(r0) + 1;
                for (int j = 0; j <= len; j++) {
                    double rad = r0 * Math.pow(1 - (double) j / len, 0.85);
                    for (int dz = -reach; dz <= reach; dz++) {
                        for (int dx = -reach; dx <= reach; dx++) {
                            if (Math.hypot(dx, dz) > rad + 0.2) continue;
                            k.canvas.set(cx + dx, ceil - 1 - j, cz + dz, j == len && hash(cx, 5, cz, 59) < 0.3 ? PEARL
                                    : pick(cx + dx, ceil - 1 - j, cz + dz, 60, PACKED, .5, BLUE, .38, "minecraft:calcite", .12));
                        }
                    }
                }
            }
        }
    }

    private static boolean overlapsReserved(DistrictKit k, int x0, int z0, int x1, int z1) {
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) if (k.reserved[x][z]) return true;
        return false;
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 26, "minecraft:polished_diorite", .45, "minecraft:quartz_bricks", .30, "minecraft:calcite", .25);
    }
}
