package dev.apexpredator.combat;

import dev.apexpredator.entity.ApexEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns one combat action at a time. Vanilla goals may find a target or keep the
 * entity afloat, but must not schedule movement or melee attacks independently.
 */
public final class CombatController {
    private final ApexEntity entity;
    private final AttackManager attacks;
    private final Map<AttackType, Long> cooldownUntil = new EnumMap<>(AttackType.class);
    private final Deque<AttackType> recentAttacks = new ArrayDeque<>(3);
    private CombatState state = CombatState.IDLE;
    private AttackType activeAttack;
    private AttackType lastAttack;
    private LivingEntity actionTarget;
    private int attackElapsed;
    private int recoveryRemaining;
    private int dodgeElapsed;
    private long dodgeUntil;
    private long dodgeReadyAt;
    private long movementUntil;
    private long nextDecision;
    private long lastNavigationTick = Long.MIN_VALUE;
    private boolean pendingReposition;
    private boolean pendingEvasion;
    private int requestedEvasionTicks;
    private int strafeDirection = 1;

    public CombatController(ApexEntity entity) {
        this.entity = java.util.Objects.requireNonNull(entity, "entity");
        this.attacks = new AttackManager(entity);
    }

    /** Called only by the entity's server-side AI tick. */
    public void tick() {
        if (entity.level().isClientSide || !entity.isAlive()) return;
        long now = entity.level().getGameTime();
        entity.damageTracker().tick(now);
        LivingEntity target = entity.getTarget();
        if (!validTarget(target)) {
            if (target != null) entity.setTarget(null);
            stop();
            return;
        }

        entity.behaviorTracker().observePosition(target.getUUID(), now,
                target.getX(), target.getY(), target.getZ(), entity.getY());
        PlayerBehaviorTracker.BehaviorSnapshot behavior = entity.behaviorTracker().snapshot(target.getUUID(), now);
        entity.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (pendingReposition) {
            pendingReposition = false;
            if (entity.getPhase() == 1) {
                // Warps unlock in phase two. Early damage thresholds produce a
                // physical sidestep without shortening an existing burst dodge.
                if (state != CombatState.DODGE) startDodge(target, now, 10);
                else {
                    dodgeUntil = Math.max(dodgeUntil, now + 10);
                    dodgeReadyAt = Math.max(dodgeReadyAt, dodgeUntil + 28);
                }
            } else {
                // A committed strike cannot finish from a new, untelegraphed position.
                if (activeAttack != null) {
                    cancelAttack();
                    enterRecovery(6);
                }
                attacks.reposition(target);
            }
        }
        if (pendingEvasion || entity.damageTracker().isEvading(now)) {
            int duration = Math.max(requestedEvasionTicks, entity.damageTracker().remainingEvasionTicks(now));
            pendingEvasion = false;
            requestedEvasionTicks = 0;
            if (state != CombatState.DODGE) startDodge(target, now, Math.max(1, duration));
            else {
                dodgeUntil = Math.max(dodgeUntil, now + duration);
                dodgeReadyAt = Math.max(dodgeReadyAt, dodgeUntil + 28);
            }
        }

        if (state == CombatState.DODGE) {
            if (now < dodgeUntil) {
                attacks.dodge(target, dodgeElapsed++);
                return;
            }
            enterRecovery(6);
        }

        if (activeAttack != null) {
            // A telegraphed attack follows its original target; switching target cancels it.
            if (actionTarget != target || !validTarget(actionTarget)) {
                cancelAttack();
                enterRecovery(6);
            } else if (attacks.tick(activeAttack, target, attackElapsed++)) {
                int recovery = activeAttack.recoveryTicks();
                if (entity.getPhase() == 3) recovery = Math.max(6, recovery * 3 / 4);
                activeAttack = null;
                actionTarget = null;
                enterRecovery(recovery);
            }
            return;
        }

        if (recoveryRemaining > 0) {
            recoveryRemaining--;
            entity.getNavigation().stop();
            if (recoveryRemaining == 0) nextDecision = now;
            return;
        }

        if (state == CombatState.STRAFE && now < movementUntil) {
            strafe(target, now, behavior.rapidFire());
            return;
        }

        if (now < nextDecision) {
            chase(target, now);
            return;
        }
        nextDecision = now + decisionInterval();

        // A short evasive response also reacts to sustained low-damage fire. The
        // independent cooldown prevents indefinite evasion from every projectile.
        if ((behavior.rapidFire() || entity.damageTracker().isSustainedFire(now))
                && now >= dodgeReadyAt && entity.getRandom().nextInt(3) == 0) {
            startDodge(target, now, 12);
            attacks.dodge(target, dodgeElapsed++);
            return;
        }

        double distance = entity.distanceTo(target);
        boolean visible = entity.getSensing().hasLineOfSight(target);
        if (visible && behavior.meleeDominant() && distance <= 4.5
                && now >= dodgeReadyAt && entity.getRandom().nextInt(4) == 0) {
            startDodge(target, now, 10);
            attacks.dodge(target, dodgeElapsed++);
            return;
        }
        // Ranged pressure changes movement as well as attack weights. Strafe windows
        // are short enough that the boss still closes distance and attacks regularly.
        if (visible && (behavior.rangedDominant() || behavior.rapidFire())
                && distance >= 4 && distance <= 36 && entity.getRandom().nextInt(3) == 0) {
            strafeDirection = entity.getRandom().nextBoolean() ? 1 : -1;
            movementUntil = now + (behavior.rapidFire() ? 14 : 20);
            setState(CombatState.STRAFE);
            entity.getNavigation().stop();
            strafe(target, now, behavior.rapidFire());
            return;
        }

        CombatDecisionPolicy.Context context = new CombatDecisionPolicy.Context(entity.getPhase(), distance,
                visible, behavior.rangedDominant(), behavior.meleeDominant(),
                behavior.elevated(), behavior.stationary(), behavior.rapidFire());
        AttackType selected = CombatDecisionPolicy.choose(context,
                attack -> now >= cooldownUntil.getOrDefault(attack, Long.MIN_VALUE),
                List.copyOf(recentAttacks), bound -> entity.getRandom().nextInt(bound));
        if (selected == null) chase(target, now);
        else startAttack(selected, target, now);
    }

    /** Entity damage hooks feed the controller health-loss reactions, never raw bullet damage. */
    public void onDamage(DamageTracker.DamageReaction reaction) {
        if (entity.level().isClientSide) return;
        pendingReposition |= reaction.forceReposition();
        pendingEvasion |= reaction.enterEvasion();
        requestedEvasionTicks = Math.max(requestedEvasionTicks, reaction.evasionTicks());
    }

    public CombatState getState() { return state; }
    public AttackType getLastAttack() { return lastAttack; }
    public int getAttackElapsed() { return attackElapsed; }

    public String debugSummary() {
        LivingEntity target = entity.getTarget();
        return String.format(Locale.ROOT, "phase=%d state=%s target=%s last=%s DPS=%.2f hits=%d",
                entity.getPhase(), state, target == null ? "none" : target.getName().getString(),
                lastAttack == null ? "none" : lastAttack,
                entity.damageTracker().getDamagePerSecond(entity.level().getGameTime()),
                entity.damageTracker().getConsecutiveHits(entity.level().getGameTime()));
    }

    private void startAttack(AttackType type, LivingEntity target, long now) {
        entity.getNavigation().stop();
        activeAttack = type;
        lastAttack = type;
        actionTarget = target;
        attackElapsed = 0;
        cooldownUntil.put(type, now + type.cooldownTicks());
        if (recentAttacks.size() == 3) recentAttacks.removeFirst();
        recentAttacks.addLast(type);
        setState(type.state());
        attacks.begin(type, target);
    }

    private void startDodge(LivingEntity target, long now, int duration) {
        cancelAttack();
        entity.getNavigation().stop();
        recoveryRemaining = 0;
        dodgeElapsed = 0;
        dodgeUntil = now + duration;
        dodgeReadyAt = dodgeUntil + 28;
        actionTarget = target;
        setState(CombatState.DODGE);
    }

    private void enterRecovery(int ticks) {
        entity.getNavigation().stop();
        setState(CombatState.RECOVERY);
        recoveryRemaining = ticks;
    }

    private void chase(LivingEntity target, long now) {
        setState(CombatState.CHASE);
        if (lastNavigationTick == Long.MIN_VALUE || now - lastNavigationTick >= 6) {
            entity.getNavigation().moveTo(target, 1.15);
            lastNavigationTick = now;
        }
    }

    private void strafe(LivingEntity target, long now, boolean rapidFire) {
        setState(CombatState.STRAFE);
        entity.getNavigation().stop();
        Vec3 toward = target.position().subtract(entity.position()).multiply(1, 0, 1);
        if (toward.horizontalDistanceSqr() < 0.01) return;
        Vec3 forward = toward.normalize();
        int direction = strafeDirection * ((now / 8) % 2 == 0 ? 1 : -1);
        Vec3 lateral = new Vec3(-forward.z, 0, forward.x).scale(direction);
        Vec3 step = lateral.scale(3).add(forward.scale(toward.length() > 7 ? 1.8 : -0.6));
        entity.getMoveControl().setWantedPosition(entity.getX() + step.x, entity.getY(),
                entity.getZ() + step.z, rapidFire ? 1.8 : 1.45);
    }

    private boolean validTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isSpectator()
                && target.level() == entity.level() && entity.distanceToSqr(target) <= 96 * 96
                && !(target instanceof Player player && player.isCreative());
    }

    private int decisionInterval() {
        return entity.getPhase() == 3 ? 3 : entity.getPhase() == 2 ? 5 : 8;
    }

    private void setState(CombatState next) {
        state = next;
        entity.setCombatState(next);
    }

    private void cancelAttack() {
        attacks.cancel();
        activeAttack = null;
        actionTarget = null;
        attackElapsed = 0;
    }

    private void stop() {
        if (state != CombatState.IDLE || activeAttack != null) {
            cancelAttack();
            entity.getNavigation().stop();
            setState(CombatState.IDLE);
        }
        recoveryRemaining = 0;
        pendingReposition = false;
        pendingEvasion = false;
        requestedEvasionTicks = 0;
        movementUntil = 0;
    }
}
