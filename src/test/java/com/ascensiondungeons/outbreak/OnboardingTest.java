package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.outbreak.Onboarding.Coach;
import com.ascensiondungeons.outbreak.Onboarding.Goal;
import com.ascensiondungeons.outbreak.Onboarding.Lesson;
import com.ascensiondungeons.outbreak.Onboarding.Pace;
import com.ascensiondungeons.outbreak.Onboarding.Progress;
import java.util.List;
import org.junit.jupiter.api.Test;

class OnboardingTest {
    private static final Pace PACE = Pace.DEFAULT;

    @Test
    void aNewPlayerIsToldEachThingOnceAndNotAllAtOnce() {
        Progress p = new Progress();
        assertTrue(p.rookie() && p.coached());
        Coach c = new Coach(p, PACE, true);
        assertEquals(1, p.dives());
        c.notice(Lesson.WELCOME, 0);
        c.notice(Lesson.OUTBREAK, 0);
        assertEquals(Lesson.WELCOME, c.next(0), "the welcome comes at once");
        assertNull(c.next(1), "and then a pause");
        assertNull(c.next(PACE.gap() - 1));
        assertEquals(Lesson.OUTBREAK, c.next(PACE.gap()));
        // hearing about it again changes nothing
        c.notice(Lesson.WELCOME, 500);
        assertFalse(c.waiting(Lesson.WELCOME));
        assertNull(c.next(5000));
        assertEquals(List.of(Lesson.WELCOME, Lesson.OUTBREAK), c.told());
        assertTrue(p.seen(Lesson.WELCOME) && p.seen(Lesson.OUTBREAK) && !p.seen(Lesson.CHESTS));
        assertFalse(p.coached(), "once welcomed, the opening briefing is the ordinary one");
    }

    @Test
    void somethingHappeningNowJumpsTheQueue() {
        Coach c = new Coach(new Progress(), PACE, true);
        c.notice(Lesson.WELCOME, 0);
        assertEquals(Lesson.WELCOME, c.next(0));
        c.notice(Lesson.CHESTS, 10);
        c.notice(Lesson.SPELLS, 20);
        assertNull(c.next(30), "even an urgent lesson leaves a moment to read the last one");
        assertEquals(Lesson.SPELLS, c.next(PACE.urgentGap()), "the spell ring is explained before the chest");
        assertNull(c.next(PACE.urgentGap() + PACE.gap() - 1));
        assertEquals(Lesson.CHESTS, c.next(PACE.urgentGap() + PACE.gap()));
    }

    @Test
    void aLessonAboutAPlaceIsDroppedOnceThePlayerHasMovedOnAndToldAnotherTime() {
        Progress p = new Progress();
        Coach c = new Coach(p, PACE, true);
        c.notice(Lesson.WELCOME, 0);
        c.next(0);
        c.notice(Lesson.ALTAR, 10);
        c.notice(Lesson.UNBANKED, 10);
        assertEquals(Lesson.UNBANKED, c.next(PACE.gap()), "what the player carries comes first: it is earlier in the book");
        // nothing more is asked for a long while: the altar is far behind by now
        assertNull(c.next(10 + PACE.expiry() + 1));
        assertFalse(c.waiting(Lesson.ALTAR));
        assertFalse(p.seen(Lesson.ALTAR), "it was never told, so the next altar explains itself");
        c.notice(Lesson.ALTAR, 5000);
        assertEquals(Lesson.ALTAR, c.next(5000));
        // the lessons about the player's own state wait as long as it takes
        c.notice(Lesson.CHECKPOINT, 5001);
        assertNull(c.next(5002));
        assertEquals(Lesson.CHECKPOINT, c.next(5001 + PACE.expiry() + 500));
    }

    @Test
    void theGoalsWalkTheLoopOnce() {
        Progress p = new Progress();
        Coach c = new Coach(p, PACE, true);
        assertEquals(Goal.LOOT, c.goal());
        c.enteredDepths();
        assertEquals(Goal.LOOT, c.goal(), "wandering down first is fine: a chest is still the goal");
        c.lootedChest(false);
        assertEquals(Goal.DESCEND, c.goal());
        c.lootedChest(false);
        assertEquals(Goal.DESCEND, c.goal());
        c.enteredDepths();
        assertEquals(Goal.DEEP_LOOT, c.goal());
        c.lootedChest(true);
        assertEquals(Goal.EXTRACT, c.goal());
        c.lootedChest(true);
        c.enteredDepths();
        assertEquals(Goal.EXTRACT, c.goal());
        assertFalse(p.graduated());
        c.extracted();
        assertTrue(p.graduated());
        assertNull(c.goal(), "no Guide bar after graduating");
        assertEquals(1, p.extractions());
    }

    @Test
    void lootingInTheDepthsFirstSkipsStraightToLeaving() {
        Coach c = new Coach(new Progress(), PACE, true);
        c.enteredDepths();
        c.lootedChest(true);
        assertEquals(Goal.EXTRACT, c.goal());
        // a dive with no Depths to walk to never asks for them
        Coach flat = new Coach(new Progress(), PACE, false);
        flat.lootedChest(false);
        assertEquals(Goal.EXTRACT, flat.goal());
    }

    @Test
    void aKnockOutKeepsThePlayerARookieForTheirNextDive() {
        Progress p = new Progress();
        Coach first = new Coach(p, PACE, true);
        first.notice(Lesson.WELCOME, 0);
        first.next(0);
        first.lootedChest(false);
        first.knockedOut();
        assertEquals(Lesson.KNOCKED_OUT, first.tellNow(Lesson.KNOCKED_OUT, 5), "told at once: there is no later in this dive");
        assertNull(first.tellNow(Lesson.KNOCKED_OUT, 6));
        assertEquals(1, p.knockOuts());
        assertTrue(p.rookie());
        Coach second = new Coach(p, PACE, true);
        assertEquals(2, p.dives());
        assertEquals(Goal.LOOT, second.goal(), "the goals start again");
        second.notice(Lesson.WELCOME, 0);
        assertNull(second.next(0), "but nothing already heard is repeated");
    }

    @Test
    void aGraduateIsOnlyToldAboutWhatTheyHaveNeverMet() {
        Progress p = new Progress();
        Coach first = new Coach(p, PACE, true);
        first.notice(Lesson.WELCOME, 0);
        first.next(0);
        first.extracted();
        Coach later = new Coach(p, PACE, true);
        assertNull(later.goal());
        later.notice(Lesson.WELCOME, 0);
        later.notice(Lesson.STRONGBOX, 0);
        assertEquals(Lesson.STRONGBOX, later.next(0));
        assertNull(later.next(10_000));
    }

    @Test
    void coachingCanBeTurnedOffAndStartedOver() {
        Progress p = new Progress();
        p.setOff(true);
        assertFalse(p.rookie() || p.coached());
        Coach c = new Coach(p, PACE, true);
        assertNull(c.goal());
        c.notice(Lesson.WELCOME, 0);
        assertNull(c.next(0));
        assertNull(c.tellNow(Lesson.KNOCKED_OUT, 0));
        assertEquals(0, p.seenBits());
        p.setOff(false);
        Coach on = new Coach(p, PACE, true);
        on.notice(Lesson.WELCOME, 0);
        assertEquals(Lesson.WELCOME, on.next(0));
        on.extracted();
        p.reset();
        assertTrue(p.rookie() && p.coached());
        assertEquals(0, p.dives() + p.extractions() + p.knockOuts());
        assertEquals(0, p.seenBits());
    }

    @Test
    void progressSurvivesBeingStoredAndReadBack() {
        Progress p = new Progress();
        Coach c = new Coach(p, PACE, true);
        assertTrue(c.pollChanged(), "a dive started: worth saving");
        assertFalse(c.pollChanged());
        c.notice(Lesson.CHESTS, 0);
        assertFalse(c.pollChanged(), "nothing to save until it is told");
        c.next(0);
        assertTrue(c.pollChanged());
        c.knockedOut();
        Progress back = new Progress(p.seenBits(), p.dives(), p.extractions(), p.knockOuts(), p.off());
        assertTrue(back.seen(Lesson.CHESTS) && !back.seen(Lesson.WELCOME));
        assertEquals(1, back.dives());
        assertEquals(1, back.knockOuts());
        assertTrue(back.rookie());
    }

    @Test
    void everyLessonHasWordsThatFitAndTheTollIsFilledIn() {
        assertTrue(Lesson.values().length <= 27, "the guide book has three rows of nine");
        assertTrue(Lesson.values().length < 64, "one bit each");
        for (Lesson l : Lesson.values()) {
            assertFalse(l.title().isBlank(), l.name());
            assertTrue(l.title().length() <= 32, l.name() + " title fits a screen title");
            assertEquals(l.weight() == Onboarding.Weight.HEADLINE, !l.subtitle().isEmpty(), l.name() + ": a headline has a subtitle, a tip has none");
            assertTrue(l.subtitle().length() <= 40, l.name());
            List<String> lines = l.lines(35);
            assertTrue(lines.size() >= 1 && lines.size() <= 3, l.name());
            assertTrue(l.lines(35, true, true).size() <= 4, l.name());
            for (String line : lines) {
                assertFalse(line.contains("{"), l.name() + ": " + line);
                assertTrue(line.length() <= 200, l.name());
                // the project's own words, not the ones its glossary avoids
                String low = line.toLowerCase();
                assertFalse(low.contains("death") || low.contains(" die ") && !low.contains("nobody dies") || low.contains("threat") || low.contains("soulbound"), l.name() + ": " + line);
            }
        }
        assertTrue(Lesson.PORTAL.lines(35).get(1).contains("35%"));
        assertTrue(Lesson.PORTAL.lines(20).get(1).contains("20%"));
        for (Goal g : Goal.values()) assertTrue(g.text().length() <= 48, g.name());
    }

    @Test
    void aPlayersFirstExtractionIsFreeAndOnlyTheFirst() {
        Progress p = new Progress();
        assertTrue(p.firstExtractionFree());
        Coach first = new Coach(p, PACE, true);
        first.knockedOut();
        assertTrue(p.firstExtractionFree(), "a knock-out does not use it up");
        // the portal's lesson says so while it is still to come, and only that lesson
        assertEquals(Lesson.FIRST_IS_FREE, Lesson.PORTAL.lines(35, true).get(3));
        assertEquals(3, Lesson.PORTAL.lines(35, false).size());
        assertEquals(Lesson.CHESTS.lines(35), Lesson.CHESTS.lines(35, true));
        Coach second = new Coach(p, PACE, true);
        second.extracted();
        assertFalse(p.firstExtractionFree());
        assertTrue(p.graduated() && p.everExtracted());
        // each way out has its own words
        assertEquals(Lesson.FIRST_EXTRACTION, Onboarding.Exit.FREE.lesson());
        assertEquals(Lesson.TOLL_PAID, Onboarding.Exit.TOLL.lesson());
        assertEquals(Lesson.EXTRACTED, Onboarding.Exit.GATEKEEPER.lesson());
        assertTrue(Lesson.FIRST_EXTRACTION.lines(35).get(1).contains("35%"));
    }

    @Test
    void startingTheTutorialOverDoesNotGiveTheFreeExtractionBack() {
        Progress p = new Progress();
        new Coach(p, PACE, true).extracted();
        p.reset();
        assertTrue(p.rookie(), "taught from the beginning again");
        assertEquals(0, p.extractions());
        assertFalse(p.firstExtractionFree(), "but the toll is the toll");
        // it is not tied to coaching either: a player who turned the tutorial off still has theirs
        Progress quiet = new Progress();
        quiet.setOff(true);
        assertTrue(quiet.firstExtractionFree());
        // saved and read back, and progress saved before this existed counts anyone who had extracted as having had theirs
        Progress back = new Progress(p.seenBits(), p.dives(), p.extractions(), p.knockOuts(), p.off(), p.everExtracted());
        assertFalse(back.firstExtractionFree());
        assertFalse(new Progress(0, 3, 1, 0, false).firstExtractionFree());
        assertTrue(new Progress(0, 3, 0, 2, false).firstExtractionFree());
    }

    @Test
    void aPlayersFirstKnockOutIsSofterAndOnlyTheFirstThatCostsThem() {
        Progress p = new Progress();
        assertTrue(p.firstKnockOutSoft());
        Coach first = new Coach(p, PACE, true);
        first.knockedOut(false);
        assertEquals(1, p.knockOuts());
        assertTrue(p.firstKnockOutSoft(), "a knock-out with nothing to lose does not use it up");
        // the low-health lesson says so while it is still to come, and only that lesson
        assertEquals(Lesson.FIRST_IS_SOFTER, Lesson.LOW_HEALTH.lines(35, false, true).get(2));
        assertEquals(2, Lesson.LOW_HEALTH.lines(35, false, false).size());
        assertEquals(Lesson.PORTAL.lines(35, true), Lesson.PORTAL.lines(35, true, true));
        new Coach(p, PACE, true).knockedOut(true);
        assertFalse(p.firstKnockOutSoft());
        assertTrue(p.everKnockedOut());
        assertTrue(p.rookie(), "they are still being taught");
        assertTrue(p.firstExtractionFree(), "and their free extraction is untouched");
        // starting over does not give it back, and it is not tied to coaching
        p.reset();
        assertFalse(p.firstKnockOutSoft());
        Progress quiet = new Progress();
        quiet.setOff(true);
        assertTrue(quiet.firstKnockOutSoft());
        quiet.forfeitFirstKnockOut();
        assertFalse(quiet.firstKnockOutSoft());
    }

    @Test
    void theSofterKnockOutSurvivesBeingStoredAndOldProgressCountsAsUsed() {
        Progress p = new Progress();
        new Coach(p, PACE, true).knockedOut(false);
        Progress back = new Progress(p.seenBits(), p.dives(), p.extractions(), p.knockOuts(), p.off(), p.everExtracted(), p.everKnockedOut());
        assertTrue(back.firstKnockOutSoft(), "knocked out once with nothing at stake: still to come after a reload");
        assertEquals(1, back.knockOuts());
        // progress saved before this existed: anyone who had been knocked out has had theirs
        assertFalse(new Progress(0, 3, 0, 2, false, false).firstKnockOutSoft());
        assertTrue(new Progress(0, 3, 1, 0, false, true).firstKnockOutSoft());
        assertFalse(new Progress(0, 3, 0, 1, false).firstKnockOutSoft());
        // the knock-out's own words: softened the first time, the plain lesson is still there for the next
        Coach c = new Coach(new Progress(), PACE, true);
        assertEquals(Lesson.FIRST_KNOCK_OUT, c.tellNow(Lesson.FIRST_KNOCK_OUT, 0));
        assertEquals(Lesson.KNOCKED_OUT, c.tellNow(Lesson.KNOCKED_OUT, 1));
        assertTrue(Lesson.FIRST_KNOCK_OUT.lines(35).get(0).contains("kept half"));
    }

    @Test
    void lessonsKeepTheirPlacesBecauseWhatWasHeardIsSavedByPosition() {
        // A new lesson goes at the end. If this fails, saved progress would point at the wrong lessons.
        String[] order = {"WELCOME", "OUTBREAK", "ENEMIES", "SPELLS", "CHESTS", "UNBANKED", "CHECKPOINT", "STAGE", "TUNNEL", "DEPTHS", "TIMER_CHEST", "OUTBREAK_CHEST",
                "STRONGBOX", "ALTAR", "LOW_HEALTH", "TIME_SHORT", "PORTAL", "GATEKEEPER", "KNOCKED_OUT", "TOLL_PAID", "EXTRACTED", "FIRST_EXTRACTION", "FIRST_KNOCK_OUT", "NEST", "ALPHA", "HAZARD", "DEEPER"};
        assertTrue(Lesson.values().length >= order.length);
        for (int i = 0; i < order.length; i++) assertEquals(order[i], Lesson.values()[i].name(), "position " + i);
    }

    @Test
    void theArrowPointsWhereTheTargetIsRelativeToWhereThePlayerLooks() {
        // facing +z (yaw 0): a target at +z is ahead, at -x is to the right, at +x to the left, at -z behind
        assertEquals("↑", Onboarding.arrow(0, 10, 0));
        assertEquals("→", Onboarding.arrow(-10, 0, 0));
        assertEquals("←", Onboarding.arrow(10, 0, 0));
        assertEquals("↓", Onboarding.arrow(0, -10, 0));
        assertEquals("↗", Onboarding.arrow(-10, 10, 0));
        assertEquals("↖", Onboarding.arrow(10, 10, 0));
        // facing -x (yaw 90): a target at -x is ahead
        assertEquals("↑", Onboarding.arrow(-10, 0, 90));
        assertEquals("↓", Onboarding.arrow(10, 0, 90));
        // yaw wraps: 450 is 90, -270 is 90
        assertEquals("↑", Onboarding.arrow(-10, 0, 450));
        assertEquals("↑", Onboarding.arrow(-10, 0, -270));
        assertEquals("↑", Onboarding.arrow(0, -10, 180));
        assertEquals("↑", Onboarding.arrow(0, -10, -180));
    }

    @Test
    void theGuideLineNamesTheGoalTheWayAndTheDistance() {
        assertEquals("Guide: Open a loot chest  ↑ 14m", Onboarding.guideLine(Goal.LOOT, 0, 0, 14, 0));
        assertEquals("Guide: Take the tunnel down to the Depths  → 9m", Onboarding.guideLine(Goal.DESCEND, -9, 1, 0, 0));
        assertEquals("Guide: Get back to the extraction portal and leave  ↓ 30m, above you", Onboarding.guideLine(Goal.EXTRACT, 0, 8, -30, 0));
        assertEquals("Guide: Loot a chest in the Depths  ↑ 1m, below you", Onboarding.guideLine(Goal.DEEP_LOOT, 0, -8, 1, 0));
        assertEquals("Guide: Open a loot chest (right here)", Onboarding.guideLine(Goal.LOOT, 1, 0, 1, 0));
    }

    @Test
    void thePointerAloneSaysWhichWayHowFarAndWhichLevel() {
        assertEquals("\u2191 14m", Onboarding.pointer(0, 0, 14, 0));
        assertEquals("\u2192 9m, above you", Onboarding.pointer(-9, 8, 0, 0));
        assertEquals("\u2193 30m, below you", Onboarding.pointer(0, -8, -30, 0));
        assertEquals(Onboarding.HERE, Onboarding.pointer(1, 2, 1, 0));
        assertEquals("\u2191 1m, below you", Onboarding.pointer(0, -3, 1, 0), "right above or below is not here");
    }

    @Test
    void longLinesAreWrappedOnSpaces() {
        List<String> lines = Onboarding.wrap("Pay the toll and lose 35% of your unbanked loot, or challenge the gatekeeper", 30);
        for (String line : lines) assertTrue(line.length() <= 30, line);
        assertEquals("Pay the toll and lose 35% of your unbanked loot, or challenge the gatekeeper", String.join(" ", lines));
        assertEquals(List.of("short"), Onboarding.wrap("short", 30));
        assertEquals(List.of("averyveryverylongwordthatcannotbreak", "ok"), Onboarding.wrap("averyveryverylongwordthatcannotbreak ok", 10));
    }
}
