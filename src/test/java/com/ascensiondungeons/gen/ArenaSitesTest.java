package com.ascensiondungeons.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ascensiondungeons.gen.layout.Layouts;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaSitesTest {
    @Test
    void everyDistrictHasAnArenaInsideItsCellWithTheActivationFifteenFromTheAnchor() {
        List<BiomeKind> all = Layouts.available();
        assertEquals(16, all.size());
        DistrictGrid grid = new DistrictGrid(7, 2, all);
        List<ArenaSites.Site> sites = ArenaSites.of(grid);
        assertEquals(grid.cells().size(), sites.size());
        for (ArenaSites.Site s : sites) {
            int x0 = s.cx() * DistrictGrid.CELL - Template.CENTER, z0 = s.cz() * DistrictGrid.CELL - Template.CENTER;
            for (int[] p : List.of(s.anchor(), s.activation(), s.staging())) {
                assertTrue(p[0] >= x0 && p[0] < x0 + DistrictGrid.CELL && p[2] >= z0 && p[2] < z0 + DistrictGrid.CELL,
                        s.biome() + " marker outside its cell");
            }
            double d = Math.hypot(s.activation()[0] - s.anchor()[0], s.activation()[2] - s.anchor()[2]);
            assertEquals(15, d, 1.5, s.biome() + " activation distance");
        }
    }
}
