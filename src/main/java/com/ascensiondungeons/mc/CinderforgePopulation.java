package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.gen.cinderforge.CinderforgeLayout;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunConfig;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.RunState;
import com.ascensiondungeons.run.SpawnTable;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wild Pokemon for the Cinderforge Bastion, from the same {@code ashen_caldera} spawn table the Fire pilot uses. They spawn
 * on the standing cells exported for each room, near a player and never on top of one, only in rooms the party has reached,
 * and never in the safe start, the spiral or the boss arena. Only constructed when Cobblemon is installed.
 */
public final class CinderforgePopulation implements ArenaDirector {
    private static final Set<String> NO_WILD = Set.of("R1", "R7", "R8");
    private static final int PER_ROOM = 6, PER_RUN = 48;

    private final RunConfig cfg;
    private final Supplier<RunManager> manager;
    private final FantasyRunHost host;
    private final Map<UUID, List<PokemonEntity>> owned = new HashMap<>();
    private final Random random = new Random();

    public CinderforgePopulation(RunConfig cfg, Supplier<RunManager> manager, FantasyRunHost host) {
        this.cfg = cfg;
        this.manager = manager;
        this.host = host;
    }

    @Override
    public void tick(long now) {
        if (now % cfg.spawnIntervalTicks != 0) return;
        RunManager m = manager.get();
        if (m == null) return;
        for (Run run : m.runs()) {
            if (!RunManager.CINDERFORGE.equals(run.dungeonType())) continue;
            if (run.state() != RunState.ACTIVE) { runReleased(run.id()); continue; }
            var view = host.cinderforgeView(run.id()).orElse(null);
            if (view == null) continue;
            List<PokemonEntity> entities = owned.computeIfAbsent(run.id(), id -> new ArrayList<>());
            entities.removeIf(e -> e.isRemoved() || !e.getPokemon().isWild());
            if (entities.size() >= Math.min(cfg.wildPerRun, PER_RUN)) continue;
            List<ServerPlayer> players = view.level().players().stream()
                    .filter(p -> run.members().containsKey(p.getUUID()))
                    .filter(p -> run.members().get(p.getUUID()).presence() != Run.Presence.LEFT)
                    .filter(p -> p.isAlive() && !p.isSpectator()).toList();
            if (players.isEmpty()) continue;
            ServerPlayer player = players.get(random.nextInt(players.size()));
            int[] at = CinderforgeChunkGenerator.toSchematic(player.blockPosition());
            CinderforgeLayout.Room room = view.generator().layout().roomAt(at[0], at[1], at[2]);
            if (room == null || NO_WILD.contains(room.id) || room.spawnCells.length == 0) continue;
            long here = entities.stream().filter(e -> {
                int[] c = CinderforgeChunkGenerator.toSchematic(e.blockPosition());
                return room.contains(c[0], c[1], c[2]);
            }).count();
            if (here >= PER_ROOM) continue;
            var pool = cfg.spawns.get("ashen_caldera");
            if (pool == null) continue;
            for (int attempt = 0; attempt < 4; attempt++) {
                BlockPos pos = CinderforgeChunkGenerator.toWorld(room.spawnCells[random.nextInt(room.spawnCells.length)]);
                if (!view.level().hasChunkAt(pos) || !view.level().isPositionEntityTicking(pos)) continue;
                // rooms are small: keep clear of players, but never demand more than the room can give
                double min = Math.min(cfg.spawnMinDistance, 10);
                if (players.stream().anyMatch(p -> p.position().distanceToSqr(pos.getX() + .5, pos.getY(), pos.getZ() + .5) < min * min)) continue;
                var pick = SpawnTable.pick(pool, random);
                if (pick == null) break;
                try {
                    var props = PokemonProperties.Companion.parse(pick.species + " level=" + SpawnTable.level(pick, random));
                    PokemonEntity entity = props.createEntity(view.level());
                    entity.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, random.nextFloat() * 360, 0);
                    if (view.level().addFreshEntity(entity)) entities.add(entity);
                } catch (RuntimeException e) {
                    AscensionDungeonsLog.LOG.warn("Cinderforge spawn refused for {}", pick.species, e);
                }
                break;
            }
        }
        for (UUID id : new ArrayList<>(owned.keySet())) if (m.get(id).isEmpty()) runReleased(id);
    }

    @Override
    public void runReleased(UUID id) {
        List<PokemonEntity> entities = owned.remove(id);
        if (entities == null) return;
        for (PokemonEntity e : entities) {
            if (!e.isRemoved() && e.getPokemon().isWild()) {
                if (e.getBattle() != null) e.getBattle().stop();
                e.discard();
            }
        }
    }
}
