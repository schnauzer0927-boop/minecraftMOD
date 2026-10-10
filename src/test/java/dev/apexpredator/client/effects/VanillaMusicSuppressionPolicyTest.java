package dev.apexpredator.client.effects;

import org.junit.jupiter.api.Test;

import static dev.apexpredator.client.effects.VanillaMusicSuppressionPolicy.Action.NONE;
import static dev.apexpredator.client.effects.VanillaMusicSuppressionPolicy.Action.RELEASE;
import static dev.apexpredator.client.effects.VanillaMusicSuppressionPolicy.Action.SUPPRESS;
import static org.junit.jupiter.api.Assertions.*;

/** Encounter ownership must survive changes to the independent boss music stream. */
class VanillaMusicSuppressionPolicyTest {
    @Test
    void noBattleOrInvalidPlayerDoesNotSuppressVanillaMusic() {
        var policy = new VanillaMusicSuppressionPolicy();
        assertEquals(NONE, policy.update(true, 0));
        assertEquals(NONE, policy.update(false, 2));
        assertEquals(NONE, policy.update(true, -1));
        assertEquals(NONE, policy.reset());
        assertFalse(policy.isSuppressed());
    }

    @Test
    void longBattleAndTargetSwitchesDoNotRepeatMusicApiOperations() {
        var policy = new VanillaMusicSuppressionPolicy();
        assertEquals(SUPPRESS, policy.update(true, 1));
        for (int tick = 1; tick < 36_000; tick++) {
            int encounters = 1 + tick % 3;
            assertEquals(NONE, policy.update(true, encounters), "Repeated music operation at tick " + tick);
        }
        assertTrue(policy.isSuppressed());
    }

    @Test
    void onlyTheLastBossEndingCombatReleasesVanillaMusic() {
        var policy = new VanillaMusicSuppressionPolicy();
        assertEquals(SUPPRESS, policy.update(true, 3));
        assertEquals(NONE, policy.update(true, 2));
        assertEquals(NONE, policy.update(true, 1));
        assertTrue(policy.isSuppressed());
        assertEquals(RELEASE, policy.update(true, 0));
        assertEquals(NONE, policy.update(true, 0));
        assertFalse(policy.isSuppressed());
    }

    @Test
    void localPlayerDeathReleasesOnceEvenIfBossesRemain() {
        var policy = new VanillaMusicSuppressionPolicy();
        assertEquals(SUPPRESS, policy.update(true, 2));
        assertEquals(RELEASE, policy.update(false, 2));
        assertEquals(NONE, policy.update(false, 2));
        assertFalse(policy.isSuppressed());
        assertEquals(SUPPRESS, policy.update(true, 1), "A new live encounter owns a fresh suppression interval");
    }

    @Test
    void worldResetReleasesOnceAndDoesNotCarryOwnershipIntoAnotherWorld() {
        var policy = new VanillaMusicSuppressionPolicy();
        assertEquals(SUPPRESS, policy.update(true, 2));
        assertEquals(RELEASE, policy.reset());
        assertEquals(NONE, policy.reset());
        assertEquals(NONE, policy.update(true, 0));
        assertFalse(policy.isSuppressed());
        assertEquals(SUPPRESS, policy.update(true, 1));
        assertEquals(RELEASE, policy.update(true, 0));
    }

    @Test
    void disablingBossMusicDuringCombatKeepsVanillaMusicSuppressed() {
        var suppression = new VanillaMusicSuppressionPolicy();
        var playback = new BossMusicPlaybackPolicy();
        assertEquals(SUPPRESS, suppression.update(true, 1));
        assertEquals(BossMusicPlaybackPolicy.Action.START, playback.update(0, true, true, 1, true, false));
        assertEquals(BossMusicPlaybackPolicy.Action.STOP, playback.update(1, false, true, 1, true, true));
        for (int tick = 2; tick < 200; tick++) {
            assertEquals(BossMusicPlaybackPolicy.Action.NONE, playback.update(tick, false, true, 1, true, false));
            assertEquals(NONE, suppression.update(true, 1));
        }
        assertTrue(suppression.isSuppressed());
        assertEquals(RELEASE, suppression.update(true, 0));
    }

    @Test
    void mutingMasterOrRecordsDoesNotReleaseVanillaMusicDuringCombat() {
        var suppression = new VanillaMusicSuppressionPolicy();
        var playback = new BossMusicPlaybackPolicy();
        assertEquals(SUPPRESS, suppression.update(true, 1));
        assertEquals(BossMusicPlaybackPolicy.Action.START, playback.update(0, true, true, 1, true, false));
        // Either zero-volume slider yields audible=false for the boss stream.
        assertEquals(BossMusicPlaybackPolicy.Action.STOP, playback.update(1, true, true, 1, false, true));
        assertEquals(NONE, suppression.update(true, 1));
        assertEquals(BossMusicPlaybackPolicy.Action.NONE, playback.update(2, true, true, 1, false, false));
        assertEquals(NONE, suppression.update(true, 1));
        assertEquals(BossMusicPlaybackPolicy.Action.START, playback.update(3, true, true, 1, true, false));
        assertEquals(NONE, suppression.update(true, 1));
        assertTrue(suppression.isSuppressed());
        assertEquals(RELEASE, suppression.update(true, 0));
    }

    @Test
    void lostBossSoundChannelAndItsRetryDoNotAllowVanillaMusicToReturn() {
        var suppression = new VanillaMusicSuppressionPolicy();
        var playback = new BossMusicPlaybackPolicy();
        assertEquals(SUPPRESS, suppression.update(true, 2));
        assertEquals(BossMusicPlaybackPolicy.Action.START, playback.update(0, true, true, 2, true, false));
        assertEquals(BossMusicPlaybackPolicy.Action.NONE, playback.update(1, true, true, 2, true, true));
        assertEquals(BossMusicPlaybackPolicy.Action.STOP, playback.update(30, true, true, 2, true, false));
        assertEquals(NONE, suppression.update(true, 2));
        for (int tick = 31; tick < 130; tick++) {
            assertEquals(BossMusicPlaybackPolicy.Action.NONE, playback.update(tick, true, true, 1, true, false));
            assertEquals(NONE, suppression.update(true, 1));
        }
        assertEquals(BossMusicPlaybackPolicy.Action.START, playback.update(130, true, true, 1, true, false));
        assertEquals(NONE, suppression.update(true, 1));
        assertTrue(suppression.isSuppressed());
        assertEquals(RELEASE, suppression.update(true, 0));
    }
}
