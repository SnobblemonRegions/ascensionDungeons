package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OutbreakClockTest {
    // 1 stage per 100 ticks, up to stage 5, so the tests read in round numbers.
    private final OutbreakClock.Params p = new OutbreakClock.Params(100, 5, 0.25, 0.04, 0.30, 2);

    @Test
    void startsCalmAndRisesOneStagePerInterval() {
        OutbreakClock c = new OutbreakClock(p, 1000);
        assertEquals(0, c.stage(1000));
        assertEquals(0, c.stage(1099));
        assertEquals(1, c.stage(1100));
        assertEquals(3, c.stage(1350));
    }

    @Test
    void neverPassesTheMaximumStage() {
        OutbreakClock c = new OutbreakClock(p, 0);
        assertEquals(5, c.stage(500));
        assertEquals(5, c.stage(1_000_000));
    }

    @Test
    void aClockReadBeforeItStartedIsCalm() {
        assertEquals(0, new OutbreakClock(p, 500).stage(100));
    }

    @Test
    void aCurseAddsTimeAndNeverTakesItAway() {
        OutbreakClock c = new OutbreakClock(p, 0);
        c.addPenalty(250);
        assertEquals(2, c.stage(0));
        c.addPenalty(-9999);
        assertEquals(2, c.stage(0), "a negative penalty is ignored");
        assertEquals(250, c.penaltyTicks());
    }

    @Test
    void pollReportsEachNewStageOnce() {
        OutbreakClock c = new OutbreakClock(p, 0);
        assertEquals(-1, c.pollNewStage(50));
        assertEquals(1, c.pollNewStage(100));
        assertEquals(-1, c.pollNewStage(150));
        assertEquals(3, c.pollNewStage(310), "skipped stages are reported as the latest one");
        assertEquals(-1, c.pollNewStage(310));
    }

    @Test
    void scalesGrowLinearlyAndStayBounded() {
        assertEquals(1.0, p.spawnScale(0), 1e-9);
        assertEquals(1.5, p.spawnScale(2), 1e-9);
        assertEquals(0.04, p.eliteChance(1), 1e-9);
        assertEquals(0.20, p.eliteChance(5), 1e-9);
        // the cap holds even if a config asks for more than 100 percent
        OutbreakClock.Params wild = new OutbreakClock.Params(100, 50, 0.25, 0.04, 0.30, 2);
        assertEquals(0.30, wild.eliteChance(50), 1e-9);
    }

    @Test
    void zoneTierRisesWithStageButStaysInRange() {
        assertEquals(3, p.zoneTier(3, 0));
        assertEquals(4, p.zoneTier(3, 2));
        assertEquals(5, p.zoneTier(3, 5));
        assertEquals(10, p.zoneTier(9, 5), "clamped to the library's maximum tier");
        assertEquals(1, p.zoneTier(0, 0), "and to its minimum");
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OutbreakClock.Params(0, 5, 0.25, 0.04, 0.3, 2));
        assertThrows(IllegalArgumentException.class, () -> new OutbreakClock.Params(100, -1, 0.25, 0.04, 0.3, 2));
        assertThrows(IllegalArgumentException.class, () -> new OutbreakClock.Params(100, 5, -0.1, 0.04, 0.3, 2));
        assertThrows(IllegalArgumentException.class, () -> new OutbreakClock.Params(100, 5, 0.25, 0.04, 1.5, 2));
        assertThrows(IllegalArgumentException.class, () -> new OutbreakClock.Params(100, 5, 0.25, 0.04, 0.3, 0));
    }
}
