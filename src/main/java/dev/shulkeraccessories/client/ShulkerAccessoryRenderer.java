package dev.shulkeraccessories.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders equipped shulker boxes on the player's right hip, stacked front to back.
 * Each shulker renders as its actual item model, preserving color/tier appearance.
 *
 * Uses body.translateAndRotate directly for predictable coordinate control.
 * After that call, coordinates are in blocks aligned with the body part:
 *   +X = player's left, -X = player's right
 *   +Y = down, -Y = up
 *   +Z = forward (player facing direction)
 */
public class ShulkerAccessoryRenderer implements AccessoryRenderer {

    static final ShulkerAccessoryRenderer INSTANCE = new ShulkerAccessoryRenderer();

    // LAYOUT (all values in blocks; 1 pixel = 1/16 block) //
    // body pivot is top center of torso. -X = right, +Y = down, +Z = forward

    private static final float SIDE_OFFSET = -0.26f;    // how far right from body center (negative = right)
    private static final float HEIGHT_OFFSET = 0.80f;   // how far down from body pivot (positive = lower)
    private static final float FORWARD_OFFSET = 0.09f;  // how far forward from body center (negative = forward)
    private static final float STACKING_GAP = 0.09f;    // front-to-back gap between each shulker
    private static final float SIZE = 0.12f;             // render scale (1.0 = full block)

    @Override
    public <M extends LivingEntity> void render(
            ItemStack stack, SlotReference ref, PoseStack pose,
            EntityModel<M> model, MultiBufferSource buffer,
            int light, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, float scale) {

        if (!(model instanceof HumanoidModel<?> humanoid)) return;
        if (stack.isEmpty()) return;

        int slot = ref.slot();

        pose.pushPose();

        // align with body rotation (walking sway, turning, etc.)
        humanoid.body.translateAndRotate(pose);

        // right hip, stacked front to back (slot 0 = frontmost)
        float forward = FORWARD_OFFSET - (slot * STACKING_GAP);
        pose.translate(SIDE_OFFSET, HEIGHT_OFFSET, forward);

        pose.scale(SIZE, SIZE, SIZE);

        // render the item model (handles vanilla colors and SS tier models automatically)
        // level can be null during world transitions; seed=0 is safe without it
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffer,
                null, 0);

        pose.popPose();
    }
}
