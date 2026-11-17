package com.ascensiondungeons.outbreak;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How dungeon enemies fight together, and how hard: spacing for ranged fighters, who to go for, how a wave shares the work,
 * when to fall back, and how a boss changes as it is worn down. Pure: positions and numbers in, decisions out.
 *
 * <p>Every knob comes from a {@link Profile}, chosen by the dive's {@link Difficulty} and sharpened by how deep the fight is and
 * how far the Outbreak has risen. A difficulty screen only has to pick the difficulty; nothing else changes.
 */
public final class Tactics {
    private Tactics() {}

    /** How hard a dive fights. Normal is what a dive is tuned for. */
    public enum Difficulty {
        /** Few enemies close in at once, nobody flanks or retreats, bosses only call help. For learning and for relaxed play. */
        CASUAL,
        NORMAL,
        /** Enemies flank, focus the hurt, fall back to their packmates, and bosses charge more often. */
        HARD,
        /** Everything Hard does, with more pressure and shorter warnings (never shorter than a second). */
        NIGHTMARE;

        public static Difficulty parse(String name, Difficulty fallback) {
            if (name == null) return fallback;
            try { return valueOf(name.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return fallback; }
        }

        /** "Casual", "Normal", ... */
        public String label() { return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT); }
    }

    /**
     * The knobs.
     *
     * @param maxPressing   how many enemies of a wave close in on one target at once; the rest hold a ring and wait their turn
     * @param flank         whether those closing in come from different sides
     * @param kiteRange     how far a ranged fighter keeps from its target
     * @param threatFocus   how much an enemy prefers whoever has been hurting it (0 = not at all)
     * @param hurtFocus     how much an enemy prefers a target that is already hurt (0 = not at all)
     * @param retreatChance the chance a badly hurt ordinary enemy falls back once, to its packmates
     * @param retreatBelow  the share of health under which it may fall back
     * @param summons       how many enemies a boss calls when it first falls below two thirds of its health (0 = none)
     * @param charges       whether a boss charges: a line on the ground, then a rush along it
     * @param chargeEvery   ticks between a boss's charges once it charges
     * @param chargeWarning ticks the line shows before the rush
     * @param enrage        how much faster a boss's spells come once it falls below a third of its health (1 = no faster)
     * @param lockTicks     how long an enemy keeps the target it chose before it looks again
     */
    public record Profile(int maxPressing, boolean flank, double kiteRange, double threatFocus, double hurtFocus, double retreatChance, double retreatBelow,
                          int summons, boolean charges, int chargeEvery, int chargeWarning, double enrage, int lockTicks) {
        public Profile {
            if (maxPressing < 1) throw new IllegalArgumentException("at least one enemy may press");
            if (chargeWarning < 20) throw new IllegalArgumentException("a charge is always shown for at least a second");
        }
    }

    /** The profile for a difficulty, in a band of the Depths (0 outer, 2 deep; the dungeon above counts as outer) at a stage. */
    public static Profile profile(Difficulty d, int band, int stage) {
        int b = Math.max(0, Math.min(2, band)), s = Math.max(0, stage);
        return switch (d) {
            case CASUAL -> new Profile(2, false, 6, 0.3, 0, 0, 0.3, 1, false, 400, 40, 1.0, 80);
            case NORMAL -> new Profile(2 + (b >= 2 || s >= 4 ? 1 : 0), b >= 1 || s >= 2, 7, 0.8, 0.3 * b, 0.15 + 0.05 * b, 0.3, 2 + b, true,
                    Math.max(160, 240 - 20 * s), 30, 1.4, 60);
            case HARD -> new Profile(3, true, 7.5, 1.0, 0.5 + 0.2 * b, 0.35, 0.35, 3 + b, true, Math.max(120, 180 - 15 * s), 26, 1.7, 50);
            case NIGHTMARE -> new Profile(4, true, 8, 1.2, 1.0, 0.5, 0.4, 4 + b, true, Math.max(90, 140 - 10 * s), 22, 2.0, 40);
        };
    }

    // ---- spacing ---------------------------------------------------------------------------------------------------

    /** What a fighter that wants distance should do. */
    public enum Spacing { BACK_OFF, CLOSE_IN, STRAFE }

    /** Within two blocks of the wanted range it strafes; nearer, it backs off; further, it closes in. */
    public static Spacing spacing(double distance, double range) {
        if (distance < range - 2) return Spacing.BACK_OFF;
        if (distance > range + 2) return Spacing.CLOSE_IN;
        return Spacing.STRAFE;
    }

    /**
     * Where a fighter that wants to be {@code range} from its target should head, as {x, z}. Backing off goes straight away from
     * the target; closing in stops on the ring; strafing steps around the ring ({@code clockwise} decides which way).
     */
    public static double[] spacingPoint(double selfX, double selfZ, double targetX, double targetZ, double range, boolean clockwise) {
        double dx = selfX - targetX, dz = selfZ - targetZ, d = Math.hypot(dx, dz);
        if (d < 1e-6) { dx = 1; dz = 0; d = 1; }
        double ux = dx / d, uz = dz / d;
        return switch (spacing(d, range)) {
            case BACK_OFF -> new double[] {selfX + ux * 4, selfZ + uz * 4};
            case CLOSE_IN -> new double[] {targetX + ux * range, targetZ + uz * range};
            case STRAFE -> {
                double step = 0.45 * (clockwise ? 1 : -1);              // about a quarter of the way round in four steps at range 7
                double a = Math.atan2(uz, ux) + step;
                yield new double[] {targetX + Math.cos(a) * range, targetZ + Math.sin(a) * range};
            }
        };
    }

    // ---- a wave working together -------------------------------------------------------------------------------

    /** The angle a member of a wave comes from: the wave's members spread evenly round the target, starting at {@code base}. */
    public static double slotAngle(int slot, int slots, double base) { return base + 2 * Math.PI * slot / Math.max(1, slots); }

    /** A point {@code radius} from the target at an angle, as {x, z}. */
    public static double[] ringPoint(double targetX, double targetZ, double angle, double radius) {
        return new double[] {targetX + Math.cos(angle) * radius, targetZ + Math.sin(angle) * radius};
    }

    /** Whether a member holding back may step in now: only while fewer than the profile allows are pressing that target. */
    public static boolean mayPress(boolean pressing, int pressingNow, Profile p) { return pressing || pressingNow < p.maxPressing(); }

    /** How far out a member waiting its turn holds. */
    public static final double HOLD_RADIUS = 6.0;

    // ---- choosing a target -------------------------------------------------------------------------------------

    /**
     * A possible target, as the enemy sees it.
     *
     * @param distSq  squared distance to it
     * @param health  its share of health left, 0..1
     * @param threat  how much it has been hurting this enemy lately (see {@link Threat})
     */
    public record Candidate(double distSq, double health, double threat) {}

    /** How attractive a target is: near is good, and with a sharper profile, the one hurting it and the one already hurt. */
    public static double score(Candidate c, Profile p) {
        double near = -Math.sqrt(c.distSq());
        return near + p.threatFocus() * Math.min(20, c.threat()) + p.hurtFocus() * 10 * (1 - Math.max(0, Math.min(1, c.health())));
    }

    /**
     * Which target to take: the best score, but a target already locked is kept while the lock lasts unless another beats it by a
     * clear margin, so enemies do not flicker between players. Returns an index, or -1 for none.
     */
    public static int pick(List<Candidate> candidates, int locked, boolean lockFresh, Profile p) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < candidates.size(); i++) {
            double s = score(candidates.get(i), p);
            if (s > bestScore) { bestScore = s; best = i; }
        }
        if (locked >= 0 && locked < candidates.size() && lockFresh && score(candidates.get(locked), p) > bestScore - 4) return locked;
        return best;
    }

    /** How much each attacker has hurt one enemy lately; it fades by half every {@link #HALF_LIFE} ticks. */
    public static final class Threat {
        public static final long HALF_LIFE = 100;
        private final Map<Object, double[]> marks = new HashMap<>();   // attacker -> {amount, tick}

        public void hurt(Object attacker, double amount, long now) {
            double[] m = marks.get(attacker);
            double kept = m == null ? 0 : m[0] * Math.pow(0.5, (now - m[1]) / (double) HALF_LIFE);
            marks.put(attacker, new double[] {kept + amount, now});
        }

        public double of(Object attacker, long now) {
            double[] m = marks.get(attacker);
            return m == null ? 0 : m[0] * Math.pow(0.5, (now - m[1]) / (double) HALF_LIFE);
        }
    }

    // ---- falling back --------------------------------------------------------------------------------------------

    /**
     * Whether an ordinary enemy falls back now: it has just dropped below the profile's line, it has not fallen back before, it
     * is not an elite or a boss, and the roll (0..1) came up.
     */
    public static boolean retreat(double healthBefore, double healthNow, boolean already, boolean special, double roll, Profile p) {
        if (already || special || p.retreatChance() <= 0) return false;
        return healthBefore >= p.retreatBelow() && healthNow < p.retreatBelow() && roll < p.retreatChance();
    }

    /** How long a fall-back lasts before the enemy comes again. */
    public static final int RETREAT_TICKS = 80;

    // ---- bosses --------------------------------------------------------------------------------------------------

    /** A boss's phases: it calls help below two thirds of its health and is enraged below a third. Phases only go forward. */
    public static final class BossPhases {
        public static final double SUMMON = 2.0 / 3, ENRAGE = 1.0 / 3;
        private int phase;

        /** 0 untouched, 1 has called help, 2 enraged. */
        public int phase() { return phase; }

        /** The phase entered now (1 or 2), or 0 when nothing changed. A big hit can skip straight to 2; help is still called. */
        public int update(double health) {
            int want = health < ENRAGE ? 2 : health < SUMMON ? 1 : 0;
            if (want <= phase) return 0;
            int from = phase;
            phase = want;
            return from == 0 && want == 2 ? 3 : want;              // 3: both at once
        }
    }

    /** Whether a point is within {@code width} of the line from a to b (all on the ground plane): the charge's path. */
    public static boolean onLine(double px, double pz, double ax, double az, double bx, double bz, double width) {
        double vx = bx - ax, vz = bz - az, len2 = vx * vx + vz * vz;
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / len2));
        double cx = ax + t * vx, cz = az + t * vz;
        return Math.hypot(px - cx, pz - cz) <= width;
    }

    /** How far a charge runs: past the target, so stepping back is not enough; step aside. */
    public static final double CHARGE_LENGTH = 12;
}
