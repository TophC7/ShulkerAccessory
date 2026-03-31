package dev.shulkeraccessories.client;

import dev.shulkeraccessories.OpenShulkerPayload;
import dev.shulkeraccessories.ShulkerAccessoriesMod;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class ClientSetup {

    public static final KeyMapping OPEN_SHULKER_KEY = new KeyMapping(
            "key.shulker_accessories.open_shulker",
            GLFW.GLFW_KEY_B,
            "key.categories.shulker_accessories");

    /** Mod-bus events: screen and keybind registration. */
    @EventBusSubscriber(modid = ShulkerAccessoriesMod.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static class ModEvents {

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_SHULKER_KEY);
        }

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(ShulkerAccessoriesMod.SHULKER_MENU.get(), ShulkerAccessoryScreen::new);
        }
    }

    /** Game-bus events: keybind handling. */
    @EventBusSubscriber(modid = ShulkerAccessoriesMod.MOD_ID, value = Dist.CLIENT)
    public static class GameEvents {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var mc = Minecraft.getInstance();
            while (OPEN_SHULKER_KEY.consumeClick()) {
                if (mc.player == null) continue;

                // check that the player has at least one shulker equipped
                var cap = AccessoriesCapability.get(mc.player);
                if (cap == null) continue;

                boolean hasShulker = cap.isEquipped(stack ->
                        Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock);
                if (hasShulker) {
                    PacketDistributor.sendToServer(new OpenShulkerPayload());
                }
            }
        }
    }
}
