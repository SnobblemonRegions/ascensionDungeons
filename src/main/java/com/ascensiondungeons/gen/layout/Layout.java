package com.ascensiondungeons.gen.layout;

import com.ascensiondungeons.gen.SpongeSchematic;
import com.ascensiondungeons.gen.landmark.Landmark;
import java.util.List;
import java.util.Map;

/**
 * A district's authored base terrain, the landmark modules to paste into it, and named template positions for operator
 * tools and runtime markers: {@code entry}, {@code nav}, {@code loot}, {@code explore} and {@code arena} (positions to
 * stand at, {x, z}).
 */
public record Layout(SpongeSchematic base, List<Landmark> landmarks, Map<String, int[]> spots) {}
