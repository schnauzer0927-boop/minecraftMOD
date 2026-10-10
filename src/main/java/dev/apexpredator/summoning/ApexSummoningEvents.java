package dev.apexpredator.summoning;

import dev.apexpredator.ApexPredator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lifecycle cleanup is independent of combat and does not need changes to the mod entrypoint. */
@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID)
public final class ApexSummoningEvents {
    private ApexSummoningEvents() { }

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) ApexSummoningRitual.tick(level);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ApexSummoningRitual.cancel(player);
    }

    @SubscribeEvent public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ApexSummoningRitual.cancel(player);
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) ApexSummoningRitual.unload(level);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { ApexSummoningRitual.stopServer(); }
}
