package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
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
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;

/** Bounded Fire-pilot population. Reuses existing Ashen config without defining future type/form eligibility. */
public final class TypePopulation implements ArenaDirector {
    private final RunConfig cfg; private final Supplier<RunManager> manager; private final FantasyRunHost host;
    private final Map<UUID,List<PokemonEntity>> owned=new HashMap<>(); private final Random random=new Random();
    public TypePopulation(RunConfig cfg,Supplier<RunManager> manager,FantasyRunHost host) { this.cfg=cfg;this.manager=manager;this.host=host; }
    @Override public void tick(long now) {
        if(now%cfg.spawnIntervalTicks!=0)return;
        for(var run:manager.get().runs()) if(run.dungeonType()!=null) {
            if(run.state()!=RunState.ACTIVE||run.bossPhase()) { runReleased(run.id());continue; }
            var view=host.typeView(run.id()).orElse(null);if(view==null)continue;
            var entities=owned.computeIfAbsent(run.id(),id->new ArrayList<>());
            entities.removeIf(e->e.isRemoved()||!e.getPokemon().isWild());
            if(entities.size()>=Math.min(cfg.wildPerRun,64))continue;
            var players=view.level().players().stream().filter(p->run.members().containsKey(p.getUUID()))
                    .filter(p->run.members().get(p.getUUID()).presence()!=com.ascensiondungeons.run.Run.Presence.LEFT)
                    .filter(p->p.isAlive()&&!p.isSpectator()).toList();
            if(players.isEmpty())continue;
            var player=players.get(random.nextInt(players.size()));
            var room=view.plan().at(player.getBlockX(),player.getBlockZ());if(room==null)continue;
            if(entities.stream().filter(e->view.plan().at(e.getBlockX(),e.getBlockZ())==room).count()>=8)continue;
            var pool=cfg.spawns.get("ashen_caldera");if(pool==null)continue;
            for(int attempt=0;attempt<4;attempt++) {
                int x=room.cx()*64+8+random.nextInt(48),z=room.cz()*64+8+random.nextInt(48);
                BlockPos floor=new BlockPos(x,4,z);
                if(!view.level().hasChunkAt(floor)||!view.level().isPositionEntityTicking(floor))continue;
                if(players.stream().anyMatch(p->Math.hypot(p.getX()-x,p.getZ()-z)<cfg.spawnMinDistance))continue;
                if(player.position().distanceToSqr(x,5,z)>cfg.spawnMaxDistance*(double)cfg.spawnMaxDistance)continue;
                if(view.level().getBlockState(floor).getCollisionShape(view.level(),floor).isEmpty())continue;
                boolean clear=true;
                for(int y=1;y<=3;y++) if(!view.level().getBlockState(floor.above(y)).isAir())clear=false;
                if(!clear)continue;
                var pick=SpawnTable.pick(pool,random);if(pick==null)break;
                try {
                    var props=PokemonProperties.Companion.parse(pick.species+" level="+SpawnTable.level(pick,random));
                    PokemonEntity entity=props.createEntity(view.level());entity.moveTo(x+.5,5,z+.5,random.nextFloat()*360,0);
                    if(view.level().addFreshEntity(entity))entities.add(entity);
                } catch(RuntimeException e) { AscensionDungeonsLog.LOG.warn("Fire pilot spawn refused for {}",pick.species,e); }
                break;
            }
        }
        for(UUID id:new ArrayList<>(owned.keySet())) if(manager.get().get(id).isEmpty())runReleased(id);
    }
    @Override public void runReleased(UUID id) {
        var entities=owned.remove(id);if(entities==null)return;
        for(var e:entities) if(!e.isRemoved()&&e.getPokemon().isWild()) {
            if(e.getBattle()!=null)e.getBattle().stop();
            e.discard();
        }
    }
}
