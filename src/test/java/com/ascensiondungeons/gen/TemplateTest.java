package com.ascensiondungeons.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Uses the composed Verdant district, so these also guard the assumptions the generator makes about a layout. */
class TemplateTest {
    private static SpongeSchematic schematic;
    private static Template template;

    @BeforeAll
    static void load() {
        var layout = com.ascensiondungeons.gen.layout.Layouts.of(BiomeKind.VERDANT_CANOPY);
        schematic = com.ascensiondungeons.gen.landmark.Composer.apply(layout.base(), layout.landmarks()).schematic();
        template = new Template(schematic);
    }

    private boolean air(int x, int y, int z) { return schematic.at(x, y, z) == template.airIndex(); }

    @Test
    void fileMatchesTheDistrictSize() {
        assertEquals(176, schematic.width());
        assertEquals(96, schematic.height());
        assertEquals(176, schematic.length());
        assertEquals("minecraft:air", schematic.palette()[template.airIndex()]);
    }

    @Test
    void gatesSitBetweenSolidJambs() {
        // The flood-fill planes must lie inside the wall on both sides of each gap, or the outside leaks in.
        assertFalse(air(83, 6, 14));
        assertFalse(air(93, 6, 14));
        assertFalse(air(83, 6, 164));
        assertFalse(air(93, 6, 164));
        assertFalse(air(164, 6, 83));
        assertFalse(air(164, 6, 93));
        for (int y = 5; y <= 12; y++) {
            assertTrue(air(88, y, 14), "north gate not clear at y=" + y);
            assertTrue(air(88, y, 164), "south gate not clear at y=" + y);
            assertTrue(air(164, y, 88), "east gate not clear at y=" + y);
        }
    }

    @Test
    void outsideIsFoundAndInsideIsNot() {
        assertTrue(template.isExterior(0, 6, 0));
        assertTrue(template.isExterior(175, 90, 175));
        assertTrue(template.exteriorCount() > 200_000);
        int[][] inside = {{88, 10, 88}, {88, 5, 140}, {88, 6, 24}, {88, 6, 156}, {156, 6, 88}};
        for (int[] p : inside) {
            assertTrue(air(p[0], p[1], p[2]), "expected air at " + java.util.Arrays.toString(p));
            assertFalse(template.isExterior(p[0], p[1], p[2]), "inside air flagged outside at " + java.util.Arrays.toString(p));
        }
    }

    @Test
    void gateCorridorsAreOutsideAndWalkable() {
        for (int z = 0; z <= 13; z++) {
            for (int x = 84; x <= 92; x++) {
                for (int y = 5; y <= 12; y++) {
                    assertTrue(template.isExterior(x, y, z), "north corridor " + x + "," + y + "," + z);
                    assertEquals(Side.N, template.corridorAt(x, y, z));
                }
            }
        }
        for (int z = 165; z <= 175; z++) assertTrue(template.isExterior(88, 8, z));
        for (int x = 165; x <= 175; x++) assertTrue(template.isExterior(x, 8, 88));
        assertEquals(Side.S, template.corridorAt(88, 8, 170));
        assertEquals(Side.E, template.corridorAt(170, 8, 88));
        assertNull(template.corridorAt(88, 8, 88));
    }

    @Test
    void plugsLieBehindThePlanes() {
        assertEquals(Side.N, template.plugAt(88, 8, 16));
        assertEquals(Side.S, template.plugAt(88, 8, 163));
        assertEquals(Side.E, template.plugAt(163, 8, 88));
        assertNull(template.plugAt(88, 8, 22));
        assertNull(template.plugAt(88, 8, 158));
        assertNull(template.plugAt(88, 8, 88));
        assertNull(template.plugAt(88, 8, 5));
    }

    @Test
    void lootChestPlaceholdersAreFound() {
        assertEquals(3, template.chestMarkers().size(), "grove, spring and cache");
    }

    @Test
    void rejectsTheOldNamedRootLayout() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
            out.writeByte(10);
            out.writeUTF("Schematic");
            out.writeByte(3);
            out.writeUTF("Version");
            out.writeInt(3);
            out.writeByte(0);
        }
        IOException e = assertThrows(IOException.class,
                () -> SpongeSchematic.read(new ByteArrayInputStream(raw.toByteArray())));
        assertTrue(e.getMessage().contains("Schematic"));
    }
}
