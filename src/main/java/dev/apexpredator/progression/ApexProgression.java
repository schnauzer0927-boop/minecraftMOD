package dev.apexpredator.progression;

import dev.apexpredator.ApexPredator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server-owned ritual achievements; kills and crafting use vanilla criteria. */
public final class ApexProgression {
    private ApexProgression() { }

    public static void award(ServerPlayer player, String name) {
        var advancement = player.server.getAdvancements().getAdvancement(new ResourceLocation(ApexPredator.MOD_ID, name));
        if (advancement == null) return; // Resource packs may intentionally remove an advancement.
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        List<String> pending = new ArrayList<>();
        progress.getRemainingCriteria().forEach(pending::add);
        for (String criterion : pending) player.getAdvancements().award(advancement, criterion);
    }
}
