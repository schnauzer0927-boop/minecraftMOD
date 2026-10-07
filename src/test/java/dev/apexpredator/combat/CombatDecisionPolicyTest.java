package dev.apexpredator.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatDecisionPolicyTest {
    private CombatDecisionPolicy.Context context(int phase, double distance, boolean visible) {
        return new CombatDecisionPolicy.Context(phase, distance, visible,
                false, false, false, false, false);
    }

    @Test void earlyPhaseNeverSelectsLateAbilities() {
        var weights = CombatDecisionPolicy.weights(context(1, 10, true), attack -> true, List.of());
        assertEquals(java.util.Set.of(AttackType.CHARGE, AttackType.JUMP), weights.keySet());
    }

    @Test void blockedSightPreventsDamagingAttacksAndFirstPhaseKeepsChasing() {
        assertNull(CombatDecisionPolicy.choose(context(1, 10, false), attack -> true, List.of(), bound -> 0));
        assertEquals(AttackType.TELEPORT,
                CombatDecisionPolicy.choose(context(2, 10, false), attack -> true, List.of(), bound -> 0));
        assertNull(CombatDecisionPolicy.choose(context(3, 2, false), attack -> true, List.of(), bound -> 0));
    }

    @Test void cooldownsCanRequireMovementRatherThanForcedAttack() {
        assertNull(CombatDecisionPolicy.choose(context(3, 10, true), attack -> false, List.of(), bound -> 0));
        assertNull(CombatDecisionPolicy.choose(context(3, 80, true), attack -> true, List.of(), bound -> 0));
    }

    @Test void repeatedAttacksLoseWeightWhenAnAlternativeExists() {
        var normal = CombatDecisionPolicy.weights(context(1, 10, true), attack -> true, List.of());
        var repeated = CombatDecisionPolicy.weights(context(1, 10, true), attack -> true,
                List.of(AttackType.CHARGE, AttackType.CHARGE));
        assertTrue(repeated.get(AttackType.CHARGE) < normal.get(AttackType.CHARGE));
        assertEquals(normal.get(AttackType.JUMP), repeated.get(AttackType.JUMP));
    }

    @Test void soleAvailableAttackIsNotPenalizedIntoInaction() {
        assertEquals(AttackType.MELEE, CombatDecisionPolicy.choose(context(1, 2, true), attack -> true,
                List.of(AttackType.MELEE, AttackType.MELEE), bound -> 0));
    }

    @Test void highGroundAndStationaryFireChangeTheSelectedWeights() {
        var normal = CombatDecisionPolicy.weights(context(3, 12, true), attack -> true, List.of());
        var adaptive = CombatDecisionPolicy.weights(new CombatDecisionPolicy.Context(3, 12, true,
                true, false, true, true, true), attack -> true, List.of());
        assertTrue(adaptive.get(AttackType.JUMP) > normal.get(AttackType.JUMP));
        assertTrue(adaptive.get(AttackType.TELEPORT) > normal.get(AttackType.TELEPORT));
        assertTrue(adaptive.get(AttackType.RANGED) > normal.get(AttackType.RANGED));
        assertTrue(adaptive.get(AttackType.MIRAGE) > normal.get(AttackType.MIRAGE));
    }
}
