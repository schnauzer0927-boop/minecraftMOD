package dev.apexpredator.summoning;

import dev.apexpredator.entity.ApexEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Optional;

/** Collision surfaces and the actual registered Apex dimensions define a fixed summoning site. */
final class BeaconSpawnSite {
    private static final double EPSILON = 1.0e-5;

    private BeaconSpawnSite() { }

    static Optional<Vec3> center(ServerLevel level, BlockPos floor, ApexEntity apex) {
        if (!level.hasChunkAt(floor) || !level.getWorldBorder().isWithinBounds(floor)) return Optional.empty();
        var state = level.getBlockState(floor);
        if (!state.getFluidState().isEmpty()) return Optional.empty();
        double x = floor.getX() + .5, z = floor.getZ() + .5;
        double surface = Double.NEGATIVE_INFINITY;
        for (AABB shape : state.getCollisionShape(level, floor, CollisionContext.of(apex)).toAabbs()) {
            if (.5 >= shape.minX && .5 <= shape.maxX && .5 >= shape.minZ && .5 <= shape.maxZ) {
                surface = Math.max(surface, shape.maxY + floor.getY());
            }
        }
        return Double.isFinite(surface) ? Optional.of(new Vec3(x, surface, z)) : Optional.empty();
    }

    static ApexSummoningRitual.BeginResult validate(ServerLevel level, BlockPos floor, Vec3 center, ApexEntity apex) {
        apex.moveTo(center.x, center.y, center.z, apex.getYRot(), 0);
        AABB body = apex.getBoundingBox();
        if (body.minY < level.getMinBuildHeight() || body.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(body)) return ApexSummoningRitual.BeginResult.UNSAFE_GROUND;
        // Loaded checks precede every state query: the ritual never creates chunk tickets.
        for (int x = Mth.floor(body.minX); x <= Mth.floor(body.maxX); x++) {
            for (int z = Mth.floor(body.minZ); z <= Mth.floor(body.maxZ); z++) {
                for (int y = Mth.floor(center.y) - 1; y <= Mth.floor(body.maxY); y++) {
                    if (!level.hasChunkAt(new BlockPos(x, y, z))) return ApexSummoningRitual.BeginResult.UNSAFE_GROUND;
                }
            }
        }
        if (!level.hasChunkAt(floor)) return ApexSummoningRitual.BeginResult.UNSAFE_GROUND;
        var state = level.getBlockState(floor);
        if (!state.getFluidState().isEmpty()) return ApexSummoningRitual.BeginResult.UNSAFE_GROUND;
        boolean supported = false;
        for (AABB shape : state.getCollisionShape(level, floor, CollisionContext.of(apex)).toAabbs()) {
            AABB surface = shape.move(floor);
            if (Math.abs(surface.maxY - center.y) <= EPSILON
                    && center.x >= surface.minX && center.x <= surface.maxX
                    && center.z >= surface.minZ && center.z <= surface.maxZ) {
                supported = true;
                break;
            }
        }
        if (!supported || level.containsAnyLiquid(body)) return ApexSummoningRitual.BeginResult.UNSAFE_GROUND;
        return level.noCollision(apex, body) ? ApexSummoningRitual.BeginResult.STARTED
                : ApexSummoningRitual.BeginResult.SPACE_BLOCKED;
    }
}
