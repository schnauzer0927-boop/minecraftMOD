package dev.apexpredator.client.effects;

/**
 * One encounter owns at most one sound request. The sound engine, rather than this
 * controller, repeats the stream; retries only recover a rejected or lost channel.
 */
public final class BossMusicPlaybackPolicy {
    public enum Action { NONE, START, STOP }
    public static final int START_GRACE_TICKS = 20;
    public static final int RETRY_TICKS = 100;
    private boolean requested;
    private long startedAt;
    private long retryAt = Long.MIN_VALUE;

    public Action update(long tick, boolean enabled, boolean validPlayer, int encounters,
                         boolean audible, boolean soundActive) {
        if (!enabled || !validPlayer || encounters <= 0 || !audible) {
            boolean stop = requested;
            reset();
            return stop ? Action.STOP : Action.NONE;
        }
        if (requested) {
            if (soundActive || tick - startedAt < START_GRACE_TICKS) return Action.NONE;
            requested = false;
            retryAt = tick + RETRY_TICKS;
            return Action.STOP;
        }
        if (tick < retryAt) return Action.NONE;
        requested = true;
        startedAt = tick;
        return Action.START;
    }

    public boolean isRequested() { return requested; }

    public void reset() {
        requested = false;
        startedAt = 0;
        retryAt = Long.MIN_VALUE;
    }
}
