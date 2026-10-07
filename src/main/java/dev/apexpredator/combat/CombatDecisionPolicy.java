package dev.apexpredator.combat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Pure decision policy so adaptation and attack restrictions can be tested without a game. */
public final class CombatDecisionPolicy {
    public record Context(int phase, double distance, boolean lineOfSight,
                          boolean rangedPressure, boolean meleePressure,
                          boolean elevated, boolean stationary, boolean rapidFire) {
        public Context {
            if (phase < 1 || phase > 3 || !Double.isFinite(distance) || distance < 0) {
                throw new IllegalArgumentException("Invalid combat context");
            }
        }
    }

    private CombatDecisionPolicy() { }

    public static Map<AttackType, Integer> weights(Context context, Predicate<AttackType> ready,
                                                  List<AttackType> recentAttacks) {
        Map<AttackType, Integer> weights = new EnumMap<>(AttackType.class);
        for (AttackType attack : AttackType.values()) {
            if (!attack.availableInPhase(context.phase()) || !ready.test(attack)) continue;
            int weight = baseWeight(attack, context);
            if (weight > 0) weights.put(attack, weight);
        }
        // Repetition is discouraged, while the only suitable attack remains usable.
        if (weights.size() > 1) {
            for (Map.Entry<AttackType, Integer> entry : weights.entrySet()) {
                long repeats = recentAttacks.stream().filter(entry.getKey()::equals).count();
                if (repeats > 0) {
                    entry.setValue(Math.max(1, entry.getValue() / (int) (1 + 3 * repeats)));
                }
            }
        }
        return weights;
    }

    /** Returns null when movement is the only appropriate action. */
    public static AttackType choose(Context context, Predicate<AttackType> ready,
                                    List<AttackType> recentAttacks, IntUnaryOperator nextInt) {
        Map<AttackType, Integer> weights = weights(context, ready, recentAttacks);
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return null;
        int choice = nextInt.applyAsInt(total);
        if (choice < 0 || choice >= total) throw new IllegalArgumentException("Random value out of range");
        for (Map.Entry<AttackType, Integer> entry : weights.entrySet()) {
            choice -= entry.getValue();
            if (choice < 0) return entry.getKey();
        }
        throw new IllegalStateException("Weighted attack selection exhausted");
    }

    private static int baseWeight(AttackType attack, Context c) {
        double distance = c.distance();
        // Warping can address cover, but every damaging ranged/charge action requires sight.
        if (!c.lineOfSight() && attack != AttackType.TELEPORT) return 0;
        return switch (attack) {
            case MELEE -> distance <= 4.3 ? 28 + (c.meleePressure() ? 8 : 0) : 0;
            case CHARGE -> distance >= 5 && distance <= 28
                    ? 18 + (c.rangedPressure() ? 20 : 0) : 0;
            case JUMP -> distance >= 4 && distance <= 20
                    ? 14 + (c.elevated() ? 38 : 0) : 0;
            case TELEPORT -> distance >= 4 && distance <= 40
                    ? 12 + (c.stationary() ? 34 : 0) + (c.elevated() ? 18 : 0)
                        + (c.rapidFire() ? 12 : 0) : 0;
            case SHOCKWAVE -> distance <= 10
                    ? 16 + (c.meleePressure() ? 20 : 0) : 0;
            case RANGED -> distance >= 8 && distance <= 48
                    ? 20 + (c.elevated() ? 22 : 0) + (c.rangedPressure() ? 10 : 0) : 0;
            case FEINT -> distance >= 5 && distance <= 24
                    ? 12 + (c.stationary() ? 10 : 0) : 0;
            case PULL -> distance >= 7 && distance <= 20 ? 18 : 0;
            case MIRAGE -> distance <= 32 ? 10 + (c.rapidFire() ? 18 : 0) : 0;
            case SPECIAL -> distance >= 5 && distance <= 24 ? 10 : 0;
        };
    }
}
