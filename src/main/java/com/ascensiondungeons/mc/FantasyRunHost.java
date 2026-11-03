package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunConfig;
import com.ascensiondungeons.run.RunHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

/**
 * RunHost backed by one Fantasy temporary level per run. Everything here encodes a spike finding:
 * delete() never completes while forced chunks or players remain, so releaseLevel() clears both; chunk generation is
 * requested a few chunks per tick and polled, never awaited; each level folder gets a marker so only our orphans are swept.
 */
public final class FantasyRunHost implements RunHost {
    static final String MARKER = "ascensiondungeons.run";

    private record ReturnPoint(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {}

    private static final class RunLevel {
        final RuntimeWorldHandle handle;
        final ServerLevel level;
        final List<ChunkPos> prewarm;
        final com.ascensiondungeons.gen.DistrictGrid grid;
        final com.ascensiondungeons.gen.type.TypePlan typePlan;
        CinderforgeChunkGenerator cf;
        AuthoredChunkGenerator au;
        int requested;
        boolean prewarmReleased;
        final Map<UUID, ReturnPoint> returns = new HashMap<>();

        RunLevel(RuntimeWorldHandle handle, List<ChunkPos> prewarm, com.ascensiondungeons.gen.DistrictGrid grid, com.ascensiondungeons.gen.type.TypePlan typePlan) {
            this.handle = handle;
            this.grid = grid;
            this.typePlan = typePlan;
            this.level = handle.asWorld();
            this.prewarm = prewarm;
        }
    }

    /** A run's live level and district grid, for systems that act inside it (raid arenas). */
    public record LevelView(ServerLevel level, com.ascensiondungeons.gen.DistrictGrid grid) {}
    public record TypeView(ServerLevel level, com.ascensiondungeons.gen.type.TypePlan plan) {}
    /** A Cinderforge run's live level and its generator (which owns the layout). */
    public record CinderforgeView(ServerLevel level, CinderforgeChunkGenerator generator) {}
    /** An authored-dungeon run's live level and its generator (which owns the layout). */
    public record AuthoredView(ServerLevel level, AuthoredChunkGenerator generator) {}
    public java.util.Optional<AuthoredView> authoredView(UUID id) {
        RunLevel r = levels.get(id);
        return r == null || r.au == null ? java.util.Optional.empty() : java.util.Optional.of(new AuthoredView(r.level, r.au));
    }
    public java.util.Optional<CinderforgeView> cinderforgeView(UUID id) {
        RunLevel r = levels.get(id);
        return r == null || r.cf == null ? java.util.Optional.empty() : java.util.Optional.of(new CinderforgeView(r.level, r.cf));
    }
    public java.util.Optional<TypeView> typeView(UUID id) {
        RunLevel r = levels.get(id);
        return r == null || r.typePlan == null ? java.util.Optional.empty() : java.util.Optional.of(new TypeView(r.level, r.typePlan));
    }

    private final MinecraftServer server;
    private final RunConfig cfg;
    private final Map<UUID, RunLevel> levels = new HashMap<>();
    private java.util.function.Predicate<Run> bossHook = r -> false;
    public void onBoss(java.util.function.Predicate<Run> hook) { bossHook = hook; }
    @Override public boolean beginBoss(Run run) { return bossHook.test(run); }
    private java.util.function.Consumer<UUID> releaseHook = id -> { };
    private AuthoredBridges.Ascension ascension;
    public void ascension(AuthoredBridges.Ascension a) { this.ascension = a; }

    /** Called with the run id at the start of {@link #releaseLevel}, before anyone is moved out. */
    public void onRelease(java.util.function.Consumer<UUID> hook) { this.releaseHook = hook; }

    public java.util.Optional<LevelView> levelView(UUID runId) {
        RunLevel rl = levels.get(runId);
        return rl == null || rl.grid == null ? java.util.Optional.empty() : java.util.Optional.of(new LevelView(rl.level, rl.grid));
    }

    public FantasyRunHost(MinecraftServer server, RunConfig cfg) {
        this.server = server;
        this.cfg = cfg;
    }

    @Override
    public void openLevel(Run run) {
        if ("cinderforge".equals(run.dungeonType())) { openCinderforge(run); return; }
        if (com.ascensiondungeons.gen.authored.AuthoredDungeons.isAuthored(run.dungeonType())) { openAuthored(run); return; }
        var voidBiome = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getOrThrow(net.minecraft.world.level.biome.Biomes.THE_VOID);
        DungeonChunkGenerator legacy = run.dungeonType() == null ? new DungeonChunkGenerator(voidBiome, run.seed(), cfg.districtRadius) : null;
        TypeDungeonChunkGenerator typed = run.dungeonType() != null ? new TypeDungeonChunkGenerator(voidBiome, run.seed()) : null;
        net.minecraft.world.level.chunk.ChunkGenerator generator = typed == null ? legacy : typed;
        int[] entry = typed == null ? legacy.entry() : typed.plan().entry();
        List<ChunkPos> prewarm = prewarmOrder(entry, cfg.prewarmRadiusChunks);
        if (typed != null) for (ChunkPos chunk : prewarmOrder(typed.plan().boss(), 2)) if (!prewarm.contains(chunk)) prewarm.add(chunk);
        RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
                .setGenerator(generator)
                .setSeed(run.seed());
        RuntimeWorldHandle handle = Fantasy.get(server).openTemporaryWorld(config);
        RunLevel rl = new RunLevel(handle, prewarm, legacy == null ? null : legacy.grid(), typed == null ? null : typed.plan());
        levels.put(run.id(), rl);
        rl.level.getWorldBorder().setCenter(typed == null ? -0.5 : 192, typed == null ? -0.5 : 96);
        // The border never reaches past the districts; the config radius can only make it smaller.
        rl.level.getWorldBorder().setSize(typed == null ? Math.min(cfg.borderRadius * 2.0, legacy.grid().extent()) : 384);
        writeMarker(rl.level, run);
    }

    private void openAuthored(Run run) {
        var layout = com.ascensiondungeons.gen.authored.AuthoredLayout.get(run.dungeonType());
        var biome = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                        net.minecraft.resources.ResourceLocation.parse(layout.data().biome)));
        AuthoredChunkGenerator generator = new AuthoredChunkGenerator(biome, run.dungeonType(), run.seed());
        BlockPos entry = generator.worldEntry();
        List<ChunkPos> prewarm = prewarmOrder(new int[] {entry.getX(), entry.getY(), entry.getZ()}, cfg.prewarmRadiusChunks);
        RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
                .setGenerator(generator)
                .setSeed(run.seed())
                .setMirrorOverworldDifficulty(true)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING, false)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK, false)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING, false)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT, false)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE, false);
        RuntimeWorldHandle handle = Fantasy.get(server).openTemporaryWorld(config);
        RunLevel rl = new RunLevel(handle, prewarm, null, null);
        rl.au = generator;
        levels.put(run.id(), rl);
        int tier = com.ascensiondungeons.gen.authored.Jx.i(layout.data().pokemon, "tier", 0);
        if (ascension != null && tier > 0) ascension.openZone(rl.level.dimension().location().toString(), tier);
        rl.level.getWorldBorder().setCenter(generator.centreX(), generator.centreZ());
        rl.level.getWorldBorder().setSize(generator.borderSize());
        writeMarker(rl.level, run);
    }

    private void openCinderforge(Run run) {
        var biome = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getOrThrow(net.minecraft.world.level.biome.Biomes.NETHER_WASTES);
        CinderforgeChunkGenerator generator = new CinderforgeChunkGenerator(biome, run.seed());
        BlockPos entry = generator.worldEntry();
        List<ChunkPos> prewarm = prewarmOrder(new int[] {entry.getX(), entry.getY(), entry.getZ()}, cfg.prewarmRadiusChunks);
        // Rules go through the level config so they belong to this level alone (never the overworld's), and the level follows
        // the server's difficulty instead of Fantasy's fixed default. Authored encounters only: no natural spawns, no fire
        // spreading from lava, no mob griefing, no day cycle or weather.
        RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
                .setGenerator(generator)
                .setSeed(run.seed())
                .setMirrorOverworldDifficulty(true)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING, false)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK, false)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING, false)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT, false)
                .setGameRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE, false);
        RuntimeWorldHandle handle = Fantasy.get(server).openTemporaryWorld(config);
        RunLevel rl = new RunLevel(handle, prewarm, null, null);
        rl.cf = generator;
        levels.put(run.id(), rl);
        rl.level.getWorldBorder().setCenter(generator.centreX(), generator.centreZ());
        rl.level.getWorldBorder().setSize(generator.borderSize());
        writeMarker(rl.level, run);
    }

    @Override
    public int requestChunks(Run run, int max) {
        RunLevel rl = levels.get(run.id());
        for (int i = 0; i < max && rl.requested < rl.prewarm.size(); i++) {
            ChunkPos p = rl.prewarm.get(rl.requested++);
            rl.level.setChunkForced(p.x, p.z, true);
        }
        return rl.prewarm.size() - rl.requested;
    }

    @Override
    public boolean chunksReady(Run run) {
        RunLevel rl = levels.get(run.id());
        for (ChunkPos p : rl.prewarm) {
            if (rl.level.getChunkSource().getChunkNow(p.x, p.z) == null) return false;
        }
        return true;
    }

    @Override
    public boolean isOnline(UUID player) { return server.getPlayerList().getPlayer(player) != null; }

    @Override
    public boolean isInside(Run run, UUID player) {
        RunLevel rl = levels.get(run.id());
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        return rl != null && p != null && p.serverLevel() == rl.level;
    }

    @Override
    public boolean enter(Run run, UUID player) {
        RunLevel rl = levels.get(run.id());
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (rl == null || p == null) return false;
        if (p.serverLevel() != rl.level) {
            rl.returns.putIfAbsent(player, new ReturnPoint(p.serverLevel().dimension(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
            int[] e = rl.au != null ? new int[] {rl.au.worldEntry().getX(), rl.au.worldEntry().getY(), rl.au.worldEntry().getZ()} : rl.cf != null ? new int[] {rl.cf.worldEntry().getX(), rl.cf.worldEntry().getY(), rl.cf.worldEntry().getZ()}
                    : rl.typePlan == null ? rl.grid.entry() : run.bossPhase() ? rl.typePlan.staging() : rl.typePlan.entry();
            BlockPos spawn = new BlockPos(e[0], e[1], e[2]);
            // Cinderforge starts facing +Z (south), toward the gate.
            p.teleportTo(rl.level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, rl.cf != null || rl.au != null ? 0f : p.getYRot(), rl.cf != null || rl.au != null ? 0f : p.getXRot());
            // The client is told about the border on dimension change, but resend so a missed packet cannot hide it.
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(rl.level.getWorldBorder()));
            com.ascensiondungeons.AscensionDungeonsLog.LOG.info("Run {}: {} entered {} (border size {}, center {},{})",
                    run.id(), p.getGameProfile().getName(), rl.level.dimension().location(),
                    rl.level.getWorldBorder().getSize(), rl.level.getWorldBorder().getCenterX(), rl.level.getWorldBorder().getCenterZ());
        }
        // The player's own chunk tickets take over from the prewarm ones.
        releasePrewarm(rl);
        return true;
    }

    @Override
    public boolean exit(Run run, UUID player) {
        RunLevel rl = levels.get(run.id());
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p == null) return false;
        if (rl == null || p.serverLevel() != rl.level) return true;
        returnTo(p, rl.returns.get(player));
        return p.serverLevel() != rl.level;
    }

    @Override
    public void releaseLevel(Run run) {
        RunLevel rl = levels.get(run.id());
        if (rl == null) return;
        releaseHook.accept(run.id());
        if (ascension != null && rl.au != null) ascension.closeZone(rl.level.dimension().location().toString());
        for (long packed : rl.level.getForcedChunks().toLongArray()) {
            rl.level.setChunkForced(ChunkPos.getX(packed), ChunkPos.getZ(packed), false);
        }
        for (ServerPlayer p : new ArrayList<>(rl.level.players())) returnTo(p, rl.returns.get(p.getUUID()));
    }

    @Override
    public void deleteLevel(Run run) {
        RunLevel rl = levels.get(run.id());
        if (rl != null) {
            if (ascension != null && rl.au != null) ascension.closeZone(rl.level.dimension().location().toString());
            rl.handle.delete();
        }
    }

    @Override
    public boolean isLevelGone(Run run) {
        RunLevel rl = levels.get(run.id());
        if (rl == null) return true;
        if (server.getLevel(rl.level.dimension()) != null) return false;
        levels.remove(run.id());
        return true;
    }

    @Override
    public void message(UUID player, String text) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) p.sendSystemMessage(Component.literal(text));
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private void returnTo(ServerPlayer p, ReturnPoint rp) {
        ServerLevel target = rp == null ? null : server.getLevel(rp.dimension());
        if (target == null) {
            // Return point lost (offline at the time, or its dimension is gone): fall back to the overworld spawn.
            ServerLevel home = server.overworld();
            BlockPos s = home.getSharedSpawnPos();
            p.teleportTo(home, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
        } else {
            p.teleportTo(target, rp.x(), rp.y(), rp.z(), rp.yRot(), rp.xRot());
        }
    }

    private static void releasePrewarm(RunLevel rl) {
        if (rl.prewarmReleased) return;
        rl.prewarmReleased = true;
        for (ChunkPos p : rl.prewarm) {
            // Keep the isolated boss arrival volume ready until the run is released.
            if (rl.typePlan != null && p.x >= 20 && p.x <= 24 && p.z >= 0 && p.z <= 4) continue;
            rl.level.setChunkForced(p.x, p.z, false);
        }
    }

    /** Chunks nearest the entry point first, so the spawn area is ready before the outer ring. */
    static List<ChunkPos> prewarmOrder(int[] entry, int radius) {
        int cx = Math.floorDiv(entry[0], 16), cz = Math.floorDiv(entry[2], 16);
        List<ChunkPos> out = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) out.add(new ChunkPos(cx + x, cz + z));
        out.sort(Comparator.comparingInt((ChunkPos p) -> (p.x - cx) * (p.x - cx) + (p.z - cz) * (p.z - cz)));
        return out;
    }

    private void writeMarker(ServerLevel level, Run run) {
        try {
            Path dir = levelFolder(server, level.dimension());
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(MARKER), run.id().toString());
        } catch (IOException e) {
            AscensionDungeonsLog.LOG.warn("Could not write orphan marker for run {}", run.id(), e);
        }
    }

    static Path levelFolder(MinecraftServer server, ResourceKey<Level> key) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("dimensions").resolve(key.location().getNamespace()).resolve(key.location().getPath());
    }
}
