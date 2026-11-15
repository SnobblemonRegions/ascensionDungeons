package com.ascensiondungeons.outbreak;

/**
 * A telegraphed attack: a warning on the ground, then the hit. Players dodge it by leaving the circle.
 *
 * @param id            stable id, e.g. {@code whirlpool}
 * @param name          shown to players (use the move's own name)
 * @param cooldownTicks ticks before the same caster can cast again
 * @param warnTicks     how long the warning shows before the hit
 * @param range         the caster must be within this of its target to cast
 * @param radius        the circle's radius
 * @param damage        damage to each player in the circle when it lands
 * @param effect        what else the hit does
 * @param strength      effect size: blocks per tick for PULL and KNOCKUP, seconds for SLOW
 */
public record Spell(String id, String name, int cooldownTicks, int warnTicks, double range, double radius, double damage, Effect effect, double strength) {
    public enum Effect { NONE, PULL, SLOW, KNOCKUP }

    public Spell {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("a spell needs an id");
        if (cooldownTicks < 1 || warnTicks < 1) throw new IllegalArgumentException("cooldown and warning must be at least 1 tick");
        if (range <= 0 || radius <= 0) throw new IllegalArgumentException("range and radius must be positive");
        if (damage < 0 || strength < 0) throw new IllegalArgumentException("damage and strength must not be negative");
        if (warnTicks > cooldownTicks) throw new IllegalArgumentException("the warning cannot outlast the cooldown");
        if (effect == null) effect = Effect.NONE;
        if (name == null || name.isBlank()) name = id;
    }

    /** One caster's casting state. Pure: the caller draws the warning and applies the hit. */
    public static final class Cast {
        public enum Phase { IDLE, WARNING, HIT }

        private Spell current;
        private long hitAt;
        private long readyAt;

        /** Starts a cast if the caster is off cooldown, not already casting, and close enough. */
        public boolean begin(Spell spell, long now, double distance) {
            if (current != null || now < readyAt || distance > spell.range()) return false;
            current = spell;
            hitAt = now + spell.warnTicks();
            readyAt = now + spell.cooldownTicks();
            return true;
        }

        /** IDLE, WARNING while the circle shows, then HIT exactly once when it lands. */
        public Phase poll(long now) {
            if (current == null) return Phase.IDLE;
            if (now < hitAt) return Phase.WARNING;
            return Phase.HIT;
        }

        /** The spell being cast, or null. */
        public Spell spell() { return current; }

        /** Call after handling HIT (or when the caster dies) to finish the cast. */
        public void finish() { current = null; }

        public boolean ready(long now) { return current == null && now >= readyAt; }
    }
}
