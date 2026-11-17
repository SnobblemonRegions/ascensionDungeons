package com.ascensiondungeons.run;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpawnTableTest {
    @Test
    void emptyOrNullPicksNothing() {
        assertNull(SpawnTable.pick(List.of(), new Random(1)));
        assertNull(SpawnTable.pick(null, new Random(1)));
    }

    @Test
    void zeroWeightNeverWins() {
        var a = new SpawnTable.Entry("a", 0, 5, 5);
        var b = new SpawnTable.Entry("b", 3, 5, 5);
        Random r = new Random(7);
        for (int i = 0; i < 500; i++) assertSame(b, SpawnTable.pick(List.of(a, b), r));
        assertNull(SpawnTable.pick(List.of(a), r));
    }

    @Test
    void pickFollowsTheWeights() {
        var a = new SpawnTable.Entry("a", 1, 5, 5);
        var b = new SpawnTable.Entry("b", 9, 5, 5);
        Random r = new Random(3);
        int bs = 0;
        for (int i = 0; i < 10_000; i++) if (SpawnTable.pick(List.of(a, b), r) == b) bs++;
        assertTrue(bs > 8700 && bs < 9300, "b picked " + bs + " of 10000");
    }

    @Test
    void levelStaysInRangeAndToleratesAReversedRange() {
        Random r = new Random(5);
        var e = new SpawnTable.Entry("x", 1, 10, 14);
        var rev = new SpawnTable.Entry("y", 1, 14, 10);
        for (int i = 0; i < 300; i++) {
            int l = SpawnTable.level(e, r);
            assertTrue(l >= 10 && l <= 14);
            int m = SpawnTable.level(rev, r);
            assertTrue(m >= 10 && m <= 14);
        }
        assertEquals(7, SpawnTable.level(new SpawnTable.Entry("z", 1, 7, 7), r));
        assertEquals(1, SpawnTable.level(new SpawnTable.Entry("z", 1, -4, 0), r));
    }
}
