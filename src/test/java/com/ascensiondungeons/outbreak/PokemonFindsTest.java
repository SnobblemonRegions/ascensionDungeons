package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.outbreak.PokemonFinds.Find;
import com.ascensiondungeons.outbreak.PokemonFinds.Lists;
import com.ascensiondungeons.outbreak.PokemonFinds.Source;
import com.ascensiondungeons.outbreak.PokemonFinds.Tier;
import com.ascensiondungeons.outbreak.Tactics.Difficulty;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PokemonFindsTest {
    private static Lists full() {
        return PokemonFinds.lists(Map.of("standard", List.of("psyduck", "wooper"), "starter", List.of("squirtle"), "pseudo", List.of("dratini"),
                "legendary", List.of("articuno"), "mythical", List.of("mew")), 12, 30, Map.of("starterLevel", 10, "pseudoLevel", 10, "legendaryLevel", 40, "mythicalLevel", 40));
    }

    @Test
    void tierOddsMatchTheDesignAndLegendariesNeedNightmareInTheDeepBand() {
        assertArrayEquals(new double[] {100, 0, 0, 0, 0}, PokemonFinds.weights(Difficulty.CASUAL, 2));
        assertArrayEquals(new double[] {90, 10, 0, 0, 0}, PokemonFinds.weights(Difficulty.NORMAL, 2));
        assertArrayEquals(new double[] {75, 20, 5, 0, 0}, PokemonFinds.weights(Difficulty.HARD, 2));
        assertArrayEquals(new double[] {65, 23, 10, 1.8, 0.2}, PokemonFinds.weights(Difficulty.NIGHTMARE, 2), 1e-9);
        // Nightmare outside the Deep band: no legendary or mythical; their share goes to standard
        double[] middle = PokemonFinds.weights(Difficulty.NIGHTMARE, 1);
        assertEquals(0, middle[3]);
        assertEquals(0, middle[4]);
        assertEquals(67, middle[0], 1e-9);
        for (Difficulty d : Difficulty.values()) for (int band = 0; band <= 2; band++) {
            double sum = 0;
            for (double x : PokemonFinds.weights(d, band)) sum += x;
            assertEquals(100, sum, 1e-9, d + " band " + band);
        }
    }

    @Test
    void theBetterTheChestTheLikelierAPokemonAndTheSetPiecesBest() {
        assertTrue(PokemonFinds.chance(Source.CHEST, ChestRarity.UNCOMMON) > PokemonFinds.chance(Source.CHEST, ChestRarity.COMMON));
        assertTrue(PokemonFinds.chance(Source.CHEST, ChestRarity.RARE) > PokemonFinds.chance(Source.CHEST, ChestRarity.UNCOMMON));
        assertTrue(PokemonFinds.chance(Source.CHEST, ChestRarity.EPIC) > PokemonFinds.chance(Source.CHEST, ChestRarity.RARE));
        assertTrue(PokemonFinds.chance(Source.NEST_CACHE, ChestRarity.RARE) > PokemonFinds.chance(Source.CHEST, ChestRarity.EPIC));
        assertTrue(PokemonFinds.chance(Source.ALPHA_HOARD, ChestRarity.EPIC) > PokemonFinds.chance(Source.NEST_CACHE, ChestRarity.EPIC));
        assertTrue(PokemonFinds.chance(Source.ALPHA_HOARD, ChestRarity.EPIC) < 0.1, "even the best chest is not a sure thing");
    }

    @Test
    void casualFindsOnlyStandardAndEveryFindIsFromTheRightListAtTheRightLevel() {
        Random rng = new Random(3);
        Lists lists = full();
        int finds = 0;
        for (int i = 0; i < 200_000; i++) {
            Find f = PokemonFinds.roll(rng, Source.ALPHA_HOARD, ChestRarity.EPIC, Difficulty.CASUAL, 2, lists);
            if (f == null) continue;
            finds++;
            assertEquals(Tier.STANDARD, f.tier());
            assertTrue(lists.of(Tier.STANDARD).contains(f.species()));
            assertTrue(f.level() >= 12 && f.level() <= 30);
        }
        assertEquals(0.08, finds / 200_000.0, 0.005, "the hoard's chance holds");
        Find starter = null;
        for (int i = 0; i < 100_000 && starter == null; i++) {
            Find f = PokemonFinds.roll(rng, Source.ALPHA_HOARD, ChestRarity.EPIC, Difficulty.NORMAL, 0, lists);
            if (f != null && f.tier() == Tier.STARTER) starter = f;
        }
        assertNotNull(starter);
        assertEquals("squirtle", starter.species());
        assertEquals(10, starter.level());
    }

    /** The headline promise: over many Nightmare finds in the Deep band, legendaries are a rare sight and mythicals rarer still. */
    @Test
    void theRarestAreRareEvenOnNightmare() {
        Random rng = new Random(11);
        Lists lists = full();
        Map<Tier, Integer> count = new EnumMap<>(Tier.class);
        int finds = 0;
        while (finds < 400_000) {
            Find f = PokemonFinds.roll(rng, Source.ALPHA_HOARD, ChestRarity.EPIC, Difficulty.NIGHTMARE, 2, lists);
            if (f == null) continue;
            finds++;
            count.merge(f.tier(), 1, Integer::sum);
        }
        assertEquals(0.018, count.get(Tier.LEGENDARY) / (double) finds, 0.002);
        assertEquals(0.002, count.get(Tier.MYTHICAL) / (double) finds, 0.0006);
        assertEquals(0.10, count.get(Tier.PSEUDO) / (double) finds, 0.004);
        // and never outside the Deep band, or below Nightmare
        for (int i = 0; i < 300_000; i++) {
            Find a = PokemonFinds.roll(rng, Source.ALPHA_HOARD, ChestRarity.EPIC, Difficulty.NIGHTMARE, 1, lists);
            Find b = PokemonFinds.roll(rng, Source.ALPHA_HOARD, ChestRarity.EPIC, Difficulty.HARD, 2, lists);
            for (Find f : new Find[] {a, b}) if (f != null) assertTrue(f.tier().ordinal() < Tier.LEGENDARY.ordinal(), String.valueOf(f));
        }
    }

    /**
     * What a player feels: in a dive of about twenty chests spread over the bands, about one Pokémon every few dives, and on
     * Nightmare a legendary roughly once in hundreds of dives.
     */
    @Test
    void perDiveOddsAreAboutOneFindEveryFewDives() {
        // a typical dive's chests: 8 common, 6 uncommon, 4 rare, 2 epic, plus a nest cache and an alpha hoard; half of it deep
        double perDive = 8 * PokemonFinds.chance(Source.CHEST, ChestRarity.COMMON) + 6 * PokemonFinds.chance(Source.CHEST, ChestRarity.UNCOMMON)
                + 4 * PokemonFinds.chance(Source.CHEST, ChestRarity.RARE) + 2 * PokemonFinds.chance(Source.CHEST, ChestRarity.EPIC)
                + PokemonFinds.chance(Source.NEST_CACHE, ChestRarity.RARE) + PokemonFinds.chance(Source.ALPHA_HOARD, ChestRarity.EPIC);
        assertTrue(perDive > 0.15 && perDive < 0.4, "about one Pokémon every three to six dives: " + perDive);
        double legendaryPerDive = perDive * 0.5 * 0.018;
        assertTrue(1 / legendaryPerDive > 150 && 1 / legendaryPerDive < 600, "a legendary once in hundreds of Nightmare dives: 1 in " + Math.round(1 / legendaryPerDive));
    }

    @Test
    void aTierThisDungeonLacksGivesWayToTheOneBelowNeverToNothing() {
        // a Water-like dungeon: no legendary, no mythical
        Lists noLegends = PokemonFinds.lists(Map.of("standard", List.of("psyduck"), "starter", List.of("squirtle"), "pseudo", List.of("lapras")), 12, 30, Map.of());
        Random rng = new Random(5);
        int pseudo = 0, finds = 0;
        while (finds < 200_000) {
            Find f = PokemonFinds.roll(rng, Source.ALPHA_HOARD, ChestRarity.EPIC, Difficulty.NIGHTMARE, 2, noLegends);
            if (f == null) continue;
            finds++;
            assertTrue(f.tier().ordinal() <= Tier.PSEUDO.ordinal());
            if (f.tier() == Tier.PSEUDO) pseudo++;
        }
        assertEquals(0.12, pseudo / (double) finds, 0.004, "pseudo-legendaries take the legendary and mythical share");
        // no starter list: starters fall to standard
        Lists bare = PokemonFinds.lists(Map.of("standard", List.of("psyduck")), 5, 5, Map.of());
        for (int i = 0; i < 50_000; i++) {
            Find f = PokemonFinds.roll(rng, Source.ALPHA_HOARD, ChestRarity.EPIC, Difficulty.HARD, 2, bare);
            if (f != null) { assertEquals(Tier.STANDARD, f.tier()); assertEquals(5, f.level()); }
        }
        // nothing at all: no find, no error
        Lists empty = PokemonFinds.lists(Map.of(), 5, 5, Map.of());
        for (int i = 0; i < 5_000; i++) assertNull(PokemonFinds.roll(rng, Source.ALPHA_HOARD, ChestRarity.EPIC, Difficulty.NORMAL, 2, empty));
    }

    @Test
    void harderDifficultiesGuaranteeBetterIndividualsAndBigFindsAreAnnounced() {
        assertEquals("common", PokemonFinds.floor(Difficulty.CASUAL));
        assertEquals("uncommon", PokemonFinds.floor(Difficulty.NORMAL));
        assertEquals("rare", PokemonFinds.floor(Difficulty.HARD));
        assertEquals("epic", PokemonFinds.floor(Difficulty.NIGHTMARE));
        assertFalse(PokemonFinds.announcedToParty(Tier.STARTER));
        assertTrue(PokemonFinds.announcedToParty(Tier.PSEUDO));
        assertFalse(PokemonFinds.announcedToServer(Tier.PSEUDO));
        assertTrue(PokemonFinds.announcedToServer(Tier.LEGENDARY) && PokemonFinds.announcedToServer(Tier.MYTHICAL));
        assertEquals("Pseudo-legendary", Tier.PSEUDO.label());
    }
}
