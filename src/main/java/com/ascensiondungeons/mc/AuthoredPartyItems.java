package com.ascensiondungeons.mc;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Cobblemon implementation of {@link RunLoot.PartyItems}: a party Pokémon's held item is somewhere a player could hide dive loot,
 * so banking and stripping look there too. Only constructed when Cobblemon is installed.
 */
public final class AuthoredPartyItems implements RunLoot.PartyItems {
    @Override
    public int each(ServerPlayer player, Function<ItemStack, ItemStack> change) {
        int changed = 0;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            ItemStack held = pokemon.heldItem();
            if (held.isEmpty()) continue;
            ItemStack after = change.apply(held);
            if (after == held) continue;
            pokemon.swapHeldItem(after, false, false);
            changed++;
        }
        return changed;
    }
}
