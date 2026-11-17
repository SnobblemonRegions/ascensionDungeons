package com.ascensiondungeons.outbreak;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a Pokémon capsule looks: the colours, stars and words of its name and tooltip, its tank's model number, and the tint of
 * the Pokémon once it is clear. Pure: the item layer turns these into components.
 *
 * <p>A capsule carries two different kinds of rare, and they never share a look. The <b>find tier</b> (what kind of Pokémon) is
 * shown with stars and its own name colours, chosen to stay clear of AscensionLib's rarity hues. The <b>Ascension rarity</b> (how
 * good this one is) only ever appears in AscensionLib's colours, under its own label.
 */
public final class CapsuleLook {
    private CapsuleLook() {}

    /** AscensionLib's rarity ids, lowest first, with the names players see and their colours (AscensionLib design/currency-items.json). */
    public static final List<String> RARITIES = List.of("common", "uncommon", "rare", "epic", "legendary", "mythical");
    private static final String[] RARITY_NAMES = {"Common", "Uncommon", "Rare", "Epic", "Radiant", "Ascended"};
    private static final int[] RARITY_COLOURS = {0xC1C5CF, 0x1BD7E8, 0x38CCEF, 0xAF69FF, 0xFFD437, 0xFF3448};

    /** The colour of an empty diamond in the rarity meter. */
    public static final int METER_EMPTY = 0x3A3A3A;

    /** The custom model number of the sealed tank for each tier, which a resource pack maps to the tank's art. */
    public static final int MODEL_BASE = 9201;

    public static int modelId(PokemonFinds.Tier tier) { return MODEL_BASE + tier.ordinal(); }

    public static String stars(PokemonFinds.Tier tier) { return "★".repeat(tier.ordinal() + 1); }

    /** Legendary and Mythical names are bold and coloured letter by letter. */
    public static boolean bold(PokemonFinds.Tier tier) { return tier.ordinal() >= PokemonFinds.Tier.LEGENDARY.ordinal(); }

    /** The colour stops of a tier: one for a flat colour, several for a gradient. */
    public static int[] stops(PokemonFinds.Tier tier) {
        return switch (tier) {
            case STANDARD -> new int[] {0xFFFFFF};
            case STARTER -> new int[] {0x7DE07D};
            case PSEUDO -> new int[] {0xFFA552};
            case LEGENDARY -> new int[] {0xF2D488, 0xFFFBEA, 0xF2D488};   // pale champagne, never Radiant's saturated gold
            case MYTHICAL -> new int[] {0xFF9BD2, 0xB69CFF, 0x8FE3FF};
        };
    }

    /** One colour per character of {@code text}, along the tier's stops: a flat colour repeats, a gradient runs end to end. */
    public static int[] colours(PokemonFinds.Tier tier, String text) {
        int[] s = stops(tier);
        int n = text.codePointCount(0, text.length());
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            if (s.length == 1) { out[i] = s[0]; continue; }
            double t = n == 1 ? 0 : (double) i / (n - 1), seg = t * (s.length - 1);
            int k = Math.min(s.length - 2, (int) Math.floor(seg));
            out[i] = mix(s[k], s[k + 1], seg - k);
        }
        return out;
    }

    /** The tier's main colour: the first stop. */
    public static int colour(PokemonFinds.Tier tier) { return stops(tier)[0]; }

    /**
     * The tint of the Pokémon model once a capsule is clear, as RGBA 0..1: mostly its own colours, washed lightly with the tier's,
     * as if still wet from the tank.
     */
    public static float[] tint(PokemonFinds.Tier tier) {
        int c = colour(tier);
        float wash = 0.18f;
        return new float[] {1 - wash + wash * ((c >> 16) & 255) / 255f, 1 - wash + wash * ((c >> 8) & 255) / 255f, 1 - wash + wash * (c & 255) / 255f, 1f};
    }

    // ---- the Ascension rarity --------------------------------------------------------------------------------------

    /** The index of an AscensionLib rarity id, 0 for common; unknown ids count as common. */
    public static int rarityIndex(String id) {
        int i = id == null ? -1 : RARITIES.indexOf(id.trim().toLowerCase(Locale.ROOT));
        return Math.max(0, i);
    }

    public static String rarityName(String id) { return RARITY_NAMES[rarityIndex(id)]; }

    public static int rarityColour(String id) { return RARITY_COLOURS[rarityIndex(id)]; }

    /** The meter for a floor: six diamonds, filled up to the floor in each rarity's own colour, the rest empty. */
    public static List<Segment> meter(String floor) {
        int f = rarityIndex(floor);
        List<Segment> out = new ArrayList<>();
        for (int i = 0; i < RARITIES.size(); i++) out.add(i <= f ? new Segment("◆", RARITY_COLOURS[i]) : new Segment("◇", METER_EMPTY));
        return out;
    }

    /** "Rare or better". */
    public static String floorText(String floor) { return rarityName(floor) + " or better"; }

    /** A piece of text in one colour. */
    public record Segment(String text, int colour) {}

    // ---- words ---------------------------------------------------------------------------------------------------------

    /** "Pseudo-legendary · Pokémon Capsule" follows the stars: the same shape as AscensionLib's first tooltip line. */
    public static final String KIND = "Pokémon Capsule";

    /** Where it was found, as up to three lines. A missing band (found outside the Depths) is left out. */
    public static List<String> found(String band, String dungeon, String difficulty, int stage) {
        List<String> out = new ArrayList<>();
        if (band != null && !band.isBlank()) out.add(band);
        if (dungeon != null && !dungeon.isBlank()) out.add(dungeon);
        out.add((difficulty == null || difficulty.isBlank() ? "" : difficulty + " · ") + "Outbreak stage " + Math.max(0, stage));
        return out;
    }

    /** What the player can do with it now. */
    public static List<String> status(boolean safe, String name) {
        return safe ? List.of("▶ Right-click to release " + name + ".")
                : List.of("⚠ At risk: bank it or carry it out.", "A knock-out or the toll can take it.");
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    /** "Bound to Steve · found 17 Nov". */
    public static String bound(String owner, long epochDay) {
        String date = epochDay > 0 ? " · found " + LocalDate.ofEpochDay(epochDay).format(DAY) : "";
        return "Bound to " + owner + date;
    }

    /** "mrmime" -> "Mrmime": only when Cobblemon could not name the species. */
    public static String fallbackName(String species) {
        return species == null || species.isEmpty() ? "?" : Character.toUpperCase(species.charAt(0)) + species.substring(1);
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
