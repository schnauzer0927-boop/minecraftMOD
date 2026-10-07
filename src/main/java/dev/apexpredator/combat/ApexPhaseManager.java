package dev.apexpredator.combat;

/** Health thresholds are inclusive; healing never returns the boss to an earlier phase. */
public final class ApexPhaseManager {
    public enum Phase {
        ONE(1, 1.0F), TWO(2, 1.18F), THREE(3, 1.35F);

        private final int index;
        private final float movementMultiplier;

        Phase(int index, float movementMultiplier) {
            this.index = index;
            this.movementMultiplier = movementMultiplier;
        }

        public int index() { return index; }
        public float movementMultiplier() { return movementMultiplier; }
        public int decisionIntervalTicks() { return this == THREE ? 3 : this == TWO ? 5 : 8; }
    }

    private Phase phase = Phase.ONE;

    public boolean update(float health) {
        if (!Float.isFinite(health)) throw new IllegalArgumentException("Health must be finite");
        Phase next = health <= 200.0F ? Phase.THREE : health <= 400.0F ? Phase.TWO : Phase.ONE;
        if (next.index() <= phase.index()) return false;
        phase = next;
        return true;
    }

    public Phase getPhase() { return phase; }
    public int getPhaseIndex() { return phase.index(); }

    /** Restore persisted phase without replaying transitions or undoing previous progress. */
    public void restore(int index) {
        if (index < 1 || index > 3) throw new IllegalArgumentException("Phase must be 1, 2 or 3");
        Phase next = Phase.values()[index - 1];
        if (next.index() > phase.index()) phase = next;
    }
}
