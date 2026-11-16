package com.ascensiondungeons.outbreak;

import java.util.ArrayList;
import java.util.List;

/**
 * The check at the door: is this player ready for this dungeon? Pure. It never refuses anyone; it names what is likely to
 * spoil the dive so the player can fix it or go in knowingly.
 */
public final class Readiness {
    private Readiness() {}

    /**
     * What a dungeon asks of a party, for the picker and for this check.
     *
     * @param id          the dungeon's id ({@code water})
     * @param name        its display name
     * @param tier        its tier: 1 is the gentlest
     * @param minLevel    the level its weakest enemies start at
     * @param maxLevel    the level its ordinary enemies reach
     * @param keeperLevel the level its gatekeepers start at
     * @param minutes     how long a dive may last
     */
    public record Card(String id, String name, int tier, int minLevel, int maxLevel, int keeperLevel, int minutes) {
        /** The dungeon's type as a word: {@code water} is "Water". */
        public String typeName() { return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1); }
    }

    /**
     * What a player brings.
     *
     * @param known     false when the server has no Pokémon mod, so nothing can be said about a team
     * @param levels    the levels of their Pokémon that can fight
     * @param fainted   how many of their Pokémon have fainted
     * @param freeSlots empty slots in their main inventory
     */
    public record Team(boolean known, int[] levels, int fainted, int freeSlots) {
        public int strongest() { int best = 0; for (int l : levels) best = Math.max(best, l); return best; }
    }

    public enum Concern { NO_POKEMON, ALL_FAINTED, UNDER_LEVELLED, INVENTORY_FULL, HIGH_TIER_FIRST_DIVE }

    /** Fewer free slots than this and loot will not fit. */
    public static final int ROOM_WANTED = 6;

    /** What the check found. {@code lines} are ready to show, one per concern, in the order of {@code concerns}. */
    public record Verdict(List<Concern> concerns, List<String> lines) {
        public boolean ready() { return concerns.isEmpty(); }
    }

    /**
     * @param rookie     the player has never extracted
     * @param gentlest   the gentlest dungeon on offer, recommended for a first dive (null when there is nothing to recommend)
     */
    public static Verdict assess(Card card, Team team, boolean rookie, Card gentlest) {
        List<Concern> concerns = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        if (team.known()) {
            if (team.levels().length == 0 && team.fainted() == 0) {
                concerns.add(Concern.NO_POKEMON);
                lines.add("You have no Pokémon with you. The enemies here fight in real time: bring a team.");
            } else if (team.levels().length == 0) {
                concerns.add(Concern.ALL_FAINTED);
                lines.add("All your Pokémon have fainted. Heal them before you go in.");
            } else if (team.strongest() < card.minLevel()) {
                concerns.add(Concern.UNDER_LEVELLED);
                lines.add("Your strongest Pokémon is level " + team.strongest() + ". Enemies here start at level " + card.minLevel() + " and reach " + card.maxLevel() + ".");
            }
        }
        if (team.freeSlots() < ROOM_WANTED) {
            concerns.add(Concern.INVENTORY_FULL);
            lines.add("You have " + team.freeSlots() + " free inventory slot" + (team.freeSlots() == 1 ? "" : "s") + ". Loot needs room: empty your pockets first.");
        }
        if (rookie && gentlest != null && card.tier() > gentlest.tier()) {
            concerns.add(Concern.HIGH_TIER_FIRST_DIVE);
            lines.add("This is a tier " + card.tier() + " dungeon. For a first dive, " + gentlest.name() + " (tier " + gentlest.tier() + ") is the one to start with.");
        }
        return new Verdict(List.copyOf(concerns), List.copyOf(lines));
    }

    /** The gentlest dungeon: lowest tier, then lowest gatekeeper level, then by name so the answer never changes. Null for none. */
    public static Card gentlest(List<Card> cards) {
        Card best = null;
        for (Card c : cards) if (best == null || order(c, best) < 0) best = c;
        return best;
    }

    /** The order dungeons are offered in: gentlest first. */
    public static int order(Card a, Card b) {
        if (a.tier() != b.tier()) return Integer.compare(a.tier(), b.tier());
        if (a.keeperLevel() != b.keeperLevel()) return Integer.compare(a.keeperLevel(), b.keeperLevel());
        return a.name().compareTo(b.name());
    }
}
