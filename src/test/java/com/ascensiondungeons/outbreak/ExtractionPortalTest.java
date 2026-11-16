package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtractionPortalTest {
    // open for 400 ticks after the gatekeeper falls; each gatekeeper beaten makes the next 25 percent stronger.
    private final ExtractionPortal.Params p = new ExtractionPortal.Params(400, 25);
    private final UUID a = UUID.randomUUID();

    @Test
    void itWaitsUntilSomeoneChallengesTheGatekeeper() {
        ExtractionPortal x = new ExtractionPortal(p);
        assertEquals(ExtractionPortal.State.IDLE, x.state());
        assertFalse(x.escape(a, 0), "nobody leaves free before the gatekeeper falls");
        assertTrue(x.challenge());
        assertEquals(ExtractionPortal.State.FIGHT, x.state());
        assertFalse(x.challenge(), "one gatekeeper at a time");
        assertFalse(x.escape(a, 10));
    }

    @Test
    void beatingTheGatekeeperOpensThePortalForAWindow() {
        ExtractionPortal x = new ExtractionPortal(p);
        x.challenge();
        x.gatekeeperDefeated(100);
        assertEquals(ExtractionPortal.State.OPEN, x.state());
        assertEquals(400, x.remaining(100));
        assertTrue(x.escape(a, 300));
        assertTrue(x.escape(a, 310), "escaping twice is harmless");
        assertEquals(1, x.escaped().size());
        x.tick(499);
        assertEquals(ExtractionPortal.State.OPEN, x.state());
        x.tick(500);
        assertEquals(ExtractionPortal.State.IDLE, x.state());
        assertFalse(x.escape(UUID.randomUUID(), 501));
    }

    @Test
    void aFailedChallengeSendsTheGatekeeperAway() {
        ExtractionPortal x = new ExtractionPortal(p);
        x.challenge();
        x.challengeFailed();
        assertEquals(ExtractionPortal.State.IDLE, x.state());
        assertEquals(0, x.gatekeepersDefeated());
        assertTrue(x.challenge(), "it can be called out again");
    }

    @Test
    void eachGatekeeperBeatenMakesTheNextStronger() {
        ExtractionPortal x = new ExtractionPortal(p);
        assertEquals(1.0, x.gatekeeperStrength(), 1e-9);
        x.challenge(); x.gatekeeperDefeated(0); x.tick(400);
        assertEquals(1.25, x.gatekeeperStrength(), 1e-9);
        x.challenge(); x.gatekeeperDefeated(500); x.tick(900);
        assertEquals(1.5625, x.gatekeeperStrength(), 1e-9);
        assertEquals(2, x.gatekeepersDefeated());
    }

    @Test
    void defeatOnlyCountsDuringAFight() {
        ExtractionPortal x = new ExtractionPortal(p);
        x.gatekeeperDefeated(0);
        assertEquals(ExtractionPortal.State.IDLE, x.state());
        assertEquals(0, x.gatekeepersDefeated());
    }

    @Test
    void closingTheRunStopsTheMachine() {
        ExtractionPortal x = new ExtractionPortal(p);
        x.challenge();
        x.gatekeeperDefeated(0);
        x.close();
        assertEquals(ExtractionPortal.State.CLOSED, x.state());
        assertFalse(x.escape(a, 10));
        assertFalse(x.challenge());
        x.tick(1000);
        assertEquals(ExtractionPortal.State.CLOSED, x.state());
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExtractionPortal.Params(0, 25));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionPortal.Params(400, -1));
    }
}
