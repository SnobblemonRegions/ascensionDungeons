package com.ascensiondungeons.run;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One dungeon run: its party, clock and state. Mutated only by {@link RunManager}. */
public final class Run {
    /** Where a party member is relative to the run level. */
    public enum Presence {
        /** Registered but not yet teleported in (offline, or the level is not ready). */
        PENDING,
        /** In the run level. */
        INSIDE,
        /** Walked out, disconnected from the level, or sent back. Never re-enters. */
        LEFT
    }

    public static final class Member {
        private Presence presence = Presence.PENDING;
        public Presence presence() { return presence; }
        void presence(Presence p) { this.presence = p; }
    }

    private final UUID id;
    private final long seed;
    private final String dungeonType;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private final Map<UUID, Member> membersView = Collections.unmodifiableMap(members);
    private final long createdAt;
    private RunState state = RunState.QUEUED;
    private EndReason endReason;
    private long deadline = Long.MAX_VALUE;
    private long stateSince;
    private boolean bossPhase;

    Run(UUID id, long seed, String dungeonType, Set<UUID> party, long now) {
        this.id = id;
        this.seed = seed;
        this.dungeonType = dungeonType;
        for (UUID p : party) members.put(p, new Member());
        this.createdAt = now;
        this.stateSince = now;
    }

    public UUID id() { return id; }
    public long seed() { return seed; }
    /** Null for a district run; otherwise {@code cinderforge}, an authored dungeon id, or a type-pilot id. */
    public String dungeonType() { return dungeonType; }

    private String difficulty;
    /** The difficulty the party's leader chose for this run, or null for the server's default. */
    public String difficulty() { return difficulty; }
    void difficulty(String d) { this.difficulty = d; }
    public Map<UUID, Member> members() { return membersView; }
    public long createdAt() { return createdAt; }
    public RunState state() { return state; }
    public EndReason endReason() { return endReason; }
    /** Server tick at which the clock runs out; only meaningful once ACTIVE. */
    public long deadline() { return deadline; }
    public long stateSince() { return stateSince; }
    /** True once the exploration clock ended and the final raid arena took over (type pilot). */
    public boolean bossPhase() { return bossPhase; }

    void state(RunState s, long now) { this.state = s; this.stateSince = now; }
    void endReason(EndReason r) { this.endReason = r; }
    void deadline(long d) { this.deadline = d; }
    void bossPhase(boolean b) { this.bossPhase = b; }
    Member member(UUID id) { return members.get(id); }
}
