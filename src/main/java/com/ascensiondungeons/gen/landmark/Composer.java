package com.ascensiondungeons.gen.landmark;

import com.ascensiondungeons.gen.SpongeSchematic;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Applies landmark modules to a district template, producing a new template. The base is never modified. */
public final class Composer {
    private Composer() {}

    /** What a composition changed outside the modules' own boxes, so tests can show nothing else was touched. */
    public record Result(SpongeSchematic schematic, int removedTreeBlocks, int removedPlantBlocks) {}

    public static Result apply(SpongeSchematic base, List<Landmark> landmarks) {
        List<String> palette = new ArrayList<>(Arrays.asList(base.palette()));
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < palette.size(); i++) index.put(palette.get(i), i);
        short[] blocks = base.blocks().clone();
        int air = index.get(Voxels.AIR);
        int removedTrees = 0, removedPlants = 0;

        for (Landmark lm : landmarks) {
            Voxels v = lm.voxels();
            if (lm.originX() < 0 || lm.originY() < 0 || lm.originZ() < 0
                    || lm.originX() + v.sizeX() > base.width() || lm.originY() + v.sizeY() > base.height()
                    || lm.originZ() + v.sizeZ() > base.length()) {
                throw new IllegalArgumentException(lm.id() + " does not fit inside the template");
            }
            if (lm.clearTrees()) removedTrees += removeTrees(base, blocks, palette, lm, air);
            if (lm.clearPlantsRadius() > 0) removedPlants += removePlants(base, blocks, palette, lm, air);
            for (int y = 0; y < v.sizeY(); y++) {
                for (int z = 0; z < v.sizeZ(); z++) {
                    for (int x = 0; x < v.sizeX(); x++) {
                        String state = v.get(x, y, z);
                        if (state == null) continue;
                        int idx = index.computeIfAbsent(state, s -> {
                            palette.add(s);
                            return palette.size() - 1;
                        });
                        blocks[base.index(lm.originX() + x, lm.originY() + y, lm.originZ() + z)] = (short) idx;
                    }
                }
            }
        }
        SpongeSchematic out = new SpongeSchematic(base.width(), base.height(), base.length(),
                palette.toArray(new String[0]), blocks);
        return new Result(out, removedTrees, removedPlants);
    }

    /** Removes every ordinary tree (connected logs and leaves) that has a block inside the module's box. */
    private static int removeTrees(SpongeSchematic base, short[] blocks, List<String> palette, Landmark lm, int air) {
        Voxels v = lm.voxels();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        boolean[] seen = new boolean[blocks.length];
        for (int y = 0; y < v.sizeY(); y++) {
            for (int z = 0; z < v.sizeZ(); z++) {
                for (int x = 0; x < v.sizeX(); x++) {
                    int tx = lm.originX() + x, ty = lm.originY() + y, tz = lm.originZ() + z;
                    int idx = base.index(tx, ty, tz);
                    if (!seen[idx] && Landmark.TREE_BLOCK.test(palette.get(blocks[idx]))) {
                        seen[idx] = true;
                        queue.add(new int[] {tx, ty, tz});
                    }
                }
            }
        }
        int removed = 0;
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            blocks[base.index(p[0], p[1], p[2])] = (short) air;
            removed++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = p[0] + dx, ny = p[1] + dy, nz = p[2] + dz;
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= base.width() || ny >= base.height() || nz >= base.length()) continue;
                        int n = base.index(nx, ny, nz);
                        if (seen[n] || !Landmark.TREE_BLOCK.test(palette.get(blocks[n]))) continue;
                        seen[n] = true;
                        queue.add(new int[] {nx, ny, nz});
                    }
                }
            }
        }
        return removed;
    }

    private static int removePlants(SpongeSchematic base, short[] blocks, List<String> palette, Landmark lm, int air) {
        Voxels v = lm.voxels();
        int cx = lm.originX() + v.sizeX() / 2, cz = lm.originZ() + v.sizeZ() / 2;
        int r = lm.clearPlantsRadius();
        int removed = 0;
        for (int z = cz - r; z <= cz + r; z++) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int y = lm.originY() + 1; y <= lm.originY() + 3; y++) {
                    int idx = base.index(x, y, z);
                    if (Landmark.PLANT_BLOCK.test(palette.get(blocks[idx]))) {
                        blocks[idx] = (short) air;
                        removed++;
                    }
                }
            }
        }
        return removed;
    }
}
