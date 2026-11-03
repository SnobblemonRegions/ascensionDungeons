package com.ascensiondungeons.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DistrictGridTest {
    private static final List<BiomeKind> BIOMES = List.of(BiomeKind.VERDANT_CANOPY);

    private static DistrictGrid grid(long seed, int radius) { return new DistrictGrid(seed, radius, BIOMES); }

    @Test
    void sameSeedSameGrid() {
        DistrictGrid a = grid(42, 2), b = grid(42, 2);
        assertEquals(a.cells().size(), b.cells().size());
        for (DistrictGrid.Cell c : a.cells()) assertEquals(c, b.cell(c.cx(), c.cz()));
        assertTrue(a.cells().stream().anyMatch(c -> !c.equals(grid(43, 2).cell(c.cx(), c.cz()))));
    }

    @Test
    void everyCellExistsAndNothingOutside() {
        DistrictGrid g = grid(7, 2);
        assertEquals(25, g.cells().size());
        assertNotNull(g.cell(-2, 2));
        assertNull(g.cell(3, 0));
        assertNull(g.cellAt(3 * 176 - 88, 0));
        assertNotNull(g.cellAt(2 * 176 + 87, -2 * 176 - 88));
    }

    @Test
    void everyDistrictIsReachableFromTheStart() {
        for (int radius = 0; radius <= 4; radius++) {
            for (long seed = 0; seed < 300; seed++) {
                DistrictGrid g = grid(seed, radius);
                Set<DistrictGrid.Cell> seen = new HashSet<>();
                ArrayDeque<DistrictGrid.Cell> queue = new ArrayDeque<>();
                queue.add(g.cell(0, 0));
                seen.add(g.cell(0, 0));
                while (!queue.isEmpty()) {
                    DistrictGrid.Cell c = queue.poll();
                    for (Side s : c.open()) {
                        DistrictGrid.Cell n = g.cell(c.cx() + s.dx, c.cz() + s.dz);
                        assertNotNull(n, "door leads outside the grid, seed " + seed);
                        if (seen.add(n)) queue.add(n);
                    }
                }
                assertEquals(g.cells().size(), seen.size(), "unreachable district, seed " + seed + " radius " + radius);
            }
        }
    }

    @Test
    void doorsAreSymmetricAndNeverMoreThanThree() {
        for (long seed = 0; seed < 500; seed++) {
            DistrictGrid g = grid(seed, 3);
            for (DistrictGrid.Cell c : g.cells()) {
                assertTrue(c.open().size() <= 3, "four doors, seed " + seed);
                for (Side s : c.open()) {
                    DistrictGrid.Cell n = g.cell(c.cx() + s.dx, c.cz() + s.dz);
                    assertTrue(n.open().contains(s.opposite()), "one-way door, seed " + seed);
                }
            }
        }
    }

    @Test
    void everyDoorIsAGateOfTheOrientedTemplate() {
        for (long seed = 0; seed < 500; seed++) {
            DistrictGrid g = grid(seed, 3);
            for (DistrictGrid.Cell c : g.cells()) {
                Set<Side> gates = EnumSet.noneOf(Side.class);
                for (Side template : Side.values()) {
                    if (Template.hasGate(template)) gates.add(c.orientation().toWorld(template));
                }
                assertTrue(gates.containsAll(c.open()), "door on the solid side, seed " + seed + " " + c);
                assertFalse(gates.contains(c.orientation().closedSide()));
            }
        }
    }

    @Test
    void orientationsRoundTripAndAreDistinct() {
        Set<List<Integer>> images = new HashSet<>();
        for (Orientation o : Orientation.all()) {
            for (int dx = -88; dx < 88; dx += 17) {
                for (int dz = -88; dz < 88; dz += 19) {
                    int[] w = o.toWorld(dx, dz);
                    int[] t = o.toTemplate(w[0], w[1]);
                    assertEquals(dx, t[0]);
                    assertEquals(dz, t[1]);
                }
            }
            int[] probe = o.toWorld(3, 7);
            images.add(List.of(probe[0], probe[1]));
        }
        assertEquals(8, images.size());
    }

    @Test
    void gateStaysCentredUnderEveryOrientation() {
        // A gate lies on the centre line (offset -4 .. +4); it must still be there after turning or mirroring.
        for (Orientation o : Orientation.all()) {
            for (int along = 60; along < 88; along += 9) {
                for (int across = -4; across <= 4; across++) {
                    int[] northGate = o.toWorld(across, -along);
                    int[] southGate = o.toWorld(across, along);
                    int[] eastGate = o.toWorld(along, across);
                    for (int[] p : List.of(northGate, southGate, eastGate)) {
                        assertTrue(Math.min(Math.abs(p[0]), Math.abs(p[1])) <= 4, "gate drifted off the centre line");
                    }
                }
            }
        }
    }

    @Test
    void entryIsInsideTheCentreDistrictOnTheFloor() {
        for (long seed = 0; seed < 100; seed++) {
            DistrictGrid g = grid(seed, 1);
            int[] e = g.entry();
            assertEquals(Template.FLOOR_Y + 1, e[1]);
            assertEquals(g.cell(0, 0), g.cellAt(e[0], e[2]));
            assertEquals(52, Math.max(Math.abs(e[0]), Math.abs(e[2])), "entry room is 52 blocks from the centre");
        }
    }

    @Test
    void cellIndexMath() {
        assertEquals(0, DistrictGrid.cellIndex(-88));
        assertEquals(0, DistrictGrid.cellIndex(87));
        assertEquals(1, DistrictGrid.cellIndex(88));
        assertEquals(-1, DistrictGrid.cellIndex(-89));
        assertEquals(-88, DistrictGrid.offsetInCell(-88));
        assertEquals(87, DistrictGrid.offsetInCell(87));
        assertEquals(-88, DistrictGrid.offsetInCell(88));
    }
}
