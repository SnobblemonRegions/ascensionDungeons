package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HaulTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void anExtractedPlayerKeepsEverythingBankedOrNot() {
        Haul h = new Haul();
        h.add(a, Haul.Kind.ARENA_WIN, 1);
        h.bank(a);
        h.add(a, Haul.Kind.ARENA_WIN, 2);
        h.add(a, Haul.Kind.CHEST, 3);
        Haul.Settlement s = h.settle(a, Haul.Exit.EXTRACTED);
        assertEquals(3, s.arenaWins());
        assertEquals(3, s.chests());
        assertFalse(s.bossDefeated());
    }

    @Test
    void aFailureKeepsOnlyWhatWasBanked() {
        Haul h = new Haul();
        h.add(a, Haul.Kind.ARENA_WIN, 2);
        h.add(a, Haul.Kind.CHEST, 1);
        h.bank(a);
        h.add(a, Haul.Kind.ARENA_WIN, 3);
        h.add(a, Haul.Kind.CHEST, 4);
        for (Haul.Exit exit : new Haul.Exit[] {Haul.Exit.DIED, Haul.Exit.TIMED_OUT, Haul.Exit.LEFT}) {
            Haul.Settlement s = h.settle(a, exit);
            assertEquals(2, s.arenaWins(), exit.name());
            assertEquals(1, s.chests(), exit.name());
        }
    }

    @Test
    void aFailureWithNothingBankedPaysNothing() {
        Haul h = new Haul();
        h.add(a, Haul.Kind.ARENA_WIN, 5);
        assertTrue(h.settle(a, Haul.Exit.DIED).isEmpty());
    }

    @Test
    void theBossCountsOnceAndOnlyWhenBankedOrExtracted() {
        Haul h = new Haul();
        h.add(a, Haul.Kind.BOSS, 1);
        h.add(a, Haul.Kind.BOSS, 1);
        assertTrue(h.settle(a, Haul.Exit.EXTRACTED).bossDefeated());
        assertFalse(h.settle(a, Haul.Exit.DIED).bossDefeated());
        h.bank(a);
        assertTrue(h.settle(a, Haul.Exit.DIED).bossDefeated());
    }

    @Test
    void playersAreIndependent() {
        Haul h = new Haul();
        h.add(a, Haul.Kind.ARENA_WIN, 4);
        h.add(b, Haul.Kind.ARENA_WIN, 1);
        h.bank(b);
        assertEquals(4, h.settle(a, Haul.Exit.EXTRACTED).arenaWins());
        assertEquals(0, h.settle(a, Haul.Exit.DIED).arenaWins());
        assertEquals(1, h.settle(b, Haul.Exit.DIED).arenaWins());
    }

    @Test
    void addSharedCreditsEveryoneAndUnknownPlayersAreEmpty() {
        Haul h = new Haul();
        h.addShared(java.util.List.of(a, b), Haul.Kind.ARENA_WIN, 1);
        assertEquals(1, h.settle(a, Haul.Exit.EXTRACTED).arenaWins());
        assertEquals(1, h.settle(b, Haul.Exit.EXTRACTED).arenaWins());
        assertTrue(h.settle(UUID.randomUUID(), Haul.Exit.EXTRACTED).isEmpty());
    }

    @Test
    void negativeAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Haul().add(a, Haul.Kind.CHEST, -1));
    }

    @Test
    void settlingDoesNotConsumeTheLedger() {
        Haul h = new Haul();
        h.add(a, Haul.Kind.ARENA_WIN, 2);
        assertEquals(2, h.settle(a, Haul.Exit.EXTRACTED).arenaWins());
        assertEquals(2, h.settle(a, Haul.Exit.EXTRACTED).arenaWins(), "the caller records the payout once, by operation id");
    }
}
