package dev.shulkeraccessories.client;

import dev.shulkeraccessories.ShulkerAccessoriesMod;
import dev.shulkeraccessories.ShulkerAccessoryMenu;
import dev.shulkeraccessories.SophisticatedCompat;
import dev.shulkeraccessories.SwitchShulkerTabPayload;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ShulkerAccessoryScreen extends AbstractContainerScreen<ShulkerAccessoryMenu> {

    // vanilla shulker box texture (3 rows)
    private static final ResourceLocation TEXTURE_SHULKER =
            ResourceLocation.withDefaultNamespace("textures/gui/container/shulker_box.png");

    // generic chest texture (supports tiling for variable rows)
    private static final ResourceLocation TEXTURE_GENERIC =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    // VANILLA TAB SPRITES //

    private static final ResourceLocation[] SELECTED_TABS = {
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_1"),
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_2"),
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_3"),
    };
    private static final ResourceLocation[] UNSELECTED_TABS = {
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_1"),
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2"),
            ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_3"),
    };

    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_STRIDE = 27;

    // COLOR //

    private static final float[] DEFAULT_TINT = {0.82f, 0.70f, 0.82f};

    private final List<TabInfo> tabs = new ArrayList<>();
    private final float[] containerTint;
    private final boolean useGenericTexture;

    public ShulkerAccessoryScreen(ShulkerAccessoryMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        int rows = menu.getContainerRows();
        this.useGenericTexture = rows != 3;
        this.imageWidth = 176;
        // vanilla shulker_box.png is 166px, generic chest uses formula
        this.imageHeight = useGenericTexture ? (114 + rows * 18) : 166;
        this.inventoryLabelY = useGenericTexture ? (rows * 18 + 1) : 73;
        this.containerTint = computeTint(menu.getDyeColorId(), 0.4f);
    }

    @Override
    protected void init() {
        super.init();
        buildTabs();
    }

    private void buildTabs() {
        tabs.clear();
        if (!menu.isFromAccessory()) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var cap = AccessoriesCapability.get(mc.player);
        if (cap == null) return;

        var containers = cap.getContainers();
        var shulkerContainer = containers.get(ShulkerAccessoriesMod.SLOT_NAME);
        if (shulkerContainer == null) return;

        for (int i = 0; i < shulkerContainer.getSize(); i++) {
            ItemStack stack = shulkerContainer.getAccessories().getItem(i);
            if (ShulkerAccessoriesMod.isShulkerBox(stack)) {
                DyeColor color = getShulkerColor(stack);
                tabs.add(new TabInfo(i, color, stack.copy(), computeTint(color, 0.5f)));
            }
        }

        // only show tabs when more than one shulker is equipped
        if (tabs.size() <= 1) {
            tabs.clear();
        }
    }

    /** Get the DyeColor for a shulker — works for vanilla, returns null for SS. */
    @Nullable
    private static DyeColor getShulkerColor(ItemStack stack) {
        if (SophisticatedCompat.isSSShulkerBox(stack)) return null;
        return ShulkerBoxBlock.getColorFromItem(stack.getItem());
    }

    // RENDERING //

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        float[] rgb = containerTint;
        graphics.setColor(rgb[0], rgb[1], rgb[2], 1.0f);

        if (useGenericTexture) {
            // tile the generic chest texture for variable row counts
            int rows = menu.getContainerRows();
            // top section: title bar + slot rows (tile row from y=17 in texture)
            graphics.blit(TEXTURE_GENERIC, leftPos, topPos, 0, 0, imageWidth, 17);
            for (int row = 0; row < rows; row++) {
                graphics.blit(TEXTURE_GENERIC, leftPos, topPos + 17 + row * 18, 0, 17, imageWidth, 18);
            }
            // bottom section: player inventory (96px from y=126 in texture)
            graphics.blit(TEXTURE_GENERIC, leftPos, topPos + 17 + rows * 18, 0, 126, imageWidth, 96);
        } else {
            // vanilla 3-row shulker box texture
            graphics.blit(TEXTURE_SHULKER, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        }

        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // unselected tabs first (behind the container edge)
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).slotIndex != menu.getSourceSlot()) {
                renderTab(graphics, i, false);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // selected tab on top (overlaps container edge)
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).slotIndex == menu.getSourceSlot()) {
                renderTab(graphics, i, true);
            }
        }

        renderTooltip(graphics, mouseX, mouseY);
        renderTabTooltips(graphics, mouseX, mouseY);
    }

    private void renderTab(GuiGraphics graphics, int tabIndex, boolean selected) {
        TabInfo tab = tabs.get(tabIndex);
        int x = leftPos + TAB_STRIDE * tabIndex;
        int y = topPos - 28;

        ResourceLocation[] sprites = selected ? SELECTED_TABS : UNSELECTED_TABS;
        ResourceLocation sprite = sprites[Math.min(tabIndex, sprites.length - 1)];

        float[] tint = tab.tint;
        graphics.setColor(tint[0], tint[1], tint[2], 1.0f);
        graphics.blitSprite(sprite, x, y, TAB_WIDTH, TAB_HEIGHT);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

        // render the actual shulker box item on the tab
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 100.0f);
        graphics.renderItem(tab.iconStack, x + 5, y + 9);
        graphics.pose().popPose();
    }

    private void renderTabTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < tabs.size(); i++) {
            int x = leftPos + TAB_STRIDE * i;
            int y = topPos - 28;
            if (mouseX >= x + 3 && mouseX < x + TAB_WIDTH - 3
                    && mouseY >= y + 3 && mouseY < y + TAB_HEIGHT - 3) {
                graphics.renderTooltip(this.font, tabs.get(i).iconStack.getHoverName(), mouseX, mouseY);
                break;
            }
        }
    }

    // CLICK HANDLING //

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < tabs.size(); i++) {
                int x = leftPos + TAB_STRIDE * i;
                int y = topPos - 28;
                if (mouseX >= x && mouseX < x + TAB_WIDTH
                        && mouseY >= y && mouseY < y + TAB_HEIGHT) {
                    TabInfo tab = tabs.get(i);
                    if (tab.slotIndex != menu.getSourceSlot()) {
                        PacketDistributor.sendToServer(new SwitchShulkerTabPayload(tab.slotIndex));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // COLOR HELPERS //

    /** Compute a lightened tint from a DyeColor id. colorWeight controls the mix (lower = lighter). */
    private static float[] computeTint(int dyeColorId, float colorWeight) {
        if (dyeColorId < 0) return DEFAULT_TINT;
        int rgb = DyeColor.byId(dyeColorId).getTextureDiffuseColor();
        float white = 1.0f - colorWeight;
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255f * colorWeight + white,
                ((rgb >> 8) & 0xFF) / 255f * colorWeight + white,
                (rgb & 0xFF) / 255f * colorWeight + white
        };
    }

    private static float[] computeTint(@Nullable DyeColor color, float colorWeight) {
        return computeTint(color != null ? color.getId() : -1, colorWeight);
    }

    // TAB DATA //

    private record TabInfo(int slotIndex, @Nullable DyeColor color, ItemStack iconStack, float[] tint) {}
}
