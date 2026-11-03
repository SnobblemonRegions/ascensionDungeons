package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;
import static com.ascensiondungeons.gen.layout.Canvas.pick;

import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.ChorusSpire;
import com.ascensiondungeons.gen.landmark.EnderRing;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.landmark.PurpurVault;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.List;
import java.util.Map;

/**
 * End Spire: a big octagon of pale end stone under a black obsidian roof. The Chorus Spire stands at the centre where the
 * three spokes meet; the Ender Ring arena fills the west side with its door onto the plaza; the Purpur Vault stands in the
 * north-east. Obsidian spires caged at the top like the pillars of the dragon's island rise to the roof, chorus forests grow
 * across the end stone, purpur arches span the spokes, and crying obsidian and end rods hang from the roof.
 */
public final class EndSpireLayout {
    private EndSpireLayout() {}

    public static final int WALL = 11;
    public static final int CX = 88, CZ = 88;
    public static final int REACH = 96;
    public static final int CACHE_X = 116, CACHE_Z = 38;
    public static final int ARENA_X = 46, ARENA_Z = 88;
    public static final double ARENA_DOOR = 0;

    private static boolean in(int x, int z, int reach, int box) {
        return Math.abs(x - CX) <= box && Math.abs(z - CZ) <= box && Math.abs(x - CX) + Math.abs(z - CZ) <= reach;
    }

    private static final DistrictKit.Shape SHAPE = new DistrictKit.Shape() {
        @Override public boolean inner(int x, int z) { return x >= 20 && z >= 19 && x <= 161 && z <= 161 && in(x, z, REACH, 69); }

        @Override public boolean outer(int x, int z) { return in(x, z, REACH + 16, 80) && x >= 0 && z >= 0 && x <= 175 && z <= 175; }

        @Override public int ceiling(int x, int z) {
            double d = Math.min(1, Math.hypot(x - CX, z - CZ) / 80.0);
            return (int) (58 + 18 * (1 - d * d));
        }
    };

    private static final String END_STONE = "minecraft:end_stone";
    private static final String BRICKS = "minecraft:end_stone_bricks";
    private static final String PURPUR = "minecraft:purpur_block";
    private static final String PILLAR = "minecraft:purpur_pillar[axis=y]";
    private static final String OBSIDIAN = "minecraft:obsidian";
    private static final String CRYING = "minecraft:crying_obsidian";
    private static final String ROD_DOWN = "minecraft:end_rod[facing=down]";
    private static final String BARS = "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]";

    private static String floor(int x, int z) {
        return pick(x, 4, z, 211, END_STONE, .56, BRICKS, .24, PURPUR, .08, OBSIDIAN, .06, "minecraft:purpur_pillar[axis=y]", .06);
    }

    private static String road(int x, int z) {
        return pick(x, 4, z, 212, PURPUR, .55, BRICKS, .45);
    }

    private static String wall(int[] p, int y) {
        return pick(p[0], y, p[1], 213, OBSIDIAN, .34, BRICKS, .18, PURPUR, .14, PILLAR, .10, END_STONE, .12, CRYING, .06, "minecraft:magenta_glazed_terracotta[facing=north]", .06);
    }

    private static String arenaFloor(int x, int z) {
        return pick(x, 4, z, 214, BRICKS, .45, END_STONE, .35, PURPUR, .20);
    }

    public static Layout build() {
        DistrictKit k = new DistrictKit(SHAPE);
        k.shell(EndSpireLayout::floor, "minecraft:end_stone", "minecraft:obsidian", EndSpireLayout::wall);
        k.gates(PURPUR, OBSIDIAN);
        int last = Template.LAST;

        k.roadSeg(88, 0, 88, last, EndSpireLayout::road, OBSIDIAN);
        k.roadSeg(88, 88, last, 88, EndSpireLayout::road, OBSIDIAN);
        k.roadRing(CX, CZ, 17, EndSpireLayout::road, OBSIDIAN);
        k.reserveCircle(CX, CZ, 12);
        int[] door = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 20);
        k.roadSeg(door[0], door[2], 71, 88, EndSpireLayout::road, OBSIDIAN);
        k.reserveRect(CACHE_X - 2, CACHE_Z, CACHE_X + 16, CACHE_Z + 12, 0);
        k.roadSeg(CACHE_X + 7, CACHE_Z + 15, 100, 72, EndSpireLayout::road, OBSIDIAN);
        k.arenaChamber(ARENA_X, ARENA_Z, ARENA_DOOR, EndSpireLayout::arenaFloor, EndSpireLayout::wall, PILLAR, PURPUR);

        arches(k);
        spires(k);
        chorusForest(k);
        hangings(k);
        k.lights();

        List<Landmark> landmarks = List.of(ChorusSpire.landmarkAt(CX, CZ), PurpurVault.landmarkAt(CACHE_X, CACHE_Z),
                EnderRing.landmarkAt(ARENA_X, ARENA_Z, ARENA_DOOR));
        int[] act = WardensRing.pointToward(ARENA_X, ARENA_Z, ARENA_DOOR, 15);
        return new Layout(connectChorus(k.canvas.toSchematic()), landmarks, Map.of(
                "entry", new int[] {88, 140},
                "nav", new int[] {88, 106},
                "loot", new int[] {CACHE_X + 7, CACHE_Z + 15},
                "arena", new int[] {act[0], act[2]},
                "arena_centre", new int[] {ARENA_X, ARENA_Z}));
    }

    /** Purpur arches over the three spokes: two pillars, a lintel and a crown of end rods. */
    private static void arches(DistrictKit k) {
        for (int[] g : new int[][] {{88, 40, 0}, {88, 138, 0}, {138, 88, 1}}) {
            for (int s : new int[] {-7, 7}) {
                int x = g[2] == 0 ? g[0] + s : g[0], z = g[2] == 0 ? g[1] : g[1] + s;
                for (int y = 5; y <= 14; y++) k.canvas.fill(x, y, z, x + (g[2] == 0 ? 1 : 0), y, z + (g[2] == 0 ? 0 : 1), y % 4 == 0 ? PURPUR : PILLAR);
            }
            for (int s = -7; s <= 7; s++) {
                int x = g[2] == 0 ? g[0] + s : g[0], z = g[2] == 0 ? g[1] : g[1] + s;
                k.canvas.set(x, 15, z, s % 2 == 0 ? PURPUR : BRICKS);
                if (s % 3 == 0) k.canvas.set(x, 16, z, "minecraft:end_rod[facing=up]");
            }
            k.reserveRect(g[0] - 8, g[1] - 8, g[0] + 8, g[1] + 8, 0);
        }
    }

    /** Obsidian spires up to the roof, each capped with an iron-bar cage round a violet core. */
    private static void spires(DistrictKit k) {
        for (int[] p : new int[][] {{40, 40}, {136, 40}, {40, 136}, {138, 138}, {64, 112}, {112, 112}, {64, 64}, {132, 88}, {62, 22}, {114, 22}}) {
            int cx = p[0], cz = p[1];
            if (k.nearBlocked(cx, cz, 5)) continue;
            int top = Math.min(SHAPE.ceiling(cx, cz) - 8, 44 + (int) (hash(cx, 0, cz, 215) * 14));
            for (int y = 5; y <= top; y++) {
                double r = 2.8 * (1 - 0.5 * (y - 5.0) / (top - 5.0)) + 0.4;
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        if (Math.hypot(dx, dz) <= r) k.canvas.set(cx + dx, y, cz + dz, y % 9 == 0 ? CRYING : OBSIDIAN);
                    }
                }
            }
            // The cage: a ring of bars three high round a magenta glass core on an end rod.
            k.canvas.set(cx, top + 1, cz, "minecraft:end_rod[facing=up]");
            k.canvas.set(cx, top + 2, cz, "minecraft:magenta_stained_glass");
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
                for (int y = top + 1; y <= top + 3; y++) k.canvas.set(cx + d[0], y, cz + d[1], BARS);
            }
            k.canvas.set(cx, top + 3, cz, "minecraft:magenta_stained_glass");
            k.reserveCircle(cx, cz, 5);
        }
    }

    /** Chorus trees on the open end stone: a stem with two or three branches and grown flowers at the tips. */
    private static void chorusForest(DistrictKit k) {
        for (int z = 22; z <= 158; z += 7) {
            for (int x = 22; x <= 158; x += 7) {
                if (hash(x, 0, z, 216) > 0.5) continue;
                int cx = x + (int) (hash(x, 1, z, 216) * 3), cz = z + (int) (hash(x, 2, z, 216) * 3);
                if (k.nearBlocked(cx, cz, 3) || !k.canvas.get(cx, 4, cz).equals(END_STONE)) continue;
                int h = 3 + (int) (hash(x, 3, z, 216) * 4);
                for (int i = 0; i < h; i++) k.canvas.set(cx, 5 + i, cz, "minecraft:chorus_plant");
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                int n = 2 + (int) (hash(x, 4, z, 216) * 2);
                for (int b = 0; b < n; b++) {
                    int[] d = dirs[(int) (hash(x, 5 + b, z, 216) * 4)];
                    int by = 5 + h - 2 - (b % 2);
                    int len = 1 + (int) (hash(x, 9 + b, z, 216) * 2);
                    for (int s = 1; s <= len; s++) k.canvas.set(cx + d[0] * s, by, cz + d[1] * s, "minecraft:chorus_plant");
                    k.canvas.set(cx + d[0] * len, by + 1, cz + d[1] * len, "minecraft:chorus_plant");
                    k.canvas.set(cx + d[0] * len, by + 2, cz + d[1] * len, "minecraft:chorus_flower[age=5]");
                }
                k.canvas.set(cx, 5 + h, cz, "minecraft:chorus_flower[age=5]");
                k.reserved[cx][cz] = true;
            }
        }
    }

    /** Crying obsidian and end rods hanging from the roof; the roof itself is obsidian with violet blocks. */
    private static void hangings(DistrictKit k) {
        for (int z = 19; z <= 161; z++) {
            for (int x = 20; x <= 161; x++) {
                if (!SHAPE.inner(x, z)) continue;
                int c = SHAPE.ceiling(x, z);
                double h = hash(x, 0, z, 217);
                k.canvas.set(x, c, z, h < 0.10 ? PURPUR : h < 0.18 ? CRYING : OBSIDIAN);
                double s = hash(x, 1, z, 217);
                if (s < 0.004) k.canvas.set(x, c - 1, z, ROD_DOWN);
                else if (s < 0.012) {
                    int len = 2 + (int) (hash(x, 2, z, 217) * 6);
                    for (int j = 1; j <= len; j++) k.canvas.set(x, c - j, z, j == len ? CRYING : OBSIDIAN);
                }
            }
        }
    }

    /** Gives every chorus plant of the base its connection properties so it looks right without a block update. */
    private static com.ascensiondungeons.gen.SpongeSchematic connectChorus(com.ascensiondungeons.gen.SpongeSchematic s) {
        Canvas c = Canvas.of(s);
        for (int y = 5; y < 40; y++) {
            for (int z = 0; z < Canvas.SIZE; z++) {
                for (int x = 0; x < Canvas.SIZE; x++) {
                    if (!c.get(x, y, z).equals("minecraft:chorus_plant")) continue;
                    c.set(x, y, z, "minecraft:chorus_plant[down=" + link(c, x, y - 1, z, true) + ",east=" + link(c, x + 1, y, z, false)
                            + ",north=" + link(c, x, y, z - 1, false) + ",south=" + link(c, x, y, z + 1, false) + ",up=" + link(c, x, y + 1, z, false)
                            + ",west=" + link(c, x - 1, y, z, false) + "]");
                }
            }
        }
        return c.toSchematic();
    }

    private static boolean link(Canvas c, int x, int y, int z, boolean ground) {
        String b = c.get(x, y, z);
        return b.startsWith("minecraft:chorus_") || ground && b.equals(END_STONE);
    }
}
