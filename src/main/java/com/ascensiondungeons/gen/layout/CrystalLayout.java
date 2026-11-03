package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.GeodeVault;
import com.ascensiondungeons.gen.landmark.HarmonicRing;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.PrismCrown;
import com.ascensiondungeons.gen.landmark.ResonantGallery;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Crystal Cavern: a chain of caves. Six chambers of different sizes, with wobbling walls, are joined by short passages; the
 * Prism Crown stands in the middle cave with a road ring round it, the Resonant Gallery is a walk-through hall on the way
 * east, the Geode Vault is cut into the back of a side cave in the north-east, and the Harmonic Ring sits in the
 * south-west cave off the arrival cave. Dense crystal growth, hourglass pillars, hanging stalactites and a crystal-lined
 * ceiling fill the caves so none of them reads as an empty hall.
 */
public final class CrystalLayout {
    private CrystalLayout() {}

    public static final int WALL = 11;
    public static final int CROWN_X = 62, CROWN_Z = 88;
    public static final double RING_R = 19;
    public static final int GALLERY_X = 94, GALLERY_Z = 82;
    public static final int VAULT_X = 128, VAULT_Z = 28;
    public static final int ARENA_X = 46, ARENA_Z = 134;
    public static final double ARENA_DOOR = 5;

    private record Lobe(double x, double z, double r, double phase) {}

    private record Capsule(double ax, double az, double bx, double bz, double r) {}

    private record Rect(int x0, int z0, int x1, int z1) {}

    private static final Lobe[] LOBES = {
            new Lobe(88, 128, 26, 0.3),     // arrival cave (south)
            new Lobe(62, 88, 32, 1.9),      // central cave
            new Lobe(134, 88, 24, 3.1),     // east cave
            new Lobe(88, 44, 22, 4.4),      // north cave
            new Lobe(134, 44, 20, 5.2),     // north-east side cave
            new Lobe(46, 134, 31, 0.9),     // south-west cave (arena)
    };

    private static final Capsule[] PASSAGES = {
            new Capsule(88, 44, 66, 88, 10),
            new Capsule(66, 88, 88, 128, 10),
            new Capsule(62, 88, 134, 88, 10),
            new Capsule(88, 44, 134, 44, 10),
            new Capsule(46, 134, 88, 128, 10),
    };

    /** The gate approaches: straight, flat-ended halls to the wall faces at z=20 (north), z=161 (south) and x=161 (east). */
    private static final Rect[] HALLS = {new Rect(79, 20, 97, 48), new Rect(79, 120, 97, 161), new Rect(124, 79, 161, 97), new Rect(20, 79, 40, 97)};

    private static final float[][] SDF = new float[Canvas.SIZE][Canvas.SIZE];
    private static final int[][] CEILING = new int[Canvas.SIZE][Canvas.SIZE];

    static {
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                double best = 1e9, f = 0;
                for (Lobe l : LOBES) {
                    double dx = x - l.x(), dz = z - l.z();
                    double th = Math.atan2(dz, dx);
                    double reff = l.r() * (1 + 0.06 * Math.sin(3 * th + l.phase()) + 0.035 * Math.sin(5 * th + 2 * l.phase() + 1));
                    double d = Math.hypot(dx, dz);
                    best = Math.min(best, d - reff);
                    f = Math.max(f, 1 - (d / reff) * (d / reff));
                }
                for (Capsule c : PASSAGES) {
                    double d = DistrictKit.distToSegment(x, z, c.ax(), c.az(), c.bx(), c.bz());
                    best = Math.min(best, d - c.r());
                    f = Math.max(f, 0.35 * Math.max(0, 1 - (d / c.r()) * (d / c.r())));
                }
                for (Rect r : HALLS) best = Math.min(best, Math.max(Math.max(r.x0() - x, x - r.x1()), Math.max(r.z0() - z, z - r.z1())));
                SDF[x][z] = (float) best;
                CEILING[x][z] = 34 + (int) Math.round(18 * Math.max(0, f));
            }
        }
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return inRange(x, z) && SDF[x][z] <= 0; }

        @Override public boolean outer(int x, int z) { return inRange(x, z) && SDF[x][z] <= WALL; }

        @Override public int ceiling(int x, int z) { return CEILING[x][z]; }
    };

    private static boolean inRange(int x, int z) { return x >= 0 && z >= 0 && x < Canvas.SIZE && z < Canvas.SIZE; }

    private static String floor(int x, int z) {
        return pick(x, 4, z, 41, "minecraft:tuff", .30, "minecraft:smooth_basalt", .28, "minecraft:calcite", .20,
                "minecraft:dripstone_block", .10, "minecraft:amethyst_block", .04, "minecraft:polished_tuff", .08);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 42, "minecraft:polished_tuff", .55, "minecraft:calcite", .25, "minecraft:tuff_bricks", .20);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 43, "minecraft:smooth_basalt", .30, "minecraft:tuff", .25, "minecraft:calcite", .15,
                "minecraft:deepslate", .10, "minecraft:amethyst_block", .12, "minecraft:budding_amethyst", .02,
                "minecraft:dripstone_block", .06);
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 44, "minecraft:polished_tuff", .45, "minecraft:smooth_basalt", .30, "minecraft:calcite", .25);
    }

    private static int[] ringPoint(double towardX, double towardZ) {
        double a = Math.atan2(towardZ - CROWN_Z, towardX - CROWN_X);
        return new int[] {(int) Math.round(CROWN_X + RING_R * Math.cos(a)), (int) Math.round(CROWN_Z + RING_R * Math.sin(a))};
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(CrystalLayout::floor, "minecraft:stone", "minecraft:tuff", CrystalLayout::wall);
        k.gates("minecraft:polished_tuff", "minecraft:amethyst_block");

        // Roads: gate approaches into the caves, the ring round the Prism Crown, and a branch to each landmark.
        String edge = "minecraft:tuff_bricks";
        k.roadSeg(88, 0, 88, 44, CrystalLayout::road, edge);
        int[] toNorth = ringPoint(88, 44), toSouth = ringPoint(88, 128), toEast = ringPoint(134, 88);
        k.roadSeg(88, 44, toNorth[0], toNorth[1], CrystalLayout::road, edge);
        k.roadRing(CROWN_X, CROWN_Z, RING_R, CrystalLayout::road, edge);
        k.roadSeg(toSouth[0], toSouth[1], 88, 128, CrystalLayout::road, edge);
        k.roadSeg(88, 128, 88, Template.LAST, CrystalLayout::road, edge);
        k.roadSeg(toEast[0], 88, Template.LAST, 88, CrystalLayout::road, edge);
        k.roadSeg(88, 44, 134, 50, CrystalLayout::road, edge);

        // Raid chamber in the south-west cave, door toward the arrival cave.
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, CrystalLayout::arenaFloor, CrystalLayout::wall,
                "minecraft:chiseled_tuff", "minecraft:polished_tuff");
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], 88, 128, CrystalLayout::road, edge);

        // Landmark footprints.
        k.reserveCircle(CROWN_X, CROWN_Z, RING_R + 6);
        k.reserveRect(VAULT_X - 3, VAULT_Z - 3, VAULT_X + GeodeVault.SIZE_X + 2, VAULT_Z + GeodeVault.SIZE_Z + 3, 0);
        k.reserveRect(GALLERY_X - 3, GALLERY_Z - 3, GALLERY_X + ResonantGallery.SIZE_X + 2, GALLERY_Z + ResonantGallery.SIZE_Z + 3, 0);

        sealGallery(k);
        pillars(k);
        wallGrowth(k);
        outcrops(k);
        ceilingDetail(k);
        // Weeping amethyst: crying obsidian drips violet tears from some stalactite tips, end rods glint at others.
        for (int[] t : k.hangingTips(3, 24)) {
            double h = hash(t[0], t[1], t[2], 47);
            if (h < 0.10) k.canvas.set(t[0], t[1], t[2], "minecraft:crying_obsidian");
            else if (h < 0.17) k.canvas.set(t[0], t[1], t[2], "minecraft:end_rod[facing=down]");
        }
        k.scatter(45, 18, 0.45, 2, 5.5, (x, z) -> pick(x, 5, z, 46, "minecraft:tuff", .5, "minecraft:calcite", .3, "minecraft:smooth_basalt", .2));
        k.lights();

        List<Landmark> landmarks = List.of(PrismCrown.landmarkAt(CROWN_X, CROWN_Z), GeodeVault.landmarkAt(VAULT_X, VAULT_Z),
                ResonantGallery.landmarkAt(GALLERY_X, GALLERY_Z), HarmonicRing.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {CROWN_X, CROWN_Z + (int) RING_R},
                "loot", new int[] {VAULT_X + 7, VAULT_Z + GeodeVault.SIZE_Z + 5},
                "explore", new int[] {GALLERY_X + 13, 88},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    /**
     * The gallery is a real tunnel: every open cell beside it, between the walls of its x range and the cave roof, is rock, and
     * so is the space over its vault. The only way through is the lane, so it cannot be walked round.
     */
    private static void sealGallery(DistrictKit k) {
        for (int x = GALLERY_X; x < GALLERY_X + ResonantGallery.SIZE_X; x++) {
            for (int z = 56; z <= 120; z++) {
                if (!SHAPE.inner(x, z)) continue;
                int top = SHAPE.ceiling(x, z);
                for (int y = 5; y < top; y++) {
                    k.canvas.set(x, y, z, wall(new int[] {x, z}, y));
                }
                k.reserved[x][z] = true;
            }
        }
    }

    // ---- fill: pillars, wall growth, outcrops, ceiling ---------------------------------------------------------

    private static final int[][] PILLARS = {
            {70, 140}, {106, 140}, {72, 116}, {104, 116},                 // arrival cave
            {74, 34}, {102, 34}, {76, 56}, {100, 56},                     // north cave
            {48, 62}, {80, 62},                                           // central cave
            {120, 76}, {148, 78}, {122, 102}, {150, 102},                 // east cave
            {124, 56}, {146, 56},                                         // north-east cave
    };

    /** Hourglass pillars from floor to ceiling, wide at both ends and narrow in the middle. */
    private static void pillars(DistrictKit k) {
        for (int[] p : PILLARS) {
            int px = p[0], pz = p[1];
            if (!SHAPE.inner(px, pz) || k.reserved[px][pz]) continue;
            int top = SHAPE.ceiling(px, pz);
            for (int y = 5; y < top; y++) {
                double t = (y - 5.0) / (top - 5.0);
                double rr = 1.3 + 2.5 * (2 * t - 1) * (2 * t - 1);
                int ir = (int) Math.ceil(rr);
                for (int dz = -ir; dz <= ir; dz++) {
                    for (int dx = -ir; dx <= ir; dx++) {
                        if (Math.hypot(dx, dz) > rr) continue;
                        int x = px + dx, z = pz + dz;
                        if (!SHAPE.inner(x, z) || k.reserved[x][z] && !(dx == 0 && dz == 0)) continue;
                        k.canvas.set(x, y, z, pick(x, y, z, 47, "minecraft:calcite", .40, "minecraft:tuff", .28,
                                "minecraft:amethyst_block", .17, "minecraft:smooth_basalt", .15));
                    }
                }
            }
            k.reserveCircle(px, pz, 4.5);
        }
    }

    /** Amethyst growing out of the walls: 3 to 8 high where a floor cell touches the wall. */
    private static void wallGrowth(DistrictKit k) {
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = 1; z < Canvas.SIZE - 1; z++) {
            for (int x = 1; x < Canvas.SIZE - 1; x++) {
                if (!SHAPE.inner(x, z) || k.reserved[x][z] || hash(x, 0, z, 48) > 0.20) continue;
                boolean touches = false;
                for (int[] d : n) if (!SHAPE.inner(x + d[0], z + d[1])) touches = true;
                if (!touches) continue;
                int h = 3 + (int) (hash(x, 1, z, 48) * 6);
                for (int y = 5; y < 5 + h; y++) {
                    k.canvas.set(x, y, z, pick(x, y, z, 49, "minecraft:amethyst_block", .80, "minecraft:budding_amethyst", .05,
                            "minecraft:calcite", .15));
                }
                k.canvas.set(x, 5 + h, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
            }
        }
    }

    /** Clumps of 3 to 5 crystal columns scattered across the floor away from roads and landmarks. */
    private static void outcrops(DistrictKit k) {
        int spacing = 8;
        for (int gz = spacing / 2; gz < Canvas.SIZE; gz += spacing) {
            for (int gx = spacing / 2; gx < Canvas.SIZE; gx += spacing) {
                if (hash(gx, 0, gz, 50) > 0.80) continue;
                int cx = gx + (int) (hash(gx, 1, gz, 50) * 5) - 2, cz = gz + (int) (hash(gx, 2, gz, 50) * 5) - 2;
                int cols = 3 + (int) (hash(gx, 3, gz, 50) * 3);
                for (int i = 0; i < cols; i++) {
                    double a = hash(gx, 10 + i, gz, 50) * Math.PI * 2, rr = i == 0 ? 0 : 1.2 + hash(gx, 20 + i, gz, 50) * 1.6;
                    int x = (int) Math.round(cx + rr * Math.cos(a)), z = (int) Math.round(cz + rr * Math.sin(a));
                    if (x < 1 || z < 1 || x >= Canvas.SIZE - 1 || z >= Canvas.SIZE - 1) continue;
                    if (!SHAPE.inner(x, z) || k.reserved[x][z] || !k.canvas.isAir(x, 5, z)) continue;
                    int h = i == 0 ? 3 + (int) (hash(gx, 4, gz, 50) * 5) : 1 + (int) (hash(gx, 30 + i, gz, 50) * 4);
                    for (int y = 5; y < 5 + h; y++) {
                        k.canvas.set(x, y, z, y == 5 ? "minecraft:calcite" : pick(x, y, z, 51, "minecraft:amethyst_block", .88,
                                "minecraft:budding_amethyst", .12));
                    }
                    double q = hash(x, 5, z, 51);
                    k.canvas.set(x, 5 + h, z, q < .6 ? "minecraft:amethyst_cluster[facing=up,waterlogged=false]"
                            : "minecraft:large_amethyst_bud[facing=up,waterlogged=false]");
                }
            }
        }
    }

    /** A crystal lining on the roof, hanging clusters and stalactites. Nothing hangs low over roads or landmarks. */
    private static void ceilingDetail(DistrictKit k) {
        for (int z = 1; z < Canvas.SIZE - 1; z++) {
            for (int x = 1; x < Canvas.SIZE - 1; x++) {
                if (!SHAPE.inner(x, z)) continue;
                int c = SHAPE.ceiling(x, z);
                k.canvas.set(x, c, z, pick(x, c, z, 52, "minecraft:tuff", .50, "minecraft:calcite", .15, "minecraft:smooth_basalt", .14,
                        "minecraft:amethyst_block", .12, "minecraft:budding_amethyst", .03, "minecraft:dripstone_block", .04,
                        "minecraft:sea_lantern", .02));
            }
        }
        for (int z = 2; z < Canvas.SIZE - 2; z += 4) {
            for (int x = 2; x < Canvas.SIZE - 2; x += 4) {
                int sx = x + (int) (hash(x, 1, z, 53) * 3) - 1, sz = z + (int) (hash(x, 2, z, 53) * 3) - 1;
                if (!SHAPE.inner(sx, sz) || hash(x, 0, z, 53) > 0.55) continue;
                if (Math.hypot(sx - ARENA_X, sz - ARENA_Z) < 27) continue;
                int c = SHAPE.ceiling(sx, sz);
                int len = 2 + (int) (hash(x, 3, z, 53) * 7);
                if (k.reserved[sx][sz]) len = Math.min(len, c - 32);
                if (len < 2) continue;
                String body = pick(sx, 1, sz, 54, "minecraft:calcite", .45, "minecraft:tuff", .35, "minecraft:dripstone_block", .20);
                for (int i = 0; i < len; i++) {
                    int y = c - 1 - i;
                    boolean wide = i < 2 && len >= 4;
                    if (wide) {
                        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int nx = sx + d[0], nz = sz + d[1];
                            if (SHAPE.inner(nx, nz) && k.canvas.isAir(nx, y, nz) && (i == 0 || hash(nx, y, nz, 55) < 0.5)) {
                                k.canvas.set(nx, y, nz, body);
                            }
                        }
                    }
                    k.canvas.set(sx, y, sz, body);
                }
                k.canvas.set(sx, c - 1 - len, sz, "minecraft:amethyst_cluster[facing=down,waterlogged=false]");
            }
        }
    }
}
