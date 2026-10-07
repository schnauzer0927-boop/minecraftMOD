package dev.apexpredator.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexBoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** A glowing, elongated energy shard, aligned with its actual flight direction. */
public final class ApexBoltRenderer extends EntityRenderer<ApexBoltEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApexPredator.MOD_ID,
            "textures/entity/apex_glow.png");

    public ApexBoltRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public ResourceLocation getTextureLocation(ApexBoltEntity entity) { return TEXTURE; }

    @Override
    public void render(ApexBoltEntity entity, float yaw, float partialTick, PoseStack stack,
                       MultiBufferSource buffers, int packedLight) {
        Vec3 direction = entity.getDeltaMovement().normalize();
        if (direction.lengthSqr() < 0.001) direction = new Vec3(0, 0, 1);
        Vec3 side = direction.cross(Math.abs(direction.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = direction.cross(side).normalize();
        double spin = (entity.tickCount + partialTick) * 0.32;
        Vec3 spunSide = side.scale(Math.cos(spin)).add(up.scale(Math.sin(spin)));
        Vec3 spunUp = direction.cross(spunSide).normalize();

        // Lightning's position/color format supplies additive full-bright color and obeys scene depth.
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = stack.last().pose();
        shard(glow, matrix, direction, spunSide, spunUp, 0.58, 0.10, 255, 71, 12, 150);
        shard(glow, matrix, direction, spunSide, spunUp, 0.42, 0.043, 255, 214, 126, 235);
        super.render(entity, yaw, partialTick, stack, buffers, packedLight);
    }

    private static void shard(VertexConsumer buffer, Matrix4f matrix, Vec3 direction, Vec3 side, Vec3 up,
                              double length, double width, int red, int green, int blue, int alpha) {
        Vec3 front = direction.scale(length);
        Vec3 rear = direction.scale(-length * 0.8);
        Vec3[] rim = {side.scale(width), up.scale(width), side.scale(-width), up.scale(-width)};
        for (int i = 0; i < 4; i++) {
            Vec3 next = rim[(i + 1) % 4];
            triangle(buffer, matrix, front, rim[i], next, red, green, blue, alpha);
            triangle(buffer, matrix, rear, next, rim[i], red, green, blue, alpha);
        }
    }

    private static void triangle(VertexConsumer buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c,
                                 int red, int green, int blue, int alpha) {
        vertex(buffer, matrix, a, red, green, blue, alpha);
        vertex(buffer, matrix, b, red, green, blue, alpha);
        vertex(buffer, matrix, c, red, green, blue, alpha);
        vertex(buffer, matrix, c, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 point,
                               int red, int green, int blue, int alpha) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(red, green, blue, alpha).endVertex();
    }
}
