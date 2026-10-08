package dev.apexpredator.combat;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Measures health actually lost, independently of the incoming damage cap.
 * The caller applies armor normally, then records its before/after health difference.
 * Rolling windows use server game time, rather than wall-clock time.
 */
public final class DamageTracker {
    public static final int DPS_WINDOW_TICKS = 20;
    private static final int RAPID_WINDOW_TICKS = 10;

    public record Settings(float maximumHitDamage, float burstDamageThreshold,
                           float repositionDamageThreshold, int evasionTicks,
                           int repositionCooldownTicks) {
        public Settings {
            if (!Float.isFinite(maximumHitDamage) || maximumHitDamage <= 0
                    || !Float.isFinite(burstDamageThreshold) || burstDamageThreshold <= 0
                    || !Float.isFinite(repositionDamageThreshold) || repositionDamageThreshold <= 0
                    || evasionTicks < 40 || evasionTicks > 60 || repositionCooldownTicks < 0) {
                throw new IllegalArgumentException("Invalid damage tracker settings");
            }
        }

        public static Settings defaults() { return new Settings(35.0F, 100.0F, 75.0F, 50, 20); }
    }

    public record DamageReaction(boolean enterEvasion, boolean forceReposition,
                                 int evasionTicks, int recentHitCount, float damagePerSecond,
                                 boolean sustainedFire) {
        public static DamageReaction none() {
            return new DamageReaction(false, false, 0, 0, 0.0F, false);
        }
    }

    private record Hit(long tick, float damage) { }

    private final Settings settings;
    private final Deque<Hit> hits = new ArrayDeque<>();
    private long lastTick = Long.MIN_VALUE;
    // Recovery keeps its clock after the short DPS window has been pruned.
    private long lastDamageTick = Long.MIN_VALUE;
    private long recoveryClockStartTick = Long.MIN_VALUE;
    private long damageRevision;
    public static final long MAX_PERSISTED_IDLE_TICKS = 12_000;
    private long evasionUntil = Long.MIN_VALUE;
    private long lastRepositionTick = Long.MIN_VALUE;
    private float damageSinceReposition;
    private float recentDamage;

    public DamageTracker() { this(Settings.defaults()); }
    public DamageTracker(Settings settings) {
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
    }

    public float capIncomingDamage(float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return 0.0F;
        return Math.min(amount, settings.maximumHitDamage());
    }

    public DamageReaction recordDamage(long tick, float actualDamage) {
        tick(tick);
        if (!Float.isFinite(actualDamage) || actualDamage <= 0) {
            return new DamageReaction(false, false, remainingEvasionTicks(tick),
                    hits.size(), recentDamage, isSustainedFire(tick));
        }
        recordRecoveryLoss(tick, actualDamage);
        hits.addLast(new Hit(tick, actualDamage));
        recentDamage += actualDamage;
        damageSinceReposition += actualDamage;

        boolean beginEvasion = recentDamage >= settings.burstDamageThreshold() && !isEvading(tick);
        if (beginEvasion) evasionUntil = tick + settings.evasionTicks();
        boolean reposition = damageSinceReposition >= settings.repositionDamageThreshold()
                && (lastRepositionTick == Long.MIN_VALUE
                    || tick - lastRepositionTick >= settings.repositionCooldownTicks());
        if (reposition) {
            lastRepositionTick = tick;
            damageSinceReposition = 0.0F;
        }
        return new DamageReaction(beginEvasion, reposition, remainingEvasionTicks(tick),
                hits.size(), recentDamage, isSustainedFire(tick));
    }

    /** Administrative damage still interrupts healing without entering the combat DPS/evasion budget. */
    public void recordDamageForRecovery(long tick, float actualDamage) {
        tick(tick);
        recordRecoveryLoss(tick, actualDamage);
    }

    private void recordRecoveryLoss(long tick, float actualDamage) {
        if (!Float.isFinite(actualDamage) || actualDamage <= 0) return;
        lastDamageTick = tick;
        damageRevision++;
    }

    public void tick(long tick) {
        // Game time may restart after loading. Never retain damage from a future timeline.
        if (lastTick != Long.MIN_VALUE && tick < lastTick) reset();
        lastTick = tick;
        if (recoveryClockStartTick == Long.MIN_VALUE) recoveryClockStartTick = tick;
        while (!hits.isEmpty() && tick - hits.peekFirst().tick() >= DPS_WINDOW_TICKS) {
            recentDamage -= hits.removeFirst().damage();
        }
        recentDamage = Math.max(0.0F, recentDamage);
    }

    public float getDamagePerSecond(long tick) { tick(tick); return recentDamage; }
    public int getConsecutiveHits(long tick) { tick(tick); return hits.size(); }
    public boolean isEvading(long tick) { return tick < evasionUntil; }
    public int remainingEvasionTicks(long tick) {
        return isEvading(tick) ? (int) Math.min(Integer.MAX_VALUE, evasionUntil - tick) : 0;
    }

    public boolean isSustainedFire(long tick) {
        tick(tick);
        int count = 0;
        for (Hit hit : hits) {
            if (tick - hit.tick() < RAPID_WINDOW_TICKS && ++count >= 4) return true;
        }
        return false;
    }

    public Settings getSettings() { return settings; }

    /** Only finite, positive health loss advances this timestamp, including ordinary gun damage. */
    public long getLastDamageTick() { return lastDamageTick; }
    /** Distinguishes multiple positive hits on the same tick for reentrant Forge healing events. */
    public long getDamageRevision() { return damageRevision; }
    public boolean hasDamageHistory() { return lastDamageTick != Long.MIN_VALUE; }

    public long getRecoveryReadyTick(long now, int delayTicks) {
        if (delayTicks < 0) throw new IllegalArgumentException("Recovery delay must not be negative");
        tick(now);
        long anchor = hasDamageHistory() ? lastDamageTick : recoveryClockStartTick;
        return anchor > Long.MAX_VALUE - delayTicks ? Long.MAX_VALUE : anchor + delayTicks;
    }

    public long getTicksSinceLastDamage(long now) {
        tick(now);
        long anchor = hasDamageHistory() ? lastDamageTick : recoveryClockStartTick;
        long elapsed = now - anchor;
        return elapsed < 0 ? Long.MAX_VALUE : elapsed;
    }

    /** Store elapsed time rather than an absolute world timestamp; reject future/oversized NBT clocks. */
    public void restoreNoDamageTime(long now, long elapsedTicks, boolean hasActualDamage) {
        reset();
        long elapsed = Math.max(0, Math.min(MAX_PERSISTED_IDLE_TICKS, elapsedTicks));
        long anchor = now < Long.MIN_VALUE + elapsed ? Long.MIN_VALUE + 1 : now - elapsed;
        lastTick = now;
        recoveryClockStartTick = anchor;
        if (hasActualDamage) lastDamageTick = anchor;
    }

    public void reset() {
        hits.clear();
        recentDamage = 0.0F;
        damageSinceReposition = 0.0F;
        evasionUntil = Long.MIN_VALUE;
        lastRepositionTick = Long.MIN_VALUE;
        lastTick = Long.MIN_VALUE;
        lastDamageTick = Long.MIN_VALUE;
        recoveryClockStartTick = Long.MIN_VALUE;
        damageRevision = 0;
    }
}
