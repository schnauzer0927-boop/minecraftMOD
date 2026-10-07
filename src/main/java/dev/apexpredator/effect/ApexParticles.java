package dev.apexpredator.effect;

import dev.apexpredator.entity.ApexEntity;
import net.minecraft.world.phys.Vec3;

/** Compatibility facade; effects are assembled locally from a single semantic packet. */
public final class ApexParticles {
    private ApexParticles() { }
    public static void telegraph(ApexEntity e, double radius) { ApexEffects.emit(e, EffectKind.WARNING_RING, e.position(), e.getLookAngle(), (float)radius, 14); }
    public static void burst(ApexEntity e) { ApexEffects.emit(e, e.getPhase() >= 3 ? EffectKind.PHASE_THREE : EffectKind.PHASE_TWO, e.position(), e.getLookAngle(), 8, 32); }
    public static void warp(ApexEntity e) { ApexEffects.emit(e, EffectKind.TELEPORT_OUT, e.position(), e.getLookAngle(), 1.4F, 12); }
    public static void trail(ApexEntity e) { if (e.tickCount % 3 == 0) ApexEffects.emit(e, EffectKind.CHARGE_TRAIL, e.position(), e.getLookAngle(), 1.4F, 10); }
    public static void special(ApexEntity e, Vec3 direction, int elapsed) { if (elapsed % 3 == 0) ApexEffects.emit(e, EffectKind.SPECIAL_SLASH, e.position(), direction, 8, 12); }
}
