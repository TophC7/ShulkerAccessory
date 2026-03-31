package dev.shulkeraccessories.compat.ss;

import dev.shulkeraccessories.client.ShulkerTabOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;

/**
 * Screen for item-based SS shulker boxes. Extends SS's base screen
 * to get full rendering with upgrades, sort buttons, etc.
 * Adds our shulker tab overlay for switching between equipped shulkers.
 */
public class ItemStorageScreen extends StorageScreenBase<ItemStorageContainerMenu> {

    private final ShulkerTabOverlay tabOverlay;

    protected ItemStorageScreen(ItemStorageContainerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.tabOverlay = menu.isFromAccessory()
                ? new ShulkerTabOverlay(menu.getSourceSlot()) : null;
    }

    public static ItemStorageScreen constructScreen(ItemStorageContainerMenu menu, Inventory playerInv, Component title) {
        return new ItemStorageScreen(menu, playerInv, title);
    }

    @Override
    protected String getStorageSettingsTabTooltip() {
        return "gui.sophisticatedstorage.settings.tooltip";
    }

    @Override
    protected void init() {
        super.init();
        if (tabOverlay != null) tabOverlay.buildTabs();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (tabOverlay != null) tabOverlay.renderUnselectedTabs(graphics, leftPos, topPos);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tabOverlay != null) tabOverlay.renderSelectedTabAndTooltips(
                graphics, leftPos, topPos, mouseX, mouseY, font);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabOverlay != null && tabOverlay.handleClick(mouseX, mouseY, button, leftPos, topPos)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
