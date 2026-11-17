package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.outbreak.Tactics.BossPhases;
import com.ascensiondungeons.outbreak.Tactics.Candidate;
import com.ascensiondungeons.outbreak.Tactics.Difficulty;
import com.ascensiondungeons.outbreak.Tactics.Profile;
import com.ascensiondungeons.outbreak.Tactics.Spacing;
import java.util.List;
import org.junit.jupiter.api.Test;

class TacticsTest {
    private static final Profile NORMAL_DEEP = Tactics.profile(Difficulty.NORMAL, 2, 3);

    @Test
    void harderDifficultiesFightHarderAtEveryKnob() {
        Difficulty[] order = Difficulty.values();
        for (int i = 1; i < order.length; i++) {
            Profile easier = Tactics.profile(order[i - 1], 2, 3), harder = Tactics.profile(order[i], 2, 3);
            String n = order[i].name();
            assertTrue(harder.maxPressing() >= easier.maxPressing(), n);
            assertTrue(harder.retreatChance() >= easier.retreatChance(), n);
            assertTrue(harder.hurtFocus() >= easier.hurtFocus(), n);
            assertTrue(harder.summons() >= easier.summons(), n);
            assertTrue(harder.chargeEvery() <= easier.chargeEvery(), n);
            assertTrue(harder.chargeWarning() <= easier.chargeWarning(), n);
            assertTrue(harder.enrage() >= easier.enrage(), n);
            assertTrue(harder.chargeWarning() >= 20, n + ": a charge is always readable");
        }
        Profile casual = Tactics.profile(Difficulty.CASUAL, 2, 5);
        assertFalse(casual.flank() || casual.charges(), "casual never flanks or charges");
        assertEquals(0.0, casual.retreatChance());
        assertEquals(2, casual.maxPressing(), "at most two enemies on a player at once");
        assertTrue(casual.kiteRange() > 0, "a ranged fighter keeps its distance on every difficulty: that is who it is");
    }

    @Test
    void onNormalTheOuterRoomsFightPlainlyAndTheDeepSharply() {
        Profile outer = Tactics.profile(Difficulty.NORMAL, 0, 0), deep = Tactics.profile(Difficulty.NORMAL, 2, 0), late = Tactics.profile(Difficulty.NORMAL, 0, 4);
        assertFalse(outer.flank(), "a first room does not flank");
        assertTrue(deep.flank());
        assertTrue(late.flank(), "a late Outbreak sharpens even the outer rooms");
        assertEquals(0.0, outer.hurtFocus());
        assertTrue(deep.hurtFocus() > 0);
        assertTrue(deep.maxPressing() > outer.maxPressing());
        assertTrue(deep.summons() > outer.summons());
        assertTrue(Tactics.profile(Difficulty.NORMAL, 0, 5).chargeEvery() < outer.chargeEvery(), "bosses charge more often as the Outbreak rises");
        assertEquals(Difficulty.HARD, Difficulty.parse(" hard ", Difficulty.NORMAL));
        assertEquals(Difficulty.NORMAL, Difficulty.parse("impossible", Difficulty.NORMAL));
        assertEquals(Difficulty.NORMAL, Difficulty.parse(null, Difficulty.NORMAL));
        assertEquals("Nightmare", Difficulty.NIGHTMARE.label());
        assertThrows(IllegalArgumentException.class, () -> new Profile(1, false, 6, 0, 0, 0, 0.3, 0, false, 100, 10, 1, 40));
    }

    @Test
    void aRangedFighterBacksOffClosesInOrCirclesToHoldItsRange() {
        assertEquals(Spacing.BACK_OFF, Tactics.spacing(3, 7));
        assertEquals(Spacing.STRAFE, Tactics.spacing(6, 7));
        assertEquals(Spacing.STRAFE, Tactics.spacing(9, 7));
        assertEquals(Spacing.CLOSE_IN, Tactics.spacing(12, 7));
        // too close: straight away from the target
        double[] back = Tactics.spacingPoint(2, 0, 0, 0, 7, true);
        assertEquals(6, back[0], 1e-9);
        assertEquals(0, back[1], 1e-9);
        // too far: onto the ring, on its own side
        double[] in = Tactics.spacingPoint(0, 20, 0, 0, 7, true);
        assertEquals(0, in[0], 1e-9);
        assertEquals(7, in[1], 1e-9);
        // at range: a step round the ring, the same distance out, either way
        double[] cw = Tactics.spacingPoint(7, 0, 0, 0, 7, true), ccw = Tactics.spacingPoint(7, 0, 0, 0, 7, false);
        assertEquals(7, Math.hypot(cw[0], cw[1]), 1e-9);
        assertTrue(cw[1] > 0 && ccw[1] < 0, "the two ways round go opposite ways");
        // standing on the target: still a sensible point, not NaN
        double[] on = Tactics.spacingPoint(0, 0, 0, 0, 7, true);
        assertFalse(Double.isNaN(on[0]) || Double.isNaN(on[1]));
    }

    @Test
    void aWaveSpreadsRoundItsTargetAndTakesTurns() {
        double a0 = Tactics.slotAngle(0, 4, 0), a1 = Tactics.slotAngle(1, 4, 0), a2 = Tactics.slotAngle(2, 4, 0);
        assertEquals(Math.PI / 2, a1 - a0, 1e-9);
        assertEquals(Math.PI, a2 - a0, 1e-9);
        double[] p = Tactics.ringPoint(10, 10, Math.PI / 2, 6);
        assertEquals(10, p[0], 1e-9);
        assertEquals(16, p[1], 1e-9);
        Profile two = Tactics.profile(Difficulty.CASUAL, 0, 0);
        assertTrue(Tactics.mayPress(false, 1, two));
        assertFalse(Tactics.mayPress(false, 2, two), "two already pressing: wait");
        assertTrue(Tactics.mayPress(true, 5, two), "one already pressing keeps going");
    }

    @Test
    void enemiesGoForWhoeverHurtsThemAndTheHurtButKeepTheirChoiceAWhile() {
        Profile casual = Tactics.profile(Difficulty.CASUAL, 0, 0), hard = Tactics.profile(Difficulty.HARD, 2, 0);
        Candidate near = new Candidate(4 * 4, 1.0, 0), farHurting = new Candidate(10 * 10, 1.0, 12), farHurt = new Candidate(9 * 9, 0.2, 0);
        // casual mostly goes for the nearest; a hard enemy turns on whoever is hurting it
        assertEquals(0, Tactics.pick(List.of(near, farHurting), -1, false, casual));
        assertEquals(1, Tactics.pick(List.of(near, farHurting), -1, false, hard));
        // a hard enemy finishes off the hurt one over a near healthy one
        assertEquals(1, Tactics.pick(List.of(new Candidate(6 * 6, 1.0, 0), farHurt), -1, false, hard));
        // a lock holds against a slightly better choice, not against a much better one, and only while fresh
        Candidate slightlyNearer = new Candidate(3 * 3, 1.0, 0);
        assertEquals(0, Tactics.pick(List.of(near, slightlyNearer), 0, true, casual));
        assertEquals(1, Tactics.pick(List.of(near, slightlyNearer), 0, false, casual));
        assertEquals(1, Tactics.pick(List.of(new Candidate(20 * 20, 1.0, 0), slightlyNearer), 0, true, casual));
        assertEquals(-1, Tactics.pick(List.of(), -1, false, casual));
        assertEquals(0, Tactics.pick(List.of(near), 7, true, casual), "a lock on someone no longer in sight does nothing");
    }

    @Test
    void threatFadesByHalfEveryHalfLife() {
        Tactics.Threat t = new Tactics.Threat();
        t.hurt("a", 8, 0);
        assertEquals(8, t.of("a", 0), 1e-9);
        assertEquals(4, t.of("a", Tactics.Threat.HALF_LIFE), 1e-9);
        t.hurt("a", 4, Tactics.Threat.HALF_LIFE);
        assertEquals(8, t.of("a", Tactics.Threat.HALF_LIFE), 1e-9);
        assertEquals(0, t.of("b", 50));
    }

    @Test
    void onlyAnOrdinaryEnemyFallsBackAndOnlyOnceAsItDropsBelowTheLine() {
        Profile hard = Tactics.profile(Difficulty.HARD, 0, 0);
        double line = hard.retreatBelow();
        assertTrue(Tactics.retreat(line + 0.1, line - 0.05, false, false, 0.1, hard));
        assertFalse(Tactics.retreat(line + 0.1, line - 0.05, true, false, 0.1, hard), "only once");
        assertFalse(Tactics.retreat(line + 0.1, line - 0.05, false, true, 0.1, hard), "elites and bosses stand");
        assertFalse(Tactics.retreat(line - 0.1, line - 0.2, false, false, 0.1, hard), "only on crossing the line");
        assertFalse(Tactics.retreat(line + 0.1, line - 0.05, false, false, 0.99, hard), "the roll decides");
        assertFalse(Tactics.retreat(1, 0, false, false, 0, Tactics.profile(Difficulty.CASUAL, 2, 5)), "casual enemies never fall back");
    }

    @Test
    void aBossCallsHelpThenRagesAndNeverGoesBack() {
        BossPhases b = new BossPhases();
        assertEquals(0, b.update(0.9));
        assertEquals(1, b.update(0.6));
        assertEquals(0, b.update(0.62), "healing back over the line does not reset it");
        assertEquals(0, b.update(0.5));
        assertEquals(2, b.update(0.3));
        assertEquals(0, b.update(0.1));
        assertEquals(2, b.phase());
        BossPhases big = new BossPhases();
        assertEquals(3, big.update(0.2), "one big hit: help and rage together");
        assertEquals(2, big.phase());
    }

    @Test
    void aChargeHitsWhatIsOnItsLineAndNothingBeside() {
        assertTrue(Tactics.onLine(5, 0.5, 0, 0, 12, 0, 1.2));
        assertFalse(Tactics.onLine(5, 2, 0, 0, 12, 0, 1.2), "a step aside is safe");
        assertTrue(Tactics.onLine(12.5, 0, 0, 0, 12, 0, 1.2), "just past the end");
        assertFalse(Tactics.onLine(15, 0, 0, 0, 12, 0, 1.2), "well past the end");
        assertFalse(Tactics.onLine(-3, 0, 0, 0, 12, 0, 1.2), "behind the charger");
        assertTrue(Tactics.onLine(0, 0.5, 0, 0, 0, 0, 1.2), "a zero-length line is a point");
        assertTrue(Tactics.CHARGE_LENGTH > 8);
    }

    @Test
    void theDeepNormalProfileIsWhatTheDiveIsTunedFor() {
        assertTrue(NORMAL_DEEP.flank());
        assertEquals(3, NORMAL_DEEP.maxPressing());
        assertTrue(NORMAL_DEEP.charges());
        assertTrue(NORMAL_DEEP.chargeWarning() >= 30, "a second and a half to read the line on Normal");
    }
}
