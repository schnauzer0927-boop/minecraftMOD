package dev.apexpredator.combat;

import dev.apexpredator.entity.ApexEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Resolves a local, loaded collision surface; never searches another storey or a heightmap. */
public final class SlamTargetResolver {
    private SlamTargetResolver() { }

    public static Optional<Vec3> resolve(ApexEntity apex, Vec3 requested) {
        if (!Double.isFinite(requested.x) || !Double.isFinite(requested.y) || !Double.isFinite(requested.z)) {
            return Optional.empty();
        }
        double half = apex.getBbWidth() / 2.0;
        AABB footprint = new AABB(requested.x - half + 0.001, requested.y - 2.0, requested.z - half + 0.001,
                requested.x + half - 0.001, requested.y + 1.0, requested.z + half - 0.001);
        List<Double> surfaces = new ArrayList<>();
        for (int x = Mth.floor(footprint.minX); x <= Mth.floor(footprint.maxX); x++) {
            for (int z = Mth.floor(footprint.minZ); z <= Mth.floor(footprint.maxZ); z++) {
                for (int y = Mth.floor(footprint.minY) - 1; y <= Mth.floor(footprint.maxY); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!apex.level().hasChunkAt(pos) || !apex.level().getWorldBorder().isWithinBounds(pos)) {
                        return Optional.empty();
                    }
                    var state = apex.level().getBlockState(pos);
                    if (!state.getFluidState().isEmpty()) continue;
                    for (AABB shape : state.getCollisionShape(apex.level(), pos, CollisionContext.of(apex)).toAabbs()) {
                        AABB surface = shape.move(x, y, z);
                        if (surface.maxY < footprint.minY || surface.maxY > footprint.maxY
                                || surface.maxX <= footprint.minX || surface.minX >= footprint.maxX
                                || surface.maxZ <= footprint.minZ || surface.minZ >= footprint.maxZ) continue;
                        surfaces.add(surface.maxY);
                    }
                }
            }
        }
        // The highest local support prevents a wide foot from intersecting the upper stair tread.
        surfaces.sort(Comparator.reverseOrder());
        for (double y : surfaces) {
            Vec3 destination = new Vec3(requested.x, y, requested.z);
            if (y < apex.level().getMinBuildHeight() || y + apex.getBbHeight() >= apex.level().getMaxBuildHeight()) continue;
            AABB body = apex.getBoundingBox().move(destination.subtract(apex.position()));
            if (apex.level().noCollision(apex, body.deflate(0.0001)) && !apex.level().containsAnyLiquid(body)
                    && apex.level().getWorldBorder().isWithinBounds(body)) return Optional.of(destination);
        }
        return Optional.empty();
    }
}
