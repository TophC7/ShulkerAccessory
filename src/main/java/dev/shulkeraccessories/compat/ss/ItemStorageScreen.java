package dev.shulkeraccessories.compat.ss;

import dev.shulkeraccessories.client.ShulkerTabOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import xyz.kwahson.core.compat.ss.SSItemStorageScreen;

/**
 * Screen for the SS shulker accessory menu. Inherits the SS upgrade/sort/settings UI
 * from the core base, then layers the per-mod tab overlay for switching between
 * equipped accessory shulkers.
 */
public class ItemStorageScreen extends SSItemStorageScreen<ItemStorageContainerMenu> {
    private final ShulkerTabOverlay tabOverlay;

    protected ItemStorageScreen(ItemStorageContainerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.tabOverlay = menu.isFromAccessory()
                ? new ShulkerTabOverlay(menu.getSourceSlot())
                : null;
    }

    public static ItemStorageScreen constructScreen(ItemStorageContainerMenu menu, Inventory playerInv, Component title) { return new ItemStorageScreen(menu, playerInv, title); }

    @Override
    protected void init() {
        super.init();
        if (tabOverlay != null)
            tabOverlay.buildTabs();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (tabOverlay != null)
            tabOverlay.renderUnselectedTabs(graphics, leftPos, topPos);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tabOverlay != null)
            tabOverlay.renderSelectedTabAndTooltips(
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
