package com.ascensiondungeons.run;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunManagerTest {
    FakeHost host;
    RunConfig cfg;
    RunManager mgr;
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    long tick;

    @BeforeEach
    void setUp() {
        host = new FakeHost();
        cfg = new RunConfig();
        cfg.durationTicks = 100;
        cfg.cleanupTimeoutTicks = 40;
        cfg.bossPhaseTicks = 60;
        mgr = new RunManager(host, cfg, () -> 42L);
        host.online.add(alice);
        host.online.add(bob);
    }

    void tickTo(long t) { while (tick < t) mgr.tick(++tick); }

    Run start(UUID... who) {
        StartResult r = mgr.request(Set.of(who));
        assertTrue(r.ok(), r.error());
        return r.run();
    }

    @Test
    void aRequestIsQueuedWithTheSeedFromTheSupplier() {
        Run run = start(alice);
        assertEquals(RunState.QUEUED, run.state());
        assertEquals(42L, run.seed());
        assertEquals(1, mgr.queued());
        assertEquals(0, mgr.resourceCount());
        assertNull(run.dungeonType());
    }

    @Test
    void emptyAndOversizedPartiesAreRefused() {
        assertFalse(mgr.request(Set.of()).ok());
        cfg.maxPartySize = 1;
        StartResult r = mgr.request(Set.of(alice, bob));
        assertFalse(r.ok());
        assertNotNull(r.error());
        assertNull(r.run());
    }

    @Test
    void aPlayerCannotBeInTwoRuns() {
        start(alice);
        assertFalse(mgr.request(Set.of(alice, bob)).ok());
        assertTrue(mgr.request(Set.of(bob)).ok());
    }

    @Test
    void theQueueIsBounded() {
        cfg.maxQueuedRuns = 1;
        start(alice);
        assertFalse(mgr.request(Set.of(bob)).ok());
    }

    @Test
    void aRunPreparesThenGoesActiveAndPutsOnlinePlayersInside() {
        host.chunksNeeded = 8;
        cfg.chunksPerTick = 4;
        Run run = start(alice);
        mgr.tick(++tick);
        assertEquals(RunState.PREPARING, run.state());
        assertEquals(1, host.opened);
        assertEquals(1, mgr.resourceCount());
        mgr.tick(++tick);
        assertEquals(RunState.PREPARING, run.state());
        mgr.tick(++tick);
        assertEquals(RunState.ACTIVE, run.state());
        assertEquals(Run.Presence.INSIDE, run.members().get(alice).presence());
        assertTrue(host.inside.contains(alice));
        assertEquals(tick + 100, run.deadline());
    }

    @Test
    void anOfflineMemberStaysPendingUntilTheyAppear() {
        host.online.remove(bob);
        Run run = start(alice, bob);
        tickTo(3);
        assertEquals(RunState.ACTIVE, run.state());
        assertEquals(Run.Presence.PENDING, run.members().get(bob).presence());
        host.online.add(bob);
        tickTo(tick + 1);
        assertEquals(Run.Presence.INSIDE, run.members().get(bob).presence());
    }

    @Test
    void theClockEndsTheRunAndTheLevelIsDeleted() {
        Run run = start(alice);
        tickTo(3);
        long deadline = run.deadline();
        tickTo(deadline - 1);
        assertEquals(RunState.ACTIVE, run.state());
        tickTo(deadline);
        assertEquals(EndReason.TIMEOUT, run.endReason());
        assertEquals(1, host.released);
        assertEquals(1, host.deleted);
        assertFalse(host.inside.contains(alice));
        // The level is already gone, so the next tick closes the run and drops it.
        tickTo(deadline + 1);
        assertEquals(RunState.CLOSED, run.state());
        assertTrue(mgr.runs().isEmpty());
        assertEquals(0, mgr.resourceCount());
    }

    @Test
    void aBossTakingOverExtendsTheRunOnce() {
        host.bossTakesOver = true;
        Run run = start(alice);
        tickTo(3);
        long first = run.deadline();
        tickTo(first);
        assertEquals(RunState.ACTIVE, run.state());
        assertTrue(run.bossPhase());
        assertEquals(first + cfg.bossPhaseTicks, run.deadline());
        // The boss window runs out too: the hook is not asked a second time.
        tickTo(run.deadline());
        assertEquals(EndReason.TIMEOUT, run.endReason());
    }

    @Test
    void completeEndsTheRunWithCompleted() {
        Run run = start(alice);
        tickTo(5);
        assertTrue(mgr.complete(run.id()));
        assertEquals(EndReason.COMPLETED, run.endReason());
        assertFalse(mgr.complete(run.id()), "already winding down");
    }

    @Test
    void endingAQueuedRunClosesItWithoutTouchingTheHost() {
        cfg.maxConcurrentRuns = 1;
        start(alice);
        Run second = start(bob);
        tickTo(1);
        assertEquals(RunState.QUEUED, second.state());
        assertTrue(mgr.end(second.id(), EndReason.ADMIN));
        assertEquals(RunState.CLOSED, second.state());
        assertEquals(1, host.opened);
        assertEquals(0, host.deleted);
    }

    @Test
    void concurrencyIsCappedAndQueuedRunsStartInOrder() {
        cfg.maxConcurrentRuns = 1;
        Run first = start(alice);
        Run second = start(bob);
        tickTo(3);
        assertEquals(RunState.ACTIVE, first.state());
        assertEquals(RunState.QUEUED, second.state());
        assertEquals(1, mgr.queued());
        mgr.end(first.id(), EndReason.ADMIN);
        tickTo(tick + 3);
        assertEquals(RunState.ACTIVE, second.state());
    }

    @Test
    void leavingSendsThePlayerOutAndTheEmptyRunEnds() {
        Run run = start(alice);
        tickTo(3);
        assertTrue(mgr.leave(alice));
        assertEquals(Run.Presence.LEFT, run.members().get(alice).presence());
        assertFalse(host.inside.contains(alice));
        assertTrue(mgr.runOf(alice).isEmpty());
        assertFalse(mgr.leave(alice), "not in a run any more");
        tickTo(tick + 1);
        assertEquals(EndReason.ABANDONED, run.endReason());
    }

    @Test
    void aPlayerWhoVanishesFromTheLevelHasLeft() {
        Run run = start(alice, bob);
        tickTo(3);
        host.inside.remove(alice);
        tickTo(tick + 1);
        assertEquals(Run.Presence.LEFT, run.members().get(alice).presence());
        assertEquals(RunState.ACTIVE, run.state(), "bob is still inside");
    }

    @Test
    void aLevelThatCannotOpenFailsTheRun() {
        host.openFails = true;
        Run run = start(alice);
        tickTo(1);
        assertEquals(EndReason.FAILED, run.endReason());
        assertEquals(RunState.ENDING, run.state());
    }

    @Test
    void aLevelThatWillNotDeleteIsFlaggedAndCanBeRetried() {
        host.levelGone = false;
        Run run = start(alice);
        tickTo(3);
        mgr.end(run.id(), EndReason.ADMIN);
        tickTo(tick + cfg.cleanupTimeoutTicks + 1);
        assertEquals(RunState.CLEANUP_FAILED, run.state());
        assertEquals(1, mgr.resourceCount(), "it still holds a level");
        assertFalse(mgr.retryCleanup(UUID.randomUUID()));
        int deletedBefore = host.deleted;
        assertTrue(mgr.retryCleanup(run.id()));
        assertEquals(RunState.ENDING, run.state());
        assertEquals(deletedBefore + 1, host.deleted);
        host.levelGone = true;
        tickTo(tick + 1);
        assertEquals(RunState.CLOSED, run.state());
    }

    @Test
    void retryOnlyAppliesToCleanupFailed() {
        Run run = start(alice);
        assertFalse(mgr.retryCleanup(run.id()));
    }

    @Test
    void dungeonTypeIsKeptAndCinderforgeUsesItsOwnClock() {
        cfg.cinderforgeDurationTicks = 500;
        Run run = mgr.request(Set.of(alice), RunManager.CINDERFORGE).run();
        assertEquals("cinderforge", run.dungeonType());
        tickTo(3);
        assertEquals(RunState.ACTIVE, run.state());
        assertEquals(run.stateSince() + 500, run.deadline());
    }

    @Test
    void anOutbreakDiveRunsOnTheLongClockItsSettingsAskFor() {
        Run dive = mgr.request(Set.of(alice), "water").run();
        tickTo(3);
        assertEquals(dive.stateSince() + 45 * 60 * 20, dive.deadline(), "water's outbreak.json asks for 45 minutes");
    }

    @Test
    void shutdownReleasesAndDeletesEveryLevel() {
        Run a = start(alice);
        Run b = start(bob);
        tickTo(3);
        mgr.shutdown();
        assertEquals(2, host.deleted);
        assertEquals(EndReason.SHUTDOWN, a.endReason());
        assertEquals(RunState.CLOSED, b.state());
        assertTrue(mgr.runs().isEmpty());
    }

    @Test
    void getAndRunOfFindLiveRuns() {
        Run run = start(alice);
        assertSame(run, mgr.get(run.id()).orElseThrow());
        assertSame(run, mgr.runOf(alice).orElseThrow());
        assertTrue(mgr.runOf(UUID.randomUUID()).isEmpty());
        assertTrue(mgr.get(UUID.randomUUID()).isEmpty());
    }
}
