package dev.apexpredator.client;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Authored skeletal model. Tracking adds to an animation instead of replacing its head pose. */
public final class ApexModel extends GeoModel<ApexEntity> {
    private static final ResourceLocation GEOMETRY = resource("geo/apex.geo.json");
    private static final ResourceLocation TEXTURE = resource("textures/entity/apex.png");
    private static final ResourceLocation ANIMATIONS = resource("animations/apex.animation.json");
    private final Map<UUID, PhaseBlend> phases = new LinkedHashMap<>();
    private WeakReference<ClientLevel> observedLevel = new WeakReference<>(null);

    private static ResourceLocation resource(String path) {
        return new ResourceLocation(ApexPredator.MOD_ID, path);
    }

    @Override
    public ResourceLocation getModelResource(ApexEntity entity) { return GEOMETRY; }

    @Override
    public ResourceLocation getTextureResource(ApexEntity entity) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(ApexEntity entity) { return ANIMATIONS; }

    @Override
    public void setCustomAnimations(ApexEntity entity, long instanceId, AnimationState<ApexEntity> state) {
        EntityModelData view = state.getData(DataTickets.ENTITY_MODEL_DATA);
        float tracking = switch (entity.getCombatState()) {
            case IDLE, CHASE, STRAFE, RECOVERY -> 1.0F;
            case SPECIAL -> 0.2F;
            default -> 0.4F;
        };
        if (!entity.isAlive()) tracking = 0;
        final float gain = tracking;
        if (view != null) {
            getBone("head").ifPresent(head -> {
                head.setRotX(head.getRotX() + Mth.clamp(view.headPitch(), -22, 22) * Mth.DEG_TO_RAD * gain);
                head.setRotY(head.getRotY() + Mth.clamp(view.netHeadYaw(), -35, 35) * Mth.DEG_TO_RAD * gain);
            });
        }

        float expansion = phaseBlend(entity, state.getPartialTick());
        getBone("back_spines").ifPresent(spines -> {
            spines.setScaleX(1 + expansion * 0.16F);
            spines.setScaleY(1 + expansion * 0.05F);
            spines.setScaleZ(1 + expansion * 0.16F);
        });
    }

    private float phaseBlend(ApexEntity entity, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != observedLevel.get()) {
            phases.clear();
            observedLevel = new WeakReference<>(level);
        }
        PhaseBlend blend = phases.computeIfAbsent(entity.getUUID(), id ->
                new PhaseBlend(entity.getPhase() - 1, entity.tickCount));
        if (blend.target != entity.getPhase() - 1) {
            blend.from = blend.value(entity.tickCount + partialTick);
            blend.target = entity.getPhase() - 1;
            blend.startTick = entity.tickCount;
        }
        // Worlds may contain many independently summoned bosses; visual state stays bounded.
        if (phases.size() > 64) phases.remove(phases.keySet().iterator().next());
        return blend.value(entity.tickCount + partialTick);
    }

    private static final class PhaseBlend {
        private float from;
        private int target;
        private int startTick;

        private PhaseBlend(int phase, int tick) { from = phase; target = phase; startTick = tick; }

        private float value(float tick) {
            float progress = Mth.clamp((tick - startTick) / 20.0F, 0, 1);
            progress = progress * progress * (3 - 2 * progress);
            return Mth.lerp(progress, from, target);
        }
    }
}
