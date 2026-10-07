package dev.apexpredator.entity;

/** Authored GeckoLib clip names, with coarse 1.0 adapter states retained for callers. */
public enum AnimationState {
    IDLE, WALK, RUN, SPRINT, STRAFE_LEFT, STRAFE_RIGHT, MELEE_LEFT, MELEE_RIGHT,
    BLADE_COMBO_1, BLADE_COMBO_2, BLADE_COMBO_3, CHARGE_PREPARE, CHARGE, CHARGE_END,
    JUMP_PREPARE, JUMP, SLAM, DODGE_LEFT, DODGE_RIGHT, BACKSTEP, TELEPORT_START, TELEPORT_END,
    SHOCKWAVE_PREPARE, SHOCKWAVE_ATTACK, RANGED_PREPARE, RANGED_ATTACK,
    SPECIAL_PREPARE, SPECIAL_ATTACK, PHASE_CHANGE, HURT_LIGHT, HURT_HEAVY, STAGGER, RECOVERY,
    DEATH, RAGE_IDLE, RAGE_RUN, RAGE_ATTACK,
    ATTACK, HURT, DODGE, TELEPORT, SPECIAL;

    public static boolean hasAnimation(String name) {
        try { return valueOf(name.toUpperCase(java.util.Locale.ROOT)).ordinal() <= RAGE_ATTACK.ordinal(); }
        catch (IllegalArgumentException e) { return false; }
    }
    public static boolean isLooping(String name) {
        return switch (name) {
            case "idle", "walk", "run", "sprint", "strafe_left", "strafe_right", "charge", "rage_idle", "rage_run" -> true;
            default -> false;
        };
    }
}
