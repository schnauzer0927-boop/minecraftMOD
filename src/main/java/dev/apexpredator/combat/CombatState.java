package dev.apexpredator.combat;

/** One authoritative combat state, shared by the controller and animation adapter. */
public enum CombatState {
    IDLE, CHASE, STRAFE, MELEE, CHARGE, JUMP, DODGE, TELEPORT,
    SHOCKWAVE, RANGED, SPECIAL, RECOVERY, FEINT, PULL, MIRAGE,
    BLADE_COMBO, PHASE_CHANGE
}
