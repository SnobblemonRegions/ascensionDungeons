package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtractionPortalTest {
    // needs 10 kills' worth of charge, stays open 400 ticks, each miss costs 50 percent more charge.
    private final ExtractionPortal.Params p = new ExtractionPortal.Params(10, 400, 50);
    private final UUID a = UUID.randomUUID();

    @Test
    void startsChargingAndOpensWhenFull() {
        ExtractionPortal x = new ExtractionPortal(p);
        assertEquals(ExtractionPortal.State.CHARGING, x.state());
        x.onKill(4, 0);
        assertEquals(ExtractionPortal.State.CHARGING, x.state());
        assertEquals(0.4, x.charge(), 1e-9);
        x.onKill(6, 100);
        assertEquals(ExtractionPortal.State.OPEN, x.state());
        assertEquals(1.0, x.charge(), 1e-9);
        assertEquals(400, x.remaining(100));
    }

    @Test
    void chargeOverflowDoesNotCarryAndWeightsMustBePositive() {
        ExtractionPortal x = new ExtractionPortal(p);
        x.onKill(25, 0);
        assertEquals(1.0, x.charge(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> x.onKill(0, 0));
        assertThrows(IllegalArgumentException.class, () -> x.onKill(-1, 0));
    }

    @Test
    void onlyAnOpenPortalLetsPlayersEscape() {
        ExtractionPortal x = new ExtractionPortal(p);
        assertFalse(x.escape(a, 0));
        x.onKill(10, 0);
        assertTrue(x.escape(a, 100));
        assertTrue(x.escaped().contains(a));
        assertTrue(x.escape(a, 150), "escaping twice is harmless");
        assertEquals(1, x.escaped().size());
    }

    @Test
    void theWindowClosesAndTheNextChargeCostsMore() {
        ExtractionPortal x = new ExtractionPortal(p);
        x.onKill(10, 0);
        x.tick(399);
        assertEquals(ExtractionPortal.State.OPEN, x.state());
        x.tick(400);
        assertEquals(ExtractionPortal.State.CHARGING, x.state());
        assertEquals(0.0, x.charge(), 1e-9);
        assertFalse(x.escape(a, 401));
        x.onKill(10, 401);
        assertEquals(ExtractionPortal.State.CHARGING, x.state(), "now needs 15");
        x.onKill(5, 402);
        assertEquals(ExtractionPortal.State.OPEN, x.state());
        assertEquals(2, x.timesOpened());
    }

    @Test
    void killsDuringTheOpenWindowAreIgnored() {
        ExtractionPortal x = new ExtractionPortal(p);
        x.onKill(10, 0);
        x.onKill(50, 10);
        x.tick(400);
        assertEquals(0.0, x.charge(), 1e-9, "the window's kills do not pre-charge the next opening");
    }

    @Test
    void closingTheRunStopsTheMachine() {
        ExtractionPortal x = new ExtractionPortal(p);
        x.onKill(10, 0);
        x.close();
        assertEquals(ExtractionPortal.State.CLOSED, x.state());
        assertFalse(x.escape(a, 10));
        x.onKill(10, 20);
        x.tick(1000);
        assertEquals(ExtractionPortal.State.CLOSED, x.state());
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExtractionPortal.Params(0, 400, 50));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionPortal.Params(10, 0, 50));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionPortal.Params(10, 400, -1));
    }
}
