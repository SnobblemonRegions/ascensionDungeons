package com.ascensiondungeons.outbreak;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The set pieces of a run's Depths, chosen from its seed: Outbreak Nests that must be cleared, Alphas that guard a hoard, and
 * hazard rooms where the dungeon's own spells pulse. Each is in a room of its own, and the rewards wait in a sealed chest.
 * Pure: it works on room ids, bands and standing cells, so the same seed always gives the same Depths.
 */
public final class SetPieces {
    public enum Kind {
        /** Wakes when a player comes in, sends waves, and opens its sealed chest when cleared. */
        NEST,
        /** A champion that guards a sealed hoard until it falls. */
        ALPHA,
        /** The room itself casts the dungeon's spells on a timer; it holds a lucky chest. */
        HAZARD
    }

    /** A Depths room on offer: its id, band, kind of shape, and the cells where something can stand. */
    public record Room(String id, int band, boolean vault, List<int[]> cells) {}

    /**
     * One set piece.
     *
     * @param centre where its heart is: the nest's core, the alpha's post, the hazard's middle
     * @param reward where its sealed chest stands (the hazard room's chest is not sealed)
     */
    public record Piece(Kind kind, String room, int band, int[] centre, int[] reward) {}

    public record Counts(int nests, int alphas, int hazards) {
        public Counts {
            if (nests < 0 || alphas < 0 || hazards < 0) throw new IllegalArgumentException("counts must not be negative");
        }
    }

    /** A reward chest keeps this far from the piece's heart, so the fight happens before the prize. */
    public static final int REWARD_DISTANCE = 5;

    private SetPieces() {}

    /**
     * @param taken cells already used by other features; a reward chest keeps {@link FeaturePlanner#SPACING} from them
     */
    public static List<Piece> plan(List<Room> rooms, Counts counts, long seed, List<int[]> taken) {
        Random rng = new Random(seed ^ 0x5345545049454345L);
        List<Piece> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        List<int[]> blocked = new ArrayList<>(taken);
        // Alphas want the deepest rooms, vaults first; nests the middle and deep; hazards anywhere past the outer band.
        place(out, used, blocked, rooms, Kind.ALPHA, counts.alphas(), rng, 2, true);
        place(out, used, blocked, rooms, Kind.NEST, counts.nests(), rng, 1, false);
        place(out, used, blocked, rooms, Kind.HAZARD, counts.hazards(), rng, 1, false);
        return out;
    }

    private static void place(List<Piece> out, Set<String> used, List<int[]> blocked, List<Room> rooms, Kind kind, int count, Random rng, int minBand, boolean vaultsFirst) {
        for (int i = 0; i < count; i++) {
            List<Room> pool = new ArrayList<>();
            for (Room r : rooms) if (!used.contains(r.id()) && r.cells().size() >= 8) pool.add(r);
            if (pool.isEmpty()) return;
            // Prefer the deepest band that has a room free, never shallower than asked unless nothing deeper is left.
            int want = -1;
            for (Room r : pool) if (r.band() >= minBand) want = Math.max(want, r.band());
            List<Room> best = new ArrayList<>();
            for (Room r : pool) if (want < 0 ? r.band() == maxBand(pool) : r.band() == want) best.add(r);
            if (vaultsFirst) {
                List<Room> vaults = new ArrayList<>();
                for (Room r : best) if (r.vault()) vaults.add(r);
                if (!vaults.isEmpty()) best = vaults;
            }
            // Spread pieces of a kind across the band: shuffle, then take the first room that has room for its reward.
            java.util.Collections.shuffle(best, rng);
            boolean placed = false;
            for (Room room : best) {
                int[] centre = middle(room.cells());
                if (near(blocked, centre, 3)) continue;               // the heart keeps clear of anything already there
                int[] reward = null;
                for (int[] c : room.cells()) {
                    double d = distSq(c, centre);
                    if (d < REWARD_DISTANCE * REWARD_DISTANCE || crowded(blocked, c)) continue;
                    if (reward == null || d < distSq(reward, centre)) reward = c;
                }
                if (reward == null) continue;
                out.add(new Piece(kind, room.id(), room.band(), centre, reward));
                used.add(room.id());
                blocked.add(reward);
                blocked.add(centre);
                placed = true;
                break;
            }
            if (!placed) {
                // No room of that band had space: try the others before giving up on this one.
                for (Room r : best) used.add(r.id());
                i--;
                boolean any = false;
                for (Room r : rooms) if (!used.contains(r.id()) && r.cells().size() >= 8) { any = true; break; }
                if (!any) return;
            }
        }
    }

    private static int maxBand(List<Room> rooms) {
        int b = 0;
        for (Room r : rooms) b = Math.max(b, r.band());
        return b;
    }

    /** The standing cell nearest the middle of a room's cells. */
    static int[] middle(List<int[]> cells) {
        double x = 0, y = 0, z = 0;
        for (int[] c : cells) { x += c[0]; y += c[1]; z += c[2]; }
        double[] m = {x / cells.size(), y / cells.size(), z / cells.size()};
        int[] best = cells.get(0);
        double bestD = Double.MAX_VALUE;
        for (int[] c : cells) {
            double dx = c[0] - m[0], dy = c[1] - m[1], dz = c[2] - m[2], d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) { bestD = d; best = c; }
        }
        return best;
    }

    private static boolean near(List<int[]> blocked, int[] c, int distance) {
        for (int[] b : blocked) if (distSq(b, c) < distance * distance) return true;
        return false;
    }

    private static boolean crowded(List<int[]> blocked, int[] c) {
        for (int[] b : blocked) if (distSq(b, c) < FeaturePlanner.SPACING * FeaturePlanner.SPACING) return true;
        return false;
    }

    private static double distSq(int[] a, int[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    // ---- the nest ------------------------------------------------------------------------------------------------

    /**
     * An Outbreak Nest's fight: dormant until a player comes close, then a set number of waves, each sent when the last is
     * down. If every player leaves it for a while it goes back to sleep and must be started again.
     */
    public static final class Nest {
        public enum State { DORMANT, AWAKE, CLEARED }

        /** What the nest wants done this tick. */
        public enum Action { NONE, WAKE, SPAWN_WAVE, CLEARED, SLEEP }

        private final int waves;
        private final long patience;
        private State state = State.DORMANT;
        private int wave;
        private long aloneSince = -1;

        /** @param waves how many waves to clear; @param patience ticks with nobody near before an awake nest sleeps again */
        public Nest(int waves, long patience) {
            if (waves < 1 || patience < 1) throw new IllegalArgumentException("a nest has at least one wave and some patience");
            this.waves = waves;
            this.patience = patience;
        }

        public State state() { return state; }
        /** The wave being fought, from 1; 0 while dormant. */
        public int wave() { return wave; }
        public int waves() { return waves; }

        /**
         * @param near  whether any player is close enough to wake it
         * @param alive how many of its enemies are still standing
         */
        public Action tick(long now, boolean near, int alive) {
            switch (state) {
                case CLEARED -> { return Action.NONE; }
                case DORMANT -> {
                    if (!near) return Action.NONE;
                    state = State.AWAKE;
                    wave = 1;
                    aloneSince = -1;
                    return Action.WAKE;
                }
                default -> {
                    if (!near) {
                        if (aloneSince < 0) aloneSince = now;
                        if (now - aloneSince >= patience) { state = State.DORMANT; wave = 0; aloneSince = -1; return Action.SLEEP; }
                        return Action.NONE;
                    }
                    aloneSince = -1;
                    if (alive > 0) return Action.NONE;
                    if (wave >= waves) { state = State.CLEARED; return Action.CLEARED; }
                    wave++;
                    return Action.SPAWN_WAVE;
                }
            }
        }
    }

    /** How many waves a nest of this band throws: two in the middle band, three deep. */
    public static int nestWaves(int band) { return band >= 2 ? 3 : 2; }

    /** How many enemies a nest wave has: grows with the wave, the band and each extra player. */
    public static int nestWaveSize(int wave, int band, int players) { return 2 + wave + band + Math.max(0, players - 1); }

    /** How much tougher than an ordinary enemy an Alpha is: grows with the band and the Outbreak stage. */
    public static double alphaHealth(int band, int stage) { return 4.0 + 2.0 * band + 0.5 * stage; }

    public static double alphaDamage(int band, int stage) { return 1.3 + 0.15 * band + 0.05 * stage; }

    /** Ticks between a hazard room's pulses: quicker as the Outbreak rises, never quicker than two seconds. */
    public static int hazardPeriod(int baseTicks, int stage) { return Math.max(40, baseTicks - stage * baseTicks / 10); }
}
