package dev.apexpredator.combat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerBehaviorTrackerTest {
    @Test
    void tracksRapidRangedFireSeparatelyForEachPlayer() {
        PlayerBehaviorTracker tracker = new PlayerBehaviorTracker();
        UUID shooter = UUID.randomUUID();
        UUID swordsman = UUID.randomUUID();
        for (int tick = 0; tick < 4; tick++) {
            tracker.recordAttack(shooter, tick, PlayerBehaviorTracker.AttackObservation.GUN);
            tracker.recordAttack(swordsman, tick, PlayerBehaviorTracker.AttackObservation.MELEE);
        }
        var shooterBehavior = tracker.snapshot(shooter, 4);
        var meleeBehavior = tracker.snapshot(swordsman, 4);
        assertTrue(shooterBehavior.rapidFire());
        assertTrue(shooterBehavior.rangedDominant());
        assertEquals(4, shooterBehavior.gunAttacks());
        assertTrue(meleeBehavior.meleeDominant());
        assertFalse(meleeBehavior.rapidFire());
    }

    @Test
    void stationaryElevatedShooterStopsBeingStationaryAfterMoving() {
        PlayerBehaviorTracker tracker = new PlayerBehaviorTracker();
        UUID shooter = UUID.randomUUID();
        tracker.observePosition(shooter, 0, 8, 7, 4, 0);
        tracker.recordAttack(shooter, 20, PlayerBehaviorTracker.AttackObservation.RANGED);
        tracker.recordAttack(shooter, 30, PlayerBehaviorTracker.AttackObservation.RANGED);
        tracker.observePosition(shooter, 40, 8.5, 7, 4, 0);
        assertTrue(tracker.snapshot(shooter, 40).stationary());
        assertTrue(tracker.snapshot(shooter, 40).elevated());
        tracker.observePosition(shooter, 41, 11, 0, 4, 0);
        assertFalse(tracker.snapshot(shooter, 41).stationary());
        assertFalse(tracker.snapshot(shooter, 41).elevated());
    }

    @Test
    void duplicateGunEventsAndOldObservationsDoNotInflateAttackRate() {
        PlayerBehaviorTracker tracker = new PlayerBehaviorTracker();
        UUID shooter = UUID.randomUUID();
        tracker.recordAttack(shooter, 0, PlayerBehaviorTracker.AttackObservation.GUN);
        tracker.recordAttack(shooter, 0, PlayerBehaviorTracker.AttackObservation.GUN);
        assertEquals(1, tracker.snapshot(shooter, 0).gunAttacks());
        assertEquals(0, tracker.snapshot(shooter, 60).totalAttacks());
        tracker.prune(201);
        assertEquals(0, tracker.trackedPlayerCount());
    }

    @Test
    void interleavedFireVanillaHurtAndGunPostEventsDescribeOneShot() {
        PlayerBehaviorTracker tracker = new PlayerBehaviorTracker();
        UUID shooter = UUID.randomUUID();
        for (int tick = 0; tick < 4; tick++) {
            tracker.recordAttack(shooter, tick, PlayerBehaviorTracker.AttackObservation.GUN);
            tracker.recordAttack(shooter, tick, PlayerBehaviorTracker.AttackObservation.RANGED);
            tracker.recordAttack(shooter, tick, PlayerBehaviorTracker.AttackObservation.GUN);
            var snapshot = tracker.snapshot(shooter, tick);
            assertEquals(tick + 1, snapshot.rangedAttacks());
            assertEquals(tick + 1, snapshot.gunAttacks());
            assertEquals(tick == 3, snapshot.rapidFire());
        }
        // Ranged may be reported before TaCZ identifies the shot as gun damage.
        tracker.recordAttack(shooter, 4, PlayerBehaviorTracker.AttackObservation.RANGED);
        tracker.recordAttack(shooter, 4, PlayerBehaviorTracker.AttackObservation.GUN);
        assertEquals(5, tracker.snapshot(shooter, 4).gunAttacks());
        assertEquals(5, tracker.snapshot(shooter, 4).rangedAttacks());
    }

    @Test
    void movingSlowlyBeyondTheOriginalAnchorBreaksStationaryClassification() {
        PlayerBehaviorTracker tracker = new PlayerBehaviorTracker();
        UUID shooter = UUID.randomUUID();
        tracker.observePosition(shooter, 0, 0, 0, 0, 0);
        tracker.recordAttack(shooter, 1, PlayerBehaviorTracker.AttackObservation.RANGED);
        tracker.recordAttack(shooter, 2, PlayerBehaviorTracker.AttackObservation.RANGED);
        for (int tick = 3; tick <= 43; tick++) {
            tracker.observePosition(shooter, tick, tick * 0.1, 0, 0, 0);
        }
        assertFalse(tracker.snapshot(shooter, 43).stationary());
    }
}
