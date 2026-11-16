package com.ascensiondungeons.outbreak;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The first-dive tutorial: what a new player is told, when, and what the Guide bar asks of them next. Pure: no Minecraft.
 *
 * <p>A {@link Lesson} is told once per player, the first time the thing it explains happens to them, so nobody reads a wall
 * of text at the door. A {@link Coach} paces one player's lessons through one dive and keeps their {@link Goal}: a short
 * list of things to do that walks them through the whole loop once. A player graduates when they first leave through the
 * portal; after that the Guide bar is gone, and they are told only about things they have still never met.
 */
public final class Onboarding {
    private Onboarding() {}

    /** How a lesson is shown: in chat only, or with a title across the screen as well. */
    public enum Weight { TIP, HEADLINE }

    /**
     * One thing a player is taught, with the words. {@code {toll}} in a line is replaced by the toll's percentage.
     * The order here is the order of the guide book. What a player has heard is saved by position, so a new lesson goes at
     * the end and none is ever removed or moved.
     */
    public enum Lesson {
        WELCOME(Weight.HEADLINE, false, "Welcome to your first dive",
                "Everything you loot in here is at risk until you leave through the extraction portal or reach a checkpoint.",
                "The pink Guide bar shows what to do next and which way to go. /dungeon guide opens these notes at any time."),
        OUTBREAK(Weight.TIP, false, "The Outbreak",
                "The bar at the top is the Outbreak. It rises the longer you stay and never falls.",
                "Every stage brings more and stronger enemies, and every stage you survive adds a win to your haul."),
        ENEMIES(Weight.TIP, false, "Enemies",
                "These Pokémon are hostile and fight in real time. Send out your own Pokémon and keep moving.",
                "They cannot be caught. A glowing one is an elite: tougher, and it casts spells."),
        SPELLS(Weight.TIP, true, "Spells",
                "A coloured ring on the ground is a spell about to land. Get out of the ring before it does.",
                "Knocking out the caster cancels it."),
        CHESTS(Weight.TIP, false, "Loot chests",
                "The label over a chest shows its rarity: Common, Uncommon, Rare or Epic.",
                "Everyone in the party gets their own loot from every chest, so there is nothing to fight over."),
        UNBANKED(Weight.TIP, false, "Your loot is at risk",
                "What you just took is not yours yet. A knock-out, running out of time or walking out loses it.",
                "To make it yours, reach a checkpoint or leave through the extraction portal."),
        CHECKPOINT(Weight.TIP, false, "Banked",
                "A checkpoint banked your haul: nothing can take what you had up to now.",
                "Whatever you loot after this is at risk again."),
        STAGE(Weight.TIP, false, "The Outbreak grew",
                "A new stage: more enemies, more elites. You earned a win for surviving the last one.",
                "The longer you stay, the more you carry and the harder it is to get out. Choose your moment."),
        TUNNEL(Weight.TIP, false, "The way down",
                "This tunnel leads down to the Depths, a maze that is different on every dive. Most of the chests are there.",
                "The stairway is the only way back up."),
        DEPTHS(Weight.TIP, false, "The Depths",
                "You are in the Depths. Look for chests, altars and strongboxes.",
                "Remember the way back: the extraction portal is up in the entrance room."),
        TIMER_CHEST(Weight.TIP, false, "Timer chest",
                "This chest is sealed until the Outbreak reaches a later stage.",
                "Come back for it, if you dare stay that long."),
        OUTBREAK_CHEST(Weight.TIP, false, "Outbreak chest",
                "Its rarity grows with the Outbreak until someone opens it.",
                "Waiting pays, if you last."),
        STRONGBOX(Weight.TIP, false, "Strongbox",
                "High-rarity loot, but opening it calls guardians to you.",
                "Be ready to fight before you open it."),
        ALTAR(Weight.TIP, false, "Curse altar",
                "Touch it to push the Outbreak ahead in exchange for a win for the whole party.",
                "A gamble: take it only if you are sure you can still get out."),
        LOW_HEALTH(Weight.TIP, true, "Careful",
                "You are badly hurt. Nobody dies in a dive, but a knock-out sends you home without your unbanked loot.",
                "Back off and heal, or head for the portal."),
        TIME_SHORT(Weight.HEADLINE, true, "Time is running out",
                "The dive ends soon. Anything you have not banked is lost if you are still inside when it does.",
                "Head for the extraction portal now."),
        PORTAL(Weight.TIP, false, "The extraction portal",
                "This is the way out. Step on it to see your choices; you can always step back off.",
                "Pay the toll and lose {toll}% of your unbanked loot (you are shown exactly which items first), or challenge the gatekeeper: beat it and keep everything.",
                "The gatekeeper grows with the Outbreak, so the time to leave is before it outgrows you."),
        GATEKEEPER(Weight.TIP, true, "The gatekeeper",
                "Beat it and the portal opens for the whole party for a short time: step through and keep everything.",
                "It casts spells too. Watch for the rings."),
        KNOCKED_OUT(Weight.HEADLINE, true, "Knocked out",
                "You lost the loot you had not banked. What you banked, and everything you brought with you, is safe.",
                "Next time, bank at checkpoints and leave through the portal before the Outbreak outgrows you."),
        TOLL_PAID(Weight.HEADLINE, true, "Extracted",
                "You paid the toll and kept the rest. Beat the gatekeeper next time and you keep it all.",
                "That is the whole loop: loot, go deeper, bank, get out. /dungeon guide keeps these notes."),
        EXTRACTED(Weight.HEADLINE, true, "Extracted",
                "You left with everything you carried.",
                "That is the whole loop: loot, go deeper, bank, get out. /dungeon guide keeps these notes."),
        FIRST_EXTRACTION(Weight.HEADLINE, true, "Extracted",
                "Your first extraction was free: this once, the toll took nothing.",
                "From now on, leaving costs {toll}% of your unbanked loot unless you beat the gatekeeper. Leave before it outgrows you.",
                "That is the whole loop: loot, go deeper, bank, get out. /dungeon guide keeps these notes.");

        /** Added to the portal's lesson for a player whose first extraction is still to come. */
        public static final String FIRST_IS_FREE = "Your first extraction is free: this once, the toll takes nothing.";

        private final Weight weight;
        private final boolean urgent;
        private final String title;
        private final List<String> lines;

        Lesson(Weight weight, boolean urgent, String title, String... lines) {
            this.weight = weight;
            this.urgent = urgent;
            this.title = title;
            this.lines = List.of(lines);
        }

        public Weight weight() { return weight; }
        /** An urgent lesson is about something happening right now, so it does not wait its turn. */
        public boolean urgent() { return urgent; }
        public String title() { return title; }

        /** The short line under a headline's title on the screen; empty for a tip. */
        public String subtitle() {
            return switch (this) {
                case WELCOME -> "Loot, go deeper, get out";
                case TIME_SHORT -> "Head for the portal";
                case KNOCKED_OUT -> "Your unbanked loot is lost";
                case TOLL_PAID -> "You paid the toll and kept the rest";
                case EXTRACTED -> "You kept everything";
                case FIRST_EXTRACTION -> "Your first extraction is free";
                default -> "";
            };
        }

        public List<String> lines(int tollPercent) { return lines(tollPercent, false); }

        /** @param firstFree the player's first extraction is free and still to come: the portal's lesson says so */
        public List<String> lines(int tollPercent, boolean firstFree) {
            List<String> out = new ArrayList<>(lines.size() + 1);
            for (String line : lines) out.add(line.replace("{toll}", Integer.toString(tollPercent)));
            if (firstFree && this == PORTAL) out.add(FIRST_IS_FREE);
            return out;
        }

        long bit() { return 1L << ordinal(); }
    }

    /** What the Guide bar asks a new player to do next. Four steps that walk the whole loop once. */
    public enum Goal {
        LOOT("Open a loot chest"),
        DESCEND("Take the tunnel down to the Depths"),
        DEEP_LOOT("Loot a chest in the Depths"),
        EXTRACT("Get back to the extraction portal and leave");

        private final String text;
        Goal(String text) { this.text = text; }
        public String text() { return text; }
    }

    /** How a player left through the portal. */
    public enum Exit {
        /** Their first extraction, which costs nothing. */
        FREE,
        /** They paid the toll. */
        TOLL,
        /** The gatekeeper was beaten and the portal stood open. */
        GATEKEEPER;

        /** The lesson that explains leaving this way. */
        public Lesson lesson() { return this == FREE ? Lesson.FIRST_EXTRACTION : this == TOLL ? Lesson.TOLL_PAID : Lesson.EXTRACTED; }
    }

    /** What is remembered about one player between dives. */
    public static final class Progress {
        private long seen;
        private int dives, extractions, knockOuts;
        private boolean off;
        /** The player has left through the portal at some time. Starting the tutorial over does not forget this. */
        private boolean everExtracted;

        public Progress() {}

        /** For progress saved before the free first extraction existed: anyone who had extracted has had theirs. */
        public Progress(long seen, int dives, int extractions, int knockOuts, boolean off) { this(seen, dives, extractions, knockOuts, off, extractions > 0); }

        public Progress(long seen, int dives, int extractions, int knockOuts, boolean off, boolean everExtracted) {
            this.seen = seen;
            this.dives = dives;
            this.extractions = extractions;
            this.knockOuts = knockOuts;
            this.off = off;
            this.everExtracted = everExtracted || extractions > 0;
        }

        public boolean everExtracted() { return everExtracted; }
        /**
         * A player's first extraction is free. It is theirs until they leave through the portal by any means, whether or not
         * they want coaching, and starting the tutorial over does not give it back.
         */
        public boolean firstExtractionFree() { return !everExtracted; }
        /** Operator aid: this player has had their free extraction. */
        public void forfeitFirstExtraction() { everExtracted = true; }

        public long seenBits() { return seen; }
        public int dives() { return dives; }
        public int extractions() { return extractions; }
        public int knockOuts() { return knockOuts; }
        /** The player asked not to be coached. */
        public boolean off() { return off; }
        public boolean seen(Lesson lesson) { return (seen & lesson.bit()) != 0; }
        /** A player graduates the first time they leave through the portal. */
        public boolean graduated() { return extractions > 0; }
        /** Still being walked through their first loop: gets the Guide bar and its goals. */
        public boolean rookie() { return !off && !graduated(); }
        /** Gets no opening briefing because the coach gives it lesson by lesson instead. */
        public boolean coached() { return !off && !seen(Lesson.WELCOME); }

        public void setOff(boolean off) { this.off = off; }
        /** Start again from nothing, as a player who has never dived. */
        public void reset() { seen = 0; dives = 0; extractions = 0; knockOuts = 0; off = false; }
        void markSeen(Lesson lesson) { seen |= lesson.bit(); }
        void startedDive() { dives++; }
        void extracted() { extractions++; everExtracted = true; }
        void knockedOut() { knockOuts++; }
    }

    /** Pacing, in ticks. */
    public record Pace(int gap, int urgentGap, int expiry) {
        /** Eight seconds between lessons, two before an urgent one, and a lesson about a place is dropped after thirty unsaid. */
        public static final Pace DEFAULT = new Pace(160, 40, 600);
    }

    /** One player's coach for one dive. */
    public static final class Coach {
        private final Progress progress;
        private final Pace pace;
        private final boolean hasDepths;
        private final Map<Lesson, Long> waiting = new EnumMap<>(Lesson.class);
        private final List<Lesson> told = new ArrayList<>();
        private long lastAt = Long.MIN_VALUE / 2;
        private Goal goal;
        private boolean changed;

        /** @param hasDepths whether this dive has Depths a player walks down to (if not, the goals skip them) */
        public Coach(Progress progress, Pace pace, boolean hasDepths) {
            this.progress = progress;
            this.pace = pace;
            this.hasDepths = hasDepths;
            this.goal = progress.rookie() ? Goal.LOOT : null;
            progress.startedDive();
            changed = true;
        }

        public Progress progress() { return progress; }

        /** Something happened that a lesson explains. It is queued unless this player has heard it or wants no coaching. */
        public void notice(Lesson lesson, long now) {
            if (progress.off() || progress.seen(lesson)) return;
            waiting.putIfAbsent(lesson, now);
        }

        /** The lesson to tell now, or null. It is marked as heard. Urgent lessons go first and wait less. */
        public Lesson next(long now) {
            if (progress.off()) { waiting.clear(); return null; }
            // A lesson about a place (a chest, an altar) is pointless once the player has walked on: drop it, to be told another time.
            waiting.entrySet().removeIf(e -> !keeps(e.getKey()) && now - e.getValue() > pace.expiry());
            Lesson pick = null;
            for (Lesson l : waiting.keySet()) if (pick == null || rank(l) < rank(pick)) pick = l;
            if (pick == null || now - lastAt < (pick.urgent() ? pace.urgentGap() : pace.gap())) return null;
            return tell(pick, now);
        }

        /** Tells a lesson at once, whatever is waiting: for the moment a player leaves, when there is no later. */
        public Lesson tellNow(Lesson lesson, long now) {
            if (progress.off() || progress.seen(lesson)) return null;
            return tell(lesson, now);
        }

        private Lesson tell(Lesson lesson, long now) {
            waiting.remove(lesson);
            progress.markSeen(lesson);
            told.add(lesson);
            lastAt = now;
            changed = true;
            return lesson;
        }

        /** Lessons that wait as long as it takes: the opening ones and those about the player's own state. */
        private static boolean keeps(Lesson l) {
            return l == Lesson.WELCOME || l == Lesson.OUTBREAK || l == Lesson.UNBANKED || l == Lesson.CHECKPOINT || l == Lesson.STAGE || l == Lesson.DEPTHS;
        }

        private static int rank(Lesson l) { return (l.urgent() ? 0 : 100) + l.ordinal(); }

        /** The lessons told in this dive, in order. */
        public List<Lesson> told() { return List.copyOf(told); }
        public boolean waiting(Lesson lesson) { return waiting.containsKey(lesson); }

        /** What the Guide bar asks for now, or null when this player has no Guide bar (graduated, or coaching off). */
        public Goal goal() { return progress.rookie() ? goal : null; }

        /** The player took loot from a chest. */
        public void lootedChest(boolean inDepths) {
            if (goal == null) return;
            Goal before = goal;
            if (inDepths || !hasDepths) goal = Goal.EXTRACT;
            else if (goal == Goal.LOOT) goal = Goal.DESCEND;
            changed |= goal != before;
        }

        /** The player walked into the Depths. */
        public void enteredDepths() {
            if (goal == Goal.DESCEND) { goal = Goal.DEEP_LOOT; changed = true; }
        }

        /** The player left through the portal: free the first time, by the toll, or past the gatekeeper. They graduate. */
        public void extracted() { progress.extracted(); goal = null; changed = true; }

        public void knockedOut() { progress.knockedOut(); changed = true; }

        /** True once after anything worth saving changed. */
        public boolean pollChanged() { boolean c = changed; changed = false; return c; }
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /**
     * An arrow pointing from a player to a target, relative to the way the player faces: up is straight ahead.
     *
     * @param dx  target x minus player x
     * @param dz  target z minus player z
     * @param yaw the player's yaw in Minecraft's sense: 0 faces +z, 90 faces -x
     */
    public static String arrow(double dx, double dz, float yaw) {
        double toTarget = Math.toDegrees(Math.atan2(-dx, dz));
        double turn = ((toTarget - yaw) % 360 + 540) % 360 - 180;          // -180..180, positive is to the right
        int sector = (int) Math.floor((turn + 22.5) / 45.0);
        return ARROWS[Math.floorMod(sector, 8)];
    }

    /** The Guide bar's words: the goal, then which way and how far, and whether it is a level up or down. */
    public static String guideLine(Goal goal, double dx, double dy, double dz, float yaw) {
        int far = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String level = dy <= -3 ? ", below you" : dy >= 3 ? ", above you" : "";
        if (far <= 2 && level.isEmpty()) return "Guide: " + goal.text() + " (right here)";
        return "Guide: " + goal.text() + "  " + arrow(dx, dz, yaw) + " " + far + "m" + level;
    }

    /** Breaks a line into pieces no longer than {@code width}, on spaces, for an item's description. */
    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) { out.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
