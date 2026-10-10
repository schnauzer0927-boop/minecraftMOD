package dev.apexpredator.client.effects;

import com.mojang.logging.LogUtils;
import dev.apexpredator.ApexClientConfig;
import dev.apexpredator.ApexConfig;
import dev.apexpredator.combat.ApexMusicEncounter;
import dev.apexpredator.effect.ApexSounds;
import dev.apexpredator.entity.ApexEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A single client-local stream shared by all current nearby player encounters. */
final class ApexBossMusic extends AbstractTickableSoundInstance {
    private static final BossMusicPlaybackPolicy PLAYBACK = new BossMusicPlaybackPolicy();
    private static final VanillaMusicSuppressionPolicy VANILLA_MUSIC = new VanillaMusicSuppressionPolicy();
    private static ApexBossMusic active;
    private static long ticks;
    private static boolean encounterPresent;
    private static int encounterCount;
    private static int sequence;
    private final int playbackSequence;
    private boolean confirmed;

    private ApexBossMusic() {
        super(ApexSounds.event("boss_music"), SoundSource.RECORDS, RandomSource.create());
        // The streaming sound engine loops its decoder at EOF in the same channel.
        looping = true;
        delay = 0;
        relative = true;
        attenuation = Attenuation.NONE;
        volume = .85F;
        pitch = 1F;
        playbackSequence = ++sequence;
    }

    static void update(Minecraft minecraft) {
        boolean validPlayer = minecraft.level != null && minecraft.player != null
                && minecraft.player.isAlive() && !minecraft.player.isRemoved() && !minecraft.player.isSpectator();
        boolean enabled = ApexClientConfig.BOSS_MUSIC.get();
        boolean audible = minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && minecraft.options.getSoundSourceVolume(SoundSource.RECORDS) > 0;
        int encounters = 0;
        // Vanilla background music stays suppressed during combat even when this BGM is muted.
        if (validPlayer) {
            for (ApexEntity apex : minecraft.level.getEntitiesOfClass(ApexEntity.class,
                    minecraft.player.getBoundingBox().inflate(ApexMusicEncounter.LISTENER_RANGE))) {
                if (apex.isMusicEncounterFor(minecraft.player)) encounters++;
            }
        }
        encounterCount = encounters;
        encounterPresent = encounters > 0;
        updateVanillaMusic(minecraft, VANILLA_MUSIC.update(validPlayer, encounters));
        boolean soundActive = active != null && !active.isStopped() && minecraft.getSoundManager().isActive(active);
        if (soundActive && !active.confirmed) {
            active.confirmed = true;
            debug("playing", active.playbackSequence, encounters, true, "channel_active");
        }
        // Keep Minecraft's paused channel; do not create a fresh unpaused sound in a pause menu.
        // The screen changes before Minecraft updates isPaused for its next frame.
        var server = minecraft.getSingleplayerServer();
        boolean pauseScreen = minecraft.screen != null && minecraft.screen.isPauseScreen()
                || minecraft.getOverlay() != null && minecraft.getOverlay().isPauseScreen();
        boolean paused = minecraft.isPaused() || server != null && !server.isPublished() && pauseScreen;
        if (paused && enabled && validPlayer && encounterPresent && audible) return;
        var action = PLAYBACK.update(++ticks, enabled, validPlayer, encounters, audible, soundActive);
        switch (action) {
            case START -> {
                // Defensive stop precedes a fresh request, including after an engine reload.
                stopActive(minecraft, "replace");
                active = new ApexBossMusic();
                minecraft.getSoundManager().play(active);
                debug("start", active.playbackSequence, encounters, minecraft.getSoundManager().isActive(active), "encounter");
            }
            case STOP -> stopActive(minecraft, !enabled ? "disabled" : !validPlayer ? "player_unavailable"
                    : !audible ? "muted" : !encounterPresent ? "no_encounter" : "sound_engine_recovery");
            case NONE -> { }
        }
    }

    private static void stopActive(Minecraft minecraft, String reason) {
        if (active == null) return;
        debug("stop", active.playbackSequence, encounterCount,
                minecraft.getSoundManager().isActive(active), reason);
        active.stop();
        minecraft.getSoundManager().stop(active);
        active = null;
    }

    private static void debug(String action, int id, int encounters, boolean playing, String reason) {
        if (ApexConfig.DEBUG.get()) LogUtils.getLogger().info(
                "[APEX BGM DEBUG] {} instance={} category=RECORDS volume=0.85 pitch=1.0 looping=true playing={} encounters={} reason={}",
                action, id, playing, encounters, reason);
    }

    static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        stopActive(minecraft, "world_reset");
        updateVanillaMusic(minecraft, VANILLA_MUSIC.reset());
        PLAYBACK.reset();
        ticks = 0;
        encounterPresent = false;
        encounterCount = 0;
    }

    static boolean isVanillaMusicSuppressed() { return VANILLA_MUSIC.isSuppressed(); }

    private static void updateVanillaMusic(Minecraft minecraft, VanillaMusicSuppressionPolicy.Action action) {
        if (action == VanillaMusicSuppressionPolicy.Action.NONE) return;
        // stopPlaying also changes the vanilla timer: call it only on battle boundaries.
        // Clearing the manager on release retires any instance whose play request we blocked.
        minecraft.getMusicManager().stopPlaying();
        if (action == VanillaMusicSuppressionPolicy.Action.SUPPRESS) {
            minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
        }
        if (ApexConfig.DEBUG.get()) LogUtils.getLogger().info(
                "[APEX BGM DEBUG] vanilla_music action={} encounters={}", action, encounterCount);
    }

    @Override public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!encounterPresent || minecraft.level == null || minecraft.player == null
                || !minecraft.player.isAlive() || minecraft.player.isRemoved() || minecraft.player.isSpectator()
                || !ApexClientConfig.BOSS_MUSIC.get()) stop();
    }
}
