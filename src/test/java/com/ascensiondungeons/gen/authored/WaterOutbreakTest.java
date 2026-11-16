package com.ascensiondungeons.gen.authored;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.gen.depths.DepthsLink;
import com.ascensiondungeons.outbreak.FeaturePlanner;
import com.ascensiondungeons.outbreak.OutbreakClock;
import com.ascensiondungeons.outbreak.Spell;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The real dungeon data: every type dungeon is an Outbreak dive whose settings load and whose features land on sound ground. */
class WaterOutbreakTest {
    static final List<String> TYPES = List.of("normal", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost",
            "dragon", "dark", "steel", "fairy");

    @Test
    void everyTypeDungeonIsADiveAndNothingElseIs() {
        for (String id : AuthoredDungeons.ids()) {
            boolean type = TYPES.contains(id);
            assertEquals(type, AuthoredLayout.get(id).outbreak() != null, id);
            if (!type) assertTrue(AuthoredLayout.get(id).features(1).isEmpty(), id);
        }
        for (String id : TYPES) assertTrue(AuthoredDungeons.ids().contains(id), id + " is registered");
    }

    @Test
    void theSettingsBuildValidClocksAndSpells() {
        for (String id : TYPES) {
            JsonObject o = AuthoredLayout.get(id).outbreak();
            JsonObject c = Jx.o(o, "clock");
            assertDoesNotThrow(() -> new OutbreakClock.Params(Jx.i(c, "secondsPerStage", 0) * 20, Jx.i(c, "maxStage", -1), Jx.d(c, "spawnStep", -1),
                    Jx.d(c, "eliteStep", -1), Jx.d(c, "eliteCap", -1), Jx.i(c, "stagesPerTierStep", 0)), id);
            Set<String> ids = new HashSet<>();
            for (JsonElement e : Jx.a(o, "spells")) {
                JsonObject s = e.getAsJsonObject();
                Spell spell = new Spell(Jx.s(s, "id", null), Jx.s(s, "name", null), (int) (Jx.d(s, "cooldownSeconds", 0) * 20), (int) (Jx.d(s, "warnSeconds", 0) * 20),
                        Jx.d(s, "range", 0), Jx.d(s, "radius", 0), Jx.d(s, "damage", 0), Spell.Effect.valueOf(Jx.s(s, "effect", "none").toUpperCase()), Jx.d(s, "strength", 0));
                assertTrue(ids.add(spell.id()), id + " " + spell.id());
            }
            assertEquals(3, ids.size(), id + " has three spells");
            // every spell a spawn, the elite list or the gatekeeper names exists
            for (JsonElement e : Jx.a(Jx.o(o, "waves"), "spawns")) {
                String spell = Jx.s(e.getAsJsonObject(), "spell", null);
                if (spell != null) assertTrue(ids.contains(spell), id + " " + spell);
            }
            for (JsonElement e : Jx.a(Jx.o(o, "elite"), "spells")) assertTrue(ids.contains(e.getAsString()), id + " " + e.getAsString());
            for (JsonElement e : Jx.a(Jx.o(o, "gatekeeper"), "spells")) assertTrue(ids.contains(e.getAsString()), id + " " + e.getAsString());
            int toll = Jx.i(Jx.o(o, "portal"), "tollPercent", -1);
            assertEquals(35, toll, id + " toll");
            assertTrue(Jx.i(o, "durationMinutes", 0) >= 30, id + " is a long run");
        }
    }

    @Test
    void everyCheckpointAndPoolExistsInTheLayout() {
        for (String id : TYPES) {
            AuthoredLayout layout = AuthoredLayout.get(id);
            JsonObject o = layout.outbreak();
            Set<String> mechanics = new HashSet<>();
            for (JsonElement e : layout.data().mechanics) mechanics.add(Jx.s(e.getAsJsonObject(), "id", ""));
            assertTrue(Jx.a(o, "checkpoints").size() >= 2, id + " has checkpoints");
            for (JsonElement e : Jx.a(o, "checkpoints")) assertTrue(mechanics.contains(e.getAsString()), id + " " + e.getAsString());
            JsonObject pools = Jx.o(layout.data().pokemon, "pools");
            for (JsonElement e : Jx.a(Jx.o(o, "waves"), "spawns")) assertTrue(pools.has(Jx.s(e.getAsJsonObject(), "pokemon", "?")), id);
            assertTrue(pools.has(Jx.s(Jx.o(o, "guardians"), "pokemon", "?")), id);
            assertTrue(pools.has(Jx.s(Jx.o(o, "gatekeeper"), "pokemon", "?")), id);
        }
    }

    @Test
    void featuresArePlacedInFullOnOpenGroundForManySeeds() {
        for (String id : TYPES) {
            AuthoredLayout layout = AuthoredLayout.get(id);
            JsonObject f = Jx.o(layout.outbreak(), "features");
            DepthsLink link = layout.link();
            // the portal and everything the settings ask for; detached Depths add the stairway down and the way back up
            int wanted = (link.physical() ? 1 : 3) + Jx.i(f, "chests", 0) + Jx.i(f, "outbreakChests", 0) + Jx.i(f, "timerChests", 0) + Jx.i(f, "strongboxes", 0)
                    + Jx.i(f, "altars", 0);
            Set<String> bossRooms = new HashSet<>();
            for (JsonElement b : layout.data().bosses) bossRooms.add(Jx.s(b.getAsJsonObject(), "room", ""));
            for (long seed = 1; seed <= 40; seed++) {
                List<FeaturePlanner.Placement> plan = layout.features(seed);
                assertEquals(wanted, plan.size(), id + " seed " + seed);
                var depths = layout.depths(seed);
                int deep = 0;
                for (FeaturePlanner.Placement p : plan) {
                    int[] c = p.cell();
                    String where = id + " seed " + seed + " " + p.kind();
                    // Authored rooms can overlap, so ask the room the plan names rather than the first one at that spot.
                    AuthoredLayout.Room room = null;
                    for (AuthoredLayout.Room r : layout.rooms(seed)) if (r.id.equals(p.room())) room = r;
                    assertNotNull(room, where + " names a real room");
                    assertTrue(room.contains(c[0], c[1], c[2]), where + " is inside " + room.id);
                    if (AuthoredLayout.inDepths(room)) {
                        deep++;
                        int[] l = link.toLocal(c[0], c[1], c[2]);
                        assertTrue(depths.open(l[0], l[1], l[2]), where + " feet");
                        assertFalse(depths.open(l[0], l[1] - 1, l[2]), where + " floor");
                    } else {
                        assertEquals("minecraft:air", layout.stateAt(c[0], c[1], c[2]), where + " feet");
                        assertNotEquals("minecraft:air", layout.stateAt(c[0], c[1] - 1, c[2]), where + " floor");
                        assertNull(link.override(c[0], c[1], c[2]), where + " is not in the tunnel");
                    }
                    assertTrue(layout.passable(seed, c[0], c[1], c[2]) && !layout.passable(seed, c[0], c[1] - 1, c[2]), where + " stands on ground");
                    assertFalse(bossRooms.contains(p.room()), id + ": nothing in the boss's room");
                }
                assertTrue(deep >= wanted / 2, id + " seed " + seed + ": most features are down in the Depths (" + deep + " of " + wanted + ")");
            }
        }
    }

    @Test
    void everyDiveHasABigMazeThatChangesFromRunToRun() {
        for (String id : TYPES) {
            AuthoredLayout layout = AuthoredLayout.get(id);
            assertTrue(layout.hasDepths(), id);
            for (long seed = 1; seed <= 10; seed++) {
                var depths = layout.depths(seed);
                assertEquals(layout.data().rooms.length + depths.rooms().size(), layout.rooms(seed).length, id);
                assertTrue(depths.rooms().size() >= 30, id + " has a big maze: " + depths.rooms().size() + " rooms");
                assertEquals(layout.link().physical(), depths.hasDoor(), id + ": a joined maze has a doorway, a detached one is sealed");
                int[] e = depths.entrance(), at = layout.link().toSchem(e[0], e[1], e[2]);
                assertTrue(AuthoredLayout.inDepths(layout.roomAt(seed, at[0], at[1], at[2])), id + ": the maze's first room is a Depths room");
            }
            // a different run is a different maze
            var a = layout.depths(101);
            var b = layout.depths(202);
            boolean differs = a.rooms().size() != b.rooms().size();
            for (int x = 0; x < a.sizeX() && !differs; x += 5) for (int z = 0; z < a.sizeZ() && !differs; z += 5) differs = a.role(x, 2, z) != b.role(x, 2, z);
            assertTrue(differs, id);
        }
    }

    /** The point of joining them: from where the party lands, a player can walk out through the tunnel, down the stairway and into the maze. */
    @Test
    void everyTypeDungeonIsJoinedToItsDepthsByATunnelYouCanWalk() {
        StringBuilder report = new StringBuilder();
        for (String id : TYPES) {
            AuthoredLayout layout = AuthoredLayout.get(id);
            DepthsLink link = layout.link();
            report.append(String.format("%-9s %s%n", id, link.physical() ? link.side() + ", tunnel " + link.tunnelLength() + " blocks, mouth " + Arrays.toString(link.mouth())
                    : "DETACHED (teleporting stairway)"));
            if (!link.physical()) continue;
            assertSame(link, layout.link(), id + ": the way down is in the same place every run");
            int[] m = link.mouth();
            assertEquals(layout.entryRoom(), layout.roomAt(m[0], m[1], m[2]), id + ": the tunnel starts in the entrance room");
            for (long seed = 1; seed <= 6; seed++) {
                // No stairway pads: the walk replaces them.
                for (FeaturePlanner.Placement p : layout.features(seed))
                    assertTrue(p.kind() != FeaturePlanner.Kind.DESCENT && p.kind() != FeaturePlanner.Kind.RETURN, id + " seed " + seed);
                int[] e = layout.depths(seed).entrance(), goal = link.toSchem(e[0], e[1], e[2]);
                int steps = walk(layout, seed, layout.data().entry, goal);
                assertTrue(steps > 0, id + " seed " + seed + ": the Depths can be walked to from the entrance");
                assertTrue(steps >= DepthsLink.GAP, id + ": the walk goes down the stairway (" + steps + " steps)");
                assertTrue(walk(layout, seed, goal, layout.data().entry) > 0, id + " seed " + seed + ": and back up again");
                // and the whole maze hangs off it: the far corner room is reachable from the entrance too
                var rooms = layout.depths(seed).rooms();
                int[] far = rooms.get(rooms.size() - 1).cells().get(0), farAt = link.toSchem(far[0], far[1], far[2]);
                assertTrue(walk(layout, seed, layout.data().entry, farAt) > 0, id + " seed " + seed + ": the last room of the maze is reachable");
            }
        }
        System.out.print(report);
        assertFalse(report.toString().contains("DETACHED"), "every type dungeon has a real way down:\n" + report);
    }

    /**
     * Steps of the shortest walk between two standing cells, or -1. A walker is two blocks tall, moves one block at a time on
     * the four compass points, climbs one block (with headroom to jump) and drops up to three.
     */
    static int walk(AuthoredLayout layout, long seed, int[] from, int[] to) {
        java.util.Map<Long, Integer> seen = new java.util.HashMap<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        seen.put(DepthsLink.key(from[0], from[1], from[2]), 0);
        queue.add(from);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            int n = seen.get(DepthsLink.key(at[0], at[1], at[2]));
            if (at[0] == to[0] && at[1] == to[1] && at[2] == to[2]) return n;
            for (int[] s : steps) {
                int x = at[0] + s[0], z = at[2] + s[1];
                for (int dy = 1; dy >= -3; dy--) {
                    int y = at[1] + dy;
                    if (!layout.passable(seed, x, y, z) || !layout.passable(seed, x, y + 1, z) || layout.passable(seed, x, y - 1, z)) continue;
                    if (dy == 1 && !layout.passable(seed, at[0], at[1] + 2, at[2])) continue;       // no room to jump
                    boolean clear = true;                                                       // dropping: the column above the landing is open
                    for (int yy = y + 2; yy <= at[1] + 1 && clear; yy++) clear = layout.passable(seed, x, yy, z);
                    if (!clear) continue;
                    long k = DepthsLink.key(x, y, z);
                    if (seen.putIfAbsent(k, n + 1) == null) queue.add(new int[] {x, y, z});
                    break;
                }
            }
        }
        return -1;
    }

    @Test
    void theDepthsAreBuiltFromFourSafeBlocksOfTheDungeonsOwn() {
        for (String id : TYPES) {
            String[] p = AuthoredLayout.get(id).depthsPalette();
            assertEquals(4, p.length);
            for (String block : p) assertTrue(block.startsWith("minecraft:") && !block.contains("["), id + " " + block);
            assertNotEquals(p[0], p[1], id + ": wall and floor differ");
        }
        assertEquals("minecraft:packed_ice", AuthoredLayout.get("ice").depthsPalette()[3], "ice has a hand-picked palette");
        assertFalse(AuthoredLayout.buildingBlock("minecraft:sand"));
        assertFalse(AuthoredLayout.buildingBlock("minecraft:oak_stairs"));
        assertFalse(AuthoredLayout.buildingBlock("minecraft:water"));
        assertTrue(AuthoredLayout.buildingBlock("minecraft:stone_bricks"));
    }

    @Test
    void theStairwaysStepsAreSlabsOfTheDungeonsOwn() {
        assertEquals("minecraft:stone_brick_slab", AuthoredLayout.slabOf("minecraft:stone_bricks"));
        assertEquals("minecraft:deepslate_tile_slab", AuthoredLayout.slabOf("minecraft:deepslate_tiles"));
        assertEquals("minecraft:oak_slab", AuthoredLayout.slabOf("minecraft:oak_planks"));
        assertEquals("minecraft:andesite_slab", AuthoredLayout.slabOf("minecraft:andesite"));
        for (String id : TYPES) {
            AuthoredLayout layout = AuthoredLayout.get(id);
            List<String> steps = layout.depthsSteps();
            // the slab forms of the four Depths blocks come first, and there is always a plain one to fall back on
            String[] p = layout.depthsPalette();
            for (int i = 0; i < 4; i++) assertTrue(steps.indexOf(AuthoredLayout.slabOf(p[i])) <= i, id + " " + steps);
            assertTrue(steps.contains("minecraft:smooth_stone_slab"), id);
            for (String s : steps) assertTrue(s.endsWith("_slab"), id + " " + s);
            assertEquals(steps.size(), new HashSet<>(steps).size(), id + ": no slab is listed twice");
        }
    }

    @Test
    void theSamePlanComesBackForTheSameSeed() {
        AuthoredLayout water = AuthoredLayout.get("water");
        assertSame(water.features(77), water.features(77));
    }
}
