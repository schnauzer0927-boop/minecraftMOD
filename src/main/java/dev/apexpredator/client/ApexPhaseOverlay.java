package dev.apexpredator.client;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A brief visual cue for nearby phase changes; never moves the camera or blocks controls. */
@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID, value = Dist.CLIENT)
public final class ApexPhaseOverlay {
    private static final Map<UUID, Integer> OBSERVED_PHASES = new HashMap<>();
    private static ClientLevel observedLevel;
    private static int flashTicks;
    private static int flashPhase;

    private ApexPhaseOverlay() { }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != observedLevel) {
            OBSERVED_PHASES.clear();
            flashTicks = 0;
            observedLevel = minecraft.level;
        }
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
        Map<UUID, Integer> current = new HashMap<>();
        for (ApexEntity apex : minecraft.level.getEntitiesOfClass(ApexEntity.class,
                minecraft.player.getBoundingBox().inflate(64.0D), ApexEntity::isAlive)) {
            int phase = apex.getPhase();
            Integer previous = OBSERVED_PHASES.get(apex.getUUID());
            if (previous != null && phase > previous) {
                flashTicks = 14;
                flashPhase = phase;
            }
            current.put(apex.getUUID(), phase);
        }
        OBSERVED_PHASES.clear();
        OBSERVED_PHASES.putAll(current);
    }

    @SubscribeEvent
    public static void drawPhaseFlash(RenderGuiOverlayEvent.Post event) {
        if (flashTicks <= 0 || !event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            return;
        }
        int alpha = Math.round(70.0F * flashTicks / 14.0F);
        int rgb = flashPhase == 3 ? 0x8E1DCA : 0xA6240A;
        Minecraft minecraft = Minecraft.getInstance();
        event.getGuiGraphics().fill(0, 0, minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(), alpha << 24 | rgb);
    }
}
