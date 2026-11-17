package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DepthBandsTest {
    @Test
    void aMazeIsCutIntoThirdsByDepth() {
        // farthest room at depth 8: 0-2 outer, 3-5 middle, 6-8 deep
        int[] want = {0, 0, 0, 1, 1, 1, 2, 2, 2};
        for (int d = 0; d <= 8; d++) assertEquals(want[d], DepthBands.band(d, 8), "depth " + d);
        assertEquals(DepthBands.OUTER, DepthBands.band(0, 12), "the entrance is always outer");
        assertEquals(DepthBands.DEEP, DepthBands.band(12, 12), "the farthest room is always deep");
        assertEquals(DepthBands.OUTER, DepthBands.band(1, 1), "too shallow to split");
        assertEquals(DepthBands.DEEP, DepthBands.band(2, 2));
        assertEquals(DepthBands.DEEP, DepthBands.band(50, 8), "never past deep");
    }

    @Test
    void deeperIsRicherAndFiercerAtEveryStep() {
        for (int b = 0; b < 2; b++) {
            assertTrue(DepthBands.chestLuck(b + 1) > DepthBands.chestLuck(b));
            assertTrue(DepthBands.chestWeight(b + 1) > DepthBands.chestWeight(b));
            assertTrue(DepthBands.extraEnemies(b + 1) > DepthBands.extraEnemies(b));
            assertTrue(DepthBands.eliteBonus(b + 1) > DepthBands.eliteBonus(b));
            assertTrue(DepthBands.levelBonus(b + 1) > DepthBands.levelBonus(b));
        }
        assertEquals(0, DepthBands.chestLuck(0));
        assertEquals(0, DepthBands.extraEnemies(0), "the outer band is no harder than the dungeon above");
        assertEquals(0.0, DepthBands.eliteBonus(0));
    }

    @Test
    void everyTypeNamesItsThreeBandsAndOthersGetPlainNames() {
        for (String t : new String[] {"normal", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark",
                "steel", "fairy", "fire"}) {
            String a = DepthBands.name(t, 0), b = DepthBands.name(t, 1), c = DepthBands.name(t, 2);
            assertTrue(a.startsWith("The ") && b.startsWith("The ") && c.startsWith("The "), t);
            assertTrue(!a.equals(b) && !b.equals(c) && !a.equals(c), t);
            assertFalse(a.equals("The Outer Depths"), t + " has its own names");
        }
        assertEquals("The Abyssal Cistern", DepthBands.name("water", 2));
        assertEquals("The Outer Depths", DepthBands.name("halloween_graveyard", 0));
        assertEquals("The Deep", DepthBands.name("nope", 9));
        assertEquals("Middle", DepthBands.label(1));
    }

    @Test
    void chestsDeepDownAreRarerOverManyRolls() {
        java.util.Random rng = new java.util.Random(1);
        double[] mean = new double[3];
        for (int band = 0; band < 3; band++) {
            int sum = 0;
            for (int i = 0; i < 20000; i++) sum += ChestRarity.roll(rng, DepthBands.chestLuck(band)).ordinal();
            mean[band] = sum / 20000.0;
        }
        assertTrue(mean[1] > mean[0] + 0.05 && mean[2] > mean[1] + 0.1, java.util.Arrays.toString(mean));
    }
}
