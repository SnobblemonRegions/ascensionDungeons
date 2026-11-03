package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.landmark.GlowcapSpring;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.RootboundCache;
import com.ascensiondungeons.gen.landmark.RootheartGrove;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Verdant Canopy: one rounded-square forest room under a rolling stone roof. A spine road runs gate to gate, a branch heads east, and
 * three spurs lead to the Rootheart Grove, the Rootbound Cache and the Glowcap Spring; the Warden's Ring stands in the
 * north-east with its door turned to the crossroads. Everything between the roads is the dense forest of
 * {@link VerdantForest}.
 */
public final class VerdantLayout {
    private VerdantLayout() {}

    public static final int CX = 88, CZ = 88;
    public static final int INNER_R = 69, OUTER_R = 77;
    public static final int ARENA_X = 118, ARENA_Z = 56;
    public static final double ARENA_DOOR = 130;
    public static final int GROVE_X = 50, GROVE_Z = 60;
    public static final int CACHE_X = 22, CACHE_Z = 69;
    public static final int SPRING_X = 112, SPRING_Z = 103;

    /** Norm of the rounded square: 1 on the boundary of the room whose half-width is {@code r}. */
    private static double norm(int x, int z) {
        double u = Math.abs(x - CX), v = Math.abs(z - CZ);
        return Math.pow(u * u * u * u + v * v * v * v, 0.25);
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return norm(x, z) <= INNER_R; }

        @Override public boolean outer(int x, int z) { return norm(x, z) <= OUTER_R; }

        @Override public int ceiling(int x, int z) {
            double r = Math.min(1, norm(x, z) / INNER_R);
            return (int) (54 + 12 * (1 - r * r));
        }
    };

    private static String floor(int x, int z) {
        return pick(x, 4, z, 1, "minecraft:grass_block[snowy=false]", .70, "minecraft:podzol[snowy=false]", .12,
                "minecraft:moss_block", .10, "minecraft:coarse_dirt", .08);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 2, "minecraft:stone_bricks", .75, "minecraft:mossy_stone_bricks", .15,
                "minecraft:cracked_stone_bricks", .10);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 3, "minecraft:stone_bricks", .55, "minecraft:mossy_stone_bricks", .30,
                "minecraft:cracked_stone_bricks", .15);
    }

    /** The bare terrain: shell, roads, arena chamber and spring pool, before the landmarks and the forest. */
    public static com.ascensiondungeons.gen.SpongeSchematic terrain() {
        return kit().canvas.toSchematic();
    }

    private static DistrictKit kit() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(VerdantLayout::floor, "minecraft:dirt", "minecraft:stone", VerdantLayout::wall);
        k.gates("minecraft:stone_bricks", "minecraft:mossy_stone_bricks");

        int last = com.ascensiondungeons.gen.Template.LAST;
        k.roadSeg(CX, 0, CX, last, VerdantLayout::road, "minecraft:mossy_stone_bricks");
        k.roadSeg(CX, CZ, last, CZ, VerdantLayout::road, "minecraft:mossy_stone_bricks");
        k.roadSeg(CX, CZ, CACHE_X + 8, CZ, VerdantLayout::road, "minecraft:mossy_stone_bricks");
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], CX + 3, CZ - 3, VerdantLayout::road, "minecraft:mossy_stone_bricks");
        k.roadSeg(CX, SPRING_Z + 11, SPRING_X - 3, SPRING_Z + 11, VerdantLayout::road, "minecraft:mossy_stone_bricks");

        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, (x, z) -> pick(x, 4, z, 6, "minecraft:polished_blackstone", .5,
                "minecraft:blackstone", .25, "minecraft:smooth_basalt", .25), VerdantLayout::arenaWall,
                "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone");

        // The spring's round pool: clay bed, water level with the floor, centred on module (6, 12).
        int px = SPRING_X + 6, pz = SPRING_Z + 12;
        for (int z = pz - 7; z <= pz + 7; z++) {
            for (int x = px - 7; x <= px + 7; x++) {
                if (Math.hypot(x - px, z - pz) > 6.4) continue;
                k.canvas.set(x, 3, z, "minecraft:clay");
                k.canvas.set(x, 4, z, "minecraft:water[level=0]");
            }
        }
        k.reserveRect(GROVE_X, GROVE_Z, GROVE_X + 20, GROVE_Z + 20, 2);
        k.reserveRect(CACHE_X, CACHE_Z, CACHE_X + 14, CACHE_Z + 10, 2);
        k.reserveRect(SPRING_X, SPRING_Z, SPRING_X + 19, SPRING_Z + 17, 2);
        k.lights();
        return k;
    }

    public static Layout build() {
        DistrictKit k = kit();
        List<Landmark> landmarks = List.of(RootheartGrove.landmarkAt(GROVE_X, GROVE_Z), GlowcapSpring.landmarkAt(SPRING_X, SPRING_Z),
                RootboundCache.verdantAt(CACHE_X, CACHE_Z),
                WardensRing.verdantAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        var base = VerdantAmbience.apply(VerdantForest.apply(k.canvas.toSchematic(), landmarks));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(base, landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {GROVE_X + 10, GROVE_Z + 28},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 14},
                "explore", new int[] {SPRING_X - 2, SPRING_Z + 11},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    private static String arenaWall(int[] p, int y) {
        return pick(p[0], y, p[1], 7, "minecraft:polished_blackstone_bricks", .6, "minecraft:blackstone", .25,
                "minecraft:cracked_polished_blackstone_bricks", .15);
    }
}
