package dev.shulkeraccessories.client;

import dev.shulkeraccessories.ShulkerAccessoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ShulkerAccessoryScreen extends AbstractContainerScreen<ShulkerAccessoryMenu> {

    // vanilla shulker box texture (3 rows)
    private static final ResourceLocation TEXTURE_SHULKER =
            ResourceLocation.withDefaultNamespace("textures/gui/container/shulker_box.png");

    // generic chest texture (supports tiling for variable rows)
    private static final ResourceLocation TEXTURE_GENERIC =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    private final float[] containerTint;
    private final boolean useGenericTexture;
    private final ShulkerTabOverlay tabOverlay;

    public ShulkerAccessoryScreen(ShulkerAccessoryMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        int rows = menu.getContainerRows();
        this.useGenericTexture = rows != 3;
        this.imageWidth = 176;
        // vanilla shulker_box.png is 166px, generic chest uses formula
        this.imageHeight = useGenericTexture ? (114 + rows * 18) : 166;
        this.inventoryLabelY = useGenericTexture ? (rows * 18 + 1) : 73;
        this.containerTint = ShulkerTabOverlay.computeTint(menu.getDyeColorId(), 0.4f);
        this.tabOverlay = menu.isFromAccessory()
                ? new ShulkerTabOverlay(menu.getSourceSlot()) : null;
    }

    @Override
    protected void init() {
        super.init();
        if (tabOverlay != null) tabOverlay.buildTabs();
    }

    // RENDERING //

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        float[] rgb = containerTint;
        graphics.setColor(rgb[0], rgb[1], rgb[2], 1.0f);

        if (useGenericTexture) {
            int rows = menu.getContainerRows();
            graphics.blit(TEXTURE_GENERIC, leftPos, topPos, 0, 0, imageWidth, 17);
            for (int row = 0; row < rows; row++) {
                graphics.blit(TEXTURE_GENERIC, leftPos, topPos + 17 + row * 18, 0, 17, imageWidth, 18);
            }
            graphics.blit(TEXTURE_GENERIC, leftPos, topPos + 17 + rows * 18, 0, 126, imageWidth, 96);
        } else {
            graphics.blit(TEXTURE_SHULKER, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        }

        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (tabOverlay != null) tabOverlay.renderUnselectedTabs(graphics, leftPos, topPos);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tabOverlay != null) tabOverlay.renderSelectedTabAndTooltips(
                graphics, leftPos, topPos, mouseX, mouseY, font);
        renderTooltip(graphics, mouseX, mouseY);
    }

    // CLICK HANDLING //

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabOverlay != null && tabOverlay.handleClick(mouseX, mouseY, button, leftPos, topPos)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
