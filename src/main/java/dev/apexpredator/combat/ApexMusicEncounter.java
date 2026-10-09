package dev.apexpredator.combat;

/** Shared eligibility for the server's target announcement and the local music listener. */
public final class ApexMusicEncounter {
    public static final double TARGET_RANGE = 96;
    public static final double LISTENER_RANGE = 64;

    private ApexMusicEncounter() { }

    public static boolean eligibleTarget(boolean bossAlive, boolean bossRemoved, boolean noAi,
                                         boolean playerAlive, boolean creative, boolean spectator,
                                         boolean sameLevel, double distanceSquared) {
        return bossAlive && !bossRemoved && !noAi && playerAlive && !creative && !spectator
                && sameLevel && inRange(distanceSquared, TARGET_RANGE);
    }

    public static boolean eligibleListener(boolean bossAlive, boolean bossRemoved, boolean noAi,
                                           boolean engaged, boolean playerAlive, boolean playerRemoved,
                                           boolean spectator, boolean sameLevel, double distanceSquared) {
        return bossAlive && !bossRemoved && !noAi && engaged && playerAlive && !playerRemoved
                && !spectator && sameLevel && inRange(distanceSquared, LISTENER_RANGE);
    }

    private static boolean inRange(double distanceSquared, double range) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0 && distanceSquared <= range * range;
    }
}
