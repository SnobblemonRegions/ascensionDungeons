package com.ascensiondungeons.gen.depths;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DepthsTest {
    private static final Depths.Params P = new Depths.Params(7, 7, 24, 0.75, 0.35);

    @Test
    void theSameSeedGivesTheSameDepthsAndADifferentSeedDoesNot() {
        Depths a = Depths.generate(42, P), b = Depths.generate(42, P), c = Depths.generate(43, P);
        assertEquals(a.rooms().size(), b.rooms().size());
        boolean same = true, differs = false;
        for (int x = 0; x < a.sizeX(); x += 3) for (int z = 0; z < a.sizeZ(); z += 3) for (int y = 0; y < Depths.HEIGHT; y++) {
            same &= a.role(x, y, z) == b.role(x, y, z);
            differs |= a.role(x, y, z) != c.role(x, y, z);
        }
        assertTrue(same);
        assertTrue(differs);
    }

    @Test
    void everyStandingCellIsReachableFromTheEntranceForManySeeds() {
        for (long seed = 0; seed < 300; seed++) {
            Depths d = Depths.generate(seed, P);
            int[] w = d.walkability();
            assertTrue(w[0] > 2000, "seed " + seed + " has room to walk: " + w[0]);
            assertEquals(w[0], w[1], "seed " + seed + ": every standing cell can be walked to");
            for (Depths.Room r : d.rooms()) assertTrue(r.cells().size() >= 20, "seed " + seed + " room " + r.id() + " (" + r.kind() + ") has " + r.cells().size() + " cells");
        }
    }

    @Test
    void theSlabIsSealed() {
        for (long seed = 0; seed < 40; seed++) {
            Depths d = Depths.generate(seed, P);
            for (int x = 0; x < d.sizeX(); x++) for (int z = 0; z < d.sizeZ(); z++) {
                assertFalse(d.open(x, 0, z), "floor of the slab");
                assertFalse(d.open(x, Depths.HEIGHT - 1, z), "roof of the slab");
                if (x == 0 || z == 0 || x == d.sizeX() - 1 || z == d.sizeZ() - 1)
                    for (int y = 0; y < Depths.HEIGHT; y++) assertFalse(d.open(x, y, z), "outer wall at " + x + "," + y + "," + z);
            }
        }
    }

    @Test
    void aDoorOpensTheEntranceRoomToTheNearEdgeAndNothingElse() {
        for (long seed = 0; seed < 40; seed++) {
            Depths d = Depths.generate(seed, P, true);
            assertTrue(d.hasDoor());
            int cx = Depths.doorX(P);
            assertEquals(d.entrance()[0], cx);
            int open = 0;
            for (int x = 0; x < d.sizeX(); x++) for (int z = 0; z < d.sizeZ(); z++) for (int y = 0; y < Depths.HEIGHT; y++) {
                boolean edge = x == 0 || z == 0 || x == d.sizeX() - 1 || z == d.sizeZ() - 1 || y == 0 || y == Depths.HEIGHT - 1;
                if (!edge || !d.open(x, y, z)) continue;
                open++;
                assertEquals(0, z, "the only opening is in the near wall");
                assertTrue(Math.abs(x - cx) <= 2 && y >= Depths.STAND && y < Depths.STAND + 6, "and it is the doorway: " + x + "," + y + "," + z);
            }
            assertEquals(30, open, "a doorway five wide and six high");
            assertEquals(2, d.doorHalf());
            // the doorway leads on foot to the entrance room and so to everything
            assertTrue(d.open(cx, Depths.STAND, 0) && !d.open(cx, Depths.STAND - 1, 0));
            int[] w = d.walkability();
            assertEquals(w[0], w[1], "seed " + seed);
        }
        assertFalse(Depths.generate(1, P).hasDoor());
        assertEquals(-1, Depths.generate(1, P).doorHalf());
        // a narrow maze has a narrow doorway
        Depths narrow = Depths.generate(1, new Depths.Params(7, 7, 24, 0.75, 0.35, 3), true);
        assertEquals(1, narrow.doorHalf());
        assertTrue(narrow.open(Depths.doorX(P) + 1, Depths.STAND + 3, 0) && !narrow.open(Depths.doorX(P) + 2, Depths.STAND, 0) && !narrow.open(Depths.doorX(P), Depths.STAND + 4, 0));
        assertThrows(IllegalArgumentException.class, () -> new Depths.Params(7, 7, 24, 0.75, 0.35, 4));
    }

    /** The point of wide passages: the biggest Pokémon in the pools (2.7 wide, 5.1 tall) can reach every room. */
    @Test
    void aBodyThreeWideAndSixTallReachesEveryRoom() {
        for (long seed = 1; seed <= 60; seed++) {
            Depths d = Depths.generate(seed, P, true);
            BigBody.Space space = d::open;
            for (Depths.Room room : d.rooms()) {
                int steps = BigBody.walk(space, d.entrance(), at -> at[0] >= room.min()[0] && at[0] <= room.max()[0] && at[2] >= room.min()[2] && at[2] <= room.max()[2], 6);
                assertTrue(steps >= 0, "seed " + seed + ": a big body reaches " + room.kind() + " room " + room.id());
            }
            // ... and gets out through the doorway
            assertTrue(BigBody.walk(space, d.entrance(), at -> at[2] == 1, 6) > 0, "seed " + seed + ": and the doorway");
        }
        // In a narrow maze it cannot leave the entrance room.
        Depths narrow = Depths.generate(3, new Depths.Params(7, 7, 24, 0.75, 0.35, 3), true);
        Depths.Room far = narrow.rooms().get(narrow.rooms().size() - 1);
        assertEquals(-1, BigBody.walk(narrow::open, narrow.entrance(), at -> at[0] >= far.min()[0] && at[0] <= far.max()[0] && at[2] >= far.min()[2] && at[2] <= far.max()[2], 6));
    }

    @Test
    void theRoomCountFollowsOccupancyAndTheEntranceIsOnTheNearEdge() {
        Depths d = Depths.generate(7, P);
        assertEquals(Math.round(49 * 0.75), d.rooms().size());
        int[] e = d.entrance();
        assertEquals(3 * 24 + 12, e[0]);
        assertEquals(12, e[2]);
        assertTrue(d.open(e[0], e[1], e[2]) && d.open(e[0], e[1] + 1, e[2]) && !d.open(e[0], e[1] - 1, e[2]));
        Depths.Room first = d.roomAt(e[0], e[2]);
        assertNotNull(first);
        assertEquals(Depths.Kind.ENTRANCE, first.kind());
        assertEquals(1, d.rooms().stream().filter(r -> r.kind() == Depths.Kind.ENTRANCE).count());
    }

    @Test
    void thereAreLoopsNotJustATree() {
        int looped = 0;
        for (long seed = 0; seed < 30; seed++) {
            Depths d = Depths.generate(seed, P);
            assertTrue(d.passages() >= d.rooms().size() - 1, "at least a spanning tree");
            if (d.passages() > d.rooms().size() + 3) looped++;
        }
        assertTrue(looped >= 25, "most Depths have several loops: " + looped);
        Depths tree = Depths.generate(5, new Depths.Params(7, 7, 24, 0.75, 0.0));
        assertEquals(tree.rooms().size() - 1, tree.passages(), "no loops asked for, none made");
    }

    @Test
    void everyRoomShapeAppearsAndDeadEndsAreOftenVaults() {
        Set<Depths.Kind> seen = EnumSet.noneOf(Depths.Kind.class);
        int deadEnds = 0, vaults = 0;
        for (long seed = 0; seed < 60; seed++)
            for (Depths.Room r : Depths.generate(seed, P).rooms()) {
                seen.add(r.kind());
                if (r.doors() == 1 && r.kind() != Depths.Kind.ENTRANCE) { deadEnds++; if (r.kind() == Depths.Kind.VAULT) vaults++; }
                if (r.kind() == Depths.Kind.VAULT) assertEquals(1, r.doors(), "a vault is always a dead end");
            }
        assertEquals(EnumSet.allOf(Depths.Kind.class), seen);
        assertTrue(deadEnds > 20 && vaults > deadEnds / 2, vaults + " vaults of " + deadEnds + " dead ends");
    }

    @Test
    void roomsHaveLightsAndCellsInsideTheirBounds() {
        Depths d = Depths.generate(11, P);
        for (Depths.Room r : d.rooms()) {
            int lights = 0;
            for (int x = r.min()[0]; x <= r.max()[0]; x++) for (int z = r.min()[2]; z <= r.max()[2]; z++) for (int y = 0; y < Depths.HEIGHT; y++)
                if (d.role(x, y, z) == Depths.LIGHT) lights++;
            assertTrue(lights >= 4, r.id() + " is lit: " + lights);
            for (int[] c : r.cells()) {
                assertTrue(c[0] >= r.min()[0] && c[0] <= r.max()[0] && c[2] >= r.min()[2] && c[2] <= r.max()[2]);
                assertSame(r, d.roomAt(c[0], c[2]));
            }
        }
    }

    @Test
    void smallAndLargeGridsWork() {
        Depths small = Depths.generate(1, new Depths.Params(2, 2, 20, 1.0, 1.0));
        assertEquals(4, small.rooms().size());
        assertEquals(small.walkability()[0], small.walkability()[1]);
        Depths big = Depths.generate(1, new Depths.Params(10, 10, 28, 0.9, 0.3));
        assertEquals(90, big.rooms().size());
        assertEquals(big.walkability()[0], big.walkability()[1]);
    }

    @Test
    void badParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Depths.Params(1, 7, 24, 0.7, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new Depths.Params(7, 7, 12, 0.7, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new Depths.Params(7, 7, 24, 0, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new Depths.Params(7, 7, 24, 0.7, 1.5));
    }
}
