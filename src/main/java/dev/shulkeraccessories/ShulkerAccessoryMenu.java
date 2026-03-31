package dev.shulkeraccessories;

import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Container menu for the portable shulker box UI.
 * Backs a SimpleContainer against the shulker item's component data,
 * saving contents on close.
 */
public class ShulkerAccessoryMenu extends AbstractContainerMenu {

    private final SimpleContainer container;
    private final int containerSize;
    private final int containerRows;
    private final int sourceSlot;
    private final boolean fromAccessory;
    private final int dyeColorId;
    private final int lockedMenuIndex;

    // the exact ItemStack we opened — used to verify the slot wasn't tampered with on save
    private final @Nullable ItemStack sourceStackRef;

    // SERVER //

    public ShulkerAccessoryMenu(int containerId, Inventory playerInv,
                                 SimpleContainer container, int sourceSlot,
                                 boolean fromAccessory, int dyeColorId,
                                 @Nullable ItemStack sourceStackRef) {
        super(ShulkerAccessoriesMod.SHULKER_MENU.get(), containerId);
        this.container = container;
        this.containerSize = container.getContainerSize();
        this.containerRows = (containerSize + 8) / 9; // round up to full rows
        this.sourceSlot = sourceSlot;
        this.fromAccessory = fromAccessory;
        this.dyeColorId = dyeColorId;
        this.sourceStackRef = sourceStackRef;

        // lock the hand-held shulker's hotbar slot to prevent moving it
        int hotbarMenuStart = containerSize + 27;
        this.lockedMenuIndex = (!fromAccessory && sourceSlot >= 0 && sourceSlot <= 8)
                ? hotbarMenuStart + sourceSlot : -1;

        container.startOpen(playerInv.player);

        // slot y-positions: vanilla shulker (3 rows) uses its own layout,
        // everything else uses the generic chest formula
        int playerInvY = (containerRows == 3) ? 84 : containerRows * 18 + 31;
        int hotbarY = (containerRows == 3) ? 142 : containerRows * 18 + 89;

        // container slots: variable rows of 9
        for (int row = 0; row < containerRows; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = col + row * 9;
                if (slotIdx < containerSize) {
                    addSlot(new NoShulkerSlot(
                            container, slotIdx, 8 + col * 18, 18 + row * 18));
                }
            }
        }

        // player inventory: y position adapts to container height
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(
                        playerInv, col + row * 9 + 9, 8 + col * 18, playerInvY + row * 18));
            }
        }

        // hotbar: 1 row of 9, with locked slot for hand-held source
        for (int col = 0; col < 9; col++) {
            int invSlot = col;
            if (!fromAccessory && invSlot == sourceSlot) {
                addSlot(new LockedSlot(playerInv, invSlot, 8 + col * 18, hotbarY));
            } else {
                addSlot(new Slot(playerInv, invSlot, 8 + col * 18, hotbarY));
            }
        }
    }

    // CLIENT //

    // max slots: 15 rows * 9 cols (netherite SS shulker)
    private static final int MAX_CONTAINER_SIZE = 135;

    /** Client-side factory — reads all fields from the network buffer in wire order. */
    public static ShulkerAccessoryMenu fromNetwork(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        int sourceSlot = buf.readVarInt();
        boolean fromAccessory = buf.readBoolean();
        int dyeColorId = buf.readVarInt();
        int size = Math.clamp(buf.readVarInt(), 1, MAX_CONTAINER_SIZE);
        return new ShulkerAccessoryMenu(containerId, playerInv,
                new SimpleContainer(size), sourceSlot, fromAccessory, dyeColorId, null);
    }

    // ACCESSORS //

    public int getSourceSlot() { return sourceSlot; }
    public boolean isFromAccessory() { return fromAccessory; }
    public int getDyeColorId() { return dyeColorId; }
    public int getContainerSize() { return containerSize; }
    public int getContainerRows() { return containerRows; }

    // VALIDATION //

    @Override
    public boolean stillValid(Player player) {
        if (fromAccessory) {
            var cap = AccessoriesCapability.get(player);
            if (cap == null) return false;
            var containers = cap.getContainers();
            var shulkerContainer = containers.get(ShulkerAccessoriesMod.SLOT_NAME);
            if (shulkerContainer == null) return false;
            if (sourceSlot < 0 || sourceSlot >= shulkerContainer.getSize()) return false;
            return ShulkerAccessoriesMod.isShulkerBox(
                    shulkerContainer.getAccessories().getItem(sourceSlot));
        } else {
            ItemStack held = player.getInventory().getItem(sourceSlot);
            return ShulkerAccessoriesMod.isShulkerBox(held);
        }
    }

    // SAVE & CLOSE //

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);

        if (!player.level().isClientSide) {
            saveContents(player);
        }
    }

    private void saveContents(Player player) {
        ItemStack target = getSourceStack(player);

        // verify the slot still holds the exact item we opened
        if (sourceStackRef != null && target != sourceStackRef) {
            dropAll(player);
            return;
        }

        if (!ShulkerAccessoriesMod.isShulkerBox(target)) {
            dropAll(player);
            return;
        }

        ShulkerAccessoriesMod.saveContents(target, container);
    }

    private void dropAll(Player player) {
        for (int i = 0; i < containerSize; i++) {
            ItemStack item = container.getItem(i);
            if (!item.isEmpty()) {
                player.drop(item, false);
            }
        }
    }

    private ItemStack getSourceStack(Player player) {
        if (fromAccessory) {
            var cap = AccessoriesCapability.get(player);
            if (cap == null) return ItemStack.EMPTY;
            var containers = cap.getContainers();
            var shulkerContainer = containers.get(ShulkerAccessoriesMod.SLOT_NAME);
            if (shulkerContainer == null) return ItemStack.EMPTY;
            if (sourceSlot < 0 || sourceSlot >= shulkerContainer.getSize()) return ItemStack.EMPTY;
            return shulkerContainer.getAccessories().getItem(sourceSlot);
        } else {
            return player.getInventory().getItem(sourceSlot);
        }
    }

    // INTERACTION //

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!fromAccessory) {
            if (lockedMenuIndex >= 0 && slotId == lockedMenuIndex) return;
            // block hotkey swap targeting the source slot (number keys or F key)
            if (clickType == ClickType.SWAP && button == sourceSlot) return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!fromAccessory && lockedMenuIndex >= 0 && index == lockedMenuIndex) {
            return ItemStack.EMPTY;
        }

        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();

            if (index < containerSize) {
                // from shulker → player inventory
                if (!moveItemStackTo(stackInSlot, containerSize, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // from player inventory → shulker
                if (!moveItemStackTo(stackInSlot, 0, containerSize, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    // SLOT TYPES //

    /** Container slot that blocks shulker box nesting (vanilla + SS). */
    private static class NoShulkerSlot extends Slot {
        public NoShulkerSlot(net.minecraft.world.Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !ShulkerAccessoriesMod.isShulkerBox(stack);
        }
    }

    /** Prevents all item interaction — used for the hand-held shulker's source slot. */
    private static class LockedSlot extends Slot {
        public LockedSlot(net.minecraft.world.Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) { return false; }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }

        // prevent moveItemStackTo from merging additional items into this slot
        @Override
        public int getMaxStackSize() { return 0; }
    }
}
