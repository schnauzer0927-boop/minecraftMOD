package dev.apexpredator.client.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.apexpredator.ApexClientConfig;
import dev.apexpredator.client.ApexRenderTypes;
import dev.apexpredator.effect.ApexEffectPacket;
import dev.apexpredator.effect.EffectKind;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Emissive world-space ribbons, kept separate from particles so low quality retains readable warnings. */
final class ApexEffectRenderer {
    private static final int MAX_QUADS = 4096;
    private static int quads;
    private static VertexConsumer vertices;
    private static Matrix4f matrix;
    private static Vec3 camera;
    private ApexEffectRenderer() { }

    static void render(RenderLevelStageEvent event, List<ApexClientEffects.ActiveEffect> effects) {
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        try {
            camera = event.getCamera().getPosition();
            matrix = poses.last().pose();
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            vertices = buffers.getBuffer(ApexRenderTypes.WORLD_ENERGY);
            quads = 0;
            // First draw boundaries: decorative arcs can be budgeted away without hiding danger.
            for (var effect : effects) if (warning(effect.packet.kind())) draw(effect, event.getPartialTick());
            for (var effect : effects) if (!warning(effect.packet.kind())) draw(effect, event.getPartialTick());
            buffers.endBatch(ApexRenderTypes.WORLD_ENERGY);
        } finally {
            vertices = null;
            matrix = null;
            camera = null;
            poses.popPose();
        }
    }
    private static boolean warning(EffectKind kind) { return kind == EffectKind.WARNING_RING || kind == EffectKind.SPECIAL_PREPARE; }
    private static void draw(ApexClientEffects.ActiveEffect effect, float partialTick) {
        ApexEffectPacket p = effect.packet;
        float age = effect.age(partialTick), progress = Mth.clamp(age / p.duration(), 0, 1);
        float fade = (1 - progress) * .82F;
        Vec3 center = p.origin().add(0, .065, 0);
        Vec3 forward = new Vec3(p.direction().x, 0, p.direction().z).normalize();
        if (forward.lengthSqr() < .001) forward = new Vec3(0, 0, 1);
        switch (p.kind()) {
            case WARNING_RING, SPECIAL_PREPARE -> {
                float pulse = .48F + .18F * (float)Math.sin(age * .7);
                ring(center, p.radius(), .12, pulse, false);
                ring(center.add(0, .008, 0), p.radius() - .26, .035, pulse * .55F, false);
                if (p.kind() == EffectKind.SPECIAL_PREPARE) {
                    ring(center, p.radius() * .25, .06, pulse * .5F, false);
                    cracks(center, p.radius(), pulse * .18F, p.entityId(), 6);
                    warningLanes(center, forward, p.radius(), pulse);
                }
            }
            case SLAM, SHOCKWAVE, PHASE_TWO, PHASE_THREE -> {
                double expansion = p.radius() * Math.min(1, progress * 2.1);
                ring(center, Math.max(.25, expansion), .14 + progress * .07, fade, true);
                ring(center.add(0, .2 * (1 - progress), 0), Math.max(.25, expansion - .35), .05, fade * .55F, true);
                if (p.kind() == EffectKind.SLAM || p.kind() == EffectKind.PHASE_THREE) cracks(center, p.radius() * .8, fade * .65F, p.entityId(), 9);
            }
            case BLADE_LEFT, BLADE_RIGHT, SPECIAL_SLASH -> {
                boolean reverse = p.kind() == EffectKind.BLADE_RIGHT;
                arc(p.origin().add(0, 1.25, 0), forward, p.radius(), age, progress, reverse, false, fade);
                line(center, center.add(forward.scale(p.radius() * .8)), .045, fade * .3F);
            }
            case BLADE_VERTICAL -> {
                arc(p.origin().add(0, .65, 0), forward, p.radius(), age, progress, false, true, fade);
                line(center, center.add(forward.scale(p.radius())), .12, fade);
                line(center.add(.11, .012, 0), center.add(forward.scale(p.radius())).add(.11, .012, 0), .03, fade * .5F);
            }
            case BLADE_X, SPECIAL_FINISH -> {
                Vec3 right = new Vec3(forward.z, 0, -forward.x);
                Vec3 c = p.origin().add(0, 1.5, 0);
                double size = Math.min(5.5, Math.max(1.5, p.radius() * .42));
                Vec3 a = right.add(0, .65, 0).scale(size), b = right.add(0, -.65, 0).scale(size);
                line(c.subtract(a), c.add(a), .3 * (1 - progress) + .05, fade);
                line(c.subtract(b), c.add(b), .3 * (1 - progress) + .05, fade);
                line(c.subtract(a), c.add(a), .065, fade, 1F, .78F, .36F);
                line(c.subtract(b), c.add(b), .065, fade, 1F, .78F, .36F);
                cracks(center, Math.min(12, p.radius()), fade * .55F, p.entityId(), 8);
            }
            case CHARGE_PREPARE -> { ring(center, .7 + progress * .2, .065, fade, false); line(p.origin().add(0, .7, 0), p.origin().add(0, 2.2, 0), .035, fade); }
            case CHARGE_TRAIL, DODGE -> {
                Vec3 end = center.subtract(forward.scale(Math.min(4, p.radius() * 1.8)));
                line(center, end, .07, fade * .6F);
                line(center.add(0, .09, 0), end.add(0, .09, 0), .025, fade * .4F);
            }
            case TELEPORT_OUT, TELEPORT_IN -> {
                double radius = p.kind() == EffectKind.TELEPORT_OUT ? p.radius() * (1 - progress) : p.radius() * Math.min(1, progress * 2);
                ring(center, Math.max(.1, radius), .06, fade, false);
                for (int i = 0; i < 6; i++) {
                    double angle = i * Math.PI / 3 + progress;
                    Vec3 pos = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    line(pos, pos.add(0, 2.5 * (1 - progress), 0), .015, fade * .4F);
                }
            }
            case RANGED_PREPARE -> ring(p.origin().add(0, 1.7, 0), .3 + progress * .25, .04, fade * .8F, false);
            case RANGED_LAUNCH, PROJECTILE_TRAIL -> {
                Vec3 a = p.origin(), b = a.subtract(p.direction().scale(p.kind() == EffectKind.PROJECTILE_TRAIL ? .95 : 2.2));
                line(a, b, .08, fade); line(a, b, .025, fade, 1, .86F, .55F);
            }
            case HIT, PROJECTILE_IMPACT -> {
                ring(center, .25 + p.radius() * progress, .055, fade, true);
                if (p.kind() == EffectKind.PROJECTILE_IMPACT) cracks(center, p.radius() * .8, fade * .4F, p.entityId(), 4);
            }
            case MIRAGE -> { }
            case DEATH -> { ring(center, p.radius() * progress, .07, fade * .65F, true); cracks(center, p.radius() * .6, fade * .4F, p.entityId(), 6); }
            default -> { }
        }
    }
    private static void ring(Vec3 origin, double radius, double thickness, float alpha, boolean gradient) {
        int segments = ApexClientConfig.PARTICLE_QUALITY.get() == ApexClientConfig.ParticleQuality.LOW ? 32 : 48;
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
            Vec3 p0 = origin.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            Vec3 p1 = origin.add(Math.cos(b) * radius, 0, Math.sin(b) * radius);
            Vec3 p2 = origin.add(Math.cos(b) * (radius - thickness), 0, Math.sin(b) * (radius - thickness));
            Vec3 p3 = origin.add(Math.cos(a) * (radius - thickness), 0, Math.sin(a) * (radius - thickness));
            quad(p0, p1, p2, p3, 1, .3F, .035F, alpha);
            if (gradient) {
                Vec3 q0 = origin.add(Math.cos(a) * (radius + thickness), -.004, Math.sin(a) * (radius + thickness));
                Vec3 q1 = origin.add(Math.cos(b) * (radius + thickness), -.004, Math.sin(b) * (radius + thickness));
                quad(q0, q1, p1, p0, .63F, .045F, .012F, alpha * .7F);
            }
        }
    }
    private static void arc(Vec3 origin, Vec3 forward, double radius, float age, float progress, boolean reverse, boolean vertical, float alpha) {
        Vec3 side = vertical ? new Vec3(0, 1, 0) : new Vec3(forward.z, 0, -forward.x);
        double sweep = Math.min(1, age / 3.0);
        int segments = 24;
        for (int i = 0; i < segments * sweep; i++) {
            double a = -1.25 + i * 2.5 / segments, b = -1.25 + (i + 1) * 2.5 / segments;
            if (reverse) { a = -a; b = -b; }
            Vec3 unitA = forward.scale(Math.cos(a)).add(side.scale(Math.sin(a))), unitB = forward.scale(Math.cos(b)).add(side.scale(Math.sin(b)));
            double outer = radius * (.85 + .15 * progress), inner = outer - .23;
            float intensity = alpha * (.28F + .72F * i / segments);
            quad(origin.add(unitA.scale(outer)), origin.add(unitB.scale(outer)), origin.add(unitB.scale(inner)), origin.add(unitA.scale(inner)), 1F, .26F, .025F, intensity);
            quad(origin.add(unitA.scale(outer - .07)), origin.add(unitB.scale(outer - .07)), origin.add(unitB.scale(outer - .105)), origin.add(unitA.scale(outer - .105)), 1F, .76F, .32F, intensity * .9F);
        }
    }
    private static void cracks(Vec3 origin, double radius, float alpha, int seed, int arms) {
        for (int i = 0; i < arms; i++) {
            double angle = i * Math.PI * 2 / arms + (seed % 13) * .1;
            Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 side = new Vec3(-direction.z, 0, direction.x);
            Vec3 p = origin.add(direction.scale(radius * .12));
            for (int n = 1; n <= 4; n++) {
                Vec3 next = origin.add(direction.scale(radius * n / 4)).add(side.scale((n % 2 == 0 ? .14 : -.11) * radius * .12));
                line(p, next, .045, alpha); p = next;
            }
        }
    }
    private static void warningLanes(Vec3 center, Vec3 forward, double radius, float alpha) {
        for (double angle : new double[]{0, Math.PI / 3, -Math.PI / 3}) {
            double cosine = Math.cos(angle), sine = Math.sin(angle);
            Vec3 lane = new Vec3(forward.x * cosine - forward.z * sine, 0, forward.x * sine + forward.z * cosine);
            Vec3 side = new Vec3(lane.z, 0, -lane.x);
            double spread = Math.sqrt(1 - .93 * .93) * radius;
            Vec3 end = center.add(lane.scale(radius));
            line(center, end.add(side.scale(spread)), .045, alpha * .62F);
            line(center, end.subtract(side.scale(spread)), .045, alpha * .62F);
            line(center, end, .025, alpha * .3F);
        }
        for (double angle : new double[]{Math.PI / 4, -Math.PI / 4}) {
            double cosine = Math.cos(angle), sine = Math.sin(angle);
            Vec3 lane = new Vec3(forward.x * cosine - forward.z * sine, 0, forward.x * sine + forward.z * cosine);
            line(center.subtract(lane.scale(radius)), center.add(lane.scale(radius)), .035, alpha * .42F, .85F, .085F, .015F);
        }
    }
    private static void line(Vec3 start, Vec3 end, double width, float alpha) { line(start, end, width, alpha, 1F, .27F, .03F); }
    private static void line(Vec3 start, Vec3 end, double width, float alpha, float r, float g, float b) {
        Vec3 side = end.subtract(start).cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < .00001) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width);
        quad(start.add(side), end.add(side), end.subtract(side), start.subtract(side), r, g, b, alpha);
    }
    private static void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float blue, float alpha) {
        if (quads++ >= MAX_QUADS || alpha < .005F) return;
        vertex(a, r, g, blue, alpha); vertex(b, r, g, blue, alpha); vertex(c, r, g, blue, alpha); vertex(d, r, g, blue, alpha);
    }
    private static void vertex(Vec3 p, float r, float g, float b, float alpha) { vertices.vertex(matrix, (float)(p.x-camera.x), (float)(p.y-camera.y), (float)(p.z-camera.z)).color(r, g, b, alpha).endVertex(); }
}
