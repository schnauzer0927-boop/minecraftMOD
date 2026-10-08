package dev.apexpredator.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The warning cylinder intersects the player's body, rather than only its center point. */
class AttackAreaTest {
    private static final Vec3 CENTER = new Vec3(120.5, 64, -31.5);
    private static final AttackArea AREA = AttackArea.ground(CENTER, 6);

    @Test
    void circleCenterAndHalfRadiusIntersect() {
        assertTrue(AREA.intersects(playerBox(0, 0, 0)));
        assertTrue(AREA.intersects(playerBox(3, 0, 0)));
        assertTrue(AREA.intersects(playerBox(0, 0, -3)));
    }

    @Test
    void bodyIntersectingTheEdgeIsHitEvenWithItsCenterOutside() {
        AABB straddling = playerBox(6.25, 0, 0);
        assertTrue(6.25 > AREA.radius());
        assertEquals(5.95, AREA.horizontalDistance(straddling), 1e-9);
        assertTrue(AREA.intersects(straddling));
    }

    @Test
    void boundaryToleranceDoesNotExpandIntoAnUnboundedHitRegion() {
        double outsideCenter = AREA.radius() + .3;
        assertTrue(AREA.intersects(playerBox(outsideCenter + .14, 0, 0)));
        AABB tangent = new AABB(CENTER.x + AREA.radius() + AREA.tolerance(), CENTER.y, CENTER.z - .3,
                CENTER.x + AREA.radius() + AREA.tolerance() + .6, CENTER.y + 1.8, CENTER.z + .3);
        assertTrue(AREA.intersects(tangent), "Binary floating-point subtraction must retain an inclusive tangent");
        assertTrue(AREA.bounds().intersects(tangent), "Strict AABB broad phase must admit the inclusive circle boundary");
        double diagonal = (AREA.radius() + AREA.tolerance()) / Math.sqrt(2);
        AABB diagonalTangent = new AABB(CENTER.x + diagonal, CENTER.y, CENTER.z + diagonal,
                CENTER.x + diagonal + .6, CENTER.y + 1.8, CENTER.z + diagonal + .6);
        assertTrue(AREA.intersects(diagonalTangent), "Diagonal tangent must have the same numerical tolerance");
        assertFalse(AREA.intersects(tangent.move(.001, 0, 0)), "Numerical epsilon must not add visible hit distance");
        assertFalse(AREA.intersects(playerBox(outsideCenter + .16, 0, 0)));
        assertFalse(AREA.intersects(playerBox(7, 0, 0)));
    }

    @Test
    void broadPhaseCornersRemainOutsideTheCircularShape() {
        AABB corner = playerBox(5.4, 0, 5.4);
        assertTrue(AREA.bounds().intersects(corner));
        assertFalse(AREA.intersects(corner));
        double onDiagonal = AREA.radius() / Math.sqrt(2);
        assertTrue(AREA.intersects(playerBox(onDiagonal, 0, onDiagonal)));
    }

    @Test
    void slabsStairsOneBlockStepsAndOrdinaryJumpsStayWithinTheVerticalBand() {
        assertTrue(AREA.intersects(playerBox(3, .5, 0)));
        assertTrue(AREA.intersects(playerBox(3, 1, 0)));
        assertTrue(AREA.intersects(playerBox(3, 2.75, 0)));
        assertTrue(AREA.intersects(playerBox(3, -1, 0)));
    }

    @Test
    void playersWhollyAboveOrBelowTheGroundBandAreExcluded() {
        assertFalse(AREA.intersects(playerBox(0, 4.25, 0)));
        assertFalse(AREA.intersects(playerBox(0, -4, 0)));
        double upper = CENTER.y + AREA.above() + AREA.tolerance();
        double lower = CENTER.y - AREA.below() - AREA.tolerance();
        AABB upperTangent = new AABB(CENTER.x - .3, upper, CENTER.z - .3,
                CENTER.x + .3, upper + 1.8, CENTER.z + .3);
        AABB lowerTangent = new AABB(CENTER.x - .3, lower - 1.8, CENTER.z - .3,
                CENTER.x + .3, lower, CENTER.z + .3);
        assertTrue(AREA.intersects(upperTangent));
        assertTrue(AREA.intersects(lowerTangent));
        assertTrue(AREA.bounds().intersects(upperTangent), "Upper vertical tangent was dropped by broad phase");
        assertTrue(AREA.bounds().intersects(lowerTangent), "Lower vertical tangent was dropped by broad phase");
        assertFalse(AREA.intersects(upperTangent.move(0, .001, 0)));
        assertFalse(AREA.intersects(lowerTangent.move(0, -.001, 0)));
    }

    @Test
    void aTallBodyIsHitWhenItsFeetIntersectTheGroundBand() {
        AABB tall = new AABB(CENTER.x - .3, CENTER.y + 2.9, CENTER.z - .3,
                CENTER.x + .3, CENTER.y + 7, CENTER.z + .3);
        assertTrue(AREA.intersects(tall));
    }

    @Test
    void translationPreservesTheCommittedAreaAndItsBoundingBoxQuery() {
        Vec3 shift = new Vec3(-258.75, 29.5, 80.25);
        AttackArea translated = AttackArea.ground(CENTER.add(shift), AREA.radius());
        for (AABB body : new AABB[]{playerBox(0, 0, 0), playerBox(6.25, .5, 0),
                playerBox(7, 0, 0), playerBox(0, 4.25, 0)}) {
            assertEquals(AREA.intersects(body), translated.intersects(body.move(shift)));
            assertEquals(AREA.horizontalDistance(body), translated.horizontalDistance(body.move(shift)), 1e-9);
        }
        assertEquals(CENTER, AREA.center());
        assertNotEquals(AREA.center(), translated.center());
        assertTrue(AREA.bounds().intersects(playerBox(6.25, .5, 0)));
    }

    private static AABB playerBox(double x, double y, double z) {
        return new AABB(CENTER.x + x - .3, CENTER.y + y, CENTER.z + z - .3,
                CENTER.x + x + .3, CENTER.y + y + 1.8, CENTER.z + z + .3);
    }
}
