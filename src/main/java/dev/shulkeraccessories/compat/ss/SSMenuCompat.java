package dev.shulkeraccessories.compat.ss;

import dev.shulkeraccessories.ShulkerAccessoriesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper;

import java.util.function.Supplier;

/**
 * Handles SS menu type registration and opening logic.
 * Only loaded when Sophisticated Storage is present (guarded by ModList check).
 */
public class SSMenuCompat {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, ShulkerAccessoriesMod.MOD_ID);
    public static final Supplier<MenuType<ItemStorageContainerMenu>> SS_SHULKER_MENU = MENU_TYPES.register("ss_shulker_box",
            () -> IMenuTypeExtension.create(ItemStorageContainerMenu::fromNetwork));

    /** Call from mod constructor to register the menu type. */
    public static void register(IEventBus modEventBus) { MENU_TYPES.register(modEventBus); }

    /** Call from client setup to register the screen. */
    public static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(SS_SHULKER_MENU.get(), ItemStorageScreen::constructScreen);
    }

    /** Open an SS shulker from an accessory slot. */
    public static void openFromAccessory(ServerPlayer player, int slotIndex, ItemStack shulkerStack) {
        IStorageWrapper wrapper = StackStorageWrapper.fromStack(player.registryAccess(), shulkerStack);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inv, p) -> new ItemStorageContainerMenu(
                        SS_SHULKER_MENU.get(), containerId, p, wrapper, slotIndex, true, shulkerStack),
                shulkerStack.getHoverName()), buf -> {
                    buf.writeVarInt(slotIndex);
                    buf.writeBoolean(true);
                });
    }

    /** Open an SS shulker from the player's hand. */
    public static void openFromHand(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        int slot = (hand == InteractionHand.MAIN_HAND)
                ? player.getInventory().selected
                : 40;
        IStorageWrapper wrapper = StackStorageWrapper.fromStack(player.registryAccess(), held);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inv, p) -> new ItemStorageContainerMenu(
                        SS_SHULKER_MENU.get(), containerId, p, wrapper, slot, false, held),
                held.getHoverName()), buf -> {
                    buf.writeVarInt(slot);
                    buf.writeBoolean(false);
                });
    }
}
