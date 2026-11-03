package com.ascensiondungeons.gen.landmark;

/**
 * Gantry Walk: the Ancient Foundry exploration landmark. A complete nine-block iron-and-stone walkway runs east to west
 * across a shallow, enclosed trench of cooled slag (carved by the layout), with continuous iron-bar railings along the
 * walkway and round the trench, and two heavy iron girders, each a pair of posts joined by a beam, standing on the bank
 * either side. The deck is flush with the floor, so the walk needs no steps.
 *
 * <p>Module coordinates: x in [0, 28], z in [0, 14], y in [0, 13]; y=0 is the floor row. The walkway is z 3..11; the trench
 * is {@link #TRENCH_X0}..{@link #TRENCH_X1} by z 1..13 and is left (null) except for the deck that crosses it.
 */
public final class GantryWalk {
    private GantryWalk() {}

    public static final String ID = "foundry_gantry_walk";
    public static final int SIZE_X = 29, SIZE_Y = 14, SIZE_Z = 15;
    public static final int ORIGIN_Y = 4;
    public static final int WALK_Z0 = 3, WALK_Z1 = 11;
    public static final int TRENCH_X0 = 8, TRENCH_X1 = 20, TRENCH_Z0 = 1, TRENCH_Z1 = 13;

    private static final String ANDESITE = "minecraft:polished_andesite";
    private static final String STONE = "minecraft:stone_bricks";
    private static final String DEEP = "minecraft:deepslate_bricks";
    private static final String IRON = "minecraft:iron_block";
    private static final String COPPER = "minecraft:cut_copper";
    private static final String BARS = "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]";
    private static final String GLOW = "minecraft:ochre_froglight[axis=y]";

    public static Landmark landmarkAt(int x, int z) {
        return new Landmark(ID, build(), x, ORIGIN_Y, z, false, 0);
    }

    public static boolean inTrench(int x, int z) {
        return x >= TRENCH_X0 && x <= TRENCH_X1 && z >= TRENCH_Z0 && z <= TRENCH_Z1;
    }

    public static Voxels build() {
        Voxels v = new Voxels(SIZE_X, SIZE_Y, SIZE_Z);
        // Deck: stone and andesite with an iron-block grating stripe every fourth block, copper rivets on the edges.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = WALK_Z0; z <= WALK_Z1; z++) {
                String s = z == WALK_Z0 || z == WALK_Z1 ? COPPER : x % 4 == 0 ? IRON : (x + z) % 2 == 0 ? ANDESITE : STONE;
                v.set(x, 0, z, s);
            }
        }
        // The trench's end rows are solid bank, so the slag is enclosed.
        for (int x = TRENCH_X0 - 1; x <= TRENCH_X1 + 1; x++) {
            for (int z : new int[] {TRENCH_Z0 - 1, TRENCH_Z1 + 1}) v.set(x, 0, z, DEEP);
        }
        // Railings: along both deck edges end to end, round the open parts of the trench, with copper posts.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z : new int[] {WALK_Z0, WALK_Z1}) {
                v.set(x, 1, z, x % 4 == 0 ? COPPER : BARS);
                v.set(x, 2, z, x % 4 == 0 ? COPPER : BARS);
            }
        }
        for (int x : new int[] {TRENCH_X0 - 1, TRENCH_X1 + 1}) {
            for (int z = TRENCH_Z0 - 1; z <= TRENCH_Z1 + 1; z++) {
                if (z >= WALK_Z0 && z <= WALK_Z1) continue;
                v.set(x, 1, z, BARS);
                v.set(x, 2, z, BARS);
            }
        }
        for (int x = TRENCH_X0 - 1; x <= TRENCH_X1 + 1; x++) {
            for (int z : new int[] {TRENCH_Z0 - 1, TRENCH_Z1 + 1}) {
                v.set(x, 1, z, BARS);
                v.set(x, 2, z, BARS);
            }
        }
        // Heavy girders: on each side a pair of 2 x 2 iron posts on the bank joined by a beam across the trench.
        for (int side = 0; side < 2; side++) {
            int z0 = side == 0 ? 0 : SIZE_Z - 2;
            for (int x : new int[] {5, 22}) {
                for (int y = 1; y <= 9; y++) {
                    for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++) v.set(x + dx, y, z0 + dz, y == 5 ? COPPER : IRON);
                }
            }
            for (int x = 5; x <= 23; x++) {
                for (int y = 9; y <= 10; y++) for (int dz = 0; dz < 2; dz++) v.set(x, y, z0 + dz, y == 10 ? IRON : DEEP);
            }
            for (int x : new int[] {TRENCH_X0 + 3, TRENCH_X0 + 9}) v.set(x, 8, z0 + (side == 0 ? 1 : 0), GLOW);
        }
        return v;
    }
}
