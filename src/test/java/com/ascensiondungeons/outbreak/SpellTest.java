package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SpellTest {
    private final Spell whirlpool = new Spell("whirlpool", "Whirlpool", 200, 40, 12, 4, 6, Spell.Effect.PULL, 0.4);

    @Test
    void aCastWarnsThenHitsOnceThenWaitsOutItsCooldown() {
        Spell.Cast c = new Spell.Cast();
        assertEquals(Spell.Cast.Phase.IDLE, c.poll(0));
        assertTrue(c.begin(whirlpool, 100, 8));
        assertEquals(Spell.Cast.Phase.WARNING, c.poll(100));
        assertEquals(Spell.Cast.Phase.WARNING, c.poll(139));
        assertEquals(Spell.Cast.Phase.HIT, c.poll(140));
        assertSame(whirlpool, c.spell());
        c.finish();
        assertEquals(Spell.Cast.Phase.IDLE, c.poll(141));
        assertFalse(c.begin(whirlpool, 200, 8), "still on cooldown");
        assertFalse(c.ready(299));
        assertTrue(c.ready(300));
        assertTrue(c.begin(whirlpool, 300, 8));
    }

    @Test
    void itCannotCastOutOfRangeOrWhileAlreadyCasting() {
        Spell.Cast c = new Spell.Cast();
        assertFalse(c.begin(whirlpool, 0, 12.5));
        assertTrue(c.begin(whirlpool, 0, 12));
        assertFalse(c.begin(whirlpool, 10, 1));
    }

    @Test
    void defaultsAndValidation() {
        Spell s = new Spell("geyser", null, 100, 20, 8, 2, 4, null, 0);
        assertEquals("geyser", s.name());
        assertEquals(Spell.Effect.NONE, s.effect());
        assertThrows(IllegalArgumentException.class, () -> new Spell("", "x", 100, 20, 8, 2, 4, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new Spell("x", "x", 10, 20, 8, 2, 4, null, 0), "warning longer than cooldown");
        assertThrows(IllegalArgumentException.class, () -> new Spell("x", "x", 100, 20, 0, 2, 4, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new Spell("x", "x", 100, 20, 8, 2, -1, null, 0));
    }
}
