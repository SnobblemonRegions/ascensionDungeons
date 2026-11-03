package com.ascensiondungeons.gen.cinderforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ascensiondungeons.gen.cinderforge.CinderforgeLayout.Box;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards the exported Cinderforge data against drifting from the schematic. The layout JSON and the schematic are written by
 * the same generator run; these tests fail if either is replaced without the other.
 */
class CinderforgeLayoutTest {
    private final CinderforgeLayout layout = CinderforgeLayout.get();

    private String base(String state) {
        int i = state.indexOf('[');
        return (i < 0 ? state : state.substring(0, i)).replace("minecraft:", "");
    }

    private String at(int[] c) { return base(layout.stateAt(c[0], c[1], c[2])); }

    private void forEach(Box b, java.util.function.Consumer<int[]> f) {
        for (int x = b.min[0]; x <= b.max[0]; x++)
            for (int y = b.min[1]; y <= b.max[1]; y++)
                for (int z = b.min[2]; z <= b.max[2]; z++) f.accept(new int[] {x, y, z});
    }

    @Test
    void schematicHasTheExpectedSizeAndRooms() {
        assertEquals(129, layout.width());
        assertEquals(85, layout.height());
        assertEquals(176, layout.length());
        Set<String> ids = new HashSet<>();
        for (var r : layout.data().rooms) ids.add(r.id);
        assertEquals(Set.of("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8"), ids);
    }

    @Test
    void theEntryIsAStandingCellWithHeadroom() {
        int[] e = layout.data().entry;
        assertEquals("air", at(e));
        assertEquals("air", base(layout.stateAt(e[0], e[1] + 1, e[2])));
        assertTrue(!base(layout.stateAt(e[0], e[1] - 1, e[2])).equals("air"));
        assertEquals("R1", layout.roomAt(e[0], e[1], e[2]).id);
    }

    @Test
    void everySpawnCellIsOpenAirOverASolidFloorInsideItsRoom() {
        for (var room : layout.data().rooms) {
            assertTrue(room.spawnCells.length >= 60, room.id + " has few spawn cells: " + room.spawnCells.length);
            for (int[] c : room.spawnCells) {
                assertTrue(room.contains(c[0], c[1], c[2]), room.id + " cell outside its room " + java.util.Arrays.toString(c));
                assertEquals("air", at(c), room.id);
                assertEquals("air", base(layout.stateAt(c[0], c[1] + 1, c[2])));
                String below = base(layout.stateAt(c[0], c[1] - 1, c[2]));
                assertFalse(below.equals("air") || below.equals("lava") || below.equals("magma_block"), room.id + " floor " + below);
            }
        }
    }

    @Test
    void lightsSitInAirAndAreOnlyEverInsideRooms() {
        assertTrue(layout.data().lights.length > 400);
        for (int[] l : layout.data().lights) {
            assertEquals("air", at(l));
            assertNotNull(layout.roomAt(l[0], l[1], l[2]));
        }
    }

    @Test
    void theThreeCisternLeversAreRealLeversInStageOrder() {
        assertEquals(3, layout.data().levers.length);
        Set<Integer> stages = new HashSet<>();
        for (var lv : layout.data().levers) {
            assertEquals("lever", at(lv.pos));
            stages.add(lv.stage);
            assertEquals("R4", layout.roomAt(lv.pos[0], lv.pos[1], lv.pos[2]).id);
        }
        assertEquals(Set.of(1, 2, 3), stages);
    }

    @Test
    void sealsAreSolidAndTheirOpeningsAreWhatTheMechanicsExpect() {
        forEach(layout.data().eastPlug, c -> assertEquals("blackstone", at(c)));
        forEach(layout.data().westPlug, c -> assertEquals("blackstone", at(c)));
        forEach(layout.data().tunnelPlug, c -> assertEquals("blackstone", at(c)));
        for (Box b : layout.data().trenchPlugs) forEach(b, c -> assertEquals("blackstone", at(c)));
        for (Box b : layout.data().hiddenDoors) forEach(b, c -> assertEquals("polished_blackstone_bricks", at(c)));
        // the descent gate is the rail wall above the reservoir stairs
        forEach(layout.data().descentGate, c -> assertTrue(at(c).endsWith("_wall"), at(c)));
        // the Overseer gate is open corridor in the schematic; the mod seals it at generation (isGate)
        forEach(layout.data().overseerGate, c -> { assertEquals("air", at(c)); assertTrue(layout.isGate(c[0], c[1], c[2])); });
        assertEquals(24, layout.data().overseerGate.volume());
    }

    @Test
    void reservoirAndTrenchHoldLavaThatTheDrainRemoves() {
        int[] lava = {0};
        forEach(layout.data().reservoir, c -> { if (at(c).equals("lava")) lava[0]++; });
        assertTrue(lava[0] > 1000, "reservoir lava cells: " + lava[0]);
        int[] trench = {0};
        forEach(layout.data().trench, c -> { if (at(c).equals("lava")) trench[0]++; });
        assertTrue(trench[0] > 100, "trench lava cells: " + trench[0]);
    }

    @Test
    void geysersAndHeatVentsAreMagmaAndTheCrucibleIsLavaInsideARim() {
        assertEquals(8, layout.data().geysers.length);
        for (int[] g : layout.data().geysers) {
            assertEquals("magma_block", at(g));
            assertEquals("R2", layout.roomAt(g[0], g[1], g[2]).id);
        }
        assertEquals(8, layout.data().heatVents.length);
        for (Box v : layout.data().heatVents) {
            assertEquals(4, v.volume());
            forEach(v, c -> assertEquals("magma_block", at(c)));
        }
        forEach(layout.data().crucibleLava, c -> assertEquals("lava", at(c)));
        int[] walls = {0};
        forEach(layout.data().crucibleRim, c -> { if (at(c).endsWith("_wall")) walls[0]++; else assertEquals("air", at(c)); });
        assertTrue(walls[0] >= 12, "rim walls: " + walls[0]);
    }

    @Test
    void chestsAreOneKeyOneNicheFourIslands() {
        int key = 0, niche = 0, island = 0;
        for (var c : layout.data().chests) {
            assertEquals("chest", at(c.pos));
            switch (c.kind) {
                case "key" -> { key++; assertEquals("R5", layout.roomAt(c.pos[0], c.pos[1], c.pos[2]).id); }
                case "niche" -> { niche++; assertEquals("R4", layout.roomAt(c.pos[0], c.pos[1], c.pos[2]).id); }
                case "island" -> island++;
                default -> throw new AssertionError(c.kind);
            }
        }
        assertEquals(1, key);
        assertEquals(1, niche);
        assertEquals(4, island);
    }

    @Test
    void furnaceMouthsAreBarsAndTheLiftArrivalIsInsideTheSealedCell() {
        assertEquals(3, layout.data().furnaces.length);
        for (var f : layout.data().furnaces) assertEquals("iron_bars", at(f.mouth));
        int[] a = layout.data().liftArrival;
        assertEquals("R3", layout.roomAt(a[0], a[1], a[2]).id);
        assertEquals("air", at(a));
    }

    @Test
    void theSpiralDescribesItsOwnWell() {
        var r7 = layout.data().r7;
        assertEquals(9.5, r7.radius, 0.001);
        assertTrue(r7.topY - r7.floorY >= 40);
        assertEquals("R7", layout.roomAt(r7.entry[0], r7.entry[1], r7.entry[2]).id);
    }
}
