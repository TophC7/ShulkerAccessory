package dev.shulkeraccessories.compat.ss;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;

/**
 * Screen for item-based SS shulker boxes. Extends SS's base screen
 * to get full rendering with upgrades, sort buttons, etc.
 */
public class ItemStorageScreen extends StorageScreenBase<ItemStorageContainerMenu> {

    protected ItemStorageScreen(ItemStorageContainerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    public static ItemStorageScreen constructScreen(ItemStorageContainerMenu menu, Inventory playerInv, Component title) {
        return new ItemStorageScreen(menu, playerInv, title);
    }

    @Override
    protected String getStorageSettingsTabTooltip() {
        return "gui.sophisticatedstorage.settings.tooltip";
    }
}
