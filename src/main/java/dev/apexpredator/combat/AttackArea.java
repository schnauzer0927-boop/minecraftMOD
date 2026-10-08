package dev.apexpredator.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable warning and damage cylinder, committed before an attack's windup. */
public record AttackArea(Vec3 center, double radius, double below, double above, double tolerance) {
    private static final double PRECISION_EPSILON = 1.0e-7;
    public AttackArea {
        if (center == null || !Double.isFinite(center.x) || !Double.isFinite(center.y)
                || !Double.isFinite(center.z) || !Double.isFinite(radius) || radius <= 0
                || !Double.isFinite(below) || below < 0 || !Double.isFinite(above) || above < 0
                || !Double.isFinite(tolerance) || tolerance < 0 || tolerance > 0.2) {
            throw new IllegalArgumentException("Invalid committed attack area");
        }
    }

    public static AttackArea ground(Vec3 center, double radius) {
        return new AttackArea(center, radius, 1.5, 3.0, 0.15);
    }

    /** Broad phase uses the same cylinder and tolerance as the precise hit test. */
    public AABB bounds() {
        // World queries use strict AABB overlap, while the cylinder includes its edge.
        double queryMargin = tolerance + 2 * PRECISION_EPSILON;
        double reach = radius + queryMargin;
        return new AABB(center.x - reach, center.y - below - queryMargin, center.z - reach,
                center.x + reach, center.y + above + queryMargin, center.z + reach);
    }

    public double horizontalDistance(AABB box) {
        double x = Math.max(box.minX, Math.min(center.x, box.maxX));
        double z = Math.max(box.minZ, Math.min(center.z, box.maxZ));
        return Math.hypot(x - center.x, z - center.z);
    }

    /** A player's edge can intersect the warning even when their center is outside it. */
    public boolean intersects(AABB box) {
        double margin = tolerance + PRECISION_EPSILON;
        if (box.maxY < center.y - below - margin || box.minY > center.y + above + margin) return false;
        return horizontalDistance(box) <= radius + margin;
    }
}
