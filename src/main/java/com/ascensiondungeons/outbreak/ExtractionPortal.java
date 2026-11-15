package com.ascensiondungeons.outbreak;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The way out. Kills charge the portal; when it is full it opens for a short window in which anyone may escape. If the window
 * passes the portal falls back to charging and needs more charge next time, so a party cannot dither forever. Pure state machine:
 * the caller spawns the effects and moves the players.
 */
public final class ExtractionPortal {
    public enum State { CHARGING, OPEN, CLOSED }

    /**
     * @param chargeNeeded  charge for the first opening, in kill weight
     * @param windowTicks   how long it stays open
     * @param growthPercent how much more charge each later opening needs (50 means x1.5 per missed opening)
     */
    public record Params(int chargeNeeded, int windowTicks, int growthPercent) {
        public Params {
            if (chargeNeeded < 1) throw new IllegalArgumentException("chargeNeeded must be at least 1");
            if (windowTicks < 1) throw new IllegalArgumentException("windowTicks must be at least 1");
            if (growthPercent < 0) throw new IllegalArgumentException("growthPercent must not be negative");
        }
    }

    private final Params params;
    private final Set<UUID> escaped = new HashSet<>();
    private State state = State.CHARGING;
    private double charge;
    private double needed;
    private long openedAt;
    private int timesOpened;

    public ExtractionPortal(Params params) {
        this.params = params;
        this.needed = params.chargeNeeded();
    }

    public State state() { return state; }

    /** Charge as a fraction of what this opening needs, 0..1. */
    public double charge() { return state == State.OPEN ? 1.0 : Math.min(1.0, charge / needed); }

    public int timesOpened() { return timesOpened; }

    public Set<UUID> escaped() { return Set.copyOf(escaped); }

    /** Ticks left in the open window, or 0 when not open. */
    public long remaining(long now) {
        return state == State.OPEN ? Math.max(0, openedAt + params.windowTicks() - now) : 0;
    }

    /** A mob died near the portal. Kills while it is open or closed do nothing. */
    public void onKill(double weight, long now) {
        if (weight <= 0) throw new IllegalArgumentException("weight must be positive");
        if (state != State.CHARGING) return;
        charge += weight;
        if (charge >= needed) {
            state = State.OPEN;
            openedAt = now;
            timesOpened++;
        }
    }

    /** Closes an open window that has run out. */
    public void tick(long now) {
        if (state == State.OPEN && now >= openedAt + params.windowTicks()) {
            state = State.CHARGING;
            charge = 0;
            needed = needed * (1.0 + params.growthPercent() / 100.0);
        }
    }

    /** The player steps through. True if the portal is open (and so they are out); escaping twice is harmless. */
    public boolean escape(UUID player, long now) {
        if (state != State.OPEN || now >= openedAt + params.windowTicks()) return false;
        escaped.add(player);
        return true;
    }

    /** The run is over: nothing more can happen. */
    public void close() { state = State.CLOSED; }
}
