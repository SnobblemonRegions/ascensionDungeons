package com.ascensiondungeons.gen.landmark;

/**
 * A small dense block volume authored in code. A cell holds a block state string (for example
 * {@code minecraft:oak_log[axis=y]}), or {@code null} to leave whatever the district template has there. Explicit
 * {@code minecraft:air} clears a cell. Index order matches Sponge schematics: x + z * sx + y * sx * sz.
 */
public final class Voxels {
    public static final String AIR = "minecraft:air";

    private final int sx;
    private final int sy;
    private final int sz;
    private final String[] cells;

    public Voxels(int sx, int sy, int sz) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.cells = new String[sx * sy * sz];
    }

    public int sizeX() { return sx; }

    public int sizeY() { return sy; }

    public int sizeZ() { return sz; }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz;
    }

    public String get(int x, int y, int z) {
        return inBounds(x, y, z) ? cells[x + z * sx + y * sx * sz] : null;
    }

    public void set(int x, int y, int z, String state) {
        if (!inBounds(x, y, z)) throw new IllegalArgumentException("outside the module: " + x + "," + y + "," + z);
        cells[x + z * sx + y * sx * sz] = state;
    }

    /** Sets a cell, silently ignoring positions outside the module (used for leaf blobs that are trimmed to size). */
    public void setClipped(int x, int y, int z, String state) {
        if (inBounds(x, y, z)) cells[x + z * sx + y * sx * sz] = state;
    }

    public boolean has(int x, int y, int z) {
        String s = get(x, y, z);
        return s != null && !s.equals(AIR);
    }
}
