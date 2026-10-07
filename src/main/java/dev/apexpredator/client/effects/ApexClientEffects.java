package dev.apexpredator.client.effects;

import dev.apexpredator.ApexClientConfig;
import dev.apexpredator.ApexPredator;
import dev.apexpredator.effect.ApexEffectPacket;
import dev.apexpredator.effect.ApexEffects;
import dev.apexpredator.effect.EffectKind;
import dev.apexpredator.entity.ApexEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One finite effect timeline per server cue. All decorative budgets are client-only. */
@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID, value = Dist.CLIENT)
public final class ApexClientEffects {
    static final class ActiveEffect {
        final ApexEffectPacket packet;
        final int born;
        ActiveEffect(ApexEffectPacket packet, int born) { this.packet = packet; this.born = born; }
        float age(float partialTick) { return tick - born + partialTick; }
    }
    private static final int MAX_EFFECTS = 72, MAX_PARTICLES_PER_TICK = 160;
    private static final List<ActiveEffect> EFFECTS = new ArrayList<>();
    private static ClientLevel observedLevel;
    private static int tick, particlesThisTick, shakeTicks, pulseTicks;
    private static float shakeStrength, pulseStrength;
    private ApexClientEffects() { }

    public static void accept(ApexEffectPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.position().distanceToSqr(packet.origin()) > 84 * 84) return;
        if (packet.kind() == EffectKind.MIRAGE && !ApexClientConfig.AFTER_IMAGES.get()) return;
        if (mc.level != observedLevel) clear(mc.level);
        if (EFFECTS.size() >= MAX_EFFECTS) {
            // Preserve persistent danger boundaries before removing short decorative trails.
            int remove = 0;
            for (int i = 0; i < EFFECTS.size(); i++) if (!warning(EFFECTS.get(i).packet.kind())) { remove = i; break; }
            EFFECTS.remove(remove);
        }
        EFFECTS.add(new ActiveEffect(packet, tick));
        if (packet.kind() == EffectKind.MIRAGE && ApexClientConfig.AFTER_IMAGES.get()
                && mc.level.getEntity(packet.entityId()) instanceof ApexEntity apex) {
            dev.apexpredator.client.ApexAfterimageLayer.addGhost(apex, packet.origin(), packet.duration());
        }
        burst(packet, mc);
        float distance = (float)mc.player.position().distanceTo(packet.origin());
        float falloff = Math.max(0, 1 - distance / 32F);
        float strength = switch (packet.kind()) {
            case HIT, PROJECTILE_IMPACT -> .16F;
            case SLAM, SHOCKWAVE -> .48F;
            case PHASE_TWO -> .62F;
            case PHASE_THREE, SPECIAL_FINISH -> .85F;
            default -> 0;
        };
        if (strength * falloff > .01F) {
            shakeTicks = Math.max(shakeTicks, 12);
            shakeStrength = Math.max(shakeStrength, strength * falloff);
        }
        if (packet.kind() == EffectKind.PHASE_TWO || packet.kind() == EffectKind.PHASE_THREE || packet.kind() == EffectKind.SPECIAL_FINISH) {
            pulseTicks = 22; pulseStrength = Math.max(.25F, falloff);
        } else if (packet.kind() == EffectKind.TELEPORT_IN && distance < 10) {
            pulseTicks = Math.max(pulseTicks, 7); pulseStrength = .28F;
        }
    }
    private static boolean warning(EffectKind kind) { return kind == EffectKind.WARNING_RING || kind == EffectKind.SPECIAL_PREPARE; }
    private static void clear(ClientLevel level) {
        EFFECTS.clear(); tick = particlesThisTick = shakeTicks = pulseTicks = 0;
        shakeStrength = pulseStrength = 0; observedLevel = level; ApexBossMusic.clear();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != observedLevel) clear(mc.level);
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++; particlesThisTick = 0;
        EFFECTS.removeIf(e -> tick - e.born >= e.packet.duration());
        if (shakeTicks > 0) shakeTicks--; else shakeStrength = 0;
        if (pulseTicks > 0) pulseTicks--;
        for (ActiveEffect effect : EFFECTS) {
            ApexEffectPacket p = effect.packet;
            int age = tick - effect.born;
            if ((p.kind() == EffectKind.SPECIAL_PREPARE || p.kind() == EffectKind.RANGED_PREPARE || p.kind() == EffectKind.CHARGE_PREPARE) && age % 3 == 0) {
                for (int i = 0; i < count(5, mc); i++) {
                    double angle = mc.level.random.nextDouble() * Math.PI * 2;
                    Vec3 offset = new Vec3(Math.cos(angle) * 1.2, .3 + mc.level.random.nextDouble() * 1.8, Math.sin(angle) * 1.2);
                    particle(ApexEffects.ENERGY.get(), p.origin().add(offset), offset.scale(-.12), mc);
                }
            }
            if (p.kind() == EffectKind.TELEPORT_OUT && age < 6) {
                Vec3 offset = new Vec3((mc.level.random.nextDouble() - .5) * 2, mc.level.random.nextDouble() * 2.6, (mc.level.random.nextDouble() - .5) * 2);
                particle(ApexEffects.ENERGY.get(), p.origin().add(offset), offset.scale(-.2), mc);
            }
        }
        ApexBossMusic.update(mc);
    }
    private static int count(int base, Minecraft mc) {
        double vanilla = mc.options.particles().get() == ParticleStatus.MINIMAL ? .25 : mc.options.particles().get() == ParticleStatus.DECREASED ? .6 : 1;
        return Math.max(1, (int)Math.ceil(base * ApexClientConfig.PARTICLE_QUALITY.get().multiplier * vanilla));
    }
    private static void particle(SimpleParticleType type, Vec3 pos, Vec3 velocity, Minecraft mc) {
        if (particlesThisTick >= MAX_PARTICLES_PER_TICK || mc.level == null) return;
        particlesThisTick++;
        mc.level.addParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
    }
    private static void burst(ApexEffectPacket p, Minecraft mc) {
        int number = switch (p.kind()) {
            case HIT, PROJECTILE_IMPACT -> 20;
            case SLAM, SHOCKWAVE -> 44;
            case PHASE_TWO -> 48;
            case PHASE_THREE, SPECIAL_FINISH -> 64;
            case TELEPORT_OUT, TELEPORT_IN -> 24;
            case CHARGE_TRAIL, DODGE, PROJECTILE_TRAIL -> 5;
            case BLADE_LEFT, BLADE_RIGHT, BLADE_VERTICAL, SPECIAL_SLASH, BLADE_X -> 9;
            case DEATH -> 42;
            case CORE_AMBIENT, MIRAGE -> 3;
            default -> 7;
        };
        for (int i = 0; i < count(number, mc); i++) {
            double angle = mc.level.random.nextDouble() * Math.PI * 2;
            double radius = mc.level.random.nextDouble() * Math.min(2.5, p.radius());
            Vec3 pos = p.origin().add(Math.cos(angle) * radius, .15 + mc.level.random.nextDouble() * .6, Math.sin(angle) * radius);
            Vec3 speed = new Vec3(Math.cos(angle) * .13, .035 + mc.level.random.nextDouble() * .13, Math.sin(angle) * .13);
            SimpleParticleType type = ApexEffects.ENERGY.get();
            switch (p.kind()) {
                case TELEPORT_OUT -> { type = i % 2 == 0 ? ApexEffects.SMOKE.get() : ApexEffects.ENERGY.get(); pos = pos.add(0, mc.level.random.nextDouble() * 1.8, 0); speed = p.origin().add(0, 1.2, 0).subtract(pos).scale(.16); }
                case TELEPORT_IN -> { type = i % 3 == 0 ? ApexEffects.SMOKE.get() : ApexEffects.ENERGY.get(); speed = new Vec3(speed.x, .2 + mc.level.random.nextDouble() * .1, speed.z); }
                case HIT, PROJECTILE_IMPACT -> { type = ApexEffects.SPARK.get(); pos = p.origin().add(0, .9, 0); speed = speed.scale(1.8); }
                case BLADE_LEFT, BLADE_RIGHT, BLADE_VERTICAL -> { type = i % 3 == 0 ? ApexEffects.SLASH.get() : ApexEffects.SPARK.get(); pos = p.origin().add(0, 1.1, 0); speed = speed.scale(1.3); }
                case SLAM, SHOCKWAVE -> { type = i % 3 == 0 ? ApexEffects.SMOKE.get() : i % 3 == 1 ? ApexEffects.SHOCKWAVE.get() : ApexEffects.SPARK.get(); speed = speed.scale(1.8); }
                case PHASE_TWO, PHASE_THREE, DEATH -> { type = i % 4 == 0 ? ApexEffects.SMOKE.get() : ApexEffects.CORE.get(); pos = pos.add(0, mc.level.random.nextDouble() * 1.6, 0); }
                case CORE_AMBIENT -> { type = ApexEffects.CORE.get(); pos = p.origin().add((mc.level.random.nextDouble() - .5) * .4, 0, (mc.level.random.nextDouble() - .5) * .4); speed = new Vec3(0, .035, 0); }
                case CHARGE_TRAIL, DODGE -> { type = i % 2 == 0 ? ApexEffects.SMOKE.get() : ApexClientConfig.AFTER_IMAGES.get() ? ApexEffects.AFTERIMAGE.get() : ApexEffects.ENERGY.get(); speed = p.direction().scale(-.08); }
                case PROJECTILE_TRAIL -> { pos = p.origin().subtract(p.direction().scale(mc.level.random.nextDouble() * .7)); speed = p.direction().scale(-.025); }
                default -> { }
            }
            particle(type, pos, speed, mc);
        }
    }
    @SubscribeEvent public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES && !EFFECTS.isEmpty()) ApexEffectRenderer.render(event, EFFECTS);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0 || !ApexClientConfig.CAMERA_SHAKE.get() || Minecraft.getInstance().isPaused()) return;
        double clock = tick + event.getPartialTick();
        float strength = shakeStrength * shakeTicks / 12F;
        event.setYaw(event.getYaw() + (float)Math.sin(clock * 2.4) * strength * .55F);
        event.setPitch(event.getPitch() + (float)Math.cos(clock * 2.9) * strength * .5F);
        event.setRoll(event.getRoll() + (float)Math.sin(clock * 2.0) * strength * .6F);
    }
    @SubscribeEvent public static void screen(RenderGuiOverlayEvent.Post event) {
        if (!ApexClientConfig.SCREEN_EFFECTS.get() || !event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        for (ActiveEffect effect : EFFECTS) if (effect.packet.kind() == EffectKind.SPECIAL_PREPARE
                && mc.player != null && mc.player.position().distanceToSqr(effect.packet.origin()) < 36 * 36) {
            int alpha = (int)(220 * Math.min(1, effect.age(0) / 4F));
            event.getGuiGraphics().drawCenteredString(mc.font, Component.translatable("effect.apexpredator.execution"),
                    width / 2, height / 4, alpha << 24 | 0xFF8A30);
            break;
        }
        if (pulseTicks <= 0) return;
        float fade = pulseTicks / 22F;
        // Edge-only bands leave the crosshair and incoming attacks visible even at maximum intensity.
        for (int i = 0; i < 5; i++) {
            int inset = i * Math.max(2, height / 80), size = Math.max(2, height / 80);
            int alpha = Math.round((26 - i * 4) * fade * pulseStrength);
            int color = alpha << 24 | 0xFF5B0C;
            event.getGuiGraphics().fill(inset, inset, width - inset, inset + size, color);
            event.getGuiGraphics().fill(inset, height - inset - size, width - inset, height - inset, color);
            event.getGuiGraphics().fill(inset, inset + size, inset + size, height - inset - size, color);
            event.getGuiGraphics().fill(width - inset - size, inset + size, width - inset, height - inset - size, color);
        }
    }
}
