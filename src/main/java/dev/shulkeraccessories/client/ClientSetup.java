package dev.shulkeraccessories.client;

import dev.shulkeraccessories.OpenShulkerPayload;
import dev.shulkeraccessories.ShulkerAccessoriesMod;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import xyz.kwahson.core.compat.ss.SSCompat;

public class ClientSetup {
    public static final KeyMapping OPEN_SHULKER_KEY = new KeyMapping(
            "key.shulker_accessories.open_shulker",
            GLFW.GLFW_KEY_B,
            "key.categories.shulker_accessories");

    /** Mod-bus events: screen and keybind registration. */
    @EventBusSubscriber(modid = ShulkerAccessoriesMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) { event.register(OPEN_SHULKER_KEY); }

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(ShulkerAccessoriesMod.SHULKER_MENU.get(), ShulkerAccessoryScreen::new);
            // register SS compat screen (only if SS is loaded)
            if (SSCompat.isLoaded()) {
                dev.shulkeraccessories.compat.ss.SSMenuCompat.registerScreen(event);
            }
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // enqueueWork for thread safety — FMLClientSetupEvent fires on a worker thread
            event.enqueueWork(ClientSetup::registerRenderers);
        }
    }
    // RENDERER REGISTRATION //

    private static void registerRenderers() {
        // vanilla shulkers (undyed + 16 colors)
        AccessoriesRendererRegistry.registerRenderer(
                ShulkerBoxBlock.getBlockByColor(null).asItem(),
                () -> ShulkerAccessoryRenderer.INSTANCE);
        for (DyeColor color : DyeColor.values()) {
            AccessoriesRendererRegistry.registerRenderer(
                    ShulkerBoxBlock.getBlockByColor(color).asItem(),
                    () -> ShulkerAccessoryRenderer.INSTANCE);
        }
        // SS shulkers: walk the registry once instead of consulting a hard-coded ID list
        for (Item item : SSCompat.findAllShulkerItems()) {
            AccessoriesRendererRegistry.registerRenderer(
                    item, () -> ShulkerAccessoryRenderer.INSTANCE);
        }
    }

    /** Game-bus events: keybind handling. */
    @EventBusSubscriber(modid = ShulkerAccessoriesMod.MOD_ID, value = Dist.CLIENT)
    public static class GameEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var mc = Minecraft.getInstance();
            while (OPEN_SHULKER_KEY.consumeClick()) {
                if (mc.player == null)
                    continue;
                // check that the player has at least one shulker equipped
                var cap = AccessoriesCapability.get(mc.player);
                if (cap == null)
                    continue;
                boolean hasShulker = cap.isEquipped(ShulkerAccessoriesMod::isShulkerBox);
                if (hasShulker) {
                    PacketDistributor.sendToServer(new OpenShulkerPayload());
                }
            }
        }
    }
}
