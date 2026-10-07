package dev.apexpredator.client.effects;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.effect.ApexEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ApexParticleRegistration {
    private ApexParticleRegistration() { }
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ApexEffects.SLASH.get(), s -> new ApexParticle.Provider(s, ApexParticle.Style.SLASH));
        event.registerSpriteSet(ApexEffects.ENERGY.get(), s -> new ApexParticle.Provider(s, ApexParticle.Style.ENERGY));
        event.registerSpriteSet(ApexEffects.SMOKE.get(), s -> new ApexParticle.Provider(s, ApexParticle.Style.SMOKE));
        event.registerSpriteSet(ApexEffects.SPARK.get(), s -> new ApexParticle.Provider(s, ApexParticle.Style.SPARK));
        event.registerSpriteSet(ApexEffects.AFTERIMAGE.get(), s -> new ApexParticle.Provider(s, ApexParticle.Style.AFTERIMAGE));
        event.registerSpriteSet(ApexEffects.SHOCKWAVE.get(), s -> new ApexParticle.Provider(s, ApexParticle.Style.SHOCKWAVE));
        event.registerSpriteSet(ApexEffects.CORE.get(), s -> new ApexParticle.Provider(s, ApexParticle.Style.CORE));
    }
}
