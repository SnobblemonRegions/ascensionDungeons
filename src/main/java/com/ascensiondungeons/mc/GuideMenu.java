package com.ascensiondungeons.mc;

import com.ascensiondungeons.outbreak.Onboarding;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The dive guide: every lesson of the tutorial as an item to hover over, in a plain three-row chest window, so no client mod
 * or resource pack is needed. Nothing in it can be taken.
 */
public final class GuideMenu extends ChestMenu {
    /** How wide an item's description may be before it is wrapped. */
    static final int WRAP = 44;

    private GuideMenu(int id, Inventory inventory, SimpleContainer shown) {
        super(MenuType.GENERIC_9x3, id, inventory, shown, 3);
    }

    /** Opens the guide for a player. {@code tollPercent} fills in the toll where a lesson names it. */
    public static void open(ServerPlayer player, Onboarding.Progress progress, int tollPercent) {
        SimpleContainer box = new SimpleContainer(27);
        int slot = 0;
        for (Onboarding.Lesson lesson : Onboarding.Lesson.values()) box.setItem(slot++, page(lesson, progress.seen(lesson), tollPercent, progress.firstExtractionFree()));
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new GuideMenu(id, inv, box), Component.literal("Dive guide (hover over an item)")));
    }

    static ItemStack page(Onboarding.Lesson lesson, boolean met, int tollPercent, boolean firstFree) {
        ItemStack stack = new ItemStack(icon(lesson));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(lesson.title()).withStyle(st -> st.withColor(ChatFormatting.GOLD).withItalic(false)));
        List<Component> lines = new ArrayList<>();
        for (String text : lesson.lines(tollPercent, firstFree)) {
            for (String part : Onboarding.wrap(text, WRAP)) lines.add(Component.literal(part).withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false)));
            lines.add(Component.empty());
        }
        lines.add(Component.literal(met ? "You have met this." : "You have not met this yet.").withStyle(st -> st.withColor(met ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        if (met) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static Item icon(Onboarding.Lesson lesson) {
        return switch (lesson) {
            case WELCOME -> Items.COMPASS;
            case OUTBREAK -> Items.CLOCK;
            case ENEMIES -> Items.IRON_SWORD;
            case SPELLS -> Items.BLAZE_POWDER;
            case CHESTS -> Items.CHEST;
            case UNBANKED -> Items.GOLD_INGOT;
            case CHECKPOINT -> Items.EMERALD;
            case STAGE -> Items.REDSTONE_TORCH;
            case TUNNEL -> Items.STONE_BRICK_STAIRS;
            case DEPTHS -> Items.DEEPSLATE_BRICKS;
            case TIMER_CHEST -> Items.TRAPPED_CHEST;
            case OUTBREAK_CHEST -> Items.ENDER_CHEST;
            case STRONGBOX -> Items.NETHERITE_INGOT;
            case ALTAR -> Items.CRYING_OBSIDIAN;
            case LOW_HEALTH -> Items.GOLDEN_APPLE;
            case TIME_SHORT -> Items.BELL;
            case PORTAL -> Items.LODESTONE;
            case GATEKEEPER -> Items.NETHERITE_SWORD;
            case KNOCKED_OUT -> Items.TOTEM_OF_UNDYING;
            case TOLL_PAID -> Items.RED_CONCRETE;
            case EXTRACTED -> Items.ENDER_PEARL;
            case FIRST_EXTRACTION -> Items.LIME_CONCRETE;
        };
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        // Never call super: that is what moves items. Put the client back in step with what the server holds.
        sendAllDataToRemote();
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
    /** The pages are for display; they must not fall into the player's inventory when the window closes. */
    @Override public void removed(Player player) { }
}
