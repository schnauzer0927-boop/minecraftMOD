package dev.apexpredator.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApexPhaseManagerTest {
    @Test
    void boundariesBelongToTheNewPhaseAndHealingNeverRegresses() {
        ApexPhaseManager manager = new ApexPhaseManager();
        assertFalse(manager.update(600));
        assertFalse(manager.update(400.01F));
        assertTrue(manager.update(400));
        assertEquals(2, manager.getPhaseIndex());
        assertFalse(manager.update(600));
        assertTrue(manager.update(200));
        assertEquals(3, manager.getPhaseIndex());
        assertFalse(manager.update(400));
    }

    @Test
    void largeHitOrLoadedEntityCanEnterPhaseThreeDirectly() {
        ApexPhaseManager manager = new ApexPhaseManager();
        assertTrue(manager.update(100));
        assertEquals(ApexPhaseManager.Phase.THREE, manager.getPhase());
        manager.restore(1);
        assertEquals(3, manager.getPhaseIndex());
        assertThrows(IllegalArgumentException.class, () -> manager.restore(4));
    }

    @Test
    void unlockedAttacksAndDecisionFrequencyFollowPhase() {
        ApexPhaseManager manager = new ApexPhaseManager();
        assertFalse(AttackType.SPECIAL.availableInPhase(manager.getPhaseIndex()));
        assertTrue(AttackType.CHARGE.availableInPhase(manager.getPhaseIndex()));
        int initialInterval = manager.getPhase().decisionIntervalTicks();
        manager.update(200);
        assertTrue(AttackType.SPECIAL.availableInPhase(manager.getPhaseIndex()));
        assertTrue(manager.getPhase().decisionIntervalTicks() < initialInterval);
    }
}
