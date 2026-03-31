package dev.shulkeraccessories;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S packet sent when the player presses the open-shulker keybind. */
public record OpenShulkerPayload() implements CustomPacketPayload {

    public static final Type<OpenShulkerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShulkerAccessoriesMod.MOD_ID, "open_shulker"));

    public static final StreamCodec<FriendlyByteBuf, OpenShulkerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {},
                    buf -> new OpenShulkerPayload());

    public static void handle(OpenShulkerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                ShulkerAccessoriesMod.openShulkerFromAccessory(sp, -1);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
