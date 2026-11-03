package com.ascensiondungeons.gen;

import com.ascensiondungeons.gen.layout.Layouts;
import com.ascensiondungeons.gen.landmark.WardensRing;
import java.util.ArrayList;
import java.util.List;

/**
 * Where each district's raid arena is in a run's world. Layouts publish template-space spots ({@code arena_centre} is the
 * boss anchor, {@code arena} the activation point at the doorway); this applies each cell's orientation.
 */
public final class ArenaSites {
    private ArenaSites() {}

    /** World block coordinates {x, y, z}. {@code staging} lies a little further out along the doorway axis. */
    public record Site(int cx, int cz, BiomeKind biome, int[] anchor, int[] activation, int[] staging) {}

    public static List<Site> of(DistrictGrid grid) {
        List<Site> out = new ArrayList<>();
        for (DistrictGrid.Cell cell : grid.cells()) {
            var spots = Layouts.of(cell.biome()).spots();
            int[] centre = spots.get("arena_centre");
            int[] act = spots.get("arena");
            if (centre == null || act == null) continue;
            int[] anchor = world(cell, centre[0], centre[1]);
            int[] activation = world(cell, act[0], act[1]);
            // Staging: two blocks past the activation point, away from the centre.
            double dx = act[0] - centre[0], dz = act[1] - centre[1];
            double len = Math.max(1, Math.hypot(dx, dz));
            int[] staging = world(cell, (int) Math.round(act[0] + 2 * dx / len), (int) Math.round(act[1] + 2 * dz / len));
            out.add(new Site(cell.cx(), cell.cz(), cell.biome(), anchor, activation, staging));
        }
        return out;
    }

    private static int[] world(DistrictGrid.Cell cell, int tx, int tz) {
        int[] d = cell.orientation().toWorld(tx - Template.CENTER, tz - Template.CENTER);
        return new int[] {cell.cx() * DistrictGrid.CELL + d[0], WardensRing.ANCHOR[1], cell.cz() * DistrictGrid.CELL + d[1]};
    }
}
