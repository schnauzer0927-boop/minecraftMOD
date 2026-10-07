package dev.apexpredator.combat;

/** Combat timings are expressed in server ticks (20 ticks per second). */
public enum AttackType {
    MELEE(CombatState.MELEE, 1, 8, 12, 24),
    CHARGE(CombatState.CHARGE, 1, 14, 16, 100),
    JUMP(CombatState.JUMP, 1, 12, 18, 120),
    TELEPORT(CombatState.TELEPORT, 2, 12, 14, 100),
    SHOCKWAVE(CombatState.SHOCKWAVE, 2, 20, 20, 140),
    RANGED(CombatState.RANGED, 2, 14, 12, 70),
    FEINT(CombatState.FEINT, 2, 14, 16, 130),
    PULL(CombatState.PULL, 3, 20, 20, 160),
    MIRAGE(CombatState.MIRAGE, 3, 16, 12, 180),
    SPECIAL(CombatState.SPECIAL, 3, 50, 30, 360);

    private final CombatState state;
    private final int minimumPhase;
    private final int telegraphTicks;
    private final int recoveryTicks;
    private final int cooldownTicks;

    AttackType(CombatState state, int minimumPhase, int telegraphTicks,
               int recoveryTicks, int cooldownTicks) {
        this.state = state;
        this.minimumPhase = minimumPhase;
        this.telegraphTicks = telegraphTicks;
        this.recoveryTicks = recoveryTicks;
        this.cooldownTicks = cooldownTicks;
    }

    public CombatState state() { return state; }
    public int minimumPhase() { return minimumPhase; }
    public int telegraphTicks() { return telegraphTicks; }
    public int recoveryTicks() { return recoveryTicks; }
    public int cooldownTicks() { return cooldownTicks; }
    public boolean availableInPhase(int phase) { return phase >= minimumPhase; }
}
