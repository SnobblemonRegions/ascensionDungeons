package com.ascensiondungeons.run;

import java.util.List;
import java.util.Random;

/** Weighted wild-Pokemon spawn entries. Pure maths, no Minecraft or Cobblemon types. */
public final class SpawnTable {
    private SpawnTable() {}

    /** A species with a selection weight and a level range (inclusive). Public mutable fields so the config file maps onto it directly. */
    public static final class Entry {
        public String species;
        public int weight = 10;
        public int min = 10;
        public int max = 20;

        public Entry() {}

        public Entry(String species, int weight, int min, int max) {
            this.species = species;
            this.weight = weight;
            this.min = min;
            this.max = max;
        }
    }

    /** A weighted random entry; entries with a non-positive weight never win. Null when nothing can be picked. */
    public static Entry pick(List<Entry> entries, Random rng) {
        if (entries == null) return null;
        long total = 0;
        for (Entry e : entries) if (e.weight > 0) total += e.weight;
        if (total <= 0) return null;
        long roll = (long) (rng.nextDouble() * total);
        for (Entry e : entries) {
            if (e.weight <= 0) continue;
            if (roll < e.weight) return e;
            roll -= e.weight;
        }
        return null;
    }

    /** A level in [min, max]; a reversed range is treated as its swap, and the result is never below 1. */
    public static int level(Entry e, Random rng) {
        int lo = Math.min(e.min, e.max);
        int hi = Math.max(e.min, e.max);
        return Math.max(1, lo + rng.nextInt(hi - lo + 1));
    }
}
