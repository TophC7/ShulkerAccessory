package dev.shulkeraccessories.compat.ss;

import dev.shulkeraccessories.ShulkerAccessoriesMod;
import dev.shulkeraccessories.SophisticatedCompat;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.util.NoopStorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * SS container menu for item-based shulker boxes (not placed blocks).
 * Extends SS's base menu so we get full upgrade/settings UI support.
 */
public class ItemStorageContainerMenu extends StorageContainerMenuBase<IStorageWrapper> {

    private final int sourceSlot;
    private final boolean fromAccessory;

    // reference to the exact ItemStack we opened
    // used for tamper detection
    private final @Nullable ItemStack sourceStackRef;

    // SERVER //

    public ItemStorageContainerMenu(MenuType<?> menuType, int containerId, Player player,
                                     IStorageWrapper wrapper, int sourceSlot, boolean fromAccessory,
                                     @Nullable ItemStack sourceStackRef) {
        super(menuType, containerId, player, wrapper, NoopStorageWrapper.INSTANCE, -1, false);
        this.sourceSlot = sourceSlot;
        this.fromAccessory = fromAccessory;
        this.sourceStackRef = sourceStackRef;
    }

    // CLIENT //

    public static ItemStorageContainerMenu fromNetwork(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        int sourceSlot = buf.readVarInt();
        boolean fromAccessory = buf.readBoolean();

        // get the ItemStack from the client-side inventory/accessories
        ItemStack stack = getStackFromSlot(playerInv.player, sourceSlot, fromAccessory);
        IStorageWrapper wrapper;

        if (SophisticatedCompat.isSSShulkerBox(stack)) {
            wrapper = StackStorageWrapper.fromStack(playerInv.player.registryAccess(), stack);
        } else {
            // fallback if client accessory data is stale — use noop wrapper,
            // server will sync actual slot contents via container sync
            wrapper = NoopStorageWrapper.INSTANCE;
        }

        return new ItemStorageContainerMenu(
                SSMenuCompat.SS_SHULKER_MENU.get(), containerId, playerInv.player,
                wrapper, sourceSlot, fromAccessory, null);
    }

    // ACCESSORS //

    public int getSourceSlot() { return sourceSlot; }
    public boolean isFromAccessory() { return fromAccessory; }

    // ABSTRACT OVERRIDES //

    @Override
    public Optional<BlockPos> getBlockPosition() {
        return Optional.empty();
    }

    @Override
    public Optional<Entity> getEntity() {
        return Optional.empty();
    }

    @Override
    protected StorageUpgradeSlot instantiateUpgradeSlot(UpgradeHandler handler, int slot) {
        return new StorageUpgradeSlot(handler, slot);
    }

    @Override
    public void openSettings() {
        // settings not available for item-based storage (no BlockPos)
    }

    @Override
    protected boolean storageItemHasChanged() {
        // detect if the source item was swapped out from under us
        if (sourceStackRef == null) return false;
        ItemStack current = getStackFromSlot(player, sourceSlot, fromAccessory);
        return current != sourceStackRef;
    }

    @Override
    public boolean detectSettingsChangeAndReload() {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = getStackFromSlot(player, sourceSlot, fromAccessory);
        // verify it's still a shulker AND it's the same item reference
        if (!ShulkerAccessoriesMod.isShulkerBox(stack)) return false;
        if (sourceStackRef != null && stack != sourceStackRef) return false;
        return true;
    }

    @Override
    protected void onStorageInventorySlotSet(int slot) {
        super.onStorageInventorySlotSet(slot);
    }

    // INTERACTION — block source slot manipulation when opened from hand //

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!fromAccessory) {
            // find our source slot's menu index in the player inventory section
            // StorageContainerMenuBase lays out: storage slots, then player inv, then hotbar
            // we need to block interaction with the hotbar slot holding the shulker
            int playerSlotsStart = this.realInventorySlots.size() - 36;
            int hotbarMenuStart = playerSlotsStart + 27;

            if (sourceSlot >= 0 && sourceSlot <= 8) {
                int lockedIdx = hotbarMenuStart + sourceSlot;
                if (slotId == lockedIdx) return;
            }
            // block hotkey swap targeting the source slot
            if (clickType == ClickType.SWAP && button == sourceSlot) return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    // HELPERS //

    private static ItemStack getStackFromSlot(Player player, int sourceSlot, boolean fromAccessory) {
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
}
