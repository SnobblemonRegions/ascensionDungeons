package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class AggroTest {
    private static final Aggro.Ranges R = Aggro.Ranges.DEFAULT;

    /** A candidate {@code d} blocks from the enemy and {@code anchor} blocks from its post. */
    private static Aggro.Candidate at(double d, double anchor) { return new Aggro.Candidate(d * d, anchor * anchor); }

    @Test
    void aHunterTakesTheNearestTargetInSight() {
        var d = Aggro.decide(Aggro.Style.HUNTER, R, false, 0, List.of(at(20, 20), at(9, 9), at(30, 30)));
        assertEquals(1, d.target());
        assertFalse(d.returnHome());
        assertEquals(-1, Aggro.decide(Aggro.Style.HUNTER, R, false, 0, List.of(at(27, 27))).target(), "out of sight");
    }

    @Test
    void aSentryOnlyFightsNearItsPostAndWalksBack() {
        assertEquals(0, Aggro.decide(Aggro.Style.SENTRY, R, false, 0, List.of(at(8, 8))).target());
        var far = Aggro.decide(Aggro.Style.SENTRY, R, false, 49, List.of(at(5, 15)));
        assertEquals(-1, far.target(), "the target is near the sentry but far from its post");
        assertTrue(far.returnHome());
        assertFalse(Aggro.decide(Aggro.Style.SENTRY, R, false, 1, List.of()).returnHome(), "already home");
    }

    @Test
    void aGuardianNeverLeavesItsGround() {
        assertEquals(0, Aggro.decide(Aggro.Style.GUARDIAN, R, false, 4, List.of(at(3, 4))).target());
        var lured = Aggro.decide(Aggro.Style.GUARDIAN, R, true, 49, List.of(at(2, 8)));
        assertEquals(-1, lured.target(), "dragged off its ground, it gives up the chase");
        assertTrue(lured.returnHome());
        assertEquals(-1, Aggro.decide(Aggro.Style.GUARDIAN, R, false, 0, List.of(at(7, 7))).target(), "outside the guarded circle");
    }

    @Test
    void anAmbusherWaitsUntilSomeoneIsCloseOrHitsIt() {
        var asleep = Aggro.decide(Aggro.Style.AMBUSHER, R, false, 0, List.of(at(12, 12)));
        assertEquals(-1, asleep.target());
        assertFalse(asleep.alerted());
        var sprung = Aggro.decide(Aggro.Style.AMBUSHER, R, false, 0, List.of(at(4, 4)));
        assertEquals(0, sprung.target());
        assertTrue(sprung.alerted());
        assertEquals(0, Aggro.decide(Aggro.Style.AMBUSHER, R, true, 0, List.of(at(20, 20))).target(), "once awake it hunts at full sight");
    }

    @Test
    void aSkirmisherKeepsItsDistance() {
        var d = Aggro.decide(Aggro.Style.SKIRMISHER, R, false, 0, List.of(at(15, 15)));
        assertEquals(0, d.target());
        assertEquals(R.skirmishRange(), d.preferredRange());
        assertEquals(0, Aggro.decide(Aggro.Style.HUNTER, R, false, 0, List.of(at(15, 15))).preferredRange());
    }

    @Test
    void aPackEnemyIgnoresDistantTargetsUntilProvoked() {
        assertEquals(-1, Aggro.decide(Aggro.Style.PACK, R, false, 0, List.of(at(15, 15))).target());
        var provoked = Aggro.decide(Aggro.Style.PACK, R, true, 0, List.of(at(15, 15)));
        assertEquals(0, provoked.target());
        assertTrue(provoked.alerted());
        assertTrue(Aggro.decide(Aggro.Style.PACK, R, false, 0, List.of(at(5, 5))).alerted(), "close enough wakes it");
    }

    @Test
    void noCandidatesMeansNoTargetForEveryStyle() {
        for (Aggro.Style s : Aggro.Style.values()) assertEquals(-1, Aggro.decide(s, R, true, 0, List.of()).target(), s.name());
    }

    @Test
    void parsingIsForgiving() {
        assertEquals(Aggro.Style.SENTRY, Aggro.Style.parse(" sentry ", Aggro.Style.HUNTER));
        assertEquals(Aggro.Style.HUNTER, Aggro.Style.parse("nonsense", Aggro.Style.HUNTER));
        assertEquals(Aggro.Style.PACK, Aggro.Style.parse(null, Aggro.Style.PACK));
    }
}
