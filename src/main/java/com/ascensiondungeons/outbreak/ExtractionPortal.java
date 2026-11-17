package com.ascensiondungeons.outbreak;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The way out. It is always there, and leaving through it costs something: either the party fights the gatekeeper, and whoever
 * is there when it falls leaves free during a short window, or a player pays the {@link Toll} and leaves at once. Each
 * gatekeeper beaten makes the next one stronger. Pure state machine: the caller spawns the gatekeeper and moves the players.
 */
public final class ExtractionPortal {
    public enum State {
        /** Waiting: a player may pay the toll or challenge the gatekeeper. */
        IDLE,
        /** The gatekeeper is being fought. The toll can still be paid. */
        FIGHT,
        /** The gatekeeper fell: anyone may leave free until the window closes. */
        OPEN,
        CLOSED
    }

    /**
     * @param windowTicks   how long the portal stays open after the gatekeeper falls
     * @param growthPercent how much stronger each later gatekeeper is (25 means x1.25 per gatekeeper already beaten)
     */
    public record Params(int windowTicks, int growthPercent) {
        public Params {
            if (windowTicks < 1) throw new IllegalArgumentException("windowTicks must be at least 1");
            if (growthPercent < 0) throw new IllegalArgumentException("growthPercent must not be negative");
        }
    }

    private final Params params;
    private final Set<UUID> escaped = new HashSet<>();
    private State state = State.IDLE;
    private long openedAt;
    private int defeated;

    public ExtractionPortal(Params params) { this.params = params; }

    public State state() { return state; }
    public int gatekeepersDefeated() { return defeated; }
    public Set<UUID> escaped() { return Set.copyOf(escaped); }

    /** How much stronger than the first the next gatekeeper is: 1 for the first. */
    public double gatekeeperStrength() { return Math.pow(1.0 + params.growthPercent() / 100.0, defeated); }

    /** Ticks left in the open window, or 0 when not open. */
    public long remaining(long now) {
        return state == State.OPEN ? Math.max(0, openedAt + params.windowTicks() - now) : 0;
    }

    /** A player calls the gatekeeper out. True if a fight starts now (false when one is already on, or the portal is open or closed). */
    public boolean challenge() {
        if (state != State.IDLE) return false;
        state = State.FIGHT;
        return true;
    }

    /** The gatekeeper fell: the portal opens. */
    public void gatekeeperDefeated(long now) {
        if (state != State.FIGHT) return;
        state = State.OPEN;
        openedAt = now;
        defeated++;
    }

    /** The challengers are gone or beaten: the gatekeeper leaves and the portal waits again. */
    public void challengeFailed() {
        if (state == State.FIGHT) state = State.IDLE;
    }

    /** Closes an open window that has run out. */
    public void tick(long now) {
        if (state == State.OPEN && now >= openedAt + params.windowTicks()) state = State.IDLE;
    }

    /** The player steps through an open portal. True if they are out; escaping twice is harmless. */
    public boolean escape(UUID player, long now) {
        if (state != State.OPEN || now >= openedAt + params.windowTicks()) return false;
        escaped.add(player);
        return true;
    }

    /** The run is over: nothing more can happen. */
    public void close() { state = State.CLOSED; }
}
