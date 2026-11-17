package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.outbreak.PokemonFinds.Tier;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CapsuleLookTest {
    @Test
    void tiersClimbInStarsAndOnlyTheTopTwoAreBoldGradients() {
        assertEquals("★", CapsuleLook.stars(Tier.STANDARD));
        assertEquals("★★★★★", CapsuleLook.stars(Tier.MYTHICAL));
        for (Tier t : Tier.values()) {
            boolean top = t == Tier.LEGENDARY || t == Tier.MYTHICAL;
            assertEquals(top, CapsuleLook.bold(t), t.name());
            assertEquals(top, CapsuleLook.stops(t).length > 1, t + ": only the top two are gradients");
        }
        Set<Integer> models = new HashSet<>();
        for (Tier t : Tier.values()) models.add(CapsuleLook.modelId(t));
        assertEquals(Tier.values().length, models.size(), "each tier has its own tank");
    }

    /** The point of the design: a find tier never wears an Ascension rarity's colour, so the two kinds of rare can't be confused. */
    @Test
    void findTierColoursStayClearOfAscensionRarityColours() {
        for (Tier t : Tier.values()) {
            if (t == Tier.STANDARD) continue;                      // white is plain text, not a rarity
            for (int c : CapsuleLook.stops(t))
                for (String r : CapsuleLook.RARITIES)
                    assertTrue(distance(c, CapsuleLook.rarityColour(r)) > 40, t + " " + Integer.toHexString(c) + " is too close to " + r);
        }
    }

    @Test
    void theRarityPaletteIsAscensionLibsAndMatchesTheChests() {
        assertEquals(ChestRarity.COMMON.colour(), CapsuleLook.rarityColour("common"));
        assertEquals(ChestRarity.UNCOMMON.colour(), CapsuleLook.rarityColour("uncommon"));
        assertEquals(ChestRarity.RARE.colour(), CapsuleLook.rarityColour("rare"));
        assertEquals(ChestRarity.EPIC.colour(), CapsuleLook.rarityColour("epic"));
        assertEquals("Radiant", CapsuleLook.rarityName("legendary"));
        assertEquals("Ascended", CapsuleLook.rarityName("mythical"));
        assertEquals("Common", CapsuleLook.rarityName("nonsense"), "an unknown id reads as the lowest");
        assertEquals("Rare or better", CapsuleLook.floorText(" RARE "));
    }

    @Test
    void theMeterFillsUpToTheFloorEachInItsOwnColour() {
        List<CapsuleLook.Segment> m = CapsuleLook.meter("rare");
        assertEquals(6, m.size());
        assertEquals(List.of("◆", "◆", "◆", "◇", "◇", "◇"), m.stream().map(CapsuleLook.Segment::text).toList());
        assertEquals(CapsuleLook.rarityColour("uncommon"), m.get(1).colour());
        assertEquals(CapsuleLook.METER_EMPTY, m.get(4).colour());
        assertEquals(1, CapsuleLook.meter("common").stream().filter(s -> s.text().equals("◆")).count());
    }

    @Test
    void aGradientRunsEndToEndAndAFlatColourRepeats() {
        int[] g = CapsuleLook.colours(Tier.LEGENDARY, "Articuno");
        assertEquals(8, g.length);
        assertEquals(0xF2D488, g[0]);
        assertEquals(0xF2D488, g[7]);
        assertNotEquals(g[0], g[3], "the middle is lighter");
        int[] f = CapsuleLook.colours(Tier.STARTER, "Froakie");
        for (int c : f) assertEquals(0x7DE07D, c);
        assertEquals(1, CapsuleLook.colours(Tier.MYTHICAL, "M").length);
        assertEquals(3, CapsuleLook.colours(Tier.MYTHICAL, "★★★").length, "counted by character, not by char unit");
    }

    @Test
    void aClearPokemonIsOnlyLightlyTinted() {
        for (Tier t : Tier.values()) {
            float[] c = CapsuleLook.tint(t);
            assertEquals(4, c.length);
            for (int i = 0; i < 3; i++) assertTrue(c[i] >= 0.8f && c[i] <= 1f, t + " channel " + i + " = " + c[i]);
            assertEquals(1f, c[3]);
        }
    }

    @Test
    void theWordsSayWhereItCameFromAndWhatToDoNow() {
        assertEquals(List.of("The Hoard", "The Elder Wyrm Roost", "Hard · Outbreak stage 3"), CapsuleLook.found("The Hoard", "The Elder Wyrm Roost", "Hard", 3));
        assertEquals(List.of("The Drowned Reservoir", "Normal · Outbreak stage 0"), CapsuleLook.found(null, "The Drowned Reservoir", "Normal", -2));
        assertTrue(CapsuleLook.status(false, "Dratini").get(0).startsWith("⚠ At risk"));
        assertEquals(List.of("▶ Right-click to release Dratini."), CapsuleLook.status(true, "Dratini"));
        assertEquals("Bound to Steve · found 17 Nov", CapsuleLook.bound("Steve", LocalDate.of(2026, 11, 17).toEpochDay()));
        assertEquals("Bound to Steve", CapsuleLook.bound("Steve", 0));
        assertEquals("Mrmime", CapsuleLook.fallbackName("mrmime"));
    }

    private static double distance(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
