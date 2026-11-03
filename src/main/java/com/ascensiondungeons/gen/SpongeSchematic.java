package com.ascensiondungeons.gen;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * A Sponge Schematic v3 file (Minecraft 1.21.1 data version) read without any Minecraft classes. Block indices are
 * {@code x + z * width + y * width * length}, as in the format.
 *
 * <p>The root must be an unnamed compound holding a {@code Schematic} child; the older layout where the root itself is
 * named {@code Schematic} is rejected, because WorldEdit rejects it too and the files should stay importable.
 */
public record SpongeSchematic(int width, int height, int length, String[] palette, short[] blocks) {

    public int index(int x, int y, int z) {
        return x + z * width + y * width * length;
    }

    public short at(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    public int paletteIndex(String state) {
        for (int i = 0; i < palette.length; i++) if (palette[i].equals(state)) return i;
        return -1;
    }

    public static SpongeSchematic read(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            int rootType = in.readUnsignedByte();
            if (rootType != 10) throw new IOException("Root tag is not a compound");
            String rootName = in.readUTF();
            Map<String, Object> root = readCompound(in);
            if (!root.containsKey("Schematic")) {
                throw new IOException("No 'Schematic' child under the root (root name '" + rootName + "'): not Sponge v3");
            }
            return fromTag(asMap(root.get("Schematic")));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static SpongeSchematic fromTag(Map<String, Object> s) throws IOException {
        if (((Number) s.get("Version")).intValue() != 3) throw new IOException("Not schematic version 3");
        int w = ((Number) s.get("Width")).intValue();
        int h = ((Number) s.get("Height")).intValue();
        int l = ((Number) s.get("Length")).intValue();
        Map<String, Object> blocks = asMap(s.get("Blocks"));
        Map<String, Object> paletteTag = asMap(blocks.get("Palette"));
        String[] palette = new String[paletteTag.size()];
        for (Map.Entry<String, Object> e : paletteTag.entrySet()) {
            palette[((Number) e.getValue()).intValue()] = e.getKey();
        }
        short[] decoded = decodeVarints((byte[]) blocks.get("Data"), w * h * l);
        return new SpongeSchematic(w, h, l, palette, decoded);
    }

    private static short[] decodeVarints(byte[] data, int expected) throws IOException {
        short[] out = new short[expected];
        int n = 0;
        int i = 0;
        while (i < data.length) {
            int v = 0;
            int shift = 0;
            while (true) {
                int b = data[i++] & 0xff;
                v |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            if (n >= expected) throw new IOException("More block entries than width*height*length");
            out[n++] = (short) v;
        }
        if (n != expected) throw new IOException("Expected " + expected + " block entries, found " + n);
        return out;
    }

    // ---- minimal NBT reader ---------------------------------------------------------------------------------

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new HashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) return map;
            String name = in.readUTF();
            map.put(name, readValue(in, type));
        }
    }

    private static Object readValue(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                return b;
            }
            case 8: return in.readUTF();
            case 9: {
                int elementType = in.readUnsignedByte();
                int n = in.readInt();
                Object[] list = new Object[n];
                for (int i = 0; i < n; i++) list[i] = readValue(in, elementType);
                return list;
            }
            case 10: return readCompound(in);
            case 11: {
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                return a;
            }
            default: throw new IOException("Unknown NBT tag type " + type);
        }
    }
}
