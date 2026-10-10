package dev.apexpredator.client.effects;

/**
 * Tracks encounter ownership of vanilla music suppression independently of the
 * boss soundtrack's preference, volume, and sound-engine recovery state.
 */
public final class VanillaMusicSuppressionPolicy {
    public enum Action { NONE, SUPPRESS, RELEASE }

    private boolean suppressed;

    public Action update(boolean validPlayer, int encounters) {
        boolean shouldSuppress = validPlayer && encounters > 0;
        if (shouldSuppress == suppressed) return Action.NONE;
        suppressed = shouldSuppress;
        return suppressed ? Action.SUPPRESS : Action.RELEASE;
    }

    public boolean isSuppressed() { return suppressed; }

    public Action reset() {
        boolean release = suppressed;
        suppressed = false;
        return release ? Action.RELEASE : Action.NONE;
    }
}
