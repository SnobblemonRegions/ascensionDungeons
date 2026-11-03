package com.ascensiondungeons.spike;

import com.ascensiondungeons.AscensionDungeonsLog;

import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

/** Spike benchmark: N concurrent Fantasy levels, one fake "party" each. Env DUNGEONSPIKE_BENCH=N, optional BENCH_RADIUS (chunks). */
public final class Bench {
    private enum Phase { WARM, BASE, OPEN, SPAWN, STEADY, DELETE, DONE }

    private final int n = Integer.parseInt(System.getenv("DUNGEONSPIKE_BENCH"));
    private final int radius = Integer.parseInt(System.getenv().getOrDefault("BENCH_RADIUS", "5"));
    private final List<RuntimeWorldHandle> handles = new ArrayList<>();
    private final List<Long> tickNanos = new ArrayList<>();
    private Phase phase = Phase.WARM;
    private int phaseTicks;
    private long tickStart;
    private int baseLevels;
    private final List<Long> spawnMs = new ArrayList<>();
    private int spawnResults;

    public static void install() {
        Bench b = new Bench();
        ServerTickEvents.START_SERVER_TICK.register(s -> b.tickStart = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(b::onTick);
    }

    private void log(String f, Object... a) { AscensionDungeonsLog.LOG.info("BENCH " + f, a); }

    private void onTick(MinecraftServer server) {
        long dt = System.nanoTime() - tickStart;
        phaseTicks++;
        switch (phase) {
            case WARM -> { if (phaseTicks >= 300) next(Phase.BASE); }
            case BASE -> {
                tickNanos.add(dt);
                if (phaseTicks >= 200) { report("baseline(idle)", server); baseLevels = count(server); next(Phase.OPEN); }
            }
            case OPEN -> {
                if (handles.size() < n) {
                    long t = System.nanoTime();
                    RuntimeWorldConfig cfg = new RuntimeWorldConfig()
                            .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
                            .setGenerator(server.overworld().getChunkSource().getGenerator())
                            .setSeed(1000L + handles.size());
                    RuntimeWorldHandle h = Fantasy.get(server).openTemporaryWorld(cfg);
                    ServerLevel level = h.asWorld();
                    long opened = System.nanoTime();
                    for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                        level.setChunkForced(x, z, true);
                        level.getChunk(x, z);
                    }
                    handles.add(h);
                    log("level {} open={}ms chunks({}x{})={}ms tick={}ms", handles.size(),
                            (opened - t) / 1_000_000, 2 * radius + 1, 2 * radius + 1, (System.nanoTime() - opened) / 1_000_000, dt / 1_000_000);
                } else if (phaseTicks > n + 5) next(Phase.SPAWN);
            }
            case SPAWN -> {
                if (phaseTicks <= n) {
                    long t = System.nanoTime();
                    int r = spawn(handles.get(phaseTicks - 1).asWorld());
                    spawnMs.add((System.nanoTime() - t) / 1_000_000);
                    spawnResults += r;
                } else if (phaseTicks == n + 1) {
                    long[] m = spawnMs.stream().mapToLong(Long::longValue).sorted().toArray();
                    log("spawn: {} results over {} levels (one level per tick); per-level 4-call cost ms: p50={} max={}",
                            spawnResults, n, m[m.length / 2], m[m.length - 1]);
                } else if (phaseTicks > n + 5) next(Phase.STEADY);
            }
            case STEADY -> {
                tickNanos.add(dt);
                if (phaseTicks >= 600) {
                    report("steady(" + n + " levels)", server);
                    long mobs = 0, chunks = 0;
                    for (RuntimeWorldHandle h : handles) {
                        for (var e : h.asWorld().getAllEntities()) if (e instanceof PokemonEntity) mobs++;
                        chunks += h.asWorld().getChunkSource().getLoadedChunksCount();
                    }
                    Runtime rt = Runtime.getRuntime();
                    log("pokemon={} loadedChunks={} heapUsedMB={}", mobs, chunks, (rt.totalMemory() - rt.freeMemory()) >> 20);
                    long t = System.nanoTime();
                    if (System.getenv("BENCH_RELEASE") != null) {
                        // Production-like teardown: release forced chunks and the fake participant before delete().
                        for (RuntimeWorldHandle h : handles) {
                            ServerLevel lv = h.asWorld();
                            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) lv.setChunkForced(x, z, false);
                            lv.players().forEach(pl -> pl.discard());
                        }
                        log("released forced chunks + fake players");
                    }
                    handles.forEach(RuntimeWorldHandle::delete);
                    log("delete() x{} returned in {}ms", n, (System.nanoTime() - t) / 1_000_000);
                    next(Phase.DELETE);
                }
            }
            case DELETE -> {
                tickNanos.add(dt);
                if (phaseTicks % 50 == 1) {
                    long chunks = 0;
                    for (RuntimeWorldHandle h : handles) chunks += h.asWorld().getChunkSource().getLoadedChunksCount();
                    log("delete t={} levels={} dungeonLoadedChunks={}", phaseTicks, count(server), chunks);
                }
                if (count(server) <= baseLevels || phaseTicks >= 400) {
                    System.gc();
                    Runtime rt = Runtime.getRuntime();
                    log("heapAfterGcMB={}", (rt.totalMemory() - rt.freeMemory()) >> 20);
                    log("levels back to {} (base {}) after {} ticks", count(server), baseLevels, phaseTicks);
                    report("delete-phase", server);
                    next(Phase.DONE);
                    server.halt(false);
                }
            }
            default -> { }
        }
    }

    private int spawn(ServerLevel level) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
        FakePlayer p = FakePlayer.get(level);
        p.setPos(8, y + 1, 8);
        var spawner = PlayerSpawnerFactory.INSTANCE.create(p);
        int total = 0;
        for (int i = 0; i < 4; i++) {
            var zone = new SpawningZoneInput(new SpawnCause(spawner, p), level, -24 + i * 12, y - 8, -24, 48, 32, 48);
            total += spawner.runForArea(zone, 5).size();
        }
        return total;
    }

    private static int count(MinecraftServer s) {
        int n = 0;
        for (ServerLevel ignored : s.getAllLevels()) n++;
        return n;
    }

    private void next(Phase p) { phase = p; phaseTicks = 0; tickNanos.clear(); }

    private void report(String name, MinecraftServer server) {
        long[] a = tickNanos.stream().mapToLong(Long::longValue).sorted().toArray();
        if (a.length == 0) return;
        double ms = 1e-6;
        log("{}: ticks={} mean={}ms p50={}ms p95={}ms p99={}ms max={}ms", name, a.length,
                String.format("%.2f", Arrays.stream(a).average().orElse(0) * ms),
                String.format("%.2f", a[a.length / 2] * ms), String.format("%.2f", a[(int) (a.length * 0.95)] * ms),
                String.format("%.2f", a[(int) (a.length * 0.99)] * ms), String.format("%.2f", a[a.length - 1] * ms));
    }
}
