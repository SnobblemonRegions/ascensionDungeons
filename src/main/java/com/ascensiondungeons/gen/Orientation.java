package com.ascensiondungeons.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * How a district template is placed in its cell: mirrored along x first, then turned clockwise by {@code rotation}
 * quarter turns (the same order and handedness as Minecraft's StructureTemplate, so block states can be transformed
 * with {@code mirror(FRONT_BACK)} then {@code rotate(CLOCKWISE_90 x n)}).
 *
 * <p>All offsets are measured from the template centre (88, 88), so a gate that is symmetric about the centre line
 * stays on the centre line under any orientation. The one row or column that falls outside the 176 square is rock.
 */
public record Orientation(int rotation, boolean mirror) {
    public Orientation {
        if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("rotation " + rotation);
    }

    /** Template offset -> cell offset. */
    public int[] toWorld(int dx, int dz) {
        if (mirror) dx = -dx;
        for (int i = 0; i < rotation; i++) {
            int nx = -dz;
            int nz = dx;
            dx = nx;
            dz = nz;
        }
        return new int[] {dx, dz};
    }

    /** Cell offset -> template offset. */
    public int[] toTemplate(int dx, int dz) {
        for (int i = 0; i < rotation; i++) {
            int nx = dz;
            int nz = -dx;
            dx = nx;
            dz = nz;
        }
        if (mirror) dx = -dx;
        return new int[] {dx, dz};
    }

    public Side toWorld(Side templateSide) {
        int[] v = toWorld(templateSide.dx, templateSide.dz);
        return Side.ofVector(v[0], v[1]);
    }

    /** The side of the cell where the template has no gate (the template's west wall). */
    public Side closedSide() { return toWorld(Side.W); }

    public static List<Orientation> all() {
        List<Orientation> out = new ArrayList<>();
        for (boolean m : new boolean[] {false, true}) for (int r = 0; r < 4; r++) out.add(new Orientation(r, m));
        return out;
    }

    /** The orientation (mirrored or not, as asked) whose closed side is {@code side}. */
    public static Orientation forClosedSide(Side side, boolean mirror) {
        for (Orientation o : all()) if (o.mirror == mirror && o.closedSide() == side) return o;
        throw new IllegalStateException("no orientation for " + side);
    }
}
