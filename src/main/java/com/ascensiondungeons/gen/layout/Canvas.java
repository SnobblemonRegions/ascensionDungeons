package com.ascensiondungeons.gen.layout;

import com.ascensiondungeons.gen.SpongeSchematic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A 176 x 96 x 176 block canvas for authoring a district layout in code. Cells start as air. */
public final class Canvas {
    public static final int SIZE = 176;
    public static final int HEIGHT = 96;
    public static final String AIR = "minecraft:air";

    private final short[] blocks = new short[SIZE * HEIGHT * SIZE];
    private final List<String> palette = new ArrayList<>(List.of(AIR));
    private final Map<String, Integer> index = new HashMap<>(Map.of(AIR, 0));

    private static int at(int x, int y, int z) { return x + z * SIZE + y * SIZE * SIZE; }

    public static boolean inside(int x, int y, int z) {
        return x >= 0 && x < SIZE && z >= 0 && z < SIZE && y >= 0 && y < HEIGHT;
    }

    private int id(String state) {
        return index.computeIfAbsent(state, s -> {
            palette.add(s);
            return palette.size() - 1;
        });
    }

    public void set(int x, int y, int z, String state) {
        if (inside(x, y, z)) blocks[at(x, y, z)] = (short) id(state);
    }

    public String get(int x, int y, int z) {
        return inside(x, y, z) ? palette.get(blocks[at(x, y, z)]) : AIR;
    }

    public boolean isAir(int x, int y, int z) {
        return !inside(x, y, z) || blocks[at(x, y, z)] == 0;
    }

    /** True for air and the invisible light block. */
    public boolean isOpen(int x, int y, int z) {
        String s = get(x, y, z);
        return s.equals(AIR) || s.startsWith("minecraft:light[");
    }

    /** Fills the box (inclusive corners). */
    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
        int b = id(state);
        for (int y = Math.max(0, y0); y <= Math.min(HEIGHT - 1, y1); y++) {
            for (int z = Math.max(0, z0); z <= Math.min(SIZE - 1, z1); z++) {
                for (int x = Math.max(0, x0); x <= Math.min(SIZE - 1, x1); x++) blocks[at(x, y, z)] = (short) b;
            }
        }
    }

    /** A canvas holding a copy of a 176 x 96 x 176 schematic, with air remapped to index 0. */
    public static Canvas of(SpongeSchematic s) {
        if (s.width() != SIZE || s.height() != HEIGHT || s.length() != SIZE) throw new IllegalArgumentException("not a district-sized schematic");
        Canvas c = new Canvas();
        int[] map = new int[s.palette().length];
        for (int i = 0; i < map.length; i++) map[i] = c.id(s.palette()[i]);
        for (int i = 0; i < c.blocks.length; i++) c.blocks[i] = (short) map[s.blocks()[i]];
        return c;
    }

    public SpongeSchematic toSchematic() {
        return new SpongeSchematic(SIZE, HEIGHT, SIZE, palette.toArray(new String[0]), blocks.clone());
    }

    /** Deterministic hash in [0, 1) of three ints plus a salt. */
    public static double hash(int a, int b, int c, int salt) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Picks one of the weighted options for a cell. */
    public static String pick(int x, int y, int z, int salt, Object... weighted) {
        double r = hash(x, y, z, salt), acc = 0;
        for (int i = 0; i < weighted.length; i += 2) {
            acc += (Double) weighted[i + 1];
            if (r < acc) return (String) weighted[i];
        }
        return (String) weighted[weighted.length - 2];
    }
}
