package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;

import com.ascensiondungeons.gen.Template;
import java.util.function.BiFunction;

/**
 * The parts every authored district layout shares, so that layouts only describe what makes them different: the
 * enclosing shell with its three gates, roads, the raid chamber, terrain scatter and the invisible light layer. The shell
 * keeps the contract {@link Template} relies on: a closed wall and roof with open air outside, the gates at north
 * (x 84..92, wall inner face z=19..20), south and east (inner face 161), and the floor row at y=4.
 */
public final class DistrictKit {
    public static final String LIGHT = "minecraft:light[level=15,waterlogged=false]";
    public static final int FLOOR = Template.FLOOR_Y;

    /** The room's footprint and ceiling. {@code outer} must contain {@code inner}; the difference is the wall. */
    public interface Shape {
        boolean inner(int x, int z);

        boolean outer(int x, int z);

        /** First solid y above the room at this column (only called where {@code inner}). */
        int ceiling(int x, int z);
    }

    public final Canvas canvas = new Canvas();
    /** Cells reserved for roads, landmarks and the arena; scatter keeps off them. */
    public final boolean[][] reserved = new boolean[Canvas.SIZE][Canvas.SIZE];
    /** Cells that are road surface (for walkability checks and tests). */
    public final boolean[][] road = new boolean[Canvas.SIZE][Canvas.SIZE];
    public final Shape shape;

    public DistrictKit(Shape shape) {
        this.shape = shape;
    }

    // ---- shell ----------------------------------------------------------------------------------------------

    /**
     * Lays the shell: stone below the floor everywhere, wall and roof solid within the outer shape, an open room inside
     * with the floor row painted by {@code floorPick}, and nothing at all outside the outer shape above the floor.
     */
    public void shell(BiFunction<Integer, Integer, String> floorPick, String under, String roof,
                      BiFunction<int[], Integer, String> wallPick) {
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                canvas.fill(x, 0, z, x, FLOOR - 1, z, under);
                boolean in = shape.inner(x, z), out = shape.outer(x, z);
                if (!out) {
                    canvas.set(x, FLOOR, z, under);
                    continue;
                }
                if (in) {
                    canvas.set(x, FLOOR, z, floorPick.apply(x, z));
                    int ceil = shape.ceiling(x, z);
                    canvas.fill(x, ceil, z, x, Canvas.HEIGHT - 1, z, roof);
                } else {
                    for (int y = FLOOR; y < Canvas.HEIGHT; y++) canvas.set(x, y, z, wallPick.apply(new int[] {x, z}, y));
                }
            }
        }
    }

    /** Opens the three gates through the wall and lays the road stripe along each outside corridor. */
    public void gates(String roadBlock, String edgeBlock) {
        for (int k = Template.GATE_MIN; k <= Template.GATE_MAX; k++) {
            boolean edge = k == Template.GATE_MIN || k == Template.GATE_MAX;
            String floor = edge ? edgeBlock : roadBlock;
            for (int along = 0; along <= 30; along++) {
                openGateCell(k, along, floor);                               // north: z = along
                openGateCell(k, Template.LAST - along, floor);                         // south
                openGateCellEast(Template.LAST - along, k, floor);                     // east: x = Template.LAST - along
            }
        }
        // The routes inside continue the stripe up to the room; mark them as road.
        for (int k = Template.GATE_MIN; k <= Template.GATE_MAX; k++) {
            for (int along = 0; along <= 40; along++) {
                road[k][along] = true;
                road[k][Template.LAST - along] = true;
                road[Template.LAST - along][k] = true;
            }
        }
        reserveRect(79, 0, 97, 40, 0);
        reserveRect(79, 135, 97, Template.LAST, 0);
        reserveRect(135, 79, Template.LAST, 97, 0);
    }

    private void openGateCell(int x, int z, String floor) {
        for (int y = 5; y <= Template.CLEAR_TOP_Y; y++) canvas.set(x, y, z, Canvas.AIR);
        canvas.set(x, FLOOR, z, floor);
    }

    private void openGateCellEast(int x, int z, String floor) {
        openGateCell(x, z, floor);
    }

    // ---- roads ----------------------------------------------------------------------------------------------

    /** A road 9 wide along a segment: surface in the middle seven, edge blocks on the outer two rows. */
    public void roadSeg(double ax, double az, double bx, double bz, BiFunction<Integer, Integer, String> surface,
                        String edge) {
        int x0 = (int) Math.floor(Math.min(ax, bx)) - 6, x1 = (int) Math.ceil(Math.max(ax, bx)) + 6;
        int z0 = (int) Math.floor(Math.min(az, bz)) - 6, z1 = (int) Math.ceil(Math.max(az, bz)) + 6;
        for (int z = Math.max(0, z0); z <= Math.min(Canvas.SIZE - 1, z1); z++) {
            for (int x = Math.max(0, x0); x <= Math.min(Canvas.SIZE - 1, x1); x++) {
                double d = distToSegment(x, z, ax, az, bx, bz);
                if (d > 4.5 || !shape.inner(x, z)) continue;
                canvas.set(x, FLOOR, z, d > 3.5 ? edge : surface.apply(x, z));
                road[x][z] = true;
                reserved[x][z] = true;
            }
        }
    }

    /** A road ring 9 wide centred on a circle of the given radius. */
    public void roadRing(double cx, double cz, double radius, BiFunction<Integer, Integer, String> surface, String edge) {
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                double d = Math.abs(Math.hypot(x - cx, z - cz) - radius);
                if (d > 4.5 || !shape.inner(x, z)) continue;
                canvas.set(x, FLOOR, z, d > 3.5 ? edge : surface.apply(x, z));
                road[x][z] = true;
                reserved[x][z] = true;
            }
        }
    }

    static double distToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2));
        return Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
    }

    // ---- reservations ---------------------------------------------------------------------------------------

    public void reserveRect(int x0, int z0, int x1, int z1, int margin) {
        for (int z = Math.max(0, z0 - margin); z <= Math.min(Canvas.SIZE - 1, z1 + margin); z++) {
            for (int x = Math.max(0, x0 - margin); x <= Math.min(Canvas.SIZE - 1, x1 + margin); x++) reserved[x][z] = true;
        }
    }

    /** True if any cell within {@code m} of (x, z) is reserved, outside the room, or off the canvas. */
    public boolean nearBlocked(int x, int z, int m) {
        for (int dz = -m; dz <= m; dz++) {
            for (int dx = -m; dx <= m; dx++) {
                int a = x + dx, b = z + dz;
                if (a < 0 || b < 0 || a >= Canvas.SIZE || b >= Canvas.SIZE || reserved[a][b] || !shape.inner(a, b)) return true;
            }
        }
        return false;
    }

    public void reserveCircle(double cx, double cz, double radius) {
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) if (Math.hypot(x - cx, z - cz) <= radius) reserved[x][z] = true;
        }
    }

    // ---- raid chamber ---------------------------------------------------------------------------------------

    /**
     * A round raid chamber: floor within radius 19.3, a wall to radius 23 up to y=30, and a doorway 9 wide facing
     * {@code doorDeg} (degrees; x east, z south) with a lintel over it. The decorations come from the arena landmark.
     */
    public void arenaChamber(int cx, int cz, double doorDeg, BiFunction<Integer, Integer, String> floorPick,
                             BiFunction<int[], Integer, String> wallPick, String lintel, String doorFloor) {
        double a = Math.toRadians(doorDeg);
        double ux = Math.cos(a), uz = Math.sin(a);
        for (int z = cz - 24; z <= cz + 24; z++) {
            for (int x = cx - 24; x <= cx + 24; x++) {
                double dx = x - cx, dz = z - cz;
                double r = Math.hypot(dx, dz);
                if (r > 23.4) continue;
                double along = dx * ux + dz * uz, lateral = -dx * uz + dz * ux;
                reserved[Math.floorMod(x, Canvas.SIZE)][Math.floorMod(z, Canvas.SIZE)] = true;
                if (r <= 19.3) {
                    canvas.set(x, FLOOR, z, floorPick.apply(x, z));
                    for (int y = 5; y <= 30; y++) canvas.set(x, y, z, Canvas.AIR);
                    continue;
                }
                boolean door = along > 0 && Math.abs(lateral) <= 4.5;
                for (int y = FLOOR; y <= 30; y++) {
                    if (door && y == FLOOR) canvas.set(x, y, z, doorFloor);
                    else if (door && y <= Template.CLEAR_TOP_Y) canvas.set(x, y, z, Canvas.AIR);
                    else if (door && y <= Template.CLEAR_TOP_Y + 2) canvas.set(x, y, z, lintel);
                    else canvas.set(x, y, z, wallPick.apply(new int[] {x, z}, y));
                }
            }
        }
    }

    // ---- ambience --------------------------------------------------------------------------------------------

    /**
     * The open cells just under a column that hangs from the roof (the tips of stalactites and icicles): the first air cell
     * below a solid run that starts at the roof and is at least {@code minDrop} long, with the cell above solid. Anything
     * lower than {@code minY} is ignored, so effects stay clear of what stands on the floor.
     */
    public java.util.List<int[]> hangingTips(int minDrop, int minY) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int z = 0; z < Canvas.SIZE; z++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                if (!shape.inner(x, z)) continue;
                int ceil = shape.ceiling(x, z), y = ceil - 1;
                if (canvas.isAir(x, y, z)) continue;
                while (y > minY && !canvas.isAir(x, y, z)) y--;
                if (y > minY && canvas.isAir(x, y, z) && ceil - 1 - y >= minDrop) out.add(new int[] {x, y, z});
            }
        }
        return out;
    }

    // ---- terrain scatter and lights -------------------------------------------------------------------------

    /** A stout mound: radius {@code r}, height {@code h} (full blocks) with a rounded profile, on the floor row. */
    public void mound(int cx, int cz, double r, int h, BiFunction<Integer, Integer, String> pick) {
        for (int z = (int) (cz - r - 1); z <= cz + r + 1; z++) {
            for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
                if (x < 0 || z < 0 || x >= Canvas.SIZE || z >= Canvas.SIZE || reserved[x][z] || !shape.inner(x, z)) continue;
                double d = Math.hypot(x - cx, z - cz) / r;
                if (d >= 1) continue;
                int height = (int) Math.round(h * (1 - d * d));
                for (int y = FLOOR + 1; y <= FLOOR + height; y++) canvas.set(x, y, z, pick.apply(x, z));
            }
        }
    }

    /** Scatters mounds where noise is high, away from reservations. */
    public void scatter(int salt, int spacing, double chance, int maxHeight, double maxRadius,
                        BiFunction<Integer, Integer, String> pick) {
        for (int z = spacing / 2; z < Canvas.SIZE; z += spacing) {
            for (int x = spacing / 2; x < Canvas.SIZE; x += spacing) {
                if (hash(x, 0, z, salt) > chance) continue;
                int cx = x + (int) (hash(x, 1, z, salt) * spacing / 2) - spacing / 4;
                int cz = z + (int) (hash(x, 2, z, salt) * spacing / 2) - spacing / 4;
                double r = 2 + hash(x, 3, z, salt) * (maxRadius - 2);
                int h = 1 + (int) (hash(x, 4, z, salt) * maxHeight);
                mound(cx, cz, r, h, pick);
            }
        }
    }

    /** Invisible full-brightness light blocks every 8 blocks at y=7, so the roofed room is lit. */
    public void lights() {
        for (int z = 4; z < Canvas.SIZE; z += 8) {
            for (int x = 4; x < Canvas.SIZE; x += 8) {
                if (shape.inner(x, z) && canvas.isAir(x, 7, z)) canvas.set(x, 7, z, LIGHT);
            }
        }
    }
}
