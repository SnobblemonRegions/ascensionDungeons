package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiveSettlementTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test
    void playersWithTheSameHaulShareOneCall() {
        Map<UUID, Haul.Settlement> m = new LinkedHashMap<>();
        m.put(a, new Haul.Settlement(3, 2, true));
        m.put(b, new Haul.Settlement(3, 5, true));
        List<DiveSettlement.Call> calls = DiveSettlement.plan(m, 4);
        assertEquals(1, calls.size());
        assertEquals(List.of(a, b), calls.get(0).players());
        assertEquals(3, calls.get(0).arenaWins());
        assertTrue(calls.get(0).bossDefeated());
        assertEquals(4, calls.get(0).tier());
    }

    @Test
    void differentHaulsGetSeparateCalls() {
        Map<UUID, Haul.Settlement> m = new LinkedHashMap<>();
        m.put(a, new Haul.Settlement(3, 0, false));
        m.put(b, new Haul.Settlement(1, 0, false));
        m.put(c, new Haul.Settlement(3, 0, true));
        assertEquals(3, DiveSettlement.plan(m, 3).size());
    }

    @Test
    void aPlayerWithNothingBankedIsNotPaid() {
        Map<UUID, Haul.Settlement> m = new LinkedHashMap<>();
        m.put(a, new Haul.Settlement(0, 4, false));
        m.put(b, new Haul.Settlement(2, 0, false));
        List<DiveSettlement.Call> calls = DiveSettlement.plan(m, 3);
        assertEquals(1, calls.size());
        assertEquals(List.of(b), calls.get(0).players());
    }

    @Test
    void arenaWinsAreCappedAtWhatTheLibraryAccepts() {
        List<DiveSettlement.Call> calls = DiveSettlement.plan(Map.of(a, new Haul.Settlement(40, 0, false)), 3);
        assertEquals(DiveSettlement.MAX_ARENA_WINS, calls.get(0).arenaWins());
    }

    @Test
    void aBossAloneStillPays() {
        assertEquals(1, DiveSettlement.plan(Map.of(a, new Haul.Settlement(0, 0, true)), 3).size());
    }

    @Test
    void theTierMustBeInRange() {
        assertThrows(IllegalArgumentException.class, () -> DiveSettlement.plan(Map.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> DiveSettlement.plan(Map.of(), 11));
    }
}
