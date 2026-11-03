package com.ascensiondungeons.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * A district template (the accepted underground v3 base) plus what the generator needs to know about it that the file
 * does not say outright:
 * <ul>
 *   <li>which air is outside the ring wall (the file leaves it empty; the world fills it with rock),</li>
 *   <li>the three gates (north, south, east) and how to join or plug them,</li>
 *   <li>where the placeholder chests are.</li>
 * </ul>
 * Coordinates are template-local: x, z in [0, 176), y in [0, 96), floor blocks at y=4.
 */
public final class Template {
    public static final int SIZE = 176;
    public static final int CENTER = 88;
    /** Last template index on either axis. */
    public static final int LAST = SIZE - 1;
    public static final int FLOOR_Y = 4;
    /** Air above the floor in a gate tunnel: y = 5 .. 12 inclusive (eight blocks of clearance). */
    public static final int CLEAR_TOP_Y = 12;
    /** Gates are nine wide, x (or z) = 84 .. 92, with a one-block lining either side. */
    public static final int GATE_MIN = 84;
    public static final int GATE_MAX = 92;
    /** Where the arriving party stands (the entry vestibule). */
    public static final int ENTRY_X = 88;
    public static final int ENTRY_Z = 140;

    /**
     * A gate. {@code plane} is a line across the gap, inside both jambs of the ring wall, that separates the
     * district's own air from the outside. The corridor runs from the plane to the envelope edge; the plug is the
     * stretch of gap behind the plane that is filled when no neighbour is attached.
     */
    private record Gate(Side side, int plane, int corridorFrom, int corridorTo, int plugFrom, int plugTo) {}

    private static final Gate[] GATES = {
            // The plug stays within the ring wall (inner face at about N z=18..20, S/E 161..162) so it never juts into the room.
            new Gate(Side.N, 14, 0, 13, 14, 18),
            new Gate(Side.S, 164, 165, 175, 162, 164),
            new Gate(Side.E, 164, 165, 175, 162, 164),
    };

    private final SpongeSchematic schematic;
    private final BitSet exterior;
    private final int airIndex;
    private final int lightIndex;
    private final List<int[]> chests = new ArrayList<>();

    public Template(SpongeSchematic schematic) {
        if (schematic.width() != SIZE || schematic.length() != SIZE) {
            throw new IllegalArgumentException("District templates must be 176 x 176 in plan");
        }
        this.schematic = schematic;
        this.airIndex = schematic.paletteIndex("minecraft:air");
        if (airIndex < 0) throw new IllegalArgumentException("Template has no air");
        int light = -1;
        for (int i = 0; i < schematic.palette().length; i++) {
            if (schematic.palette()[i].startsWith("minecraft:light[")) light = i;
        }
        this.lightIndex = light;
        this.exterior = floodExterior();
        BitSet chestIdx = new BitSet();
        for (int i = 0; i < schematic.palette().length; i++) {
            if (schematic.palette()[i].startsWith("minecraft:chest")) chestIdx.set(i);
        }
        if (!chestIdx.isEmpty()) {
            for (int y = 0; y < schematic.height(); y++) {
                for (int z = 0; z < SIZE; z++) {
                    for (int x = 0; x < SIZE; x++) if (chestIdx.get(schematic.at(x, y, z))) chests.add(new int[] {x, y, z});
                }
            }
        }
    }

    public SpongeSchematic schematic() { return schematic; }

    public int height() { return schematic.height(); }

    public int airIndex() { return airIndex; }

    /** Palette index of the invisible light block, or -1. Counts as open space when sealing a gate. */
    public int lightIndex() { return lightIndex; }

    /** Template-local chest placeholders, as {x, y, z}. */
    public List<int[]> chestMarkers() { return chests; }

    /** True for open space (air or invisible light) outside the ring wall, beyond the gate planes. */
    public boolean isExterior(int x, int y, int z) {
        return exterior.get(schematic.index(x, y, z));
    }

    public int exteriorCount() { return exterior.cardinality(); }

    /**
     * If (x, y, z) is within the outside corridor of a gate (including its one-block lining and the floor and
     * ceiling rows), returns that gate's side; otherwise null.
     */
    public Side corridorAt(int x, int y, int z) {
        if (y < FLOOR_Y || y > CLEAR_TOP_Y + 1) return null;
        for (Gate g : GATES) {
            int along = alongAxis(g.side, x, z);
            int across = acrossAxis(g.side, x, z);
            if (along >= g.corridorFrom && along <= g.corridorTo && across >= GATE_MIN - 1 && across <= GATE_MAX + 1) {
                return g.side;
            }
        }
        return null;
    }

    /** True where the corridor is walkway (air), as opposed to its lining. */
    public static boolean isCorridorClear(int x, int y, int z, Side side) {
        int across = acrossAxis(side, x, z);
        return y > FLOOR_Y && y <= CLEAR_TOP_Y && across >= GATE_MIN && across <= GATE_MAX;
    }

    /** True on the centre line of a gate corridor, where its ceiling lanterns go. */
    public static boolean isCorridorCentre(int x, int z, Side side) {
        return acrossAxis(side, x, z) == CENTER;
    }

    /** If (x, y, z) is in the gap that must be filled to seal a gate with no neighbour, returns that gate's side. */
    public Side plugAt(int x, int y, int z) {
        if (y <= FLOOR_Y || y > CLEAR_TOP_Y) return null;
        for (Gate g : GATES) {
            int along = alongAxis(g.side, x, z);
            int across = acrossAxis(g.side, x, z);
            if (along >= g.plugFrom && along <= g.plugTo && across >= GATE_MIN && across <= GATE_MAX) return g.side;
        }
        return null;
    }

    public static boolean hasGate(Side s) { return s != Side.W; }

    private static int alongAxis(Side side, int x, int z) {
        return side == Side.N || side == Side.S ? z : x;
    }

    private static int acrossAxis(Side side, int x, int z) {
        return side == Side.N || side == Side.S ? x : z;
    }

    // ---- outside flood fill ---------------------------------------------------------------------------------

    private static boolean isBarrier(int x, int z) {
        for (Gate g : GATES) {
            if (alongAxis(g.side, x, z) == g.plane
                    && acrossAxis(g.side, x, z) >= GATE_MIN - 1 && acrossAxis(g.side, x, z) <= GATE_MAX + 1) {
                return true;
            }
        }
        return false;
    }

    /** Air and the invisible light block both count as open space. */
    public boolean isOpen(int paletteIndex) {
        return paletteIndex == airIndex || paletteIndex == lightIndex;
    }

    private BitSet floodExterior() {
        int w = SIZE, l = SIZE, h = schematic.height();
        BitSet seen = new BitSet(w * h * l);
        int[] stack = new int[1 << 16];
        int sp = 0;
        int[][] seeds = {{0, 6, 0}, {0, h - 1, 0}, {w - 1, 6, l - 1}};
        for (int[] s : seeds) {
            int idx = schematic.index(s[0], s[1], s[2]);
            if (isOpen(schematic.blocks()[idx]) && !seen.get(idx)) {
                seen.set(idx);
                stack[sp++] = idx;
            }
        }
        int plane = w * l;
        while (sp > 0) {
            int idx = stack[--sp];
            int x = idx % w;
            int z = (idx / w) % l;
            int y = idx / plane;
            for (int d = 0; d < 6; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
                int ny = y + (d == 4 ? 1 : d == 5 ? -1 : 0);
                if (nx < 0 || nx >= w || nz < 0 || nz >= l || ny < 0 || ny >= h) continue;
                int n = schematic.index(nx, ny, nz);
                if (seen.get(n) || !isOpen(schematic.blocks()[n]) || isBarrier(nx, nz)) continue;
                seen.set(n);
                if (sp == stack.length) stack = Arrays.copyOf(stack, sp * 2);
                stack[sp++] = n;
            }
        }
        return seen;
    }
}
