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
}
