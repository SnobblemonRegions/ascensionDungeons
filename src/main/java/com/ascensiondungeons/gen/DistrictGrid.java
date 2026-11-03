package com.ascensiondungeons.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The districts of one run: a square block of 176 x 176 cells around the origin, joined through their gates.
 *
 * <p>Every district template has three gates and a solid fourth side, so a cell can have at most three open sides.
 * Doors are chosen as a random spanning tree (everything is reachable from the start) plus a few extra loops, never
 * giving a cell a fourth door. A cell's orientation is then picked so its solid side faces a closed edge; any gate
 * that is not used is plugged. Pure function of (seed, radius, available biomes).
 */
public final class DistrictGrid {
    public static final int CELL = Template.SIZE;
    private static final int MAX_DOORS = 3;
    private static final double EXTRA_DOOR_CHANCE = 0.25;

    /** One district placed in the grid. {@code open} are the sides joined to a neighbouring district. */
    public record Cell(int cx, int cz, BiomeKind biome, Orientation orientation, Set<Side> open) {}

    private final long seed;
    private final int radius;
    private final Map<Long, Cell> cells = new HashMap<>();

    public DistrictGrid(long seed, int radius, List<BiomeKind> available) {
        if (radius < 0 || radius > 4) throw new IllegalArgumentException("radius " + radius);
        if (available.isEmpty()) throw new IllegalArgumentException("no biomes available");
        this.seed = seed;
        this.radius = radius;
        build(available);
    }

    public long seed() { return seed; }

    /** Cells reach from -radius to +radius on each axis. */
    public int radius() { return radius; }

    /** Width of the whole grid in blocks. */
    public int extent() { return (2 * radius + 1) * CELL; }

    public Cell cell(int cx, int cz) { return cells.get(key(cx, cz)); }

    public List<Cell> cells() { return List.copyOf(cells.values()); }

    /** The cell column index containing world coordinate {@code c}: cell 0 covers -88 .. 87. */
    public static int cellIndex(int c) { return Math.floorDiv(c + Template.CENTER, CELL); }

    /** Offset from the cell centre, -88 .. 87. */
    public static int offsetInCell(int c) { return Math.floorMod(c + Template.CENTER, CELL) - Template.CENTER; }

    public Cell cellAt(int x, int z) { return cell(cellIndex(x), cellIndex(z)); }

    /** Where the party arrives: the entry vestibule of the centre district, as world {x, y, z}. */
    public int[] entry() {
        Cell c = cell(0, 0);
        int[] d = c.orientation().toWorld(Template.ENTRY_X - Template.CENTER, Template.ENTRY_Z - Template.CENTER);
        return new int[] {d[0], Template.FLOOR_Y + 1, d[1]};
    }

    // ---- construction ---------------------------------------------------------------------------------------

    private record Edge(int ax, int az, Side side) {
        int bx() { return ax + side.dx; }
        int bz() { return az + side.dz; }
    }

    private void build(List<BiomeKind> available) {
        List<Edge> all = new ArrayList<>();
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                if (cx < radius) all.add(new Edge(cx, cz, Side.E));
                if (cz < radius) all.add(new Edge(cx, cz, Side.S));
            }
        }
        Set<Edge> doors = chooseDoors(all);

        Map<Long, Set<Side>> open = new HashMap<>();
        for (Edge e : doors) {
            open.computeIfAbsent(key(e.ax(), e.az()), k -> EnumSet.noneOf(Side.class)).add(e.side());
            open.computeIfAbsent(key(e.bx(), e.bz()), k -> EnumSet.noneOf(Side.class)).add(e.side().opposite());
        }
        SplittableRandom rng = new SplittableRandom(seed ^ 0x2545F4914F6CDD1DL);
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                Set<Side> sides = open.getOrDefault(key(cx, cz), EnumSet.noneOf(Side.class));
                List<Side> closable = new ArrayList<>(List.of(Side.values()));
                closable.removeAll(sides);
                Side closed = closable.get(rng.nextInt(closable.size()));
                Orientation o = Orientation.forClosedSide(closed, rng.nextBoolean());
                BiomeKind biome = available.get(rng.nextInt(available.size()));
                cells.put(key(cx, cz), new Cell(cx, cz, biome, o, Set.copyOf(sides)));
            }
        }
    }

    /** Random spanning tree with at most three doors per cell, then a few extra loops. */
    private Set<Edge> chooseDoors(List<Edge> all) {
        for (int attempt = 0; attempt < 200; attempt++) {
            SplittableRandom rng = new SplittableRandom(seed + attempt * 0x9E3779B97F4A7C15L);
            List<Edge> shuffled = new ArrayList<>(all);
            Collections.shuffle(shuffled, new java.util.Random(rng.nextLong()));
            UnionFind uf = new UnionFind();
            Map<Long, Integer> degree = new HashMap<>();
            Set<Edge> chosen = new java.util.LinkedHashSet<>();
            for (Edge e : shuffled) {
                long a = key(e.ax(), e.az()), b = key(e.bx(), e.bz());
                if (degree.getOrDefault(a, 0) >= MAX_DOORS || degree.getOrDefault(b, 0) >= MAX_DOORS) continue;
                if (uf.union(a, b)) {
                    chosen.add(e);
                    degree.merge(a, 1, Integer::sum);
                    degree.merge(b, 1, Integer::sum);
                }
            }
            if (chosen.size() != cellCount() - 1) continue;
            for (Edge e : shuffled) {
                long a = key(e.ax(), e.az()), b = key(e.bx(), e.bz());
                if (chosen.contains(e) || degree.getOrDefault(a, 0) >= MAX_DOORS || degree.getOrDefault(b, 0) >= MAX_DOORS) continue;
                if (rng.nextDouble() < EXTRA_DOOR_CHANCE) {
                    chosen.add(e);
                    degree.merge(a, 1, Integer::sum);
                    degree.merge(b, 1, Integer::sum);
                }
            }
            return chosen;
        }
        return snake();
    }

    /** Fallback that always works: one winding path through every cell (at most two doors each). */
    private Set<Edge> snake() {
        Set<Edge> path = new java.util.LinkedHashSet<>();
        for (int cz = -radius; cz <= radius; cz++) {
            boolean eastward = ((cz + radius) & 1) == 0;
            for (int i = 0; i < 2 * radius; i++) {
                int cx = eastward ? -radius + i : radius - i - 1;
                path.add(new Edge(cx, cz, Side.E));
            }
            if (cz < radius) path.add(new Edge(eastward ? radius : -radius, cz, Side.S));
        }
        return path;
    }

    private int cellCount() { return (2 * radius + 1) * (2 * radius + 1); }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    private static final class UnionFind {
        private final Map<Long, Long> parent = new HashMap<>();

        long find(long x) {
            Long p = parent.get(x);
            if (p == null || p == x) return x;
            long root = find(p);
            parent.put(x, root);
            return root;
        }

        boolean union(long a, long b) {
            long ra = find(a), rb = find(b);
            if (ra == rb) return false;
            parent.put(ra, rb);
            return true;
        }
    }
}
