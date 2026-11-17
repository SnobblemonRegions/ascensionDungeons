package com.ascensiondungeons.gen.depths;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DepthsLinkTest {
    /** A 60 x 20 x 40 block of stone with rooms hollowed out of it; the cells of {@code special} override it. */
    private static final class Box implements DepthsLink.Grid {
        final String[] cells = new String[60 * 20 * 40];
        Box() { java.util.Arrays.fill(cells, "minecraft:stone"); }
        void set(int x, int y, int z, String s) { cells[(y * 40 + z) * 60 + x] = s; }
        void room(int x1, int z1, int x2, int z2) { for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) for (int y = 5; y <= 9; y++) set(x, y, z, "minecraft:air"); }
        @Override public int width() { return 60; }
        @Override public int height() { return 20; }
        @Override public int length() { return 40; }
        @Override public String state(int x, int y, int z) { return cells[(y * 40 + z) * 60 + x]; }
    }

    private static final int SIZE = 168, DOOR = 84;

    private static DepthsLink find(Box g, int[] start) { return DepthsLink.find(g, List.of(start), c -> false, null, SIZE, SIZE, DOOR); }

    @Test
    void itTunnelsOutThroughTheNearestWall() {
        Box g = new Box();
        g.room(6, 10, 20, 30);                       // six blocks from the west face, ten from the north
        DepthsLink link = find(g, new int[] {12, 5, 20});
        assertTrue(link.physical());
        assertEquals(DepthsLink.Side.WEST, link.side());
        assertArrayEquals(new int[] {6, 5, 20}, link.mouth(), "the mouth is the last room cell before the west wall");
        assertEquals(6, link.tunnelLength(), "six blocks of wall to cut");
        // the tunnel is open three wide and three high, the ring round it is left as the dungeon's own stone
        assertEquals(Depths.AIR, link.override(3, 5, 20));
        assertEquals(Depths.AIR, link.override(3, 7, 21));
        assertNull(link.override(3, 8, 20), "the roof of the tunnel is untouched stone");
        assertNull(link.override(3, 5, 22), "and so are its sides");
    }

    @Test
    void theStairwayDescendsAndEndsAtTheMazesDoor() {
        Box g = new Box();
        g.room(6, 10, 20, 30);
        DepthsLink link = find(g, new int[] {12, 5, 20});
        assertEquals(5, link.standLevel(0));
        assertEquals(5, link.standLevel(1));
        assertEquals(4, link.standLevel(2));
        assertEquals(5 - DepthsLink.DROP, link.standLevel(16));
        assertEquals(5 - DepthsLink.DROP, link.standLevel(DepthsLink.GAP - 1), "level again at the bottom");
        assertArrayEquals(new int[] {0, 5, 20}, link.stair(-1), "the last block of tunnel inside the dungeon");
        assertArrayEquals(new int[] {-1, 5, 20}, link.stair(0), "the first block outside it");
        assertArrayEquals(new int[] {-11, 0, 20}, link.stair(10), "ten blocks out, five steps down");
        // the maze's doorway (local z = 0, x = door) is the cell just past the stairway, at the stairway's bottom level
        int[] door = link.toSchem(DOOR, Depths.STAND, 0);
        assertArrayEquals(new int[] {-1 - DepthsLink.GAP, 5 - DepthsLink.DROP, 20}, door);
        // every step of the stairway is open, floored and roofed
        for (int t = 0; t < DepthsLink.GAP; t++) {
            int x = -1 - t, y = link.standLevel(t);
            assertTrue(open(link, x, y, 20) && open(link, x, y + 1, 20) && open(link, x, y + 2, 20), "step " + t);
            assertEquals(Depths.FLOOR, link.override(x, y - 1, 20), "floor under step " + t);
            assertEquals(Depths.WALL, link.override(x, y, 22), "wall beside step " + t);
            assertEquals(Depths.WALL, link.override(x, y + link.stairHeight(), 20), "roof over step " + t);
        }
        assertEquals(5, link.stairHeight(), "a narrow stairway is five high");
        assertEquals(3, link.width());
        assertEquals(3, link.clearHeight());
    }

    private static DepthsLink wide(Box g, int[] start) { return DepthsLink.findWidest(g, List.of(start), c -> false, c -> false, null, SIZE, SIZE, DOOR); }

    @Test
    void theWidestTunnelTheWallCanTakeIsCut() {
        // The room is five high (y 5..9) under ten blocks of stone, so a six-high tunnel fits beside it.
        Box g = new Box();
        g.room(8, 10, 20, 30);
        DepthsLink link = wide(g, new int[] {12, 5, 20});
        assertTrue(link.physical());
        assertEquals(5, link.width());
        assertEquals(6, link.clearHeight());
        assertEquals(7, link.stairHeight(), "the stairway is a block higher than the tunnel");
        // five wide and six high inside the dungeon, with the dungeon's own stone left round it
        for (int z = 18; z <= 22; z++) for (int y = 5; y <= 10; y++) assertTrue(open(link, 3, y, z), "tunnel " + y + " " + z);
        assertNull(link.override(3, 5, 23));
        assertNull(link.override(3, 5, 17));
        assertNull(link.override(3, 11, 20));
        // the stairway outside is five wide too, with a slab the whole width at each drop
        for (int z = 18; z <= 22; z++) assertEquals(DepthsLink.STEP, link.override(-3, 4, z));
        assertEquals(Depths.WALL, link.override(-3, 4, 23));
        // a big body walks it from the tunnel to the stairway's foot
        BigBody.Space space = (x, y, z) -> { Byte r = link.override(x, y, z); return r != null ? DepthsLink.open(r) : inside(g, x, y, z) && g.state(x, y, z).equals("minecraft:air"); };
        int[] foot = link.stair(DepthsLink.GAP - 2);
        assertTrue(BigBody.walk(space, link.stair(-2), at -> at[0] == foot[0] && at[2] == foot[2], 6) > 0, "three wide and six tall gets down the stairs");
    }

    private static boolean inside(Box g, int x, int y, int z) { return x >= 0 && y >= 0 && z >= 0 && x < g.width() && y < g.height() && z < g.length(); }

    @Test
    void aLowOrCrowdedWallGetsALowerOrNarrowerTunnel() {
        // Water three blocks above the floor on the only reachable wall: six, five and four high are refused, three is not.
        Box low = new Box();
        low.room(8, 10, 20, 30);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 40; z++) low.set(x, 9, z, "minecraft:water");
        for (int x = 0; x < 60; x++) for (int z = 0; z < 40; z++) if (z < 4 || z > 36 || x > 50) for (int y = 0; y < 20; y++) low.set(x, y, z, "minecraft:water");
        DepthsLink a = wide(low, new int[] {12, 5, 20});
        assertTrue(a.physical());
        assertEquals(DepthsLink.Side.WEST, a.side());
        assertEquals(5, a.width());
        assertEquals(3, a.clearHeight(), "only three blocks of wall under the water (a block is kept between): " + java.util.Arrays.toString(a.mouth()) + " " + a.side() + " len " + a.tunnelLength());
        // Another room close beside the way out on both sides: five wide would break into it, three wide fits.
        Box tight = new Box();
        tight.room(8, 18, 20, 22);
        for (int y = 5; y <= 9; y++) for (int x = 1; x <= 6; x++) { tight.set(x, y, 17, "minecraft:air"); tight.set(x, y, 23, "minecraft:air"); }
        for (int x = 0; x < 60; x++) for (int z = 0; z < 40; z++) if (z < 4 || z > 36 || x > 50) for (int y = 0; y < 20; y++) tight.set(x, y, z, "minecraft:water");
        DepthsLink b = wide(tight, new int[] {12, 5, 20});
        assertTrue(b.physical());
        assertEquals(3, b.width());
        assertThrows(IllegalArgumentException.class, () -> DepthsLink.find(tight, List.of(new int[] {12, 5, 20}), c -> false, c -> false, null, SIZE, SIZE, DOOR, 3, 3));
    }

    private static boolean open(DepthsLink link, int x, int y, int z) { Byte r = link.override(x, y, z); return r != null && DepthsLink.open(r); }

    @Test
    void everyDropOfTheStairwayIsHalvedByAStep() {
        Box g = new Box();
        g.room(6, 10, 20, 30);
        DepthsLink link = find(g, new int[] {12, 5, 20});
        int steps = 0;
        for (int t = 0; t < DepthsLink.GAP; t++) {
            int x = -1 - t, y = link.standLevel(t);
            boolean drops = t > 0 && y < link.standLevel(t - 1);
            for (int z = 19; z <= 21; z++) assertEquals(drops ? DepthsLink.STEP : Depths.AIR, link.override(x, y, z), "tread " + t + " z " + z);
            assertNotEquals(DepthsLink.STEP, link.override(x, y + 1, 20), "only the tread's own cell holds a step");
            if (drops) {
                steps++;
                // The step lies on a floor block, the tread before it is a block higher and the one after is level with it.
                assertEquals(Depths.FLOOR, link.override(x, y - 1, 20));
                assertEquals(y + 1, link.standLevel(t - 1));
                assertEquals(y, link.standLevel(t + 1));
                assertEquals(Depths.AIR, link.override(x - 1, y, 20));
            }
        }
        assertEquals(DepthsLink.DROP, steps, "one half step for every block the stairway descends");
        assertTrue(DepthsLink.open(DepthsLink.STEP) && DepthsLink.open(Depths.AIR) && DepthsLink.open(Depths.LIGHT));
        assertFalse(DepthsLink.open(Depths.WALL) || DepthsLink.open(Depths.FLOOR));
    }

    @Test
    void localAndDungeonCoordinatesRoundTripOnEverySide() {
        int[][] rooms = {{6, 10, 20, 30}, {40, 10, 53, 30}, {20, 6, 40, 20}, {20, 20, 40, 33}};
        DepthsLink.Side[] want = {DepthsLink.Side.WEST, DepthsLink.Side.EAST, DepthsLink.Side.NORTH, DepthsLink.Side.SOUTH};
        for (int i = 0; i < rooms.length; i++) {
            Box g = new Box();
            g.room(rooms[i][0], rooms[i][1], rooms[i][2], rooms[i][3]);
            DepthsLink link = find(g, new int[] {(rooms[i][0] + rooms[i][2]) / 2, 5, (rooms[i][1] + rooms[i][3]) / 2});
            assertEquals(want[i], link.side());
            for (int[] local : new int[][] {{0, 0, 0}, {DOOR, 2, 0}, {167, 11, 167}, {3, 5, 90}}) {
                int[] s = link.toSchem(local[0], local[1], local[2]);
                assertArrayEquals(local, link.toLocal(s[0], s[1], s[2]), want[i].name());
                boolean outside = s[0] < 0 || s[2] < 0 || s[0] >= 60 || s[2] >= 40;
                assertTrue(outside, want[i] + ": the maze is wholly outside the dungeon");
            }
            int[] reach = link.reach();
            assertTrue(reach[2] - reach[0] >= 167 && reach[3] - reach[1] >= 167);
        }
    }

    @Test
    void itNeverBreaksIntoAnotherRoom() {
        Box g = new Box();
        g.room(20, 10, 30, 30);
        g.room(4, 10, 14, 30);                       // a second room between the first and the west face
        g.room(36, 10, 55, 30);                      // and one to the east
        for (int z = 10; z <= 30; z++) for (int y = 5; y <= 9; y++) { g.set(20, y, 5, "minecraft:air"); }
        DepthsLink link = find(g, new int[] {25, 5, 20});
        assertTrue(link.physical());
        assertTrue(link.side() == DepthsLink.Side.NORTH || link.side() == DepthsLink.Side.SOUTH, "west and east would cut into the other rooms: " + link.side());
    }

    @Test
    void itNeverRunsBesideWaterOrCutsAMechanism() {
        Box wet = new Box();
        wet.room(6, 10, 20, 30);
        for (int z = 0; z < 40; z++) for (int y = 0; y < 20; y++) wet.set(3, y, z, "minecraft:water[level=0]");      // a sheet of water in the west wall
        assertNotEquals(DepthsLink.Side.WEST, find(wet, new int[] {12, 5, 20}).side());

        Box lever = new Box();
        lever.room(6, 10, 20, 30);
        for (int z = 0; z < 40; z++) for (int y = 0; y < 20; y++) lever.set(4, y, z, "minecraft:redstone_block");
        assertNotEquals(DepthsLink.Side.WEST, find(lever, new int[] {12, 5, 20}).side());

        Box sand = new Box();
        sand.room(6, 10, 20, 30);
        for (int z = 0; z < 40; z++) for (int x = 0; x < 6; x++) sand.set(x, 8, z, "minecraft:sand");                // sand as the tunnel's roof
        assertNotEquals(DepthsLink.Side.WEST, find(sand, new int[] {12, 5, 20}).side());

        Box above = new Box();
        above.room(6, 10, 20, 30);
        for (int z = 0; z < 40; z++) for (int x = 0; x < 6; x++) above.set(x, 9, z, "minecraft:sand");               // sand with a block of stone under it is fine
        assertEquals(DepthsLink.Side.WEST, find(above, new int[] {12, 5, 20}).side());
    }

    @Test
    void aRoomWithASlopingWallIsLeftThroughItsOwnAir() {
        // A dome: the west wall leans out one block further at each level above the floor.
        Box g = new Box();
        g.room(8, 10, 22, 30);
        for (int z = 10; z <= 30; z++) { g.set(7, 6, z, "minecraft:air"); g.set(7, 7, z, "minecraft:air"); g.set(6, 7, z, "minecraft:air"); }
        int[] start = {12, 5, 20};
        // Without knowing the room, the air beside the tunnel looks like another room and the west wall is refused.
        assertNotEquals(DepthsLink.Side.WEST, find(g, start).side());
        DepthsLink link = DepthsLink.find(g, List.of(start), c -> false, c -> c[0] >= 6 && c[0] <= 22 && c[2] >= 10 && c[2] <= 30, null, SIZE, SIZE, DOOR);
        assertTrue(link.physical());
        assertEquals(DepthsLink.Side.WEST, link.side());
        assertArrayEquals(new int[] {8, 5, 20}, link.mouth());
        assertEquals(8, link.tunnelLength());
        // ... but only on the way out. A second pocket of the room's air further along the wall is still refused.
        Box pocket = new Box();
        pocket.room(12, 10, 22, 30);
        for (int y = 5; y <= 7; y++) pocket.set(4, y, 22, "minecraft:air");
        assertNotEquals(DepthsLink.Side.WEST,
                DepthsLink.find(pocket, List.of(start), c -> false, c -> c[0] <= 22 && c[2] >= 10 && c[2] <= 30, null, SIZE, SIZE, DOOR).side());
    }

    @Test
    void theMouthPrefersTheFloorThePartyLandsOn() {
        // Two ways out of the same room: a short one from a ledge four blocks up, a longer one from the floor.
        Box g = new Box();
        g.room(10, 10, 20, 30);
        for (int x = 4; x <= 9; x++) for (int z = 12; z <= 16; z++) for (int y = 9; y <= 11; y++) g.set(x, y, z, "minecraft:air");
        DepthsLink link = DepthsLink.find(g, List.of(new int[] {6, 9, 14}, new int[] {15, 5, 20}), c -> false, new int[] {15, 5, 28}, SIZE, SIZE, DOOR);
        assertEquals(5, link.mouth()[1], "the tunnel starts at floor level");
    }

    @Test
    void reservedCellsAreNeverCut() {
        Box g = new Box();
        g.room(6, 10, 20, 30);
        DepthsLink link = DepthsLink.find(g, List.of(new int[] {12, 5, 20}), c -> c[0] < 6, null, SIZE, SIZE, DOOR);
        assertNotEquals(DepthsLink.Side.WEST, link.side(), "the whole west wall is reserved");
    }

    @Test
    void theMouthKeepsAwayFromWhereThePartyArrives() {
        Box g = new Box();
        g.room(6, 6, 30, 30);
        // two candidate starts, both six blocks from a wall; the one beside the arrival cell is avoided
        DepthsLink link = DepthsLink.find(g, List.of(new int[] {8, 5, 8}, new int[] {8, 5, 26}), c -> false, new int[] {7, 5, 8}, SIZE, SIZE, DOOR);
        assertTrue(link.mouth()[2] >= 20, "the far start wins: " + java.util.Arrays.toString(link.mouth()));
    }

    @Test
    void whenNothingIsSafeTheDepthsStayDetached() {
        Box g = new Box();
        g.room(6, 10, 20, 30);
        DepthsLink link = DepthsLink.find(g, List.of(new int[] {12, 5, 20}), c -> true, null, SIZE, SIZE, DOOR);
        assertFalse(link.physical());
        assertNull(link.mouth());
        assertTrue(link.overrides().isEmpty());
        assertArrayEquals(new int[] {0, 2, 40 + DepthsLink.DETACHED_GAP}, link.toSchem(0, 0, 0), "beyond the far face, as before");
        assertArrayEquals(new int[] {5, 7, 9}, link.toLocal(5, 9, 40 + DepthsLink.DETACHED_GAP + 9));
    }

    /** Walk on foot from the tunnel mouth, down the stairway, to the maze's doorway. */
    @Test
    void theWholeWayCanBeWalked() {
        Box g = new Box();
        g.room(6, 10, 20, 30);
        DepthsLink link = find(g, new int[] {12, 5, 20});
        int[] door = link.toSchem(DOOR, Depths.STAND, 0);
        int[] goal = {door[0] + 1, door[1], door[2]};          // the last step of the stairway, beside the doorway
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(link.mouth());
        seen.add(DepthsLink.key(6, 5, 20));
        boolean reached = false;
        while (!queue.isEmpty() && !reached) {
            int[] at = queue.poll();
            for (int[] s : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int dy = -1; dy <= 1; dy++) {
                int x = at[0] + s[0], y = at[1] + dy, z = at[2] + s[1];
                boolean stand = open(link, x, y, z) && open(link, x, y + 1, z) && !open(link, x, y - 1, z) && (link.override(x, y - 1, z) != null || x >= 0);
                if (!stand || !seen.add(DepthsLink.key(x, y, z))) continue;
                if (x == goal[0] && y == goal[1] && z == goal[2]) reached = true;
                queue.add(new int[] {x, y, z});
            }
        }
        assertTrue(reached, "mouth to maze door on foot");
    }
}
