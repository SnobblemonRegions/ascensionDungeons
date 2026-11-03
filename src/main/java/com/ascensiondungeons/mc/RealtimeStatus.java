package com.ascensiondungeons.mc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Burn, poison and bleed in real time: a ledger of who applied what to whom. Each entry ticks once a second; the caller turns a
 * tick into damage (so the same faint, affix and Unique rules apply) through {@link Sink}. Numbers follow the catalogue text:
 * burn 1/16 max HP for 8 s, poison 1/8 for 10 s (Creeping Venom grows it), bleed 1/12 for 3 s stacking to 3 (Rupture weights the stacks).
 */
final class RealtimeStatus {
    enum Kind {
        BURN("burn", 1.0 / 16, 8), POISON("poison", 1.0 / 8, 10), BLEED("bleeding", 1.0 / 12, 3);
        final String label; final double fraction; final int seconds;
        Kind(String label, double fraction, int seconds) { this.label = label; this.fraction = fraction; this.seconds = seconds; }
    }

    static final class Entry {
        final Kind kind;
        UUID applier;
        int applierLevel;
        double potency = 1.0;
        double growth;           // Creeping Venom: extra per tick, up to maxGrowth
        double maxGrowth;
        double[] stackWeights = {1.0, 1.0, 1.0};
        int stacks = 1;
        int ticks;
        long end, next;
        Entry(Kind kind) { this.kind = kind; }

        /** Fraction of max HP for this tick, before the damage budget cap. */
        double fraction() {
            double f = kind.fraction * potency * (1.0 + Math.min(maxGrowth, growth * ticks));
            if (kind == Kind.BLEED) {
                double w = 0;
                for (int i = 0; i < stacks; i++) w += stackWeights[i];
                f *= w;
            }
            return f;
        }
    }

    interface Sink {
        /** Deals one tick. Returns false when the target is gone and the entry should be dropped. */
        boolean tick(UUID target, Entry entry);

        /** An entry ended or was dropped. */
        void ended(UUID target, Entry entry);
    }

    private final Map<UUID, Map<Kind, Entry>> byTarget = new HashMap<>();

    boolean has(UUID target) {
        Map<Kind, Entry> m = byTarget.get(target);
        return m != null && !m.isEmpty();
    }

    boolean has(UUID target, Kind kind) {
        Map<Kind, Entry> m = byTarget.get(target);
        return m != null && m.containsKey(kind);
    }

    /** Applies a status (or refreshes it; bleed adds a stack up to 3). Returns the entry. */
    Entry apply(UUID target, Kind kind, UUID applier, int applierLevel, long now) {
        Map<Kind, Entry> m = byTarget.computeIfAbsent(target, k -> new HashMap<>());
        Entry e = m.get(kind);
        if (e == null) {
            e = new Entry(kind);
            m.put(kind, e);
            e.next = now + 20;
        } else if (kind == Kind.BLEED) e.stacks = Math.min(3, e.stacks + 1);
        e.applier = applier;
        e.applierLevel = applierLevel;
        e.end = now + kind.seconds * 20L;
        return e;
    }

    void tick(long now, Sink sink) {
        if (byTarget.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Map<Kind, Entry>>> it = byTarget.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<Kind, Entry>> t = it.next();
            List<Entry> due = new ArrayList<>(t.getValue().values());
            for (Entry e : due) {
                if (now > e.end) { t.getValue().remove(e.kind); sink.ended(t.getKey(), e); continue; }
                if (now < e.next) continue;
                e.next = now + 20;
                boolean alive = sink.tick(t.getKey(), e);
                e.ticks++;
                if (!alive || now >= e.end) {
                    t.getValue().remove(e.kind);
                    sink.ended(t.getKey(), e);
                }
            }
            if (t.getValue().isEmpty()) it.remove();
        }
    }

    void clear() { byTarget.clear(); }
}
