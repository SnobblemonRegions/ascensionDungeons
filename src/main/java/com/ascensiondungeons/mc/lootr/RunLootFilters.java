package com.ascensiondungeons.mc.lootr;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.mc.RunLoot;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import noobanidus.mods.lootr.common.api.data.LootFiller;
import noobanidus.mods.lootr.common.api.filter.ILootrFilter;
import noobanidus.mods.lootr.common.api.filter.ILootrFilterProvider;

/**
 * Lootr discovers this through {@code META-INF/services} (its {@code LootrServiceRegistry} uses ServiceLoader), so the class is only
 * ever loaded when Lootr is installed. Lootr calls every filter from its {@code LootTable.fill} mixin just before the rolled stacks
 * are split into slots, for every fill: the state is null when the fill is not one of Lootr's per-player rolls.
 */
public final class RunLootFilters implements ILootrFilterProvider {
    @Override
    public List<ILootrFilter> getFilters() { return List.of(new TagRunLoot()); }

    static final class TagRunLoot implements ILootrFilter {
        @Override public int getPriority() { return 0; }
        @Override public String getName() { return "ascensiondungeons:tag_run_loot"; }

        /** Returning true would stop Lootr's later filters, so this always returns false. */
        @Override
        public boolean mutate(ObjectArrayList<ItemStack> stacks, LootFiller.LootFillerState state, LootContext context, RandomSource random) {
            try {
                if (!RunLoot.ENABLED || state == null || state.provider() == null) return false;
                UUID run = RunLoot.runOf(state.provider().getInfoDimension());
                if (run == null) return false;
                for (ItemStack stack : stacks) RunLoot.tag(stack, run);
                AscensionDungeonsLog.LOG.info("[run-loot] tagged {} stack(s) for {} in run {} at {}", stacks.size(),
                        state.player() == null ? "?" : state.player().getGameProfile().getName(), run, state.provider().getInfoPos());
            } catch (RuntimeException e) {
                // A fault here must never break chest loot.
                AscensionDungeonsLog.LOG.error("[run-loot] filter failed; loot left untagged", e);
            }
            return false;
        }
    }
}
