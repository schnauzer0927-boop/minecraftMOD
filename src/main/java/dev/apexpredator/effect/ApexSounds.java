package dev.apexpredator.effect;
import dev.apexpredator.entity.ApexEntity;
import net.minecraft.sounds.SoundEvents;
/** Vanilla events keep the first release self-contained. */
public final class ApexSounds {
    private ApexSounds() { }
    public static void telegraph(ApexEntity e) { e.playSound(SoundEvents.RAVAGER_ATTACK, 1.4F, .65F); }
    public static void attack(ApexEntity e) { e.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, .6F); }
    public static void warp(ApexEntity e) { e.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.4F, .7F); }
    public static void phase(ApexEntity e) { e.playSound(SoundEvents.WITHER_SPAWN, 2F, .65F); }
    public static void special(ApexEntity e) { e.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.5F, .65F); }
}
