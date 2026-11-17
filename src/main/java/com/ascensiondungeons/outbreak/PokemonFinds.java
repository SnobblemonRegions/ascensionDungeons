package com.ascensiondungeons.outbreak;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pokémon found in dive chests: how often a chest holds one, which tier it comes from at each difficulty, which species, at what
 * level, and the least ascension rarity AscensionLib gives it. Pure: no Minecraft, no Cobblemon.
 *
 * <p>A find is rare by design. The chance is per chest and grows with the chest's rarity and the set pieces that guard the best
 * chests. The tier odds grow with the difficulty. Legendary and mythical Pokémon come only from the Deep band on Nightmare, and
 * even there they are the rarest thing in the game.
 */
public final class PokemonFinds {
    private PokemonFinds() {}

    /** What kind of Pokémon a find is. Not AscensionLib's ascension rarity, which is graded separately. */
    public enum Tier {
        STANDARD("Standard"), STARTER("Starter"), PSEUDO("Pseudo-legendary"), LEGENDARY("Legendary"), MYTHICAL("Mythical");

        private final String label;
        Tier(String label) { this.label = label; }
        public String label() { return label; }
    }

    /** The chest a find can come from. */
    public enum Source { CHEST, NEST_CACHE, ALPHA_HOARD }

    /** One type dungeon's species by tier and its levels, from its {@code finds.json}. Species are Cobblemon ids, lower case. */
    public record Lists(Map<Tier, List<String>> species, int standardMin, int standardMax, Map<Tier, Integer> levels) {
        public List<String> of(Tier t) { return species.getOrDefault(t, List.of()); }
    }

    /** A find: the tier, the species and its level. */
    public record Find(Tier tier, String species, int level) {}

    /** The chance, 0..1, that a chest holds a Pokémon. The better the chest, and the harder it was to reach, the higher. */
    public static double chance(Source source, ChestRarity rarity) {
        return switch (source) {
            case ALPHA_HOARD -> 0.08;
            case NEST_CACHE -> 0.05;
            case CHEST -> switch (rarity) {
                case COMMON -> 0.003;
                case UNCOMMON -> 0.006;
                case RARE -> 0.012;
                case EPIC -> 0.025;
            };
        };
    }

    /**
     * The odds of each tier at a difficulty, in percent, in {@link Tier} order. Legendary and mythical only on Nightmare in the
     * Deep band; anywhere else their share goes to standard.
     */
    public static double[] weights(Tactics.Difficulty d, int band) {
        double[] w = switch (d) {
            case CASUAL -> new double[] {100, 0, 0, 0, 0};
            case NORMAL -> new double[] {90, 10, 0, 0, 0};
            case HARD -> new double[] {75, 20, 5, 0, 0};
            case NIGHTMARE -> new double[] {65, 23, 10, 1.8, 0.2};
        };
        if (d != Tactics.Difficulty.NIGHTMARE || band < DepthBands.DEEP) {
            w[0] += w[3] + w[4];
            w[3] = 0;
            w[4] = 0;
        }
        return w;
    }

    /** The least ascension rarity a find is given, as AscensionLib's id: higher difficulties guarantee better individuals. */
    public static String floor(Tactics.Difficulty d) {
        return switch (d) {
            case CASUAL -> "common";
            case NORMAL -> "uncommon";
            case HARD -> "rare";
            case NIGHTMARE -> "epic";
        };
    }

    /**
     * Whether this chest holds a Pokémon, and which. A tier with no species for this dungeon gives its share to the next tier down,
     * so a Water dive on Nightmare, with no legendary of its type, finds a pseudo-legendary instead and never nothing.
     *
     * @return the find, or null for none
     */
    public static Find roll(Random rng, Source source, ChestRarity rarity, Tactics.Difficulty d, int band, Lists lists) {
        if (rng.nextDouble() >= chance(source, rarity)) return null;
        double[] w = weights(d, band);
        for (int i = w.length - 1; i > 0; i--) if (lists.of(Tier.values()[i]).isEmpty()) { w[i - 1] += w[i]; w[i] = 0; }
        double total = 0;
        for (double x : w) total += x;
        if (total <= 0) return null;
        double roll = rng.nextDouble() * total;
        Tier tier = Tier.STANDARD;
        for (int i = 0; i < w.length; i++) {
            if (roll < w[i]) { tier = Tier.values()[i]; break; }
            roll -= w[i];
        }
        List<String> pool = lists.of(tier);
        if (pool.isEmpty()) return null;
        String species = pool.get(rng.nextInt(pool.size()));
        int level = tier == Tier.STANDARD
                ? lists.standardMin() + rng.nextInt(Math.max(1, lists.standardMax() - lists.standardMin() + 1))
                : lists.levels().getOrDefault(tier, 10);
        return new Find(tier, species, level);
    }

    /** Whether the whole server hears about a find, or only the party. */
    public static boolean announcedToServer(Tier tier) { return tier == Tier.LEGENDARY || tier == Tier.MYTHICAL; }

    public static boolean announcedToParty(Tier tier) { return tier.ordinal() >= Tier.PSEUDO.ordinal(); }

    /** Builds the lists from what {@code finds.json} holds. */
    public static Lists lists(Map<String, List<String>> byName, int standardMin, int standardMax, Map<String, Integer> levelsByName) {
        Map<Tier, List<String>> species = new EnumMap<>(Tier.class);
        Map<Tier, Integer> levels = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            String key = t == Tier.PSEUDO ? "pseudo" : t.name().toLowerCase(java.util.Locale.ROOT);
            species.put(t, List.copyOf(byName.getOrDefault(key, List.of())));
            Integer level = levelsByName.get(key + "Level");
            if (level != null) levels.put(t, level);
        }
        return new Lists(species, Math.min(standardMin, standardMax), Math.max(standardMin, standardMax), levels);
    }
}
