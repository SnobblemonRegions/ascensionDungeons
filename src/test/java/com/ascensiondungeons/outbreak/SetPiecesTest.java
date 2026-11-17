package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.outbreak.SetPieces.Kind;
import com.ascensiondungeons.outbreak.SetPieces.Nest;
import com.ascensiondungeons.outbreak.SetPieces.Piece;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SetPiecesTest {
    /** A row of rooms 24 blocks apart, each a 16 x 16 floor, in the bands given. */
    private static List<SetPieces.Room> rooms(int... bands) {
        List<SetPieces.Room> out = new ArrayList<>();
        for (int i = 0; i < bands.length; i++) {
            List<int[]> cells = new ArrayList<>();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) cells.add(new int[] {i * 24 + x, 2, z});
            out.add(new SetPieces.Room("D" + i, bands[i], i % 4 == 3, cells));
        }
        return out;
    }

    private static double dist(int[] a, int[] b) { double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2]; return Math.sqrt(dx * dx + dy * dy + dz * dz); }

    @Test
    void eachPieceHasARoomOfItsOwnWithItsRewardAwayFromItsHeart() {
        for (long seed = 1; seed <= 200; seed++) {
            List<Piece> plan = SetPieces.plan(rooms(0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2), new SetPieces.Counts(2, 2, 3), seed, List.of());
            assertEquals(7, plan.size(), "seed " + seed);
            Set<String> used = new HashSet<>();
            for (Piece p : plan) {
                assertTrue(used.add(p.room()), "seed " + seed + ": one piece per room");
                assertTrue(dist(p.reward(), p.centre()) >= SetPieces.REWARD_DISTANCE, "seed " + seed);
                assertTrue(p.band() >= 1, "seed " + seed + ": nothing in the outer band while deeper rooms are free");
            }
            for (Piece p : plan) if (p.kind() == Kind.ALPHA) assertEquals(2, p.band(), "seed " + seed + ": alphas take the deepest rooms");
            // rewards keep the usual spacing from each other
            for (Piece a : plan) for (Piece b : plan) if (a != b) assertTrue(dist(a.reward(), b.reward()) >= FeaturePlanner.SPACING);
        }
    }

    @Test
    void alphasPreferVaultsAndTheSameSeedGivesTheSamePlan() {
        // rooms 3 and 7 are vaults; both deep
        List<Piece> plan = SetPieces.plan(rooms(2, 2, 2, 2, 2, 2, 2, 2), new SetPieces.Counts(0, 2, 0), 9, List.of());
        Set<String> alphaRooms = new HashSet<>();
        for (Piece p : plan) alphaRooms.add(p.room());
        assertEquals(Set.of("D3", "D7"), alphaRooms);
        List<Piece> again = SetPieces.plan(rooms(2, 2, 2, 2, 2, 2, 2, 2), new SetPieces.Counts(0, 2, 0), 9, List.of());
        for (int i = 0; i < plan.size(); i++) assertArrayEquals(plan.get(i).reward(), again.get(i).reward());
    }

    @Test
    void piecesKeepClearOfWhatIsAlreadyThereAndStopWhenRoomsRunOut() {
        // A chest already at the middle of every room: no reward within the spacing of it, no heart on it.
        List<SetPieces.Room> rs = rooms(1, 1, 2);
        List<int[]> taken = new ArrayList<>();
        for (SetPieces.Room r : rs) taken.add(SetPieces.middle(r.cells()));
        List<Piece> plan = SetPieces.plan(rs, new SetPieces.Counts(1, 1, 1), 3, taken);
        for (Piece p : plan) for (int[] t : taken) {
            assertTrue(dist(p.reward(), t) >= FeaturePlanner.SPACING);
            assertTrue(dist(p.centre(), t) >= 3);
        }
        // more pieces asked for than there are rooms: as many as fit, no more, no error
        assertEquals(3, SetPieces.plan(rooms(1, 2, 2), new SetPieces.Counts(3, 3, 3), 1, List.of()).size());
        assertTrue(SetPieces.plan(List.of(), new SetPieces.Counts(1, 1, 1), 1, List.of()).isEmpty());
        // a room too small for anything is skipped
        List<SetPieces.Room> tiny = List.of(new SetPieces.Room("T", 2, false, List.of(new int[] {0, 2, 0}, new int[] {1, 2, 0})));
        assertTrue(SetPieces.plan(tiny, new SetPieces.Counts(1, 1, 1), 1, List.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new SetPieces.Counts(-1, 0, 0));
    }

    @Test
    void aNestWakesSendsItsWavesOneAfterAnotherAndIsCleared() {
        Nest n = new Nest(3, 600);
        assertEquals(Nest.State.DORMANT, n.state());
        assertEquals(Nest.Action.NONE, n.tick(0, false, 0), "nobody near: it sleeps");
        assertEquals(Nest.Action.WAKE, n.tick(10, true, 0), "a player comes near: the first wave comes with it");
        assertEquals(1, n.wave());
        assertEquals(Nest.Action.NONE, n.tick(20, true, 4), "the wave is still standing");
        assertEquals(Nest.Action.SPAWN_WAVE, n.tick(30, true, 0));
        assertEquals(2, n.wave());
        assertEquals(Nest.Action.SPAWN_WAVE, n.tick(40, true, 0));
        assertEquals(3, n.wave());
        assertEquals(Nest.Action.NONE, n.tick(50, true, 1));
        assertEquals(Nest.Action.CLEARED, n.tick(60, true, 0));
        assertEquals(Nest.State.CLEARED, n.state());
        assertEquals(Nest.Action.NONE, n.tick(70, true, 0), "cleared for good");
    }

    @Test
    void aNestLeftAloneLongEnoughGoesBackToSleep() {
        Nest n = new Nest(2, 600);
        n.tick(0, true, 0);
        assertEquals(Nest.Action.NONE, n.tick(100, false, 3), "a step back out of range is not leaving");
        assertEquals(Nest.Action.NONE, n.tick(650, true, 3), "back in time: the clock starts again");
        assertEquals(Nest.Action.NONE, n.tick(700, false, 3));
        assertEquals(Nest.Action.SLEEP, n.tick(1300, false, 3));
        assertEquals(Nest.State.DORMANT, n.state());
        assertEquals(0, n.wave());
        assertEquals(Nest.Action.WAKE, n.tick(1400, true, 0), "and starts over from the first wave");
        assertEquals(1, n.wave());
        assertThrows(IllegalArgumentException.class, () -> new Nest(0, 10));
    }

    @Test
    void deeperSetPiecesAreHarder() {
        assertEquals(2, SetPieces.nestWaves(1));
        assertEquals(3, SetPieces.nestWaves(2));
        assertTrue(SetPieces.nestWaveSize(2, 1, 1) > SetPieces.nestWaveSize(1, 1, 1));
        assertTrue(SetPieces.nestWaveSize(1, 2, 1) > SetPieces.nestWaveSize(1, 1, 1));
        assertTrue(SetPieces.nestWaveSize(1, 1, 3) > SetPieces.nestWaveSize(1, 1, 1), "more players, more enemies");
        assertTrue(SetPieces.alphaHealth(2, 0) > SetPieces.alphaHealth(1, 0));
        assertTrue(SetPieces.alphaHealth(1, 4) > SetPieces.alphaHealth(1, 0));
        assertTrue(SetPieces.alphaDamage(2, 3) > SetPieces.alphaDamage(1, 0));
        assertTrue(SetPieces.hazardPeriod(160, 5) < SetPieces.hazardPeriod(160, 0));
        assertEquals(40, SetPieces.hazardPeriod(50, 5), "never quicker than two seconds");
    }
}
