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
 * The extraction screen: a plain six-row chest window, so no client mod is needed. The top rows show exactly what the toll
 * would take; the bottom row has the choices. Nothing in it can be taken: every click is answered here and nothing moves.
 */
public final class TollMenu extends ChestMenu {
    public static final int PAY = 45, FIGHT = 49, STAY = 53;
    /** Slots 0 to 35 show the loot the toll would take. */
    public static final int SHOWN = 36;

    private final IntConsumer onChoice;
    private boolean answered;

    public TollMenu(int id, Inventory inventory, Container shown, IntConsumer onChoice) {
        super(MenuType.GENERIC_9x6, id, inventory, shown, 6);
        this.onChoice = onChoice;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        // Never call super: that is what moves items. Put the client back in step with what the server holds.
        sendAllDataToRemote();
        if (answered || (slot != PAY && slot != FIGHT && slot != STAY)) return;
        answered = true;
        onChoice.accept(slot);
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
    /** The shown items are copies for display; they must not fall into the player's inventory when the window closes. */
    @Override public void removed(Player player) { }
}
