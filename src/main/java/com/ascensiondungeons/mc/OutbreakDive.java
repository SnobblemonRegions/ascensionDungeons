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
import com.ascensiondungeons.outbreak.OutbreakClock;
import com.ascensiondungeons.outbreak.Spell;
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
    private final double portalRadius, chargeRadius;
    private final Map<String, Spell> spells = new HashMap<>();
    private final Set<String> checkpoints = new HashSet<>();
    private final Map<BlockPos, Feature> features = new LinkedHashMap<>();
    private final Map<UUID, Enemy> enemies = new LinkedHashMap<>();
    private final Set<UUID> settled = new HashSet<>();
    private final ServerBossEvent outbreakBar, portalBar;
    private Vec3 portalCentre;
    private OutbreakClock clock;
    private long now, nextWaveAt;
    private boolean bossDown, closed;
    private ExtractionPortal.State lastPortalState = ExtractionPortal.State.CHARGING;

    /** The dive for this session, or null when its dungeon has no Outbreak settings. */
    static OutbreakDive create(Host host) {
        JsonObject cfg = host.layout().outbreak();
        if (cfg == null) return null;
        try {
            return new OutbreakDive(host, cfg);
        } catch (RuntimeException e) {
            // Bad settings must not take the dungeon down: it runs as an ordinary dungeon instead.
            AscensionDungeonsLog.LOG.error("Outbreak settings for {} are invalid; running without the Outbreak", host.layout().id(), e);
            return null;
        }
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
        this.portal = new ExtractionPortal(new ExtractionPortal.Params(Jx.i(p, "charge", 10), ticks(Jx.d(p, "windowSeconds", 20)), Jx.i(p, "growthPercent", 50)));
        this.portalRadius = Jx.d(p, "radius", 2.5);
        this.chargeRadius = Jx.d(p, "chargeRadius", 24);
        this.baseTier = Math.max(1, Jx.i(host.layout().data().pokemon, "tier", 1));
        for (JsonElement e : Jx.a(cfg, "checkpoints")) checkpoints.add(e.getAsString());
        for (JsonElement e : Jx.a(cfg, "spells")) {
            JsonObject s = e.getAsJsonObject();
            Spell spell = new Spell(Jx.s(s, "id", null), Jx.s(s, "name", null), ticks(Jx.d(s, "cooldownSeconds", 12)), ticks(Jx.d(s, "warnSeconds", 2)),
                    Jx.d(s, "range", 10), Jx.d(s, "radius", 3), Jx.d(s, "damage", 4),
                    Spell.Effect.valueOf(Jx.s(s, "effect", "none").toUpperCase(java.util.Locale.ROOT)), Jx.d(s, "strength", 0));
            spells.put(spell.id(), spell);
        }
        for (FeaturePlanner.Placement pl : host.layout().features(host.run().seed())) {
            BlockPos pos = host.world(pl.cell());
            if (pl.kind() == FeaturePlanner.Kind.PORTAL) portalCentre = Vec3.atBottomCenterOf(pos);
            features.put(pos, new Feature(pl, pos));
        }
        if (portalCentre == null) throw new IllegalStateException("no room for an extraction portal");
        outbreakBar = new ServerBossEvent(Component.literal("Outbreak"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_6);
        portalBar = new ServerBossEvent(Component.literal("Extraction portal"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
        portalBar.setProgress(0f);
    }

    // ---- queries ---------------------------------------------------------------------------------------------

    public int stage() { return clock == null ? 0 : clock.stage(now); }
    public boolean settled(UUID player) { return settled.contains(player); }
    public Vec3 portalCentre() { return portalCentre; }
    private int tier() { return params.zoneTier(baseTier, stage()); }
    private UUID runId() { return host.run().id(); }

    private List<UUID> present() {
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer p : host.players()) if (!settled.contains(p.getUUID())) out.add(p.getUUID());
        return out;
    }

    public String describe() {
        return "outbreak stage=" + stage() + " tier=" + tier() + " portal=" + portal.state() + " " + Math.round(portal.charge() * 100) + "% enemies=" + enemies.size()
                + " features=" + features.size() + " settled=" + settled.size();
    }

    /** Operator aid: one line per feature, "KIND rarity x y z". */
    public List<String> featureLines() {
        List<String> out = new ArrayList<>();
        for (Feature f : features.values())
            out.add(f.kind + " " + (f.rarity == null ? "-" : f.rarity.name()) + " " + f.pos.getX() + " " + f.pos.getY() + " " + f.pos.getZ() + (f.used ? " used" : ""));
        return out;
    }

    /** Operator aid: bank everyone's haul as a checkpoint would. */
    public void operatorCheckpoint() { if (!closed) checkpoint("Checkpoint (operator)"); }

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
            nextWaveAt = now + waveEvery;
            host.say("An Outbreak is building here. The longer you stay, the fiercer it gets and the more you carry out.", ChatFormatting.GOLD);
            host.say("Loot is yours only once it is banked at a checkpoint or carried out through the extraction portal near the entrance.", ChatFormatting.GRAY);
        }
        // Anyone who walked out or dropped off without extracting keeps only what they banked.
        for (Map.Entry<UUID, Run.Member> e : host.run().members().entrySet())
            if (e.getValue().presence() == Run.Presence.LEFT && !settled.contains(e.getKey())) settle(e.getKey(), Haul.Exit.LEFT);

        int risen = clock.pollNewStage(now);
        if (risen > 0) stageRose(risen);

        portal.tick(now);
        if (portal.state() != lastPortalState) {
            if (portal.state() == ExtractionPortal.State.OPEN) {
                host.say("The extraction portal is open! Reach it within " + portal.remaining(now) / 20 + " seconds.", ChatFormatting.AQUA);
                sound(BlockPos.containing(portalCentre), SoundEvents.END_PORTAL_SPAWN, 0.6f, 1.2f);
            } else if (lastPortalState == ExtractionPortal.State.OPEN) {
                host.say("The extraction portal has closed. It needs more charge now.", ChatFormatting.RED);
            }
            lastPortalState = portal.state();
        }
        if (portal.state() == ExtractionPortal.State.OPEN)
            for (ServerPlayer p : players)
                if (!settled.contains(p.getUUID()) && p.position().distanceToSqr(portalCentre) <= portalRadius * portalRadius && portal.escape(p.getUUID(), now)) extract(p);

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

    private void stageRose(int stage) {
        String name = STAGE_NAMES[Math.min(stage, STAGE_NAMES.length - 1)];
        host.say("The Outbreak grows: stage " + stage + ", " + name + ". Surviving it adds to your haul.", ChatFormatting.GOLD);
        haul.addShared(present(), Haul.Kind.ARENA_WIN, 1);
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
        if (portal.state() == ExtractionPortal.State.OPEN) {
            portalBar.setName(Component.literal("Extraction portal: OPEN, " + (portal.remaining(now) + 19) / 20 + "s"));
            portalBar.setColor(BossEvent.BossBarColor.WHITE);
            portalBar.setProgress(1f);
        } else {
            portalBar.setName(Component.literal("Extraction portal: " + Math.round(portal.charge() * 100) + "% charged (defeat enemies near it)"));
            portalBar.setColor(BossEvent.BossBarColor.BLUE);
            portalBar.setProgress((float) portal.charge());
        }
        for (ServerBossEvent bar : List.of(outbreakBar, portalBar)) {
            for (ServerPlayer p : players) bar.addPlayer(p);
            for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (p.level() != host.level() || p.isRemoved()) bar.removePlayer(p);
        }
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
            int cleared = RunLoot.clearAll(p, runId());
            Haul.Settlement s = haul.settle(p.getUUID(), Haul.Exit.DIED);
            p.sendSystemMessage(Component.literal(what + ": your haul is banked (" + describe(s) + (cleared > 0 ? ", " + cleared + " item stack(s) secured" : "") + ").").withStyle(ChatFormatting.GREEN));
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
        if (!closed && !settled.contains(player.getUUID())) haul.add(player.getUUID(), Haul.Kind.CHEST, 1);
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
    boolean knockedOut(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.removeAllEffects();
        p.invulnerableTime = 60;
        if (!closed && !settled.contains(p.getUUID())) {
            settle(p.getUUID(), Haul.Exit.DIED);
            UUID id = p.getUUID();
            host.later(1, () -> host.leave(id));
        }
        return false;
    }

    /** Pays one player for the way they left, exactly once, and banks or takes back the loot they carry. */
    private void settle(UUID player, Haul.Exit exit) {
        if (!settled.add(player)) return;
        Haul.Settlement s = haul.settle(player, exit);
        int tier = tier();
        for (DiveSettlement.Call call : DiveSettlement.plan(Map.of(player, s), tier)) host.pay(call.tier(), call.arenaWins(), call.bossDefeated(), call.players());
        ServerPlayer online = host.level().getServer().getPlayerList().getPlayer(player);
        int stacks = 0;
        boolean kept = exit == Haul.Exit.EXTRACTED;
        if (online != null) {
            stacks = kept ? RunLoot.clearAll(online, runId()) : RunLoot.stripAll(online, runId());
            String text = switch (exit) {
                case EXTRACTED -> "Extracted! You keep everything: " + describe(s) + ".";
                case DIED -> "Knocked out. You keep what you banked (" + describe(s) + "); the rest of your haul is lost.";
                case TIMED_OUT -> "Time ran out. You keep what you banked (" + describe(s) + "); the rest of your haul is lost.";
                case LEFT -> "You left without extracting. You keep what you banked (" + describe(s) + "); the rest of your haul is lost.";
            };
            online.sendSystemMessage(Component.literal(text).withStyle(kept ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        AscensionDungeonsLog.LOG.info("[dive] run {} settled {} exit={} arenaWins={} boss={} chests={} tier={} loot {} {} stack(s){}", runId(), player, exit,
                s.arenaWins(), s.bossDefeated(), s.chests(), tier, kept ? "kept" : "stripped", stacks, online == null ? " (offline: swept on next login)" : "");
    }

    /** The run is over. Whoever is still inside leaves with everything if the boss fell, otherwise with what they banked. */
    void close() {
        if (closed) return;
        for (Map.Entry<UUID, Run.Member> e : host.run().members().entrySet()) {
            if (settled.contains(e.getKey())) continue;
            settle(e.getKey(), e.getValue().presence() == Run.Presence.LEFT ? Haul.Exit.LEFT : bossDown ? Haul.Exit.EXTRACTED : Haul.Exit.TIMED_OUT);
        }
        closed = true;
        portal.close();
        outbreakBar.removeAllPlayers();
        portalBar.removeAllPlayers();
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
        AuthoredLayout.Room room = around != null ? roomOf(around) : host.layout().roomAt(sc[0], sc[1], sc[2]);
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
            Mob mob = host.spawn(def, cell, isElite ? Jx.d(elite, "healthMultiplier", 2.0) : 1.0);
            if (mob == null) continue;
            String spellId = Jx.s(def, "spell", null);
            if (isElite) {
                com.google.gson.JsonArray pool = Jx.a(elite, "spells");
                if (pool.size() > 0) spellId = pool.get(rng.nextInt(pool.size())).getAsString();
                mob.setGlowingTag(true);
                String base = mob.hasCustomName() ? mob.getCustomName().getString() : mob.getName().getString();
                mob.setCustomName(Component.literal("Elite " + base).withStyle(ChatFormatting.LIGHT_PURPLE));
                mob.setCustomNameVisible(true);
                host.empower(mob, Jx.d(elite, "damageMultiplier", 1.4));
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
        for (AuthoredLayout.Room r : host.layout().data().rooms)
            for (int[] c : r.spawnCells) {
                double d = Vec3.atBottomCenterOf(host.world(c)).distanceToSqr(world);
                if (d < bestD) { bestD = d; best = r; }
            }
        return best;
    }

    private AuthoredLayout.Room roomOf(Vec3 world) {
        BlockPos origin = host.world(new int[] {0, 0, 0});
        return host.layout().roomAt((int) Math.floor(world.x) - origin.getX(), (int) Math.floor(world.y) - origin.getY(), (int) Math.floor(world.z) - origin.getZ());
    }

    private void register(Mob mob, Aggro.Style style, boolean elite, Spell spell) {
        Enemy en = new Enemy(mob, style, mob.position(), elite, spell);
        enemies.put(mob.getUUID(), en);
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
                if (!near) { en.mob.discard(); it.remove(); continue; }
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

    /** Something died in the dive's level. A kill near the portal charges it. */
    void entityDied(LivingEntity e) {
        if (closed || !(e instanceof Mob mob)) return;
        Enemy en = enemies.remove(mob.getUUID());
        if (mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null) return;
        if (mob.position().distanceToSqr(portalCentre) > chargeRadius * chargeRadius) return;
        double weight = en != null && en.elite ? Jx.d(Jx.o(cfg, "elite"), "killWeight", 3) : 1;
        portal.onKill(weight, now);
        host.level().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 0.8, mob.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
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
                if (now % 4 == 0) ring(en.castCentre, en.spell.radius(), new DustParticleOptions(new Vector3f(0.2f, 0.85f, 1f), 1.2f));
            } else {
                land(en, players);
                en.cast.finish();
            }
        }
    }

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
        level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.3, c.z, 80, s.radius() / 2, 0.3, s.radius() / 2, 0.1);
        level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 0.5, c.z, 40, s.radius() / 2, 0.5, s.radius() / 2, 0.05);
        sound(BlockPos.containing(c), SoundEvents.GENERIC_SPLASH, 1f, 0.9f);
        for (ServerPlayer p : players) {
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            if (dx * dx + dz * dz > s.radius() * s.radius() || Math.abs(p.getY() - c.y) > 3 || p.isCreative()) continue;
            if (s.damage() > 0) p.hurt(level.damageSources().magic(), (float) s.damage());
            if (!p.isAlive() || settled.contains(p.getUUID())) continue;
            switch (s.effect()) {
                case PULL -> {
                    Vec3 pull = new Vec3(c.x - p.getX(), 0, c.z - p.getZ());
                    if (pull.lengthSqr() > 0.01) { p.setDeltaMovement(pull.normalize().scale(s.strength()).add(0, 0.15, 0)); p.hurtMarked = true; }
                }
                case KNOCKUP -> { p.setDeltaMovement(p.getDeltaMovement().x, s.strength(), p.getDeltaMovement().z); p.hurtMarked = true; }
                case SLOW -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (s.strength() * 20), 2));
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
                    text = open ? "Extraction Portal: OPEN" : "Extraction Portal (" + Math.round(portal.charge() * 100) + "%)";
                    colour = open ? 0xFFFFFF : 0x55CCFF;
                    level.sendParticles(open ? ParticleTypes.END_ROD : ParticleTypes.PORTAL, portalCentre.x, portalCentre.y + (open ? 1.5 : 0.6), portalCentre.z, open ? 30 : 12, 0.5, open ? 1.2 : 0.4, 0.5, 0.02);
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
            if (f.kind != FeaturePlanner.Kind.PORTAL && !(f.kind == FeaturePlanner.Kind.ALTAR && f.used)) {
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
            d.moveTo(f.pos.getX() + 0.5, f.pos.getY() + (f.kind == FeaturePlanner.Kind.PORTAL ? 2.6 : 1.6), f.pos.getZ() + 0.5);
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
