package com.ascensiondungeons.mc;

import java.util.function.IntConsumer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * The dungeon picker: a plain three-row chest window, so no client mod is needed. Each dungeon is an item to hover over and
 * click. Nothing in it can be taken: every click is answered here and nothing moves.
 */
public final class DiveMenu extends ChestMenu {
    /** The party's item and the guide's, on the bottom row. */
    public static final int PARTY = 22, GUIDE = 26;
    /** The four difficulty buttons, Casual to Nightmare, on the bottom row. */
    public static final int DIFFICULTY = 18;

    private final IntConsumer onClick;
    /** The dungeon the player has been warned about and may confirm with a second click; -1 for none. */
    int armed = -1;

    DiveMenu(int id, Inventory inventory, Container shown, IntConsumer onClick) {
        super(MenuType.GENERIC_9x3, id, inventory, shown, 3);
        this.onClick = onClick;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        // Never call super: that is what moves items. Put the client back in step with what the server holds.
        sendAllDataToRemote();
        if (slot >= 0 && slot < 27 && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) onClick.accept(slot);
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
    /** The items are for display; they must not fall into the player's inventory when the window closes. */
    @Override public void removed(Player player) { }
}
