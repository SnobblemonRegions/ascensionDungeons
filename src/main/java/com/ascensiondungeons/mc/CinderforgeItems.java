package com.ascensiondungeons.mc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;

/** The Forge Key: a renamed vanilla tripwire hook tagged in custom data, so no client mod or resource pack is needed. */
public final class CinderforgeItems {
    public static final String KEY_TAG = "ascension_key";
    public static final String FORGE_KEY = "forge_key";

    private CinderforgeItems() {}

    public static ItemStack forgeKey() {
        ItemStack stack = new ItemStack(Items.TRIPWIRE_HOOK);
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_TAG, FORGE_KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Forge Key").withStyle(net.minecraft.ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Opens the sealed door of the Crimson Barracks.")
                .withStyle(net.minecraft.ChatFormatting.GRAY))));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isForgeKey(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.TRIPWIRE_HOOK)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && FORGE_KEY.equals(data.copyTag().getString(KEY_TAG));
    }
}
