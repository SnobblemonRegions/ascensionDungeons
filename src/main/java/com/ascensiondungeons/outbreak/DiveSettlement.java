package com.ascensiondungeons.outbreak;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns each player's {@link Haul.Settlement} into the fewest AscensionLib {@code settleDungeonRun} calls. That call takes one
 * arena-win count and one boss flag for everyone it names, so players who earned different amounts need separate calls.
 * A player with nothing to pay is left out.
 */
public final class DiveSettlement {
    /** AscensionLib accepts 0..16 arena wins. */
    public static final int MAX_ARENA_WINS = 16;

    public record Call(int tier, int arenaWins, boolean bossDefeated, List<UUID> players) {}

    private DiveSettlement() {}

    public static List<Call> plan(Map<UUID, Haul.Settlement> settlements, int tier) {
        if (tier < 1 || tier > 10) throw new IllegalArgumentException("tier must be 1..10");
        Map<Long, List<UUID>> groups = new LinkedHashMap<>();
        settlements.forEach((player, s) -> {
            int wins = Math.min(MAX_ARENA_WINS, s.arenaWins());
            if (wins == 0 && !s.bossDefeated()) return;
            groups.computeIfAbsent(wins * 2L + (s.bossDefeated() ? 1 : 0), k -> new ArrayList<>()).add(player);
        });
        List<Call> calls = new ArrayList<>();
        groups.forEach((key, players) -> calls.add(new Call(tier, (int) (key / 2), key % 2 == 1, List.copyOf(players))));
        return calls;
    }
}
