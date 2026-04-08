package dev.shulkeraccessories.compat.ss;

import dev.shulkeraccessories.ShulkerAccessoriesMod;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import xyz.kwahson.compat.ss.SSCompat;
import xyz.kwahson.compat.ss.SSVirtualHost;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side per-player ticker for SS shulkers equipped in accessory slots.
 *
 * <p>Placed SS shulkers tick their {@link net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade}s
 * from {@code StorageBlockEntity.serverTick}. An accessory-equipped shulker has
 * no block entity, so without this ticker every tickable upgrade -- magnet,
 * feeding, smelting, brewing, jukebox, xp pump, tool-swap -- silently does
 * nothing while equipped. We replicate the placed-block tick path through
 * {@link SSVirtualHost}, one host per (player, slot) pair, with the wearer
 * supplied as the upgrade context entity (so feeding/xp pump/tool-swap can
 * target them, which placed blocks can't).
 *
 * <p><b>Loading rule:</b> hard-links SS and Accessories types via
 * {@link SSVirtualHost} and {@link AccessoriesCapability}. The class is only
 * loaded when {@link ShulkerAccessoriesMod} explicitly calls
 * {@link #register(IEventBus)} from its constructor inside an
 * {@link SSCompat#isLoaded()} guard. Do not annotate with
 * {@code @EventBusSubscriber} -- that would force-load the class on every
 * mod-loading run regardless of whether SS is present, and link the SS types
 * eagerly.
 *
 * <p><b>Lifetime:</b> hosts are cached per player UUID and per slot index. The
 * cache is pruned every tick (slots that no longer hold an SS shulker drop
 * their host), and the whole player entry is dropped on logout <em>and on
 * respawn</em> -- respawn creates a fresh {@link ServerPlayer} instance with
 * the same UUID, and the old hosts captured the previous {@code ServerPlayer}
 * in their suppliers. Without the respawn purge those suppliers would forever
 * read from the dead player's level/position, ticking upgrades against a
 * removed entity. Stack identity inside a single slot is handled by the host
 * itself (it rebuilds its wrapper cache when the supplier returns a different
 * stack reference), so no manual invalidation is needed when the player swaps
 * one shulker for another in the same slot.
 */
public final class SSAccessoryTicker {

    /**
     * Per-player slot hosts. Outer key: player UUID. Inner key: slot index in
     * the {@link ShulkerAccessoriesMod#SLOT_NAME} accessory container. Server
     * thread only -- {@link PlayerTickEvent.Post} for server players runs on
     * the server thread, and {@link PlayerEvent.PlayerLoggedOutEvent} fires
     * there too.
     */
    private static final Map<UUID, Map<Integer, SSVirtualHost>> hostsByPlayer = new HashMap<>();

    private SSAccessoryTicker() {}

    /** Wire the ticker into the game event bus. Call from mod constructor only when SS is loaded. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(SSAccessoryTicker::onPlayerTick);
        gameBus.addListener(SSAccessoryTicker::onPlayerLoggedOut);
        gameBus.addListener(SSAccessoryTicker::onPlayerRespawn);
        gameBus.addListener(SSAccessoryTicker::onPlayerChangedDimension);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        // PlayerTickEvent.Post fires on the server thread for ServerPlayer
        // instances and on the client thread for the local player. Gating on
        // the player type instead of level.isClientSide() is the same check
        // but reads as the actual contract: "I want the server's view."
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        // Between Player.die() and respawn, the ServerPlayer still receives
        // tick events but is not meaningfully in the world. Skip the corpse
        // window to avoid wasted upgrade ticks against a dead entity.
        if (serverPlayer.isDeadOrDying()) return;

        AccessoriesCapability cap = AccessoriesCapability.get(serverPlayer);
        if (cap == null) return;
        AccessoriesContainer container = cap.getContainers().get(ShulkerAccessoriesMod.SLOT_NAME);
        if (container == null) return;

        UUID playerUuid = serverPlayer.getUUID();
        Map<Integer, SSVirtualHost> hosts = hostsByPlayer.get(playerUuid);
        int size = container.getSize();

        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = container.getAccessories().getItem(slot);
            boolean isSS = !stack.isEmpty() && SSCompat.isSSShulkerBox(stack);

            if (!isSS) {
                // Slot is empty or holds a vanilla shulker -- nothing to tick.
                // Drop any host that was lingering for this slot.
                if (hosts != null) {
                    SSVirtualHost stale = hosts.remove(slot);
                    if (stale != null) stale.invalidate();
                }
                continue;
            }

            if (hosts == null) {
                hosts = new HashMap<>();
                hostsByPlayer.put(playerUuid, hosts);
            }
            SSVirtualHost host = hosts.get(slot);
            if (host == null) {
                final int finalSlot = slot;
                // Suppliers re-read the slot every tick. If the player swaps
                // shulkers in this slot the supplier returns the new stack;
                // SSVirtualHost detects the identity mismatch and rebuilds its
                // wrapper cache without help from us. The wearer is passed as
                // the upgrade context entity so feeding/xp pump/tool-swap can
                // target them.
                //
                // The serverPlayer reference captured here is per-host, which
                // is why respawn must purge the whole player entry: a fresh
                // ServerPlayer instance after death would never match this
                // capture, but the UUID does, so a stale host would otherwise
                // keep ticking against the dead player's level and position.
                host = new SSVirtualHost(
                        () -> readSlotSafely(serverPlayer, finalSlot),
                        serverPlayer::level,
                        serverPlayer::blockPosition,
                        () -> serverPlayer);
                hosts.put(slot, host);
            }
            host.tick();
        }

        // Player has no SS shulkers equipped at all -- prune the player entry
        // so the outer map doesn't grow with empty inner maps after every
        // unequip.
        if (hosts != null && hosts.isEmpty()) {
            hostsByPlayer.remove(playerUuid);
        }
    }

    /**
     * Read the current stack at this accessory slot, defending against the
     * container being torn down between when the host was created and when its
     * supplier is invoked. Returns {@link ItemStack#EMPTY} on any structural
     * mismatch -- the host treats EMPTY as "source gone" and invalidates
     * cleanly.
     */
    private static ItemStack readSlotSafely(ServerPlayer player, int slot) {
        AccessoriesCapability cap = AccessoriesCapability.get(player);
        if (cap == null) return ItemStack.EMPTY;
        AccessoriesContainer container = cap.getContainers().get(ShulkerAccessoriesMod.SLOT_NAME);
        if (container == null) return ItemStack.EMPTY;
        if (slot < 0 || slot >= container.getSize()) return ItemStack.EMPTY;
        return container.getAccessories().getItem(slot);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        purge(serverPlayer.getUUID());
    }

    /**
     * Respawn replaces the {@link ServerPlayer} instance while preserving
     * the UUID. Hosts captured the previous instance in their suppliers, so
     * keeping them around would tick upgrades against a removed entity at the
     * frozen death position. Drop the whole player entry; the next tick after
     * respawn rebuilds hosts against the new instance.
     */
    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        purge(serverPlayer.getUUID());
    }

    /**
     * Dimension change in 1.21.1 does not recreate the {@link ServerPlayer},
     * and the captured {@code serverPlayer::level} supplier returns the new
     * level automatically via the live reference. Strictly redundant today --
     * the hosts remain valid across the teleport. Kept as a defensive purge
     * in case vanilla ever changes teleport semantics; rebuilding next tick
     * is cheap.
     */
    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        purge(serverPlayer.getUUID());
    }

    private static void purge(UUID playerUuid) {
        Map<Integer, SSVirtualHost> hosts = hostsByPlayer.remove(playerUuid);
        if (hosts != null) {
            // Drop wrapper caches eagerly so the SS storage repository can GC
            // any wrappers it was holding for these stacks. The map is already
            // orphaned by the remove() above; no need to iterate-and-remove.
            hosts.values().forEach(SSVirtualHost::invalidate);
        }
    }
}
