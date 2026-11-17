package com.ascensiondungeons.run;

import com.ascensiondungeons.AscensionDungeonsLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Owns every run's lifecycle and talks to the world only through {@link RunHost}. Server thread only; driven by {@link #tick}.
 * No Minecraft types, so the whole state machine is unit tested.
 */
public final class RunManager {
    /** The dungeon type id of the hand-built Cinderforge Bastion. */
    public static final String CINDERFORGE = "cinderforge";

    private final RunHost host;
    private final RunConfig cfg;
    private final LongSupplier seeds;
    private final Map<UUID, Run> runs = new LinkedHashMap<>();
    private long now;

    public RunManager(RunHost host, RunConfig cfg, LongSupplier seeds) {
        this.host = host;
        this.cfg = cfg;
        this.seeds = seeds;
    }

    // ---- queries -----------------------------------------------------------------------------------------

    /** The last tick passed to {@link #tick}. */
    public long now() { return now; }
    /** Live runs, oldest first. A snapshot, so callers may end runs while iterating. */
    public List<Run> runs() { return new ArrayList<>(runs.values()); }
    public Optional<Run> get(UUID id) { return Optional.ofNullable(runs.get(id)); }
    /** The run this player belongs to (not yet LEFT), if any. */
    public Optional<Run> runOf(UUID player) {
        for (Run r : runs.values()) {
            Run.Member m = r.members().get(player);
            if (m != null && m.presence() != Run.Presence.LEFT && r.state() != RunState.ENDING && r.state() != RunState.CLEANUP_FAILED) return Optional.of(r);
        }
        return Optional.empty();
    }
    /** Runs that hold a level (or are still deleting one). */
    public int resourceCount() {
        int n = 0;
        for (Run r : runs.values()) if (r.state() != RunState.QUEUED) n++;
        return n;
    }
    public int queued() {
        int n = 0;
        for (Run r : runs.values()) if (r.state() == RunState.QUEUED) n++;
        return n;
    }

    // ---- requests ----------------------------------------------------------------------------------------

    /** A district run. */
    public StartResult request(Set<UUID> party) { return request(party, null); }

    /** A run of the given dungeon type at a difficulty the party's leader chose ({@code null} for the server's default). */
    public StartResult request(Set<UUID> party, String dungeonType, String difficulty) {
        StartResult r = request(party, dungeonType);
        if (r.ok() && r.run() != null) r.run().difficulty(difficulty);
        return r;
    }

    /** A run of the given dungeon type ({@code null} for a district run). */
    public StartResult request(Set<UUID> party, String dungeonType) {
        if (party == null || party.isEmpty()) return StartResult.refused("A dungeon needs at least one player.");
        if (party.size() > cfg.maxPartySize) return StartResult.refused("A party can have at most " + cfg.maxPartySize + " players.");
        for (UUID p : party) if (runOf(p).isPresent()) return StartResult.refused("A player in the party is already in a dungeon run.");
        if (queued() >= cfg.maxQueuedRuns) return StartResult.refused("The dungeon queue is full. Try again shortly.");
        Run run = new Run(UUID.randomUUID(), seeds.getAsLong(), dungeonType, party, now);
        runs.put(run.id(), run);
        return StartResult.ok(run);
    }

    /** The player walks out of their run. False if they are not in one. */
    public boolean leave(UUID player) {
        Optional<Run> found = runOf(player);
        if (found.isEmpty()) return false;
        Run run = found.get();
        Run.Member m = run.member(player);
        if (run.state() == RunState.ACTIVE) host.exit(run, player);
        m.presence(Run.Presence.LEFT);
        return true;
    }

    /** Ends a run that has not already ended. False if the id is unknown or the run is already winding down. */
    public boolean end(UUID id, EndReason reason) {
        Run run = runs.get(id);
        if (run == null) return false;
        switch (run.state()) {
            case ENDING, CLOSED, CLEANUP_FAILED -> { return false; }
            case QUEUED -> { finish(run, reason); return true; }
            default -> { begin(run, reason); return true; }
        }
    }

    /** The party won. */
    public boolean complete(UUID id) { return end(id, EndReason.COMPLETED); }

    /** Tries again to delete the level of a run stuck in CLEANUP_FAILED. */
    public boolean retryCleanup(UUID id) {
        Run run = runs.get(id);
        if (run == null || run.state() != RunState.CLEANUP_FAILED) return false;
        run.state(RunState.ENDING, now);
        deleteLevel(run);
        return true;
    }

    /** Server stopping: tear every level down now, best effort. */
    public void shutdown() {
        for (Run run : new ArrayList<>(runs.values())) {
            if (run.state() == RunState.QUEUED) { run.endReason(EndReason.SHUTDOWN); run.state(RunState.CLOSED, now); continue; }
            if (run.endReason() == null) run.endReason(EndReason.SHUTDOWN);
            try { host.releaseLevel(run); } catch (RuntimeException e) { AscensionDungeonsLog.LOG.warn("Run {}: release failed at shutdown", run.id(), e); }
            try { host.deleteLevel(run); } catch (RuntimeException e) { AscensionDungeonsLog.LOG.warn("Run {}: delete failed at shutdown", run.id(), e); }
            run.state(RunState.CLOSED, now);
        }
        runs.clear();
    }

    // ---- the clock ---------------------------------------------------------------------------------------

    public void tick(long tick) {
        this.now = tick;
        for (Run run : new ArrayList<>(runs.values())) {
            try {
                switch (run.state()) {
                    case QUEUED -> tickQueued(run);
                    case PREPARING -> tickPreparing(run);
                    case ACTIVE -> tickActive(run);
                    case ENDING -> tickEnding(run);
                    case CLOSED, CLEANUP_FAILED -> { }
                }
            } catch (RuntimeException e) {
                // One run's bug must not stall the others; end it so it cannot throw every tick.
                AscensionDungeonsLog.LOG.error("Run {} failed in state {}", run.id(), run.state(), e);
                if (run.state() == RunState.ENDING) fail(run); else begin(run, EndReason.FAILED);
            }
        }
        runs.values().removeIf(r -> r.state() == RunState.CLOSED);
    }

    private void tickQueued(Run run) {
        // Oldest queued run first: only the head of the queue may start, so order is fair.
        if (resourceCount() >= cfg.maxConcurrentRuns) return;
        for (Run other : runs.values()) {
            if (other.state() == RunState.QUEUED) { if (other != run) return; break; }
        }
        run.state(RunState.PREPARING, now);
        try {
            host.openLevel(run);
        } catch (Exception e) {
            AscensionDungeonsLog.LOG.error("Run {}: could not open the level", run.id(), e);
            notifyAll(run, "The dungeon could not be opened.");
            begin(run, EndReason.FAILED);
        }
    }

    private void tickPreparing(Run run) {
        if (!anyoneWaiting(run)) { begin(run, EndReason.ABANDONED); return; }
        host.requestChunks(run, cfg.chunksPerTick);
        if (!host.chunksReady(run)) return;
        run.state(RunState.ACTIVE, now);
        run.deadline(now + durationFor(run));
        enterWaiting(run);
    }

    private void tickActive(Run run) {
        // A player who vanished from the level (disconnect, death teleport, admin tp) has left the run.
        for (Map.Entry<UUID, Run.Member> e : run.members().entrySet()) {
            if (e.getValue().presence() == Run.Presence.INSIDE && !host.isInside(run, e.getKey())) e.getValue().presence(Run.Presence.LEFT);
        }
        enterWaiting(run);
        if (!anyoneWaiting(run) && run.members().values().stream().noneMatch(m -> m.presence() == Run.Presence.INSIDE)) {
            begin(run, EndReason.ABANDONED);
            return;
        }
        if (now < run.deadline()) return;
        if (!run.bossPhase() && host.beginBoss(run)) {
            run.bossPhase(true);
            run.deadline(now + cfg.bossPhaseTicks);
            return;
        }
        notifyAll(run, "Time is up.");
        begin(run, EndReason.TIMEOUT);
    }

    private void tickEnding(Run run) {
        if (host.isLevelGone(run)) { finish(run, run.endReason()); return; }
        if (now - run.stateSince() > cfg.cleanupTimeoutTicks) fail(run);
    }

    // ---- transitions -------------------------------------------------------------------------------------

    /** Starts winding a run down: everyone out, level released and deleted. */
    private void begin(Run run, EndReason reason) {
        run.endReason(reason);
        run.state(RunState.ENDING, now);
        try {
            host.releaseLevel(run);
            for (UUID p : run.members().keySet()) {
                Run.Member m = run.member(p);
                if (m.presence() == Run.Presence.INSIDE) host.exit(run, p);
                m.presence(Run.Presence.LEFT);
            }
        } catch (RuntimeException e) {
            AscensionDungeonsLog.LOG.error("Run {}: release failed", run.id(), e);
        }
        deleteLevel(run);
    }

    private void deleteLevel(Run run) {
        try {
            host.deleteLevel(run);
        } catch (RuntimeException e) {
            AscensionDungeonsLog.LOG.error("Run {}: delete failed", run.id(), e);
        }
    }

    private void finish(Run run, EndReason reason) {
        if (run.endReason() == null) run.endReason(reason);
        run.state(RunState.CLOSED, now);
    }

    private void fail(Run run) {
        AscensionDungeonsLog.LOG.error("Run {}: the level did not delete within {} ticks; use /dungeon retry {}", run.id(), cfg.cleanupTimeoutTicks, run.id());
        run.state(RunState.CLEANUP_FAILED, now);
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private long durationFor(Run run) {
        if (CINDERFORGE.equals(run.dungeonType())) return cfg.cinderforgeDurationTicks;
        if (run.dungeonType() != null && com.ascensiondungeons.gen.authored.AuthoredDungeons.isAuthored(run.dungeonType())) {
            // An Outbreak dive is long on purpose: there is a lot to explore, and the Outbreak is what ends most runs.
            var outbreak = com.ascensiondungeons.gen.authored.AuthoredLayout.get(run.dungeonType()).outbreak();
            if (outbreak == null) return cfg.authoredDurationTicks;
            int minutes = com.ascensiondungeons.gen.authored.Jx.i(outbreak, "durationMinutes", 0);
            return minutes > 0 ? minutes * 60L * 20L : cfg.diveDurationTicks;
        }
        return cfg.durationTicks;
    }

    private boolean anyoneWaiting(Run run) {
        for (Map.Entry<UUID, Run.Member> e : run.members().entrySet())
            if (e.getValue().presence() == Run.Presence.PENDING) return true;
        return false;
    }

    /** Moves every PENDING member who is online into the level. */
    private void enterWaiting(Run run) {
        for (Map.Entry<UUID, Run.Member> e : run.members().entrySet()) {
            if (e.getValue().presence() != Run.Presence.PENDING || !host.isOnline(e.getKey())) continue;
            if (host.enter(run, e.getKey())) e.getValue().presence(Run.Presence.INSIDE);
        }
    }

    private void notifyAll(Run run, String text) {
        for (UUID p : run.members().keySet()) host.message(p, text);
    }
}
