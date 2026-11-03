package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * AscensionLib affixes in real-time combat. The library says which affixes a Pokemon holds ({@code AscensionCombatFx}, reached by
 * reflection like the rest of AscensionLib); this class applies the ordinary damage and recovery affixes with the same conditions and
 * stacking as the turn-based simulator module: each active effect is its own multiplier, then each channel is capped once.
 * Not applied in real time: residual (burn/poison/bleed potency), swift_strike, bracing_entry, wardstone, stubborn, uniques.
 */
final class AffixFx {
    record Eff(String id, int percent, String type) { }

    /** What a hit looks like to the affixes. */
    record Hit(String moveType, boolean physical, double userHp, boolean userStatus, double targetHp, boolean superEffective,
               boolean firstMove, java.util.Set<String> userTypes, int kos) { }

    private final Method effectsOf, caps, zoneTier;
    private final Map<UUID, List<Eff>> cache = new HashMap<>();
    private int[] ceilings = {100, 50, 50};

    private AffixFx(Method effectsOf, Method caps, Method zoneTier) { this.effectsOf = effectsOf; this.caps = caps; this.zoneTier = zoneTier; }

    /** For tests: the maths without the library. */
    static AffixFx forTest(int[] ceilings) {
        AffixFx fx = new AffixFx(null, null, null);
        fx.ceilings = ceilings;
        return fx;
    }

    /** Null when AscensionLib (or a version with {@code AscensionCombatFx}) is absent. */
    static AffixFx tryCreate() {
        try {
            Class<?> fx = Class.forName("com.ascensionlib.AscensionCombatFx");
            Class<?> zones = Class.forName("com.ascensionlib.AscensionZones");
            return new AffixFx(fx.getMethod("effectsOf", Object.class, int.class), fx.getMethod("caps"), zones.getMethod("zoneTier", String.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    void refreshCaps() {
        try { ceilings = (int[]) caps.invoke(null); } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) { }
    }

    /** The Pokemon's effects, read once per fight (a dungeon Pokemon is rated at its level's zone tier). Empty when it has none. */
    List<Eff> of(Pokemon p, ServerLevel level) {
        return cache.computeIfAbsent(p.getUuid(), id -> {
            var out = new ArrayList<Eff>();
            try {
                int tier = (int) zoneTier.invoke(null, level.dimension().location().toString());
                for (Object line : (List<?>) effectsOf.invoke(null, p, Math.max(1, tier))) {
                    String[] f = String.valueOf(line).split("\\|", -1);
                    out.add(new Eff(f[0], Integer.parseInt(f[1]), f.length > 2 ? f[2] : ""));
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                AscensionDungeonsLog.LOG.warn("Could not read affixes for {}", p.getSpecies().getName(), e);
            }
            return out;
        });
    }

    void clear() { cache.clear(); }

    /** Damage multiplier of the user's outgoing affixes against this hit (1.0 = none). */
    double outgoing(List<Eff> effects, Hit h) {
        double mult = 1.0;
        for (Eff e : effects) {
            boolean on = switch (e.id()) {
                case "type_focus", "type_mastery" -> e.type().equalsIgnoreCase(h.moveType());
                case "physical_force" -> h.physical();
                case "special_force" -> !h.physical();
                case "healthy_force" -> h.userHp() >= 0.8;
                case "last_stand" -> h.userHp() <= 0.35;
                case "defiant_force" -> h.userStatus();
                case "overwhelming_force", "momentum" -> true;
                case "kindred_force" -> h.userTypes().contains(h.moveType());
                case "executioner" -> h.targetHp() <= 0.5;
                case "opening_strike" -> h.firstMove();
                case "super_effective_force" -> h.superEffective();
                default -> false;
            };
            if (!on) continue;
            int pct = e.id().equals("momentum") ? e.percent() * Math.min(3, h.kos()) : e.percent();
            mult *= 1.0 + pct / 100.0;
        }
        return Math.min(mult, 1.0 + ceilings[0] / 100.0) * (has(effects, "ashen_heart") ? 0.85 : 1.0);
    }

    /** Damage multiplier of the target's incoming-reduction affixes (1.0 = none, never below 1 - cap). {@code firstHit} is the target's first hit taken. */
    double incoming(List<Eff> effects, Hit h, boolean firstHit) {
        double keep = 1.0;
        for (Eff e : effects) {
            boolean on = switch (e.id()) {
                case "type_ward", "type_bulwark" -> e.type().equalsIgnoreCase(h.moveType());
                case "healthy_guard" -> h.userHp() >= 0.8;
                case "steadfast_guard" -> h.userHp() <= 0.35;
                case "status_guard" -> h.userStatus();
                case "opening_guard" -> firstHit;
                case "iron_resolve" -> true;
                case "resilient_hide" -> h.superEffective();
                case "physical_bulwark" -> h.physical();
                case "special_bulwark" -> !h.physical();
                default -> false;
            };
            if (on) keep *= 1.0 - e.percent() / 100.0;
        }
        keep = Math.max(keep, 1.0 - ceilings[1] / 100.0);
        if (has(effects, "triple_seven")) keep *= 1.25;
        if (has(effects, "creeping_venom") && h.moveType().equalsIgnoreCase("psychic")) keep *= 1.2;
        return keep;
    }

    /** Percent chance of an extra critical hit (keen_edge) or of slowing the target (ensnaring); 0 when not held. */
    static int percentOf(List<Eff> effects, String id) {
        int total = 0;
        for (Eff e : effects) if (e.id().equals(id)) total += e.percent();
        return total;
    }

    static boolean has(List<Eff> effects, String id) {
        for (Eff e : effects) if (e.id().equals(id)) return true;
        return false;
    }

    /** Scale of any healing the holder receives; {@code fromMove} adds Restorative (capped with the healing channel). */
    double healing(List<Eff> effects, boolean fromMove) {
        if (has(effects, "titans_heart")) return 0.0;
        double scale = has(effects, "last_breath") ? 0.5 : 1.0;
        if (fromMove) scale *= 1.0 + Math.min(percentOf(effects, "restorative"), ceilings[2]) / 100.0;
        return scale;
    }

    /** Share of max HP healed after a knockout (triumphant), capped like the healing channel. */
    double knockoutHeal(List<Eff> effects) { return Math.min(percentOf(effects, "triumphant"), ceilings[2]) / 100.0; }
}
