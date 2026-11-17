package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.outbreak.PokemonFinds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * A Pokémon found in a dive chest, carried as an item until it is safe: a capsule. It is ordinary dive loot (marked with the run,
 * lost to a knock-out, possibly taken by the toll) until it is banked or carried out. Then its finder uses it and the Pokémon
 * joins their party (or PC), graded by AscensionLib. Only the finder can release it. No client mod is needed: the item is one of
 * Cobblemon's balls with a name and a description.
 */
public final class Capsules {
    public static final String TAG = "ascension_capsule";

    /** Releases a capsule's Pokémon to a player. Present only when Cobblemon is installed. */
    public interface Releaser {
        /** Creates the Pokémon, adds it to the party (or PC when full) and grades it. Returns its name, or null on failure. */
        String release(ServerPlayer player, String species, int level, String floor, int zoneTier);
    }

    public static volatile Releaser releaser;

    /** Capsules released since the server started, by id: a recap counts one already opened as kept. */
    static final java.util.Set<String> RELEASED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean released(String id) { return RELEASED.contains(id); }

    private Capsules() {}

    /** A capsule for a find. */
    public static ItemStack make(PokemonFinds.Find find, String floor, int zoneTier, ServerPlayer finder, String dungeon, String difficulty) {
        ItemStack stack = new ItemStack(ball(find.tier()));
        String name = displayName(find.species());
        ChatFormatting colour = switch (find.tier()) {
            case STANDARD -> ChatFormatting.WHITE;
            case STARTER -> ChatFormatting.AQUA;
            case PSEUDO -> ChatFormatting.LIGHT_PURPLE;
            case LEGENDARY -> ChatFormatting.GOLD;
            case MYTHICAL -> ChatFormatting.RED;
        };
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Capsule: " + name).withStyle(st -> st.withColor(colour).withItalic(false).withBold(find.tier().ordinal() >= 3)));
        List<Component> lore = new ArrayList<>();
        lore.add(line(find.tier().label() + " · level " + find.level(), colour));
        lore.add(line("Found in " + dungeon + " on " + difficulty + ".", ChatFormatting.GRAY));
        lore.add(line("Ascension rarity: at least " + cap(floor) + ", graded at zone tier " + zoneTier + ".", ChatFormatting.GRAY));
        lore.add(Component.empty());
        lore.add(line("Dive loot until banked or carried out:", ChatFormatting.YELLOW));
        lore.add(line("a knock-out or the toll can take it.", ChatFormatting.YELLOW));
        lore.add(line("Once safe, use it to release " + name + " to your team.", ChatFormatting.GREEN));
        lore.add(line("Bound to " + finder.getGameProfile().getName() + ".", ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        CompoundTag tag = new CompoundTag();
        tag.putString("species", find.species());
        tag.putInt("level", find.level());
        tag.putString("tier", find.tier().name());
        tag.putString("floor", floor);
        tag.putInt("zoneTier", zoneTier);
        tag.putUUID("owner", finder.getUUID());
        tag.putString("ownerName", finder.getGameProfile().getName());
        tag.putUUID("id", UUID.randomUUID());                 // two finds of one species never merge into a stack
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(TAG, tag));
        return stack;
    }

    /** The capsule's data, or null when the stack is not a capsule. */
    public static CompoundTag read(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG)) return null;
        return data.copyTag().getCompound(TAG);
    }

    /**
     * A player used a capsule. Releases it when it is theirs and safe; otherwise says why not. Never lets the item act as what
     * it looks like (a Poké Ball is never thrown). Returns null when the stack is not a capsule.
     */
    public static InteractionResult use(ServerPlayer player, ItemStack stack) {
        CompoundTag c = read(stack);
        if (c == null) return null;
        if (!player.getUUID().equals(c.getUUID("owner"))) {
            player.displayClientMessage(Component.literal("This capsule is bound to " + c.getString("ownerName") + ". Only they can release it.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (RunLoot.runOf(stack) != null) {
            player.displayClientMessage(Component.literal("Not yet: bank it at a checkpoint or carry it out through the portal first.").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.FAIL;
        }
        Releaser r = releaser;
        if (r == null) {
            player.displayClientMessage(Component.literal("Pokémon can't be released on this server (Cobblemon is not installed).").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        String name;
        try {
            name = r.release(player, c.getString("species"), c.getInt("level"), c.getString("floor"), Math.max(1, c.getInt("zoneTier")));
        } catch (RuntimeException | LinkageError e) {
            AscensionDungeonsLog.LOG.error("[capsule] could not release {} for {}", c.getString("species"), player.getGameProfile().getName(), e);
            name = null;
        }
        if (name == null) {
            player.displayClientMessage(Component.literal("The capsule would not open. It is still yours; try again, or tell a moderator.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        RELEASED.add(c.getUUID("id").toString());
        stack.shrink(1);
        player.sendSystemMessage(Component.literal(name + " joined your team!").withStyle(ChatFormatting.GREEN));
        player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.3f);
        AscensionDungeonsLog.LOG.info("[capsule] {} released {} (level {}, {}, floor {}, zone tier {})", player.getGameProfile().getName(), c.getString("species"),
                c.getInt("level"), c.getString("tier"), c.getString("floor"), c.getInt("zoneTier"));
        return InteractionResult.SUCCESS;
    }

    private static Item ball(PokemonFinds.Tier tier) {
        String id = switch (tier) {
            case STANDARD -> "cobblemon:poke_ball";
            case STARTER -> "cobblemon:great_ball";
            case PSEUDO -> "cobblemon:ultra_ball";
            case LEGENDARY -> "cobblemon:master_ball";
            case MYTHICAL -> "cobblemon:cherish_ball";
        };
        ResourceLocation rl = ResourceLocation.parse(id);
        return BuiltInRegistries.ITEM.containsKey(rl) ? BuiltInRegistries.ITEM.get(rl) : Items.ENDER_EYE;
    }

    /** "mrmime" -> "Mrmime", "dratini" -> "Dratini": Cobblemon's own name is used when the Pokémon is released. */
    static String displayName(String species) { return species.isEmpty() ? species : Character.toUpperCase(species.charAt(0)) + species.substring(1); }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static Component line(String text, ChatFormatting colour) { return Component.literal(text).withStyle(st -> st.withColor(colour).withItalic(false)); }
}
