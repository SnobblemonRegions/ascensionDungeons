package com.ascensiondungeons.gen.depths;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How the Depths are joined to a hand-built dungeon: a tunnel cut out through a wall of the entrance room, a stairway that
 * descends away from the dungeon, and the maze at the bottom, so players and enemies walk between the two. Pure: it reads the
 * dungeon as a grid of block-state names and produces block roles to place.
 *
 * <p>{@link #find} looks for a place where the tunnel can go straight out without touching anything it should not: it never
 * breaks into another room or passage, never cuts a mechanism, and never runs beside water or lava. If there is no such place
 * the Depths stay {@linkplain #detached detached} and are reached by a teleporting stairway instead.
 *
 * <p>All coordinates are the dungeon's schematic coordinates; the Depths' own (local) coordinates are converted with
 * {@link #toSchem} and {@link #toLocal}. The maze's doorway (local {@code z = 0}) faces back toward the dungeon.
 */
public final class DepthsLink {
    public enum Side {
        NORTH(0, -1), SOUTH(0, 1), WEST(-1, 0), EAST(1, 0);
        final int dx, dz;
        Side(int dx, int dz) { this.dx = dx; this.dz = dz; }
    }

    /** The hand-built dungeon as block-state names. {@code state} is only asked about cells inside the bounds. */
    public interface Grid {
        int width();
        int height();
        int length();
        String state(int x, int y, int z);
    }

    /**
     * A role only the stairway uses, beside {@link Depths}' own: a half-height step (a slab) lying in an otherwise open cell, so
     * each step of the stairway is half a block and can be walked without jumping. Where the dungeon has no slab to build it
     * from it is left open, which makes a full-block step.
     */
    public static final byte STEP = 6;

    /** True for a role a creature can occupy: open air, a light, or a half step. */
    public static boolean open(byte role) { return role == Depths.AIR || role == Depths.LIGHT || role == STEP; }

    /** Blocks of stairway between the dungeon's outer face and the maze. */
    public static final int GAP = 28;
    /** How far the stairway descends. */
    public static final int DROP = 8;
    /** The longest tunnel that may be cut inside the dungeon. */
    public static final int MAX_TUNNEL = 24;
    /** How many blocks of tunnel may run beside the entrance room's own air before the wall must be solid. */
    public static final int OPENING = 6;
    /** When there is no physical link the Depths sit this far beyond the dungeon's far face. */
    public static final int DETACHED_GAP = 48;

    private final boolean physical;
    private final Side side;
    /** The standing cell in the entrance room where the tunnel starts (null when detached). */
    private final int[] mouth;
    private final int tunnel;
    /** The first cell on the centre line outside the dungeon's bounds, at standing level. */
    private final int ex, ey, ez;
    private final int yBase, doorX, depthsX, depthsZ;
    private final Map<Long, Byte> overrides;

    private DepthsLink(boolean physical, Side side, int[] mouth, int tunnel, int ex, int ey, int ez, int yBase, int doorX, int depthsX, int depthsZ, Map<Long, Byte> overrides) {
        this.physical = physical;
        this.side = side;
        this.mouth = mouth;
        this.tunnel = tunnel;
        this.ex = ex;
        this.ey = ey;
        this.ez = ez;
        this.yBase = yBase;
        this.doorX = doorX;
        this.depthsX = depthsX;
        this.depthsZ = depthsZ;
        this.overrides = overrides;
    }

    /** True when the Depths are joined to the dungeon by a tunnel; false when they are detached and need a teleporting stairway. */
    public boolean physical() { return physical; }
    public Side side() { return side; }
    public int[] mouth() { return mouth == null ? null : mouth.clone(); }
    /** Blocks of tunnel cut inside the dungeon. */
    public int tunnelLength() { return tunnel; }

    // ---- where the maze is ---------------------------------------------------------------------------------------

    private int px() { return side.dz != 0 ? 1 : 0; }
    private int pz() { return side.dz != 0 ? 0 : 1; }

    /** A cell of the maze (local) in the dungeon's coordinates. */
    public int[] toSchem(int lx, int ly, int lz) {
        int across = lx - doorX, along = GAP + lz;
        return new int[] {ex + side.dx * along + px() * across, yBase + ly, ez + side.dz * along + pz() * across};
    }

    /** A dungeon cell in the maze's local coordinates (which may lie outside the maze: the caller checks the range). */
    public int[] toLocal(int x, int y, int z) {
        int rx = x - ex, rz = z - ez;
        int along = rx * side.dx + rz * side.dz, across = rx * px() + rz * pz();
        return new int[] {across + doorX, y - yBase, along - GAP};
    }

    /** The role the link puts at a cell (the tunnel and the stairway), or null where it changes nothing. */
    public Byte override(int x, int y, int z) { return overrides.get(key(x, y, z)); }

    public Map<Long, Byte> overrides() { return overrides; }

    public static long key(int x, int y, int z) { return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096); }
    public static int[] unkey(long k) { return new int[] {(int) (k >> 40) - 4096, (int) ((k >> 20) & 0xFFFFF) - 4096, (int) (k & 0xFFFFF) - 4096}; }

    /** The smallest and largest x and z the stairway and the maze reach, as {minX, minZ, maxX, maxZ}. */
    public int[] reach() {
        int[] a = toSchem(0, 0, 0), b = toSchem(depthsX - 1, 0, depthsZ - 1);
        int minX = Math.min(a[0], b[0]), maxX = Math.max(a[0], b[0]), minZ = Math.min(a[2], b[2]), maxZ = Math.max(a[2], b[2]);
        for (long k : overrides.keySet()) {
            int[] c = unkey(k);
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]); minZ = Math.min(minZ, c[2]); maxZ = Math.max(maxZ, c[2]);
        }
        return new int[] {minX, minZ, maxX, maxZ};
    }

    // ---- building one -------------------------------------------------------------------------------------------

    /** The Depths placed beyond the dungeon's far (south) face with nothing joining them. */
    public static DepthsLink detached(Grid g, int depthsX, int depthsZ, int doorX) {
        return new DepthsLink(false, Side.SOUTH, null, 0, doorX, Depths.STAND + 2, g.length() + DETACHED_GAP - GAP, 2, doorX, depthsX, depthsZ, Map.of());
    }

    /**
     * Finds the best place to join the Depths to the entrance room, or returns a {@linkplain #detached detached} link.
     *
     * @param starts   standing cells of the entrance room to try tunnelling from
     * @param reserved cells that must not be cut (mechanisms, hazards, seals, chests)
     * @param avoid    a cell the tunnel mouth should keep away from (where the party arrives), or null
     */
    public static DepthsLink find(Grid g, List<int[]> starts, Predicate<int[]> reserved, int[] avoid, int depthsX, int depthsZ, int doorX) {
        return find(g, starts, reserved, c -> false, avoid, depthsX, depthsZ, doorX);
    }

    /**
     * As {@link #find(Grid, List, Predicate, int[], int, int, int)}, for a room whose walls are not flat (a dome, a cave).
     *
     * @param home cells of the entrance room itself. Where its wall bulges or slopes, the first few blocks of tunnel may run
     *             beside the room's own air; once the tunnel is in solid wall it may not meet any air again
     */
    public static DepthsLink find(Grid g, List<int[]> starts, Predicate<int[]> reserved, Predicate<int[]> home, int[] avoid, int depthsX, int depthsZ, int doorX) {
        boolean[] exterior = exterior(g);
        int[] bestMouth = null;
        Side bestSide = null;
        int bestLen = 0, bestScore = Integer.MAX_VALUE;
        for (int[] start : starts) {
            for (Side side : Side.values()) {
                int[] mouth = walkToWall(g, start, side);
                if (mouth == null) continue;
                int len = tunnelLength(g, exterior, reserved, home, mouth, side);
                if (len < 0) continue;
                int score = len;
                if (avoid != null) {
                    int dx = mouth[0] - avoid[0], dz = mouth[2] - avoid[2];
                    if (dx * dx + dz * dz < 36) score += 100;     // not right where the party lands
                    score += 4 * Math.abs(mouth[1] - avoid[1]);   // ... but on the floor they land on, not up a ledge
                }
                if (score < bestScore) { bestScore = score; bestMouth = mouth; bestSide = side; bestLen = len; }
            }
        }
        if (bestMouth == null) return detached(g, depthsX, depthsZ, doorX);
        int ex = bestMouth[0] + bestSide.dx * (bestLen + 1), ez = bestMouth[2] + bestSide.dz * (bestLen + 1), ey = bestMouth[1];
        DepthsLink link = new DepthsLink(true, bestSide, bestMouth, bestLen, ex, ey, ez, ey - DROP - Depths.STAND, doorX, depthsX, depthsZ, new HashMap<>());
        link.build(g, exterior);
        return link;
    }

    private static boolean in(Grid g, int x, int y, int z) { return x >= 0 && y >= 0 && z >= 0 && x < g.width() && y < g.height() && z < g.length(); }

    static boolean air(String state) {
        return state.equals("minecraft:air") || state.equals("minecraft:cave_air") || state.equals("minecraft:void_air") || state.startsWith("minecraft:light[") || state.equals("minecraft:light");
    }

    static boolean wet(String s) { return s.contains("water") || s.contains("lava") || s.contains("kelp") || s.contains("seagrass") || s.contains("bubble_column"); }

    static boolean falls(String s) {
        String n = s.substring(s.indexOf(':') + 1);
        return (n.contains("sand") && !n.contains("sandstone")) || n.contains("gravel") || n.contains("concrete_powder") || n.contains("anvil");
    }

    static boolean mechanism(String s) {
        for (String bad : new String[] {"chest", "barrel", "lever", "button", "pressure_plate", "door", "sign", "redstone", "piston", "dispenser", "dropper", "hopper",
                "spawner", "command", "structure", "bell", "shulker", "furnace", "lectern", "note_block", "tripwire", "target", "observer", "comparator", "repeater",
                "rail", "tnt", "banner", "skull", "candle", "campfire", "cauldron", "beacon", "portal"})
            if (s.contains(bad)) return true;
        return false;
    }

    /** Air that is open to the outside of the dungeon's bounds (the void around it), as opposed to air inside its rooms. */
    static boolean[] exterior(Grid g) {
        int w = g.width(), h = g.height(), l = g.length();
        boolean[] out = new boolean[w * h * l];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) for (int z = 0; z < l; z++) {
            boolean face = x == 0 || y == 0 || z == 0 || x == w - 1 || y == h - 1 || z == l - 1;
            if (face && air(g.state(x, y, z))) { out[(y * l + z) * w + x] = true; queue.add(new int[] {x, y, z}); }
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] s : steps) {
                int x = at[0] + s[0], y = at[1] + s[1], z = at[2] + s[2];
                if (!in(g, x, y, z) || out[(y * l + z) * w + x] || !air(g.state(x, y, z))) continue;
                out[(y * l + z) * w + x] = true;
                queue.add(new int[] {x, y, z});
            }
        }
        return out;
    }

    private static boolean standable(Grid g, int x, int y, int z) {
        if (!in(g, x, y, z) || !in(g, x, y + 1, z) || !in(g, x, y - 1, z)) return false;
        String below = g.state(x, y - 1, z);
        return air(g.state(x, y, z)) && air(g.state(x, y + 1, z)) && !air(below) && !wet(below);
    }

    /** Walks from a standing cell straight toward a wall; the last cell before the wall is the tunnel's mouth. Null if the walk is not clear. */
    static int[] walkToWall(Grid g, int[] start, Side side) {
        if (!standable(g, start[0], start[1], start[2])) return null;
        int x = start[0], z = start[2], y = start[1];
        for (int i = 0; i < 64; i++) {
            int nx = x + side.dx, nz = z + side.dz;
            if (!in(g, nx, y, nz)) return null;                       // the room is open to the edge: nothing to cut
            if (!standable(g, nx, y, nz)) return new int[] {x, y, z};
            x = nx;
            z = nz;
        }
        return null;
    }

    /**
     * How many blocks of tunnel it takes to get from the mouth out of the dungeon's bounds, or a negative reason if a tunnel there would touch
     * something it must not. The tunnel is three wide and three high; the ring of blocks round it must stay closed.
     */
    static int tunnelLength(Grid g, boolean[] exterior, Predicate<int[]> reserved, Predicate<int[]> home, int[] mouth, Side side) {
        int w = g.width(), l = g.length(), y = mouth[1];
        int px = side.dz != 0 ? 1 : 0, pz = side.dz != 0 ? 0 : 1;
        int solidSections = 0;
        for (int t = 1; t <= MAX_TUNNEL + 1; t++) {
            int cx = mouth[0] + side.dx * t, cz = mouth[2] + side.dz * t;
            if (!in(g, cx, y, cz)) return solidSections >= 1 ? t - 1 : -1;
            boolean allSolid = true;
            for (int a = -2; a <= 2; a++) {
                for (int dy = -1; dy <= 3; dy++) {
                    int x = cx + px * a, z = cz + pz * a, yy = y + dy;
                    boolean carve = Math.abs(a) <= 1 && dy >= 0 && dy <= 2;
                    if (!in(g, x, yy, z)) { if (carve) allSolid = false; continue; }      // beyond the bounds: the stairway builds its own shell
                    String s = g.state(x, yy, z);
                    boolean outside = exterior[(yy * l + z) * w + x];
                    if (wet(s) || falls(s)) return -2;                                   // never beside water, lava or anything that falls
                    if (air(s)) {
                        // Air of the entrance room itself is fine while the tunnel is still leaving it (a sloping or bulging wall).
                        boolean leaving = solidSections == 0 && t <= OPENING && home.test(new int[] {x, yy, z});
                        if (!outside && !leaving) return -3;                             // that is another room or passage
                        if (carve) allSolid = false;
                    } else if (carve) {
                        if (mechanism(s) || reserved.test(new int[] {x, yy, z})) return -4;
                    }
                }
            }
            if (allSolid) solidSections++;
        }
        return -5;                                                                        // too far to the edge
    }

    /**
     * The standing cell on the centre line of the way down, {@code t} blocks beyond the dungeon's bounds: negative inside the
     * tunnel, 0 to {@link #GAP}-1 on the stairway. Null when the Depths are detached.
     */
    public int[] stair(int t) {
        if (!physical) return null;
        return new int[] {ex + side.dx * t, t < 0 ? ey : standLevel(t), ez + side.dz * t};
    }

    private void put(int x, int y, int z, byte role) { overrides.put(key(x, y, z), role); }

    /**
     * The stand level of the stairway {@code t} blocks beyond the dungeon's bounds: level for two blocks, then one block down
     * every two. The first cell of each lower tread holds a {@link #STEP}, so the walk is really half a block at a time.
     */
    public int standLevel(int t) { return ey - Math.max(0, Math.min(DROP, t / 2)); }

    private void build(Grid g, boolean[] exterior) {
        int w = g.width(), l = g.length();
        int px = px(), pz = pz();
        // Shells first, then air, so a step's wall never fills its neighbour's headroom.
        for (int pass = 0; pass < 2; pass++) {
            for (int t = -tunnel; t < GAP; t++) {
                int cx = ex + side.dx * t, cz = ez + side.dz * t;
                int stand = t < 0 ? ey : standLevel(t), height = t < 0 ? 3 : 4;
                for (int a = -2; a <= 2; a++) {
                    for (int dy = -1; dy <= height; dy++) {
                        int x = cx + px * a, z = cz + pz * a, y = stand + dy;
                        boolean carve = Math.abs(a) <= 1 && dy >= 0 && dy < height;
                        boolean inside = in(g, x, y, z);
                        if (pass == 0 && !carve) {
                            // Inside the dungeon its own blocks are the shell; only fill where there is open void.
                            if (!inside || exterior[(y * l + z) * w + x]) put(x, y, z, dy == -1 ? Depths.FLOOR : Depths.WALL);
                            // ... and a floor under the tunnel where the room's own floor falls away beneath it.
                            else if (dy == -1 && Math.abs(a) <= 1 && air(g.state(x, y, z))) put(x, y, z, Depths.FLOOR);
                        } else if (pass == 1 && carve) {
                            put(x, y, z, a == 0 && dy == height - 1 && Math.floorMod(t, 4) == 0 ? Depths.LIGHT : Depths.AIR);
                        }
                    }
                }
            }
        }
        // Half steps: where the stairway drops a block, the first cell of the lower tread holds a slab.
        for (int t = 2; t < GAP; t++) {
            if (standLevel(t) == standLevel(t - 1)) continue;
            for (int a = -1; a <= 1; a++) put(ex + side.dx * t + px * a, standLevel(t), ez + side.dz * t + pz * a, STEP);
        }
        // A frame of accent blocks where the tunnel leaves the room, so the way down is easy to spot.
        for (int a = -2; a <= 2; a++) for (int dy = -1; dy <= 3; dy++) {
            boolean frame = Math.abs(a) == 2 || dy == 3 || dy == -1;
            if (!frame) continue;
            int x = mouth[0] + side.dx + px * a, z = mouth[2] + side.dz + pz * a, y = mouth[1] + dy;
            if (in(g, x, y, z) && !air(g.state(x, y, z))) put(x, y, z, dy == -1 ? Depths.ACCENT : Depths.PILLAR);
        }
    }
}
