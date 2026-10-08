package dev.apexpredator.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Health amounts and clocks are independent of Minecraft rendering and wall-clock time. */
class Phase3HealingControllerTest {
    private static final Phase3HealingController.Settings DEFAULTS = Phase3HealingController.Settings.defaults();

    @Test
    void entryHealRespectsMaximumHealthAndTheMasterSwitch() {
        assertEquals(60, Phase3HealingController.entryHealAmount(DEFAULTS, 200, 600));
        assertEquals(10, Phase3HealingController.entryHealAmount(DEFAULTS, 590, 600));
        assertEquals(0, Phase3HealingController.entryHealAmount(DEFAULTS, 600, 600));
        var disabled = new Phase3HealingController.Settings(false, 60, 100, 4, 300);
        assertEquals(0, Phase3HealingController.entryHealAmount(disabled, 200, 600));
    }

    @Test
    void onlyTheLivingFinalPhaseMayRegenerate() {
        for (int phase : new int[]{1, 2}) {
            var controller = new Phase3HealingController();
            DamageTracker damage = hitAt(0);
            for (int tick = 0; tick <= 140; tick++) {
                var result = controller.tick(tick, phase, true, false, 100, 600, damage, DEFAULTS);
                assertEquals(0, result.healAmount());
                assertEquals(Phase3HealingController.State.INACTIVE, result.state());
                assertFalse(result.warningStarted());
            }
        }
        var controller = new Phase3HealingController();
        var dead = controller.tick(100, 3, false, false, 0, 600, hitAt(0), DEFAULTS);
        assertEquals(0, dead.healAmount());
        assertEquals(Phase3HealingController.State.INACTIVE, dead.state());
    }

    @Test
    void fiveSecondDelayIncludesOneSecondWarningBeforeFourHpPulse() {
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        for (int tick = 0; tick < 80; tick++) {
            var result = tick(controller, damage, tick, 200);
            assertEquals(0, result.healAmount());
            assertEquals(Phase3HealingController.State.INACTIVE, result.state());
        }
        var warning = tick(controller, damage, 80, 200);
        assertEquals(Phase3HealingController.State.WARNING, warning.state());
        assertTrue(warning.warningStarted());
        assertEquals(0, warning.healAmount());
        assertEquals(80, controller.getStateStartTick());
        for (int tick = 81; tick < 100; tick++) assertEquals(0, tick(controller, damage, tick, 200).healAmount());
        var healing = tick(controller, damage, 100, 200);
        assertEquals(Phase3HealingController.State.HEALING, healing.state());
        assertEquals(4, healing.healAmount());
        assertEquals(120, controller.getNextHealTick());
        assertEquals(0, tick(controller, damage, 119, 204).healAmount());
        assertEquals(4, tick(controller, damage, 120, 204).healAmount());
    }

    @Test
    void actualDamageCancelsImmediatelyAndRestartsTheFullNoDamageDelay() {
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        tick(controller, damage, 80, 200);
        assertEquals(4, tick(controller, damage, 100, 200).healAmount());
        damage.recordDamage(102, 3);
        controller.onDamage();
        assertEquals(Phase3HealingController.State.INACTIVE, controller.getState());
        for (int tick = 102; tick < 182; tick++) assertEquals(0, tick(controller, damage, tick, 201).healAmount());
        assertTrue(tick(controller, damage, 182, 201).warningStarted());
        assertEquals(0, tick(controller, damage, 201, 201).healAmount());
        assertEquals(4, tick(controller, damage, 202, 201).healAmount());
    }

    @Test
    void zeroDamageAndExpiredDpsWindowDoNotMoveTheHealingDeadline() {
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        assertEquals(0, damage.getDamagePerSecond(20));
        damage.recordDamage(40, 0);
        damage.recordDamage(60, Float.NaN);
        tick(controller, damage, 80, 200);
        assertEquals(4, tick(controller, damage, 100, 200).healAmount());
        assertEquals(0, damage.getLastDamageTick());
    }

    @Test
    void capClipsTheLastPulseAndPreventsFurtherRecovery() {
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        tick(controller, damage, 80, 299);
        assertEquals(1, tick(controller, damage, 100, 299).healAmount());
        var capped = tick(controller, damage, 120, 300);
        assertEquals(0, capped.healAmount());
        assertEquals(Phase3HealingController.State.INACTIVE, capped.state());
        assertEquals(0, tick(controller, damage, 200, 400).healAmount());
    }

    @Test
    void entityMaximumHealthAlsoCapsPassiveRecovery() {
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        controller.tick(80, 3, true, false, 280, 282, damage, DEFAULTS);
        assertEquals(2, controller.tick(100, 3, true, false, 280, 282, damage, DEFAULTS).healAmount());
        assertEquals(0, controller.tick(120, 3, true, false, 282, 282, damage, DEFAULTS).healAmount());
    }

    @Test
    void executionPauseAccruesNoDebtAndRequiresNewWarningBeforeResuming() {
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        tick(controller, damage, 80, 200);
        assertEquals(4, tick(controller, damage, 100, 200).healAmount());
        assertEquals(0, controller.tick(101, 3, true, true, 204, 600, damage, DEFAULTS).healAmount());
        assertEquals(Phase3HealingController.State.INACTIVE, controller.getState());
        assertEquals(0, controller.tick(500, 3, true, true, 204, 600, damage, DEFAULTS).healAmount());
        var resumed = tick(controller, damage, 501, 204);
        assertTrue(resumed.warningStarted());
        assertEquals(0, resumed.healAmount());
        assertEquals(0, tick(controller, damage, 520, 204).healAmount());
        assertEquals(4, tick(controller, damage, 521, 204).healAmount());
    }

    @Test
    void damageReceivedDuringExecutionStillControlsItsPostAttackDeadline() {
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        controller.tick(100, 3, true, true, 200, 600, damage, DEFAULTS);
        damage.recordDamage(150, 1);
        controller.onDamage();
        assertEquals(0, tick(controller, damage, 170, 199).healAmount());
        assertEquals(Phase3HealingController.State.INACTIVE, controller.getState());
        assertTrue(tick(controller, damage, 230, 199).warningStarted());
        assertEquals(0, tick(controller, damage, 249, 199).healAmount());
        assertEquals(4, tick(controller, damage, 250, 199).healAmount());
    }

    @Test
    void configuredDelayRateAndCapReplaceDefaults() {
        var settings = new Phase3HealingController.Settings(true, 12, 60, 7, 250);
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        assertFalse(controller.tick(39, 3, true, false, 246, 600, damage, settings).warningStarted());
        assertTrue(controller.tick(40, 3, true, false, 246, 600, damage, settings).warningStarted());
        assertEquals(0, controller.tick(59, 3, true, false, 246, 600, damage, settings).healAmount());
        assertEquals(4, controller.tick(60, 3, true, false, 246, 600, damage, settings).healAmount());
        assertEquals(12, Phase3HealingController.entryHealAmount(settings, 200, 600));
    }

    @Test
    void switchingOffAlsoCancelsWarningAndActiveHealing() {
        var disabled = new Phase3HealingController.Settings(false, 60, 100, 4, 300);
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        tick(controller, damage, 80, 200);
        var stopped = controller.tick(100, 3, true, false, 200, 600, damage, disabled);
        assertEquals(0, stopped.healAmount());
        assertFalse(stopped.warningStarted());
        assertEquals(Phase3HealingController.State.INACTIVE, stopped.state());
        assertEquals(0, controller.tick(500, 3, true, false, 200, 600, damage, disabled).healAmount());
    }

    @Test
    void leavingFinalPhaseCancelsAnExistingWarning() {
        var controller = new Phase3HealingController();
        DamageTracker damage = hitAt(0);
        tick(controller, damage, 80, 200);
        var stopped = controller.tick(100, 2, true, false, 200, 600, damage, DEFAULTS);
        assertEquals(0, stopped.healAmount());
        assertEquals(Phase3HealingController.State.INACTIVE, stopped.state());
    }

    private static DamageTracker hitAt(long tick) {
        DamageTracker damage = new DamageTracker();
        damage.recordDamage(tick, 1);
        return damage;
    }

    private static Phase3HealingController.TickResult tick(Phase3HealingController controller,
                                                          DamageTracker damage, long tick, float health) {
        return controller.tick(tick, 3, true, false, health, 600, damage, DEFAULTS);
    }
}
