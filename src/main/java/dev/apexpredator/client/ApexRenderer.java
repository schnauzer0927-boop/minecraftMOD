package dev.apexpredator.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ApexRenderer extends MobRenderer<ApexEntity, ApexModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApexPredator.MOD_ID,
            "textures/entity/apex.png");
    private static final ResourceLocation EYES = new ResourceLocation(ApexPredator.MOD_ID,
            "textures/entity/apex_eyes.png");

    public ApexRenderer(EntityRendererProvider.Context context) {
        super(context, new ApexModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.9F);
        addLayer(new Eyes(this));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ApexRegistries.APEX.get(), ApexRenderer::new);
        event.registerEntityRenderer(ApexRegistries.APEX_BOLT.get(), ThrownItemRenderer::new);
    }

    @Override
    public ResourceLocation getTextureLocation(ApexEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(ApexEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(2.0F, 2.0F, 2.0F);
    }

    private static final class Eyes extends RenderLayer<ApexEntity, ApexModel> {
        private Eyes(ApexRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack stack, MultiBufferSource buffers, int packedLight,
                           ApexEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float green = entity.getPhase() == 2 ? 0.55F : 0.08F;
            float blue = entity.getPhase() == 3 ? 0.9F : 0.05F;
            getParentModel().renderToBuffer(stack, buffers.getBuffer(RenderType.eyes(EYES)),
                    15728640, OverlayTexture.NO_OVERLAY, 1.0F, green, blue, 1.0F);
        }
    }
}
