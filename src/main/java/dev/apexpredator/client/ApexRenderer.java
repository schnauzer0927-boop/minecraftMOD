package dev.apexpredator.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ApexRenderer extends GeoEntityRenderer<ApexEntity> {
    private static final ResourceLocation GLOW = new ResourceLocation(ApexPredator.MOD_ID,
            "textures/entity/apex_glow.png");

    public ApexRenderer(EntityRendererProvider.Context context) {
        super(context, new ApexModel());
        shadowRadius = 0.75F;
        addRenderLayer(new Glow(this));
        addRenderLayer(new ApexAfterimageLayer(this));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ApexRegistries.APEX.get(), ApexRenderer::new);
        event.registerEntityRenderer(ApexRegistries.APEX_BOLT.get(), ApexBoltRenderer::new);
    }

    @Override
    protected float getDeathMaxRotation(ApexEntity entity) {
        // The death animation already performs the authored collapse.
        return 0;
    }

    private static final class Glow extends GeoRenderLayer<ApexEntity> {
        private Glow(ApexRenderer renderer) { super(renderer); }

        @Override
        public void render(PoseStack stack, ApexEntity entity, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            if (entity.isInvisible()) return;
            float brightness = switch (entity.getPhase()) {
                case 2 -> 0.84F;
                case 3 -> 1.0F;
                default -> 0.68F;
            };
            brightness *= 0.96F + Mth.sin((entity.tickCount + partialTick) * 0.16F) * 0.04F;
            RenderType glow = RenderType.eyes(GLOW);
            getRenderer().reRender(model, stack, buffers, entity, glow, buffers.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    brightness, brightness, brightness, 1);
        }
    }
}
