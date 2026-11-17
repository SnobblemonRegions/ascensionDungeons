package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon implementation of {@link Capsules.Releaser}: makes the Pokémon, puts it in the party (or the PC when the party is
 * full), then asks AscensionLib, when installed, to grade it at the dive's zone tier with the difficulty's floor. AscensionLib is
 * optional and reached by reflection: the newest grant first, then the older floor grant, then nothing (Cobblemon's own Pokémon).
 */
public final class CapsuleRelease implements Capsules.Releaser {
    @Override
    public String release(ServerPlayer player, String species, int level, String floor, int zoneTier) {
        Pokemon pokemon = PokemonProperties.Companion.parse(species + " level=" + Math.max(1, level)).create();
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        boolean inParty = party.add(pokemon), placed = inParty;
        if (!placed) placed = Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
        if (!placed) return null;
        String graded = grade(pokemon, floor, zoneTier);
        AscensionDungeonsLog.LOG.info("[capsule] {} placed in {}'s {}; AscensionLib: {}", species, player.getGameProfile().getName(), inParty ? "party" : "PC", graded);
        return pokemon.getDisplayName(false).getString();
    }

    @Override
    public Capsules.Info info(String species) {
        var s = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(species);
        if (s == null) return null;
        java.util.List<Capsules.Type> types = new java.util.ArrayList<>();
        for (var t : s.getTypes()) types.add(new Capsules.Type(t.getName(), t.getHue() & 0xFFFFFF));   // "Water", and its colour (RGB)
        return new Capsules.Info(s.getName(), types);
    }

    /** Cobblemon's own item for showing a Pokémon: each player's Cobblemon draws the real model, so no art is shipped. */
    @Override
    public net.minecraft.world.item.ItemStack model(String species, float[] tint) {
        var s = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(species);
        if (s == null) return null;
        return com.cobblemon.mod.common.item.PokemonItem.Companion.from(s, new java.util.HashSet<String>(), 1, new org.joml.Vector4f(tint[0], tint[1], tint[2], tint[3]));
    }

    /** The AscensionLib grant's result name, or why there was none. */
    static String grade(Pokemon pokemon, String floor, int zoneTier) {
        Class<?> grants;
        try { grants = Class.forName("com.ascensionlib.AscensionGrants"); } catch (ClassNotFoundException e) { return "not installed"; }
        try {
            Method atTier = grants.getMethod("grantWithRarityFloorAtTier", Pokemon.class, String.class, int.class);
            return String.valueOf(atTier.invoke(null, pokemon, floor, zoneTier));
        } catch (NoSuchMethodException e) {
            try {
                Method withFloor = grants.getMethod("grantWithRarityFloor", Pokemon.class, String.class);
                return String.valueOf(withFloor.invoke(null, pokemon, floor)) + " (floor only: this AscensionLib cannot grade at a zone tier)";
            } catch (ReflectiveOperationException e2) {
                return "no grant method";
            }
        } catch (ReflectiveOperationException e) {
            AscensionDungeonsLog.LOG.error("[capsule] AscensionLib grant failed", e);
            return "failed";
        }
    }
}
