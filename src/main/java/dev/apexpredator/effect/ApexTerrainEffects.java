package dev.apexpredator.effect;

import dev.apexpredator.ApexConfig;
import dev.apexpredator.entity.ApexEntity;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.ForgeEventFactory;

/** Optional, bounded surface damage. Cosmetic cracks never depend on block destruction. */
public final class ApexTerrainEffects {
    private ApexTerrainEffects() { }
    public static void slam(ApexEntity apex) {
        if (apex.level().isClientSide || !ApexConfig.DESTROY_TERRAIN.get()
                || !ForgeEventFactory.getMobGriefingEvent(apex.level(), apex)) return;
        BlockPos center = apex.blockPosition().below();
        int broken = 0;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (broken >= 8 || x * x + z * z > 4) continue;
            BlockPos pos = center.offset(x, 0, z);
            if (!apex.level().hasChunkAt(pos) || !apex.level().getWorldBorder().isWithinBounds(pos)) continue;
            var state = apex.level().getBlockState(pos);
            float hardness = state.getDestroySpeed(apex.level(), pos);
            if (state.isAir() || hardness < 0 || hardness > 2 || state.hasBlockEntity()
                    || !state.getFluidState().isEmpty() || !state.canEntityDestroy(apex.level(), pos, apex)) continue;
            if (ForgeEventFactory.onEntityDestroyBlock(apex, pos, state)
                    && apex.level().destroyBlock(pos, true, apex)) broken++;
        }
    }
}
