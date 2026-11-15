package com.ascensiondungeons.mc;

import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Marks items looted inside a run level as part of that run's haul, in the stack's custom data, so a failed exit can take back
 * what was not banked. No client mod or resource pack is involved. SPIKE: only tagging exists; nothing strips or banks yet, so it
 * is off unless {@code -Dascensiondungeons.runLootTag=true}.
 */
public final class RunLoot {
    public static final String RUN_TAG = "ascension_run";
    public static final boolean ENABLED = Boolean.getBoolean("ascensiondungeons.runLootTag");

    /** Which run, if any, owns a dimension. Set while a server is running; null otherwise. */
    public static volatile Function<ResourceKey<Level>, UUID> runOfDimension;

    private RunLoot() {}

    /** The run that owns this dimension, or null (not a run level, or no server). */
    public static UUID runOf(ResourceKey<Level> dimension) {
        Function<ResourceKey<Level>, UUID> f = runOfDimension;
        return f == null || dimension == null ? null : f.apply(dimension);
    }

    /** Marks the stack as loot of this run, keeping any custom data it already has. */
    public static void tag(ItemStack stack, UUID runId) {
        if (stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(RUN_TAG, runId.toString()));
    }

    /** The run this stack was looted in, or null. */
    public static UUID runOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(RUN_TAG)) return null;
        try {
            return UUID.fromString(data.copyTag().getString(RUN_TAG));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Banks the stack: removes the mark, and the custom data component with it if nothing else is stored there. */
    public static void clear(ItemStack stack) {
        if (stack.isEmpty()) return;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(RUN_TAG)) return;
        CompoundTag tag = data.copyTag();
        tag.remove(RUN_TAG);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
