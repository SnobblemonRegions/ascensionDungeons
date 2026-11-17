package com.ascensiondungeons.gen.depths;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Test aid: can a big creature get through? A body three blocks square (so up to 2.75 wide: every Pokémon in the dungeons'
 * pools) and {@code height} blocks tall, moved a block at a time. Like a real wide creature it rests on the highest ground
 * under it, so on a stairway it still stands on the tread behind while its front hangs over the one below.
 */
public final class BigBody {
    public interface Space { boolean open(int x, int y, int z); }

    private BigBody() {}

    /** The level the body rests at when centred on this column near {@code near}, or MIN_VALUE if it does not fit there. */
    static int rest(Space s, int x, int near, int z, int height) {
        int level = Integer.MIN_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int ground = Integer.MIN_VALUE;
            for (int y = near + 1; y >= near - 3; y--) if (s.open(x + dx, y, z + dz) && !s.open(x + dx, y - 1, z + dz)) { ground = y; break; }
            if (ground == Integer.MIN_VALUE) {
                // No ground in reach under this part of the body: fine if it is open air there (an edge), not if it is wall.
                if (!s.open(x + dx, near, z + dz)) return Integer.MIN_VALUE;
                continue;
            }
            level = Math.max(level, ground);
        }
        if (level == Integer.MIN_VALUE || s.open(x, level - 1, z) && !groundUnder(s, x, level, z)) return Integer.MIN_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            for (int y = level; y < level + height; y++) if (!s.open(x + dx, y, z + dz)) return Integer.MIN_VALUE;
        return level;
    }

    private static boolean groundUnder(Space s, int x, int level, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (!s.open(x + dx, level - 1, z + dz)) return true;
        return false;
    }

    /** Steps of the body's shortest walk from a standing cell to any cell the goal accepts, or -1. */
    public static int walk(Space s, int[] from, Predicate<int[]> goal, int height) {
        int start = rest(s, from[0], from[1], from[2], height);
        if (start == Integer.MIN_VALUE) return -1;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {from[0], start, from[2], 0});
        seen.add(DepthsLink.key(from[0], start, from[2]));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            if (goal.test(at)) return at[3];
            for (int[] step : steps) {
                int x = at[0] + step[0], z = at[2] + step[1];
                int y = rest(s, x, at[1], z, height);
                if (y == Integer.MIN_VALUE || y > at[1] + 1) continue;          // climbs one block at most
                if (seen.add(DepthsLink.key(x, y, z))) queue.add(new int[] {x, y, z, at[3] + 1});
            }
        }
        return -1;
    }
}
