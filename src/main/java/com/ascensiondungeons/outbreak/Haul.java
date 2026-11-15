package com.ascensiondungeons.outbreak;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What each player has earned in a run and not yet secured. Entries are <em>unbanked</em> until a checkpoint banks them. A player
 * who extracts keeps everything; one who dies, runs out of time or leaves keeps only what was banked. Only the haul is ever lost:
 * never Pokémon, gear or the wallet. Pure bookkeeping: the caller turns a {@link Settlement} into one AscensionLib payout.
 */
public final class Haul {
    public enum Kind { ARENA_WIN, CHEST, BOSS }

    public enum Exit {
        /** Left through the extraction portal. */
        EXTRACTED,
        DIED,
        TIMED_OUT,
        /** Walked out without extracting. */
        LEFT;

        boolean keepsEverything() { return this == EXTRACTED; }
    }

    /** What to pay one player. {@code arenaWins} and {@code chests} are counts; {@code bossDefeated} is a flag. */
    public record Settlement(int arenaWins, int chests, boolean bossDefeated) {
        public boolean isEmpty() { return arenaWins == 0 && chests == 0 && !bossDefeated; }
    }

    private static final class Ledger {
        final Map<Kind, Integer> banked = new EnumMap<>(Kind.class);
        final Map<Kind, Integer> unbanked = new EnumMap<>(Kind.class);

        int total(Kind k) { return banked.getOrDefault(k, 0) + unbanked.getOrDefault(k, 0); }
    }

    private final Map<UUID, Ledger> ledgers = new HashMap<>();

    public void add(UUID player, Kind kind, int amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
        ledgers.computeIfAbsent(player, p -> new Ledger()).unbanked.merge(kind, amount, Integer::sum);
    }

    public void addShared(Collection<UUID> players, Kind kind, int amount) {
        for (UUID p : players) add(p, kind, amount);
    }

    /** A checkpoint: secures everything the player has earned so far. */
    public void bank(UUID player) {
        Ledger l = ledgers.get(player);
        if (l == null) return;
        l.unbanked.forEach((k, v) -> l.banked.merge(k, v, Integer::sum));
        l.unbanked.clear();
    }

    /** What the player is owed for leaving the run this way. Does not change the ledger: the caller records the payout once. */
    public Settlement settle(UUID player, Exit exit) {
        Ledger l = ledgers.get(player);
        if (l == null) return new Settlement(0, 0, false);
        if (exit.keepsEverything()) return new Settlement(l.total(Kind.ARENA_WIN), l.total(Kind.CHEST), l.total(Kind.BOSS) > 0);
        return new Settlement(l.banked.getOrDefault(Kind.ARENA_WIN, 0), l.banked.getOrDefault(Kind.CHEST, 0), l.banked.getOrDefault(Kind.BOSS, 0) > 0);
    }
}
