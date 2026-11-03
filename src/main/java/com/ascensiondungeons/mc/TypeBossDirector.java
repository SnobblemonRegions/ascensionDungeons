package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.api.RaidEntrance;
import com.ascensiondungeons.run.EndReason;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunConfig;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.RunState;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobbleraids.api.encounter.CobbleRaidsEncounters;
import com.cobbleraids.api.encounter.EncounterRequest;
import com.cobbleraids.api.encounter.EncounterPolicy;
import com.cobbleraids.api.encounter.EncounterOutcome;
import com.cobbleraids.api.encounter.StartResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Automatic isolated-arena transition for the Fire pilot. Policy uses existing config, not new balance choices. */
public final class TypeBossDirector implements ArenaDirector {
    private static final ResourceLocation OWNER=ResourceLocation.fromNamespaceAndPath("ascensiondungeons","type_run");
    private static final class Pending {
        final Run run; final FantasyRunHost.TypeView view; final List<UUID> party; final long since;
        RaidEntrance.Animation animation; UUID encounter; boolean committed;
        Pending(Run run,FantasyRunHost.TypeView view,List<UUID> party,long since) { this.run=run;this.view=view;this.party=List.copyOf(party);this.since=since; }
    }
    private final MinecraftServer server; private final RunConfig cfg; private final Supplier<RunManager> manager;
    private final FantasyRunHost host; private final Map<UUID,Pending> active=new HashMap<>();
    private final RaidEntrance entrance;
    public TypeBossDirector(MinecraftServer server,RunConfig cfg,Supplier<RunManager> manager,FantasyRunHost host) {
        this.server=server;this.cfg=cfg;this.manager=manager;this.host=host;
        var hooks=FabricLoader.getInstance().getEntrypoints("ascensiondungeons:raid_entrance",RaidEntrance.class);
        entrance=hooks.isEmpty()?null:hooks.getFirst();
    }
    public boolean begin(Run run) {
        if(active.containsKey(run.id())) return true;
        var view=host.typeView(run.id()).orElse(null);
        if(view==null) return false;
        List<ServerPlayer> party=new ArrayList<>();
        for(var entry:run.members().entrySet()) {
            var p=server.getPlayerList().getPlayer(entry.getKey());
            if(entry.getValue().presence()!=Run.Presence.LEFT && p!=null && p.isAlive() && !p.isSpectator() && p.serverLevel()==view.level()) party.add(p);
        }
        if(party.isEmpty() || party.size()>EncounterRequest.MAX_PLAYERS) return false;
        int[] staging=view.plan().staging();
        if(view.level().getChunkSource().getChunkNow(Math.floorDiv(staging[0],16),Math.floorDiv(staging[2],16))==null) return false;
        // No exploration raids exist in the pilot. Stop each distinct wild battle through Cobblemon's lifecycle.
        var stopped=new HashSet<UUID>();
        for(ServerPlayer p:party) {
            var battle=BattleRegistry.getBattleByParticipatingPlayerId(p.getUUID());
            if(battle!=null && stopped.add(battle.getBattleId())) battle.stop();
        }
        for(ServerPlayer p:party) if(BattleRegistry.getBattleByParticipatingPlayerId(p.getUUID())!=null) return false;
        for(int i=0;i<party.size();i++) {
            ServerPlayer p=party.get(i);
            p.teleportTo(view.level(),staging[0]+.5+(i%2)*2,staging[1],staging[2]+.5+(i/2)*2,180,0);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Exploration has ended. The final arena awaits."));
        }
        Pending pending=new Pending(run,view,party.stream().map(ServerPlayer::getUUID).toList(),server.getTickCount());
        active.put(run.id(),pending);
        if(entrance!=null) {
            try { pending.animation=entrance.begin(new RaidEntrance.Context(run.id(),view.level(),anchor(view),"ASHEN_CALDERA",pending.party)); }
            catch(RuntimeException e) { AscensionDungeonsLog.LOG.warn("Type entrance unavailable; continuing without decoration",e); }
        }
        return true;
    }
    private static Vec3 anchor(FantasyRunHost.TypeView view) { int[] p=view.plan().boss();return new Vec3(p[0]+.5,p[1],p[2]+.5); }
    @Override public void tick(long now) {
        for(Pending p:new ArrayList<>(active.values())) {
            if(p.run.state()!=RunState.ACTIVE) { runReleased(p.run.id());continue; }
            if(p.committed) continue;
            List<ServerPlayer> party=p.party.stream().map(id->server.getPlayerList().getPlayer(id)).filter(java.util.Objects::nonNull)
                    .filter(player->player.isAlive()&&!player.isSpectator()&&player.serverLevel()==p.view.level())
                    .filter(player->p.run.members().get(player.getUUID()).presence()!=Run.Presence.LEFT)
                    .filter(player->player.position().distanceToSqr(anchor(p.view))<=28*28).toList();
            if(party.size()!=p.party.size() || !p.view.level().hasChunkAt(BlockPos.containing(anchor(p.view)))) { fail(p);continue; }
            try {
                long age=now-p.since;
                if(age<80) { if(p.animation!=null) p.animation.tick((int)age);continue; }
                p.committed=true;
                var pool=cfg.raidBosses.get("ashen_caldera");
                if(pool==null||pool.isEmpty()) { fail(p);continue; }
                ResourceLocation boss=ResourceLocation.parse(pool.get(new java.util.Random(p.run.seed()^0x424f5353L).nextInt(pool.size())));
                UUID id=UUID.randomUUID(); p.encounter=id;
                StartResult result=CobbleRaidsEncounters.start(new EncounterRequest(OWNER,id,party,boss,p.view.level(),anchor(p.view),
                        cfg.raidBossLevel,OptionalLong.empty(),EncounterPolicy.none().withRaidRewards(cfg.raidRewards).withCatchable(cfg.raidCatchable)),
                        ended->{ p.encounter=null; manager.get().end(p.run.id(),ended.outcome()==EncounterOutcome.VICTORY?EndReason.COMPLETED:EndReason.ABANDONED); });
                if(result instanceof StartResult.Refused) { p.encounter=null;fail(p); }
                else { if(p.animation!=null) p.animation.reveal(); closeAnimation(p); }
            } catch(RuntimeException e) { AscensionDungeonsLog.LOG.error("Type final raid failed",e);fail(p); }
        }
    }
    private void fail(Pending p) { runReleased(p.run.id());manager.get().end(p.run.id(),EndReason.ABANDONED); }
    private static void closeAnimation(Pending p) {
        var animation=p.animation;p.animation=null;
        if(animation!=null) try { animation.close(); } catch(RuntimeException e) { AscensionDungeonsLog.LOG.warn("Type effect cleanup failed",e); }
    }
    @Override public void runReleased(UUID id) {
        Pending p=active.remove(id); if(p==null)return;closeAnimation(p);
        if(p.encounter!=null) { UUID encounter=p.encounter;p.encounter=null;CobbleRaidsEncounters.abort(encounter); }
    }
}
