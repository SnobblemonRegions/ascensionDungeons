package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.gen.authored.AuthoredLayout;
import com.ascensiondungeons.gen.authored.Jx;
import com.ascensiondungeons.outbreak.Aggro;
import com.ascensiondungeons.outbreak.ChestRarity;
import com.ascensiondungeons.outbreak.DiveSettlement;
import com.ascensiondungeons.outbreak.ExtractionPortal;
import com.ascensiondungeons.outbreak.FeaturePlanner;
import com.ascensiondungeons.outbreak.Haul;
import com.ascensiondungeons.outbreak.Onboarding;
import com.ascensiondungeons.outbreak.OutbreakClock;
import com.ascensiondungeons.outbreak.Recap;
import com.ascensiondungeons.outbreak.Spell;
import com.ascensiondungeons.outbreak.Toll;
import com.ascensiondungeons.outbreak.WavePlanner;
import com.ascensiondungeons.run.Run;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * One run's Outbreak dive: the thin Minecraft layer over {@code com.ascensiondungeons.outbreak}. It shows the Outbreak rising,
 * sends waves, runs the chests, altars and the extraction portal, and pays each player once when they leave. Every rule it
 * applies (stage, wave size, who keeps what, who an enemy attacks, when a spell lands) is decided by the pure classes; this
 * class only draws, spawns and moves. Vanilla blocks, entities, boss bars, particles and sounds only: no client mod.
 */
public final class OutbreakDive {
    /** What the dive needs from its dungeon session. */
    interface Host {
        Run run();
        ServerLevel level();
        AuthoredLayout layout();
        BlockPos world(int[] cell);
        int[] schem(Entity e);
        Mob spawn(JsonObject def, int[] cell, double healthMul);
        void say(String text, ChatFormatting colour);
        List<ServerPlayer> players();
        Random random();
        void later(long delayTicks, Runnable action);
        /** The player leaves the run and is sent back to where they came from. */
        void leave(UUID player);
        /** Pays these players for this many arena wins at this tier (AscensionLib, when installed). */
        void pay(int tier, int arenaWins, boolean bossDefeated, Collection<UUID> players);
        /** The run level's profile-zone tier changed. */
        void zoneTier(int tier);
        /** Makes a real-time-combat creature hit harder. */
        void empower(Mob mob, double damageMultiplier);
        /** Marks a creature as a dungeon enemy (a Pokémon becomes uncatchable and cannot be battled turn-based). */
        void hostile(Mob mob);
        /** How much tougher creatures are for this party's size. */
        double healthFactor();
        /** True when this creature's targeting is run by the real-time Pokémon combat, which asks {@link #chooseTarget}. */
        boolean combatManaged(Mob mob);
    }

    static final String[] STAGE_NAMES = {"Calm", "Restless", "Agitated", "Swarming", "Rampage", "Mass Outbreak"};
    /** Test aid: {@code -Dascensiondungeons.outbreakSpeed=20} runs every Outbreak timer 20 times faster. */
    static final double SPEED = Math.max(0.01, Double.parseDouble(System.getProperty("ascensiondungeons.outbreakSpeed", "1")));
    static final String LABEL_TAG = "ascension_outbreak_label";
    /** Enemies farther than this from every player are removed. */
    static final double LET_GO = 56;

    private static final class Feature {
        final FeaturePlanner.Kind kind;
        final ChestRarity rarity;
        final BlockPos pos;
        boolean used;
        ChestRarity shown;
        String shownText;
        Display.TextDisplay label;
        Display.ItemDisplay model;
        int shownModel;
        /** Down in the Depths, as opposed to in the hand-built dungeon. */
        boolean deep;
        Feature(FeaturePlanner.Placement p, BlockPos pos) { this.kind = p.kind(); this.rarity = p.rarity(); this.pos = pos; this.shown = p.rarity(); }
    }

    private static final class Enemy {
        final Mob mob;
        final Aggro.Style style;
        final Vec3 anchor;
        final boolean elite;
        final Spell spell;
        final Spell.Cast cast = new Spell.Cast();
        boolean alerted;
        Vec3 castCentre;
        Enemy(Mob mob, Aggro.Style style, Vec3 anchor, boolean elite, Spell spell) { this.mob = mob; this.style = style; this.anchor = anchor; this.elite = elite; this.spell = spell; }
    }

    private final Host host;
    private final JsonObject cfg;
    private final OutbreakClock.Params params;
    private final WavePlanner waves;
    private final ExtractionPortal portal;
    private final Haul haul = new Haul();
    private final int baseTier;
    private final long waveEvery;
    private final double portalRadius;
    private final int tollPercent;
    /** Players standing on the portal who have already been shown the extraction screen; cleared when they step off. */
    private final Set<UUID> atPortal = new HashSet<>();
    private Mob gatekeeper;
    private final Map<String, Spell> spells = new HashMap<>();
    /** Ring colour per spell id (0xRRGGBB), from the dungeon's type. */
    private final Map<String, Integer> spellColours = new HashMap<>();
    private final Set<String> checkpoints = new HashSet<>();
    private final Map<BlockPos, Feature> features = new LinkedHashMap<>();
    private final Map<UUID, Enemy> enemies = new LinkedHashMap<>();
    private final Set<UUID> settled = new HashSet<>();
    /** Creatures that take only a share of each hit: 4.0 means a quarter. This is how a Pokémon enemy is made tough (see {@link #scale}). */
    private final Map<UUID, Double> toughness = new HashMap<>();
    private final ServerBossEvent outbreakBar;
    /** The portal's bar, one per player: it also points each of them the way out from where they stand. */
    private final Map<UUID, ServerBossEvent> portalBars = new HashMap<>();
    private final Map<UUID, String> portalTexts = new HashMap<>();
    // ---- what each player did, for the recap when they leave ----
    private long startedAt;
    /** Players who went down to the Depths, and players a checkpoint banked for. */
    private final Set<UUID> everBelow = new HashSet<>(), everBanked = new HashSet<>();
    /** Looted items a checkpoint made theirs, and items and wins the toll took. */
    private final Map<UUID, Integer> bankedItems = new HashMap<>(), tollItems = new HashMap<>(), tollWins = new HashMap<>();
    /** The last thing that hit each player with a spell, and when: a knock-out a moment later was that spell. */
    private final Map<UUID, String> lastSpell = new HashMap<>();
    private final Map<UUID, Long> lastSpellAt = new HashMap<>();
    /** What knocked each player out, and whether it was a spell. */
    private final Map<UUID, String> knockedOutBy = new HashMap<>();
    private final Set<UUID> knockedOutBySpell = new HashSet<>();
    /** True while the run is ending around the players still inside. */
    private boolean closing;
    private Vec3 portalCentre;
    /** The teleporting stairway to detached Depths and the spot it leads to (both null when the Depths are joined by a tunnel, or absent). */
    private Vec3 descentCentre, returnCentre;
    /** Where the tunnel to the Depths leaves the entrance room, and the maze's doorway at the bottom (both null unless joined by a tunnel). */
    private Vec3 tunnelMouth, depthsDoor;
    /** Players who are down in the Depths right now. */
    private final Set<UUID> below = new HashSet<>();
    /** The first-dive tutorial for the players of this dive. */
    private final DiveCoach coach;
    /** Players who have had their opening words, whichever kind. */
    private final Set<UUID> greeted = new HashSet<>();
    /** Players who left by paying the toll (as opposed to past the gatekeeper). */
    private final Set<UUID> tolled = new HashSet<>();
    /** Players who left on their free first extraction. */
    private final Set<UUID> graced = new HashSet<>();
    /** Players who left with nothing unbanked. That is not an extraction the tutorial counts: it neither graduates nor uses the free one. */
    private final Set<UUID> emptyHanded = new HashSet<>();
    /** Whether a player's first extraction is free here ({@code portal.firstExtractionFree}, on unless set false). */
    private final boolean firstFree;
    /** Whether a player's first knock-out here keeps half of what was unbanked ({@code knockOut.firstKeepsHalf}, on unless set false). */
    private final boolean firstSofter;
    /** The share of unbanked loot and wins a softened knock-out leaves the player, in percent. */
    static final int SOFT_KEEP = 50;
    /** Players whose knock-out was softened, and players who had something unbanked when they were knocked out. */
    private final Set<UUID> softened = new HashSet<>(), knockedOutAtStake = new HashSet<>();
    /** The chests each player has taken loot from, so the Guide points at one they have not. */
    private final Map<UUID, Set<BlockPos>> looted = new HashMap<>();
    /** Players who just used the stairway and have not stepped off it yet, so they are not sent straight back. */
    private final Set<UUID> travelled = new HashSet<>();
    private OutbreakClock clock;
    private long now, nextWaveAt;
    private boolean bossDown, closed;
    private ExtractionPortal.State lastPortalState = ExtractionPortal.State.IDLE;

    /** The dive for this session, or null when its dungeon has no Outbreak settings. */
    static OutbreakDive create(Host host) {
        JsonObject cfg = host.layout().outbreak();
        if (cfg == null) return null;
        if (!lootrInstalled()) {
            // Haul-only loss depends on Lootr's per-player loot and its fill hook; without it the dungeon runs as an ordinary one.
            AscensionDungeonsLog.LOG.warn("{} has Outbreak settings but Lootr is not installed: running without the Outbreak", host.layout().id());
            return null;
        }
        try {
            return new OutbreakDive(host, cfg);
        } catch (RuntimeException e) {
            // Bad settings must not take the dungeon down: it runs as an ordinary dungeon instead.
            AscensionDungeonsLog.LOG.error("Outbreak settings for {} are invalid; running without the Outbreak", host.layout().id(), e);
            return null;
        }
    }

    /** Outbreak dives need Lootr (owner decision 2026-11-15). The chunk generator asks too, so features and dive agree. */
    public static boolean lootrInstalled() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("lootr", "lootr_chest"));
    }

    private static int ticks(double seconds) { return Math.max(1, (int) Math.round(seconds * 20 / SPEED)); }

    private OutbreakDive(Host host, JsonObject cfg) {
        this.host = host;
        this.cfg = cfg;
        JsonObject c = Jx.o(cfg, "clock");
        this.params = new OutbreakClock.Params(ticks(Jx.d(c, "secondsPerStage", 180)), Jx.i(c, "maxStage", 5), Jx.d(c, "spawnStep", 0.25),
                Jx.d(c, "eliteStep", 0.06), Jx.d(c, "eliteCap", 0.3), Jx.i(c, "stagesPerTierStep", 2));
        JsonObject w = Jx.o(cfg, "waves");
        this.waves = new WavePlanner(Jx.i(w, "base", 2), Jx.i(w, "perStage", 1), Jx.i(w, "perExtraPlayer", 1), Jx.i(w, "maxAlive", 14));
        this.waveEvery = ticks(Jx.d(w, "everySeconds", 40));
        JsonObject p = Jx.o(cfg, "portal");
        this.portal = new ExtractionPortal(new ExtractionPortal.Params(ticks(Jx.d(p, "windowSeconds", 30)), Jx.i(p, "growthPercent", 25)));
        this.portalRadius = Jx.d(p, "radius", 2.5);
        this.tollPercent = Math.max(0, Math.min(100, Jx.i(p, "tollPercent", 35)));
        this.firstFree = Jx.b(p, "firstExtractionFree", true);
        this.firstSofter = Jx.b(Jx.o(cfg, "knockOut"), "firstKeepsHalf", true);
        this.baseTier = Math.max(1, Jx.i(host.layout().data().pokemon, "tier", 1));
        for (JsonElement e : Jx.a(cfg, "checkpoints")) checkpoints.add(e.getAsString());
        for (JsonElement e : Jx.a(cfg, "spells")) {
            JsonObject s = e.getAsJsonObject();
            Spell spell = new Spell(Jx.s(s, "id", null), Jx.s(s, "name", null), ticks(Jx.d(s, "cooldownSeconds", 12)), ticks(Jx.d(s, "warnSeconds", 2)),
                    Jx.d(s, "range", 10), Jx.d(s, "radius", 3), Jx.d(s, "damage", 4),
                    Spell.Effect.valueOf(Jx.s(s, "effect", "none").toUpperCase(java.util.Locale.ROOT)), Jx.d(s, "strength", 0));
            spells.put(spell.id(), spell);
            spellColours.put(spell.id(), Jx.i(s, "colour", Jx.i(cfg, "colour", 0x33D9FF)));
        }
        for (FeaturePlanner.Placement pl : host.layout().features(host.run().seed())) {
            BlockPos pos = host.world(pl.cell());
            if (pl.kind() == FeaturePlanner.Kind.PORTAL) portalCentre = Vec3.atBottomCenterOf(pos);
            if (pl.kind() == FeaturePlanner.Kind.DESCENT) descentCentre = Vec3.atBottomCenterOf(pos);
            if (pl.kind() == FeaturePlanner.Kind.RETURN) returnCentre = Vec3.atBottomCenterOf(pos);
            features.put(pos, new Feature(pl, pos));
        }
        com.ascensiondungeons.gen.depths.DepthsLink link = host.layout().link();
        if (link != null && link.physical()) {
            // The Depths are walked to: signposts at the tunnel's mouth and at the maze's doorway, nothing to step on.
            int[] mouth = link.mouth();
            int[] door = link.toSchem(com.ascensiondungeons.gen.depths.Depths.doorX(host.layout().depths(host.run().seed()).params()), com.ascensiondungeons.gen.depths.Depths.STAND, 1);
            BlockPos mouthPos = host.world(mouth), doorPos = host.world(door);
            tunnelMouth = Vec3.atBottomCenterOf(mouthPos);
            depthsDoor = Vec3.atBottomCenterOf(doorPos);
            features.put(mouthPos, new Feature(new FeaturePlanner.Placement(FeaturePlanner.Kind.DESCENT, null, "", mouth), mouthPos));
            features.put(doorPos, new Feature(new FeaturePlanner.Placement(FeaturePlanner.Kind.RETURN, null, "", door), doorPos));
        }
        if (portalCentre == null) throw new IllegalStateException("no room for an extraction portal");
        for (Feature f : features.values()) f.deep = AuthoredLayout.inDepths(roomOf(Vec3.atBottomCenterOf(f.pos)));
        coach = new DiveCoach(host.level(), tunnelMouth != null || descentCentre != null, tollPercent, firstFree, firstSofter, this::guideTarget);
        outbreakBar = new ServerBossEvent(Component.literal("Outbreak"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_6);
    }

    // ---- queries ---------------------------------------------------------------------------------------------

    public int stage() { return clock == null ? 0 : clock.stage(now); }
    public boolean settled(UUID player) { return settled.contains(player); }
    public Vec3 portalCentre() { return portalCentre; }
    /** The teleporting stairway to detached Depths, or null (the Depths are joined by a tunnel, or there are none). */
    public Vec3 descentCentre() { return descentCentre; }
    /** Where the teleporting stairway leads, in the Depths, or null. */
    public Vec3 returnCentre() { return returnCentre; }
    /** Where the tunnel down to the Depths leaves the entrance room, or null when they are not joined by one. */
    public Vec3 tunnelMouth() { return tunnelMouth; }
    /** The maze's doorway at the bottom of the tunnel, or null. */
    public Vec3 depthsDoor() { return depthsDoor; }
    /** Whether this player is down in the Depths. */
    public boolean inDepths(UUID player) { return below.contains(player); }
    private int tier() { return params.zoneTier(baseTier, stage()); }
    private UUID runId() { return host.run().id(); }

    private List<UUID> present() {
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer p : host.players()) if (!settled.contains(p.getUUID())) out.add(p.getUUID());
        return out;
    }

    public String describe() {
        return "outbreak stage=" + stage() + " tier=" + tier() + " portal=" + portal.state() + " gatekeepers=" + portal.gatekeepersDefeated() + " enemies=" + enemies.size()
                + " features=" + features.size() + " settled=" + settled.size();
    }

    /** Operator aid: one line per feature, "KIND rarity x y z", then "depths" for one down in the Depths and "used" for a spent one. */
    public List<String> featureLines() {
        List<String> out = new ArrayList<>();
        for (Feature f : features.values())
            out.add(f.kind + " " + (f.rarity == null ? "-" : f.rarity.name()) + " " + f.pos.getX() + " " + f.pos.getY() + " " + f.pos.getZ()
                    + (AuthoredLayout.inDepths(roomOf(Vec3.atBottomCenterOf(f.pos))) ? " depths" : "") + (f.used ? " used" : ""));
        return out;
    }

    /** Test aid: the lessons the tutorial told this player in this dive, in order. */
    public List<String> lessonsTold(UUID player) { return coach.told(player); }
    /** Test aid: the Guide bar's text for this player, or null when they have none. */
    public String guideText(UUID player) { return coach.guideText(player); }
    /** Test aid: the goal the Guide bar is on for this player, or null. */
    public String guideGoal(UUID player) { return coach.goal(player); }
    /** Test aid: the portal bar's text for this player (it points their own way out), or null when they have none. */
    public String portalText(UUID player) { return portalBars.containsKey(player) ? portalTexts.get(player) : null; }
    /** The toll, as a percentage of unbanked loot. */
    public int tollPercent() { return tollPercent; }
    /** Whether a knock-out now would be this player's softer first one. */
    public boolean firstKnockOutSoft(ServerPlayer player) { return coach.firstKnockOutSoft(player); }
    /** Whether the portal would let this player out for nothing right now (their first extraction). */
    public boolean firstExtractionFree(ServerPlayer player) { return coach.firstExtractionFree(player); }

    /** Operator aid: bank everyone's haul as a checkpoint would. */
    public void operatorCheckpoint() { if (!closed) checkpoint("Checkpoint (operator)"); }

    /** Test aid: the gatekeeper being fought, or null. */
    public Mob gatekeeperMob() { return gatekeeper != null && gatekeeper.isAlive() ? gatekeeper : null; }

    /** Test aid: the dive's living enemies. */
    public List<Mob> enemyMobs() {
        List<Mob> out = new ArrayList<>();
        for (Enemy en : enemies.values()) if (en.mob.isAlive()) out.add(en.mob);
        return out;
    }

    // ---- the clock -------------------------------------------------------------------------------------------

    void tick(long tickNow, List<ServerPlayer> players) {
        if (closed) return;
        now = tickNow;
        if (clock == null) {
            clock = new OutbreakClock(params, now);
            startedAt = now;
            nextWaveAt = now + waveEvery;
        }
        for (ServerPlayer p : players) if (greeted.add(p.getUUID())) greet(p);
        // Anyone who walked out or dropped off without extracting keeps only what they banked.
        for (Map.Entry<UUID, Run.Member> e : host.run().members().entrySet())
            if (e.getValue().presence() == Run.Presence.LEFT && !settled.contains(e.getKey())) settle(e.getKey(), Haul.Exit.LEFT);

        int risen = clock.pollNewStage(now);
        if (risen > 0) stageRose(risen);

        portal.tick(now);
        if (portal.state() != lastPortalState) {
            if (lastPortalState == ExtractionPortal.State.OPEN && portal.state() == ExtractionPortal.State.IDLE)
                host.say("The extraction portal has sealed again. Its next gatekeeper will be stronger.", ChatFormatting.RED);
            lastPortalState = portal.state();
        }
        gatekeeperTick(players);
        for (ServerPlayer p : players) {
            if (settled.contains(p.getUUID())) continue;
            if (p.position().distanceToSqr(portalCentre) > portalRadius * portalRadius) { atPortal.remove(p.getUUID()); continue; }
            if (portal.state() == ExtractionPortal.State.OPEN) { if (portal.escape(p.getUUID(), now)) extract(p); }
            else if (atPortal.add(p.getUUID())) offer(p);
        }

        stairwayTick(players);
        if (tunnelMouth != null && now % 10 == 0) depthsTick(players);
        if (now % 10 == 0) lessonsTick(players);
        coach.tick(now, unsettled(players));

        if (!bossDown && !players.isEmpty() && now >= nextWaveAt) {
            enemies.values().removeIf(en -> !en.mob.isAlive());
            int count = waves.count(stage(), players.size(), enemies.size());
            if (count > 0) spawnWave(count, players, Jx.a(Jx.o(cfg, "waves"), "spawns"), null);
            nextWaveAt = now + Math.max(20, (long) (waveEvery / params.spawnScale(stage())));
        }
        if (now % 5 == 0) enemiesTick(players);
        spellsTick(players);
        if (now % 10 == 0) barsTick(players);
        if (now % 20 == 0) featuresTick(players);
    }

    /** Standing on the stairway carries a player down to the Depths; standing on the arrival spot carries them back up. */
    private void stairwayTick(List<ServerPlayer> players) {
        if (descentCentre == null || returnCentre == null) return;
        for (ServerPlayer p : players) {
            UUID id = p.getUUID();
            if (settled.contains(id)) continue;
            boolean down = p.position().distanceToSqr(descentCentre) < 1.3 * 1.3, up = p.position().distanceToSqr(returnCentre) < 1.3 * 1.3;
            if (!down && !up) { travelled.remove(id); continue; }
            if (!travelled.add(id)) continue;
            Vec3 to = down ? returnCentre : descentCentre;
            host.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.6, 0.3, 0.05);
            p.teleportTo(host.level(), to.x, to.y, to.z, p.getYRot(), p.getXRot());
            sound(BlockPos.containing(to), SoundEvents.ENDERMAN_TELEPORT, 0.8f, down ? 0.7f : 1.3f);
            if (down) { everBelow.add(id); coach.notice(p, Onboarding.Lesson.DEPTHS, now); coach.enteredDepths(p); }
            p.displayClientMessage(Component.literal(down ? "You descend into the Depths." : "You climb back to the dungeon.").withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Joined Depths: notices who has walked down into the maze and who has come back up. */
    private void depthsTick(List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            UUID id = p.getUUID();
            if (settled.contains(id)) { below.remove(id); continue; }
            int[] c = host.schem(p);
            AuthoredLayout.Room room = host.layout().roomAt(host.run().seed(), c[0], c[1], c[2]);
            if (room == null) continue;                                   // in the tunnel, on the stairway or between rooms: no change
            boolean deep = AuthoredLayout.inDepths(room);
            if (deep == below.contains(id)) continue;
            if (deep) { below.add(id); everBelow.add(id); } else below.remove(id);
            if (deep) { coach.notice(p, Onboarding.Lesson.DEPTHS, now); coach.enteredDepths(p); }
            p.displayClientMessage(Component.literal(deep ? "You descend into the Depths." : "You climb back to the dungeon.").withStyle(ChatFormatting.AQUA), true);
        }
    }

    private List<ServerPlayer> unsettled(List<ServerPlayer> players) {
        List<ServerPlayer> out = new ArrayList<>(players.size());
        for (ServerPlayer p : players) if (!settled.contains(p.getUUID())) out.add(p);
        return out;
    }

    /** A player's opening words: the tutorial for someone new to dives, the short briefing for everyone else. */
    private void greet(ServerPlayer p) {
        if (coach.coached(p)) {
            coach.notice(p, Onboarding.Lesson.WELCOME, now);
            coach.notice(p, Onboarding.Lesson.OUTBREAK, now);
            return;
        }
        p.sendSystemMessage(Component.literal("An Outbreak is building here. The longer you stay, the fiercer it gets and the more you carry out.").withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.literal("Loot is yours only once it is banked at a checkpoint or carried out through the extraction portal near the entrance.").withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal("Leaving has a price: beat the portal's gatekeeper, which grows with the Outbreak, or pay a toll of " + tollPercent + "% of your unbanked loot.").withStyle(ChatFormatting.GRAY));
        if (tunnelMouth != null)
            p.sendSystemMessage(Component.literal("A tunnel leads out of this room and down to the Depths: a maze that is different on every dive, with most of the chests.").withStyle(ChatFormatting.GRAY));
    }

    /** Notices what each player is close to or going through, so the tutorial can explain it the first time. */
    private void lessonsTick(List<ServerPlayer> players) {
        long left = host.run().deadline() == Long.MAX_VALUE ? Long.MAX_VALUE : host.run().deadline() - host.level().getServer().getTickCount();
        for (ServerPlayer p : players) {
            if (settled.contains(p.getUUID())) continue;
            for (Enemy en : enemies.values())
                if (en.mob.isAlive() && en.mob.distanceToSqr(p) < 14 * 14) { coach.notice(p, Onboarding.Lesson.ENEMIES, now); break; }
            for (Feature f : features.values()) {
                if (Math.abs(f.pos.getY() - p.getY()) > 5 || p.blockPosition().distSqr(f.pos) > 9 * 9) continue;
                Onboarding.Lesson lesson = switch (f.kind) {
                    case CHEST -> Onboarding.Lesson.CHESTS;
                    case TIMER_CHEST -> Onboarding.Lesson.TIMER_CHEST;
                    case OUTBREAK_CHEST -> Onboarding.Lesson.OUTBREAK_CHEST;
                    case STRONGBOX -> Onboarding.Lesson.STRONGBOX;
                    case ALTAR -> Onboarding.Lesson.ALTAR;
                    case PORTAL -> Onboarding.Lesson.PORTAL;
                    case DESCENT -> Onboarding.Lesson.TUNNEL;
                    case RETURN -> null;
                };
                // The way out is explained when it matters: once the player is carrying something they could lose.
                if (lesson == Onboarding.Lesson.PORTAL && !looted.containsKey(p.getUUID())) continue;
                if (lesson != null) coach.notice(p, lesson, now);
            }
            if (p.getHealth() <= p.getMaxHealth() * 0.4f) coach.notice(p, Onboarding.Lesson.LOW_HEALTH, now);
            if (left <= 6000) coach.notice(p, Onboarding.Lesson.TIME_SHORT, now);
        }
    }

    /** Where the Guide bar points a new player for each goal. */
    private Vec3 guideTarget(ServerPlayer p, Onboarding.Goal goal) {
        boolean deep = below.contains(p.getUUID());
        // The way between the dungeon and the Depths, from whichever end the player is at.
        Vec3 down = tunnelMouth == null ? descentCentre : p.getY() < tunnelMouth.y - 2 || p.position().distanceToSqr(tunnelMouth) < 6 * 6 ? depthsDoor : tunnelMouth;
        Vec3 up = tunnelMouth == null ? returnCentre : p.getY() < tunnelMouth.y - 2 && p.position().distanceToSqr(depthsDoor) > 8 * 8 ? depthsDoor : tunnelMouth;
        switch (goal) {
            // A chest on the player's own level; if there is none left there, the way to the other level.
            case LOOT -> { Vec3 chest = nearestChest(p, deep, true); return chest != null ? chest : deep ? up : down; }
            case DESCEND -> { return down; }
            case DEEP_LOOT -> { return deep ? nearestChest(p, true, true) : down; }
            case EXTRACT -> { return wayOut(p); }
        }
        return null;
    }

    /**
     * Where to head to get out, from where this player stands: the portal when they are up in the dungeon; from the Depths, the
     * maze's doorway first, then the tunnel's mouth at the top of the stairway.
     */
    private Vec3 wayOut(ServerPlayer p) {
        if (tunnelMouth == null) return below.contains(p.getUUID()) && returnCentre != null ? returnCentre : portalCentre;
        boolean under = p.getY() < tunnelMouth.y - 2;
        if (!below.contains(p.getUUID()) && !under) return portalCentre;
        return under && p.position().distanceToSqr(depthsDoor) > 8 * 8 ? depthsDoor : tunnelMouth;
    }

    /** The nearest chest this player has not looted that can be opened now, upstairs or down in the Depths. */
    private Vec3 nearestChest(ServerPlayer p, boolean deep, boolean fresh) {
        Set<BlockPos> mine = looted.getOrDefault(p.getUUID(), Set.of());
        int timerStage = Jx.i(Jx.o(cfg, "features"), "timerStage", 2);
        Feature best = null;
        double bestD = Double.MAX_VALUE;
        for (Feature f : features.values()) {
            if (f.rarity == null || f.deep != deep || f.kind == FeaturePlanner.Kind.STRONGBOX) continue;
            if (f.kind == FeaturePlanner.Kind.TIMER_CHEST && stage() < timerStage) continue;
            if (fresh && mine.contains(f.pos)) continue;
            double d = p.blockPosition().distSqr(f.pos);
            if (d < bestD) { bestD = d; best = f; }
        }
        return best == null ? null : Vec3.atBottomCenterOf(best.pos);
    }

    private void stageRose(int stage) {
        String name = STAGE_NAMES[Math.min(stage, STAGE_NAMES.length - 1)];
        host.say("The Outbreak grows: stage " + stage + ", " + name + ". Surviving it adds to your haul.", ChatFormatting.GOLD);
        haul.addShared(present(), Haul.Kind.ARENA_WIN, 1);
        for (ServerPlayer p : unsettled(host.players())) coach.notice(p, Onboarding.Lesson.STAGE, now);
        host.zoneTier(tier());
        for (ServerPlayer p : host.players()) host.level().playSound(null, p.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.5f, 0.8f + 0.1f * stage);
        if (stage == Jx.i(Jx.o(cfg, "features"), "timerStage", 2))
            for (Feature f : features.values()) if (f.kind == FeaturePlanner.Kind.TIMER_CHEST) { host.say("Somewhere in the dungeon a timer chest has unsealed.", ChatFormatting.YELLOW); break; }
        AscensionDungeonsLog.LOG.info("[dive] run {} outbreak stage {} ({}), zone tier {}", runId(), stage, name, tier());
    }

    private void barsTick(List<ServerPlayer> players) {
        int stage = stage();
        String name = STAGE_NAMES[Math.min(stage, STAGE_NAMES.length - 1)];
        outbreakBar.setName(Component.literal("Outbreak: stage " + stage + ", " + name));
        outbreakBar.setColor(stage <= 1 ? BossEvent.BossBarColor.GREEN : stage == 2 ? BossEvent.BossBarColor.YELLOW : stage <= 4 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
        outbreakBar.setProgress((float) clock.progress(now));
        for (ServerPlayer p : players) outbreakBar.addPlayer(p);
        for (ServerPlayer p : new ArrayList<>(outbreakBar.getPlayers())) if (p.level() != host.level() || p.isRemoved()) outbreakBar.removePlayer(p);
        // The portal's bar is each player's own: the same news for everyone, and the way out from where that player stands.
        boolean open = portal.state() == ExtractionPortal.State.OPEN, fight = portal.state() == ExtractionPortal.State.FIGHT && gatekeeper != null;
        for (ServerPlayer p : players) {
            if (settled.contains(p.getUUID())) continue;
            Vec3 to = wayOut(p);
            String where = Onboarding.pointer(to.x - p.getX(), to.y - p.getY(), to.z - p.getZ(), p.getYRot());
            String text = open ? "Extraction portal OPEN " + where + ": " + (portal.remaining(now) + 19) / 20 + "s"
                    : fight ? gatekeeper.getName().getString() + " at the portal " + where
                    : "Extraction portal " + where + ": " + (coach.firstExtractionFree(p) ? "your first extraction is free" : "beat its gatekeeper, or pay the " + tollPercent + "% toll");
            ServerBossEvent bar = portalBars.get(p.getUUID());
            if (bar == null) {
                bar = new ServerBossEvent(Component.literal(text), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
                bar.addPlayer(p);
                portalBars.put(p.getUUID(), bar);
            } else if (!text.equals(portalTexts.get(p.getUUID()))) {
                bar.setName(Component.literal(text));
            }
            portalTexts.put(p.getUUID(), text);
            bar.setColor(open ? BossEvent.BossBarColor.WHITE : fight ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.BLUE);
            bar.setProgress(open ? 1f : fight ? Math.max(0f, Math.min(1f, gatekeeper.getHealth() / gatekeeper.getMaxHealth())) : 0f);
        }
        portalBars.entrySet().removeIf(e -> {
            ServerPlayer p = host.level().getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null && p.level() == host.level() && !settled.contains(e.getKey())) return false;
            e.getValue().removeAllPlayers();
            portalTexts.remove(e.getKey());
            return true;
        });
    }

    // ---- haul: checkpoints, the boss, chests ------------------------------------------------------------------

    /** A mechanic was finished. A trial is an arena win; a checkpoint banks everyone's haul. */
    void mechanicDone(String id, boolean trial) {
        if (closed) return;
        if (trial) haul.addShared(present(), Haul.Kind.ARENA_WIN, 1);
        if (checkpoints.contains(id)) checkpoint("Checkpoint reached");
    }

    private void checkpoint(String what) {
        for (ServerPlayer p : host.players()) {
            if (settled.contains(p.getUUID())) continue;
            haul.bank(p.getUUID());
            bankedItems.merge(p.getUUID(), RunLoot.countItems(p, runId()), Integer::sum);
            everBanked.add(p.getUUID());
            int cleared = RunLoot.clearAll(p, runId());
            Haul.Settlement s = haul.settle(p.getUUID(), Haul.Exit.DIED);
            p.sendSystemMessage(Component.literal(what + ": your haul is banked (" + describe(s) + (cleared > 0 ? ", " + cleared + " item stack(s) secured" : "") + ").").withStyle(ChatFormatting.GREEN));
            coach.notice(p, Onboarding.Lesson.CHECKPOINT, now);
            host.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
        AscensionDungeonsLog.LOG.info("[dive] run {} checkpoint: {}", runId(), what);
    }

    /** The dungeon's final boss fell: that is a win for everyone still inside, and everything they carry is theirs. */
    void bossDefeated() {
        if (closed) return;
        bossDown = true;
        haul.addShared(present(), Haul.Kind.BOSS, 1);
        checkpoint("Boss defeated");
    }

    /** Lootr rolled a chest for this player. */
    void chestLooted(ServerPlayer player, BlockPos pos) {
        if (closed || settled.contains(player.getUUID())) return;
        haul.add(player.getUUID(), Haul.Kind.CHEST, 1);
        looted.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(pos.immutable());
        Feature f = features.get(pos);
        coach.notice(player, Onboarding.Lesson.UNBANKED, now);
        coach.lootedChest(player, f != null ? f.deep : below.contains(player.getUUID()));
    }

    private static String describe(Haul.Settlement s) {
        if (s.isEmpty()) return "nothing yet";
        List<String> parts = new ArrayList<>();
        if (s.arenaWins() > 0) parts.add(s.arenaWins() + " win" + (s.arenaWins() == 1 ? "" : "s"));
        if (s.bossDefeated()) parts.add("the boss");
        if (s.chests() > 0) parts.add(s.chests() + " chest" + (s.chests() == 1 ? "" : "s"));
        return String.join(", ", parts);
    }

    // ---- leaving ---------------------------------------------------------------------------------------------

    private void extract(ServerPlayer p) {
        settle(p.getUUID(), Haul.Exit.EXTRACTED);
        host.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.4, 0.8, 0.4, 0.05);
        sound(p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, 1f, 1f);
        UUID id = p.getUUID();
        host.later(1, () -> host.leave(id));
    }

    /**
     * A hit that would kill a player inside the dive. Nobody dies here: the player is knocked out, loses the unbanked part of
     * their haul and is sent home with everything else they own. Always returns false (the death is cancelled).
     */
    boolean knockedOut(ServerPlayer p) { return knockedOut(p, null); }

    /** @param source what landed the hit, for the recap; may be null */
    boolean knockedOut(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
        if (!closed && !settled.contains(p.getUUID())) {
            UUID id = p.getUUID();
            Entity attacker = source == null ? null : source.getEntity();
            if (attacker == null && lastSpellAt.getOrDefault(id, Long.MIN_VALUE / 2) >= now - 2) { knockedOutBy.put(id, lastSpell.get(id)); knockedOutBySpell.add(id); }
            else if (attacker != null) knockedOutBy.put(id, attacker.getName().getString());
        }
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.removeAllEffects();
        p.invulnerableTime = 60;
        if (!closed && !settled.contains(p.getUUID())) {
            UUID id = p.getUUID();
            if (RunLoot.countItems(p, runId()) > 0 || haul.unbankedWins(id) > 0) {
                knockedOutAtStake.add(id);
                if (coach.firstKnockOutSoft(p)) soften(p);
            }
            settle(id, Haul.Exit.DIED);
            host.later(1, () -> host.leave(id));
        }
        return false;
    }

    /**
     * A player's first knock-out is softer: they keep half of the loot and wins they had not banked, split by kind the way the
     * toll splits it and rounded in their favour. What they keep is banked here, so the settlement that follows leaves it alone.
     * Loot tucked away (bundles, boxes, the ender chest, a Pokémon) is lost in full, as with the toll: hiding never pays.
     */
    private void soften(ServerPlayer p) {
        UUID id = p.getUUID();
        Offer o = offerFor(p, true);
        take(p, o.kinds(), o.plan().lostPerKind());
        int before = RunLoot.countItems(p, runId());
        RunLoot.stripHidden(p, runId());
        int kept = RunLoot.countItems(p, runId());
        int wins = haul.forfeitWins(id, o.plan().winsLost());
        haul.bank(id);
        RunLoot.clearAll(p, runId());
        bankedItems.merge(id, kept, Integer::sum);
        tollItems.merge(id, o.plan().itemsLost() + (before - kept), Integer::sum);
        tollWins.merge(id, wins, Integer::sum);
        softened.add(id);
        AscensionDungeonsLog.LOG.info("[dive] run {} first knock-out of {} is softened: kept {} item(s), lost {} and {} hidden, {} of {} unbanked win(s) lost at stage {}", runId(),
                p.getGameProfile().getName(), kept, o.plan().itemsLost(), before - kept, wins, o.unbankedWins(), stage());
    }

    /** Takes this many items of each kind of this run's loot from the player's inventory. */
    private void take(ServerPlayer p, List<net.minecraft.world.item.ItemStack> kinds, int[] lostPerKind) {
        var inv = p.getInventory();
        for (int k = 0; k < kinds.size(); k++) {
            int owed = lostPerKind[k];
            for (int i = 0; i < inv.getContainerSize() && owed > 0; i++) {
                var s = inv.getItem(i);
                if (!ours(s) || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(kinds.get(k), s)) continue;
                int n = Math.min(owed, s.getCount());
                s.shrink(n);
                owed -= n;
            }
        }
    }

    /** Pays one player for the way they left, exactly once, and banks or takes back the loot they carry. */
    private void settle(UUID player, Haul.Exit exit) {
        if (!settled.add(player)) return;
        int unbankedWins = haul.unbankedWins(player);
        Haul.Settlement s = haul.settle(player, exit);
        int tier = tier();
        for (DiveSettlement.Call call : DiveSettlement.plan(Map.of(player, s), tier)) host.pay(call.tier(), call.arenaWins(), call.bossDefeated(), call.players());
        ServerPlayer online = host.level().getServer().getPlayerList().getPlayer(player);
        int stacks = 0;
        boolean kept = exit == Haul.Exit.EXTRACTED;
        if (online != null) {
            int carried = RunLoot.countItems(online, runId());
            stacks = kept ? RunLoot.clearAll(online, runId()) : RunLoot.stripAll(online, runId());
            String text = switch (exit) {
                case EXTRACTED -> "Extracted! You leave with: " + describe(s) + ".";
                case DIED -> softened.contains(player)
                        ? "Knocked out. It was your first, so you keep half of what you had not banked, as well as what you banked (" + describe(s) + ")."
                        : "Knocked out. You keep what you banked (" + describe(s) + "); the rest of your haul is lost.";
                case TIMED_OUT -> "Time ran out. You keep what you banked (" + describe(s) + "); the rest of your haul is lost.";
                case LEFT -> "You left without extracting. You keep what you banked (" + describe(s) + "); the rest of your haul is lost.";
            };
            online.sendSystemMessage(Component.literal(text).withStyle(kept ? ChatFormatting.GREEN : ChatFormatting.RED));
            switch (exit) {
                case EXTRACTED -> {
                    if (emptyHanded.contains(player)) coach.leave(online);
                    else coach.extracted(online, graced.contains(player) ? Onboarding.Exit.FREE : tolled.contains(player) ? Onboarding.Exit.TOLL : Onboarding.Exit.GATEKEEPER, now);
                }
                case DIED -> coach.knockedOut(online, softened.contains(player), knockedOutAtStake.contains(player), now);
                default -> coach.leave(online);
            }
            Recap.Way way = switch (exit) {
                case EXTRACTED -> closing ? Recap.Way.CLEARED : emptyHanded.contains(player) ? Recap.Way.EMPTY_HANDED : graced.contains(player) ? Recap.Way.FREE
                        : tolled.contains(player) ? Recap.Way.TOLL : Recap.Way.GATEKEEPER;
                case DIED -> Recap.Way.KNOCKED_OUT;
                case TIMED_OUT -> Recap.Way.TIMED_OUT;
                case LEFT -> Recap.Way.LEFT;
            };
            int stage = stage();
            recap(online, new Recap.Facts(way, stage, STAGE_NAMES[Math.min(stage, STAGE_NAMES.length - 1)], Math.max(0, now - startedAt) / 20,
                    looted.getOrDefault(player, Set.of()).size(), everBelow.contains(player), bankedItems.getOrDefault(player, 0) + (kept ? carried : 0),
                    tollItems.getOrDefault(player, 0) + (kept ? 0 : carried), s.arenaWins(), tollWins.getOrDefault(player, 0) + (kept ? 0 : unbankedWins),
                    everBanked.contains(player), knockedOutBy.get(player), knockedOutBySpell.contains(player), tollPercent, softened.contains(player)));
        }
        AscensionDungeonsLog.LOG.info("[dive] run {} settled {} exit={} arenaWins={} boss={} chests={} tier={} loot {} {} stack(s){}", runId(), player, exit,
                s.arenaWins(), s.bossDefeated(), s.chests(), tier, kept ? "kept" : "stripped", stacks, online == null ? " (offline: swept on next login)" : "");
    }

    /** What the player is told as they leave: how it ended, what they kept and lost, and one thing to do differently. */
    private void recap(ServerPlayer p, Recap.Facts facts) {
        List<String> lines = Recap.lines(host.layout().data().name == null ? host.layout().id() : host.layout().data().name, facts);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean tip = i == lines.size() - 1;
            p.sendSystemMessage(i == 0 ? Component.literal(line).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    : Component.literal("  " + line).withStyle(tip ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
        AscensionDungeonsLog.LOG.info("[dive] run {} recap for {}: {}", runId(), p.getGameProfile().getName(), facts);
    }

    /** The run is over. Whoever is still inside leaves with everything if the boss fell, otherwise with what they banked. */
    void close() {
        if (closed) return;
        closing = true;
        coach.close();
        for (Map.Entry<UUID, Run.Member> e : host.run().members().entrySet()) {
            if (settled.contains(e.getKey())) continue;
            settle(e.getKey(), e.getValue().presence() == Run.Presence.LEFT ? Haul.Exit.LEFT : bossDown ? Haul.Exit.EXTRACTED : Haul.Exit.TIMED_OUT);
        }
        closed = true;
        portal.close();
        outbreakBar.removeAllPlayers();
        for (ServerBossEvent bar : portalBars.values()) bar.removeAllPlayers();
        portalBars.clear();
        portalTexts.clear();
        enemies.clear();
    }

    // ---- enemies ---------------------------------------------------------------------------------------------

    private void spawnWave(int count, List<ServerPlayer> players, com.google.gson.JsonArray defs, Vec3 around) {
        Random rng = host.random();
        int stage = stage();
        List<JsonObject> eligible = new ArrayList<>();
        for (JsonElement e : defs) if (Jx.i(e.getAsJsonObject(), "minStage", 0) <= stage) eligible.add(e.getAsJsonObject());
        if (eligible.isEmpty() || players.isEmpty()) return;
        ServerPlayer focus = players.get(rng.nextInt(players.size()));
        Vec3 centre = around != null ? around : focus.position();
        int[] sc = host.schem(focus);
        AuthoredLayout.Room room = around != null ? roomOf(around) : host.layout().roomAt(host.run().seed(), sc[0], sc[1], sc[2]);
        if (room == null || room.spawnCells.length == 0) room = nearestRoom(centre);
        if (room == null) return;
        JsonObject elite = Jx.o(cfg, "elite");
        int placed = 0;
        for (int tries = 0; tries < count * 16 && placed < count; tries++) {
            int[] cell = room.spawnCells[rng.nextInt(room.spawnCells.length)];
            Vec3 pos = Vec3.atBottomCenterOf(host.world(cell));
            if (pos.distanceToSqr(centre) > 48 * 48) continue;
            boolean tooClose = false;
            for (ServerPlayer p : players) if (p.position().distanceToSqr(pos) < 8 * 8) { tooClose = true; break; }
            if (tooClose) continue;
            JsonObject def = eligible.get(rng.nextInt(eligible.size()));
            boolean isElite = rng.nextDouble() < params.eliteChance(stage);
            Mob mob = host.spawn(def, cell, 1.0);
            if (mob == null) continue;
            String spellId = Jx.s(def, "spell", null);
            if (isElite) {
                com.google.gson.JsonArray pool = Jx.a(elite, "spells");
                if (pool.size() > 0) spellId = pool.get(rng.nextInt(pool.size())).getAsString();
                mob.setGlowingTag(true);
                String base = mob.hasCustomName() ? mob.getCustomName().getString() : mob.getName().getString();
                mob.setCustomName(Component.literal("Elite " + base).withStyle(ChatFormatting.LIGHT_PURPLE));
                mob.setCustomNameVisible(true);
                scale(mob, Jx.d(elite, "healthMultiplier", 2.0), Jx.d(elite, "damageMultiplier", 1.4));
            }
            Aggro.Style style = Aggro.Style.parse(Jx.s(def, "style", null), Aggro.Style.HUNTER);
            register(mob, style, isElite, spells.get(spellId));
            placed++;
        }
        if (placed > 0) AscensionDungeonsLog.LOG.info("[dive] run {} wave: {} enemies at stage {} in {}", runId(), placed, stage, room.id);
    }

    /** The room with a standing cell closest to this point: for a player in a doorway or corridor between rooms. */
    private AuthoredLayout.Room nearestRoom(Vec3 world) {
        AuthoredLayout.Room best = null;
        double bestD = Double.MAX_VALUE;
        for (AuthoredLayout.Room r : host.layout().rooms(host.run().seed()))
            for (int[] c : r.spawnCells) {
                double d = Vec3.atBottomCenterOf(host.world(c)).distanceToSqr(world);
                if (d < bestD) { bestD = d; best = r; }
            }
        return best;
    }

    private AuthoredLayout.Room roomOf(Vec3 world) {
        BlockPos origin = host.world(new int[] {0, 0, 0});
        return host.layout().roomAt(host.run().seed(), (int) Math.floor(world.x) - origin.getX(), (int) Math.floor(world.y) - origin.getY(), (int) Math.floor(world.z) - origin.getZ());
    }

    private void register(Mob mob, Aggro.Style style, boolean elite, Spell spell) {
        Enemy en = new Enemy(mob, style, mob.position(), elite, spell);
        enemies.put(mob.getUUID(), en);
        host.hostile(mob);
        // Vanilla creatures pick targets by themselves within their follow range; narrow it for the styles that hold ground.
        var follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) {
            Aggro.Ranges r = Aggro.Ranges.DEFAULT;
            follow.setBaseValue(switch (style) {
                case SENTRY -> r.postRadius();
                case GUARDIAN -> r.guardRadius();
                default -> 40.0;
            });
        }
    }

    private Aggro.Decision decide(Enemy en, List<? extends LivingEntity> candidates) {
        List<Aggro.Candidate> cs = new ArrayList<>(candidates.size());
        for (LivingEntity c : candidates) cs.add(new Aggro.Candidate(en.mob.distanceToSqr(c), c.position().distanceToSqr(en.anchor)));
        if (en.mob.getLastHurtByMob() != null) en.alerted = true;
        Aggro.Decision d = Aggro.decide(en.style, Aggro.Ranges.DEFAULT, en.alerted, en.mob.position().distanceToSqr(en.anchor), cs);
        if (d.alerted() && !en.alerted) {
            en.alerted = true;
            // A provoked pack enemy wakes its packmates.
            if (en.style == Aggro.Style.PACK)
                for (Enemy other : enemies.values())
                    if (other.style == Aggro.Style.PACK && other.mob.distanceToSqr(en.mob) < 12 * 12) other.alerted = true;
        }
        return d;
    }

    /** Vanilla creatures: carry the style out by steering what the creature's own AI does. Pokémon go through {@link #chooseTarget}. */
    private void enemiesTick(List<ServerPlayer> players) {
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer p : players) if (!p.isCreative() && !settled.contains(p.getUUID())) targets.add(p);
        for (var it = enemies.values().iterator(); it.hasNext(); ) {
            Enemy en = it.next();
            if (!en.mob.isAlive()) { it.remove(); continue; }
            // An enemy the whole party has left far behind is let go, so the Outbreak follows the party instead of
            // filling a room nobody is in (which would also hold the wave cap and starve the portal of kills).
            if (!players.isEmpty()) {
                boolean near = false;
                for (ServerPlayer p : players) if (p.distanceToSqr(en.mob) < LET_GO * LET_GO) { near = true; break; }
                if (!near && en.mob != gatekeeper) { en.mob.discard(); it.remove(); continue; }
            }
            if (host.combatManaged(en.mob)) continue;
            Aggro.Decision d = decide(en, targets);
            if (d.target() >= 0) {
                en.mob.setNoAi(false);
                en.mob.setTarget(targets.get(d.target()));
            } else if (d.returnHome()) {
                en.mob.setNoAi(false);
                en.mob.setTarget(null);
                en.mob.getNavigation().moveTo(en.anchor.x, en.anchor.y, en.anchor.z, 1.0);
            } else {
                en.mob.setTarget(null);
                // Ambushers and resting pack members hold perfectly still until something wakes them.
                if (en.style == Aggro.Style.AMBUSHER || en.style == Aggro.Style.PACK) en.mob.setNoAi(true);
            }
        }
    }

    /**
     * Real-time Pokémon combat asks which of these it may attack. Returns the target, or null to stand down.
     * {@code nearest} is what it would have picked by itself; an enemy the dive does not know keeps that.
     */
    LivingEntity chooseTarget(Mob hostile, List<LivingEntity> candidates, LivingEntity nearest) {
        Enemy en = enemies.get(hostile.getUUID());
        if (en == null || closed) return nearest;
        Aggro.Decision d = decide(en, candidates);
        if (d.target() >= 0) return candidates.get(d.target());
        if (d.returnHome()) hostile.getNavigation().moveTo(en.anchor.x, en.anchor.y, en.anchor.z, 1.0);
        return null;
    }

    /** How many times tougher than normal this creature is: 1 for anything the dive has not toughened. */
    public double toughness(Entity e) { return closed ? 1.0 : toughness.getOrDefault(e.getUUID(), 1.0); }

    /** Something died in the dive's level. When it is the gatekeeper, the portal opens. */
    void entityDied(LivingEntity e) {
        if (closed || !(e instanceof Mob mob)) return;
        enemies.remove(mob.getUUID());
        toughness.remove(mob.getUUID());
        if (mob != gatekeeper) return;
        gatekeeper = null;
        portal.gatekeeperDefeated(now);
        host.say("The gatekeeper has fallen! The portal is open for " + portal.remaining(now) / 20 + " seconds: step through and keep everything.", ChatFormatting.AQUA);
        sound(BlockPos.containing(portalCentre), SoundEvents.END_PORTAL_SPAWN, 0.6f, 1.2f);
        host.level().sendParticles(ParticleTypes.END_ROD, portalCentre.x, portalCentre.y + 1.5, portalCentre.z, 80, 0.6, 1.2, 0.6, 0.05);
        AscensionDungeonsLog.LOG.info("[dive] run {} gatekeeper defeated at stage {} ({} so far)", runId(), stage(), portal.gatekeepersDefeated());
    }

    // ---- extraction: the gatekeeper and the toll ----------------------------------------------------------------

    /** What the toll would take from one player right now. */
    private record Offer(List<net.minecraft.world.item.ItemStack> kinds, int[] counts, Toll.Plan plan, int hidden, int unbankedWins) {
        /** Changes when the player's unbanked loot changes, so a stale screen cannot be confirmed. */
        String fingerprint() {
            StringBuilder sb = new StringBuilder().append(hidden).append('/').append(unbankedWins);
            for (int i = 0; i < counts.length; i++) sb.append('|').append(kinds.get(i).getItem()).append('x').append(counts[i]).append('-').append(plan.lostPerKind()[i]);
            return sb.toString();
        }
        boolean free() { return plan.itemsLost() == 0 && plan.winsLost() == 0 && hidden == 0; }
    }

    private boolean ours(net.minecraft.world.item.ItemStack s) { return !s.isEmpty() && runId().equals(RunLoot.runOf(s)); }

    private Offer offerFor(ServerPlayer p) { return offerFor(p, false); }

    /** @param softKnockOut plan what a softened knock-out takes (half, rounded the player's way) instead of what the toll takes */
    private Offer offerFor(ServerPlayer p, boolean softKnockOut) {
        List<net.minecraft.world.item.ItemStack> kinds = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (!ours(s)) continue;
            int k = -1;
            for (int j = 0; j < kinds.size(); j++) if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(kinds.get(j), s)) { k = j; break; }
            if (k < 0) { kinds.add(s.copyWithCount(1)); counts.add(s.getCount()); } else counts.set(k, counts.get(k) + s.getCount());
        }
        int[] c = new int[counts.size()];
        for (int i = 0; i < c.length; i++) c[i] = counts.get(i);
        int wins = haul.unbankedWins(p.getUUID());
        // Seeded by run and player, so the same loot always shows, and costs, the same items.
        long seed = host.run().seed() ^ p.getUUID().getLeastSignificantBits();
        Toll.Plan plan = softKnockOut ? Toll.softened(c, wins, SOFT_KEEP, seed) : Toll.plan(c, wins, tollPercent, seed);
        return new Offer(kinds, c, plan, RunLoot.countHidden(p, runId()), wins);
    }

    private static net.minecraft.world.item.ItemStack button(net.minecraft.world.item.Item item, String name, ChatFormatting colour, String... lore) {
        var stack = new net.minecraft.world.item.ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withColor(colour).withItalic(false)));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Component.literal(l).withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false)));
        stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
        return stack;
    }

    /** Shows the extraction screen: exactly what the toll would take, and the choices. */
    private void offer(ServerPlayer p) {
        Offer o = offerFor(p);
        var box = new net.minecraft.world.SimpleContainer(54);
        int slot = 0;
        for (int i = 0; i < o.kinds().size() && slot < TollMenu.SHOWN; i++) {
            int lost = o.plan().lostPerKind()[i];
            while (lost > 0 && slot < TollMenu.SHOWN) {
                int n = Math.min(lost, o.kinds().get(i).getMaxStackSize());
                box.setItem(slot++, o.kinds().get(i).copyWithCount(n));
                lost -= n;
            }
        }
        int stage = stage();
        String stageName = STAGE_NAMES[Math.min(stage, STAGE_NAMES.length - 1)];
        boolean grace = coach.firstExtractionFree(p);
        box.setItem(TollMenu.PAY, o.free()
                ? button(net.minecraft.world.item.Items.LIME_CONCRETE, "Leave now", ChatFormatting.GREEN, "You have nothing unbanked to lose.")
                : grace ? button(net.minecraft.world.item.Items.LIME_CONCRETE, "Leave free: your first extraction", ChatFormatting.GREEN,
                        "This once, the toll takes nothing.",
                        "Next time, leaving this way costs what is shown above:",
                        o.plan().itemsLost() + " item(s), " + tollPercent + "% of your unbanked loot"
                                + (o.plan().winsLost() > 0 ? ", and " + o.plan().winsLost() + " of your " + o.unbankedWins() + " unbanked win(s)." : "."))
                : button(net.minecraft.world.item.Items.RED_CONCRETE, "Pay the toll and leave", ChatFormatting.RED,
                        "You lose the " + o.plan().itemsLost() + " item(s) shown above (" + tollPercent + "% of your unbanked loot)",
                        o.plan().winsLost() > 0 ? "and " + o.plan().winsLost() + " of your " + o.unbankedWins() + " unbanked win(s)." : "and none of your unbanked wins.",
                        o.hidden() > 0 ? "Also lost: " + o.hidden() + " stack(s) tucked into bundles, boxes, your ender chest or on a Pokemon." : "Everything else leaves with you."));
        box.setItem(TollMenu.FIGHT, portal.state() == ExtractionPortal.State.FIGHT
                ? button(net.minecraft.world.item.Items.IRON_SWORD, "The gatekeeper is being fought", ChatFormatting.YELLOW, "Help beat it: the portal then opens for everyone.")
                : button(net.minecraft.world.item.Items.NETHERITE_SWORD, "Challenge the gatekeeper", ChatFormatting.GOLD,
                        "Outbreak stage " + stage + " (" + stageName + "): the higher the stage, the stronger it is.",
                        "Beat it and everyone leaves with everything.",
                        coach.firstKnockOutSoft(p) ? "Be knocked out and, this first time, you keep half of your unbanked loot." : "Be knocked out and you lose all your unbanked loot."));
        box.setItem(TollMenu.STAY, button(net.minecraft.world.item.Items.BARRIER, "Keep exploring", ChatFormatting.WHITE, "Step off the portal and back on to see this again."));
        String fingerprint = o.fingerprint();
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, player) -> new TollMenu(id, inv, box, choice -> host.later(1, () -> chosen(p, choice, fingerprint))),
                Component.literal(grace && !o.free() ? "Extraction: your first is free" : "Extraction: fight or pay")));
    }

    private void chosen(ServerPlayer p, int choice, String fingerprint) {
        if (p.containerMenu instanceof TollMenu) p.closeContainer();
        if (closed || settled.contains(p.getUUID()) || p.level() != host.level()) return;
        if (choice == TollMenu.PAY) payToll(p, fingerprint);
        else if (choice == TollMenu.FIGHT) challenge(p);
    }

    /** Takes exactly what the screen showed, then lets the player out. If their loot changed since, they are shown the screen again. */
    private void payToll(ServerPlayer p, String fingerprint) {
        Offer o = offerFor(p);
        if (!o.free() && coach.firstExtractionFree(p)) {
            // A player's first extraction costs nothing: no items, no hidden loot, no wins. Nothing is taken, so it does not
            // matter whether their haul changed while the screen was open.
            AscensionDungeonsLog.LOG.info("[dive] run {} first extraction of {} is free: toll of {} item(s) waived at stage {}", runId(), p.getGameProfile().getName(),
                    o.plan().itemsLost(), stage());
            p.sendSystemMessage(Component.literal("Your first extraction is free: nothing was taken.").withStyle(ChatFormatting.GREEN));
            graced.add(p.getUUID());
            extract(p);
            return;
        }
        if (!o.fingerprint().equals(fingerprint)) {
            // Loot was picked up or put away, or a stage rose and added a win: never take anything other than what was shown.
            p.sendSystemMessage(Component.literal("Your haul changed while the screen was open. Look at the toll again before you pay it.").withStyle(ChatFormatting.YELLOW));
            offer(p);
            return;
        }
        if (o.free()) {
            // Nothing unbanked, nothing to pay: the player just leaves.
            emptyHanded.add(p.getUUID());
            p.sendSystemMessage(Component.literal("You have nothing unbanked to lose: you leave free.").withStyle(ChatFormatting.GREEN));
            extract(p);
            return;
        }
        take(p, o.kinds(), o.plan().lostPerKind());
        int hiddenItems = RunLoot.countItems(p, runId());           // after the shown items went: what is left before the hidden loot goes too
        int hidden = RunLoot.stripHidden(p, runId());
        hiddenItems -= RunLoot.countItems(p, runId());
        int wins = haul.forfeitWins(p.getUUID(), o.plan().winsLost());
        tollItems.merge(p.getUUID(), o.plan().itemsLost() + hiddenItems, Integer::sum);
        tollWins.merge(p.getUUID(), wins, Integer::sum);
        AscensionDungeonsLog.LOG.info("[dive] run {} toll paid by {}: {} item(s), {} hidden stack(s), {} win(s) at stage {}", runId(), p.getGameProfile().getName(),
                o.plan().itemsLost(), hidden, wins, stage());
        p.sendSystemMessage(Component.literal("Toll paid: " + o.plan().itemsLost() + " item(s)" + (hidden > 0 ? ", " + hidden + " hidden stack(s)" : "")
                + (wins > 0 ? " and " + wins + " win(s)" : "") + " lost.").withStyle(ChatFormatting.RED));
        tolled.add(p.getUUID());
        extract(p);
    }

    private void challenge(ServerPlayer p) {
        if (portal.state() == ExtractionPortal.State.FIGHT) { p.displayClientMessage(Component.literal("The gatekeeper is already being fought.").withStyle(ChatFormatting.YELLOW), true); return; }
        if (!portal.challenge()) return;
        if (!spawnGatekeeper()) {
            portal.challengeFailed();
            p.sendSystemMessage(Component.literal("No gatekeeper answers. Try again in a moment.").withStyle(ChatFormatting.GRAY));
            return;
        }
        host.say(p.getGameProfile().getName() + " calls out the gatekeeper! Beat it and the portal opens for everyone.", ChatFormatting.GOLD);
        for (ServerPlayer other : unsettled(host.players())) coach.notice(other, Onboarding.Lesson.GATEKEEPER, now);
        sound(BlockPos.containing(portalCentre), SoundEvents.WITHER_SPAWN, 0.6f, 1.0f);
    }

    /** The gatekeeper: drawn from the dungeon's boss pool, tougher with every Outbreak stage and with every gatekeeper already beaten. */
    private boolean spawnGatekeeper() {
        JsonObject g = Jx.o(cfg, "gatekeeper");
        if (g == null) return false;
        AuthoredLayout.Room room = roomOf(portalCentre);
        if (room == null || room.spawnCells.length == 0) room = nearestRoom(portalCentre);
        if (room == null) return false;
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int[] c : room.spawnCells) {
            double d = Vec3.atBottomCenterOf(host.world(c)).distanceToSqr(portalCentre);
            if (d >= 5 * 5 && d < bestD) { bestD = d; best = c; }
        }
        if (best == null) best = room.spawnCells[0];
        int stage = stage();
        JsonObject def = g.deepCopy();
        def.addProperty("levelBonus", Jx.i(g, "levelPerStage", 3) * stage);
        Mob mob = host.spawn(def, best, 1.0);
        if (mob == null) return false;
        double strength = portal.gatekeeperStrength();
        scale(mob, Jx.d(g, "health", 6.0) * (1 + Jx.d(g, "healthPerStage", 0.5) * stage) * strength * (host.combatManaged(mob) ? host.healthFactor() : 1.0),
                (1 + Jx.d(g, "damagePerStage", 0.2) * stage) * strength);
        mob.setGlowingTag(true);
        mob.setCustomName(Component.literal(Jx.s(g, "name", "Gatekeeper") + " (stage " + stage + ")").withStyle(ChatFormatting.GOLD));
        mob.setCustomNameVisible(true);
        com.google.gson.JsonArray pool = Jx.a(g, "spells");
        Spell spell = pool.size() == 0 ? null : spells.get(pool.get(host.random().nextInt(pool.size())).getAsString());
        register(mob, Aggro.Style.HUNTER, true, spell);
        gatekeeper = mob;
        AscensionDungeonsLog.LOG.info("[dive] run {} gatekeeper {} called at stage {}: health {} strength x{}", runId(), mob.getName().getString(), stage, mob.getMaxHealth(), strength);
        return true;
    }

    /**
     * Tougher and harder-hitting. A vanilla creature gets more health and attack on its attributes. A Pokémon cannot: Cobblemon
     * keeps its entity's health in step with its stats (a raised maximum is reset within ticks), so it takes a share of each hit
     * instead, which lasts the same number of hits; real-time combat takes its damage factor.
     */
    private void scale(Mob mob, double health, double damage) {
        if (health != 1.0) {
            if (host.combatManaged(mob)) toughness.put(mob.getUUID(), health);
            else {
                var max = mob.getAttribute(Attributes.MAX_HEALTH);
                if (max != null) { max.setBaseValue(max.getBaseValue() * health); mob.setHealth(mob.getMaxHealth()); }
            }
        }
        if (damage == 1.0) return;
        if (host.combatManaged(mob)) host.empower(mob, damage);
        else {
            var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack != null) attack.setBaseValue(attack.getBaseValue() * damage);
        }
    }

    /** A gatekeeper nobody is left to fight goes away, and the portal waits again. */
    private void gatekeeperTick(List<ServerPlayer> players) {
        if (portal.state() != ExtractionPortal.State.FIGHT || now % 20 != 0) return;
        boolean anyone = false;
        if (gatekeeper != null && gatekeeper.isAlive())
            for (ServerPlayer p : players) if (!settled.contains(p.getUUID()) && p.position().distanceToSqr(portalCentre) < 48 * 48) { anyone = true; break; }
        if (anyone) return;
        if (gatekeeper != null) { enemies.remove(gatekeeper.getUUID()); gatekeeper.discard(); gatekeeper = null; }
        portal.challengeFailed();
        host.say("The gatekeeper withdraws. The portal waits.", ChatFormatting.GRAY);
    }

    // ---- spells ----------------------------------------------------------------------------------------------

    private void spellsTick(List<ServerPlayer> players) {
        for (Enemy en : enemies.values()) {
            if (en.spell == null || !en.mob.isAlive()) continue;
            Spell.Cast.Phase phase = en.cast.poll(now);
            if (phase == Spell.Cast.Phase.IDLE) {
                LivingEntity target = en.mob.getTarget();
                if (target == null || !target.isAlive() || now % 5 != 0) continue;
                if (en.cast.begin(en.spell, now, en.mob.distanceTo(target))) {
                    en.castCentre = target.position();
                    for (ServerPlayer p : players)
                        if (p.position().distanceToSqr(en.castCentre) < 20 * 20)
                            p.displayClientMessage(Component.literal(en.mob.getName().getString() + " is casting " + en.spell.name() + "!").withStyle(ChatFormatting.AQUA), true);
                }
            } else if (phase == Spell.Cast.Phase.WARNING) {
                if (now % 4 == 0) {
                    ring(en.castCentre, en.spell.radius(), new DustParticleOptions(rgb(spellColours.getOrDefault(en.spell.id(), 0x33D9FF)), 1.2f));
                    double near = en.spell.radius() + 8;
                    for (ServerPlayer p : players) if (!settled.contains(p.getUUID()) && p.position().distanceToSqr(en.castCentre) < near * near) coach.notice(p, Onboarding.Lesson.SPELLS, now);
                }
            } else {
                land(en, players);
                en.cast.finish();
            }
        }
    }

    private static Vector3f rgb(int colour) { return new Vector3f(((colour >> 16) & 255) / 255f, ((colour >> 8) & 255) / 255f, (colour & 255) / 255f); }

    private void ring(Vec3 c, double radius, DustParticleOptions dust) {
        int points = Math.max(12, (int) (radius * 8));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            host.level().sendParticles(dust, c.x + Math.cos(a) * radius, c.y + 0.15, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    private void land(Enemy en, List<ServerPlayer> players) {
        Spell s = en.spell;
        Vec3 c = en.castCentre;
        ServerLevel level = host.level();
        level.sendParticles(new DustParticleOptions(rgb(spellColours.getOrDefault(s.id(), 0x33D9FF)), 1.6f), c.x, c.y + 0.4, c.z, 90, s.radius() / 2, 0.4, s.radius() / 2, 0.1);
        level.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.3, c.z, 20, s.radius() / 2, 0.2, s.radius() / 2, 0.02);
        sound(BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.4f);
        for (ServerPlayer p : players) {
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            if (dx * dx + dz * dz > s.radius() * s.radius() || Math.abs(p.getY() - c.y) > 3 || p.isCreative()) continue;
            // Remembered before the hit lands: if this one knocks the player out, the recap names it.
            lastSpell.put(p.getUUID(), s.name() + " (" + en.mob.getName().getString() + ")");
            lastSpellAt.put(p.getUUID(), now);
            if (s.damage() > 0) p.hurt(level.damageSources().magic(), (float) s.damage());
            if (!p.isAlive() || settled.contains(p.getUUID())) continue;
            int lasting = Math.max(1, (int) (s.strength() * 20));
            switch (s.effect()) {
                case PULL -> {
                    Vec3 pull = new Vec3(c.x - p.getX(), 0, c.z - p.getZ());
                    if (pull.lengthSqr() > 0.01) { p.setDeltaMovement(pull.normalize().scale(s.strength()).add(0, 0.15, 0)); p.hurtMarked = true; }
                }
                case PUSH -> {
                    Vec3 push = new Vec3(p.getX() - c.x, 0, p.getZ() - c.z);
                    p.setDeltaMovement((push.lengthSqr() > 0.01 ? push.normalize() : new Vec3(1, 0, 0)).scale(s.strength()).add(0, 0.25, 0));
                    p.hurtMarked = true;
                }
                case KNOCKUP -> { p.setDeltaMovement(p.getDeltaMovement().x, s.strength(), p.getDeltaMovement().z); p.hurtMarked = true; }
                case SLOW -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, lasting, 2));
                case FREEZE -> { p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, lasting, 4)); p.setTicksFrozen(Math.max(p.getTicksFrozen(), lasting + 140)); }
                case BURN -> p.igniteForSeconds((float) s.strength());
                case POISON -> p.addEffect(new MobEffectInstance(MobEffects.POISON, lasting, 0));
                case WITHER -> p.addEffect(new MobEffectInstance(MobEffects.WITHER, lasting, 0));
                case BLIND -> p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, lasting, 0));
                case LEVITATE -> p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, lasting, 1));
                case WEAKEN -> p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, lasting, 0));
                case NONE -> { }
            }
        }
    }

    // ---- chests, altars and the portal -----------------------------------------------------------------------

    /**
     * A player right-clicked a block. Returns null when the dive has nothing to say about it (the click goes on as usual),
     * FAIL to refuse it, SUCCESS when the dive used it.
     */
    InteractionResult blockUsed(ServerPlayer p, BlockPos pos, boolean mainHand) {
        Feature f = features.get(pos);
        if (f == null || closed) return null;
        JsonObject fc = Jx.o(cfg, "features");
        switch (f.kind) {
            case ALTAR -> {
                if (!mainHand) return InteractionResult.FAIL;
                if (f.used) { p.displayClientMessage(Component.literal("This altar is spent.").withStyle(ChatFormatting.GRAY), true); return InteractionResult.FAIL; }
                f.used = true;
                int before = stage();
                clock.addPenalty(ticks(Jx.d(fc, "curseSeconds", 120)));
                haul.addShared(present(), Haul.Kind.ARENA_WIN, 1);
                host.level().setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                host.level().sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.3, 0.5, 0.3, 0.02);
                sound(pos, SoundEvents.WITHER_AMBIENT, 0.7f, 0.6f);
                host.say(p.getGameProfile().getName() + " took the altar's curse: the Outbreak surges, and everyone's haul grows.", ChatFormatting.DARK_PURPLE);
                AscensionDungeonsLog.LOG.info("[dive] run {} curse altar used by {}: stage {} -> {}", runId(), p.getGameProfile().getName(), before, stage());
                return InteractionResult.SUCCESS;
            }
            case TIMER_CHEST -> {
                int need = Jx.i(fc, "timerStage", 2);
                if (stage() >= need) return null;
                if (mainHand) p.displayClientMessage(Component.literal("Sealed until the Outbreak reaches stage " + need + " (" + STAGE_NAMES[Math.min(need, STAGE_NAMES.length - 1)] + ").").withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.FAIL;
            }
            case OUTBREAK_CHEST -> {
                // Whoever opens it now rolls from the rarity it has grown to.
                setTable(pos, outbreakRarity(f));
                return null;
            }
            case STRONGBOX -> {
                if (mainHand && !f.used) {
                    f.used = true;
                    host.say("The strongbox wakes the dungeon! Guardians are coming for " + p.getGameProfile().getName() + ".", ChatFormatting.RED);
                    sound(pos, SoundEvents.WITHER_SPAWN, 0.5f, 1.3f);
                    JsonObject g = Jx.o(cfg, "guardians");
                    if (g != null) {
                        com.google.gson.JsonArray defs = new com.google.gson.JsonArray();
                        JsonObject def = g.deepCopy();
                        def.addProperty("style", "hunter");
                        defs.add(def);
                        spawnWave(Jx.i(fc, "strongboxWave", 6), host.players(), defs, Vec3.atBottomCenterOf(pos));
                    }
                }
                return null;
            }
            default -> { return null; }
        }
    }

    private ChestRarity outbreakRarity(Feature f) {
        return f.rarity.upgraded(stage() / Math.max(1, Jx.i(Jx.o(cfg, "features"), "outbreakChestStagesPerUpgrade", 2)));
    }

    private void setTable(BlockPos pos, ChestRarity rarity) {
        try {
            RandomizableContainer.setBlockEntityLootTable(host.level(), host.level().getRandom(), pos,
                    ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(rarity.lootTable())));
        } catch (RuntimeException e) {
            AscensionDungeonsLog.LOG.warn("[dive] could not set the loot table at {}", pos, e);
        }
    }

    /** Labels and rarity sparkles for features a player is near, and the portal's own effects. */
    private void featuresTick(List<ServerPlayer> players) {
        ServerLevel level = host.level();
        int timerStage = Jx.i(Jx.o(cfg, "features"), "timerStage", 2);
        for (Feature f : features.values()) {
            boolean near = false;
            for (ServerPlayer p : players) if (p.blockPosition().distSqr(f.pos) < 40 * 40) { near = true; break; }
            if (!near || !level.isLoaded(f.pos)) continue;
            String text;
            int colour;
            switch (f.kind) {
                case PORTAL -> {
                    boolean open = portal.state() == ExtractionPortal.State.OPEN;
                    text = open ? "Extraction Portal: OPEN" : portal.state() == ExtractionPortal.State.FIGHT ? "Extraction Portal: beat the gatekeeper" : "Extraction Portal: fight or pay the toll";
                    colour = open ? 0xFFFFFF : 0x55CCFF;
                    level.sendParticles(open ? ParticleTypes.END_ROD : ParticleTypes.PORTAL, portalCentre.x, portalCentre.y + (open ? 1.5 : 0.6), portalCentre.z, open ? 30 : 12, 0.5, open ? 1.2 : 0.4, 0.5, 0.02);
                }
                case DESCENT -> {
                    text = tunnelMouth != null ? "Tunnel down to the Depths" : "Stairway down to the Depths";
                    colour = 0xFFB347;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, f.pos.getX() + 0.5, f.pos.getY() + 0.6, f.pos.getZ() + 0.5, 10, 0.4, 0.4, 0.4, 0.02);
                }
                case RETURN -> {
                    text = tunnelMouth != null ? "Way back up to the dungeon" : "Way back up";
                    colour = 0xFFB347;
                    if (tunnelMouth == null) level.sendParticles(ParticleTypes.REVERSE_PORTAL, f.pos.getX() + 0.5, f.pos.getY() + 0.6, f.pos.getZ() + 0.5, 10, 0.4, 0.4, 0.4, 0.02);
                }
                case ALTAR -> { text = f.used ? "Curse Altar (spent)" : "Curse Altar: touch to enrage the Outbreak"; colour = f.used ? 0x777777 : 0xC050FF; }
                case TIMER_CHEST -> {
                    boolean unlocked = stage() >= timerStage;
                    text = unlocked ? f.rarity.label() + " Timer Chest: unsealed" : "Timer Chest: unseals at stage " + timerStage;
                    colour = unlocked ? f.rarity.colour() : 0xFFD437;
                }
                case STRONGBOX -> { text = f.used ? "Strongbox (opened)" : "Strongbox: guarded"; colour = 0xFF3448; }
                case OUTBREAK_CHEST -> {
                    ChestRarity r = outbreakRarity(f);
                    text = r.label() + " Outbreak Chest" + (r == ChestRarity.EPIC ? "" : " (still growing)");
                    colour = r.colour();
                    f.shown = r;
                }
                default -> { text = f.rarity.label() + " Chest"; colour = f.rarity.colour(); }
            }
            boolean sealed = f.kind == FeaturePlanner.Kind.TIMER_CHEST && stage() < timerStage;
            ChestRarity look = f.kind == FeaturePlanner.Kind.OUTBREAK_CHEST ? outbreakRarity(f) : f.rarity;
            displays(f, text, colour, f.rarity == null ? 0 : com.ascensiondungeons.outbreak.ChestLook.model(f.kind, look, sealed));
            if (f.rarity != null || (f.kind == FeaturePlanner.Kind.ALTAR && !f.used)) {
                Vector3f rgb = new Vector3f(((colour >> 16) & 255) / 255f, ((colour >> 8) & 255) / 255f, (colour & 255) / 255f);
                level.sendParticles(new DustParticleOptions(rgb, 1.0f), f.pos.getX() + 0.5, f.pos.getY() + 1.1, f.pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0);
            }
        }
    }

    /**
     * The floating label of a feature and, when the dungeon turns models on, the chest model shown over the real chest.
     * The model is an item display of {@link com.ascensiondungeons.outbreak.ChestLook#DISPLAY_ITEM} with a custom-model number;
     * players need the resource pack for it, so it is off unless {@code features.models} is true.
     */
    private void displays(Feature f, String text, int colour, int modelId) {
        ServerLevel level = host.level();
        boolean models = modelId != 0 && Jx.b(Jx.o(cfg, "features"), "models", false);
        if (f.label == null || f.label.isRemoved() || (models && (f.model == null || f.model.isRemoved()))) {
            // The chunk may have been unloaded and reloaded: drop whatever was left there before making new ones.
            for (Display old : level.getEntitiesOfClass(Display.class, new AABB(f.pos).inflate(1.5, 3, 1.5), d -> d.getTags().contains(LABEL_TAG))) old.discard();
            Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(level);
            if (d == null) return;
            d.moveTo(f.pos.getX() + 0.5, f.pos.getY() + (f.rarity == null && f.kind != FeaturePlanner.Kind.ALTAR ? 2.6 : 1.6), f.pos.getZ() + 0.5);
            d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            d.setViewRange(0.6f);
            d.addTag(LABEL_TAG);
            level.addFreshEntity(d);
            f.label = d;
            f.shownText = null;
            f.model = null;
            if (models) {
                Display.ItemDisplay m = EntityType.ITEM_DISPLAY.create(level);
                if (m != null) {
                    // An item display turns its model half a turn; the entity is turned back so the chest's front faces north, like the block.
                    m.moveTo(f.pos.getX() + 0.5, f.pos.getY() + 0.5, f.pos.getZ() + 0.5, 180f, 0f);
                    m.setItemTransform(net.minecraft.world.item.ItemDisplayContext.NONE);
                    m.setViewRange(0.8f);
                    m.addTag(LABEL_TAG);
                    level.addFreshEntity(m);
                    f.model = m;
                    f.shownModel = 0;
                }
            }
        }
        if (!text.equals(f.shownText)) {
            f.label.setText(Component.literal(text).withStyle(st -> st.withColor(TextColor.fromRgb(colour))));
            f.shownText = text;
        }
        if (models && f.model != null && f.shownModel != modelId) {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(com.ascensiondungeons.outbreak.ChestLook.DISPLAY_ITEM)));
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(modelId));
            f.model.setItemStack(stack);
            f.shownModel = modelId;
        }
    }

    private void sound(BlockPos at, SoundEvent event, float volume, float pitch) {
        host.level().playSound(null, at, event, SoundSource.HOSTILE, volume, pitch);
    }
}
