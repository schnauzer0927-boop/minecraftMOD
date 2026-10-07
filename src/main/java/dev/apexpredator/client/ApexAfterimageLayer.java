package dev.apexpredator.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.apexpredator.ApexClientConfig;
import dev.apexpredator.combat.CombatState;
import dev.apexpredator.entity.ApexEntity;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Short-lived copies of the actual posed model, rather than spawned gameplay entities. */
public final class ApexAfterimageLayer extends GeoRenderLayer<ApexEntity> {
    private static final int MAX_IMAGES = 4;
    private static final int LIFETIME = 10;
    private static final Map<UUID, Deque<GhostRequest>> REQUESTS = new LinkedHashMap<>();
    private static WeakReference<ClientLevel> requestLevel = new WeakReference<>(null);
    private final Map<UUID, Trail> trails = new LinkedHashMap<>();
    private WeakReference<ClientLevel> observedLevel = new WeakReference<>(null);

    ApexAfterimageLayer(ApexRenderer renderer) { super(renderer); }

    /** Called on the client thread for a server-announced Phase 3 illusion. */
    public static void addGhost(ApexEntity entity, Vec3 position, int duration) {
        updateRequestLevel();
        if (!ApexClientConfig.AFTER_IMAGES.get()) return;
        Deque<GhostRequest> requests = REQUESTS.computeIfAbsent(entity.getUUID(), id -> new ArrayDeque<>());
        requests.addLast(new GhostRequest(position, Math.max(1, Math.min(30, duration)), entity.tickCount));
        while (requests.size() > MAX_IMAGES) requests.removeFirst();
        if (REQUESTS.size() > 64) REQUESTS.remove(REQUESTS.keySet().iterator().next());
    }

    private static void updateRequestLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (requestLevel.get() != level) {
            REQUESTS.clear();
            requestLevel = new WeakReference<>(level);
        }
    }

    @Override
    public void render(PoseStack stack, ApexEntity entity, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        ClientLevel level = Minecraft.getInstance().level;
        updateRequestLevel();
        if (observedLevel.get() != level || !ApexClientConfig.AFTER_IMAGES.get()) {
            trails.clear();
            observedLevel = new WeakReference<>(level);
            if (!ApexClientConfig.AFTER_IMAGES.get()) REQUESTS.clear();
        }
        if (!ApexClientConfig.AFTER_IMAGES.get() || !entity.isAlive() || entity.isInvisible()) return;

        Trail trail = trails.computeIfAbsent(entity.getUUID(), id -> new Trail(model));
        if (trail.model != model) {
            trail = new Trail(model);
            trails.put(entity.getUUID(), trail);
        }
        if (trails.size() > 64) trails.remove(trails.keySet().iterator().next());
        int tick = entity.tickCount;
        trail.images.removeIf(image -> tick - image.tick >= image.lifetime);
        Vec3 position = entity.getPosition(partialTick);
        float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        List<BonePose> livePose = capture(model);

        if (tick != trail.lastCaptureTick) {
            boolean movingFast = entity.getDeltaMovement().horizontalDistanceSqr() > 0.10;
            CombatState state = entity.getCombatState();
            boolean evasive = state == CombatState.DODGE || state == CombatState.CHARGE
                    || state == CombatState.TELEPORT;
            boolean relocated = trail.previous != null && position.distanceToSqr(trail.previous.position) > 1.2;
            if (trail.previous != null && (relocated || (movingFast || evasive) && tick % 2 == 0)) {
                trail.images.addLast(trail.previous);
                while (trail.images.size() > MAX_IMAGES) trail.images.removeFirst();
            }
            trail.previous = new Image(position, yaw, tick, LIFETIME, 0.28F, livePose);
            trail.lastCaptureTick = tick;
        }

        Deque<GhostRequest> requests = REQUESTS.remove(entity.getUUID());
        if (requests != null) {
            for (GhostRequest request : requests) {
                int remaining = request.duration - (tick - request.tick);
                if (remaining > 0) trail.images.addLast(new Image(request.position, yaw, tick,
                        remaining, 0.22F, livePose));
            }
            while (trail.images.size() > MAX_IMAGES) trail.images.removeFirst();
        }

        RenderType translucent = RenderType.entityTranslucent(getTextureResource(entity));
        try {
            for (Image image : trail.images) {
                float life = 1 - (tick + partialTick - image.tick) / image.lifetime;
                if (life <= 0 || position.distanceToSqr(image.position) < 0.025) continue;
                image.pose.forEach(BonePose::apply);
                stack.pushPose();
                try {
                    Vec3 offset = image.position.subtract(position);
                    stack.translate(offset.x, offset.y, offset.z);
                    // reRender applies the current body yaw; this delta restores the captured yaw.
                    stack.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(yaw - image.yaw)));
                    getRenderer().reRender(model, stack, buffers, entity, translucent,
                            buffers.getBuffer(translucent), partialTick, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, 1.0F, 0.68F, 0.42F, image.alpha * life);
                } finally {
                    stack.popPose();
                }
            }
        } finally {
            // Baked bones are shared by render passes. Never let a ghost's pose leak into the boss.
            livePose.forEach(BonePose::apply);
        }
    }

    private static List<BonePose> capture(BakedGeoModel model) {
        List<BonePose> pose = new ArrayList<>();
        model.topLevelBones().forEach(bone -> capture(bone, pose));
        return pose;
    }

    private static void capture(GeoBone bone, List<BonePose> pose) {
        pose.add(new BonePose(bone, bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                bone.getPosX(), bone.getPosY(), bone.getPosZ(), bone.getScaleX(), bone.getScaleY(),
                bone.getScaleZ(), bone.isHidden(), bone.isHidingChildren(), bone.hasRotationChanged(),
                bone.hasPositionChanged(), bone.hasScaleChanged()));
        bone.getChildBones().forEach(child -> capture(child, pose));
    }

    private record BonePose(GeoBone bone, float rx, float ry, float rz, float x, float y, float z,
                            float sx, float sy, float sz, boolean hidden, boolean childrenHidden,
                            boolean rotationChanged, boolean positionChanged, boolean scaleChanged) {
        private void apply() {
            bone.setRotX(rx); bone.setRotY(ry); bone.setRotZ(rz);
            bone.setPosX(x); bone.setPosY(y); bone.setPosZ(z);
            bone.setScaleX(sx); bone.setScaleY(sy); bone.setScaleZ(sz);
            bone.setHidden(hidden); bone.setChildrenHidden(childrenHidden);
            bone.resetStateChanges();
            if (rotationChanged) bone.markRotationAsChanged();
            if (positionChanged) bone.markPositionAsChanged();
            if (scaleChanged) bone.markScaleAsChanged();
        }
    }

    private record Image(Vec3 position, float yaw, int tick, int lifetime, float alpha, List<BonePose> pose) { }
    private record GhostRequest(Vec3 position, int duration, int tick) { }

    private static final class Trail {
        private final BakedGeoModel model;
        private final Deque<Image> images = new ArrayDeque<>();
        private Image previous;
        private int lastCaptureTick = Integer.MIN_VALUE;

        private Trail(BakedGeoModel model) { this.model = model; }
    }
}
