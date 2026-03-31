package dev.shulkeraccessories;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.AccessoriesAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;

/**
 * Lightweight compatibility layer for Sophisticated Storage.
 * Handles detection and accessory registration via reflection (no compile-time SS dependency).
 * The full SS menu integration lives in compat.ss.* (compile-time dep, guarded by isLoaded).
 */
public class SophisticatedCompat {

    private static boolean loaded = false;

    // cached reflection target for SS shulker block detection
    private static Class<?> ssShulkerBlockClass;

    private static final String[] SHULKER_IDS = {
            "sophisticatedstorage:shulker_box",
            "sophisticatedstorage:copper_shulker_box",
            "sophisticatedstorage:iron_shulker_box",
            "sophisticatedstorage:gold_shulker_box",
            "sophisticatedstorage:diamond_shulker_box",
            "sophisticatedstorage:netherite_shulker_box",
    };

    public static boolean isLoaded() { return loaded; }

    /** Initialize reflection targets. Call once during mod setup. */
    public static void init() {
        if (!ModList.get().isLoaded("sophisticatedstorage")) return;

        try {
            ssShulkerBlockClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedstorage.block.ShulkerBoxBlock");

            loaded = true;
            ShulkerAccessoriesMod.LOGGER.info("Sophisticated Storage compat initialized");
        } catch (Exception e) {
            ShulkerAccessoriesMod.LOGGER.error("Failed to init Sophisticated Storage compat - "
                    + "SS may have changed its internal class names", e);
        }
    }

    /** Register SS shulker items as accessories. Call after item registration (FMLCommonSetupEvent). */
    public static void registerAccessories() {
        if (!loaded) return;

        Accessory accessory = new Accessory() {
            @Override
            public boolean canEquipFromUse(ItemStack stack) {
                return false;
            }
        };

        for (String id : SHULKER_IDS) {
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).ifPresent(item ->
                    AccessoriesAPI.registerAccessory(item, accessory)
            );
        }
    }

    /** Check if an ItemStack is a Sophisticated Storage shulker box. */
    public static boolean isSSShulkerBox(ItemStack stack) {
        if (!loaded || stack.isEmpty()) return false;
        return ssShulkerBlockClass.isInstance(Block.byItem(stack.getItem()));
    }
}
