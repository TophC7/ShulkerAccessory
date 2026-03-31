package dev.shulkeraccessories;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Mod(ShulkerAccessoriesMod.MOD_ID)
public class ShulkerAccessoriesMod {

    public static final String MOD_ID = "shulker_accessories";
    public static final String SLOT_NAME = "shulker_box";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int SWITCH_COOLDOWN_TICKS = 5; // 250ms at 20tps
    private static final Map<UUID, Integer> switchCooldowns = new HashMap<>();

    // REGISTRY //

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MOD_ID);

    public static final Supplier<MenuType<ShulkerAccessoryMenu>> SHULKER_MENU =
            MENU_TYPES.register("shulker_box",
                    () -> IMenuTypeExtension.create(ShulkerAccessoryMenu::fromNetwork));

    public ShulkerAccessoriesMod(IEventBus modEventBus) {
        LOGGER.info("Shulker Accessories loaded");

        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::onCommonSetup);

        SophisticatedCompat.init();
        registerShulkerAccessories();

        // register SS compat menu type (if SS is loaded otherwise class is never touched)
        if (SophisticatedCompat.isLoaded()) {
            dev.shulkeraccessories.compat.ss.SSMenuCompat.register(modEventBus);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // SS items are registered
        // Safe to look them up and register as accessories
        event.enqueueWork(SophisticatedCompat::registerAccessories);
    }

    private static void registerShulkerAccessories() {
        // disable right-click-to-equip 
        // We handle right-click ourselves to open the UI
        Accessory accessory = new Accessory() {
            @Override
            public boolean canEquipFromUse(ItemStack stack) {
                return false;
            }
        };
        // undyed shulker
        AccessoriesAPI.registerAccessory(ShulkerBoxBlock.getBlockByColor(null).asItem(), accessory);
        // all 16 dyed variants
        for (DyeColor color : DyeColor.values()) {
            AccessoriesAPI.registerAccessory(ShulkerBoxBlock.getBlockByColor(color).asItem(), accessory);
        }
    }

    // OPENING //

    /** Open a shulker from accessory slots. Pass -1 for targetSlot to use first equipped. */
    public static void openShulkerFromAccessory(ServerPlayer player, int targetSlot) {
        if (player.containerMenu instanceof ShulkerAccessoryMenu current
                && current.isFromAccessory() && current.getSourceSlot() == targetSlot) {
            return; // already viewing this slot
        }

        // rate limit tab switches to prevent packet spam
        int tick = player.getServer().getTickCount();
        UUID uuid = player.getUUID();
        Integer lastTick = switchCooldowns.get(uuid);
        if (lastTick != null && tick - lastTick < SWITCH_COOLDOWN_TICKS) return;
        switchCooldowns.put(uuid, tick);

        var cap = AccessoriesCapability.get(player);
        if (cap == null) return;

        var containers = cap.getContainers();
        var shulkerContainer = containers.get(SLOT_NAME);
        if (shulkerContainer == null) return;

        int slotIndex = -1;
        ItemStack shulkerStack = ItemStack.EMPTY;

        if (targetSlot >= 0 && targetSlot < shulkerContainer.getSize()) {
            ItemStack stack = shulkerContainer.getAccessories().getItem(targetSlot);
            if (isShulkerBox(stack)) {
                slotIndex = targetSlot;
                shulkerStack = stack;
            }
        }

        // fallback: find first equipped shulker
        if (slotIndex < 0) {
            for (int i = 0; i < shulkerContainer.getSize(); i++) {
                ItemStack stack = shulkerContainer.getAccessories().getItem(i);
                if (isShulkerBox(stack)) {
                    slotIndex = i;
                    shulkerStack = stack;
                    break;
                }
            }
        }

        if (slotIndex < 0) return;

        // SS shulkers get their native UI with upgrades and settings
        if (SophisticatedCompat.isSSShulkerBox(shulkerStack)) {
            dev.shulkeraccessories.compat.ss.SSMenuCompat.openFromAccessory(player, slotIndex, shulkerStack);
            return;
        }

        SimpleContainer container = loadContents(shulkerStack);
        int colorId = getColorId(shulkerStack);
        int containerSize = container.getContainerSize();
        final int finalSlot = slotIndex;
        final ItemStack stackRef = shulkerStack;

        player.openMenu(new SimpleMenuProvider(
                (containerId, inv, p) -> new ShulkerAccessoryMenu(
                        containerId, inv, container, finalSlot, true, colorId, stackRef),
                shulkerStack.getHoverName()
        ), buf -> {
            buf.writeVarInt(finalSlot);
            buf.writeBoolean(true);
            buf.writeVarInt(colorId);
            buf.writeVarInt(containerSize);
        });
    }

    /** Open a shulker held in the player's hand. */
    public static void openShulkerFromHand(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isShulkerBox(held)) return;
        if (player.containerMenu instanceof ShulkerAccessoryMenu) return;

        // SS shulkers get their native UI with upgrades and settings
        if (SophisticatedCompat.isSSShulkerBox(held)) {
            dev.shulkeraccessories.compat.ss.SSMenuCompat.openFromHand(player, hand);
            return;
        }

        int slot = (hand == InteractionHand.MAIN_HAND)
                ? player.getInventory().selected
                : 40;

        SimpleContainer container = loadContents(held);
        int colorId = getColorId(held);
        int containerSize = container.getContainerSize();

        player.openMenu(new SimpleMenuProvider(
                (containerId, inv, p) -> new ShulkerAccessoryMenu(
                        containerId, inv, container, slot, false, colorId, held),
                held.getHoverName()
        ), buf -> {
            buf.writeVarInt(slot);
            buf.writeBoolean(false);
            buf.writeVarInt(colorId);
            buf.writeVarInt(containerSize);
        });
    }

    // HELPERS //

    public static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock) return true;
        return SophisticatedCompat.isSSShulkerBox(stack);
    }

    /** Load a vanilla shulker item's contents into a live container. SS shulkers use SSMenuCompat. */
    public static SimpleContainer loadContents(ItemStack shulkerStack) {
        SimpleContainer container = new SimpleContainer(27);
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            contents.copyInto(items);
            for (int i = 0; i < items.size(); i++) {
                container.setItem(i, items.get(i));
            }
        }
        return container;
    }

    /** Save a live container back into a vanilla shulker item's component data. */
    public static void saveContents(ItemStack shulkerStack, SimpleContainer container) {
        int size = container.getContainerSize();
        NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < size; i++) {
            items.set(i, container.getItem(i));
        }
        shulkerStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    public static int getColorId(ItemStack shulkerStack) {
        DyeColor color = ShulkerBoxBlock.getColorFromItem(shulkerStack.getItem());
        return color != null ? color.getId() : -1;
    }

    // NETWORKING //

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(MOD_ID)
                .playToServer(
                        OpenShulkerPayload.TYPE,
                        OpenShulkerPayload.STREAM_CODEC,
                        OpenShulkerPayload::handle)
                .playToServer(
                        SwitchShulkerTabPayload.TYPE,
                        SwitchShulkerTabPayload.STREAM_CODEC,
                        SwitchShulkerTabPayload::handle);
    }

    // RIGHT-CLICK EVENTS //

    /** Intercept right-click to open held shulker boxes instead of placing them. */
    @EventBusSubscriber(modid = MOD_ID)
    public static class InteractionEvents {

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getEntity().isShiftKeyDown()) return;
            if (!isShulkerBox(event.getItemStack())) return;

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);

            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer sp) {
                openShulkerFromHand(sp, event.getHand());
            }
        }

        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (event.getEntity().isShiftKeyDown()) return;
            if (!isShulkerBox(event.getItemStack())) return;

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);

            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer sp) {
                openShulkerFromHand(sp, event.getHand());
            }
        }
    }
}
