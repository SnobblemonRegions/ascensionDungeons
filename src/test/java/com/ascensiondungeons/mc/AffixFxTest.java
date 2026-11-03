package com.ascensiondungeons.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AffixFxTest {
    private final AffixFx fx = AffixFx.forTest(new int[] {100, 50, 50});

    private static AffixFx.Eff e(String id, int pct, String type) { return new AffixFx.Eff(id, pct, type); }

    private static AffixFx.Hit hit(String type, boolean physical, double userHp, boolean status, double targetHp, boolean se, boolean first, int kos) {
        return new AffixFx.Hit(type, physical, userHp, status, targetHp, se, first, Set.of("fire"), kos);
    }

    @Test void outgoingConditionsAndStacking() {
        var fire = hit("fire", false, 1.0, false, 1.0, false, false, 0);
        assertEquals(1.0, fx.outgoing(List.of(), fire), 1e-9);
        assertEquals(1.08, fx.outgoing(List.of(e("type_focus", 8, "fire")), fire), 1e-9);
        assertEquals(1.0, fx.outgoing(List.of(e("type_focus", 8, "water")), fire), 1e-9);
        assertEquals(1.0, fx.outgoing(List.of(e("physical_force", 6, "")), fire), 1e-9);
        assertEquals(1.06 * 1.05, fx.outgoing(List.of(e("special_force", 6, ""), e("healthy_force", 5, "")), fire), 1e-9);
        assertEquals(1.0, fx.outgoing(List.of(e("last_stand", 10, "")), fire), 1e-9);
        assertEquals(1.1, fx.outgoing(List.of(e("last_stand", 10, "")), hit("fire", false, 0.3, false, 1.0, false, false, 0)), 1e-9);
        assertEquals(1.0 + 0.06 * 2, 0.06 * 2 + 1.0, 1e-9);
        assertEquals(1.12, fx.outgoing(List.of(e("momentum", 6, "")), hit("fire", true, 1, false, 1, false, false, 2)), 1e-9);
        assertEquals(1.18, fx.outgoing(List.of(e("momentum", 6, "")), hit("fire", true, 1, false, 1, false, false, 9)), 1e-9);
    }

    @Test void outgoingIsCappedAndAshenHeartCosts() {
        var h = hit("fire", false, 1.0, false, 1.0, false, false, 0);
        var many = List.of(e("overwhelming_force", 100, ""), e("overwhelming_force", 100, ""));
        assertEquals(2.0, fx.outgoing(many, h), 1e-9);
        assertEquals(0.85, fx.outgoing(List.of(e("ashen_heart", 1, "")), h), 1e-9);
    }

    @Test void incomingReductionsCapAndUniqueDrawbacks() {
        var h = hit("psychic", true, 1.0, false, 1.0, true, false, 0);
        assertEquals(0.95, fx.incoming(List.of(e("iron_resolve", 5, "")), h, false), 1e-9);
        assertEquals(0.5, fx.incoming(List.of(e("iron_resolve", 80, ""), e("physical_bulwark", 80, "")), h, false), 1e-9);
        assertEquals(1.0, fx.incoming(List.of(e("opening_guard", 10, "")), h, false), 1e-9);
        assertEquals(0.9, fx.incoming(List.of(e("opening_guard", 10, "")), h, true), 1e-9);
        assertEquals(1.25, fx.incoming(List.of(e("triple_seven", 1, "")), h, false), 1e-9);
        assertEquals(1.2, fx.incoming(List.of(e("creeping_venom", 1, "")), h, false), 1e-9);
        assertEquals(1.0, fx.incoming(List.of(e("creeping_venom", 1, "")), hit("fire", true, 1, false, 1, false, false, 0), false), 1e-9);
    }

    @Test void healingScale() {
        assertEquals(1.0, fx.healing(List.of(), true), 1e-9);
        assertEquals(1.1, fx.healing(List.of(e("restorative", 10, "")), true), 1e-9);
        assertEquals(1.0, fx.healing(List.of(e("restorative", 10, "")), false), 1e-9);
        assertEquals(0.5, fx.healing(List.of(e("last_breath", 1, "")), false), 1e-9);
        assertEquals(0.0, fx.healing(List.of(e("titans_heart", 1, "")), true), 1e-9);
        assertEquals(0.08, fx.knockoutHeal(List.of(e("triumphant", 8, ""))), 1e-9);
        assertTrue(fx.knockoutHeal(List.of(e("triumphant", 90, ""))) <= 0.5);
    }

    @Test void statusLedgerTicksStacksAndExpires() {
        var ledger = new RealtimeStatus();
        var target = java.util.UUID.randomUUID();
        var applier = java.util.UUID.randomUUID();
        var e = ledger.apply(target, RealtimeStatus.Kind.BLEED, applier, 30, 0);
        e.stackWeights = new double[] {1.0, 0.8, 0.6};
        ledger.apply(target, RealtimeStatus.Kind.BLEED, applier, 30, 5);
        assertEquals(2, e.stacks);
        assertEquals((1.0 / 12) * 1.8, e.fraction(), 1e-9);
        int[] ticks = {0}; boolean[] ended = {false};
        var sink = new RealtimeStatus.Sink() {
            public boolean tick(java.util.UUID t, RealtimeStatus.Entry x) { ticks[0]++; return true; }
            public void ended(java.util.UUID t, RealtimeStatus.Entry x) { ended[0] = true; }
        };
        for (long now = 0; now <= 200; now++) ledger.tick(now, sink);
        assertEquals(3, ticks[0]);          // bleed lasts 3 s from the last application at tick 5
        assertTrue(ended[0]);
        assertEquals(false, ledger.has(target));
    }

    @Test void creepingVenomGrowsToItsLimit() {
        var ledger = new RealtimeStatus();
        var e = ledger.apply(java.util.UUID.randomUUID(), RealtimeStatus.Kind.POISON, java.util.UUID.randomUUID(), 20, 0);
        e.growth = 0.2; e.maxGrowth = 0.8;
        assertEquals(1.0 / 8, e.fraction(), 1e-9);
        e.ticks = 2;
        assertEquals(1.0 / 8 * 1.4, e.fraction(), 1e-9);
        e.ticks = 10;
        assertEquals(1.0 / 8 * 1.8, e.fraction(), 1e-9);
    }
}
