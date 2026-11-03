package com.ascensiondungeons.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SpongeSchematicWriterTest {
    @Test
    void roundTripsThroughTheReader() throws IOException {
        short[] blocks = new short[3 * 2 * 4];
        for (int i = 0; i < blocks.length; i++) blocks[i] = (short) (i % 3 == 0 ? 0 : i % 300);
        String[] palette = new String[300];
        palette[0] = "minecraft:air";
        for (int i = 1; i < palette.length; i++) palette[i] = "minecraft:test_block_" + i;
        SpongeSchematic in = new SpongeSchematic(3, 2, 4, palette, blocks);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SpongeSchematicWriter.write(in, "test", "tester", bytes);
        SpongeSchematic out = SpongeSchematic.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(3, out.width());
        assertEquals(2, out.height());
        assertEquals(4, out.length());
        assertArrayEquals(palette, out.palette());
        assertArrayEquals(blocks, out.blocks());
    }
}
