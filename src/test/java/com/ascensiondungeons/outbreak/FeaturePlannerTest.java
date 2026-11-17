package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeaturePlannerTest {
    private static final int[] ENTRY = {5, 0, 5};

    /** A 30 x 30 floor of standing cells per room, rooms side by side along x. */
    private static List<FeaturePlanner.Room> rooms(int count) {
        List<FeaturePlanner.Room> out = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            List<int[]> cells = new ArrayList<>();
            for (int x = 0; x < 30; x += 2) for (int z = 0; z < 30; z += 2) cells.add(new int[] {r * 40 + x, 0, z});
            out.add(new FeaturePlanner.Room("R" + (r + 1), cells));
        }
        return out;
    }

    private static final FeaturePlanner.Counts COUNTS = new FeaturePlanner.Counts(6, 2, 1, 1, 2);

    private static double dist(int[] a, int[] b) { return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2)); }

    @Test
    void theSameSeedGivesTheSameDungeonAndADifferentSeedDoesNot() {
        var a = FeaturePlanner.plan(rooms(4), ENTRY, "R1", COUNTS, 42);
        var b = FeaturePlanner.plan(rooms(4), ENTRY, "R1", COUNTS, 42);
        var c = FeaturePlanner.plan(rooms(4), ENTRY, "R1", COUNTS, 43);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).kind(), b.get(i).kind());
            assertArrayEquals(a.get(i).cell(), b.get(i).cell());
        }
        boolean differs = a.size() != c.size();
        for (int i = 0; i < Math.min(a.size(), c.size()) && !differs; i++) differs = !java.util.Arrays.equals(a.get(i).cell(), c.get(i).cell());
        assertTrue(differs);
    }

    @Test
    void everythingAskedForIsPlaced() {
        var plan = FeaturePlanner.plan(rooms(4), ENTRY, "R1", COUNTS, 7);
        assertEquals(1, plan.stream().filter(p -> p.kind() == FeaturePlanner.Kind.PORTAL).count());
        assertEquals(6, plan.stream().filter(p -> p.kind() == FeaturePlanner.Kind.CHEST).count());
        assertEquals(2, plan.stream().filter(p -> p.kind() == FeaturePlanner.Kind.OUTBREAK_CHEST).count());
        assertEquals(1, plan.stream().filter(p -> p.kind() == FeaturePlanner.Kind.TIMER_CHEST).count());
        assertEquals(1, plan.stream().filter(p -> p.kind() == FeaturePlanner.Kind.STRONGBOX).count());
        assertEquals(2, plan.stream().filter(p -> p.kind() == FeaturePlanner.Kind.ALTAR).count());
    }

    @Test
    void thePortalIsNearTheEntryButNotOnIt() {
        for (long seed = 0; seed < 20; seed++) {
            var portal = FeaturePlanner.plan(rooms(4), ENTRY, "R1", COUNTS, seed).get(0);
            assertEquals(FeaturePlanner.Kind.PORTAL, portal.kind());
            assertEquals("R1", portal.room());
            double d = dist(portal.cell(), ENTRY);
            assertTrue(d >= FeaturePlanner.PORTAL_MIN && d < 8, "portal " + d + " from the entry");
        }
    }

    @Test
    void featuresKeepTheirDistanceFromEachOtherAndFromTheEntry() {
        for (long seed = 0; seed < 30; seed++) {
            var plan = FeaturePlanner.plan(rooms(4), ENTRY, "R1", COUNTS, seed);
            for (int i = 0; i < plan.size(); i++) {
                if (plan.get(i).kind() != FeaturePlanner.Kind.PORTAL)
                    assertTrue(dist(plan.get(i).cell(), ENTRY) >= FeaturePlanner.SPACING, "seed " + seed);
                for (int j = i + 1; j < plan.size(); j++)
                    assertTrue(dist(plan.get(i).cell(), plan.get(j).cell()) >= FeaturePlanner.SPACING, "seed " + seed + ": " + i + " and " + j);
            }
        }
    }

    @Test
    void prizesStayOutOfTheEntryRoom() {
        for (long seed = 0; seed < 30; seed++) {
            for (var p : FeaturePlanner.plan(rooms(4), ENTRY, "R1", COUNTS, seed)) {
                if (p.kind() == FeaturePlanner.Kind.STRONGBOX || p.kind() == FeaturePlanner.Kind.TIMER_CHEST || p.kind() == FeaturePlanner.Kind.ALTAR)
                    assertNotEquals("R1", p.room());
            }
        }
    }

    @Test
    void strongboxesAreEpicTimerChestsRareAndAltarsHaveNoRarity() {
        for (var p : FeaturePlanner.plan(rooms(4), ENTRY, "R1", COUNTS, 3)) {
            switch (p.kind()) {
                case STRONGBOX -> assertEquals(ChestRarity.EPIC, p.rarity());
                case TIMER_CHEST -> assertEquals(ChestRarity.RARE, p.rarity());
                case ALTAR, PORTAL -> assertNull(p.rarity());
                default -> assertNotNull(p.rarity());
            }
        }
    }

    @Test
    void roomsWithoutCellsAreSkippedAndAnEmptyDungeonGivesNothing() {
        List<FeaturePlanner.Room> rs = rooms(2);
        rs.add(new FeaturePlanner.Room("R9", List.of()));
        for (var p : FeaturePlanner.plan(rs, ENTRY, "R1", COUNTS, 5)) assertNotEquals("R9", p.room());
        assertTrue(FeaturePlanner.plan(List.of(new FeaturePlanner.Room("R1", List.of())), ENTRY, "R1", COUNTS, 5).isEmpty());
    }

    @Test
    void aSingleRoomStillWorks() {
        var plan = FeaturePlanner.plan(rooms(1), ENTRY, "R1", new FeaturePlanner.Counts(2, 0, 0, 1, 0), 9);
        assertEquals(1, plan.stream().filter(p -> p.kind() == FeaturePlanner.Kind.STRONGBOX).count());
        assertEquals(2, plan.stream().filter(p -> p.kind() == FeaturePlanner.Kind.CHEST).count());
    }
}
