package dev.lovepaw.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.lovepaw.client.PetAssets;
import dev.lovepaw.client.PetInstance;
import dev.lovepaw.model.Vec3f;
import dev.lovepaw.model.anim.BonePose;
import dev.lovepaw.model.geo.baked.BakedBone;
import dev.lovepaw.model.geo.baked.BakedCube;
import dev.lovepaw.model.geo.baked.BakedQuad;
import dev.lovepaw.model.geo.baked.BakedVertex;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetRenderSettings;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Draws a pet in the world.
 *
 * <p>The model arrives already in Minecraft's coordinates (see
 * {@link dev.lovepaw.model.geo.GeoBaker}), so the only thing done here is the
 * usual entity turn: rotate by {@code 180 - yaw}, then walk the bone tree
 * applying rest pose plus animation.
 */
public final class PetRenderer {
    private static final ResourceLocation SHADOW_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/shadow.png");
    private static final int SHADOW_MAX_DROP = 2;
    private static final float SHADOW_STRENGTH = 0.5f;

    private PetRenderer() {
    }

    public static void render(PetInstance pet,
                              BlockAndTintGetter level,
                              PoseStack poseStack,
                              MultiBufferSource buffers,
                              Vec3 cameraPosition,
                              float partialTick) {
        PetAssets assets = pet.assets();
        PetDefinition definition = pet.definition();
        PetRenderSettings settings = pet.renderSettings();

        Vec3 position = pet.renderPosition(partialTick);
        Map<String, BonePose> pose = pet.pose();

        int light = settings.glow()
                ? LightTexture.FULL_BRIGHT
                : LevelRenderer.getLightColor(level, BlockPos.containing(position.x, position.y + 0.1, position.z));

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(definition.texture()));

        poseStack.pushPose();
        poseStack.translate(
                position.x - cameraPosition.x,
                position.y - cameraPosition.y,
                position.z - cameraPosition.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - pet.renderYaw(partialTick)));

        float scale = settings.scale();
        if (scale != 1f) {
            poseStack.scale(scale, scale, scale);
        }
        if (settings.yOffset() != 0) {
            poseStack.translate(0, settings.yOffset(), 0);
        }

        for (BakedBone bone : assets.model().roots()) {
            renderBone(bone, pose, poseStack, consumer, light);
        }

        poseStack.popPose();

        if (settings.shadowRadius() > 0) {
            renderShadow(level, poseStack, buffers, position, cameraPosition, settings.shadowRadius() * scale);
        }
    }

    private static void renderShadow(BlockAndTintGetter level,
                                     PoseStack poseStack,
                                     MultiBufferSource buffers,
                                     Vec3 position,
                                     Vec3 cameraPosition,
                                     float radius) {
        double groundY = Double.NaN;
        for (int step = 0; step <= SHADOW_MAX_DROP; step++) {
            BlockPos pos = BlockPos.containing(position.x, position.y - step, position.z);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                groundY = pos.getY() + shape.max(Direction.Axis.Y);
                break;
            }
        }
        if (Double.isNaN(groundY)) {
            return;
        }

        double height = position.y - groundY;
        if (height < 0 || height > SHADOW_MAX_DROP) {
            return;
        }
        float alpha = (float) (1 - height / SHADOW_MAX_DROP) * SHADOW_STRENGTH;

        poseStack.pushPose();
        poseStack.translate(
                position.x - cameraPosition.x,
                groundY + 0.015 - cameraPosition.y,
                position.z - cameraPosition.z);

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityShadow(SHADOW_TEXTURE));
        PoseStack.Pose last = poseStack.last();
        shadowVertex(consumer, last, -radius, -radius, 0, 0, alpha);
        shadowVertex(consumer, last, -radius, radius, 0, 1, alpha);
        shadowVertex(consumer, last, radius, radius, 1, 1, alpha);
        shadowVertex(consumer, last, radius, -radius, 1, 0, alpha);

        poseStack.popPose();
    }

    private static void shadowVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                     float x, float z, float u, float v, float alpha) {
        consumer.addVertex(pose, x, 0, z)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    private static void renderBone(BakedBone bone,
                                   Map<String, BonePose> pose,
                                   PoseStack poseStack,
                                   VertexConsumer consumer,
                                   int light) {
        BonePose animated = pose.getOrDefault(bone.name(), BonePose.REST);

        poseStack.pushPose();

        Vec3f offset = animated.position();
        if (!offset.isZero()) {
            poseStack.translate(offset.x(), offset.y(), offset.z());
        }

        Vec3f pivot = bone.pivot();
        poseStack.translate(pivot.x(), pivot.y(), pivot.z());
        rotateZyx(poseStack,
                bone.rotation().x() + animated.rotation().x(),
                bone.rotation().y() + animated.rotation().y(),
                bone.rotation().z() + animated.rotation().z());

        Vec3f boneScale = animated.scale();
        if (!boneScale.isOne()) {
            poseStack.scale(boneScale.x(), boneScale.y(), boneScale.z());
        }

        poseStack.translate(-pivot.x(), -pivot.y(), -pivot.z());

        for (BakedCube cube : bone.cubes()) {
            renderCube(cube, poseStack, consumer, light);
        }
        for (BakedBone child : bone.children()) {
            renderBone(child, pose, poseStack, consumer, light);
        }

        poseStack.popPose();
    }

    private static void renderCube(BakedCube cube, PoseStack poseStack, VertexConsumer consumer, int light) {
        poseStack.pushPose();

        if (cube.hasRotation()) {
            Vec3f pivot = cube.pivot();
            poseStack.translate(pivot.x(), pivot.y(), pivot.z());
            rotateZyx(poseStack, cube.rotation().x(), cube.rotation().y(), cube.rotation().z());
            poseStack.translate(-pivot.x(), -pivot.y(), -pivot.z());
        }

        PoseStack.Pose last = poseStack.last();
        for (BakedQuad quad : cube.quads()) {
            for (BakedVertex vertex : quad.vertices()) {
                consumer.addVertex(last, vertex.x(), vertex.y(), vertex.z())
                        .setColor(255, 255, 255, 255)
                        .setUv(vertex.u(), vertex.v())
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(last, quad.normalX(), quad.normalY(), quad.normalZ());
            }
        }

        poseStack.popPose();
    }

    private static void rotateZyx(PoseStack poseStack, float x, float y, float z) {
        if (z != 0) {
            poseStack.mulPose(Axis.ZP.rotation(z));
        }
        if (y != 0) {
            poseStack.mulPose(Axis.YP.rotation(y));
        }
        if (x != 0) {
            poseStack.mulPose(Axis.XP.rotation(x));
        }
    }
}
