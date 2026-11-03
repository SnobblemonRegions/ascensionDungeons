package com.ascensiondungeons.mc;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Real-time Pokemon combat inside authored dungeons (Cobblemon). Dungeon Pokemon hunt players and their Pokemon; a
 * player's sent-out Pokemon fight back. Damage is dealt by this class (Cobblemon has no real-time attack of its own),
 * scaled from the Pokemon's level. A player's Pokemon never dies in here: a lethal hit faints it, recalls it and
 * restores its health, and it cannot be sent out again until a cooldown ends. Everything is healed when the run ends.
 * Only constructed when Cobblemon is installed.
 */
public final class AuthoredCombat implements AuthoredBridges.Combat {
    private static final double SIGHT = 26.0;
    private static final double ALLY_SIGHT = 20.0;
    private static final int HIT_INTERVAL = 20;
    private static final double RANGED = 9.0;

    private final int cooldownTicks;
    private final double damageMultiplier;
    private final double moveBaseSeconds;
    private final double moveSecondsPerPower;
    private final Set<ServerLevel> watched = new HashSet<>();
    private final Map<UUID, Long> downedUntil = new HashMap<>();
    private final Map<UUID, Long> nextHit = new HashMap<>();
    private final Map<UUID, Pokemon> touched = new HashMap<>();
    private final Map<UUID, Double> empowered = new HashMap<>();
    private final Map<UUID, Map<String, Long>> moveReady = new HashMap<>();
    private final Set<UUID> firstMoveDone = new HashSet<>();
    private final Set<UUID> firstHitTaken = new HashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final AffixFx affixes = AffixFx.tryCreate();
    private final Map<ServerLevel, RealtimeStatus> statuses = new HashMap<>();
    private final Set<UUID> seen = new HashSet<>();
    private final Set<UUID> survived = new HashSet<>();
    private static final Set<String> RECOVERY = Set.of("recover", "roost", "softboiled", "slackoff", "milkdrink", "moonlight", "morningsun", "synthesis", "shoreup", "healorder", "wish");
    private static final Set<String> DRAIN = Set.of("absorb", "megadrain", "gigadrain", "drainpunch", "leechlife", "hornleech", "drainingkiss", "oblivionwing", "paraboliclcharge", "bitterblade", "matchagotcha", "dreameater");

    @Override
    public void empower(Mob creature, double damageMultiplier) { empowered.put(creature.getUUID(), damageMultiplier); }

    public AuthoredCombat(int cooldownSeconds, double damageMultiplier, double moveBaseSeconds, double moveSecondsPerPower) {
        if (affixes != null) affixes.refreshCaps();
        this.moveBaseSeconds = moveBaseSeconds;
        this.moveSecondsPerPower = moveSecondsPerPower;
        this.cooldownTicks = Math.max(0, cooldownSeconds) * 20;
        this.damageMultiplier = damageMultiplier;
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::allowDamage);
        // Dungeons fight in real time: a turn-based Cobblemon battle (thrown ball, challenge) must not open inside a run level.
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST, event -> {
            for (ServerPlayer p : event.getBattle().getPlayers()) {
                if (watched.contains(p.serverLevel())) {
                    event.cancel();
                    p.displayClientMessage(Component.literal("Turn-based battles are disabled in dungeons: fight in real time.").withStyle(ChatFormatting.RED), true);
                    break;
                }
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /** A lethal hit: Last Breath / Stubborn leave 1 HP once; otherwise a player's Pokemon faints instead of dying. */
    private boolean allowDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!(entity instanceof PokemonEntity pe) || !(pe.level() instanceof ServerLevel level) || !watched.contains(level)) return true;
        Pokemon p = pe.getPokemon();
        if (amount < pe.getHealth()) return true;
        if (affixes != null && survived.add(pe.getUUID())) {
            java.util.List<AffixFx.Eff> eff = affixes.of(p, level);
            boolean last = AffixFx.has(eff, "last_breath");
            int stubborn = AffixFx.percentOf(eff, "stubborn");
            if (last || (stubborn > 0 && pe.getRandom().nextInt(100) < stubborn)) {
                pe.setHealth(1.0f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, pe.getX(), pe.getY() + pe.getBbHeight() * 0.5, pe.getZ(), 20, 0.3, 0.4, 0.3, 0.3);
                announce(level, pe, p.getDisplayName(false).getString() + " hung on with 1 HP!");
                return false;
            }
        }
        if (!p.isPlayerOwned()) return true;
        faint(level, pe, p);
        return false;
    }

    private void faint(ServerLevel level, PokemonEntity pe, Pokemon p) {
        long now = level.getServer().getTickCount();
        downedUntil.put(p.getUuid(), now + cooldownTicks);
        p.setCurrentHealth(p.getMaxHealth());
        ServerPlayer owner = p.getOwnerPlayer();
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(p.getDisplayName(false).getString() + " fainted! It can fight again in " + cooldownTicks / 20 + "s.").withStyle(ChatFormatting.RED));
            level.playSound(null, pe.blockPosition(), SoundEvents.ALLAY_DEATH, SoundSource.NEUTRAL, 1f, 0.8f);
        }
        level.getServer().execute(p::recall);
    }

    @Override
    public void tick(ServerLevel level, long now, Collection<? extends Mob> listed) {
        watched.add(level);
        // Every dungeon Pokemon fights, not only the ones the director spawned: anything in the level that no player owns is hostile.
        java.util.List<Mob> hostiles = new java.util.ArrayList<>(listed);
        java.util.Set<UUID> known = new HashSet<>();
        for (Mob m : listed) known.add(m.getUUID());
        for (Entity e : level.getAllEntities()) {
            if (e instanceof PokemonEntity pe && pe.isAlive() && !pe.getPokemon().isPlayerOwned() && known.add(pe.getUUID())) hostiles.add(pe);
        }
        statuses.computeIfAbsent(level, l -> new RealtimeStatus()).tick(now, new StatusSink(level));
        java.util.List<ServerPlayer> players = new java.util.ArrayList<>();
        for (ServerPlayer pl : level.players()) if (pl.isAlive() && !pl.isSpectator() && !pl.isCreative()) players.add(pl);
        java.util.List<PokemonEntity> allies = new java.util.ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof PokemonEntity pe && pe.isAlive() && pe.getPokemon().isPlayerOwned()) allies.add(pe);
        }
        for (PokemonEntity ally : allies) {
            Pokemon p = ally.getPokemon();
            touched.put(p.getUuid(), p);
            Long until = downedUntil.get(p.getUuid());
            if (until != null) {
                if (now < until) {
                    ServerPlayer owner = p.getOwnerPlayer();
                    if (owner != null) owner.sendSystemMessage(Component.literal(p.getDisplayName(false).getString() + " is recovering for " + (until - now + 19) / 20 + "s.").withStyle(ChatFormatting.GRAY), true);
                    p.recall();
                    continue;
                }
                downedUntil.remove(p.getUuid());
            }
            prepare(ally, level);
            if (ally.isBattling()) continue;
            Mob target = null; double best = ALLY_SIGHT * ALLY_SIGHT;
            for (Mob h : hostiles) {
                if (!h.isAlive() || h.level() != level || h.isInvulnerable()) continue;
                double d = ally.distanceToSqr(h);
                if (d < best) { best = d; target = h; }
            }
            if (target != null) attack(ally, target, now, 2.0 + p.getLevel() * 0.15);
        }
        for (Mob h : hostiles) {
            if (!h.isAlive() || h.level() != level) continue;
            LivingEntity target = null; double best = SIGHT * SIGHT;
            for (ServerPlayer pl : players) {
                double d = h.distanceToSqr(pl);
                if (d < best) { best = d; target = pl; }
            }
            for (PokemonEntity ally : allies) {
                if (downedUntil.containsKey(ally.getPokemon().getUuid()) || ally.isBattling()) continue;
                double d = h.distanceToSqr(ally);
                if (d < best) { best = d; target = ally; }
            }
            if (h instanceof PokemonEntity hp) prepare(hp, level);
            if (target == null) continue;
            int lvl = h instanceof PokemonEntity pe ? pe.getPokemon().getLevel() : 20;
            attack(h, target, now, 2.0 + lvl * 0.1);
        }
    }

    /** First time a Pokemon is seen in a fight: Bracing Entry (a short resistance) and Swift Strike (faster movement). */
    private void prepare(PokemonEntity e, ServerLevel level) {
        if (affixes == null || !seen.add(e.getUUID())) return;
        java.util.List<AffixFx.Eff> eff = affixes.of(e.getPokemon(), level);
        int bracing = AffixFx.percentOf(eff, "bracing_entry");
        if (bracing > 0 && e.getRandom().nextInt(100) < bracing) {
            e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 0));
            announce(level, e, e.getPokemon().getDisplayName(false).getString() + " braced itself!");
        }
        int swift = AffixFx.percentOf(eff, "swift_strike");
        var speed = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (swift > 0 && speed != null) {
            speed.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ascensiondungeons", "swift_strike"), swift / 100.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Ticks between two actions of this attacker: the base gap, shortened by Swift Strike. */
    private int gap(Mob attacker) {
        if (affixes == null || !(attacker instanceof PokemonEntity pe)) return HIT_INTERVAL;
        int swift = AffixFx.percentOf(affixes.of(pe.getPokemon(), (ServerLevel) pe.level()), "swift_strike");
        return Math.max(8, (int) (HIT_INTERVAL / (1.0 + swift / 100.0)));
    }

    /** One damaging action. Every hit goes through {@link #deal}, so affixes and Uniques cannot be skipped. */
    private record Strike(String name, String type, boolean physical, double power, double drain, boolean basic) { }

    /** Picks and fires the best ready move that fits the range, or a recovery move when hurt; false when none is usable. */
    private boolean useMove(PokemonEntity attacker, LivingEntity target, long now, double damage) {
        if (now < nextHit.getOrDefault(attacker.getUUID(), 0L)) return false;
        boolean near = attacker.getBoundingBox().inflate(1.2).intersects(target.getBoundingBox());
        boolean clear = attacker.distanceToSqr(target) <= RANGED * RANGED && attacker.hasLineOfSight(target);
        Pokemon p = attacker.getPokemon();
        ServerLevel level = (ServerLevel) attacker.level();
        Map<String, Long> ready = moveReady.computeIfAbsent(attacker.getUUID(), k -> new HashMap<>());
        double bestScore = 0; Move best = null; Move recovery = null;
        for (Move m : p.getMoveSet().getMoves()) {
            if (now < ready.getOrDefault(m.getName(), 0L)) continue;
            if (RECOVERY.contains(m.getName())) {
                if (attacker.getHealth() < attacker.getMaxHealth() * 0.6) recovery = m;
                continue;
            }
            double power = m.getPower();
            String category = m.getDamageCategory().getName().toLowerCase(java.util.Locale.ROOT);
            if (power <= 0 || category.equals("status")) continue;
            if (category.equals("physical") ? !near : !clear) continue;
            double score = power * effectiveness(m.getType().getName(), target);
            if (score > bestScore) { bestScore = score; best = m; }
        }
        if (recovery != null) {
            halt(attacker);
            nextHit.put(attacker.getUUID(), now + gap(attacker));
            ready.put(recovery.getName(), now + 240);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 14, 0.4, 0.4, 0.4, 0.05);
            level.playSound(null, attacker.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.2f);
            announce(level, attacker, p.getDisplayName(false).getString() + " used " + recovery.getDisplayName().getString() + "!");
            heal(attacker, attacker.getMaxHealth() * 0.5, true);
            return true;
        }
        if (best == null) return false;
        halt(attacker);
        String type = best.getType().getName().toLowerCase(java.util.Locale.ROOT);
        boolean physical = best.getDamageCategory().getName().equalsIgnoreCase("physical");
        Vec3 from = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        nextHit.put(attacker.getUUID(), now + gap(attacker));
        ready.put(best.getName(), now + (long) ((moveBaseSeconds + best.getPower() * moveSecondsPerPower) * 20));
        double accuracy = best.getAccuracy();
        if (accuracy > 1.0) accuracy /= 100.0;
        boolean hits = accuracy <= 0 || attacker.getRandom().nextDouble() < accuracy;
        if (physical) MoveFx.melee(level, type, from, to); else MoveFx.ranged(level, type, from, to);
        double eff = effectiveness(type, target);
        String note = !hits ? " It missed!" : eff >= 2 ? " It's super effective!" : eff <= 0.5 ? " It's not very effective..." : "";
        announce(level, attacker, p.getDisplayName(false).getString() + " used " + best.getDisplayName().getString() + "!" + note);
        if (hits) deal(attacker, target, new Strike(best.getName(), type, physical, best.getPower(), DRAIN.contains(best.getName()) ? 0.5 : 0, false), damage, now);
        return true;
    }

    private static boolean isType(Pokemon p, String type) {
        for (var t : p.getTypes()) if (t.getName().equalsIgnoreCase(type)) return true;
        return false;
    }

    /** Type chart multiplier of an attacking type against a Pokemon target (1.0 against players and other mobs), kept within 0.25..4. */
    private static double effectiveness(String attackType, LivingEntity target) {
        if (!(target instanceof PokemonEntity pe)) return 1.0;
        String type = attackType.toLowerCase(java.util.Locale.ROOT);
        double mult = 1.0;
        for (var t : pe.getPokemon().getTypes()) mult *= MoveFx.effectiveness(type, t.getName().toLowerCase(java.util.Locale.ROOT));
        return Math.max(0.25, Math.min(4.0, mult));
    }

    /** Action-bar line "X used Y!" for the owner of a player's Pokemon, or for every player within 20 blocks of a dungeon one. */
    private static void announce(ServerLevel level, PokemonEntity user, String text) {
        Component line = Component.literal(text).withStyle(ChatFormatting.YELLOW);
        ServerPlayer owner = user.getPokemon().getOwnerPlayer();
        if (owner != null) { owner.displayClientMessage(line, true); return; }
        for (ServerPlayer pl : level.players()) if (pl.distanceToSqr(user) <= 400) pl.displayClientMessage(line, true);
    }

    private boolean hasStatus(LivingEntity e) {
        if (e instanceof PokemonEntity pe && pe.getPokemon().getStatus() != null) return true;
        RealtimeStatus st = statuses.get((ServerLevel) e.level());
        return st != null && st.has(e.getUUID());
    }

    /** Heals a Pokemon, scaled by Last Breath / Titan's Heart / Restorative. */
    private void heal(PokemonEntity e, double amount, boolean fromMove) {
        double scale = affixes == null ? 1.0 : affixes.healing(affixes.of(e.getPokemon(), (ServerLevel) e.level()), fromMove);
        if (amount * scale > 0) e.heal((float) (amount * scale));
    }

    /**
     * The single place real-time damage is dealt (moves, the basic hit): stab, type chart, outgoing and incoming affixes, Uniques,
     * criticals, then the effects of a hit that lands (slow, knockout heal, drain, burn/poison/bleed).
     */
    private boolean deal(Mob attacker, LivingEntity target, Strike s, double base, long now) {
        ServerLevel level = (ServerLevel) attacker.level();
        PokemonEntity ap = attacker instanceof PokemonEntity a ? a : null;
        PokemonEntity tp = target instanceof PokemonEntity t ? t : null;
        Pokemon p = ap == null ? null : ap.getPokemon();
        double eff = effectiveness(s.type(), target);
        double stab = p != null && isType(p, s.type()) ? 1.5 : 1.0;
        double factor = Math.max(0.5, Math.min(3.0, s.power() / 60.0));
        java.util.List<AffixFx.Eff> mine = p != null && affixes != null ? affixes.of(p, level) : java.util.List.of();
        java.util.List<AffixFx.Eff> theirs = tp != null && affixes != null ? affixes.of(tp.getPokemon(), level) : java.util.List.of();
        double affix = 1.0, flat = 0;
        boolean crit = false;
        if (affixes != null) {
            java.util.Set<String> types = new java.util.HashSet<>();
            if (p != null) for (var t : p.getTypes()) types.add(t.getName().toLowerCase(java.util.Locale.ROOT));
            boolean first = ap != null && firstMoveDone.add(ap.getUUID());
            AffixFx.Hit out = new AffixFx.Hit(s.type(), s.physical(), attacker.getHealth() / attacker.getMaxHealth(), hasStatus(attacker),
                    target.getHealth() / target.getMaxHealth(), eff >= 2, first, types, kills.getOrDefault(attacker.getUUID(), 0));
            affix = affixes.outgoing(mine, out);
            if (tp != null) {
                AffixFx.Hit in = new AffixFx.Hit(s.type(), s.physical(), target.getHealth() / target.getMaxHealth(), hasStatus(target),
                        attacker.getHealth() / attacker.getMaxHealth(), eff >= 2, false, java.util.Set.of(), 0);
                affix *= affixes.incoming(theirs, in, firstHitTaken.add(tp.getUUID()));
            }
            int keen = AffixFx.percentOf(mine, "keen_edge");
            if (keen > 0 && attacker.getRandom().nextInt(100) < keen) { crit = true; affix *= 1.5; }
            if (eff >= 2 && AffixFx.has(mine, "titans_heart")) flat = attacker.getMaxHealth() * 0.1;
        }
        float dealt = (float) (base * factor * stab * eff * affix * damageMultiplier * empowered.getOrDefault(attacker.getUUID(), 1.0) + flat);
        boolean hurt = target.hurt(level.damageSources().mobAttack(attacker), dealt);
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        if (crit) level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.2);
        if (hurt && affixes != null) {
            int slow = AffixFx.percentOf(mine, "ensnaring");
            if (slow > 0 && attacker.getRandom().nextInt(100) < slow) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
            if (!target.isAlive()) {
                kills.merge(attacker.getUUID(), 1, Integer::sum);
                if (ap != null) heal(ap, ap.getMaxHealth() * affixes.knockoutHeal(mine), false);
            }
        }
        if (hurt && ap != null && s.drain() > 0) heal(ap, dealt * s.drain(), true);
        if (hurt && ap != null && tp != null && target.isAlive() && !s.basic()) inflict(ap, tp, s, mine, theirs, now, level);
        if (LOG) com.ascensiondungeons.AscensionDungeonsLog.LOG.info("Combat: {} used {} on {} for {} (affix x{}) -> {} (hp {})", attacker.getName().getString(), s.name(), target.getName().getString(), dealt, affix, hurt, target.getHealth());
        return hurt;
    }

    /** Burn (Fire moves), poison (Poison moves) and bleed (Rending, Rupture), unless the target's Wardstone stops it. */
    private void inflict(PokemonEntity ap, PokemonEntity tp, Strike s, java.util.List<AffixFx.Eff> mine, java.util.List<AffixFx.Eff> theirs, long now, ServerLevel level) {
        var rnd = ap.getRandom();
        java.util.List<RealtimeStatus.Kind> kinds = new java.util.ArrayList<>();
        if (s.type().equals("fire") && rnd.nextInt(100) < (LOG ? 100 : 10)) kinds.add(RealtimeStatus.Kind.BURN);
        if (s.type().equals("poison") && rnd.nextInt(100) < (LOG ? 100 : 30)) kinds.add(RealtimeStatus.Kind.POISON);
        boolean rupture = AffixFx.has(mine, "rupture") && s.physical();
        if (rupture || (AffixFx.percentOf(mine, "rending") > 0 && rnd.nextInt(100) < 30)) kinds.add(RealtimeStatus.Kind.BLEED);
        RealtimeStatus ledger = statuses.computeIfAbsent(level, l -> new RealtimeStatus());
        for (RealtimeStatus.Kind kind : kinds) {
            int ward = AffixFx.percentOf(theirs, "wardstone");
            if (ward > 0 && rnd.nextInt(100) < ward) { announce(level, tp, tp.getPokemon().getDisplayName(false).getString() + " shrugged off " + kind.label + "!"); continue; }
            RealtimeStatus.Entry e = ledger.apply(tp.getUUID(), kind, ap.getUUID(), ap.getPokemon().getLevel(), now);
            String affixId = kind == RealtimeStatus.Kind.BURN ? "smoldering" : kind == RealtimeStatus.Kind.POISON ? "venomous" : "rending";
            e.potency = 1.0 + Math.min(100, AffixFx.percentOf(mine, affixId)) / 100.0;
            if (kind == RealtimeStatus.Kind.BURN && AffixFx.has(mine, "ashen_heart")) e.potency *= 1.5;
            if (kind == RealtimeStatus.Kind.POISON && AffixFx.has(mine, "creeping_venom")) { e.growth = 0.2; e.maxGrowth = 0.8; }
            if (kind == RealtimeStatus.Kind.BLEED && rupture) e.stackWeights = new double[] {1.0, 0.8, 0.6};
            Pokemon target = tp.getPokemon();
            try {
                if (target.getStatus() == null && kind == RealtimeStatus.Kind.BURN) target.applyStatus(com.cobblemon.mod.common.api.pokemon.status.Statuses.BURN);
                if (target.getStatus() == null && kind == RealtimeStatus.Kind.POISON) target.applyStatus(com.cobblemon.mod.common.api.pokemon.status.Statuses.POISON);
            } catch (RuntimeException ignored) { }
            announce(level, tp, target.getDisplayName(false).getString() + " is " + (kind == RealtimeStatus.Kind.BLEED ? "bleeding" : kind == RealtimeStatus.Kind.BURN ? "burned" : "poisoned") + "!");
        }
    }

    /** One tick of burn/poison/bleed on one target: damage is a share of max HP, never more than a normal hit's budget. */
    private final class StatusSink implements RealtimeStatus.Sink {
        private final ServerLevel level;
        StatusSink(ServerLevel level) { this.level = level; }

        @Override
        public boolean tick(UUID id, RealtimeStatus.Entry e) {
            if (!(level.getEntity(id) instanceof LivingEntity t) || !t.isAlive()) return false;
            double dmg = Math.min(t.getMaxHealth() * e.fraction(), 2.0 + e.applierLevel * 0.15);
            t.hurt(level.damageSources().magic(), (float) dmg);
            if (LOG) com.ascensiondungeons.AscensionDungeonsLog.LOG.info("Status: {} tick on {} for {}", e.kind, t.getName().getString(), dmg);
            var particle = e.kind == RealtimeStatus.Kind.BURN ? net.minecraft.core.particles.ParticleTypes.FLAME
                    : e.kind == RealtimeStatus.Kind.POISON ? net.minecraft.core.particles.ParticleTypes.WITCH : net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR;
            level.sendParticles(particle, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            return t.isAlive();
        }

        @Override
        public void ended(UUID id, RealtimeStatus.Entry e) {
            if (level.getEntity(id) instanceof PokemonEntity pe && e.kind != RealtimeStatus.Kind.BLEED) {
                RealtimeStatus st = statuses.get(level);
                if (st == null || (!st.has(id, RealtimeStatus.Kind.BURN) && !st.has(id, RealtimeStatus.Kind.POISON))) {
                    try { pe.getPokemon().setStatus(null); } catch (RuntimeException ignored) { }
                }
            }
        }
    }

    /** Cobblemon's own brain wanders, rests and follows its owner; push it toward the target on every combat tick so it cannot idle. */
    private static void drive(Mob mob, LivingEntity target, double speed) {
        if (mob instanceof PokemonEntity pe && pe.getPose() == net.minecraft.world.entity.Pose.SLEEPING) pe.setPose(net.minecraft.world.entity.Pose.STANDING);
        try {
            var brain = mob.getBrain();
            brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET, new net.minecraft.world.entity.ai.memory.WalkTarget(target, (float) speed, 1));
            brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET, new net.minecraft.world.entity.ai.behavior.EntityTracker(target, true));
        } catch (RuntimeException ignored) { }
        mob.getNavigation().moveTo(target, speed);
    }

    /** Stops moving to fire a ranged move or a heal. */
    private static void halt(Mob mob) {
        try { mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET); } catch (RuntimeException ignored) { }
        mob.getNavigation().stop();
    }

    private void attack(Mob attacker, LivingEntity target, long now, double damage) {
        attacker.setTarget(target);
        attacker.getLookControl().setLookAt(target);
        if (attacker instanceof PokemonEntity pe && useMove(pe, target, now, damage)) return;
        if (!attacker.getBoundingBox().inflate(1.2).intersects(target.getBoundingBox())) {
            drive(attacker, target, 1.2);
            return;
        }
        if (now < nextHit.getOrDefault(attacker.getUUID(), 0L)) return;
        nextHit.put(attacker.getUUID(), now + gap(attacker));
        // The plain hit is a 40-power strike of the attacker's own type (a non-Pokemon mob: 60-power normal, no stab), so it takes the same affix path.
        String type = "normal";
        double power = 60;
        if (attacker instanceof PokemonEntity pe) {
            for (var t : pe.getPokemon().getTypes()) { type = t.getName().toLowerCase(java.util.Locale.ROOT); break; }
            power = 40;
        }
        deal(attacker, target, new Strike("tackle", type, true, power, 0, true), damage, now);
    }

    @Override
    public String sendOut(ServerPlayer player) {
        var party = com.cobblemon.mod.common.util.PlayerExtensionsKt.party(player);
        Pokemon p = party.get(0);
        if (p == null) return "Your party is empty.";
        if (!(player.level() instanceof ServerLevel level)) return "Not in a server level.";
        PokemonEntity pe = p.sendOut(level, player.position().add(1.5, 0, 0), null, e -> kotlin.Unit.INSTANCE);
        return pe == null ? "Could not send out " + p.getSpecies().getName() : "Sent out " + p.getSpecies().getName();
    }

    @Override
    public String testInflict(ServerPlayer player, String kind) {
        if (!(player.level() instanceof ServerLevel level)) return "Not in a server level.";
        RealtimeStatus.Kind k;
        try { k = RealtimeStatus.Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException e) { return "Use burn, poison or bleed."; }
        PokemonEntity nearest = null; double best = 400;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof PokemonEntity pe && pe.isAlive() && !pe.getPokemon().isPlayerOwned() && pe.distanceToSqr(player) < best) { best = pe.distanceToSqr(player); nearest = pe; }
        }
        if (nearest == null) return "No dungeon Pokemon within 20 blocks.";
        statuses.computeIfAbsent(level, l -> new RealtimeStatus()).apply(nearest.getUUID(), k, player.getUUID(), 40, level.getServer().getTickCount());
        return "Applied " + k.label + " to " + nearest.getPokemon().getSpecies().getName();
    }

    private static final boolean LOG = Boolean.getBoolean("ascensiondungeons.debug");

    @Override
    public void release(ServerLevel level) {
        watched.remove(level);
        if (!watched.isEmpty()) return;
        for (Pokemon p : touched.values()) {
            try { p.heal(); p.setStatus(null); } catch (RuntimeException ignored) { }
        }
        touched.clear();
        downedUntil.clear();
        nextHit.clear();
        moveReady.clear();
        firstMoveDone.clear();
        firstHitTaken.clear();
        kills.clear();
        if (affixes != null) affixes.clear();
        seen.clear();
        survived.clear();
        statuses.clear();
    }
}
