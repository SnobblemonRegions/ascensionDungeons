package com.ascensiondungeons.gen;

/** A side of a district cell. North is -z, east is +x, as in Minecraft. */
public enum Side {
    N(0, -1), E(1, 0), S(0, 1), W(-1, 0);

    public final int dx;
    public final int dz;

    Side(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Side opposite() { return values()[(ordinal() + 2) & 3]; }

    public static Side ofVector(int dx, int dz) {
        for (Side s : values()) if (s.dx == dx && s.dz == dz) return s;
        throw new IllegalArgumentException("Not a unit side vector: " + dx + "," + dz);
    }
}
