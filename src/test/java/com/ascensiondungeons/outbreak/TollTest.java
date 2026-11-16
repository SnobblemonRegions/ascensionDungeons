package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TollTest {
    private static int sum(int[] a) { int s = 0; for (int x : a) s += x; return s; }

    @Test
    void aBigStackLosesItsShare() {
        Toll.Plan p = Toll.plan(new int[] {64}, 0, 35, 1);
        assertEquals(22, p.itemsLost());
        assertArrayEquals(new int[] {22}, p.lostPerKind());
    }

    @Test
    void twentySingleItemsLoseSevenOfThem() {
        int[] kinds = new int[20];
        java.util.Arrays.fill(kinds, 1);
        Toll.Plan p = Toll.plan(kinds, 0, 35, 9);
        assertEquals(7, p.itemsLost());
        for (int lost : p.lostPerKind()) assertTrue(lost == 0 || lost == 1);
    }

    @Test
    void theTotalIsTheShareOfAllItemsWhateverTheKinds() {
        for (long seed = 0; seed < 50; seed++) {
            int[] kinds = {1, 1, 3, 10, 64, 2, 1, 7};
            Toll.Plan p = Toll.plan(kinds, 0, 35, seed);
            assertEquals(Math.round(89 * 0.35), p.itemsLost(), "seed " + seed);
            assertEquals(p.itemsLost(), sum(p.lostPerKind()));
            for (int i = 0; i < kinds.length; i++) assertTrue(p.lostPerKind()[i] <= kinds[i]);
        }
    }

    @Test
    void anyLootAtAllCostsAtLeastOneItem() {
        assertEquals(1, Toll.plan(new int[] {1}, 0, 35, 3).itemsLost());
        assertEquals(1, Toll.plan(new int[] {1, 1}, 0, 35, 3).itemsLost());
        assertEquals(0, Toll.plan(new int[] {}, 0, 35, 3).itemsLost());
        assertEquals(0, Toll.plan(new int[] {5, 5}, 0, 0, 3).itemsLost());
    }

    @Test
    void theSameSeedAndLootAlwaysGiveTheSamePlan() {
        int[] kinds = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        assertArrayEquals(Toll.plan(kinds, 0, 35, 42).lostPerKind(), Toll.plan(kinds, 0, 35, 42).lostPerKind());
        boolean differs = false;
        for (long seed = 0; seed < 20 && !differs; seed++)
            differs = !java.util.Arrays.equals(Toll.plan(kinds, 0, 35, seed).lostPerKind(), Toll.plan(kinds, 0, 35, 42).lostPerKind());
        assertTrue(differs, "the seed decides which single items go");
    }

    @Test
    void splittingAStackDoesNotChangeWhatItCosts() {
        // The caller groups by kind, so 64 bread is one kind however it is spread across slots; the kind's loss is fixed.
        assertEquals(Toll.plan(new int[] {64, 1}, 0, 35, 5).lostPerKind()[0], Toll.plan(new int[] {64, 1}, 0, 35, 77).lostPerKind()[0]);
    }

    @Test
    void winsAreRoundedToTheNearest() {
        assertEquals(0, Toll.winsLost(0, 35));
        assertEquals(0, Toll.winsLost(1, 35));
        assertEquals(1, Toll.winsLost(2, 35));
        assertEquals(1, Toll.winsLost(4, 35));
        assertEquals(2, Toll.winsLost(5, 35));
        assertEquals(4, Toll.winsLost(10, 35));
        assertEquals(3, Toll.plan(new int[] {}, 8, 35, 1).winsLost());
    }

    @Test
    void theHaulForfeitsOnlyUnbankedWins() {
        Haul h = new Haul();
        UUID a = UUID.randomUUID();
        h.add(a, Haul.Kind.ARENA_WIN, 2);
        h.bank(a);
        h.add(a, Haul.Kind.ARENA_WIN, 5);
        assertEquals(5, h.unbankedWins(a));
        assertEquals(2, h.forfeitWins(a, Toll.winsLost(h.unbankedWins(a), 35)));
        assertEquals(3, h.unbankedWins(a));
        assertEquals(5, h.settle(a, Haul.Exit.EXTRACTED).arenaWins(), "2 banked + 3 left");
        assertEquals(3, h.forfeitWins(a, 99), "never more than is unbanked");
        assertEquals(2, h.settle(a, Haul.Exit.EXTRACTED).arenaWins());
        assertEquals(0, h.forfeitWins(UUID.randomUUID(), 3));
    }

    @Test
    void badInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Toll.plan(new int[] {1}, 0, 101, 1));
        assertThrows(IllegalArgumentException.class, () -> Toll.plan(new int[] {-1}, 0, 35, 1));
    }
}
