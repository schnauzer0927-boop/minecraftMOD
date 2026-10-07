package dev.apexpredator.combat;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID)
public final class ApexDamageEvents {
    private ApexDamageEvents() { }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finalDamageCap(LivingDamageEvent event) {
        if (event.getEntity() instanceof ApexEntity apex && !ApexEntity.isAdministrativeDamage(event.getSource())) {
            event.setAmount(Math.min(event.getAmount(), apex.maximumFinalDamage(event.getSource())));
        }
    }
}
