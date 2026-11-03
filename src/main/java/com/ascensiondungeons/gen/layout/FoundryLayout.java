package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.AnvilCourt;
import com.ascensiondungeons.gen.landmark.FurnaceHeart;
import com.ascensiondungeons.gen.landmark.GantryWalk;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.RiveterCache;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * Ancient Foundry: two halls set diagonally. The forge hall in the north-west and the assembly hall in the south-east
 * overlap in a short wide passage in the middle, which is where the three gate routes cross. The Furnace Heart stands
 * against the north wall of the forge hall, the Riveter's Cache is set into the same wall beside the north gate, the
 * Gantry Walk crosses a slag trench in the west arm, and the Anvil Court fills the assembly hall's south-east corner. Iron
 * girders and hanging lamps cross the roof; stout pillars, forge stacks with copper chimneys and risers on the walls fill
 * the floor.
 */
public final class FoundryLayout {
    private FoundryLayout() {}

    public static final int WALL = 11;
    public static final int FURNACE_X = 28, FURNACE_Z = 21;
    public static final int CACHE_X = 98, CACHE_Z = 21;
    public static final int GANTRY_X = 28, GANTRY_Z = 81;
    public static final int ARENA_X = 132, ARENA_Z = 130;
    public static final double ARENA_DOOR = 180;

    private static boolean inA(int x, int z, int m) { return x >= 20 - m && x <= 115 + m && z >= 20 - m && z <= 96 + m; }

    private static boolean inB(int x, int z, int m) { return x >= 55 - m && x <= 161 + m && z >= 80 - m && z <= 161 + m; }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return inA(x, z, 0) || inB(x, z, 0); }

        @Override public boolean outer(int x, int z) { return inA(x, z, WALL) || inB(x, z, WALL); }

        @Override public int ceiling(int x, int z) { return inA(x, z, 0) ? (inB(x, z, 0) ? 42 : 40) : 44; }
    };

    private static final String DEEP = "minecraft:deepslate_bricks";
    private static final String TILES = "minecraft:deepslate_tiles";
    private static final String ANDESITE = "minecraft:polished_andesite";
    private static final String STONE = "minecraft:stone_bricks";
    private static final String COPPER = "minecraft:cut_copper";
    private static final String IRON = "minecraft:iron_block";
    private static final String GLOW = "minecraft:ochre_froglight[axis=y]";
    private static final String CHAIN = "minecraft:chain[axis=y,waterlogged=false]";

    private static String floor(int x, int z) {
        if (x % 6 == 0 && z % 6 == 0) return COPPER;                                   // rivets
        return pick(x, 4, z, 101, TILES, .35, ANDESITE, .25, STONE, .15, "minecraft:cracked_deepslate_tiles", .10,
                "minecraft:polished_deepslate", .15);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 102, ANDESITE, .50, STONE, .30, "minecraft:polished_deepslate", .20);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 103, DEEP, .35, STONE, .20, ANDESITE, .15, TILES, .15, COPPER, .10, IRON, .05);
    }

    private static String floorArena(int x, int z) {
        return pick(x, 4, z, 104, ANDESITE, .60, "minecraft:polished_deepslate", .25, STONE, .15);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(FoundryLayout::floor, "minecraft:stone", "minecraft:stone", FoundryLayout::wall);
        k.gates(ANDESITE, COPPER);

        // Roads: the N-S spine, the E-W line to the gantry, and a road to each landmark.
        k.roadSeg(88, 0, 88, Template.LAST, FoundryLayout::road, COPPER);
        k.roadSeg(Template.LAST, 88, GANTRY_X + 28, 88, FoundryLayout::road, COPPER);
        k.reserveRect(FURNACE_X - 1, FURNACE_Z, FURNACE_X + 27, FURNACE_Z + 27, 0);
        k.roadSeg(FURNACE_X + 13, 48, 88, 48, FoundryLayout::road, COPPER);
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 17, 0);
        k.roadSeg(CACHE_X + 7, 37, 88, 37, FoundryLayout::road, COPPER);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, FoundryLayout::floorArena, FoundryLayout::wall, DEEP, ANDESITE);
        k.roadSeg(ARENA_X - 20, ARENA_Z, 88, ARENA_Z, FoundryLayout::road, COPPER);
        anvilRelief(k);

        // The gantry's slag trench: cooled slag one block above the bed, open to the air above, enclosed by the bank.
        k.reserveRect(GANTRY_X - 1, GANTRY_Z - 1, GANTRY_X + 29, GANTRY_Z + 15, 0);
        for (int z = GantryWalk.TRENCH_Z0; z <= GantryWalk.TRENCH_Z1; z++) {
            for (int x = GantryWalk.TRENCH_X0; x <= GantryWalk.TRENCH_X1; x++) {
                int wx = GANTRY_X + x, wz = GANTRY_Z + z;
                k.canvas.set(wx, 2, wz, "minecraft:magma_block");
                k.canvas.set(wx, 3, wz, Canvas.AIR);
                k.canvas.set(wx, 4, wz, Canvas.AIR);
            }
        }

        pillars(k);
        forges(k);
        risers(k);
        girders(k);
        k.lights();

        List<Landmark> landmarks = List.of(FurnaceHeart.landmarkAt(FURNACE_X, FURNACE_Z), RiveterCache.landmarkAt(CACHE_X, CACHE_Z),
                GantryWalk.landmarkAt(GANTRY_X, GANTRY_Z), AnvilCourt.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(k.canvas.toSchematic(), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {FURNACE_X + 13, 48},
                "loot", new int[] {CACHE_X + 7, 37},
                "explore", new int[] {GANTRY_X + 14, 88},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    private static boolean nearReserved(DistrictKit k, int x, int z, int m) {
        for (int dz = -m; dz <= m; dz++) {
            for (int dx = -m; dx <= m; dx++) {
                int a = x + dx, b = z + dz;
                if (a < 0 || b < 0 || a >= Canvas.SIZE || b >= Canvas.SIZE || k.reserved[a][b] || !SHAPE.inner(a, b)) return true;
            }
        }
        return false;
    }

    /** The anvil relief: a block-built anvil silhouette in iron with a copper outline, cut into the arena's rear wall. */
    private static void anvilRelief(DistrictKit k) {
        double rear = Math.toRadians(ARENA_DOOR + 180), ux = Math.cos(rear), uz = Math.sin(rear);
        for (int y = 17; y <= 27; y++) {
            int half;
            if (y >= 25) half = 9;                       // the face
            else if (y == 24) half = 5;                  // taper
            else if (y >= 21) half = 3;                  // the waist
            else if (y == 20) half = 6;                  // flare
            else half = 8;                               // the foot
            for (int t = -half; t <= half; t++) {
                boolean edge = Math.abs(t) == half || y == 27 || y == 17 || y == 25 || y == 20;
                for (double r : new double[] {18.7, 19.6}) {
                    int x = (int) Math.round(ARENA_X + r * ux - t * uz), z = (int) Math.round(ARENA_Z + r * uz + t * ux);
                    k.canvas.set(x, y, z, edge ? COPPER : IRON);
                }
            }
        }
    }

    /** Stout 3 x 3 pillars with copper bands, floor to roof. */
    private static void pillars(DistrictKit k) {
        for (int z = 36; z <= 156; z += 32) {
            for (int x = 36; x <= 156; x += 32) {
                if (!SHAPE.inner(x, z) || nearReserved(k, x, z, 5)) continue;
                int ceil = SHAPE.ceiling(x, z);
                for (int y = 5; y < ceil; y++) {
                    k.canvas.fill(x - 1, y, z - 1, x + 1, y, z + 1, y % 8 == 0 ? COPPER : (x + y) % 5 == 0 ? IRON : DEEP);
                }
                k.reserveRect(x - 1, z - 1, x + 1, z + 1, 1);
            }
        }
    }

    /** Forge stacks: squat deepslate bodies with a glowing front window and a copper chimney at the back corner. */
    private static void forges(DistrictKit k) {
        for (int z = 26; z <= 158; z += 14) {
            for (int x = 26; x <= 158; x += 14) {
                if (hash(x, 0, z, 105) > 0.6) continue;
                int cx = x + (int) (hash(x, 1, z, 105) * 5) - 2, cz = z + (int) (hash(x, 2, z, 105) * 5) - 2;
                if (nearReserved(k, cx, cz, 6)) continue;
                int h = 5 + (int) (hash(x, 3, z, 105) * 4);
                for (int dz = -3; dz <= 2; dz++) {
                    for (int dx = -3; dx <= 2; dx++) {
                        for (int y = 5; y < 5 + h; y++) {
                            k.canvas.set(cx + dx, y, cz + dz, y == 5 + h - 1 ? ANDESITE : pick(cx + dx, y, cz + dz, 106, DEEP, .6, TILES, .25, STONE, .15));
                        }
                    }
                }
                for (int dx = -1; dx <= 0; dx++) for (int y = 6; y <= 7; y++) k.canvas.set(cx + dx, y, cz + 2, GLOW);
                int ch = 6 + (int) (hash(x, 4, z, 105) * 6);
                for (int y = 5 + h; y < 5 + h + ch; y++) k.canvas.fill(cx - 3, y, cz - 3, cx - 2, y, cz - 2, y % 5 == 0 ? "minecraft:oxidized_cut_copper" : COPPER);
                // Chimney smoke: a lit signal campfire on a hay bale crowns the stack (the bale is what makes the tall column).
                k.canvas.set(cx - 3, 5 + h + ch - 1, cz - 3, FurnaceHeart.HAY);
                k.canvas.set(cx - 3, 5 + h + ch, cz - 3, FurnaceHeart.CAMPFIRE);
                k.reserveRect(cx - 3, cz - 3, cx + 2, cz + 2, 1);
            }
        }
    }

    /** Copper pipe risers up the walls, with a lamp every other one. */
    private static void risers(DistrictKit k) {
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z) || k.reserved[x][z]) continue;
                boolean edge = !SHAPE.inner(x + 1, z) || !SHAPE.inner(x - 1, z) || !SHAPE.inner(x, z + 1) || !SHAPE.inner(x, z - 1);
                if (!edge || hash(x, 0, z, 107) > 0.10) continue;
                int top = SHAPE.ceiling(x, z) - 4;
                for (int y = 5; y < top; y++) k.canvas.set(x, y, z, y % 7 == 0 ? IRON : COPPER);
                if (hash(x, 1, z, 107) < 0.5) k.canvas.set(x, 8, z, GLOW);
            }
        }
    }

    /** Iron girders across the roof every 32 blocks each way, with a chain and lamp hanging at each crossing. */
    private static void girders(DistrictKit k) {
        for (int z = 20; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z)) continue;
                int ceil = SHAPE.ceiling(x, z), u = x % 32, v = z % 32;
                if (u < 2 || v < 2) k.canvas.fill(x, ceil - 3, z, x, ceil - 1, z, (u < 2 && v < 2) || (x + z) % 4 == 0 ? IRON : DEEP);
                if (u == 0 && v == 0) {
                    for (int y = ceil - 8; y < ceil - 3; y++) k.canvas.set(x, y, z, CHAIN);
                    k.canvas.set(x, ceil - 9, z, GLOW);
                }
            }
        }
    }
}
