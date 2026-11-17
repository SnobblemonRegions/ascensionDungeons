package com.ascensiondungeons.outbreak;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player is told when a dive ends: how it ended, how far the Outbreak got, what they kept and lost, and one thing to
 * do differently, chosen from what actually happened. Pure: no Minecraft.
 */
public final class Recap {
    private Recap() {}

    /** How the dive ended for this player. */
    public enum Way {
        /** Their free first extraction. */
        FREE,
        TOLL,
        GATEKEEPER,
        /** Left through the portal with nothing unbanked. */
        EMPTY_HANDED,
        KNOCKED_OUT,
        TIMED_OUT,
        /** Walked out or disconnected. */
        LEFT,
        /** The run ended around them with the dungeon's boss beaten. */
        CLEARED
    }

    /**
     * @param way           how it ended
     * @param stage         the Outbreak stage when it did
     * @param stageName     that stage's name
     * @param seconds       how long the player was inside
     * @param chests        chests they opened
     * @param reachedDepths whether they went down to the Depths
     * @param itemsKept     looted items they left with
     * @param itemsLost     looted items taken from them (by the toll, or by losing what was unbanked)
     * @param winsKept      wins they are paid for
     * @param winsLost      wins they lost
     * @param everBanked    whether a checkpoint banked anything for them
     * @param knockedOutBy  what landed the last hit, or null
     * @param bySpell       whether that was a spell
     * @param tollPercent   the toll, for the tips that name it
     * @param softened      a knock-out that was the player's first, so they kept part of what was unbanked
     * @param deepest       the name of the deepest band of the Depths they reached, or null
     * @param deepestBand   that band (0 outer, 2 deep), or -1
     * @param conquered     Outbreak Nests cleared and Alphas slain with them there
     */
    public record Facts(Way way, int stage, String stageName, long seconds, int chests, boolean reachedDepths, int itemsKept, int itemsLost, int winsKept, int winsLost,
                        boolean everBanked, String knockedOutBy, boolean bySpell, int tollPercent, boolean softened, String deepest, int deepestBand, int conquered,
                        List<String> found) {
        /** Without the Pokémon found. */
        public Facts(Way way, int stage, String stageName, long seconds, int chests, boolean reachedDepths, int itemsKept, int itemsLost, int winsKept, int winsLost,
                     boolean everBanked, String knockedOutBy, boolean bySpell, int tollPercent, boolean softened, String deepest, int deepestBand, int conquered) {
            this(way, stage, stageName, seconds, chests, reachedDepths, itemsKept, itemsLost, winsKept, winsLost, everBanked, knockedOutBy, bySpell, tollPercent, softened,
                    deepest, deepestBand, conquered, List.of());
        }
        /** Without the Depths' bands and set pieces, for callers that do not track them. */
        public Facts(Way way, int stage, String stageName, long seconds, int chests, boolean reachedDepths, int itemsKept, int itemsLost, int winsKept, int winsLost,
                     boolean everBanked, String knockedOutBy, boolean bySpell, int tollPercent, boolean softened) {
            this(way, stage, stageName, seconds, chests, reachedDepths, itemsKept, itemsLost, winsKept, winsLost, everBanked, knockedOutBy, bySpell, tollPercent, softened, null, -1, 0, List.of());
        }
    }

    /** The recap, line by line: a heading, how it ended, the Outbreak, the tally, and a tip. */
    public static List<String> lines(String dungeon, Facts f) {
        List<String> out = new ArrayList<>();
        out.add("Dive recap: " + dungeon);
        out.add(ended(f));
        out.add("Outbreak: stage " + f.stage() + " (" + f.stageName() + ") after " + clock(f.seconds()) + ". Chests opened: " + f.chests()
                + (!f.reachedDepths() ? ". You did not go down to the Depths" : f.deepest() != null ? ". Deepest: " + f.deepest() : "")
                + (f.conquered() > 0 ? ". Nests and Alphas beaten: " + f.conquered() : "") + ".");
        out.add("Kept: " + count(f.itemsKept(), "item") + ", " + count(f.winsKept(), "win") + ". Lost: " + count(f.itemsLost(), "item") + ", " + count(f.winsLost(), "win") + ".");
        if (f.found() != null && !f.found().isEmpty()) out.add("Pokémon found: " + String.join(", ", f.found()) + ".");
        out.add("Tip: " + tip(f));
        return out;
    }

    static String ended(Facts f) {
        return switch (f.way()) {
            case FREE -> "You extracted free: your first extraction cost nothing.";
            case TOLL -> "You extracted by paying the toll.";
            case GATEKEEPER -> "You extracted past the gatekeeper and kept everything.";
            case EMPTY_HANDED -> "You left through the portal with nothing unbanked.";
            case KNOCKED_OUT -> "You were knocked out" + (f.knockedOutBy() == null ? "." : " by " + f.knockedOutBy() + ".")
                    + (f.softened() ? " It was your first, so you kept half of what was unbanked." : "");
            case TIMED_OUT -> "Time ran out while you were still inside.";
            case LEFT -> "You left without extracting.";
            case CLEARED -> "The dungeon's boss fell and you left with everything.";
        };
    }

    /** One thing to do differently next time, picked from what happened. The first rule that fits wins. */
    public static String tip(Facts f) {
        switch (f.way()) {
            case KNOCKED_OUT -> {
                if (f.softened()) return (f.bySpell() ? "That was a spell: step out of the ring on the ground. " : "")
                        + "Next time a knock-out takes everything unbanked. Reach a checkpoint or the portal before you push on.";
                if (f.bySpell()) return "That was a spell. The ring on the ground shows where it lands: step out of it, or knock out the caster first.";
                if (f.itemsLost() > 0 && !f.everBanked()) return "Nothing was banked, so the knock-out took it all. A checkpoint or the portal makes loot yours: get to one before you push on.";
                if (f.stage() >= 3) return "You were still inside at stage " + f.stage() + ". The Outbreak only grows: leaving a stage earlier keeps what you carry.";
                if (f.itemsLost() == 0) return "You lost nothing you had not banked. Heal up and go again.";
                return "When your health drops, back off and heal, or head for the portal: a knock-out costs everything unbanked.";
            }
            case TIMED_OUT -> { return "The dive has a time limit, and the tutorial warns when it is close. Be at the portal before it runs out."; }
            case LEFT -> { return "Walking out loses everything unbanked. Leave through the extraction portal instead."; }
            case TOLL -> {
                if (f.stage() <= 1) return "The gatekeeper is at its weakest this early. Beat it instead of paying and you keep everything.";
                return "The toll took " + f.tollPercent() + "% of what was unbanked. Bank at checkpoints on the way and the toll has less to take.";
            }
            case FREE -> { return "From now on leaving costs " + f.tollPercent() + "% of your unbanked loot, unless you beat the gatekeeper or bank at checkpoints first."; }
            case EMPTY_HANDED -> {
                if (f.chests() == 0) return "You opened no chests. The Guide bar and the labels over chests show where the loot is.";
                return "Everything you had was already banked, so leaving cost nothing. That is the safest way to dive.";
            }
            case GATEKEEPER, CLEARED -> {
                if (!f.reachedDepths()) return "Most of a dungeon's chests are down in the Depths. Take the tunnel from the entrance room next time.";
                if (f.deepestBand() >= 0 && f.deepestBand() < 2) return "You never reached the deepest band, where the best chests and the Alphas' hoards are. Push further next time.";
                if (f.conquered() == 0 && f.deepestBand() >= 2) return "Outbreak Nests and Alphas guard sealed chests of Rare and Epic loot. Clear one next time.";
                if (f.stage() >= 3) return "You got out with everything at stage " + f.stage() + ". A higher-tier dungeon pays more for the same risk.";
                return "You kept everything. Staying a stage longer next time grows the haul, and the gatekeeper with it.";
            }
        }
        return "";
    }

    private static String count(int n, String what) { return n + " " + what + (n == 1 ? "" : "s"); }

    static String clock(long seconds) { return seconds / 60 + "m " + String.format("%02d", seconds % 60) + "s"; }
}
