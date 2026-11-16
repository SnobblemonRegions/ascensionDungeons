package com.ascensiondungeons.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * Marks items looted inside an Outbreak dive as part of that run's haul, in the stack's custom data, so a failed exit can take
 * back what was not banked. No client mod or resource pack is involved.
 *
 * <p>Life of a mark: Lootr's fill tags the stack ({@link com.ascensiondungeons.mc.lootr.RunLootFilters}); a checkpoint or an
 * extraction {@linkplain #clearAll clears} it (the item is now simply the player's); a knock-out, timeout or walk-out
 * {@linkplain #stripAll removes} the stack. Anything eaten, used up or crafted into something else is no longer marked and stays.
 */
public final class RunLoot {
    public static final String RUN_TAG = "ascension_run";

    /** Told when a player's per-player chest loot is rolled inside a dive. */
    public interface ChestListener { void looted(UUID run, ServerPlayer player, BlockPos chest); }

    /** Held items of a player's Pokémon party (Cobblemon). Present only when Cobblemon is installed. */
    public interface PartyItems {
        /** Applies {@code change} to each party Pokémon's held item; returns how many were changed. */
        int each(ServerPlayer player, Function<ItemStack, ItemStack> change);
    }

    /** Which dive run, if any, owns a dimension. Set while a server is running; null otherwise. */
    public static volatile Function<ResourceKey<Level>, UUID> runOfDimension;
    public static volatile ChestListener chestListener;
    public static volatile PartyItems partyItems;

    private RunLoot() {}

    /** The dive run that owns this dimension, or null (not a dive level, or no server). */
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

    /** Removes the mark, and the custom data component with it if nothing else is stored there. */
    public static void clear(ItemStack stack) {
        if (stack.isEmpty()) return;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(RUN_TAG)) return;
        CompoundTag tag = data.copyTag();
        tag.remove(RUN_TAG);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Banks or extracts: every stack of this run the player carries becomes an ordinary item. Returns how many stacks. */
    public static int clearAll(ServerPlayer player, UUID run) {
        return apply(player, s -> run.equals(runOf(s)), false);
    }

    /** A failed exit: every stack of this run the player carries is removed. Returns how many stacks. */
    public static int stripAll(ServerPlayer player, UUID run) {
        return apply(player, s -> run.equals(runOf(s)), true);
    }

    /**
     * Removes marked stacks whose run is no longer live for this player: loot from a run that ended while they were offline, or
     * that died with a server crash. {@code live} answers whether the player is still inside that run.
     */
    public static int sweep(ServerPlayer player, Predicate<UUID> live) {
        return apply(player, s -> { UUID r = runOf(s); return r != null && !live.test(r); }, true);
    }

    /**
     * Loot of this run that is tucked away from a plain look at the inventory: inside bundles and shulker boxes, on the cursor,
     * in the ender chest, or held by a party Pokémon. The extraction toll takes all of it, so hiding loot never pays.
     */
    public static int countHidden(ServerPlayer player, UUID run) {
        Predicate<ItemStack> marked = s -> run.equals(runOf(s));
        int[] n = {0};
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) n[0] += countNested(player.getInventory().getItem(i), marked);
        Container ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) { ItemStack s = ender.getItem(i); if (marked.test(s)) n[0]++; n[0] += countNested(s, marked); }
        if (marked.test(player.containerMenu.getCarried())) n[0]++;
        PartyItems party = partyItems;
        if (party != null) {
            try { party.each(player, held -> { if (marked.test(held)) n[0]++; return held; }); }
            catch (RuntimeException | LinkageError e) { com.ascensiondungeons.AscensionDungeonsLog.LOG.warn("[run-loot] could not check party held items", e); }
        }
        return n[0];
    }

    /**
     * How many items of this run's loot the player carries, wherever they are: the inventory, inside bundles and boxes, the
     * ender chest, the cursor and party Pokémon. Counts items, not stacks.
     */
    public static int countItems(ServerPlayer player, UUID run) {
        Predicate<ItemStack> marked = s -> run.equals(runOf(s));
        int[] n = {0};
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) n[0] += items(player.getInventory().getItem(i), marked);
        Container ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) n[0] += items(ender.getItem(i), marked);
        n[0] += items(player.containerMenu.getCarried(), marked);
        PartyItems party = partyItems;
        if (party != null) {
            try { party.each(player, held -> { n[0] += items(held, marked); return held; }); }
            catch (RuntimeException | LinkageError e) { com.ascensiondungeons.AscensionDungeonsLog.LOG.warn("[run-loot] could not count party held items", e); }
        }
        return n[0];
    }

    /** The marked items in a stack: the stack itself if it is marked, and anything marked inside it. */
    private static int items(ItemStack s, Predicate<ItemStack> marked) {
        if (s.isEmpty()) return 0;
        int n = marked.test(s) ? s.getCount() : 0;
        BundleContents bundle = s.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) for (ItemStack inner : bundle.items()) n += items(inner, marked);
        ItemContainerContents box = s.get(DataComponents.CONTAINER);
        if (box != null) for (ItemStack inner : box.nonEmptyItems()) n += items(inner, marked);
        return n;
    }

    private static int countNested(ItemStack holder, Predicate<ItemStack> marked) {
        if (holder.isEmpty()) return 0;
        int n = 0;
        BundleContents bundle = holder.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) for (ItemStack inner : bundle.items()) { if (marked.test(inner)) n++; n += countNested(inner, marked); }
        ItemContainerContents box = holder.get(DataComponents.CONTAINER);
        if (box != null) for (ItemStack inner : box.nonEmptyItems()) { if (marked.test(inner)) n++; n += countNested(inner, marked); }
        return n;
    }

    /** Removes the hidden loot {@link #countHidden} counts. Returns how many stacks went. */
    public static int stripHidden(ServerPlayer player, UUID run) {
        Predicate<ItemStack> marked = s -> run.equals(runOf(s));
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) n += nested(player.getInventory().getItem(i), marked, true);
        n += container(player.getEnderChestInventory(), marked, true);
        ItemStack carried = player.containerMenu.getCarried();
        if (marked.test(carried)) { player.containerMenu.setCarried(ItemStack.EMPTY); n++; }
        PartyItems party = partyItems;
        if (party != null) {
            try { n += party.each(player, held -> change(held, marked, true)); }
            catch (RuntimeException | LinkageError e) { com.ascensiondungeons.AscensionDungeonsLog.LOG.warn("[run-loot] could not strip party held items", e); }
        }
        if (n > 0) player.inventoryMenu.broadcastChanges();
        return n;
    }

    private static int apply(ServerPlayer player, Predicate<ItemStack> marked, boolean remove) {
        int n = container(player.getInventory(), marked, remove) + container(player.getEnderChestInventory(), marked, remove);
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack after = change(carried, marked, remove);
        if (after != carried) { player.containerMenu.setCarried(after); n++; }
        else n += nested(carried, marked, remove);
        PartyItems party = partyItems;
        if (party != null) {
            try {
                n += party.each(player, held -> change(held, marked, remove));
            } catch (RuntimeException | LinkageError e) {
                com.ascensiondungeons.AscensionDungeonsLog.LOG.warn("[run-loot] could not check party held items of {}", player.getGameProfile().getName(), e);
            }
        }
        if (n > 0) player.inventoryMenu.broadcastChanges();
        return n;
    }

    private static int container(Container c, Predicate<ItemStack> marked, boolean remove) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            ItemStack after = change(s, marked, remove);
            if (after != s) { c.setItem(i, after); n++; }
            else n += nested(s, marked, remove);
        }
        return n;
    }

    /** The stack after banking or stripping: the same instance if untouched, EMPTY if removed, itself with the mark cleared if banked. */
    private static ItemStack change(ItemStack s, Predicate<ItemStack> marked, boolean remove) {
        if (s.isEmpty() || !marked.test(s)) return s;
        if (remove) return ItemStack.EMPTY;
        ItemStack copy = s.copy();
        clear(copy);
        return copy;
    }

    /** Bundles and shulker boxes: the same rule for what is inside them. Returns how many inner stacks changed. */
    private static int nested(ItemStack holder, Predicate<ItemStack> marked, boolean remove) {
        if (holder.isEmpty()) return 0;
        int n = 0;
        BundleContents bundle = holder.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            List<ItemStack> kept = new ArrayList<>();
            int changed = 0;
            for (ItemStack inner : bundle.itemsCopy()) {
                ItemStack after = change(inner, marked, remove);
                if (after != inner) changed++; else changed += nested(inner, marked, remove);
                if (!after.isEmpty()) kept.add(after);
            }
            if (changed > 0) { holder.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(kept)); n += changed; }
        }
        ItemContainerContents box = holder.get(DataComponents.CONTAINER);
        if (box != null) {
            List<ItemStack> kept = new ArrayList<>();
            int changed = 0;
            for (ItemStack inner : box.nonEmptyItemsCopy()) {
                ItemStack after = change(inner, marked, remove);
                if (after != inner) changed++; else changed += nested(inner, marked, remove);
                if (!after.isEmpty()) kept.add(after);
            }
            if (changed > 0) { holder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(kept)); n += changed; }
        }
        return n;
    }
}
