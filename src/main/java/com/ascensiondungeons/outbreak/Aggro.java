package com.ascensiondungeons.outbreak;

import java.util.List;

/**
 * How a dungeon enemy picks a fight. Pure: distances in, a decision out, so every style is unit tested and the Minecraft layer
 * only carries the decision out. Distances are squared, as the game gives them.
 */
public final class Aggro {
    public enum Style {
        /** Chases the nearest target it can see. */
        HUNTER,
        /** Holds its post; fights whatever comes near the post. */
        SENTRY,
        /** Still until someone passes close or hits it, then hunts. */
        AMBUSHER,
        /** Fights from range and keeps its distance. */
        SKIRMISHER,
        /** Ignores a distant target until it or a packmate is provoked. */
        PACK,
        /** Guards one spot and never leaves it. */
        GUARDIAN;

        public static Style parse(String name, Style fallback) {
            if (name == null) return fallback;
            try { return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException e) { return fallback; }
        }
    }

    /** Ranges in blocks. */
    public record Ranges(double sight, double postRadius, double guardRadius, double wakeRadius, double skirmishRange) {
        public static final Ranges DEFAULT = new Ranges(26, 10, 6, 5, 8);
    }

    /** A possible target: how far it is from the enemy and from the enemy's anchor (its post). */
    public record Candidate(double distSq, double anchorDistSq) {}

    /**
     * @param target         index into the candidates, or -1 for none
     * @param returnHome     walk back to the anchor
     * @param preferredRange stay about this far from the target (0 means close in)
     * @param alerted        the enemy is now provoked (remember it; a PACK enemy also wakes its packmates)
     */
    public record Decision(int target, boolean returnHome, double preferredRange, boolean alerted) {}

    private Aggro() {}

    public static Decision decide(Style style, Ranges r, boolean alerted, double selfAnchorDistSq, List<Candidate> candidates) {
        double sight = r.sight() * r.sight();
        switch (style) {
            case SENTRY: {
                int t = nearest(candidates, sight, r.postRadius() * r.postRadius());
                return new Decision(t, t < 0 && selfAnchorDistSq > 4, 0, alerted);
            }
            case GUARDIAN: {
                double guard = r.guardRadius() * r.guardRadius();
                // Never chases past its ground: outside it, it goes home even with a target in sight.
                if (selfAnchorDistSq > guard) return new Decision(-1, true, 0, alerted);
                int t = nearest(candidates, sight, guard);
                return new Decision(t, t < 0 && selfAnchorDistSq > 4, 0, alerted);
            }
            case AMBUSHER: {
                boolean awake = alerted || nearest(candidates, r.wakeRadius() * r.wakeRadius(), Double.MAX_VALUE) >= 0;
                return new Decision(awake ? nearest(candidates, sight, Double.MAX_VALUE) : -1, false, 0, awake);
            }
            case PACK: {
                boolean awake = alerted || nearest(candidates, r.guardRadius() * r.guardRadius(), Double.MAX_VALUE) >= 0;
                return new Decision(awake ? nearest(candidates, sight, Double.MAX_VALUE) : -1, false, 0, awake);
            }
            case SKIRMISHER:
                return new Decision(nearest(candidates, sight, Double.MAX_VALUE), false, r.skirmishRange(), alerted);
            case HUNTER:
            default:
                return new Decision(nearest(candidates, sight, Double.MAX_VALUE), false, 0, alerted);
        }
    }

    /** The closest candidate within both limits, or -1. */
    private static int nearest(List<Candidate> candidates, double maxDistSq, double maxAnchorDistSq) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (c.distSq() <= maxDistSq && c.anchorDistSq() <= maxAnchorDistSq && c.distSq() < bestD) { bestD = c.distSq(); best = i; }
        }
        return best;
    }
}
