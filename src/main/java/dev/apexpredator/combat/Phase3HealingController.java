package dev.apexpredator.combat;

import java.util.Objects;

/** Schedules interruptible recovery without changing attack decisions or owning another damage clock. */
public final class Phase3HealingController {
    public static final int WARNING_TICKS = 20;
    public static final int PULSE_INTERVAL_TICKS = 20;

    public enum State { INACTIVE, WARNING, HEALING }

    public record Settings(boolean enabled, float entryHeal, int healDelayTicks,
                           float healPerSecond, float healCap) {
        public Settings {
            if (!Float.isFinite(entryHeal) || entryHeal < 0 || healDelayTicks < 0
                    || !Float.isFinite(healPerSecond) || healPerSecond < 0
                    || !Float.isFinite(healCap) || healCap < 0) {
                throw new IllegalArgumentException("Invalid Phase 3 healing settings");
            }
        }

        public static Settings defaults() { return new Settings(true, 60, 100, 4, 300); }
    }

    public record TickResult(State state, boolean warningStarted, float healAmount) { }

    private State state = State.INACTIVE;
    private long stateStartTick;
    private long nextHealTick = Long.MIN_VALUE;

    public static float entryHealAmount(Settings settings, float health, float maximumHealth) {
        Objects.requireNonNull(settings, "settings");
        if (!settings.enabled() || !Float.isFinite(health) || !Float.isFinite(maximumHealth)
                || health <= 0 || maximumHealth <= health) return 0;
        return Math.min(settings.entryHeal(), maximumHealth - health);
    }

    /** The caller applies the returned positive amount once; delayed ticks never accumulate extra pulses. */
    public TickResult tick(long now, int phase, boolean alive, boolean blocked, float health,
                           float maximumHealth, DamageTracker damage, Settings settings) {
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(settings, "settings");
        float ceiling = Math.min(settings.healCap(), maximumHealth);
        if (!settings.enabled() || phase != 3 || !alive || blocked || !Float.isFinite(health)
                || !Float.isFinite(maximumHealth) || health <= 0 || health >= ceiling
                || settings.healPerSecond() <= 0) {
            reset();
            return new TickResult(state, false, 0);
        }

        long ready = damage.getRecoveryReadyTick(now, settings.healDelayTicks());
        long warningAt = ready < Long.MIN_VALUE + WARNING_TICKS ? Long.MIN_VALUE : ready - WARNING_TICKS;
        if (now < warningAt) {
            reset();
            return new TickResult(state, false, 0);
        }
        if (state == State.INACTIVE) {
            state = State.WARNING;
            stateStartTick = now;
            nextHealTick = Math.max(ready, addTicks(now, WARNING_TICKS));
            return new TickResult(state, true, 0);
        }
        // Runtime increases to the delay cannot silently shorten the promised no-damage interval.
        if (now < ready) {
            nextHealTick = Math.max(nextHealTick, ready);
            return new TickResult(state, false, 0);
        }
        if (now < nextHealTick) return new TickResult(state, false, 0);

        if (state != State.HEALING) stateStartTick = now;
        state = State.HEALING;
        nextHealTick = addTicks(now, PULSE_INTERVAL_TICKS);
        return new TickResult(state, false, Math.min(settings.healPerSecond(), ceiling - health));
    }

    private static long addTicks(long now, int ticks) {
        return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
    }

    /** Called in the same actual-HP-loss hook that records damage in DamageTracker. */
    public void onDamage() { reset(); }
    public void reset() {
        state = State.INACTIVE;
        stateStartTick = 0;
        nextHealTick = Long.MIN_VALUE;
    }
    public State getState() { return state; }
    public long getNextHealTick() { return nextHealTick; }
    public long getStateStartTick() { return stateStartTick; }
}
