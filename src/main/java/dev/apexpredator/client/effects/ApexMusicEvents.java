package dev.apexpredator.client.effects;

import com.mojang.logging.LogUtils;
import dev.apexpredator.ApexConfig;
import dev.apexpredator.ApexPredator;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** Stops new background music requests while leaving records and combat sounds alone. */
@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID, value = Dist.CLIENT)
public final class ApexMusicEvents {
    private ApexMusicEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playSound(PlaySoundEvent event) {
        var sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.MUSIC
                || !ApexBossMusic.isVanillaMusicSuppressed()) return;
        event.setSound(null);
        if (ApexConfig.DEBUG.get()) LogUtils.getLogger().info(
                "[APEX BGM DEBUG] vanilla_blocked sound={} category=MUSIC", sound.getLocation());
    }
}
