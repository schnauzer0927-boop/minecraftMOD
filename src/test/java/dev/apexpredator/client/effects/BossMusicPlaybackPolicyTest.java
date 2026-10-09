package dev.apexpredator.client.effects;

import org.junit.jupiter.api.Test;

import static dev.apexpredator.client.effects.BossMusicPlaybackPolicy.Action.NONE;
import static dev.apexpredator.client.effects.BossMusicPlaybackPolicy.Action.START;
import static dev.apexpredator.client.effects.BossMusicPlaybackPolicy.Action.STOP;
import static org.junit.jupiter.api.Assertions.*;

/** Regressions for one encounter-wide soundtrack rather than one sound per tick or boss. */
class BossMusicPlaybackPolicyTest {
    @Test
    void aLongBattleRequestsOneInstanceAcrossMultipleSongLengths() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 0, 1, false));
        // The native looping channel stays active across each end-of-track boundary.
        for (int tick = 1; tick < 36_000; tick++) {
            assertEquals(NONE, update(policy, tick, 1, true), "Duplicate soundtrack at tick " + tick);
        }
        assertTrue(policy.isRequested());
    }

    @Test
    void defeatingOneBossOrSwitchingTargetsKeepsTheOtherEncounterPlaying() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 0, 2, false));
        assertEquals(NONE, update(policy, 1, 2, true));
        assertEquals(NONE, update(policy, 2, 1, true), "First defeated boss must not silence the second");
        assertEquals(NONE, update(policy, 3, 3, true), "Additional bosses must not add sound channels");
        assertEquals(NONE, update(policy, 4, 1, true), "Changing the selected boss must not restart the song");
        assertEquals(STOP, update(policy, 5, 0, true), "Only the final disengagement stops the soundtrack");
        assertFalse(policy.isRequested());
    }

    @Test
    void aNewBattleStartsFreshAfterThePreviousOneEnds() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 0, 1, false));
        assertEquals(NONE, update(policy, 1, 1, true));
        assertEquals(STOP, update(policy, 2, 0, true));
        assertEquals(NONE, update(policy, 3, 0, false));
        assertEquals(START, update(policy, 4, 1, false), "Re-engagement needs a new instance starting at sample zero");
        assertEquals(NONE, update(policy, 5, 1, true));
    }

    @Test
    void deadOrAbsentLocalPlayerStopsImmediatelyEvenWhileBossesRemain() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 0, 2, false));
        assertEquals(NONE, update(policy, 1, 2, true));
        assertEquals(STOP, policy.update(2, true, false, 2, true, true));
        assertFalse(policy.isRequested());
        assertEquals(NONE, policy.update(3, true, false, 2, true, false));
        assertEquals(START, policy.update(4, true, true, 1, true, false));
    }

    @Test
    void preferenceSwitchClearsTheSoundAndAllowsImmediateReenable() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 0, 1, false));
        assertEquals(NONE, update(policy, 1, 1, true));
        assertEquals(STOP, policy.update(2, false, true, 1, true, true));
        assertEquals(NONE, policy.update(3, false, true, 1, true, false));
        assertEquals(START, policy.update(4, true, true, 1, true, false));
    }

    @Test
    void zeroMasterOrRecordsVolumeSilencesThenUnmuteStartsWithoutLayering() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 0, 1, false));
        assertEquals(NONE, update(policy, 1, 1, true));
        assertEquals(STOP, policy.update(2, true, true, 1, false, true));
        for (int tick = 3; tick < 200; tick++) {
            assertEquals(NONE, policy.update(tick, true, true, 1, false, false));
        }
        assertEquals(START, policy.update(200, true, true, 1, true, false));
        assertEquals(NONE, update(policy, 201, 1, true));
    }

    @Test
    void asynchronousSoundStartupHasGraceWithoutDuplicatePlayRequests() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 100, 1, false));
        for (int tick = 101; tick < 120; tick++) {
            assertEquals(NONE, update(policy, tick, 1, false), "Do not layer instances while OpenAL starts a stream");
        }
        assertEquals(NONE, update(policy, 120, 1, true));
        assertTrue(policy.isRequested());
    }

    @Test
    void lostAudioChannelStopsBeforeRetryAndDoesNotSpamNewInstances() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 0, 1, false));
        assertEquals(NONE, update(policy, 1, 1, true));
        assertEquals(STOP, update(policy, 30, 1, false), "Reloaded or vanished channel must be retired first");
        assertFalse(policy.isRequested());
        for (int tick = 31; tick < 130; tick++) {
            assertEquals(NONE, update(policy, tick, 1, false), "Do not flood a failing audio device at tick " + tick);
        }
        assertEquals(START, update(policy, 130, 1, false));
        assertEquals(NONE, update(policy, 131, 1, true));
        assertTrue(policy.isRequested());
    }

    @Test
    void disconnectOrDimensionResetCancelsAnOldPlaybackRequest() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 9_000, 1, false));
        assertEquals(NONE, update(policy, 9_001, 1, true));
        policy.reset();
        assertFalse(policy.isRequested());
        assertEquals(NONE, update(policy, 0, 0, false));
        assertEquals(START, update(policy, 1, 1, false), "The new level must not inherit the old sound or retry deadline");
    }

    @Test
    void disengagementClearsAReloadRetryBeforeReengagement() {
        var policy = new BossMusicPlaybackPolicy();
        assertEquals(START, update(policy, 0, 1, false));
        assertEquals(NONE, update(policy, 1, 1, true));
        assertEquals(STOP, update(policy, 30, 1, false));
        assertEquals(NONE, update(policy, 31, 0, false));
        assertEquals(START, update(policy, 32, 1, false), "A fresh battle must not inherit a failed old battle's retry timer");
    }

    private static BossMusicPlaybackPolicy.Action update(BossMusicPlaybackPolicy policy, long tick,
                                                         int encounters, boolean soundActive) {
        return policy.update(tick, true, true, encounters, true, soundActive);
    }
}
