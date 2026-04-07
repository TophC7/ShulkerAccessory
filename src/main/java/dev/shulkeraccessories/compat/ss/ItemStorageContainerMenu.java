package dev.shulkeraccessories.compat.ss;

import dev.shulkeraccessories.ShulkerAccessoriesMod;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.util.NoopStorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper;
import xyz.kwahson.core.compat.ss.SSCompat;
import xyz.kwahson.core.compat.ss.SSItemStorageMenu;

import javax.annotation.Nullable;

/**
 * SS container menu for accessory- or hand-held shulker boxes.
 * <p>
 * The base class ({@link SSItemStorageMenu}) handles the SS plumbing: wrapper
 * hookup,
 * BlockPos absence, settings stub, identity tracking. This subclass adds the
 * bits that
 * are specific to "the source is a player slot or accessory slot":
 * <ul>
 * <li>Resolving the source stack from a (slot index, fromAccessory) pair</li>
 * <li>Blocking interaction with the source slot when opened from hand</li>
 * <li>Network deserialization on the client</li>
 * </ul>
 */
public class ItemStorageContainerMenu extends SSItemStorageMenu {
    private final int sourceSlot;
    private final boolean fromAccessory;
    // SERVER //

    public ItemStorageContainerMenu(MenuType<?> menuType, int containerId, Player player,
            IStorageWrapper wrapper, int sourceSlot, boolean fromAccessory,
            @Nullable ItemStack sourceStackRef) {
        super(menuType, containerId, player, wrapper,
                () -> getStackFromSlot(player, sourceSlot, fromAccessory),
                sourceStackRef);
        this.sourceSlot = sourceSlot;
        this.fromAccessory = fromAccessory;
    }
    // CLIENT //

    public static ItemStorageContainerMenu fromNetwork(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        int sourceSlot = buf.readVarInt();
        boolean fromAccessory = buf.readBoolean();
        // resolve the source from the client-side player view, then build a wrapper
        ItemStack stack = getStackFromSlot(playerInv.player, sourceSlot, fromAccessory);
        IStorageWrapper wrapper;
        if (SSCompat.isSSShulkerBox(stack)) {
            wrapper = StackStorageWrapper.fromStack(playerInv.player.registryAccess(), stack);
        } else {
            // fallback: client view of the source is stale. Server will sync real contents.
            wrapper = NoopStorageWrapper.INSTANCE;
        }
        return new ItemStorageContainerMenu(
                SSMenuCompat.SS_SHULKER_MENU.get(), containerId, playerInv.player,
                wrapper, sourceSlot, fromAccessory, null);
    }
    // ACCESSORS //

    public int getSourceSlot() { return sourceSlot; }

    public boolean isFromAccessory() { return fromAccessory; }
    // INTERACTION //

    /**
     * When opened from hand, block all interaction with the source slot to prevent
     * the player from moving the shulker out from under itself mid-menu.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!fromAccessory) {
            // StorageContainerMenuBase lays out: storage slots, then player inv, then
            // hotbar
            int playerSlotsStart = this.realInventorySlots.size() - 36;
            int hotbarMenuStart = playerSlotsStart + 27;
            if (sourceSlot >= 0 && sourceSlot <= 8) {
                int lockedIdx = hotbarMenuStart + sourceSlot;
                if (slotId == lockedIdx)
                    return;
            }
            // also block hotkey-swap targeting the source slot
            if (clickType == ClickType.SWAP && button == sourceSlot)
                return;
        }
        super.clicked(slotId, button, clickType, player);
    }
    // HELPERS //

    private static ItemStack getStackFromSlot(Player player, int sourceSlot, boolean fromAccessory) {
        if (fromAccessory) {
            var cap = AccessoriesCapability.get(player);
            if (cap == null)
                return ItemStack.EMPTY;
            var containers = cap.getContainers();
            var shulkerContainer = containers.get(ShulkerAccessoriesMod.SLOT_NAME);
            if (shulkerContainer == null)
                return ItemStack.EMPTY;
            if (sourceSlot < 0 || sourceSlot >= shulkerContainer.getSize())
                return ItemStack.EMPTY;
            return shulkerContainer.getAccessories().getItem(sourceSlot);
        } else {
            return player.getInventory().getItem(sourceSlot);
        }
    }
}
