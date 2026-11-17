package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WavePlannerTest {
    private final WavePlanner w = new WavePlanner(2, 1, 1, 14);

    @Test
    void growsWithStageAndParty() {
        assertEquals(2, w.count(0, 1, 0));
        assertEquals(5, w.count(3, 1, 0));
        assertEquals(8, w.count(3, 4, 0));
    }

    @Test
    void neverPassesTheCapOnEnemiesAlive() {
        assertEquals(4, w.count(5, 4, 10));
        assertEquals(0, w.count(5, 4, 14));
        assertEquals(0, w.count(5, 4, 99));
    }

    @Test
    void anEmptyDungeonGetsNoWave() { assertEquals(0, w.count(5, 0, 0)); }

    @Test
    void invalidSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WavePlanner(-1, 1, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new WavePlanner(1, 1, 1, 0));
    }
}
