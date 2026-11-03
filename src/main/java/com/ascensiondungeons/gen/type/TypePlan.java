package com.ascensiondungeons.gen.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Immutable seeded plan; independent of Minecraft and of chunk request order. */
public record TypePlan(String type, long seed, List<Room> rooms) {
    public static final int CELL = 64, HEIGHT = 48, FLOOR = 4;
    public static final int N = 1, E = 2, S = 4, W = 8;
    public record Room(int cx, int cz, int shell, int variant, int rotation, int doors, int landmark) {}
    public TypePlan { rooms = List.copyOf(rooms); }
    public static TypePlan fire(long seed) {
        Random graph = new Random(seed ^ 0x4752415048L);
        int[] parent = new int[12], doors = new int[12];
        for (int i = 0; i < 12; i++) parent[i] = i;
        List<int[]> edges = new ArrayList<>();
        for (int z = 0; z < 3; z++) for (int x = 0; x < 4; x++) {
            int i = z * 4 + x;
            if (x < 3) edges.add(new int[] {i, i + 1, E, W});
            if (z < 2) edges.add(new int[] {i, i + 4, S, N});
        }
        Collections.shuffle(edges, graph);
        for (int[] edge : edges) {
            int a = root(parent, edge[0]), b = root(parent, edge[1]);
            if (a != b) { parent[a] = b; connect(doors, edge); }
        }
        int loops = 0;
        for (int[] edge : edges) if (loops < 2 && (doors[edge[0]] & edge[2]) == 0 && graph.nextBoolean()) {
            connect(doors, edge); loops++;
        }
        Random geometry = new Random(seed ^ 0x5348454c4cL), content = new Random(seed ^ 0x4c414e444dL);
        List<Integer> landmarks = new ArrayList<>();
        for (int i = 0; i < 15; i++) landmarks.add(i);
        Collections.shuffle(landmarks, content);
        List<Room> rooms = new ArrayList<>();
        List<Integer> initial = new ArrayList<>(List.of(0, 1, 2, 3, 4));
        Collections.shuffle(initial, geometry);
        for (int i = 0; i < 12; i++) {
            List<Integer> choices = new ArrayList<>(List.of(0, 1, 2, 3, 4));
            if (i % 4 > 0) choices.remove(Integer.valueOf(rooms.get(i - 1).shell()));
            if (i >= 4) choices.remove(Integer.valueOf(rooms.get(i - 4).shell()));
            int shell = i < 5 ? initial.get(i) : choices.get(geometry.nextInt(choices.size()));
            rooms.add(new Room(i % 4, i / 4, shell, geometry.nextInt(3), geometry.nextInt(4), doors[i], landmarks.get(i)));
        }
        return new TypePlan("fire", seed, rooms);
    }
    private static int root(int[] p, int n) { while (p[n] != n) n = p[n]; return n; }
    private static void connect(int[] doors, int[] edge) { doors[edge[0]] |= edge[2]; doors[edge[1]] |= edge[3]; }
    public Room at(int x, int z) {
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        return cx >= 0 && cx < 4 && cz >= 0 && cz < 3 ? rooms.get(cz * 4 + cx) : null;
    }
    public int[] entry() { return new int[] {32, FLOOR + 1, 32}; }
    public int[] boss() { return new int[] {5 * CELL + 32, FLOOR + 1, 32}; }
    public int[] staging() { int[] b = boss(); return new int[] {b[0], b[1], b[2] + 16}; }
}
