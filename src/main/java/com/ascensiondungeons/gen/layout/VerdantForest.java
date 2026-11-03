package com.ascensiondungeons.gen.layout;

import static com.ascensiondungeons.gen.layout.Canvas.hash;

import com.ascensiondungeons.gen.SpongeSchematic;
import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.Landmark;
import java.util.List;

/**
 * Turns the accepted Verdant v3 district into a dense forest. The roads, wall, roof and landmark sockets stay exactly as
 * they are; this pass fills every forest-floor cell that is clear of a road or a landmark with trees of four kinds, bushes,
 * undergrowth, flowers, mushrooms, boulders, fallen logs and stumps, and hangs vines and glow berries from the canopy.
 *
 * <p>Rules that keep it playable: trunks are at least 3 apart; nothing grows within 2 blocks of a road, and canopy over a
 * road starts at y=10 or higher so there is head room; landmark footprints and the three gate approaches are left alone.
 */
public final class VerdantForest {
    private VerdantForest() {}

    private static final int SIZE = Canvas.SIZE, FLOOR = DistrictKit.FLOOR;

    private static boolean forestFloor(String b) {
        return b.startsWith("minecraft:grass_block") || b.startsWith("minecraft:podzol") || b.equals("minecraft:coarse_dirt")
                || b.equals("minecraft:moss_block");
    }

    private static boolean plant(String b) {
        return b.equals("minecraft:short_grass") || b.equals("minecraft:fern") || b.startsWith("minecraft:flowering_azalea")
                || b.equals("minecraft:azure_bluet") || b.equals("minecraft:allium") || b.equals("minecraft:cornflower");
    }

    private static String leaves(String kind) {
        return "minecraft:" + kind + "_leaves[distance=1,persistent=true,waterlogged=false]";
    }

    private static final String[] FLOWERS = {"minecraft:poppy", "minecraft:dandelion", "minecraft:blue_orchid", "minecraft:azure_bluet",
            "minecraft:cornflower", "minecraft:lily_of_the_valley", "minecraft:oxeye_daisy", "minecraft:allium"};

    private static final class Grid {
        final Canvas c;
        final boolean[][] forest = new boolean[SIZE][SIZE];     // forest floor with clear air above
        final boolean[][] nearPath = new boolean[SIZE][SIZE];   // within 2 of a road or built floor
        final boolean[][] kept = new boolean[SIZE][SIZE];       // landmark footprints plus a margin, gates
        final boolean[][] box = new boolean[SIZE][SIZE];        // landmark footprints themselves
        final boolean[][] trunk = new boolean[SIZE][SIZE];      // trunk and its neighbours
        final int[][] roof = new int[SIZE][SIZE];                // first y of the solid roof

        Grid(Canvas c) { this.c = c; }

        boolean free(int x, int y, int z) {
            if (!Canvas.inside(x, y, z)) return false;
            String b = c.get(x, y, z);
            return b.equals(Canvas.AIR) || plant(b);
        }

        boolean air(int x, int y, int z) { return Canvas.inside(x, y, z) && c.get(x, y, z).equals(Canvas.AIR); }
    }

    public static SpongeSchematic apply(SpongeSchematic base, List<Landmark> landmarks) {
        Canvas c = Canvas.of(base);
        Grid g = new Grid(c);
        survey(g, landmarks);
        trees(g);
        understory(g);
        hangings(g);
        canopyLights(g);
        return c.toSchematic();
    }

    // ---- survey -------------------------------------------------------------------------------------------------

    private static void survey(Grid g, List<Landmark> landmarks) {
        Canvas c = g.c;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int y = Canvas.HEIGHT - 1;
                while (y > FLOOR && !c.get(x, y, z).equals(Canvas.AIR) && !c.get(x, y, z).contains("leaves") && !c.get(x, y, z).contains("log")) y--;
                g.roof[x][z] = y + 1;
                String floor = c.get(x, FLOOR, z);
                boolean open = c.isOpen(x, FLOOR + 1, z) || plant(c.get(x, FLOOR + 1, z));
                g.forest[x][z] = forestFloor(floor) && open && g.roof[x][z] > FLOOR + 8;
                if (!forestFloor(floor) && open && g.roof[x][z] > FLOOR + 8) {
                    for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx >= 0 && nz >= 0 && nx < SIZE && nz < SIZE) g.nearPath[nx][nz] = true;
                    }
                }
                if (c.get(x, FLOOR + 1, z).startsWith("minecraft:oak_log")) {
                    for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx >= 0 && nz >= 0 && nx < SIZE && nz < SIZE) g.trunk[nx][nz] = true;
                    }
                }
            }
        }
        for (Landmark l : landmarks) {
            for (int z = l.originZ(); z < l.originZ() + l.voxels().sizeZ(); z++) {
                for (int x = l.originX(); x < l.originX() + l.voxels().sizeX(); x++) {
                    if (x >= 0 && z >= 0 && x < SIZE && z < SIZE) g.box[x][z] = true;
                }
            }
            for (int z = l.originZ() - 2; z < l.originZ() + l.voxels().sizeZ() + 2; z++) {
                for (int x = l.originX() - 2; x < l.originX() + l.voxels().sizeX() + 2; x++) {
                    if (x >= 0 && z >= 0 && x < SIZE && z < SIZE) g.kept[x][z] = true;
                }
            }
        }
        for (int k = Template.GATE_MIN - 6; k <= Template.GATE_MAX + 6; k++) {
            for (int along = 0; along <= 42; along++) {
                g.kept[k][along] = true;
                g.kept[k][Template.LAST - along] = true;
                g.kept[Template.LAST - along][k] = true;
            }
        }
    }

    // ---- trees --------------------------------------------------------------------------------------------------

    private static void trees(Grid g) {
        int spacing = 4;
        for (int gz = 2; gz < SIZE; gz += spacing) {
            for (int gx = 2; gx < SIZE; gx += spacing) {
                if (hash(gx, 0, gz, 61) > 0.62) continue;
                int x = gx + (int) (hash(gx, 1, gz, 61) * 3) - 1, z = gz + (int) (hash(gx, 2, gz, 61) * 3) - 1;
                if (x < 2 || z < 2 || x > SIZE - 4 || z > SIZE - 4) continue;
                if (!g.forest[x][z] || g.kept[x][z] || g.nearPath[x][z] || g.trunk[x][z]) continue;
                double kind = hash(x, 3, z, 61);
                int room = g.roof[x][z] - FLOOR - 4;
                if (room < 9) continue;
                if (kind < 0.50) tree(g, x, z, "oak", "oak", 1, room);
                else if (kind < 0.76) tree(g, x, z, "dark_oak", "dark_oak", 2, room);
                else if (kind < 0.92) tree(g, x, z, "birch", "birch", 1, room);
                else tree(g, x, z, "oak", hash(x, 4, z, 61) < 0.5 ? "azalea" : "flowering_azalea", 1, room);
            }
        }
    }

    private static void tree(Grid g, int x, int z, String log, String leaf, int girth, int room) {
        Canvas c = g.c;
        for (int dz = 0; dz < girth; dz++) for (int dx = 0; dx < girth; dx++) {
            if (!g.forest[x + dx][z + dz] || g.kept[x + dx][z + dz] || g.nearPath[x + dx][z + dz]) return;
        }
        double t = hash(x, 5, z, 62);
        int h = t < 0.40 ? 15 + (int) (hash(x, 6, z, 62) * 12) : t < 0.80 ? 9 + (int) (hash(x, 7, z, 62) * 7) : 5 + (int) (hash(x, 8, z, 62) * 5);
        if (girth == 2) h = Math.max(h, 11);
        double r = 2.8 + h * 0.19 + (girth == 2 ? 1.6 : 0) - (log.equals("birch") ? 0.8 : 0);
        h = Math.min(h, room - (int) Math.ceil(r * 0.55) - 1);
        if (h < 5) return;
        String logState = "minecraft:" + log + "_log[axis=y]";
        for (int y = FLOOR + 1; y <= FLOOR + h; y++) {
            for (int dz = 0; dz < girth; dz++) for (int dx = 0; dx < girth; dx++) c.set(x + dx, y, z + dz, logState);
        }
        for (int dz = -2; dz <= girth + 1; dz++) for (int dx = -2; dx <= girth + 1; dx++) g.trunk[x + dx][z + dz] = true;
        double cx = x + (girth - 1) / 2.0, cz = z + (girth - 1) / 2.0;
        int top = FLOOR + h;
        // Roots flaring over the ground.
        if (h >= 9) {
            int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 1}, {0, -1, 1}};
            for (int[] n : d) {
                int rx = n[0] > 0 ? x + girth : x - 1, rz = n[1] > 0 ? z + girth : z - 1;
                if (n[0] == 0) rx = x;
                if (n[1] == 0) rz = z;
                if (hash(rx, 9, rz, 62) < 0.6 && g.air(rx, FLOOR + 1, rz) && g.forest[rx][rz] && !g.nearPath[rx][rz]) {
                    c.set(rx, FLOOR + 1, rz, "minecraft:" + log + "_log[axis=" + (n[2] == 0 ? "x" : "z") + "]");
                }
            }
        }
        canopy(g, cx, top - 1, cz, r, 0.62, leaf, x, z);
        // Branches: horizontal stubs in the upper trunk, each ending in a leaf cluster.
        int branches = h >= 12 ? 3 : h >= 8 ? 2 : 0;
        for (int i = 0; i < branches; i++) {
            int by = top - 2 - (int) (hash(x, 10 + i, z, 62) * Math.max(1, h * 0.4));
            int dir = (int) (hash(x, 20 + i, z, 62) * 4), len = 2 + (int) (hash(x, 30 + i, z, 62) * 3);
            int[] d = {1, 0, -1, 0, 0, 1, 0, -1};
            int bx = x + (dir == 0 ? girth - 1 : 0), bz = z + (dir == 2 ? girth - 1 : 0);
            for (int s = 1; s <= len; s++) {
                int px = bx + d[dir * 2 % 8] * s, pz = bz + d[(dir * 2 + 1) % 8] * s;
                if (g.air(px, by, pz)) c.set(px, by, pz, "minecraft:" + log + "_log[axis=" + (dir % 2 == 0 ? "x" : "z") + "]");
            }
            canopy(g, bx + d[dir * 2 % 8] * len, by + 1, bz + d[(dir * 2 + 1) % 8] * len, 2.4 + len * 0.3, 0.8, leaf, x, z);
        }
    }

    private static void canopy(Grid g, double cx, int cy, double cz, double r, double vy, String leaf, int sx, int sz) {
        Canvas c = g.c;
        double ry = Math.max(1.8, r * vy);
        String state = leaves(leaf);
        for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
            for (int z = (int) Math.floor(cz - r); z <= Math.ceil(cz + r); z++) {
                for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
                    if (x < 0 || z < 0 || x >= SIZE || z >= SIZE || y <= FLOOR + 1 || y >= g.roof[x][z] - 1) continue;
                    double d = ((x - cx) * (x - cx) + (z - cz) * (z - cz)) / (r * r) + ((y - cy) * (y - cy)) / (ry * ry);
                    if (d > 1 || hash(x, y, z, 63) > 0.90 - 0.25 * d) continue;
                    if (g.kept[x][z]) continue;
                    if (g.nearPath[x][z] && y < FLOOR + 6) continue;
                    if (!g.air(x, y, z)) continue;
                    c.set(x, y, z, state);
                }
            }
        }
    }

    // ---- understory ---------------------------------------------------------------------------------------------

    private static void understory(Grid g) {
        Canvas c = g.c;
        // Bushes: low leaf blobs between the trees.
        for (int gz = 1; gz < SIZE; gz += 3) {
            for (int gx = 1; gx < SIZE; gx += 3) {
                if (hash(gx, 0, gz, 64) > 0.30) continue;
                int x = gx + (int) (hash(gx, 1, gz, 64) * 3) - 1, z = gz + (int) (hash(gx, 2, gz, 64) * 3) - 1;
                if (x < 2 || z < 2 || x > SIZE - 3 || z > SIZE - 3) continue;
                if (!g.forest[x][z] || g.kept[x][z] || g.nearPath[x][z] || g.trunk[x][z]) continue;
                String kind = hash(x, 3, z, 64) < 0.55 ? "oak" : hash(x, 4, z, 64) < 0.5 ? "azalea" : "flowering_azalea";
                double r = 1.4 + hash(x, 5, z, 64) * 1.0;
                for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                    for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                        int px = x + dx, pz = z + dz;
                        if (!Canvas.inside(px, y, pz) || g.kept[px][pz] || g.nearPath[px][pz]) continue;
                        double d = (dx * dx + dz * dz) / (r * r) + (y - FLOOR - 1) * (y - FLOOR - 1) / 2.2;
                        if (d > 1 || hash(px, y, pz, 65) > 0.85 || !g.free(px, y, pz)) continue;
                        c.set(px, y, pz, leaves(kind));
                    }
                }
            }
        }
        // Boulders, fallen logs and stumps.
        for (int gz = 6; gz < SIZE; gz += 13) {
            for (int gx = 6; gx < SIZE; gx += 13) {
                if (hash(gx, 0, gz, 66) > 0.55) continue;
                int x = gx + (int) (hash(gx, 1, gz, 66) * 7) - 3, z = gz + (int) (hash(gx, 2, gz, 66) * 7) - 3;
                if (x < 5 || z < 5 || x > SIZE - 6 || z > SIZE - 6 || !g.forest[x][z] || g.kept[x][z] || g.nearPath[x][z]) continue;
                double r = 1.5 + hash(x, 3, z, 66) * 1.7;
                int h = 1 + (int) (hash(x, 4, z, 66) * 3);
                for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
                    int px = x + dx, pz = z + dz;
                    if (!Canvas.inside(px, FLOOR, pz) || g.kept[px][pz] || g.nearPath[px][pz] || !g.forest[px][pz]) continue;
                    double d = Math.hypot(dx, dz) / r;
                    if (d >= 1) continue;
                    int height = (int) Math.round(h * (1 - d * d));
                    for (int y = FLOOR + 1; y <= FLOOR + height; y++) {
                        if (g.free(px, y, pz)) c.set(px, y, pz, hash(px, y, pz, 67) < 0.75 ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone");
                    }
                    if (height > 0 && g.free(px, FLOOR + height + 1, pz) && hash(px, 9, pz, 67) < 0.5) {
                        c.set(px, FLOOR + height + 1, pz, "minecraft:moss_carpet");
                    }
                }
            }
        }
        for (int gz = 8; gz < SIZE; gz += 17) {
            for (int gx = 8; gx < SIZE; gx += 17) {
                if (hash(gx, 0, gz, 68) > 0.60) continue;
                int x = gx + (int) (hash(gx, 1, gz, 68) * 9) - 4, z = gz + (int) (hash(gx, 2, gz, 68) * 9) - 4;
                boolean alongX = hash(gx, 3, gz, 68) < 0.5;
                int len = 4 + (int) (hash(gx, 4, gz, 68) * 4);
                boolean ok = true;
                for (int s = 0; s < len && ok; s++) {
                    int px = alongX ? x + s : x, pz = alongX ? z : z + s;
                    ok = px > 3 && pz > 3 && px < SIZE - 4 && pz < SIZE - 4 && g.forest[px][pz] && !g.kept[px][pz] && !g.nearPath[px][pz]
                            && !g.trunk[px][pz] && g.air(px, FLOOR + 1, pz);
                }
                if (!ok) continue;
                for (int s = 0; s < len; s++) {
                    int px = alongX ? x + s : x, pz = alongX ? z : z + s;
                    c.set(px, FLOOR + 1, pz, "minecraft:oak_log[axis=" + (alongX ? "x" : "z") + "]");
                    double q = hash(px, 5, pz, 68);
                    if (g.air(px, FLOOR + 2, pz)) {
                        if (q < 0.5) c.set(px, FLOOR + 2, pz, "minecraft:moss_carpet");
                        else if (q < 0.62) c.set(px, FLOOR + 2, pz, "minecraft:brown_mushroom");
                        else if (q < 0.70) c.set(px, FLOOR + 2, pz, "minecraft:red_mushroom");
                    }
                }
            }
        }
        for (int gz = 5; gz < SIZE; gz += 11) {
            for (int gx = 5; gx < SIZE; gx += 11) {
                if (hash(gx, 0, gz, 69) > 0.40) continue;
                int x = gx + (int) (hash(gx, 1, gz, 69) * 5) - 2, z = gz + (int) (hash(gx, 2, gz, 69) * 5) - 2;
                if (x < 3 || z < 3 || x > SIZE - 4 || z > SIZE - 4 || !g.forest[x][z] || g.kept[x][z] || g.nearPath[x][z] || g.trunk[x][z]) continue;
                int h = 1 + (int) (hash(x, 3, z, 69) * 2);
                for (int y = 0; y < h; y++) c.set(x, FLOOR + 1 + y, z, "minecraft:oak_log[axis=y]");
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (hash(x + d[0], 7, z + d[1], 69) < 0.45 && g.air(x + d[0], FLOOR + 1, z + d[1]) && g.forest[x + d[0]][z + d[1]]) {
                        c.set(x + d[0], FLOOR + 1, z + d[1], hash(x, 8, z, 69) < 0.5 ? "minecraft:brown_mushroom" : "minecraft:red_mushroom");
                    }
                }
            }
        }
        // Ground cover on every free forest cell.
        for (int z = 1; z < SIZE - 1; z++) {
            for (int x = 1; x < SIZE - 1; x++) {
                if (!g.forest[x][z] || g.box[x][z] || !g.free(x, FLOOR + 1, z)) continue;
                double patch = hash(x / 5, 0, z / 5, 70);
                double q = hash(x, 1, z, 70);
                if (q < 0.10 + 0.10 * patch && hash(x, 2, z, 70) < 0.5) {
                    c.set(x, FLOOR, z, patch < 0.5 ? "minecraft:moss_block" : "minecraft:podzol[snowy=false]");
                }
                double r = hash(x, 3, z, 70);
                boolean tall = g.air(x, FLOOR + 2, z) && !g.nearPath[x][z];
                if (r < 0.18) c.set(x, FLOOR + 1, z, "minecraft:short_grass");
                else if (r < 0.26 && tall) {
                    c.set(x, FLOOR + 1, z, "minecraft:tall_grass[half=lower]");
                    c.set(x, FLOOR + 2, z, "minecraft:tall_grass[half=upper]");
                } else if (r < 0.31) c.set(x, FLOOR + 1, z, "minecraft:fern");
                else if (r < 0.35 && tall) {
                    c.set(x, FLOOR + 1, z, "minecraft:large_fern[half=lower]");
                    c.set(x, FLOOR + 2, z, "minecraft:large_fern[half=upper]");
                } else if (r < 0.39) c.set(x, FLOOR + 1, z, FLOWERS[(int) (hash(x, 4, z, 70) * FLOWERS.length)]);
                else if (r < 0.42) c.set(x, FLOOR + 1, z, hash(x, 5, z, 70) < 0.6 ? "minecraft:brown_mushroom" : "minecraft:red_mushroom");
                else if (r < 0.50) c.set(x, FLOOR + 1, z, "minecraft:moss_carpet");
                else if (r < 0.52 && !g.nearPath[x][z]) c.set(x, FLOOR + 1, z, hash(x, 6, z, 70) < 0.5 ? "minecraft:azalea" : "minecraft:flowering_azalea");
            }
        }
    }

    // ---- light --------------------------------------------------------------------------------------------------

    /** Invisible light blocks just under the canopy every 4 blocks, so the forest floor is not left in the dark. */
    private static void canopyLights(Grid g) {
        Canvas c = g.c;
        for (int z = 2; z < SIZE; z += 4) {
            for (int x = 2; x < SIZE; x += 4) {
                for (int y = FLOOR + 6; y < Canvas.HEIGHT - 2; y++) {
                    if (!c.get(x, y, z).contains("_leaves")) continue;
                    if (g.air(x, y - 1, z)) c.set(x, y - 1, z, DistrictKit.LIGHT);
                    break;
                }
            }
        }
    }

    // ---- hangings -----------------------------------------------------------------------------------------------

    /** Vines on the leaf sides and glow-berry vines under the canopy. */
    private static void hangings(Grid g) {
        Canvas c = g.c;
        int[][] side = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = 1; z < SIZE - 1; z++) {
            for (int x = 1; x < SIZE - 1; x++) {
                for (int y = FLOOR + 6; y < Canvas.HEIGHT - 2; y++) {
                    String b = c.get(x, y, z);
                    if (!b.contains("_leaves")) continue;
                    double q = hash(x, y, z, 71);
                    if (q > 0.10) continue;
                    if (g.air(x, y - 1, z) && !g.kept[x][z] && !(g.nearPath[x][z] && y < FLOOR + 12)) {
                        // Glow berries hang straight down from the underside, the lowest block carrying the berries.
                        int len = 2 + (int) (hash(x, y, z, 72) * 4);
                        int bottom = y - 1;
                        for (int i = 0; i < len && g.air(x, y - 1 - i, z) && y - 1 - i > FLOOR + 3; i++) bottom = y - 1 - i;
                        if (q < 0.035) {
                            for (int yy = y - 1; yy > bottom; yy--) c.set(x, yy, z, "minecraft:cave_vines_plant[berries=true]");
                            c.set(x, bottom, z, "minecraft:cave_vines[age=25,berries=true]");
                        } else {
                            for (int yy = y - 1; yy >= bottom; yy--) c.set(x, yy, z, "minecraft:vine[east=false,north=false,south=false,up=true,west=false]");
                        }
                    } else {
                        int k = (int) (hash(x, y, z, 73) * 4);
                        int vx = x + side[k][0], vz = z + side[k][1];
                        if (g.air(vx, y, vz) && !g.kept[vx][vz]) {
                            String vine = vine(k);
                            int len = 1 + (int) (hash(x, y, z, 74) * 3);
                            for (int i = 0; i < len && g.air(vx, y - i, vz) && y - i > FLOOR + 3; i++) c.set(vx, y - i, vz, vine);
                        }
                    }
                }
            }
        }
    }

    /** A vine placed at leaf + side[k], so it is stuck to the face looking back at the leaf. */
    private static String vine(int k) {
        String[] attach = {"west", "east", "north", "south"};
        StringBuilder sb = new StringBuilder("minecraft:vine[");
        for (String f : new String[] {"east", "north", "south", "up", "west"}) {
            sb.append(f).append('=').append(f.equals(attach[k])).append(f.equals("west") ? "" : ",");
        }
        return sb.append(']').toString();
    }
}
