package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.gen.ArenaSites;
import com.ascensiondungeons.gen.DistrictGrid;
import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunConfig;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.RunState;
import com.ascensiondungeons.run.SpawnTable;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-biome wild Pokemon. Cobblemon's own spawner is not used: its biome conditions cannot match our void-biome levels
 * and the spike measured 100-290 ms per area call. Instead each active run gets one cheap placement attempt every few
 * ticks: pick a district near a player, pick a floor cell that passes the clearance, distance and exclusion rules, and
 * create the entity from that biome's {@link SpawnTable}. Counts are capped per district and per run.
 */
public final class WildPopulation implements ArenaDirector {
    private static final int FLOOR_Y = Template.FLOOR_Y;
    private static final int CLEARANCE = 3;
    private static final int TRIES = 8;
    private static final double ARENA_KEEP_OUT = 22;
    private static final double ENTRY_KEEP_OUT = 24;

    private static final com.cobblemon.mod.common.api.entity.Despawner<PokemonEntity> NEVER =
            new com.cobblemon.mod.common.api.entity.Despawner<>() {
                @Override public void beginTracking(PokemonEntity entity) { }
                @Override public boolean shouldDespawn(PokemonEntity entity) { return false; }
            };

    private static final class RunState2 {
        final Map<Long, List<PokemonEntity>> byCell = new HashMap<>();
        final List<ArenaSites.Site> arenas;

        RunState2(List<ArenaSites.Site> arenas) { this.arenas = arenas; }

        int total() { return byCell.values().stream().mapToInt(List::size).sum(); }
    }

    private final MinecraftServer server;
    private final RunConfig cfg;
    private final Supplier<RunManager> manager;
    private final FantasyRunHost host;
    private final Map<UUID, RunState2> runs = new HashMap<>();
    private final Map<String, List<SpawnTable.Entry>> tables = new HashMap<>();
    private final Random rnd = new Random();

    public WildPopulation(MinecraftServer server, RunConfig cfg, Supplier<RunManager> manager, FantasyRunHost host) {
        this.server = server;
        this.cfg = cfg;
        this.manager = manager;
        this.host = host;
        loadTables();
    }

    /** Drops entries whose species Cobblemon does not know, so a typo costs one log line instead of every attempt. */
    private void loadTables() {
        cfg.spawns.forEach((biome, entries) -> {
            List<SpawnTable.Entry> ok = new ArrayList<>();
            for (SpawnTable.Entry e : entries) {
                if (e.species != null && PokemonSpecies.INSTANCE.getByName(e.species.toLowerCase(Locale.ROOT)) != null) {
                    ok.add(e);
                } else {
                    AscensionDungeonsLog.LOG.warn("Spawn table {}: unknown species '{}' ignored", biome, e.species);
                }
            }
            tables.put(biome, ok);
        });
    }

    @Override
    public void tick(long now) {
        RunManager m = manager.get();
        if (m == null || now % cfg.spawnIntervalTicks != 0) return;
        Set<UUID> live = new HashSet<>();
        for (Run run : m.runs()) {
            live.add(run.id());
            if (run.state() != RunState.ACTIVE) continue;
            var view = host.levelView(run.id());
            if (view.isEmpty()) continue;
            RunState2 st = runs.computeIfAbsent(run.id(), id -> new RunState2(ArenaSites.of(view.get().grid())));
            attempt(run, view.get(), st);
        }
        runs.keySet().retainAll(live);
    }

    @Override
    public void runReleased(UUID runId) {
        runs.remove(runId);
    }

    private void attempt(Run run, FantasyRunHost.LevelView view, RunState2 st) {
        ServerLevel level = view.level();
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : run.members().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.serverLevel() == level && p.isAlive()) players.add(p);
        }
        if (players.isEmpty()) return;
        st.byCell.values().forEach(l -> l.removeIf(e -> e.isRemoved() || !e.isAlive()));
        if (st.total() >= cfg.wildPerRun) return;

        ServerPlayer near = players.get(rnd.nextInt(players.size()));
        List<DistrictGrid.Cell> candidates = new ArrayList<>();
        for (DistrictGrid.Cell c : view.grid().cells()) {
            if (distanceToCell(near, c) <= cfg.spawnMaxDistance && st.byCell.getOrDefault(key(c), List.of()).size() < cfg.wildPerDistrict) {
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) return;
        DistrictGrid.Cell cell = candidates.get(rnd.nextInt(candidates.size()));
        List<SpawnTable.Entry> table = tables.get(cell.biome().name().toLowerCase(Locale.ROOT));
        if (table == null || table.isEmpty()) return;

        int[] entry = view.grid().entry();
        for (int i = 0; i < TRIES; i++) {
            int x = cell.cx() * DistrictGrid.CELL - Template.CENTER + rnd.nextInt(DistrictGrid.CELL);
            int z = cell.cz() * DistrictGrid.CELL - Template.CENTER + rnd.nextInt(DistrictGrid.CELL);
            if (!allowed(level, players, near, st, entry, x, z)) continue;
            SpawnTable.Entry pick = SpawnTable.pick(table, rnd);
            if (pick == null) return;
            PokemonEntity entity = create(level, pick, x, z);
            if (entity == null) return;
            st.byCell.computeIfAbsent(key(cell), k -> new ArrayList<>()).add(entity);
            return;
        }
    }

    private boolean allowed(ServerLevel level, List<ServerPlayer> players, ServerPlayer near, RunState2 st, int[] entry, int x, int z) {
        double dn = Math.hypot(near.getX() - x, near.getZ() - z);
        if (dn > cfg.spawnMaxDistance) return false;
        for (ServerPlayer p : players) if (Math.hypot(p.getX() - x, p.getZ() - z) < cfg.spawnMinDistance) return false;
        if (Math.hypot(entry[0] - x, entry[2] - z) < ENTRY_KEEP_OUT) return false;
        for (ArenaSites.Site a : st.arenas) {
            if (Math.hypot(a.anchor()[0] - x, a.anchor()[2] - z) < ARENA_KEEP_OUT) return false;
        }
        BlockPos floor = new BlockPos(x, FLOOR_Y, z);
        if (!level.hasChunkAt(floor) || !level.isPositionEntityTicking(floor)) return false;
        BlockState ground = level.getBlockState(floor);
        if (ground.getCollisionShape(level, floor).isEmpty() || !ground.getFluidState().isEmpty()) return false;
        for (int dy = 1; dy <= CLEARANCE; dy++) {
            BlockPos p = floor.above(dy);
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private PokemonEntity create(ServerLevel level, SpawnTable.Entry pick, int x, int z) {
        try {
            PokemonProperties props = PokemonProperties.Companion.parse(
                    pick.species.toLowerCase(Locale.ROOT) + " level=" + SpawnTable.level(pick, rnd));
            PokemonEntity entity = props.createEntity(level);
            // Run levels are deleted wholesale, so nothing should despawn them: neither Cobblemon's despawner nor vanilla's.
            entity.setDespawner(NEVER);
            entity.setPersistenceRequired();
            entity.moveTo(x + 0.5, FLOOR_Y + 1, z + 0.5, rnd.nextFloat() * 360f, 0f);
            return level.addFreshEntity(entity) ? entity : null;
        } catch (RuntimeException e) {
            AscensionDungeonsLog.LOG.warn("Could not spawn {}", pick.species, e);
            return null;
        }
    }

    private static long key(DistrictGrid.Cell c) { return ((long) c.cx() << 32) ^ (c.cz() & 0xffffffffL); }

    private static double distanceToCell(ServerPlayer p, DistrictGrid.Cell c) {
        double x0 = c.cx() * DistrictGrid.CELL - Template.CENTER, z0 = c.cz() * DistrictGrid.CELL - Template.CENTER;
        double dx = Math.max(0, Math.max(x0 - p.getX(), p.getX() - (x0 + DistrictGrid.CELL)));
        double dz = Math.max(0, Math.max(z0 - p.getZ(), p.getZ() - (z0 + DistrictGrid.CELL)));
        return Math.hypot(dx, dz);
    }
}
