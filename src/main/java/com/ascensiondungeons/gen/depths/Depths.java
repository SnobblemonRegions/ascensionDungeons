package com.ascensiondungeons.gen.depths;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Depths: a large procedural area under an Outbreak dive, different for every run. A grid of cells is partly filled with
 * rooms; the rooms are joined into one connected maze (a random spanning tree, plus extra passages so there are loops and more
 * than one way round), and each room gets one of several shapes. Pure: no Minecraft types. The same seed and parameters always
 * give the same Depths, and {@link #generate} guarantees every room can be walked to from the entrance.
 *
 * <p>Coordinates are block offsets from the Depths' own corner. Everything is one storey: floors at {@code y = 1}, so a
 * standing cell is at {@code y = 2}. A block is a role (wall, floor, accent, pillar, air, light); the game layer chooses
 * the actual blocks from the dungeon's own materials.
 */
public final class Depths {
    public static final byte WALL = 0, AIR = 1, FLOOR = 2, ACCENT = 3, LIGHT = 4, PILLAR = 5;
    /** Height of the whole slab in blocks. */
    public static final int HEIGHT = 12;
    /** A standing cell's y. */
    public static final int STAND = 2;

    public enum Kind { ENTRANCE, HALL, PILLARS, CROSS, RING, AISLES, DAIS, VAULT }

    /**
     * @param gridW     cells across
     * @param gridH     cells deep
     * @param cell      blocks per cell side (a room and half of each passage live in a cell)
     * @param occupancy share of the grid that holds a room, 0..1
     * @param loops     chance that two neighbouring rooms not already joined get an extra passage, 0..1
     * @param passage   how wide the passages between rooms are: 5 (six high, room for the largest Pokémon) or 3 (four high)
     */
    public record Params(int gridW, int gridH, int cell, double occupancy, double loops, int passage) {
        /** Wide passages, the default. */
        public Params(int gridW, int gridH, int cell, double occupancy, double loops) { this(gridW, gridH, cell, occupancy, loops, 5); }

        public Params {
            if (passage != 3 && passage != 5) throw new IllegalArgumentException("a passage is 3 or 5 blocks wide");
            if (gridW < 2 || gridH < 2 || gridW > 16 || gridH > 16) throw new IllegalArgumentException("the grid must be 2..16 cells each way");
            if (cell < 20 || cell > 48) throw new IllegalArgumentException("a cell must be 20..48 blocks");
            if (occupancy <= 0 || occupancy > 1) throw new IllegalArgumentException("occupancy must be in (0, 1]");
            if (loops < 0 || loops > 1) throw new IllegalArgumentException("loops must be 0..1");
        }
    }

    /** One room. {@code min}/{@code max} bound its cell's interior; {@code cells} are places a creature or a chest can stand. */
    public record Room(String id, Kind kind, int gx, int gz, int[] min, int[] max, List<int[]> cells, int doors) {}

    private final Params params;
    private final int sizeX, sizeZ;
    private final byte[] roles;
    private final List<Room> rooms = new ArrayList<>();
    private int[] entrance;
    private int passages;

    private Depths(Params params) {
        this.params = params;
        this.sizeX = params.gridW() * params.cell();
        this.sizeZ = params.gridH() * params.cell();
        this.roles = new byte[sizeX * HEIGHT * sizeZ];
    }

    public Params params() { return params; }
    public int sizeX() { return sizeX; }
    public int sizeZ() { return sizeZ; }
    public List<Room> rooms() { return rooms; }
    /** The standing cell a party arrives on: the middle of the entrance room. */
    public int[] entrance() { return entrance; }
    /** How many passages join the rooms (rooms - 1 would be a tree; more means loops). */
    public int passages() { return passages; }

    /** The role at a block; anything outside the slab is wall. */
    public byte role(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= HEIGHT || z >= sizeZ) return WALL;
        return roles[(y * sizeZ + z) * sizeX + x];
    }

    public boolean open(int x, int y, int z) { byte r = role(x, y, z); return r == AIR || r == LIGHT; }

    private boolean door;
    private int doorHalf = -1;

    /** Half the doorway's width (2 for a five-wide one), or -1 when there is none. */
    public int doorHalf() { return doorHalf; }

    /** Whether the slab has a doorway in its near wall. */
    public boolean hasDoor() { return door; }

    /** The x of the middle of the doorway (and of the entrance room). */
    public static int doorX(Params p) { return (p.gridW() / 2) * p.cell() + p.cell() / 2; }

    private void setRaw(int x, int y, int z, byte role) { roles[(y * sizeZ + z) * sizeX + x] = role; }

    private void set(int x, int y, int z, byte role) {
        if (x < 1 || y < 0 || z < 1 || x >= sizeX - 1 || y >= HEIGHT || z >= sizeZ - 1) return;   // the outer shell stays wall
        roles[(y * sizeZ + z) * sizeX + x] = role;
    }

    public Room roomAt(int x, int z) {
        for (Room r : rooms) if (x >= r.min()[0] && x <= r.max()[0] && z >= r.min()[2] && z <= r.max()[2]) return r;
        return null;
    }

    // ---- generation ----------------------------------------------------------------------------------------------

    public static Depths generate(long seed, Params p) { return generate(seed, p, -1); }

    /** As {@link #generate(long, Params, int)}, with a doorway as wide as the maze's own passages when {@code door} is true. */
    public static Depths generate(long seed, Params p, boolean door) { return generate(seed, p, door ? p.passage() / 2 : -1); }

    /** How high a passage (or a doorway) of this half-width is: wide ones are six high, narrow ones four. */
    public static int passageHeight(int half) { return half >= 2 ? 6 : 4; }

    /**
     * @param doorHalf when not negative the entrance room is opened to the slab's near edge (z = 0) with a passage
     *                 {@code 2 * doorHalf + 1} wide, so a tunnel from outside can walk straight in. The opening is centred on
     *                 {@link #doorX} at {@code z = 0}, standing level {@link #STAND}.
     */
    public static Depths generate(long seed, Params p, int doorHalf) {
        boolean door = doorHalf >= 0;
        Random rng = new Random(seed ^ 0x4445505448534CL);
        Depths d = new Depths(p);
        int w = p.gridW(), h = p.gridH();
        boolean[] used = new boolean[w * h];
        int start = w / 2;                                   // the middle of the near edge
        // 1. Which cells hold rooms: grow outward from the entrance so the region is one piece.
        int want = Math.max(2, (int) Math.round(w * h * p.occupancy()));
        List<Integer> frontier = new ArrayList<>();
        used[start] = true;
        int count = 1;
        addNeighbours(frontier, used, start, w, h);
        while (count < want && !frontier.isEmpty()) {
            int pick = frontier.remove(rng.nextInt(frontier.size()));
            if (used[pick]) continue;
            used[pick] = true;
            count++;
            addNeighbours(frontier, used, pick, w, h);
        }
        // 2. Join them: a random spanning tree (every room reachable), then extra passages for loops.
        List<int[]> edges = new ArrayList<>();
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        seen[start] = true;
        while (!stack.isEmpty()) {
            int at = stack.peek();
            List<Integer> next = new ArrayList<>();
            for (int n : neighbours(at, w, h)) if (used[n] && !seen[n]) next.add(n);
            if (next.isEmpty()) { stack.pop(); continue; }
            int to = next.get(rng.nextInt(next.size()));
            seen[to] = true;
            edges.add(new int[] {at, to});
            stack.push(to);
        }
        for (int a = 0; a < w * h; a++) {
            if (!used[a]) continue;
            for (int b : new int[] {a + 1, a + w}) {
                if (b >= w * h || !used[b] || (b == a + 1 && a % w == w - 1)) continue;
                boolean joined = false;
                for (int[] e : edges) if ((e[0] == a && e[1] == b) || (e[0] == b && e[1] == a)) { joined = true; break; }
                if (!joined && rng.nextDouble() < p.loops()) edges.add(new int[] {a, b});
            }
        }
        d.passages = edges.size();
        int[] degree = new int[w * h];
        for (int[] e : edges) { degree[e[0]]++; degree[e[1]]++; }
        // 3. Shapes. Dead ends make good vaults; the entrance is always an open hall.
        Kind[] kinds = new Kind[w * h];
        Kind[] common = {Kind.HALL, Kind.PILLARS, Kind.CROSS, Kind.RING, Kind.AISLES, Kind.DAIS};
        for (int c = 0; c < w * h; c++) {
            if (!used[c]) continue;
            kinds[c] = c == start ? Kind.ENTRANCE : degree[c] == 1 && rng.nextDouble() < 0.7 ? Kind.VAULT : common[rng.nextInt(common.length)];
        }
        // 4. Carve passages first, then rooms, then what stands inside the rooms.
        for (int[] e : edges) d.carvePassage(e[0] % w, e[0] / w, e[1] % w, e[1] / w);
        for (int c = 0; c < w * h; c++) if (used[c]) d.carveRoom(c % w, c / w, kinds[c], rng);
        int mid = p.cell() / 2;
        d.entrance = new int[] {start * p.cell() + mid, STAND, mid};
        if (door) {
            // The way in: from the entrance room's middle out through the slab's near wall.
            int cx = start * p.cell() + mid;
            for (int x = cx - doorHalf; x <= cx + doorHalf; x++)
                for (int z = 0; z <= mid; z++) {
                    d.setRaw(x, 1, z, FLOOR);
                    for (int y = STAND; y < STAND + passageHeight(doorHalf); y++) d.setRaw(x, y, z, AIR);
                }
            d.door = true;
            d.doorHalf = doorHalf;
        }
        // 5. Prove it: every room must be reachable. A shape that walls itself off is replaced by an open hall.
        for (int attempt = 0; attempt < 3; attempt++) {
            boolean[] reach = d.flood();
            boolean changed = false;
            for (int c = 0; c < w * h; c++) {
                if (!used[c] || d.reachableInCell(reach, c % w, c / w)) continue;
                d.clearCell(c % w, c / w);
                for (int[] e : edges) if (e[0] == c || e[1] == c) d.carvePassage(e[0] % w, e[0] / w, e[1] % w, e[1] / w);
                d.carveRoom(c % w, c / w, Kind.HALL, rng);
                kinds[c] = Kind.HALL;
                changed = true;
            }
            if (!changed) break;
        }
        // 6. Lights, and the list of rooms with their standing cells.
        boolean[] reach = d.flood();
        for (int c = 0; c < w * h; c++) {
            if (!used[c]) continue;
            int gx = c % w, gz = c / w, x0 = gx * p.cell(), z0 = gz * p.cell();
            d.light(x0, z0);
            List<int[]> cells = new ArrayList<>();
            for (int x = x0 + 2; x < x0 + p.cell() - 2; x++)
                for (int z = z0 + 2; z < z0 + p.cell() - 2; z++)
                    for (int y = STAND; y <= STAND + 1; y++)
                        if (d.standable(x, y, z) && reach[d.index2(x, y, z)]) cells.add(new int[] {x, y, z});
            Collections.shuffle(cells, new Random(seed * 31 + c));
            if (cells.size() > 90) cells = new ArrayList<>(cells.subList(0, 90));
            d.rooms.add(new Room("D" + (gx + 1) + "_" + (gz + 1), kinds[c], gx, gz, new int[] {x0 + 1, 0, z0 + 1},
                    new int[] {x0 + p.cell() - 2, HEIGHT - 1, z0 + p.cell() - 2}, List.copyOf(cells), degree[c]));
        }
        return d;
    }

    private static void addNeighbours(List<Integer> frontier, boolean[] used, int at, int w, int h) {
        for (int n : neighbours(at, w, h)) if (!used[n]) frontier.add(n);
    }

    private static List<Integer> neighbours(int at, int w, int h) {
        List<Integer> out = new ArrayList<>(4);
        int x = at % w, z = at / w;
        if (x > 0) out.add(at - 1);
        if (x < w - 1) out.add(at + 1);
        if (z > 0) out.add(at - w);
        if (z < h - 1) out.add(at + w);
        return out;
    }

    /** A passage between two neighbouring cells, centre to centre: five wide and six high, or three and four. */
    private void carvePassage(int ax, int az, int bx, int bz) {
        int c = params.cell(), mid = c / 2, half = params.passage() / 2, high = passageHeight(half);
        int x1 = Math.min(ax, bx) * c + mid, x2 = Math.max(ax, bx) * c + mid, z1 = Math.min(az, bz) * c + mid, z2 = Math.max(az, bz) * c + mid;
        for (int x = x1 - half; x <= x2 + half; x++)
            for (int z = z1 - half; z <= z2 + half; z++) {
                set(x, 1, z, FLOOR);
                for (int y = STAND; y < STAND + high; y++) set(x, y, z, AIR);
            }
    }

    private void clearCell(int gx, int gz) {
        int c = params.cell();
        for (int x = gx * c; x < (gx + 1) * c; x++) for (int z = gz * c; z < (gz + 1) * c; z++) for (int y = 0; y < HEIGHT; y++) set(x, y, z, WALL);
    }

    private void box(int x1, int z1, int x2, int z2, int height) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, 1, z, edge ? ACCENT : FLOOR);
                for (int y = STAND; y < STAND + height; y++) set(x, y, z, AIR);
            }
    }

    private void column(int x, int z, int size, int height, byte role) {
        for (int dx = 0; dx < size; dx++) for (int dz = 0; dz < size; dz++) for (int y = STAND; y < STAND + height; y++) set(x + dx, y, z + dz, role);
    }

    private void carveRoom(int gx, int gz, Kind kind, Random rng) {
        int c = params.cell(), x0 = gx * c, z0 = gz * c, mid = c / 2, cx = x0 + mid, cz = z0 + mid;
        int half = (c - 6) / 2;                              // a full room leaves three blocks of wall to the cell edge
        switch (kind) {
            case ENTRANCE, HALL -> {
                box(cx - half, cz - half, cx + half, cz + half, 8);
                int o = half - 3;
                for (int sx : new int[] {-1, 1}) for (int sz : new int[] {-1, 1}) column(cx + sx * o - (sx > 0 ? 1 : 0), cz + sz * o - (sz > 0 ? 1 : 0), 2, 8, PILLAR);
            }
            case PILLARS -> {
                box(cx - half, cz - half, cx + half, cz + half, 6);
                // Pillars every third block, but never on the five-wide lanes through the middle.
                for (int ax = 3; ax <= half - 2; ax += 3)
                    for (int az = 3; az <= half - 2; az += 3)
                        for (int sx : new int[] {-1, 1}) for (int sz : new int[] {-1, 1}) column(cx + sx * ax, cz + sz * az, 1, 6, PILLAR);
            }
            case CROSS -> {
                int arm = half + 1;
                box(cx - arm, cz - 2, cx + arm, cz + 2, 6);
                box(cx - 2, cz - arm, cx + 2, cz + arm, 6);
                box(cx - 4, cz - 4, cx + 4, cz + 4, 7);
            }
            case RING -> {
                box(cx - half, cz - half, cx + half, cz + half, 6);
                int core = Math.max(1, half - 5);               // a walkway five wide all the way round
                for (int x = cx - core; x <= cx + core; x++) for (int z = cz - core; z <= cz + core; z++) {
                    boolean rim = x == cx - core || x == cx + core || z == cz - core || z == cz + core;
                    for (int y = STAND; y < STAND + 6; y++) set(x, y, z, rim ? PILLAR : WALL);
                }
            }
            case AISLES -> {
                box(cx - half, cz - half, cx + half, cz + half, 6);
                boolean alongX = rng.nextBoolean();
                // Two walls make three aisles; each wall has five-wide gaps in the middle and at both ends.
                for (int o : new int[] {-4, 4}) {
                    for (int t = -half + 1; t <= half - 1; t++) {
                        if (Math.abs(t) <= 2 || Math.abs(t) >= half - 4) continue;
                        for (int y = STAND; y < STAND + 6; y++) set(alongX ? cx + t : cx + o, y, alongX ? cz + o : cz + t, WALL);
                    }
                }
            }
            case DAIS -> {
                box(cx - half, cz - half, cx + half, cz + half, 7);
                for (int x = cx - 3; x <= cx + 3; x++) for (int z = cz - 3; z <= cz + 3; z++) set(x, STAND, z, ACCENT);   // a platform one block up
                for (int sx : new int[] {-1, 1}) for (int sz : new int[] {-1, 1}) column(cx + sx * 5, cz + sz * 5, 1, 7, PILLAR);
            }
            case VAULT -> {
                box(cx - 5, cz - 5, cx + 5, cz + 5, 6);
                for (int x = cx - 4; x <= cx + 4; x++) for (int z = cz - 4; z <= cz + 4; z++) if ((x + z) % 2 == 0) set(x, 1, z, ACCENT);
            }
        }
    }

    private void light(int x0, int z0) {
        int c = params.cell();
        // Every fourth block each way, so even a four-wide walkway round a ring gets its share.
        for (int x = x0 + 2; x < x0 + c - 1; x += 4)
            for (int z = z0 + 2; z < z0 + c - 1; z += 4)
                for (int y = HEIGHT - 2; y > STAND + 1; y--)
                    if (role(x, y, z) == AIR) { set(x, y, z, LIGHT); break; }
    }

    private boolean standable(int x, int y, int z) {
        byte below = role(x, y - 1, z);
        return open(x, y, z) && open(x, y + 1, z) && below != AIR && below != LIGHT;
    }

    private int index2(int x, int y, int z) { return ((y - STAND) * sizeZ + z) * sizeX + x; }

    /** Standing cells reachable on foot from the entrance (steps of one block up or down are allowed). */
    private boolean[] flood() {
        boolean[] reach = new boolean[2 * sizeX * sizeZ];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int[] e = entrance;
        if (!standable(e[0], e[1], e[2])) return reach;
        reach[index2(e[0], e[1], e[2])] = true;
        queue.add(e);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] s : steps) {
                int x = at[0] + s[0], z = at[2] + s[1];
                for (int y = STAND; y <= STAND + 1; y++) {
                    if (Math.abs(y - at[1]) > 1 || !standable(x, y, z) || reach[index2(x, y, z)]) continue;
                    reach[index2(x, y, z)] = true;
                    queue.add(new int[] {x, y, z});
                }
            }
        }
        return reach;
    }

    private boolean reachableInCell(boolean[] reach, int gx, int gz) {
        int c = params.cell();
        for (int x = gx * c + 2; x < (gx + 1) * c - 2; x++)
            for (int z = gz * c + 2; z < (gz + 1) * c - 2; z++)
                for (int y = STAND; y <= STAND + 1; y++) if (reach[index2(x, y, z)]) return true;
        return false;
    }

    /** For tests: how many standing cells there are, and how many of them can be reached from the entrance. */
    public int[] walkability() {
        boolean[] reach = flood();
        int standing = 0, reached = 0;
        for (int x = 0; x < sizeX; x++)
            for (int z = 0; z < sizeZ; z++)
                for (int y = STAND; y <= STAND + 1; y++)
                    if (standable(x, y, z)) { standing++; if (reach[index2(x, y, z)]) reached++; }
        return new int[] {standing, reached};
    }
}
