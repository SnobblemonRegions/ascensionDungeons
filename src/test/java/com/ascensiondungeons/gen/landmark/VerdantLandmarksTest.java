package com.ascensiondungeons.gen.landmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ascensiondungeons.gen.SpongeSchematic;
import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.layout.VerdantLayout;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Glowcap Spring, Rootbound Cache and Warden's Ring composed with the Rootheart Grove into the Verdant district. */
class VerdantLandmarksTest {
    private static SpongeSchematic composed;

    private static List<Landmark> landmarks;

    @BeforeAll
    static void load() {
        var layout = com.ascensiondungeons.gen.layout.Layouts.of(com.ascensiondungeons.gen.BiomeKind.VERDANT_CANOPY);
        landmarks = layout.landmarks();
        composed = Composer.apply(layout.base(), landmarks).schematic();
    }

    private static String at(int x, int y, int z) {
        return composed.palette()[composed.at(x, y, z)];
    }

    private static boolean air(int x, int y, int z) {
        return at(x, y, z).equals("minecraft:air") || at(x, y, z).startsWith("minecraft:light[");
    }

    @Test
    void landmarksDoNotOverlap() {
        List<Landmark> all = landmarks;
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Landmark a = all.get(i), b = all.get(j);
                boolean apart = a.originX() + a.voxels().sizeX() <= b.originX() || b.originX() + b.voxels().sizeX() <= a.originX()
                        || a.originZ() + a.voxels().sizeZ() <= b.originZ() || b.originZ() + b.voxels().sizeZ() <= a.originZ();
                assertTrue(apart, a.id() + " overlaps " + b.id());
            }
        }
    }

    @Test
    void threeChestsAndTheOldPlatformIsGone() {
        Template t = new Template(composed);
        assertEquals(3, t.chestMarkers().size(), "grove, spring and cache");
        // Cache chest is at module (7, 2, 4); the spring chest at (16, 1, 16).
        int cx = VerdantLayout.CACHE_X + 7, cy = 4 + 2, cz = VerdantLayout.CACHE_Z + 4;
        assertTrue(t.chestMarkers().stream().anyMatch(m -> m[0] == cx && m[1] == cy && m[2] == cz));
        int sx = VerdantLayout.SPRING_X + 16, sy = 4 + 1, sz = VerdantLayout.SPRING_Z + 16;
        assertTrue(t.chestMarkers().stream().anyMatch(m -> m[0] == sx && m[1] == sy && m[2] == sz));
    }

    @Test
    void cacheChestIsVisibleThroughTheArchAndReachable() {
        int ox = VerdantLayout.CACHE_X, oy = 4, oz = VerdantLayout.CACHE_Z;
        // The line from the outside, up the middle of the arch, to the chest's front is open at head height.
        for (int z = 5; z <= 12; z++) {
            assertTrue(air(ox + 7, oy + 2, oz + z), "centre line closed at module z=" + z);
            assertTrue(air(ox + 7, oy + 3, oz + z), "centre line closed above at module z=" + z);
        }
        // The arch is a doorway: 3 tall at its edges on the 7-wide base.
        for (int x = 4; x <= 10; x++) for (int y = 1; y <= 3; y++) assertTrue(air(ox + x, oy + y, oz + 10));
    }

    @Test
    void springWaterIsContained() {
        int ox = VerdantLayout.SPRING_X, oy = 4, oz = VerdantLayout.SPRING_Z;
        // Every falling or source water cell of the waterfall: side neighbours are rock or other water, except the free
        // faces of the falling column (air, which falling water does not flow into).
        for (int z = 11; z <= 13; z++) {
            for (int y = 1; y <= 6; y++) assertTrue(at(ox + 15, oy + y, oz + z).startsWith("minecraft:water[level=8"));
            for (int x = 16; x <= 18; x++) {
                assertEquals("minecraft:water[level=0]", at(ox + x, oy + 7, oz + z));
                assertTrue(at(ox + x, oy + 6, oz + z).equals("minecraft:tuff") || at(ox + x, oy + 6, oz + z).contains("tuff")
                        || at(ox + x, oy + 6, oz + z).contains("cobblestone"), "basin floor under " + x + "," + z);
            }
            for (int x = 13; x <= 15; x++) assertEquals("minecraft:water[level=0]", at(ox + x, oy, oz + z));
        }
        for (int x = 15; x <= 18; x++) {
            assertFalse(air(ox + x, oy + 7, oz + 10), "rim north");
            assertFalse(air(ox + x, oy + 7, oz + 14), "rim south");
        }
        assertFalse(air(ox + 19, oy + 7, oz + 12), "back of the basin");
        // The fall must not run onto a route: the trough joins the pool and nothing else carries water.
        assertTrue(at(ox + 12, oy, oz + 12).startsWith("minecraft:water"), "trough joins the pool");
    }

    @Test
    void mushroomCapsAreAttachedToStems() {
        int ox = VerdantLayout.SPRING_X, oy = 4, oz = VerdantLayout.SPRING_Z;
        int[][] shrooms = {{7, 3, 12}, {12, 4, 9}, {3, 4, 6}};
        for (int[] s : shrooms) {
            for (int y = 1; y < s[2]; y++) assertTrue(at(ox + s[0], oy + y, oz + s[1]).startsWith("minecraft:mushroom_stem"));
            assertTrue(at(ox + s[0], oy + s[2], oz + s[1]).startsWith("minecraft:brown_mushroom_block"));
        }
    }

    @Test
    void arenaCombatDiscStaysFlatAndClear() {
        for (int dz = -WardensRing.COMBAT_RADIUS; dz <= WardensRing.COMBAT_RADIUS; dz++) {
            for (int dx = -WardensRing.COMBAT_RADIUS; dx <= WardensRing.COMBAT_RADIUS; dx++) {
                if (Math.hypot(dx, dz) > WardensRing.COMBAT_RADIUS - 0.6) continue;
                int x = VerdantLayout.ARENA_X + dx, z = VerdantLayout.ARENA_Z + dz;
                for (int y = 5; y <= 14; y++) assertTrue(air(x, y, z), "blocked at " + x + "," + y + "," + z);
                assertFalse(air(x, 4, z), "floor missing at " + x + "," + z);
            }
        }
    }

    @Test
    void arenaDoorwayStaysOpenFromTheOutside() {
        // The markers lie on the clear approach strip and inside the chamber.
        for (int[] m : new int[][] {WardensRing.pointToward(VerdantLayout.ARENA_X, VerdantLayout.ARENA_Z, VerdantLayout.ARENA_DOOR, 15),
                WardensRing.pointToward(VerdantLayout.ARENA_X, VerdantLayout.ARENA_Z, VerdantLayout.ARENA_DOOR, 17)}) {
            assertTrue(air(m[0], 5, m[2]) && air(m[0], 6, m[2]), "marker blocked at " + m[0] + "," + m[2]);
        }
        // Walk the door ray from the centre outward: nothing solid stands in the way at head height.
        double a = Math.toRadians(VerdantLayout.ARENA_DOOR);
        for (double r = 0; r <= 24; r += 0.5) {
            int x = (int) Math.round(VerdantLayout.ARENA_X + r * Math.cos(a));
            int z = (int) Math.round(VerdantLayout.ARENA_Z + r * Math.sin(a));
            assertTrue(air(x, 6, z) && air(x, 7, z), "door ray blocked at r=" + r + " (" + x + "," + z + ")");
        }
    }

    @Test
    void arenaPilastersAreAttachedToTheWall() {
        // Every non-air block of the crescent at y>=6 has a solid neighbour (no floating pieces).
        Landmark lm = landmarks.get(3);
        Voxels v = lm.voxels();
        for (int y = 2; y < v.sizeY(); y++) {
            for (int z = 0; z < v.sizeZ(); z++) {
                for (int x = 0; x < v.sizeX(); x++) {
                    if (!v.has(x, y, z)) continue;
                    int tx = lm.originX() + x, ty = lm.originY() + y, tz = lm.originZ() + z;
                    boolean supported = !air(tx, ty - 1, tz) || !air(tx + 1, ty, tz) || !air(tx - 1, ty, tz)
                            || !air(tx, ty, tz + 1) || !air(tx, ty, tz - 1) || !air(tx, ty + 1, tz);
                    assertTrue(supported, "floating block at " + tx + "," + ty + "," + tz);
                }
            }
        }
    }
}
