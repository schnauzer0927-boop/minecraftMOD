package dev.apexpredator.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DamageTrackerTest {
    @Test
    void capsIncomingHitsWithoutCountingDamageThatArmorOrInvulnerabilityPrevented() {
        DamageTracker tracker = new DamageTracker();
        assertEquals(35.0F, tracker.capIncomingDamage(500.0F));
        assertEquals(14.0F, tracker.capIncomingDamage(14.0F));
        assertEquals(0.0F, tracker.capIncomingDamage(Float.NaN));
        assertEquals(0.0F, tracker.getDamagePerSecond(0));
        tracker.recordDamage(0, 9.0F);
        assertEquals(9.0F, tracker.getDamagePerSecond(0));
    }

    @Test
    void exactlyOneHundredActualDamageEntersFiniteEvasionMode() {
        DamageTracker tracker = new DamageTracker();
        assertFalse(tracker.recordDamage(0, 35).enterEvasion());
        assertFalse(tracker.recordDamage(2, 35).enterEvasion());
        DamageTracker.DamageReaction response = tracker.recordDamage(4, 30);
        assertTrue(response.enterEvasion());
        assertEquals(100.0F, response.damagePerSecond());
        assertTrue(tracker.isEvading(53));
        assertFalse(tracker.isEvading(54));
        assertEquals(0.0F, tracker.getDamagePerSecond(54));
    }

    @Test
    void damageAtTwentyTicksIsOutsideRollingOneSecondWindow() {
        DamageTracker tracker = new DamageTracker();
        tracker.recordDamage(0, 35);
        tracker.recordDamage(19, 35);
        assertEquals(70.0F, tracker.getDamagePerSecond(19));
        assertEquals(35.0F, tracker.getDamagePerSecond(20));
        assertFalse(tracker.recordDamage(20, 35).enterEvasion());
    }

    @Test
    void repeatedHitsStrengthenEvasionAndRepositionHasCooldown() {
        DamageTracker tracker = new DamageTracker();
        tracker.recordDamage(0, 25);
        tracker.recordDamage(1, 25);
        assertTrue(tracker.recordDamage(2, 25).forceReposition());
        DamageTracker.DamageReaction fourthHit = tracker.recordDamage(3, 25);
        assertTrue(fourthHit.sustainedFire());
        assertTrue(fourthHit.enterEvasion());
        tracker.recordDamage(4, 35);
        assertFalse(tracker.recordDamage(5, 35).forceReposition());
        assertTrue(tracker.recordDamage(22, 1).forceReposition());
    }

    @Test
    void blockedHitsAndTimeResetCannotRetainBurstDamage() {
        DamageTracker tracker = new DamageTracker();
        tracker.recordDamage(100, 35);
        tracker.recordDamage(100, 0);
        assertEquals(1, tracker.getConsecutiveHits(100));
        assertEquals(0.0F, tracker.getDamagePerSecond(0));
        assertFalse(tracker.isEvading(0));
    }

    @Test
    void lastHealthLossSurvivesExpirationOfTheDpsWindow() {
        DamageTracker tracker = new DamageTracker();
        tracker.recordDamage(12, 5);
        assertTrue(tracker.hasDamageHistory());
        assertEquals(12, tracker.getLastDamageTick());
        assertEquals(0, tracker.getDamagePerSecond(42));
        assertEquals(12, tracker.getLastDamageTick());
        assertEquals(30, tracker.getTicksSinceLastDamage(42));
        assertEquals(112, tracker.getRecoveryReadyTick(42, 100));
    }

    @Test
    void rejectedOrNonFiniteDamageCannotRestartTheRecoveryClock() {
        DamageTracker tracker = new DamageTracker();
        tracker.recordDamage(10, 2);
        for (float rejected : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            tracker.recordDamage(50, rejected);
            assertEquals(10, tracker.getLastDamageTick());
            assertEquals(110, tracker.getRecoveryReadyTick(50, 100));
        }
    }

    @Test
    void elapsedDamageTimeCanBeRestoredWithoutReplayingBurstDamage() {
        DamageTracker tracker = new DamageTracker();
        tracker.recordDamage(100, 35);
        tracker.restoreNoDamageTime(200, 40, true);
        assertTrue(tracker.hasDamageHistory());
        assertEquals(160, tracker.getLastDamageTick());
        assertEquals(40, tracker.getTicksSinceLastDamage(200));
        assertEquals(260, tracker.getRecoveryReadyTick(200, 100));
        assertEquals(0, tracker.getDamagePerSecond(200));
        assertFalse(tracker.isEvading(200));
    }

    @Test
    void timelineRollbackInvalidatesFutureRecoveryHistory() {
        DamageTracker tracker = new DamageTracker();
        tracker.recordDamage(100, 35);
        tracker.tick(1);
        assertFalse(tracker.hasDamageHistory());
        assertEquals(0, tracker.getDamagePerSecond(1));
    }

    @Test
    void administrativeHealthLossRestartsRecoveryWithoutEnteringCombatDamageBudgets() {
        DamageTracker tracker = new DamageTracker();
        tracker.recordDamage(0, 35);
        long beforeRevision = tracker.getDamageRevision();
        tracker.recordDamageForRecovery(10, 1000);
        assertEquals(10, tracker.getLastDamageTick());
        assertEquals(beforeRevision + 1, tracker.getDamageRevision());
        assertEquals(110, tracker.getRecoveryReadyTick(10, 100));
        assertEquals(35, tracker.getDamagePerSecond(10));
        assertEquals(1, tracker.getConsecutiveHits(10));
        assertFalse(tracker.isEvading(10));
        DamageTracker.DamageReaction nextOrdinaryHit = tracker.recordDamage(11, 35);
        assertEquals(70, nextOrdinaryHit.damagePerSecond());
        assertFalse(nextOrdinaryHit.enterEvasion());
        assertFalse(nextOrdinaryHit.forceReposition());
        long revisionAfterOrdinaryHit = tracker.getDamageRevision();
        for (float rejected : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            tracker.recordDamageForRecovery(12, rejected);
            assertEquals(11, tracker.getLastDamageTick());
            assertEquals(revisionAfterOrdinaryHit, tracker.getDamageRevision());
        }
    }
}
