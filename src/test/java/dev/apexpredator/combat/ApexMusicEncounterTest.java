package dev.apexpredator.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Encounter bounds shared by authoritative server target sync and client soundtrack filtering. */
class ApexMusicEncounterTest {
    @Test
    void nearbyListenersUseSixtyFourBlocksWithoutChangingTheAiPursuitRange() {
        assertTrue(listenerAt(0));
        assertTrue(listenerAt(32 * 32));
        assertTrue(listenerAt(64 * 64));
        assertFalse(listenerAt(64.001 * 64.001));
        assertTrue(targetAt(96 * 96), "The soundtrack must preserve the existing AI target cutoff");
        assertFalse(targetAt(96.001 * 96.001));
    }

    @Test
    void deadRemovedOrNonCombatBossesAreNotEncounters() {
        assertFalse(ApexMusicEncounter.eligibleTarget(false, false, false, true, false, false, true, 16));
        assertFalse(ApexMusicEncounter.eligibleTarget(true, true, false, true, false, false, true, 16));
        assertFalse(ApexMusicEncounter.eligibleTarget(true, false, true, true, false, false, true, 16));
        assertFalse(ApexMusicEncounter.eligibleListener(true, false, false, false,
                true, false, false, true, 16), "Merely approaching an idle boss is not a battle");
    }

    @Test
    void deathCreativeAndSpectatorPlayersCannotKeepMusicAlive() {
        assertFalse(ApexMusicEncounter.eligibleTarget(true, false, false, false, false, false, true, 16));
        assertFalse(ApexMusicEncounter.eligibleTarget(true, false, false, true, true, false, true, 16));
        assertFalse(ApexMusicEncounter.eligibleTarget(true, false, false, true, false, true, true, 16));
        assertFalse(ApexMusicEncounter.eligibleListener(true, false, false, true,
                false, false, false, true, 16));
        assertFalse(ApexMusicEncounter.eligibleListener(true, false, false, true,
                true, true, false, true, 16));
        assertFalse(ApexMusicEncounter.eligibleListener(true, false, false, true,
                true, false, true, true, 16));
    }

    @Test
    void aPlayerAtTheSameCoordinatesInAnotherDimensionIsNotEngaged() {
        assertFalse(ApexMusicEncounter.eligibleTarget(true, false, false, true, false, false, false, 0));
        assertFalse(ApexMusicEncounter.eligibleListener(true, false, false, true,
                true, false, false, false, 0));
        assertTrue(listenerAt(0));
    }

    @Test
    void malformedDistancesCannotLeaveAnEncounterSoundRunning() {
        for (double distance : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertFalse(targetAt(distance));
            assertFalse(listenerAt(distance));
        }
    }

    private static boolean targetAt(double distanceSquared) {
        return ApexMusicEncounter.eligibleTarget(true, false, false, true, false, false, true, distanceSquared);
    }

    private static boolean listenerAt(double distanceSquared) {
        return ApexMusicEncounter.eligibleListener(true, false, false, true,
                true, false, false, true, distanceSquared);
    }
}
