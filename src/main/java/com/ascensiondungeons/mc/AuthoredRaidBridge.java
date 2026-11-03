package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.run.RunConfig;
import com.cobbleraids.api.encounter.CobbleRaidsEncounters;
import com.cobbleraids.api.encounter.EncounterPolicy;
import com.cobbleraids.api.encounter.EncounterRequest;
import com.cobbleraids.api.encounter.StartResult;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** CobbleRaids implementation of {@link AuthoredBridges.RaidBridge}. Only constructed when CobbleRaids is installed. */
public final class AuthoredRaidBridge implements AuthoredBridges.RaidBridge {
    private static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("ascensiondungeons", "run");
    private final RunConfig cfg;

    public AuthoredRaidBridge(RunConfig cfg) { this.cfg = cfg; }

    @Override
    public String start(UUID id, List<ServerPlayer> party, String bossDefinition, ServerLevel level, BlockPos anchor, int bossLevel,
                        Consumer<AuthoredBridges.RaidOutcome> ended) {
        ResourceLocation boss = ResourceLocation.tryParse(bossDefinition);
        if (boss == null) return "bad boss id " + bossDefinition;
        EncounterPolicy policy = EncounterPolicy.none().withRaidRewards(cfg.raidRewards).withCatchable(cfg.raidCatchable);
        try {
            StartResult r = CobbleRaidsEncounters.start(new EncounterRequest(OWNER, id, party.stream().limit(EncounterRequest.MAX_PLAYERS).toList(), boss, level,
                    new Vec3(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5), bossLevel, OptionalLong.empty(), policy), result -> {
                AuthoredBridges.RaidOutcome o = switch (result.outcome()) {
                    case VICTORY -> AuthoredBridges.RaidOutcome.VICTORY;
                    case DEFEAT -> AuthoredBridges.RaidOutcome.DEFEAT;
                    default -> AuthoredBridges.RaidOutcome.ABORTED;
                };
                ended.accept(o);
            });
            if (r instanceof StartResult.Refused refused) return String.valueOf(refused.reason());
            return null;
        } catch (RuntimeException e) {
            AscensionDungeonsLog.LOG.error("Raid start failed for {}", bossDefinition, e);
            return "start failed";
        }
    }

    @Override
    public void abort(UUID id) {
        try { CobbleRaidsEncounters.abort(id); }
        catch (RuntimeException e) { AscensionDungeonsLog.LOG.warn("Could not abort raid {}", id, e); }
    }
}
