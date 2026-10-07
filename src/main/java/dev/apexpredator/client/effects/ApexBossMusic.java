package dev.apexpredator.client.effects;

import dev.apexpredator.ApexClientConfig;
import dev.apexpredator.effect.ApexSounds;
import dev.apexpredator.entity.ApexEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A single client-local encounter loop, stopped on death, distance, disconnect or preference changes. */
final class ApexBossMusic extends AbstractTickableSoundInstance {
    private static ApexBossMusic active;
    private final ApexEntity apex;
    private ApexBossMusic(ApexEntity apex) {
        super(ApexSounds.event("boss_music"), SoundSource.MUSIC, RandomSource.create());
        this.apex = apex;
        looping = true; delay = 0; relative = true;
        attenuation = Attenuation.NONE;
        volume = .01F;
    }
    static void update(Minecraft minecraft) {
        if (active != null && (active.isStopped() || minecraft.level == null || minecraft.player == null || !ApexClientConfig.BOSS_MUSIC.get())) {
            minecraft.getSoundManager().stop(active); active = null;
        }
        if (active != null || minecraft.level == null || minecraft.player == null || !ApexClientConfig.BOSS_MUSIC.get()) return;
        ApexEntity nearest = null;
        double nearestDistance = 48 * 48;
        for (ApexEntity candidate : minecraft.level.getEntitiesOfClass(ApexEntity.class, minecraft.player.getBoundingBox().inflate(48), ApexEntity::isAlive)) {
            double distance = candidate.distanceToSqr(minecraft.player);
            if (distance < nearestDistance) { nearest = candidate; nearestDistance = distance; }
        }
        if (nearest != null) { active = new ApexBossMusic(nearest); minecraft.getSoundManager().play(active); }
    }
    static void clear() {
        if (active != null) Minecraft.getInstance().getSoundManager().stop(active);
        active = null;
    }
    @Override public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level != apex.level() || !apex.isAlive() || apex.isRemoved()
                || !ApexClientConfig.BOSS_MUSIC.get() || apex.distanceToSqr(minecraft.player) > 64 * 64) { stop(); return; }
        float target = (.14F + .035F * apex.getPhase()) * (float)Math.max(.15, 1 - apex.distanceTo(minecraft.player) / 72.0);
        volume += (target - volume) * .05F;
        pitch = 1F + (apex.getPhase() - 1) * .025F;
    }
}
