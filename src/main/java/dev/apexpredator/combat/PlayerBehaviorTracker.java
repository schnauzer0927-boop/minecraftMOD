package dev.apexpredator.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Per-player observations; changing targets never transfers another player's habits. */
public final class PlayerBehaviorTracker {
    public enum AttackObservation { MELEE, RANGED, GUN }

    public record BehaviorSnapshot(UUID playerId, int meleeAttacks, int rangedAttacks,
                                   int gunAttacks, boolean rapidFire, boolean stationary,
                                   boolean elevated, double lastX, double lastY, double lastZ,
                                   long lastAttackTick) {
        public boolean rangedDominant() { return rangedAttacks >= 2 && rangedAttacks > meleeAttacks; }
        public boolean meleeDominant() { return meleeAttacks >= 2 && meleeAttacks >= rangedAttacks; }
        public int totalAttacks() { return meleeAttacks + rangedAttacks; }
    }

    private static final int ATTACK_WINDOW_TICKS = 60;
    private static final int RAPID_WINDOW_TICKS = 10;
    private static final int FORGET_AFTER_TICKS = 200;
    private static final int MAX_TRACKED_PLAYERS = 128;
    private static final double STATIONARY_RADIUS_SQUARED = 4.0;
    private record Attack(long tick, AttackObservation observation) { }

    private static final class Profile {
        final Deque<Attack> attacks = new ArrayDeque<>();
        double x, y, z, anchorX, anchorZ;
        long stationarySince;
        long lastSeen;
        long lastAttack = Long.MIN_VALUE;
        boolean hasPosition;
        boolean elevated;
    }

    private final Map<UUID, Profile> profiles = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;

    public void observePosition(UUID playerId, long tick, double x, double y, double z, double apexY) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(apexY)) {
            throw new IllegalArgumentException("Observed positions must be finite");
        }
        prune(tick);
        Profile profile = profile(playerId, tick);
        double dx = x - profile.anchorX;
        double dz = z - profile.anchorZ;
        if (!profile.hasPosition || dx * dx + dz * dz > STATIONARY_RADIUS_SQUARED) {
            profile.anchorX = x;
            profile.anchorZ = z;
            profile.stationarySince = tick;
        }
        profile.x = x;
        profile.y = y;
        profile.z = z;
        profile.elevated = y - apexY >= 3.0;
        profile.hasPosition = true;
        profile.lastSeen = tick;
    }

    public void recordAttack(UUID playerId, long tick, AttackObservation observation) {
        java.util.Objects.requireNonNull(observation, "observation");
        prune(tick);
        Profile profile = profile(playerId, tick);
        boolean add = true;
        Iterator<Attack> sameTick = profile.attacks.descendingIterator();
        while (sameTick.hasNext()) {
            Attack previous = sameTick.next();
            if (previous.tick() < tick) break;
            boolean bothRanged = previous.observation() != AttackObservation.MELEE
                    && observation != AttackObservation.MELEE;
            if (previous.tick() == tick && (bothRanged || previous.observation() == observation)) {
                // Fire, vanilla damage and TaCZ's post-hurt event can all describe one shot.
                // Upgrade a generic ranged observation to GUN, without counting an extra attack.
                add = observation == AttackObservation.GUN && previous.observation() == AttackObservation.RANGED;
                if (add) sameTick.remove();
                break;
            }
        }
        if (add) {
            profile.attacks.addLast(new Attack(tick, observation));
        }
        profile.lastSeen = tick;
        profile.lastAttack = tick;
    }

    public BehaviorSnapshot snapshot(UUID playerId, long tick) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        prune(tick);
        Profile profile = profiles.get(playerId);
        if (profile == null) {
            return new BehaviorSnapshot(playerId, 0, 0, 0, false, false, false,
                    0.0, 0.0, 0.0, Long.MIN_VALUE);
        }
        removeOldAttacks(profile, tick);
        int melee = 0, ranged = 0, gun = 0, rapid = 0;
        for (Attack attack : profile.attacks) {
            if (attack.observation() == AttackObservation.MELEE) {
                melee++;
            } else {
                ranged++;
                if (attack.observation() == AttackObservation.GUN) gun++;
                if (tick - attack.tick() < RAPID_WINDOW_TICKS) rapid++;
            }
        }
        boolean stationary = profile.hasPosition && tick - profile.stationarySince >= 40
                && melee + ranged >= 2 && profile.lastAttack >= profile.stationarySince;
        return new BehaviorSnapshot(playerId, melee, ranged, gun, rapid >= 4, stationary,
                profile.elevated, profile.x, profile.y, profile.z, profile.lastAttack);
    }

    public void forget(UUID playerId) { profiles.remove(playerId); }
    public void reset() { profiles.clear(); lastTick = Long.MIN_VALUE; }
    public int trackedPlayerCount() { return profiles.size(); }

    public void prune(long tick) {
        if (lastTick != Long.MIN_VALUE && tick < lastTick) profiles.clear();
        lastTick = tick;
        Iterator<Profile> iterator = profiles.values().iterator();
        while (iterator.hasNext()) {
            Profile profile = iterator.next();
            if (tick - profile.lastSeen > FORGET_AFTER_TICKS) iterator.remove();
            else removeOldAttacks(profile, tick);
        }
    }

    private Profile profile(UUID playerId, long tick) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        Profile existing = profiles.get(playerId);
        if (existing != null) return existing;
        if (profiles.size() >= MAX_TRACKED_PLAYERS) {
            UUID oldest = profiles.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(entry -> entry.getValue().lastSeen))
                    .orElseThrow().getKey();
            profiles.remove(oldest);
        }
        Profile profile = new Profile();
        profile.lastSeen = tick;
        profiles.put(playerId, profile);
        return profile;
    }

    private static void removeOldAttacks(Profile profile, long tick) {
        while (!profile.attacks.isEmpty() && tick - profile.attacks.peekFirst().tick() >= ATTACK_WINDOW_TICKS) {
            profile.attacks.removeFirst();
        }
    }
}
