package com.ascensiondungeons.mc;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Key items of the authored dungeons: renamed vanilla items tagged in custom data, so no client mod or pack is needed. */
public final class AuthoredItems {
    public static final String KEY_TAG = "ascension_key";

    private AuthoredItems() {}

    /** Builds the key declared in layout.json as {@code keys.<id> = {item, name, lore}}. */
    public static ItemStack key(String id, JsonObject def) {
        String item = def != null && def.has("item") ? def.get("item").getAsString() : "minecraft:tripwire_hook";
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(item)).orElse(Items.TRIPWIRE_HOOK));
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_TAG, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(def != null && def.has("name") ? def.get("name").getAsString() : id)
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        if (def != null && def.has("lore"))
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(def.get("lore").getAsString()).withStyle(net.minecraft.ChatFormatting.GRAY))));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isKey(ItemStack stack, String id) {
        if (stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && id.equals(data.copyTag().getString(KEY_TAG));
    }
}
