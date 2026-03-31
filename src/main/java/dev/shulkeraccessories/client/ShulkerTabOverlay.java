package dev.shulkeraccessories.client;

import dev.shulkeraccessories.ShulkerAccessoriesMod;
import dev.shulkeraccessories.SophisticatedCompat;
import dev.shulkeraccessories.SwitchShulkerTabPayload;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared tab overlay for shulker accessory screens.
 * Renders clickable tabs above any container screen that shows an equipped shulker.
 * Used by both our vanilla ShulkerAccessoryScreen and the SS ItemStorageScreen.
 */
public class ShulkerTabOverlay {

    // vanilla creative tab sprites
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
    private static final float[] DEFAULT_TINT = {0.82f, 0.70f, 0.82f};

    private final List<TabInfo> tabs = new ArrayList<>();
    private final int activeSlot;

    public ShulkerTabOverlay(int activeSlot) {
        this.activeSlot = activeSlot;
    }

    /** Build the tab list from equipped accessories. Call during init(). */
    public void buildTabs() {
        tabs.clear();

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
                // SS shulkers (color == null, not vanilla undyed) get no tint
                float[] tint = (color == null && SophisticatedCompat.isSSShulkerBox(stack))
                        ? null : computeTint(color, 0.5f);
                tabs.add(new TabInfo(i, stack.copy(), tint));
            }
        }

        // only show tabs when 2+ shulkers equipped
        if (tabs.size() <= 1) {
            tabs.clear();
        }
    }

    public boolean hasTabs() { return !tabs.isEmpty(); }

    // RENDERING //

    /** Render unselected tabs (call BEFORE super.render). */
    public void renderUnselectedTabs(GuiGraphics graphics, int leftPos, int topPos) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).slotIndex != activeSlot) {
                renderTab(graphics, i, false, leftPos, topPos);
            }
        }
    }

    /** Render selected tab + tooltips (call AFTER super.render). */
    public void renderSelectedTabAndTooltips(GuiGraphics graphics, int leftPos, int topPos,
                                              int mouseX, int mouseY, Font font) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).slotIndex == activeSlot) {
                renderTab(graphics, i, true, leftPos, topPos);
            }
        }
        renderTabTooltips(graphics, leftPos, topPos, mouseX, mouseY, font);
    }

    private void renderTab(GuiGraphics graphics, int tabIndex, boolean selected, int leftPos, int topPos) {
        TabInfo tab = tabs.get(tabIndex);
        int x = leftPos + TAB_STRIDE * tabIndex;
        int y = topPos - 28;

        ResourceLocation[] sprites = selected ? SELECTED_TABS : UNSELECTED_TABS;
        ResourceLocation sprite = sprites[Math.min(tabIndex, sprites.length - 1)];

        if (tab.tint != null) {
            graphics.setColor(tab.tint[0], tab.tint[1], tab.tint[2], 1.0f);
        }
        graphics.blitSprite(sprite, x, y, TAB_WIDTH, TAB_HEIGHT);
        if (tab.tint != null) {
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 100.0f);
        graphics.renderItem(tab.iconStack, x + 5, y + 9);
        graphics.pose().popPose();
    }

    private void renderTabTooltips(GuiGraphics graphics, int leftPos, int topPos,
                                    int mouseX, int mouseY, Font font) {
        for (int i = 0; i < tabs.size(); i++) {
            int x = leftPos + TAB_STRIDE * i;
            int y = topPos - 28;
            if (mouseX >= x + 3 && mouseX < x + TAB_WIDTH - 3
                    && mouseY >= y + 3 && mouseY < y + TAB_HEIGHT - 3) {
                graphics.renderTooltip(font, tabs.get(i).iconStack.getHoverName(), mouseX, mouseY);
                break;
            }
        }
    }

    // CLICK HANDLING //

    /** Handle a mouse click. Returns true if a tab was clicked. */
    public boolean handleClick(double mouseX, double mouseY, int button, int leftPos, int topPos) {
        if (button != 0) return false;
        for (int i = 0; i < tabs.size(); i++) {
            int x = leftPos + TAB_STRIDE * i;
            int y = topPos - 28;
            if (mouseX >= x && mouseX < x + TAB_WIDTH
                    && mouseY >= y && mouseY < y + TAB_HEIGHT) {
                TabInfo tab = tabs.get(i);
                if (tab.slotIndex != activeSlot) {
                    PacketDistributor.sendToServer(new SwitchShulkerTabPayload(tab.slotIndex));
                    return true;
                }
            }
        }
        return false;
    }

    // COLOR HELPERS //

    @Nullable
    private static DyeColor getShulkerColor(ItemStack stack) {
        if (SophisticatedCompat.isSSShulkerBox(stack)) return null;
        return ShulkerBoxBlock.getColorFromItem(stack.getItem());
    }

    public static float[] computeTint(int dyeColorId, float colorWeight) {
        if (dyeColorId < 0) return DEFAULT_TINT;
        int rgb = DyeColor.byId(dyeColorId).getTextureDiffuseColor();
        float white = 1.0f - colorWeight;
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255f * colorWeight + white,
                ((rgb >> 8) & 0xFF) / 255f * colorWeight + white,
                (rgb & 0xFF) / 255f * colorWeight + white
        };
    }

    public static float[] computeTint(@Nullable DyeColor color, float colorWeight) {
        return computeTint(color != null ? color.getId() : -1, colorWeight);
    }

    private record TabInfo(int slotIndex, ItemStack iconStack, @Nullable float[] tint) {}
}
