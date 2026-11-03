package com.ascensiondungeons.gen;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Writes Sponge Schematic v3 in the layout WorldEdit accepts: an unnamed root compound holding a {@code Schematic}
 * child. (The older mistake of naming the root {@code Schematic} makes WorldEdit fail with "No tag under the name
 * 'Schematic' exists".)
 */
public final class SpongeSchematicWriter {
    private static final int DATA_VERSION_1_21_1 = 3955;

    private SpongeSchematicWriter() {}

    public static void write(SpongeSchematic s, String name, String author, OutputStream raw) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
            out.writeByte(10);
            out.writeUTF("");
            // Schematic compound
            tag(out, 10, "Schematic");
            tag(out, 3, "Version");
            out.writeInt(3);
            tag(out, 3, "DataVersion");
            out.writeInt(DATA_VERSION_1_21_1);
            tag(out, 2, "Width");
            out.writeShort(s.width());
            tag(out, 2, "Height");
            out.writeShort(s.height());
            tag(out, 2, "Length");
            out.writeShort(s.length());
            tag(out, 11, "Offset");
            out.writeInt(3);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            tag(out, 10, "Metadata");
            tag(out, 8, "Name");
            out.writeUTF(name);
            tag(out, 8, "Author");
            out.writeUTF(author);
            tag(out, 9, "RequiredMods");
            out.writeByte(8);
            out.writeInt(0);
            out.writeByte(0);
            tag(out, 10, "Blocks");
            tag(out, 10, "Palette");
            for (int i = 0; i < s.palette().length; i++) {
                tag(out, 3, s.palette()[i]);
                out.writeInt(i);
            }
            out.writeByte(0);
            tag(out, 7, "Data");
            byte[] data = varints(s.blocks());
            out.writeInt(data.length);
            out.write(data);
            tag(out, 9, "BlockEntities");
            out.writeByte(10);
            out.writeInt(0);
            out.writeByte(0);   // end of Blocks
            out.writeByte(0);   // end of Schematic
            out.writeByte(0);   // end of root
        }
    }

    private static void tag(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    private static byte[] varints(short[] blocks) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(blocks.length);
        for (short b : blocks) {
            int v = b & 0xffff;
            while ((v & ~0x7f) != 0) {
                bytes.write((v & 0x7f) | 0x80);
                v >>>= 7;
            }
            bytes.write(v);
        }
        return bytes.toByteArray();
    }
}
