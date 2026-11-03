package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.api.RaidEntrance;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import com.ascensiondungeons.gen.ArenaSites;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunConfig;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.RunState;
import com.cobbleraids.api.encounter.CobbleRaidsEncounters;
import com.cobbleraids.api.encounter.EncounterPolicy;
import com.cobbleraids.api.encounter.EncounterRequest;
import com.cobbleraids.api.encounter.EncounterResult;
import com.cobbleraids.api.encounter.StartResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Raid markers: each district's arena has an activation plate at its doorway and a boss anchor at its centre. Standing
 * on the plate for {@code raidActivationSeconds} starts a CobbleRaids owned encounter for the run members who are
 * near the arena (up to 4). Victory clears that arena for the run; a defeat reopens it after a delay.
 *
 * <p>Only loaded when CobbleRaids is installed; the mod core sees it through {@link ArenaDirector}.
 */
public final class RaidArenas implements ArenaDirector {
    private static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("ascensiondungeons", "run");
    private static final int CHECK_EVERY = 10;
    private static final double PLATE_RADIUS = 2.5;
    private static final double GATHER_RADIUS = 28;

    private static final class Arena {
        final ArenaSites.Site site;
        final ResourceLocation boss;
        boolean cleared;
        UUID encounter;
        long retryAt;
        int held;
        Pending pending;

        Arena(ArenaSites.Site site, ResourceLocation boss) {
            this.site = site;
            this.boss = boss;
        }
    }

    private record Pending(long since, UUID trigger, List<UUID> party, RaidEntrance.Animation animation) {}
    private final RaidEntrance entrance;
    private final MinecraftServer server;
    private final RunConfig cfg;
    private final Supplier<RunManager> manager;
    private final FantasyRunHost host;
    private final Map<UUID, List<Arena>> arenas = new HashMap<>();

    public RaidArenas(MinecraftServer server, RunConfig cfg, Supplier<RunManager> manager, FantasyRunHost host) {
        this.server = server;
        this.cfg = cfg;
        this.manager = manager;
        this.host = host;
        List<RaidEntrance> hooks = FabricLoader.getInstance().getEntrypoints("ascensiondungeons:raid_entrance", RaidEntrance.class);
        if (hooks.size() > 1) throw new IllegalStateException("Only one raid entrance provider may be installed");
        entrance = hooks.isEmpty() ? null : hooks.getFirst();
    }

    @Override
    public void tick(long now) {
        RunManager m = manager.get();
        if (m == null) return;
        for (Run run : m.runs()) {
            if (run.state() != RunState.ACTIVE) {
                if (arenas.containsKey(run.id())) runReleased(run.id());
                continue;
            }
            var view = host.levelView(run.id());
            if (view.isEmpty()) { runReleased(run.id()); continue; }
            List<Arena> list = arenas.computeIfAbsent(run.id(), id -> create(run, view.get()));
            if (now % CHECK_EVERY != 0 && list.stream().noneMatch(a -> a.pending != null)) continue;
            List<ServerPlayer> inside = new ArrayList<>();
            for (UUID id : run.members().keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && p.isAlive() && !p.isSpectator() && run.members().get(id).presence() == Run.Presence.INSIDE && p.serverLevel() == view.get().level()) inside.add(p);
            }
            for (Arena a : list) {
                if (a.pending != null) advance(now, view.get().level(), a, inside);
                else if (now % CHECK_EVERY == 0) tickArena(now, view.get().level(), a, inside, run.id());
            }
        }
        for (UUID id : new ArrayList<>(arenas.keySet())) {
            if (m.runs().stream().noneMatch(r -> r.id().equals(id))) runReleased(id);
        }
    }

    private List<Arena> create(Run run, FantasyRunHost.LevelView view) {
        Random rnd = new Random(run.seed() ^ 0x5241494CL);
        List<Arena> out = new ArrayList<>();
        for (ArenaSites.Site s : ArenaSites.of(view.grid())) {
            List<String> pool = cfg.raidBosses.get(s.biome().name().toLowerCase(Locale.ROOT));
            if (pool == null || pool.isEmpty()) continue;
            ResourceLocation id = ResourceLocation.tryParse(pool.get(rnd.nextInt(pool.size())));
            if (id != null) out.add(new Arena(s, id));
        }
        return out;
    }

    private void tickArena(long now, ServerLevel level, Arena a, List<ServerPlayer> inside, UUID runId) {
        if (a.cleared || a.encounter != null || now < a.retryAt) return;
        Vec3 plate = new Vec3(a.site.activation()[0] + 0.5, a.site.activation()[1], a.site.activation()[2] + 0.5);
        if (now % 20 == 0) ring(level, plate);
        ServerPlayer trigger = inside.stream()
                .filter(p -> Math.hypot(p.getX() - plate.x, p.getZ() - plate.z) <= PLATE_RADIUS && Math.abs(p.getY() - plate.y) < 3)
                .findFirst().orElse(null);
        if (trigger == null) {
            a.held = 0;
            return;
        }
        int needed = cfg.raidActivationSeconds * 20 / CHECK_EVERY;
        if (++a.held <= needed) {
            if (a.held % (20 / CHECK_EVERY) == 1) {
                int left = Math.max(1, (needed - a.held + 1) * CHECK_EVERY / 20);
                trigger.displayClientMessage(Component.literal("Raid begins in " + left + "s - gather your party in the arena"), true);
            }
            return;
        }
        a.held = 0;
        Vec3 anchor = anchor(a);
        List<ServerPlayer> party = party(inside, trigger, anchor);
        if (!level.hasChunkAt(BlockPos.containing(anchor))) return;
        if (entrance != null) {
            try {
                RaidEntrance.Animation animation = entrance.begin(new RaidEntrance.Context(runId, level, anchor,
                        a.site.biome().name(), party.stream().map(ServerPlayer::getUUID).toList()));
                if (animation != null) {
                    a.pending = new Pending(now, trigger.getUUID(), party.stream().map(ServerPlayer::getUUID).toList(), animation);
                    for (ServerPlayer p : party) p.sendSystemMessage(Component.literal("The arena is awakening..."));
                    return;
                }
            } catch (RuntimeException e) {
                AscensionDungeonsLog.LOG.warn("Entrance creation failed; starting raid without decoration", e);
            }
        }
        start(now, level, a, trigger, party);
    }

    private void start(long now, ServerLevel level, Arena a, ServerPlayer trigger, List<ServerPlayer> inside) {
        Vec3 anchor = anchor(a);
        List<ServerPlayer> party = party(inside, trigger, anchor);
        UUID id = UUID.randomUUID();
        EncounterPolicy policy = EncounterPolicy.none().withRaidRewards(cfg.raidRewards).withCatchable(cfg.raidCatchable);
        StartResult r;
        try {
            r = CobbleRaidsEncounters.start(new EncounterRequest(OWNER, id, party, a.boss, level, anchor,
                    cfg.raidBossLevel, OptionalLong.empty(), policy), result -> ended(a, result));
        } catch (RuntimeException e) {
            AscensionDungeonsLog.LOG.error("Raid start failed for {}", a.boss, e);
            a.retryAt = now + 200;
            return;
        }
        if (r instanceof StartResult.Refused refused) {
            AscensionDungeonsLog.LOG.warn("CobbleRaids refused arena raid {}: {}", a.boss, refused.reason());
            trigger.sendSystemMessage(Component.literal("The arena does not answer (" + refused.reason() + ")."));
            a.retryAt = now + 200;
            return;
        }
        a.encounter = id;
        for (ServerPlayer p : party) p.sendSystemMessage(Component.literal("The arena awakens: " + a.boss.getPath() + "!"));
    }

    private static Vec3 anchor(Arena a) {
        return new Vec3(a.site.anchor()[0] + 0.5, a.site.anchor()[1], a.site.anchor()[2] + 0.5);
    }

    private static List<ServerPlayer> party(List<ServerPlayer> inside, ServerPlayer trigger, Vec3 anchor) {
        return inside.stream().filter(p -> p == trigger || p.position().distanceTo(anchor) <= GATHER_RADIUS)
                .sorted(Comparator.comparingDouble((ServerPlayer p) -> p == trigger ? -1 : p.position().distanceTo(anchor)))
                .limit(EncounterRequest.MAX_PLAYERS).toList();
    }

    private void advance(long now, ServerLevel level, Arena a, List<ServerPlayer> inside) {
        Pending pending = a.pending;
        Vec3 anchor = anchor(a);
        List<ServerPlayer> party = inside.stream().filter(p -> pending.party().contains(p.getUUID()))
                .filter(p -> p.position().distanceTo(anchor) <= GATHER_RADIUS).toList();
        ServerPlayer trigger = party.stream().filter(p -> p.getUUID().equals(pending.trigger())).findFirst().orElse(null);
        long elapsed = now - pending.since();
        if (trigger == null || party.size() != pending.party().size() || !level.hasChunkAt(BlockPos.containing(anchor))
                || elapsed < 0 || elapsed > 100) {
            cancel(a);
            a.retryAt = now + 100;
            for (ServerPlayer p : party) p.sendSystemMessage(Component.literal("The summoning fades. Gather your party and try again."));
            return;
        }
        try {
            if (elapsed < 80) pending.animation().tick((int) elapsed);
            else {
                // Remove the reservation before commit so this arena can never commit the same sequence twice.
                a.pending = null;
                try {
                    start(now, level, a, trigger, party);
                    if (a.encounter != null) pending.animation().reveal();
                } finally { close(pending); }
            }
        } catch (RuntimeException e) {
            AscensionDungeonsLog.LOG.warn("Entrance failed for {}", a.boss, e);
            cancel(a);
            a.retryAt = now + 100;
        }
    }

    private static void close(Pending pending) {
        try { pending.animation().close(); }
        catch (RuntimeException e) { AscensionDungeonsLog.LOG.warn("Entrance cleanup failed", e); }
    }

    private static void cancel(Arena a) {
        Pending pending = a.pending;
        a.pending = null;
        if (pending != null) close(pending);
    }

    private void ended(Arena a, EncounterResult result) {
        a.encounter = null;
        long now = server.getTickCount();
        switch (result.outcome()) {
            case VICTORY -> {
                a.cleared = true;
                tell(result, "The arena falls silent. Raid cleared!");
            }
            case DEFEAT -> {
                a.retryAt = now + cfg.raidRetryTicks;
                tell(result, "The raid was lost. The arena will stir again soon.");
            }
            case ABORTED -> a.retryAt = now + 100;
        }
    }

    private void tell(EncounterResult result, String text) {
        for (UUID id : result.contribution().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(Component.literal(text));
        }
    }

    @Override
    public void runReleased(UUID runId) {
        List<Arena> list = arenas.remove(runId);
        if (list == null) return;
        for (Arena a : list) {
            cancel(a);
            if (a.encounter == null) continue;
            UUID id = a.encounter;
            a.encounter = null;
            try {
                CobbleRaidsEncounters.abort(id);
            } catch (RuntimeException e) {
                AscensionDungeonsLog.LOG.warn("Could not abort raid {}", id, e);
            }
        }
    }

    private static void ring(ServerLevel level, Vec3 c) {
        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI / 6;
            level.sendParticles(ParticleTypes.END_ROD, c.x + 1.6 * Math.cos(ang), c.y + 0.3, c.z + 1.6 * Math.sin(ang), 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.2, c.z, 3, 0.3, 0.1, 0.3, 0.01);
    }
}
