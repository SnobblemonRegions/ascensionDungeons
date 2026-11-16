package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.cobblemon.mod.common.api.entity.Despawner;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Cobblemon implementation of {@link AuthoredBridges.PokemonSpawner}. Only constructed when Cobblemon is installed. */
public final class AuthoredPokemon implements AuthoredBridges.PokemonSpawner {
    private static final Despawner<PokemonEntity> NEVER = new Despawner<>() {
        @Override public void beginTracking(PokemonEntity entity) { }
        @Override public boolean shouldDespawn(PokemonEntity entity) { return false; }
    };

    /** Outbreak enemies fight in real time and are not a way to collect Pokémon. */
    @Override
    public void hostile(Mob creature) {
        if (!(creature instanceof PokemonEntity entity)) return;
        try {
            PokemonProperties.Companion.parse("uncatchable=true").apply(entity);
            entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(), true);
        } catch (RuntimeException e) {
            AscensionDungeonsLog.LOG.warn("Could not mark {} as a dungeon enemy", creature.getName().getString(), e);
        }
    }

    @Override
    public Mob spawn(ServerLevel level, String species, int pokemonLevel, BlockPos at, float yaw) {
        try {
            PokemonEntity entity = PokemonProperties.Companion.parse(species.toLowerCase(Locale.ROOT) + " level=" + pokemonLevel).createEntity(level);
            // Run levels are deleted wholesale, so nothing should despawn them: neither Cobblemon's despawner nor vanilla's.
            entity.setDespawner(NEVER);
            entity.setPersistenceRequired();
            entity.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
            return level.addFreshEntity(entity) ? entity : null;
        } catch (RuntimeException e) {
            AscensionDungeonsLog.LOG.warn("Could not spawn Pokemon {} for an authored dungeon", species, e);
            return null;
        }
    }
}
