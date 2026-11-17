package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ChestRarityTest {
    private static Map<ChestRarity, Integer> sample(int luck) {
        Map<ChestRarity, Integer> n = new EnumMap<>(ChestRarity.class);
        Random r = new Random(11);
        for (int i = 0; i < 20_000; i++) n.merge(ChestRarity.roll(r, luck), 1, Integer::sum);
        return n;
    }

    @Test
    void withoutLuckCommonIsTheUsualChestAndEpicIsRare() {
        Map<ChestRarity, Integer> n = sample(0);
        assertTrue(n.get(ChestRarity.COMMON) > 9000 && n.get(ChestRarity.COMMON) < 11000, n.toString());
        assertTrue(n.get(ChestRarity.EPIC) > 600 && n.get(ChestRarity.EPIC) < 1400, n.toString());
    }

    @Test
    void luckMovesRollsUpwardButNeverRemovesCommon() {
        Map<ChestRarity, Integer> base = sample(0), lucky = sample(3), max = sample(99);
        assertTrue(lucky.get(ChestRarity.EPIC) > base.get(ChestRarity.EPIC));
        assertTrue(lucky.get(ChestRarity.COMMON) < base.get(ChestRarity.COMMON));
        assertTrue(max.getOrDefault(ChestRarity.COMMON, 0) > 0, "common still appears at maximum luck");
    }

    @Test
    void upgradingStopsAtEpic() {
        assertEquals(ChestRarity.UNCOMMON, ChestRarity.COMMON.upgraded());
        assertEquals(ChestRarity.EPIC, ChestRarity.EPIC.upgraded());
        assertEquals(ChestRarity.EPIC, ChestRarity.COMMON.upgraded(9));
        assertEquals(ChestRarity.RARE, ChestRarity.COMMON.upgraded(2));
    }

    @Test
    void everyRarityHasItsOwnTableColourAndModel() {
        java.util.Set<Object> seen = new java.util.HashSet<>();
        for (ChestRarity r : ChestRarity.values()) {
            assertTrue(seen.add(r.lootTable()));
            assertTrue(seen.add(r.colour()));
            assertTrue(seen.add(r.modelId()));
            assertTrue(seen.add(r.floorBlock()));
            assertTrue(r.lootTable().startsWith("ascensiondungeons:chests/rarity/"));
        }
    }
}
