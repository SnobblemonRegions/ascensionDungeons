package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.outbreak.Recap.Facts;
import com.ascensiondungeons.outbreak.Recap.Way;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecapTest {
    private static Facts facts(Way way, int stage, int chests, boolean depths, int kept, int lost, int winsKept, int winsLost, boolean banked, String by, boolean spell) {
        return new Facts(way, stage, OUTBREAK[stage], 754, chests, depths, kept, lost, winsKept, winsLost, banked, by, spell, 35, false);
    }

    private static Facts softened(String by, boolean spell) {
        return new Facts(Way.KNOCKED_OUT, 2, OUTBREAK[2], 754, 4, true, 6, 5, 1, 1, false, by, spell, 35, true);
    }

    @Test
    void aSoftenedKnockOutSaysSoAndWarnsAboutTheNext() {
        List<String> lines = Recap.lines("The Drowned Reservoir", softened("Elite Golduck", false));
        assertEquals("You were knocked out by Elite Golduck. It was your first, so you kept half of what was unbanked.", lines.get(1));
        assertEquals("Kept: 6 items, 1 win. Lost: 5 items, 1 win.", lines.get(3));
        assertEquals("Tip: Next time a knock-out takes everything unbanked. Reach a checkpoint or the portal before you push on.", lines.get(4));
        assertEquals("You were knocked out. It was your first, so you kept half of what was unbanked.", Recap.ended(softened(null, false)));
        // by a spell: the ring is still worth saying
        assertTrue(Recap.tip(softened("Whirlpool", true)).startsWith("That was a spell: step out of the ring on the ground. Next time"));
        // an ordinary knock-out says nothing of the kind
        assertFalse(Recap.ended(facts(Way.KNOCKED_OUT, 2, 4, true, 0, 5, 0, 1, false, "Golduck", false)).contains("your first"));
    }

    private static final String[] OUTBREAK = {"Calm", "Restless", "Agitated", "Swarming", "Rampage", "Mass Outbreak"};

    @Test
    void theRecapSaysHowItEndedHowFarItGotAndWhatWasKeptAndLost() {
        List<String> lines = Recap.lines("The Drowned Reservoir", facts(Way.TOLL, 2, 5, true, 13, 7, 2, 1, false, null, false));
        assertEquals(5, lines.size());
        assertEquals("Dive recap: The Drowned Reservoir", lines.get(0));
        assertEquals("You extracted by paying the toll.", lines.get(1));
        assertEquals("Outbreak: stage 2 (Agitated) after 12m 34s. Chests opened: 5.", lines.get(2));
        assertEquals("Kept: 13 items, 2 wins. Lost: 7 items, 1 win.", lines.get(3));
        assertTrue(lines.get(4).startsWith("Tip: "));
        // singulars, and saying so when the Depths were never reached
        List<String> one = Recap.lines("X", facts(Way.KNOCKED_OUT, 0, 1, false, 1, 0, 1, 0, true, "Elite Golduck", false));
        assertEquals("You were knocked out by Elite Golduck.", one.get(1));
        assertEquals("Outbreak: stage 0 (Calm) after 12m 34s. Chests opened: 1. You did not go down to the Depths.", one.get(2));
        assertEquals("Kept: 1 item, 1 win. Lost: 0 items, 0 wins.", one.get(3));
        assertEquals("You were knocked out.", Recap.ended(facts(Way.KNOCKED_OUT, 0, 0, false, 0, 0, 0, 0, false, null, false)));
        assertEquals("0m 05s", Recap.clock(5));
        assertEquals("61m 01s", Recap.clock(3661));
    }

    @Test
    void everyWayOutHasItsOwnSentence() {
        for (Way way : Way.values()) {
            String s = Recap.ended(facts(way, 1, 2, true, 3, 0, 1, 0, false, null, false));
            assertFalse(s.isBlank(), way.name());
            assertFalse(Recap.tip(facts(way, 1, 2, true, 3, 0, 1, 0, false, null, false)).isBlank(), way.name());
        }
    }

    @Test
    void theTipComesFromWhatHappened() {
        // knocked out by a spell: the ring
        assertTrue(Recap.tip(facts(Way.KNOCKED_OUT, 1, 3, true, 0, 9, 0, 1, false, "Whirlpool", true)).contains("ring on the ground"));
        // knocked out carrying loot with nothing ever banked: banking
        assertTrue(Recap.tip(facts(Way.KNOCKED_OUT, 1, 3, true, 0, 9, 0, 1, false, "Golduck", false)).contains("Nothing was banked"));
        // knocked out late, having banked: leaving earlier
        assertTrue(Recap.tip(facts(Way.KNOCKED_OUT, 4, 3, true, 5, 9, 2, 1, true, "Golduck", false)).contains("still inside at stage 4"));
        // knocked out with nothing to lose: no scolding
        assertTrue(Recap.tip(facts(Way.KNOCKED_OUT, 1, 3, true, 5, 0, 2, 0, true, "Golduck", false)).contains("lost nothing"));
        assertTrue(Recap.tip(facts(Way.KNOCKED_OUT, 1, 3, true, 5, 2, 2, 0, true, "Golduck", false)).contains("back off and heal"));
        // the toll, early: the gatekeeper was beatable; later: bank on the way
        assertTrue(Recap.tip(facts(Way.TOLL, 0, 3, true, 6, 3, 1, 0, false, null, false)).contains("weakest this early"));
        assertTrue(Recap.tip(facts(Way.TOLL, 3, 3, true, 6, 3, 1, 0, false, null, false)).contains("35% of what was unbanked"));
        // the free one: what it costs from now on
        assertTrue(Recap.tip(facts(Way.FREE, 0, 3, true, 9, 0, 1, 0, false, null, false)).contains("costs 35%"));
        // out with everything but never went down: the Depths; at a high stage: a harder dungeon
        assertTrue(Recap.tip(facts(Way.GATEKEEPER, 1, 3, false, 9, 0, 1, 0, false, null, false)).contains("down in the Depths"));
        assertTrue(Recap.tip(facts(Way.GATEKEEPER, 4, 3, true, 9, 0, 1, 0, false, null, false)).contains("higher-tier dungeon"));
        assertTrue(Recap.tip(facts(Way.GATEKEEPER, 1, 3, true, 9, 0, 1, 0, false, null, false)).contains("a stage longer"));
        // left with nothing at stake
        assertTrue(Recap.tip(facts(Way.EMPTY_HANDED, 0, 0, false, 0, 0, 0, 0, false, null, false)).contains("opened no chests"));
        assertTrue(Recap.tip(facts(Way.EMPTY_HANDED, 1, 2, false, 4, 0, 1, 0, true, null, false)).contains("already banked"));
        assertTrue(Recap.tip(facts(Way.LEFT, 1, 2, false, 0, 4, 0, 1, false, null, false)).contains("extraction portal"));
        assertTrue(Recap.tip(facts(Way.TIMED_OUT, 5, 2, true, 0, 4, 0, 1, false, null, false)).contains("time limit"));
    }

    @Test
    void theRecapUsesTheProjectsWords() {
        for (Way way : Way.values()) for (int stage = 0; stage <= 5; stage++) for (boolean spell : new boolean[] {false, true}) {
            for (String line : Recap.lines("X", facts(way, stage, 2, stage % 2 == 0, 3, stage, 1, 1, stage > 2, "Golduck", spell))) {
                String low = line.toLowerCase();
                assertFalse(low.contains("death") || low.contains("died") || low.contains("threat") || low.contains("boss fight"), line);
                assertTrue(line.length() <= 220, line);
            }
        }
    }

    @Test
    void theRecapSaysHowDeepThePlayerGotAndWhatTheyBeat() {
        Facts deep = new Facts(Way.GATEKEEPER, 3, "Swarming", 600, 7, true, 20, 0, 4, 0, true, null, false, 35, false, "The Abyssal Cistern", 2, 2);
        List<String> lines = Recap.lines("The Drowned Reservoir", deep);
        assertEquals("Outbreak: stage 3 (Swarming) after 10m 00s. Chests opened: 7. Deepest: The Abyssal Cistern. Nests and Alphas beaten: 2.", lines.get(2));
        // never reached the deep band: push further
        Facts shallow = new Facts(Way.GATEKEEPER, 1, "Restless", 300, 3, true, 9, 0, 1, 0, false, null, false, 35, false, "The Sunken Halls", 0, 0);
        assertTrue(Recap.tip(shallow).contains("never reached the deepest band"));
        // reached it but beat nothing there: the sealed chests
        Facts timid = new Facts(Way.CLEARED, 2, "Agitated", 300, 3, true, 9, 0, 1, 0, false, null, false, 35, false, "The Abyssal Cistern", 2, 0);
        assertTrue(Recap.tip(timid).contains("Nests and Alphas"));
        // the old form still reads as before
        assertEquals("Outbreak: stage 2 (Agitated) after 12m 34s. Chests opened: 4.", Recap.lines("X", facts(Way.TOLL, 2, 4, true, 3, 1, 0, 0, false, null, false)).get(2));
    }
}
