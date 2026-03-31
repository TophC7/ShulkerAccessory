package dev.shulkeraccessories;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S packet sent when the player clicks a tab to switch to a different shulker. */
public record SwitchShulkerTabPayload(int targetSlot) implements CustomPacketPayload {

    public static final Type<SwitchShulkerTabPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShulkerAccessoriesMod.MOD_ID, "switch_shulker_tab"));

    public static final StreamCodec<FriendlyByteBuf, SwitchShulkerTabPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.targetSlot),
                    buf -> new SwitchShulkerTabPayload(buf.readVarInt()));

    public static void handle(SwitchShulkerTabPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                // basic bounds check — real validation happens in openShulkerFromAccessory
                if (payload.targetSlot >= 0 && payload.targetSlot < 64) {
                    ShulkerAccessoriesMod.openShulkerFromAccessory(sp, payload.targetSlot);
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
