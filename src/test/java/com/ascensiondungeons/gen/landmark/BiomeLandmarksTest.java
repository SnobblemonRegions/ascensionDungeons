package com.ascensiondungeons.gen.landmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ascensiondungeons.gen.BiomeKind;
import com.ascensiondungeons.gen.SpongeSchematic;
import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.layout.AshenLayout;
import com.ascensiondungeons.gen.layout.BadlandsLayout;
import com.ascensiondungeons.gen.layout.CathedralLayout;
import com.ascensiondungeons.gen.layout.CherryLayout;
import com.ascensiondungeons.gen.layout.EndSpireLayout;
import com.ascensiondungeons.gen.layout.LibraryLayout;
import com.ascensiondungeons.gen.layout.NecropolisLayout;
import com.ascensiondungeons.gen.layout.ObservatoryLayout;
import com.ascensiondungeons.gen.layout.WarpedLayout;
import com.ascensiondungeons.gen.layout.CrystalLayout;
import com.ascensiondungeons.gen.layout.FloodedLayout;
import com.ascensiondungeons.gen.layout.FoundryLayout;
import com.ascensiondungeons.gen.layout.FungalLayout;
import com.ascensiondungeons.gen.layout.SunkenLayout;
import com.ascensiondungeons.gen.layout.VerdantLayout;
import com.ascensiondungeons.gen.layout.GlacialLayout;
import com.ascensiondungeons.gen.layout.Layout;
import com.ascensiondungeons.gen.layout.Layouts;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Rules every biome's layout and landmark set keeps, and the specifics of the Ashen Caldera and Glacial Vault. */
class BiomeLandmarksTest {
    private static final BiomeKind[] BIOMES = {BiomeKind.VERDANT_CANOPY, BiomeKind.ASHEN_CALDERA, BiomeKind.GLACIAL_VAULT,
            BiomeKind.CRYSTAL_CAVERN, BiomeKind.FLOODED_RUINS, BiomeKind.FUNGAL_GROTTO, BiomeKind.SUNKEN_GARDEN, BiomeKind.ANCIENT_FOUNDRY,
            BiomeKind.SUNSCORCHED_NECROPOLIS, BiomeKind.PAINTED_BADLANDS, BiomeKind.CHERRY_GROVE, BiomeKind.LAPIS_OBSERVATORY,
            BiomeKind.CRIMSON_CATHEDRAL, BiomeKind.GILDED_LIBRARY, BiomeKind.WARPED_HOLLOW, BiomeKind.END_SPIRE};
    private static final BiomeKind[] NEW = {BiomeKind.ASHEN_CALDERA, BiomeKind.GLACIAL_VAULT, BiomeKind.FLOODED_RUINS,
            BiomeKind.FUNGAL_GROTTO, BiomeKind.SUNKEN_GARDEN, BiomeKind.ANCIENT_FOUNDRY,
            BiomeKind.SUNSCORCHED_NECROPOLIS, BiomeKind.PAINTED_BADLANDS, BiomeKind.CHERRY_GROVE, BiomeKind.LAPIS_OBSERVATORY,
            BiomeKind.CRIMSON_CATHEDRAL, BiomeKind.GILDED_LIBRARY, BiomeKind.WARPED_HOLLOW, BiomeKind.END_SPIRE};
    /** The eight biomes added after the first eight: three landmarks each (navigation, loot, raid). */
    private static final BiomeKind[] SECOND_EIGHT = {BiomeKind.SUNSCORCHED_NECROPOLIS, BiomeKind.PAINTED_BADLANDS,
            BiomeKind.CHERRY_GROVE, BiomeKind.LAPIS_OBSERVATORY, BiomeKind.CRIMSON_CATHEDRAL, BiomeKind.GILDED_LIBRARY,
            BiomeKind.WARPED_HOLLOW, BiomeKind.END_SPIRE};
    private static final BiomeKind[] AUTHORED = {BiomeKind.ASHEN_CALDERA, BiomeKind.GLACIAL_VAULT, BiomeKind.CRYSTAL_CAVERN,
            BiomeKind.FLOODED_RUINS, BiomeKind.FUNGAL_GROTTO, BiomeKind.SUNKEN_GARDEN,
            BiomeKind.ANCIENT_FOUNDRY, BiomeKind.SUNSCORCHED_NECROPOLIS, BiomeKind.PAINTED_BADLANDS, BiomeKind.CHERRY_GROVE,
            BiomeKind.LAPIS_OBSERVATORY, BiomeKind.CRIMSON_CATHEDRAL, BiomeKind.GILDED_LIBRARY, BiomeKind.WARPED_HOLLOW,
            BiomeKind.END_SPIRE};

    private static final Map<BiomeKind, SpongeSchematic> composed = new EnumMap<>(BiomeKind.class);
    private static final Map<BiomeKind, Template> templates = new EnumMap<>(BiomeKind.class);

    @BeforeAll
    static void load() {
        for (BiomeKind b : BIOMES) {
            Layout layout = Layouts.of(b);
            SpongeSchematic s = Composer.apply(layout.base(), layout.landmarks()).schematic();
            composed.put(b, s);
            templates.put(b, new Template(s));
        }
    }

    private static String at(BiomeKind b, int x, int y, int z) {
        SpongeSchematic s = composed.get(b);
        return s.palette()[s.at(x, y, z)];
    }

    private static boolean open(BiomeKind b, int x, int y, int z) {
        String n = at(b, x, y, z);
        return n.equals("minecraft:air") || n.startsWith("minecraft:light[");
    }

    /** Arena centre and door direction in each biome's layout. */
    private static int[] arena(BiomeKind b) {
        return switch (b) {
            case VERDANT_CANOPY -> new int[] {VerdantLayout.ARENA_X, VerdantLayout.ARENA_Z};
            case ASHEN_CALDERA -> new int[] {AshenLayout.ARENA_X, AshenLayout.ARENA_Z};
            case CRYSTAL_CAVERN -> new int[] {CrystalLayout.ARENA_X, CrystalLayout.ARENA_Z};
            case FLOODED_RUINS -> new int[] {FloodedLayout.ARENA_X, FloodedLayout.ARENA_Z};
            case FUNGAL_GROTTO -> new int[] {FungalLayout.ARENA_X, FungalLayout.ARENA_Z};
            case SUNKEN_GARDEN -> new int[] {SunkenLayout.ARENA_X, SunkenLayout.ARENA_Z};
            case ANCIENT_FOUNDRY -> new int[] {FoundryLayout.ARENA_X, FoundryLayout.ARENA_Z};
            case SUNSCORCHED_NECROPOLIS -> new int[] {NecropolisLayout.ARENA_X, NecropolisLayout.ARENA_Z};
            case PAINTED_BADLANDS -> new int[] {BadlandsLayout.ARENA_X, BadlandsLayout.ARENA_Z};
            case CHERRY_GROVE -> new int[] {CherryLayout.ARENA_X, CherryLayout.ARENA_Z};
            case LAPIS_OBSERVATORY -> new int[] {ObservatoryLayout.ARENA_X, ObservatoryLayout.ARENA_Z};
            case CRIMSON_CATHEDRAL -> new int[] {CathedralLayout.ARENA_X, CathedralLayout.ARENA_Z};
            case GILDED_LIBRARY -> new int[] {LibraryLayout.ARENA_X, LibraryLayout.ARENA_Z};
            case WARPED_HOLLOW -> new int[] {WarpedLayout.ARENA_X, WarpedLayout.ARENA_Z};
            case END_SPIRE -> new int[] {EndSpireLayout.ARENA_X, EndSpireLayout.ARENA_Z};
            default -> new int[] {GlacialLayout.ARENA_X, GlacialLayout.ARENA_Z};
        };
    }

    private static double door(BiomeKind b) {
        return switch (b) {
            case VERDANT_CANOPY -> VerdantLayout.ARENA_DOOR;
            case ASHEN_CALDERA -> AshenLayout.ARENA_DOOR;
            case CRYSTAL_CAVERN -> CrystalLayout.ARENA_DOOR;
            case FLOODED_RUINS -> FloodedLayout.ARENA_DOOR;
            case FUNGAL_GROTTO -> FungalLayout.ARENA_DOOR;
            case SUNKEN_GARDEN -> SunkenLayout.ARENA_DOOR;
            case ANCIENT_FOUNDRY -> FoundryLayout.ARENA_DOOR;
            case SUNSCORCHED_NECROPOLIS -> NecropolisLayout.ARENA_DOOR;
            case PAINTED_BADLANDS -> BadlandsLayout.ARENA_DOOR;
            case CHERRY_GROVE -> CherryLayout.ARENA_DOOR;
            case LAPIS_OBSERVATORY -> ObservatoryLayout.ARENA_DOOR;
            case CRIMSON_CATHEDRAL -> CathedralLayout.ARENA_DOOR;
            case GILDED_LIBRARY -> LibraryLayout.ARENA_DOOR;
            case WARPED_HOLLOW -> WarpedLayout.ARENA_DOOR;
            case END_SPIRE -> EndSpireLayout.ARENA_DOOR;
            default -> GlacialLayout.ARENA_DOOR;
        };
    }

    // ---- the contract every layout must keep ------------------------------------------------------------------

    @Test
    void everyLayoutKeepsTheGateAndEntryContract() {
        for (BiomeKind b : AUTHORED) {
            // Entry: solid floor, two blocks of head room.
            assertFalse(open(b, 88, 4, 140), b + " entry floor");
            assertTrue(open(b, 88, 5, 140) && open(b, 88, 6, 140), b + " entry head room");
            assertFalse(templates.get(b).isExterior(88, 6, 140), b + " entry must be inside");
            assertTrue(templates.get(b).isExterior(0, 6, 0), b + " outside is exterior");
            for (int k = 84; k <= 92; k++) {
                for (int y = 5; y <= 12; y++) {
                    for (int along = 0; along <= 24; along++) {
                        assertTrue(open(b, k, y, along), b + " north gap " + k + "," + y + "," + along);
                        assertTrue(open(b, k, y, 175 - along), b + " south gap");
                        assertTrue(open(b, 175 - along, y, k), b + " east gap");
                    }
                }
                assertFalse(open(b, k, 4, 0), b + " north corridor floor");
                assertFalse(open(b, k, 4, 175), b + " south corridor floor");
                assertFalse(open(b, 175, 4, k), b + " east corridor floor");
            }
            // Jambs: solid beside the gap throughout the plug zones and over the lintel, so a plug never juts into the room.
            for (int y = 5; y <= 13; y++) {
                for (int z = 14; z <= 18; z++) for (int x : new int[] {83, 93}) assertFalse(open(b, x, y, z), b + " N jamb " + x + "," + y + "," + z);
                for (int z = 162; z <= 164; z++) for (int x : new int[] {83, 93}) assertFalse(open(b, x, y, z), b + " S jamb");
                for (int x = 162; x <= 164; x++) for (int z : new int[] {83, 93}) assertFalse(open(b, x, y, z), b + " E jamb");
            }
            for (int k = 84; k <= 92; k++) {
                assertFalse(open(b, k, 13, 16), b + " N lintel");
                assertFalse(open(b, k, 13, 163), b + " S lintel");
                assertFalse(open(b, 163, 13, k), b + " E lintel");
            }
            // The west side is closed: no gate.
            for (int z = 84; z <= 92; z++) assertFalse(open(b, 14, 8, z), b + " west wall breached");
        }
    }

    @Test
    void everythingImportantIsWalkableFromTheEntry() {
        for (BiomeKind b : AUTHORED) {
            boolean[][] seen = floodWalk(b, 88, 140);
            Layout layout = Layouts.of(b);
            for (Map.Entry<String, int[]> e : layout.spots().entrySet()) {
                int[] s = e.getValue();
                assertTrue(walkable(b, s[0], s[1]), b + " spot " + e.getKey() + " is not walkable at " + s[0] + "," + s[1]);
                assertTrue(seen[s[0]][s[1]], b + " spot " + e.getKey() + " is not reachable from the entry");
            }
            // The three gates' inner ends.
            assertTrue(seen[88][30] && seen[88][150] && seen[150][88], b + " a gate is not reachable");
            // The arena's centre.
            int[] a = arena(b);
            assertTrue(seen[a[0]][a[1]], b + " arena centre not reachable");
        }
    }

    private static boolean walkable(BiomeKind b, int x, int z) {
        if (x < 0 || z < 0 || x > 175 || z > 175) return false;
        String floor = at(b, x, 4, z);
        if (floor.equals("minecraft:air") || floor.startsWith("minecraft:lava") || floor.startsWith("minecraft:water")
                || floor.startsWith("minecraft:light")) return false;
        return open(b, x, 5, z) && open(b, x, 6, z) && !at(b, x, 5, z).startsWith("minecraft:water");
    }

    private static boolean[][] floodWalk(BiomeKind b, int sx, int sz) {
        boolean[][] seen = new boolean[176][176];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] {sx, sz});
        seen[sx][sz] = true;
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] n : d) {
                int x = p[0] + n[0], z = p[1] + n[1];
                if (x < 0 || z < 0 || x > 175 || z > 175 || seen[x][z] || !walkable(b, x, z)) continue;
                seen[x][z] = true;
                q.add(new int[] {x, z});
            }
        }
        return seen;
    }

    @Test
    void layoutsAreDistinct() {
        // The shape of the open space (is y=8 open?) must differ a lot between biomes, not just their materials.
        for (int i = 0; i < BIOMES.length; i++) {
            for (int j = i + 1; j < BIOMES.length; j++) {
                int differ = 0, total = 0;
                for (int z = 0; z < 176; z += 2) {
                    for (int x = 0; x < 176; x += 2) {
                        total++;
                        if (open(BIOMES[i], x, 8, z) != open(BIOMES[j], x, 8, z)) differ++;
                    }
                }
                assertTrue(differ > total * 0.08, BIOMES[i] + " and " + BIOMES[j] + " share too much floor plan: "
                        + differ + "/" + total);
            }
        }
    }

    @Test
    void landmarksFitAndDoNotOverlap() {
        for (BiomeKind b : BIOMES) {
            List<Landmark> all = Layouts.of(b).landmarks();
            assertEquals(java.util.Arrays.asList(SECOND_EIGHT).contains(b) ? 3 : 4, all.size(), b.toString());
            for (int i = 0; i < all.size(); i++) {
                for (int j = i + 1; j < all.size(); j++) {
                    Landmark a = all.get(i), c = all.get(j);
                    boolean apart = a.originX() + a.voxels().sizeX() <= c.originX() || c.originX() + c.voxels().sizeX() <= a.originX()
                            || a.originZ() + a.voxels().sizeZ() <= c.originZ() || c.originZ() + c.voxels().sizeZ() <= a.originZ();
                    assertTrue(apart, b + ": " + a.id() + " overlaps " + c.id());
                }
            }
        }
    }

    @Test
    void newBiomesHaveNoTreesOrFlowers() {
        for (BiomeKind b : AUTHORED) {
            for (String n : composed.get(b).palette()) {
                assertFalse(n.contains("oak_") && !n.contains("dark_oak") || n.contains("azalea") && b != BiomeKind.SUNKEN_GARDEN || n.contains("allium"), b + " has " + n);
            }
        }
    }

    // ---- the raid arena ---------------------------------------------------------------------------------------

    @Test
    void arenaIsFlatClearAndOpenForEveryBiome() {
        for (BiomeKind b : BIOMES) {
            int[] c = arena(b);
            for (int dz = -13; dz <= 13; dz++) {
                for (int dx = -13; dx <= 13; dx++) {
                    if (Math.hypot(dx, dz) > 12.4) continue;
                    int x = c[0] + dx, z = c[1] + dz;
                    for (int y = 5; y <= 14; y++) assertTrue(open(b, x, y, z), b + " blocked at " + x + "," + y + "," + z);
                    assertFalse(open(b, x, 4, z), b + " floor missing");
                    String f = at(b, x, 4, z);
                    assertFalse(f.startsWith("minecraft:lava") || f.contains("ice") || f.startsWith("minecraft:water"),
                            b + " hazardous or slippery floor at " + x + "," + z + ": " + f);
                }
            }
            double a = Math.toRadians(door(b));
            for (double r = 0; r <= 26; r += 0.5) {
                int x = (int) Math.round(c[0] + r * Math.cos(a));
                int z = (int) Math.round(c[1] + r * Math.sin(a));
                assertTrue(open(b, x, 6, z) && open(b, x, 7, z), b + " door ray blocked at r=" + r + " (" + x + "," + z + ")");
            }
        }
    }

    @Test
    void arenaDecorationIsAttached() {
        for (BiomeKind b : BIOMES) {
            Landmark lm = Layouts.of(b).landmarks().stream().filter(l -> l.voxels().sizeX() == WardensRing.SIZE).findFirst().orElseThrow();
            Voxels v = lm.voxels();
            for (int y = 2; y < v.sizeY(); y++) {
                for (int z = 0; z < v.sizeZ(); z++) {
                    for (int x = 0; x < v.sizeX(); x++) {
                        if (!v.has(x, y, z)) continue;
                        int tx = lm.originX() + x, ty = lm.originY() + y, tz = lm.originZ() + z;
                        boolean supported = !open(b, tx, ty - 1, tz) || !open(b, tx + 1, ty, tz) || !open(b, tx - 1, ty, tz)
                                || !open(b, tx, ty, tz + 1) || !open(b, tx, ty, tz - 1) || !open(b, tx, ty + 1, tz);
                        assertTrue(supported, b + " floating block at " + tx + "," + ty + "," + tz);
                    }
                }
            }
        }
    }

    // ---- the loot cache ---------------------------------------------------------------------------------------

    @Test
    void newBiomesHaveOneChestAtTheirCacheWithAClearLineToIt() {
        int[][] origin = {{AshenLayout.CACHE_X, AshenLayout.CACHE_Z}, {GlacialLayout.CACHE_X, GlacialLayout.CACHE_Z},
                {FloodedLayout.CACHE_X, FloodedLayout.CACHE_Z}, {FungalLayout.CACHE_X, FungalLayout.CACHE_Z},
                {SunkenLayout.CACHE_X, SunkenLayout.CACHE_Z}, {FoundryLayout.CACHE_X, FoundryLayout.CACHE_Z},
                {NecropolisLayout.CACHE_X, NecropolisLayout.CACHE_Z}, {BadlandsLayout.CACHE_X, BadlandsLayout.CACHE_Z},
                {CherryLayout.CACHE_X, CherryLayout.CACHE_Z}, {ObservatoryLayout.CACHE_X, ObservatoryLayout.CACHE_Z},
                {CathedralLayout.CACHE_X, CathedralLayout.CACHE_Z}, {LibraryLayout.CACHE_X, LibraryLayout.CACHE_Z},
                {WarpedLayout.CACHE_X, WarpedLayout.CACHE_Z}, {EndSpireLayout.CACHE_X, EndSpireLayout.CACHE_Z}};
        for (int i = 0; i < NEW.length; i++) {
            BiomeKind b = NEW[i];
            Template t = templates.get(b);
            assertEquals(1, t.chestMarkers().size(), b.toString());
            int[] m = t.chestMarkers().get(0);
            assertEquals(origin[i][0] + 7, m[0]);
            assertEquals(RootboundCache.ORIGIN_Y + 2, m[1]);
            assertEquals(origin[i][1] + 4, m[2]);
            for (int z = 5; z <= 12; z++) {
                assertTrue(open(b, origin[i][0] + 7, 6, origin[i][1] + z) && open(b, origin[i][0] + 7, 7, origin[i][1] + z),
                        b + " line to the chest closed at module z=" + z);
            }
        }
    }

    // ---- navigation landmarks ---------------------------------------------------------------------------------

    @Test
    void cinderCrownIsSolidSupportedAndCool() {
        Voxels v = CinderCrown.build();
        for (int y = 1; y <= CinderCrown.HEIGHT - 3; y++) assertTrue(v.has(CinderCrown.CENTER, y, CinderCrown.CENTER));
        for (int z = 0; z < v.sizeZ(); z++) {
            for (int x = 0; x < v.sizeX(); x++) {
                String floor = v.get(x, 0, z);
                assertTrue(floor == null || !floor.contains("magma"), "magma where a player can stand");
                for (int y = 1; y < v.sizeY(); y++) {
                    if (v.has(x, y, z)) assertTrue(v.has(x, y - 1, z), "unsupported cone block at " + x + "," + y + "," + z);
                }
            }
        }
        assertTrue(CinderCrown.radiusAt(CinderCrown.HEIGHT) > 3.5, "the bowl keeps a wall round it");
    }

    @Test
    void frostheartArchHasAClearPathAndOneConnectedMass() {
        BiomeKind b = BiomeKind.GLACIAL_VAULT;
        for (int z = GlacialLayout.ARCH_Z; z < GlacialLayout.ARCH_Z + 21; z++) {
            for (int x = 84; x <= 92; x++) {
                for (int y = 5; y <= 8; y++) assertTrue(open(b, x, y, z), "path blocked at " + x + "," + y + "," + z);
                assertFalse(open(b, x, 4, z), "path floor missing at " + x + "," + z);
            }
        }
        Voxels v = FrostheartArch.build();
        boolean[][][] seen = new boolean[21][24][21];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int total = 0;
        for (int z = 0; z < 21; z++) {
            for (int y = 0; y < 24; y++) for (int x = 0; x < 21; x++) if (v.has(x, y, z)) total++;
            for (int x = 0; x < 21; x++) if (v.has(x, 0, z) || v.has(x, 1, z)) q.add(new int[] {x, v.has(x, 0, z) ? 0 : 1, z});
        }
        int reached = 0;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[0] < 0 || p[0] > 20 || p[1] < 0 || p[1] > 23 || p[2] < 0 || p[2] > 20) continue;
            if (seen[p[0]][p[1]][p[2]] || !v.has(p[0], p[1], p[2])) continue;
            seen[p[0]][p[1]][p[2]] = true;
            reached++;
            q.add(new int[] {p[0] + 1, p[1], p[2]});
            q.add(new int[] {p[0] - 1, p[1], p[2]});
            q.add(new int[] {p[0], p[1] + 1, p[2]});
            q.add(new int[] {p[0], p[1] - 1, p[2]});
            q.add(new int[] {p[0], p[1], p[2] + 1});
            q.add(new int[] {p[0], p[1], p[2] - 1});
        }
        assertEquals(total, reached, "every block of the arch connects to the ground");
        assertTrue(v.has(10, 21, 10), "the crown is closed over the opening");
    }

    // ---- exploration landmarks --------------------------------------------------------------------------------

    @Test
    void causewayDeckIsContinuousAndEveryLavaEdgeIsFenced() {
        BiomeKind b = BiomeKind.ASHEN_CALDERA;
        for (int x = AshenLayout.LAKE_X - 24; x <= AshenLayout.LAKE_X + 24; x++) {
            for (int z = 84; z <= 92; z++) {
                assertEquals("minecraft:obsidian", at(b, x, 4, z), "deck gap at " + x + "," + z);
                for (int y = 5; y <= 7; y++) assertTrue(open(b, x, y, z), "lane blocked at " + x + "," + y + "," + z);
            }
            for (int z : new int[] {83, 93}) {
                assertFalse(open(b, x, 5, z), "parapet gap at " + x);
                assertFalse(open(b, x, 6, z), "parapet gap at " + x);
            }
        }
        int lava = 0;
        for (int z = 0; z < 176; z++) {
            for (int x = 0; x < 176; x++) {
                if (!at(b, x, 4, z).startsWith("minecraft:lava")) continue;
                lava++;
                assertTrue(Math.hypot(x - AshenLayout.LAKE_X, z - AshenLayout.LAKE_Z) <= 18, "lava outside the lake at " + x + "," + z);
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + d[0], nz = z + d[1];
                    if (at(b, nx, 4, nz).startsWith("minecraft:lava")) continue;
                    assertFalse(open(b, nx, 4, nz), "lava touches air at " + x + "," + z);
                    assertFalse(open(b, nx, 5, nz) && open(b, nx, 6, nz), "open edge beside lava at " + nx + "," + nz);
                }
            }
        }
        assertTrue(lava > 300, "the lake is a lake: " + lava);
        for (BiomeKind other : new BiomeKind[] {BiomeKind.GLACIAL_VAULT}) {
            for (String n : composed.get(other).palette()) assertFalse(n.startsWith("minecraft:lava"), other + " has lava");
        }
    }

    @Test
    void mirrorwaterShelfKeepsItsWaterApproachAndWalkway() {
        BiomeKind b = BiomeKind.GLACIAL_VAULT;
        int px = GlacialLayout.POOL_X, pz = GlacialLayout.POOL_Z;
        assertTrue(at(b, px, 4, pz).startsWith("minecraft:water"));
        for (int x = px - 12; x <= px - 7; x++) assertTrue(open(b, x, 5, pz) && open(b, x, 6, pz), "west approach blocked at " + x);
        for (int x = px - 2; x <= px + 2; x++) assertEquals("minecraft:polished_diorite", at(b, x, 4, pz - 10), "walkway at " + x);
        for (String n : composed.get(b).palette()) assertFalse(n.startsWith("minecraft:water[level=8") || n.startsWith("minecraft:water[level=1"));
    }

    // ---- Crystal Cavern ---------------------------------------------------------------------------------------

    @Test
    void geodeVaultHasOneChestWithAClearDoorway() {
        BiomeKind b = BiomeKind.CRYSTAL_CAVERN;
        Template t = templates.get(b);
        assertEquals(1, t.chestMarkers().size());
        int[] m = t.chestMarkers().get(0);
        assertEquals(CrystalLayout.VAULT_X + GeodeVault.CHEST_X, m[0]);
        assertEquals(4 + GeodeVault.CHEST_Y, m[1]);
        assertEquals(CrystalLayout.VAULT_Z + GeodeVault.CHEST_Z, m[2]);
        for (int z = GeodeVault.CHEST_Z + 1; z <= GeodeVault.SIZE_Z + 3; z++) {
            assertTrue(open(b, m[0], 6, CrystalLayout.VAULT_Z + z) && open(b, m[0], 7, CrystalLayout.VAULT_Z + z),
                    "line to the chest closed at module z=" + z);
        }
        assertFalse(open(b, m[0], 5, m[2]), "plinth under the chest");
    }

    @Test
    void resonantGalleryHasANineWideLaneOpenAtBothEnds() {
        BiomeKind b = BiomeKind.CRYSTAL_CAVERN;
        for (int x = CrystalLayout.GALLERY_X - 4; x < CrystalLayout.GALLERY_X + ResonantGallery.SIZE_X + 4; x++) {
            for (int z = 84; z <= 92; z++) {
                for (int y = 5; y <= 10; y++) assertTrue(open(b, x, y, z), "lane blocked at " + x + "," + y + "," + z);
                assertFalse(open(b, x, 4, z), "lane floor missing at " + x + "," + z);
            }
        }
        // The vault is complete: the shell is closed above the lane.
        int roof = 0;
        for (int x = CrystalLayout.GALLERY_X; x < CrystalLayout.GALLERY_X + ResonantGallery.SIZE_X; x++) {
            if (!open(b, x, 4 + 12, CrystalLayout.GALLERY_Z + 6)) roof++;
        }
        assertEquals(ResonantGallery.SIZE_X, roof, "the vault has a gap in its crown");
    }

    @Test
    void theGalleryIsATunnelWithNoWayRoundIt() {
        BiomeKind b = BiomeKind.CRYSTAL_CAVERN;
        boolean[][] seen = floodWalk(b, 88, 140);
        for (int x = CrystalLayout.GALLERY_X; x < CrystalLayout.GALLERY_X + ResonantGallery.SIZE_X; x++) {
            for (int z = 56; z <= 120; z++) {
                if (z >= CrystalLayout.GALLERY_Z && z < CrystalLayout.GALLERY_Z + ResonantGallery.SIZE_Z) continue;
                // Outside air is filled with stone when generated, so only interior cells count.
                assertFalse(seen[x][z] && !templates.get(b).isExterior(x, 5, z), "walkable beside the gallery at " + x + "," + z);
            }
        }
        assertTrue(seen[CrystalLayout.GALLERY_X + 13][88] && seen[150][88], "the lane leads through to the east");
    }

    @Test
    void rockSitsFlushOnTheGalleryVault() {
        BiomeKind b = BiomeKind.CRYSTAL_CAVERN;
        for (int x = CrystalLayout.GALLERY_X; x < CrystalLayout.GALLERY_X + ResonantGallery.SIZE_X; x++) {
            for (int z = CrystalLayout.GALLERY_Z; z < CrystalLayout.GALLERY_Z + ResonantGallery.SIZE_Z; z++) {
                for (int y = 5; y < 34; y++) {
                    if (!open(b, x, y, z)) continue;
                    int dz = z - 88, dy = y - 4;
                    boolean lane = Math.abs(dz) <= 4 && (dy <= 7 || Math.hypot(dz, dy - 7) <= 4.7);
                    assertTrue(lane, "gap between the vault and the rock at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void prismCrownIsOneConnectedMassWithARoadRingAroundIt() {
        Voxels v = PrismCrown.build();
        boolean[][][] seen = new boolean[v.sizeX()][v.sizeY()][v.sizeZ()];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int total = 0;
        for (int y = 0; y < v.sizeY(); y++) for (int z = 0; z < v.sizeZ(); z++) for (int x = 0; x < v.sizeX(); x++) if (v.has(x, y, z)) total++;
        q.add(new int[] {PrismCrown.CX, 0, PrismCrown.CZ});
        int reached = 0;
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (!v.has(p[0], p[1], p[2]) || seen[p[0]][p[1]][p[2]]) continue;
            seen[p[0]][p[1]][p[2]] = true;
            reached++;
            for (int[] n : d) q.add(new int[] {p[0] + n[0], p[1] + n[1], p[2] + n[2]});
        }
        assertEquals(total, reached, "crown parts are not connected");
        BiomeKind b = BiomeKind.CRYSTAL_CAVERN;
        for (int i = 0; i < 360; i += 6) {
            int x = (int) Math.round(CrystalLayout.CROWN_X + CrystalLayout.RING_R * Math.cos(Math.toRadians(i)));
            int z = (int) Math.round(CrystalLayout.CROWN_Z + CrystalLayout.RING_R * Math.sin(Math.toRadians(i)));
            assertTrue(walkable(b, x, z), "ring blocked at " + x + "," + z);
        }
    }

    @Test
    void crystalCavernIsDenseNotAnEmptyHall() {
        BiomeKind b = BiomeKind.CRYSTAL_CAVERN;
        int floorCells = 0, filled = 0;
        for (int z = 0; z < 176; z++) {
            for (int x = 0; x < 176; x++) {
                if (!walkableOrGrown(b, x, z)) continue;
                floorCells++;
                for (int y = 6; y <= 14; y++) if (!open(b, x, y, z)) { filled++; break; }
            }
        }
        assertTrue(filled > floorCells * 0.12, "too empty: " + filled + "/" + floorCells);
        for (String n : composed.get(b).palette()) assertFalse(n.startsWith("minecraft:lava") || n.startsWith("minecraft:water"));
    }

    // ---- Flooded Ruins ----------------------------------------------------------------------------------------

    /** Still water must be sealed: no water block has air beside or below it, so nothing can flow when it is placed. */
    @Test
    void floodedWaterIsContained() {
        BiomeKind b = BiomeKind.FLOODED_RUINS;
        int water = 0;
        for (int y = 1; y < 40; y++) {
            for (int z = 1; z < 175; z++) {
                for (int x = 1; x < 175; x++) {
                    if (!at(b, x, y, z).startsWith("minecraft:water")) continue;
                    water++;
                    int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] n : d) assertFalse(open(b, x + n[0], y, z + n[1]), "water leaks at " + x + "," + y + "," + z);
                    assertFalse(open(b, x, y - 1, z), "water has no floor at " + x + "," + y + "," + z);
                }
            }
        }
        assertTrue(water > 5000, "too little water: " + water);
    }

    @Test
    void floodedRoutesAndLandmarkApproachesAreDry() {
        BiomeKind b = BiomeKind.FLOODED_RUINS;
        int prom = FloodedLayout.PROM_Z;
        for (int z = 0; z <= prom; z++) assertTrue(walkable(b, 88, z), "north spur blocked at z=" + z);
        for (int x = 54; x <= 141; x++) assertTrue(walkable(b, x, prom), "promenade blocked at " + x);
        for (int z = prom; z <= 88; z++) assertTrue(walkable(b, 122, z), "east link blocked at z=" + z);
        for (int x = 122; x <= 175; x++) assertTrue(walkable(b, x, 88), "east spur blocked at x=" + x);
        for (int z = FloodedLayout.ISLE_Z + 4; z <= 175; z++) assertTrue(walkable(b, 88, z), "south causeway blocked at z=" + z);
        for (int x = FloodedLayout.SLUICE_X; x < FloodedLayout.SLUICE_X + SluiceWalk.SIZE_X; x++) {
            for (int z = FloodedLayout.SLUICE_Z + SluiceWalk.WALK_Z0 + 1; z <= FloodedLayout.SLUICE_Z + SluiceWalk.WALK_Z1 - 1; z++) {
                assertTrue(walkable(b, x, z), "sluice walkway blocked at " + x + "," + z);
            }
            // Water on both sides, below the route.
            for (int z : new int[] {FloodedLayout.SLUICE_Z + 1, FloodedLayout.SLUICE_Z + 13}) {
                String n = at(b, x, 3, z);
                assertTrue(n.startsWith("minecraft:water") || n.contains("coral_fan"), "no water beside the walkway at " + x + "," + z);
            }
        }
        // The court's wall is surrounded by water on every side except the door, never inside it.
        int[] ac = arena(b);
        assertTrue(at(b, ac[0], 3, ac[1] - 26).startsWith("minecraft:water") || at(b, ac[0], 3, ac[1] - 26).contains("coral"), "no moat north of the court");
        for (int dz = -12; dz <= 12; dz++) for (int dx = -12; dx <= 12; dx++) {
            if (Math.hypot(dx, dz) <= 12.4) assertFalse(at(b, ac[0] + dx, 3, ac[1] + dz).startsWith("minecraft:water"));
        }
    }

    @Test
    void tidewheelIsOneConnectedPieceStandingOnTheFloor() {
        Voxels v = Tidewheel.build();
        boolean[][][] seen = new boolean[v.sizeX()][v.sizeY()][v.sizeZ()];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int total = 0;
        for (int z = 0; z < v.sizeZ(); z++) for (int x = 0; x < v.sizeX(); x++) {
            for (int y = 0; y < v.sizeY(); y++) {
                String n = v.get(x, y, z);
                if (n != null && !n.startsWith("minecraft:water") && !n.equals(Voxels.AIR)) total++;
            }
            if (v.has(x, 0, z) && !v.get(x, 0, z).startsWith("minecraft:water")) { seen[x][0][z] = true; q.add(new int[] {x, 0, z}); }
        }
        int reached = 0;
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            reached++;
            for (int[] n : d) {
                int x = p[0] + n[0], y = p[1] + n[1], z = p[2] + n[2];
                if (!v.inBounds(x, y, z) || seen[x][y][z] || !v.has(x, y, z)) continue;
                String name = v.get(x, y, z);
                if (name.startsWith("minecraft:water")) continue;
                seen[x][y][z] = true;
                q.add(new int[] {x, y, z});
            }
        }
        assertEquals(total, reached, "Tidewheel parts are not connected to the floor");
        assertTrue(Tidewheel.build().has(Tidewheel.CENTER_X, 0, Tidewheel.WHEEL_Z0), "the wheel rests on the floor");
    }

    @Test
    void floodedRuinsIsDenseNotAnEmptyHall() {
        BiomeKind b = BiomeKind.FLOODED_RUINS;
        int ground = 0, filled = 0;
        for (int z = 21; z < 161; z++) {
            for (int x = 21; x < 161; x++) {
                if (open(b, x, 4, z)) continue;                    // water or other open floor
                ground++;
                for (int y = 6; y <= 20; y++) if (!open(b, x, y, z)) { filled++; break; }
            }
        }
        assertTrue(filled > ground * 0.10, "too empty: " + filled + "/" + ground);
    }

    // ---- palettes ----------------------------------------------------------------------------------------------

    /** The ten most common block ids above the floor row, the biome's visible palette (properties dropped). */
    private static java.util.Set<String> palette(BiomeKind b) {
        SpongeSchematic sc = composed.get(b);
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (int y = 4; y < 40; y++) {
            for (int z = 0; z < 176; z += 2) {
                for (int x = 0; x < 176; x += 2) {
                    String n = sc.palette()[sc.at(x, y, z)];
                    n = n.contains("[") ? n.substring(0, n.indexOf('[')) : n;
                    if (n.equals("minecraft:air") || n.equals("minecraft:light") || n.equals("minecraft:stone") || n.equals("minecraft:deepslate")) continue;
                    counts.merge(n, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(10).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void theSecondEightKeepToTheirOwnPalettes() {
        Map<BiomeKind, java.util.Set<String>> palettes = new EnumMap<>(BiomeKind.class);
        for (BiomeKind b : BIOMES) palettes.put(b, palette(b));
        for (BiomeKind a : SECOND_EIGHT) {
            for (BiomeKind b : BIOMES) {
                if (a == b || java.util.Arrays.asList(SECOND_EIGHT).contains(b) && b.ordinal() < a.ordinal()) continue;
                java.util.Set<String> shared = new java.util.HashSet<>(palettes.get(a));
                shared.retainAll(palettes.get(b));
                assertTrue(shared.size() <= 3, a + " and " + b + " share most of their palette: " + shared);
            }
        }
    }

    private static boolean walkableOrGrown(BiomeKind b, int x, int z) {
        return !open(b, x, 4, z) && !at(b, x, 4, z).equals("minecraft:stone") && x > 14 && z > 14 && x < 162 && z < 162;
    }
}
