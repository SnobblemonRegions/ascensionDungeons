package com.ascensiondungeons.outbreak;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The price of leaving without beating the gatekeeper: a share of the unbanked loot and wins. Pure maths.
 *
 * <p>Loot is counted in items, grouped by kind, so splitting a stack into many small ones changes nothing. Each kind loses its
 * share rounded down; the items still owed after that are taken one each from the kinds with the largest remainders, ties broken
 * by a seeded shuffle. The same seed and the same loot always give the same plan, so what a player is shown is what they lose.
 */
public final class Toll {
    /** How many items each kind loses, in the order the kinds were given, and how many wins. */
    public record Plan(int[] lostPerKind, int itemsLost, int winsLost) {}

    private Toll() {}

    /**
     * @param kindCounts    how many items the player holds of each kind of unbanked loot
     * @param unbankedWins  wins earned and not banked
     * @param percent       the toll, 0..100
     */
    public static Plan plan(int[] kindCounts, int unbankedWins, int percent, long seed) { return plan(kindCounts, unbankedWins, percent, seed, false); }

    /**
     * What a softened knock-out takes: the player keeps {@code keepPercent} of their unbanked loot and wins and loses the rest.
     * Split by kind the same way as the toll, but every rounding goes the player's way: one item is kept, not lost.
     *
     * @param keepPercent the share kept, 0..100
     */
    public static Plan softened(int[] kindCounts, int unbankedWins, int keepPercent, long seed) {
        if (keepPercent < 0 || keepPercent > 100) throw new IllegalArgumentException("percent must be 0..100");
        return plan(kindCounts, unbankedWins, 100 - keepPercent, seed, true);
    }

    /** @param lenient round what is lost down, and take nothing when the share is less than one item or one win */
    private static Plan plan(int[] kindCounts, int unbankedWins, int percent, long seed, boolean lenient) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("percent must be 0..100");
        int total = 0;
        for (int c : kindCounts) {
            if (c < 0) throw new IllegalArgumentException("counts must not be negative");
            total += c;
        }
        int[] lost = new int[kindCounts.length];
        int owed = total == 0 || percent == 0 ? 0 : lenient ? (int) Math.floor(total * percent / 100.0) : Math.max(1, (int) Math.round(total * percent / 100.0));
        int taken = 0;
        double[] remainder = new double[kindCounts.length];
        for (int i = 0; i < kindCounts.length; i++) {
            double share = kindCounts[i] * percent / 100.0;
            lost[i] = (int) Math.floor(share);
            remainder[i] = share - lost[i];
            taken += lost[i];
        }
        // Hand out what is still owed: largest remainder first, a seeded shuffle deciding ties.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < kindCounts.length; i++) order.add(i);
        java.util.Collections.shuffle(order, new Random(seed));
        order.sort((a, b) -> Double.compare(remainder[b], remainder[a]));
        for (int round = 0; taken < owed && round < 2; round++) {
            for (int i : order) {
                if (taken >= owed) break;
                if (lost[i] < kindCounts[i]) { lost[i]++; taken++; }
            }
        }
        return new Plan(lost, taken, lenient ? Math.max(0, (int) Math.floor(unbankedWins * percent / 100.0)) : winsLost(unbankedWins, percent));
    }

    /** Wins lost to the toll: the share, rounded to the nearest win. One unbanked win at 35 percent loses nothing. */
    public static int winsLost(int unbankedWins, int percent) {
        if (unbankedWins <= 0) return 0;
        return (int) Math.min(unbankedWins, Math.round(unbankedWins * percent / 100.0));
    }
}
