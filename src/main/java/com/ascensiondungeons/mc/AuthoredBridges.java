package com.ascensiondungeons.mc;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * The seams between the authored-dungeon engine and the optional mods. The engine only sees these interfaces; the
 * implementations ({@link AuthoredPokemon}, {@link AuthoredRaidBridge}, {@link AscensionBridge}) are loaded only when the
 * matching mod is installed, so a server without Cobblemon, CobbleRaids or AscensionLib still runs the vanilla dungeons.
 */
public final class AuthoredBridges {
    private AuthoredBridges() {}

    /** Places a wild Pokemon (Cobblemon) as the dungeon's creature. Returns null if it could not be placed. */
    public interface PokemonSpawner {
        Mob spawn(ServerLevel level, String species, int pokemonLevel, BlockPos at, float yaw);
    }

    /** Real-time combat between dungeon Pokemon, players and the players' own Pokemon. */
    public interface Combat {
        /** Called every few ticks with the dungeon's hostile creatures. */
        void tick(ServerLevel level, long now, Collection<? extends Mob> hostiles);

        /** Makes a creature hit harder (bosses). */
        void empower(Mob creature, double damageMultiplier);

        /** Operator/test aid: sends the player's first party Pokemon out beside them. Returns a message. */
        String sendOut(ServerPlayer player);

        /** Operator/test aid: burns, poisons or bleeds the nearest dungeon Pokemon within 20 blocks. Returns a message. */
        String testInflict(ServerPlayer player, String kind);

        /** The run's level is going away: heal and forget everything about it. */
        void release(ServerLevel level);
    }

    public enum RaidOutcome { VICTORY, DEFEAT, ABORTED }

    /** Starts a CobbleRaids owned encounter and reports how it ended. */
    public interface RaidBridge {
        /** @return null on success, or a refusal reason. The callback runs on the server thread when the encounter ends. */
        String start(UUID encounterId, List<ServerPlayer> party, String bossDefinition, ServerLevel level, BlockPos anchor, int bossLevel,
                     Consumer<RaidOutcome> ended);

        void abort(UUID encounterId);
    }

    /** AscensionLib: profile zones per run level and the run-end reward settlement. */
    public interface Ascension {
        void openZone(String dimensionId, int tier);

        void closeZone(String dimensionId);

        /** Pays a finished run (VICTORY only pays). Statuses that mean "try again later" are retried by the bridge itself. */
        void settle(UUID runId, String outcome, int tier, int arenaWins, boolean bossDefeated, Collection<UUID> players);

        /** Called every server tick so pending settlements can be retried. */
        void tick(long now);
    }
}
