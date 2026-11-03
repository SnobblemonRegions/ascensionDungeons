package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.landmark.CinderCrown;
import com.ascensiondungeons.gen.landmark.CrucibleRing;
import com.ascensiondungeons.gen.landmark.KilnCache;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.ObsidianCauseway;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Ashen Caldera: one round caldera. A lava lake lies west of the spine road, crossed by the obsidian causeway; roads run
 * from it to the three gates; the Cinder Crown stands south-east, the Kiln Cache in the south-west rim, and the Crucible
 * Ring in the north-east, its door facing the crossroads.
 */
public final class AshenLayout {
    private AshenLayout() {}

    public static final int CENTRE_X = 88, CENTRE_Z = 88;
    public static final int INNER_R = 69, OUTER_R = 77;
    public static final int LAKE_X = 58, LAKE_Z = 88;
    public static final double LAKE_R = ObsidianCauseway.LAKE_RADIUS;
    public static final int CROWN_X = 108, CROWN_Z = 134;
    public static final int CACHE_X = 32, CACHE_Z = 112;
    public static final int ARENA_X = 118, ARENA_Z = 54;
    public static final double ARENA_DOOR = 125;

    private static final String BLACKSTONE = "minecraft:blackstone";
    private static final String BASALT = "minecraft:basalt[axis=y]";
    private static final String SMOOTH = "minecraft:smooth_basalt";
    private static final String POLISHED = "minecraft:polished_blackstone_bricks";

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return Math.hypot(x - CENTRE_X, z - CENTRE_Z) <= INNER_R; }

        @Override public boolean outer(int x, int z) { return Math.hypot(x - CENTRE_X, z - CENTRE_Z) <= OUTER_R; }

        @Override public int ceiling(int x, int z) {
            double r = Math.hypot(x - CENTRE_X, z - CENTRE_Z) / INNER_R;
            return (int) (44 + 24 * (1 - r * r));
        }
    };

    private static String floor(int x, int z) {
        return pick(x, 4, z, 1, BLACKSTONE, .40, BASALT, .20, SMOOTH, .20, "minecraft:tuff", .10, "minecraft:deepslate", .10);
    }

    private static String roadSurface(int x, int z) {
        return pick(x, 4, z, 2, "minecraft:deepslate_bricks", .70, "minecraft:cracked_deepslate_bricks", .15,
                "minecraft:deepslate_tiles", .15);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 3, POLISHED, .35, BLACKSTONE, .30, "minecraft:deepslate_bricks", .15, BASALT, .10, SMOOTH, .10);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(AshenLayout::floor, "minecraft:stone", "minecraft:deepslate", AshenLayout::wall);
        k.gates("minecraft:deepslate_bricks", POLISHED);

        // Roads: the spine gate to gate, the east road from the lake shore to the east gate, and the causeway's west stub.
        int last = com.ascensiondungeons.gen.Template.LAST;
        k.roadSeg(88, 0, 88, last, AshenLayout::roadSurface, POLISHED);
        k.roadSeg(LAKE_X + LAKE_R + 2, 88, last, 88, AshenLayout::roadSurface, POLISHED);
        k.roadSeg(LAKE_X - LAKE_R - 2, 88, 30, 88, AshenLayout::roadSurface, POLISHED);

        // Raid chamber, NE, door toward the crossroads, joined to the spine by a short road.
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, AshenLayout::floorArena, AshenLayout::wall,
                "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone");
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], 92, 84, AshenLayout::roadSurface, POLISHED);

        // Kiln cache: built against the south-west rim, a road from its front to the spine.
        k.reserveRect(CACHE_X - 2, CACHE_Z - 2, CACHE_X + 16, CACHE_Z + 12, 0);
        int cacheMid = CACHE_X + 7;
        k.roadSeg(cacheMid, CACHE_Z + 13, 88, CACHE_Z + 13, AshenLayout::roadSurface, POLISHED);

        // Crown approach from the spine.
        k.roadSeg(88, CROWN_Z, CROWN_X - 12, CROWN_Z, AshenLayout::roadSurface, POLISHED);

        // Lake: lava over a basalt bed, filling the lake exactly (the curb is part of the causeway module).
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                if (Math.hypot(x - LAKE_X, z - LAKE_Z) > LAKE_R) continue;
                k.canvas.set(x, 3, z, "minecraft:basalt[axis=y]");
                k.canvas.set(x, 4, z, "minecraft:lava[level=0]");
                k.reserved[x][z] = true;
            }
        }
        k.reserveCircle(LAKE_X, LAKE_Z, 21);
        k.reserveRect(CROWN_X - 12, CROWN_Z - 12, CROWN_X + 12, CROWN_Z + 12, 0);

        // Terrain scatter: low ash drifts, then stout basalt boulders.
        k.scatter(11, 12, 0.55, 2, 6, (x, z) -> pick(x, 5, z, 4, BLACKSTONE, .5, SMOOTH, .3, "minecraft:tuff", .2));
        k.scatter(12, 18, 0.35, 4, 3.4, (x, z) -> pick(x, 6, z, 5, BASALT, .5, BLACKSTONE, .3, POLISHED, .2));
        rim(k);
        magmaFalls(k);
        channels(k);
        columns(k);
        gateways(k);
        geysers(k);
        braziers(k);
        stalactites(k);
        k.lights();

        List<Landmark> landmarks = List.of(CinderCrown.landmarkAt(CROWN_X, CROWN_Z),
                ObsidianCauseway.landmarkAt(LAKE_X, LAKE_Z), KilnCache.landmarkAt(CACHE_X, CACHE_Z),
                CrucibleRing.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {CROWN_X - 13, CROWN_Z},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 15},
                "explore", new int[] {LAKE_X + 22, 88},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    private static final String GLOW = "minecraft:ochre_froglight[axis=y]";
    private static final String BRICKS = "minecraft:polished_blackstone_bricks";

    private static boolean nearReserved(DistrictKit k, int x, int z, int m) {
        for (int dz = -m; dz <= m; dz++) {
            for (int dx = -m; dx <= m; dx++) {
                int a = x + dx, b = z + dz;
                if (a < 0 || b < 0 || a >= Canvas.SIZE || b >= Canvas.SIZE || k.reserved[a][b] || !SHAPE.inner(a, b)) return true;
            }
        }
        return false;
    }

    /** The caldera rim: three stepped terraces rising 1, 2 and 3 above the floor, with ember insets in the risers. */
    private static void rim(DistrictKit k) {
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                double r = Math.hypot(x - CENTRE_X, z - CENTRE_Z);
                if (r < 57 || !SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                int h = r < 61 ? 1 : r < 65 ? 2 : 3;
                double edge = r < 61 ? r - 57 : r < 65 ? r - 61 : r - 65;
                for (int y = 5; y <= 4 + h; y++) {
                    boolean riser = edge < 1.2;
                    String b = riser ? (y == 5 && hash(x, y, z, 21) < 0.16 ? GLOW : BRICKS)
                            : y == 4 + h ? pick(x, y, z, 22, SMOOTH, .5, BLACKSTONE, .3, "minecraft:tuff", .2) : BLACKSTONE;
                    k.canvas.set(x, y, z, b);
                }
            }
        }
    }

    /** Magma falls: three-wide strips of magma set into the rim wall behind brick frames, above the highest terrace. */
    private static void magmaFalls(DistrictKit k) {
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(30 * i + 15), ux = Math.cos(a), uz = Math.sin(a);
            for (int z = 0; z < Canvas.SIZE; z++) {
                for (int x = 0; x < Canvas.SIZE; x++) {
                    double dx = x - CENTRE_X, dz = z - CENTRE_Z, along = dx * ux + dz * uz, lateral = Math.abs(-dx * uz + dz * ux);
                    if (along < 64 || along > 69.5 || lateral > 2.6 || !SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                    for (int y = 8; y <= 30; y++) k.canvas.set(x, y, z, lateral <= 1.5 ? "minecraft:magma_block" : BRICKS);
                }
            }
        }
    }

    /** Two channels of cooled magma running out from the lake, fenced by 3-high curbs and capped wherever a road crosses. */
    private static void channels(DistrictKit k) {
        boolean[][] magma = new boolean[Canvas.SIZE][Canvas.SIZE];
        for (double deg : new double[] {205, 160}) {
            double a = Math.toRadians(deg), ux = Math.cos(a), uz = Math.sin(a);
            for (int z = 0; z < Canvas.SIZE; z++) {
                for (int x = 0; x < Canvas.SIZE; x++) {
                    double dx = x - LAKE_X, dz = z - LAKE_Z, along = dx * ux + dz * uz, lateral = Math.abs(-dx * uz + dz * ux);
                    if (along < 20 || along > 52 || lateral > 1.6 || !SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                    magma[x][z] = true;
                }
            }
        }
        for (int z = 1; z < Canvas.SIZE - 1; z++) {
            for (int x = 1; x < Canvas.SIZE - 1; x++) {
                if (magma[x][z] && (k.reserved[x + 1][z] || k.reserved[x - 1][z] || k.reserved[x][z + 1] || k.reserved[x][z - 1])) {
                    magma[x][z] = false;
                }
            }
        }
        for (int z = 1; z < Canvas.SIZE - 1; z++) {
            for (int x = 1; x < Canvas.SIZE - 1; x++) {
                if (!magma[x][z]) continue;
                k.canvas.set(x, 3, z, "minecraft:basalt[axis=y]");
                k.canvas.set(x, 4, z, "minecraft:magma_block");
            }
        }
        for (int z = 1; z < Canvas.SIZE - 1; z++) {
            for (int x = 1; x < Canvas.SIZE - 1; x++) {
                if (magma[x][z] || !nearMagma(magma, x, z) || !SHAPE.inner(x, z)) continue;
                for (int y = 4; y <= 6; y++) k.canvas.set(x, y, z, y == 6 ? BRICKS : BLACKSTONE);
                if (hash(x, 6, z, 23) < 0.12) k.canvas.set(x, 6, z, GLOW);
            }
        }
        for (int z = 1; z < Canvas.SIZE - 1; z++) {
            for (int x = 1; x < Canvas.SIZE - 1; x++) if (magma[x][z] || nearMagma(magma, x, z)) k.reserved[x][z] = true;
        }
    }

    private static boolean nearMagma(boolean[][] m, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (m[x + dx][z + dz]) return true;
        return false;
    }

    /** Clusters of basalt columns of uneven height, kept well clear of the Cinder Crown. */
    private static void columns(DistrictKit k) {
        for (int z = 22; z <= 154; z += 12) {
            for (int x = 22; x <= 154; x += 12) {
                if (hash(x, 0, z, 24) > 0.45) continue;
                int cx = x + (int) (hash(x, 1, z, 24) * 7) - 3, cz = z + (int) (hash(x, 2, z, 24) * 7) - 3;
                if (nearReserved(k, cx, cz, 6) || Math.hypot(cx - CROWN_X, cz - CROWN_Z) < 30) continue;
                int n = 3 + (int) (hash(x, 3, z, 24) * 5);
                for (int i = 0; i < n; i++) {
                    int px = cx + (int) (hash(x, 10 + i, z, 24) * 7) - 3, pz = cz + (int) (hash(x, 20 + i, z, 24) * 7) - 3;
                    int h = 3 + (int) (hash(px, 4, pz, 25) * 10), w = hash(px, 5, pz, 25) < 0.4 ? 1 : 0;
                    for (int y = 5; y < 5 + h; y++) {
                        k.canvas.fill(px, y, pz, px + w, y, pz + w,
                                pick(px, y, pz, 26, "minecraft:basalt[axis=y]", .5, SMOOTH, .3, BLACKSTONE, .2));
                    }
                    if (hash(px, 6, pz, 25) < 0.15) k.canvas.set(px, 5 + h, pz, GLOW);
                }
                k.reserveCircle(cx, cz, 5);
            }
        }
    }

    /** Charred gateways: two pillars and a lintel, broken in the middle half the time. */
    private static void gateways(DistrictKit k) {
        for (int z = 30; z <= 150; z += 24) {
            for (int x = 30; x <= 150; x += 24) {
                if (hash(x, 0, z, 27) > 0.4) continue;
                int cx = x + (int) (hash(x, 1, z, 27) * 9) - 4, cz = z + (int) (hash(x, 2, z, 27) * 9) - 4;
                if (nearReserved(k, cx, cz, 8) || Math.hypot(cx - CROWN_X, cz - CROWN_Z) < 30) continue;
                int h = 7 + (int) (hash(x, 3, z, 27) * 3);
                for (int px : new int[] {cx - 5, cx + 4}) {
                    for (int y = 5; y < 5 + h; y++) {
                        k.canvas.fill(px, y, cz, px + 1, y, cz + 1, y == 4 + h ? BRICKS
                                : pick(px, y, cz, 28, BRICKS, .5, BLACKSTONE, .3, "minecraft:cracked_polished_blackstone_bricks", .2));
                    }
                }
                for (int px = cx - 3; px <= cx + 3; px++) {
                    if (hash(x, 4, z, 27) < 0.5 && Math.abs(px - cx) <= 1) continue;
                    for (int y = 5 + h - 2; y < 5 + h; y++) k.canvas.fill(px, y, cz, px, y, cz + 1, BRICKS);
                }
                k.canvas.set(cx - 5, 5 + h, cz, GLOW);
                k.reserveRect(cx - 5, cz - 1, cx + 5, cz + 2, 1);
            }
        }
    }

    public static final String CAMPFIRE = "minecraft:campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]";
    public static final String HAY = "minecraft:hay_block[axis=y]";

    /** Smoke geysers: a lit signal campfire on a hay bale at the bottom of a small crater, walled in by a 2-high rim. */
    private static void geysers(DistrictKit k) {
        for (int z = 26; z <= 150; z += 10) {
            for (int x = 26; x <= 150; x += 10) {
                if (hash(x, 0, z, 32) > 0.70) continue;
                int cx = x + (int) (hash(x, 1, z, 32) * 9) - 4, cz = z + (int) (hash(x, 2, z, 32) * 9) - 4;
                double fromCentre = Math.hypot(cx - CENTRE_X, cz - CENTRE_Z);
                if (nearReserved(k, cx, cz, 4) || Math.hypot(cx - CROWN_X, cz - CROWN_Z) < 26 || fromCentre > 62) continue;
                geyser(k, cx, cz);
            }
        }
    }

    private static void geyser(DistrictKit k, int cx, int cz) {
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                double r = Math.hypot(dx, dz);
                int x = cx + dx, z = cz + dz;
                if (r < 0.5) {
                    k.canvas.set(x, 3, z, HAY);
                    k.canvas.set(x, 4, z, CAMPFIRE);
                } else if (r <= 1.6) {                                       // the rim: two blocks above the floor all round
                    k.canvas.set(x, 4, z, BRICKS);
                    k.canvas.set(x, 5, z, BLACKSTONE);
                    k.canvas.set(x, 6, z, Math.abs(dx) == 1 && Math.abs(dz) == 1 ? GLOW : BRICKS);
                } else if (r <= 2.5) {                                       // a sloped skirt, one block high
                    k.canvas.set(x, 4, z, "minecraft:basalt[axis=y]");
                    k.canvas.set(x, 5, z, "minecraft:basalt[axis=y]");
                } else if (r <= 3.3) {
                    k.canvas.set(x, 4, z, SMOOTH);
                }
            }
        }
        k.reserveCircle(cx, cz, 4);
    }

    /** Brazier posts every twelve blocks along both sides of the spine and the east road. */
    private static void braziers(DistrictKit k) {
        for (int t = 24; t <= 152; t += 12) {
            for (int side : new int[] {-7, 7}) {
                post(k, 88 + side, t);
                if (t >= 100) post(k, t, 88 + side);
            }
        }
    }

    private static void post(DistrictKit k, int x, int z) {
        if (x < 0 || z < 0 || x >= Canvas.SIZE || z >= Canvas.SIZE || !SHAPE.inner(x, z) || k.reserved[x][z] && !k.road[x][z]) return;
        if (k.road[x][z]) return;
        for (int y = 5; y <= 7; y++) k.canvas.set(x, y, z, BRICKS);
        k.canvas.set(x, 8, z, GLOW);
    }

    /** Stalactites hanging from the dome, a few tipped with ember light, and ember points set into the roof. */
    private static void stalactites(DistrictKit k) {
        for (int z = 22; z <= 154; z += 6) {
            for (int x = 22; x <= 154; x += 6) {
                if (hash(x, 0, z, 29) > 0.5 || !SHAPE.inner(x, z)) continue;
                int cx = x + (int) (hash(x, 1, z, 29) * 5) - 2, cz = z + (int) (hash(x, 2, z, 29) * 5) - 2;
                if (!SHAPE.inner(cx, cz)) continue;
                int ceil = SHAPE.ceiling(cx, cz), len = 4 + (int) (hash(x, 3, z, 29) * 10);
                for (int j = 0; j <= len; j++) {
                    double rad = 2.4 * (1 - (double) j / len);
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (Math.hypot(dx, dz) > rad + 0.2) continue;
                            k.canvas.set(cx + dx, ceil - 1 - j, cz + dz, j == len && hash(cx, 5, cz, 29) < 0.15 ? GLOW
                                    : pick(cx + dx, ceil - 1 - j, cz + dz, 30, BLACKSTONE, .5, "minecraft:basalt[axis=y]", .3, SMOOTH, .2));
                        }
                    }
                }
            }
        }
        for (int z = 22; z <= 154; z++) {
            for (int x = 22; x <= 154; x++) {
                if (SHAPE.inner(x, z) && hash(x, 0, z, 31) < 0.004) k.canvas.set(x, SHAPE.ceiling(x, z) - 1, z, GLOW);
            }
        }
    }

    private static String floorArena(int x, int z) {
        return pick(x, 4, z, 6, "minecraft:polished_blackstone", .5, "minecraft:blackstone", .25, SMOOTH, .25);
    }
}
