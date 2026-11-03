package com.ascensiondungeons.gen.landmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ascensiondungeons.gen.SpongeSchematic;
import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.layout.VerdantLayout;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The rules from the handoff: connected supports, persistent leaves, routes and walls untouched, chest reachable. */
class RootheartGroveTest {
    private static SpongeSchematic base;
    private static Composer.Result composed;
    private static Voxels grove;
    private static final Set<String> ROUTE_FLOOR = Set.of("minecraft:stone_bricks", "minecraft:mossy_stone_bricks",
            "minecraft:tuff_bricks", "minecraft:deepslate_bricks", "minecraft:andesite");
    private static final Set<String> WALKTHROUGH = Set.of("minecraft:air", "minecraft:short_grass", "minecraft:fern",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:cornflower", "minecraft:flowering_azalea");

    @BeforeAll
    static void load() {
        // The bare Verdant terrain with one ordinary oak planted inside the grove's footprint.
        var canvas = com.ascensiondungeons.gen.layout.Canvas.of(VerdantLayout.terrain());
        int tx = VerdantLayout.GROVE_X + 3, tz = VerdantLayout.GROVE_Z + 3;
        for (int y = 5; y <= 12; y++) canvas.set(tx, y, tz, "minecraft:oak_log[axis=y]");
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            canvas.set(tx + dx, 12, tz + dz, "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]");
        }
        base = canvas.toSchematic();
        grove = RootheartGrove.build();
        composed = Composer.apply(base, List.of(RootheartGrove.landmarkAt(VerdantLayout.GROVE_X, VerdantLayout.GROVE_Z)));
    }

    private static boolean log(String s) { return s != null && (s.contains("oak_log") || s.contains("froglight")); }

    private static boolean leaf(String s) { return s != null && s.contains("oak_leaves"); }

    @Test
    void fitsTheEnvelopeFromTheAtlas() {
        int minU = 99, maxU = -99, minW = 99, maxW = -99, maxY = 0;
        int baseMin = 99, baseMax = -99;
        for (int y = 0; y < grove.sizeY(); y++) {
            for (int z = 0; z < grove.sizeZ(); z++) {
                for (int x = 0; x < grove.sizeX(); x++) {
                    if (!grove.has(x, y, z)) continue;
                    minU = Math.min(minU, x - 10);
                    maxU = Math.max(maxU, x - 10);
                    minW = Math.min(minW, z - 10);
                    maxW = Math.max(maxW, z - 10);
                    maxY = Math.max(maxY, y);
                    if (y <= 3) {
                        baseMin = Math.min(baseMin, Math.min(x, z) - 10);
                        baseMax = Math.max(baseMax, Math.max(x, z) - 10);
                    }
                }
            }
        }
        assertTrue(maxU - minU + 1 <= 21 && maxW - minW + 1 <= 21, "crown wider than 21");
        assertTrue(baseMax - baseMin + 1 <= 13, "root base wider than 13");
        assertEquals(29, maxY, "tree should reach 29 above the floor");
    }

    @Test
    void everyLogIsConnectedToTheGround() {
        boolean[][][] seen = new boolean[grove.sizeX()][grove.sizeY()][grove.sizeZ()];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int z = 0; z < grove.sizeZ(); z++) {
            for (int x = 0; x < grove.sizeX(); x++) {
                if (grove.has(x, 0, z)) {
                    seen[x][0][z] = true;
                    queue.add(new int[] {x, 0, z});
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
                if (!grove.inBounds(x, y, z) || seen[x][y][z] || !grove.has(x, y, z) || leaf(grove.get(x, y, z))) continue;
                seen[x][y][z] = true;
                queue.add(new int[] {x, y, z});
            }
        }
        for (int y = 0; y < grove.sizeY(); y++) {
            for (int z = 0; z < grove.sizeZ(); z++) {
                for (int x = 0; x < grove.sizeX(); x++) {
                    if (grove.has(x, y, z) && !leaf(grove.get(x, y, z))) {
                        assertTrue(seen[x][y][z], "floating block " + grove.get(x, y, z) + " at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void leavesArePersistentAndNeverFarFromWood() {
        // Persistent leaves never decay, so vanilla distance 7 does not apply; this keeps every leaf visibly attached.
        int[][][] dist = new int[grove.sizeX()][grove.sizeY()][grove.sizeZ()];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < grove.sizeY(); y++) {
            for (int z = 0; z < grove.sizeZ(); z++) {
                for (int x = 0; x < grove.sizeX(); x++) {
                    if (log(grove.get(x, y, z))) {
                        dist[x][y][z] = 1;
                        queue.add(new int[] {x, y, z});
                    }
                    if (leaf(grove.get(x, y, z))) assertTrue(grove.get(x, y, z).contains("persistent=true"));
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
                if (!grove.inBounds(x, y, z) || dist[x][y][z] != 0 || !leaf(grove.get(x, y, z))) continue;
                dist[x][y][z] = dist[p[0]][p[1]][p[2]] + 1;
                queue.add(new int[] {x, y, z});
            }
        }
        for (int y = 0; y < grove.sizeY(); y++) {
            for (int z = 0; z < grove.sizeZ(); z++) {
                for (int x = 0; x < grove.sizeX(); x++) {
                    if (leaf(grove.get(x, y, z))) {
                        assertTrue(dist[x][y][z] > 0 && dist[x][y][z] <= 9, "leaf cut off from wood at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void branchesStartAbovePlayerHeight() {
        // Everything except trunk, roots and the chest recess is above head height (floor row + 12).
        for (int y = 1; y <= 12; y++) {
            for (int z = 0; z < grove.sizeZ(); z++) {
                for (int x = 0; x < grove.sizeX(); x++) {
                    if (!leaf(grove.get(x, y, z))) continue;
                    throw new AssertionError("leaves at head height: " + x + "," + y + "," + z);
                }
            }
        }
    }

    private String baseBlock(int x, int y, int z) { return base.palette()[base.at(x, y, z)]; }

    @Test
    void routesAndWallsAreUntouched() {
        for (int y = 0; y < grove.sizeY(); y++) {
            for (int z = 0; z < grove.sizeZ(); z++) {
                for (int x = 0; x < grove.sizeX(); x++) {
                    if (!grove.has(x, y, z)) continue;
                    int tx = VerdantLayout.GROVE_X + x, ty = RootheartGrove.ORIGIN_Y + y, tz = VerdantLayout.GROVE_Z + z;
                    // Nothing is built over a path or a water pool (check the floor under the column).
                    // Canopy may overhang a path from y=13 up (a path needs eight blocks of clearance).
                    String floor = baseBlock(tx, RootheartGrove.ORIGIN_Y, tz);
                    if (y <= 12) assertFalse(ROUTE_FLOOR.contains(floor), "built over a path at " + tx + "," + tz + " (" + floor + ")");
                    assertFalse(floor.contains("water"), "built over water at " + tx + "," + tz);
                    // Only vegetation or air was there before: no wall, ceiling or structure was replaced above the floor.
                    if (y > 0) {
                        String was = baseBlock(tx, ty, tz);
                        boolean harmless = WALKTHROUGH.contains(was) || was.startsWith("minecraft:light") || Landmark.TREE_BLOCK.test(was)
                                || was.startsWith("minecraft:flowering_azalea");
                        assertTrue(harmless, "replaced " + was + " at " + tx + "," + ty + "," + tz);
                    }
                }
            }
        }
    }

    @Test
    void staysWellBelowTheVault() {
        for (int z = 0; z < grove.sizeZ(); z++) {
            for (int x = 0; x < grove.sizeX(); x++) {
                int top = -1;
                for (int y = 0; y < grove.sizeY(); y++) if (grove.has(x, y, z)) top = y;
                if (top < 0) continue;
                int tx = VerdantLayout.GROVE_X + x, tz = VerdantLayout.GROVE_Z + z;
                int underside = 99;
                for (int y = 95; y > 4; y--) {
                    String b = baseBlock(tx, y, tz);
                    if (!(b.equals("minecraft:air") || b.startsWith("minecraft:light") || Landmark.TREE_BLOCK.test(b)
                            || WALKTHROUGH.contains(b))) underside = y;
                }
                assertTrue(RootheartGrove.ORIGIN_Y + top <= underside - 3, "too close to the vault at " + tx + "," + tz);
            }
        }
    }

    @Test
    void removesOrdinaryTreesButNothingElseOutsideItsBox() {
        assertTrue(composed.removedTreeBlocks() > 0, "expected an ordinary tree in the footprint");
        SpongeSchematic out = composed.schematic();
        int ox = VerdantLayout.GROVE_X, oy = RootheartGrove.ORIGIN_Y, oz = VerdantLayout.GROVE_Z;
        int changedOutside = 0;
        for (int y = 0; y < base.height(); y++) {
            for (int z = 0; z < base.length(); z++) {
                for (int x = 0; x < base.width(); x++) {
                    String a = base.palette()[base.at(x, y, z)], b = out.palette()[out.at(x, y, z)];
                    if (a.equals(b)) continue;
                    boolean inBox = x >= ox && x < ox + 21 && y >= oy && y < oy + 30 && z >= oz && z < oz + 21;
                    if (inBox) continue;
                    changedOutside++;
                    assertTrue(Landmark.TREE_BLOCK.test(a) && b.equals("minecraft:air"), "unexpected change " + a + " -> " + b);
                }
            }
        }
        assertEquals(composed.removedTreeBlocks() - countRemovedInsideBox(out), changedOutside);
    }

    private int countRemovedInsideBox(SpongeSchematic out) {
        int ox = VerdantLayout.GROVE_X, oy = RootheartGrove.ORIGIN_Y, oz = VerdantLayout.GROVE_Z;
        int n = 0;
        for (int y = oy; y < oy + 30; y++) {
            for (int z = oz; z < oz + 21; z++) {
                for (int x = ox; x < ox + 21; x++) {
                    if (Landmark.TREE_BLOCK.test(base.palette()[base.at(x, y, z)])) n++;
                }
            }
        }
        return n;
    }

    @Test
    void chestIsRegisteredAndReachableOnFoot() {
        Template t = new Template(composed.schematic());
        assertEquals(1, t.chestMarkers().size(), "Rootheart's chest");
        int cx = VerdantLayout.GROVE_X + 10, cy = RootheartGrove.ORIGIN_Y + 1, cz = VerdantLayout.GROVE_Z + 13;
        assertTrue(t.chestMarkers().stream().anyMatch(m -> m[0] == cx && m[1] == cy && m[2] == cz));
        SpongeSchematic s = composed.schematic();
        // Walk from the cell in front of the chest to a path, always with two blocks of headroom.
        int[] start = {cx, VerdantLayout.GROVE_Z + 14 + 0};
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        boolean[][] seen = new boolean[176][176];
        queue.add(new int[] {cx, cz + 1});
        seen[cz + 1][cx] = true;
        boolean reachedPath = false;
        int limit = 4000;
        while (!queue.isEmpty() && limit-- > 0) {
            int[] p = queue.poll();
            if (ROUTE_FLOOR.contains(s.palette()[s.at(p[0], 4, p[1])])) {
                reachedPath = true;
                break;
            }
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = p[0] + d[0], z = p[1] + d[1];
                if (x < 0 || z < 0 || x > 175 || z > 175 || seen[z][x]) continue;
                String feet = s.palette()[s.at(x, 5, z)], head = s.palette()[s.at(x, 6, z)];
                String floor = s.palette()[s.at(x, 4, z)];
                boolean walkable = (WALKTHROUGH.contains(feet) || feet.startsWith("minecraft:light"))
                        && (WALKTHROUGH.contains(head) || head.startsWith("minecraft:light"))
                        && !floor.equals("minecraft:air") && !floor.contains("water") && !floor.contains("lava");
                if (!walkable) continue;
                seen[z][x] = true;
                queue.add(new int[] {x, z});
            }
        }
        assertTrue(reachedPath, "no walkable way from the chest to a path");
        assertEquals(cx, start[0]);
    }
}
