package dev.apexpredator.combat;

import com.mojang.logging.LogUtils;
import dev.apexpredator.ApexConfig;
import dev.apexpredator.entity.ApexBoltEntity;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.effect.ApexEffects;
import dev.apexpredator.effect.ApexSounds;
import dev.apexpredator.effect.ApexTerrainEffects;
import dev.apexpredator.effect.EffectKind;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Executes one committed attack. Decisions and cooldowns belong to CombatController. */
public final class AttackManager {
    public static final float EXECUTION_RADIUS = 15;
    public static final int EXECUTION_WARNING_TICKS = 36;
    public static final int EXECUTION_X_TICK = 40;
    public static final int EXECUTION_SHOCKWAVE_TICK = 52;
    public static final int EXECUTION_END_TICK = 58;
    public static final float EXECUTION_MAXIMUM_DAMAGE = 42;
    private final ApexEntity apex;
    private final Set<Integer> struck = new HashSet<>();
    private final Map<Integer, Float> executionDamage = new HashMap<>();
    private Vec3 targetSnapshot = Vec3.ZERO;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 direction = Vec3.ZERO;
    private int strafeSign;
    private boolean launched;
    private boolean meleeLeft;
    private boolean dodgeBackstep;
    private AttackType committedAttack;
    private AttackArea attackArea;
    private Vec3 slamTarget;
    private Vec3 actualLanding;
    private Vec3 damageCenter;
    private boolean slamTargetValid;
    private boolean jumpOwnsGravity;
    private boolean previousNoGravity;

    public AttackManager(ApexEntity apex) {
        this.apex = apex;
    }

    public void begin(AttackType attack, LivingEntity target) {
        restoreJumpGravity();
        struck.clear();
        executionDamage.clear();
        launched = false;
        apex.getNavigation().stop();
        stopHorizontalMotion();
        origin = apex.position();
        targetSnapshot = target.position();
        committedAttack = attack;
        attackArea = null;
        slamTarget = null;
        actualLanding = null;
        damageCenter = null;
        slamTargetValid = false;
        if (attack == AttackType.JUMP) {
            var ground = SlamTargetResolver.resolve(apex, targetSnapshot);
            slamTargetValid = ground.isPresent();
            slamTarget = ground.orElse(targetSnapshot);
            attackArea = AttackArea.ground(slamTarget, 5);
        } else if (attack == AttackType.SHOCKWAVE || attack == AttackType.PULL) {
            attackArea = AttackArea.ground(origin, attack == AttackType.PULL || apex.getPhase() == 3 ? 10 : 6);
        } else if (attack == AttackType.SPECIAL) {
            attackArea = AttackArea.ground(origin, EXECUTION_RADIUS);
        }
        direction = targetSnapshot.subtract(origin).multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < 0.001) direction = apex.getLookAngle().multiply(1, 0, 1).normalize();
        strafeSign = apex.getRandom().nextBoolean() ? 1 : -1;
        int windup = attack == AttackType.MELEE
                ? Math.max(4, attack.telegraphTicks() - 2 * (apex.getPhase() - 1)) : attack.telegraphTicks();
        String animation = switch (attack) {
            case MELEE -> apex.getPhase() == 3 ? "rage_attack" : ((meleeLeft = !meleeLeft) ? "melee_left" : "melee_right");
            case BLADE_COMBO -> "blade_combo_1";
            case CHARGE, FEINT -> "charge_prepare";
            case JUMP -> "jump_prepare";
            case TELEPORT, MIRAGE -> "teleport_start";
            case SHOCKWAVE, PULL -> "shockwave_prepare";
            case RANGED -> "ranged_prepare";
            case SPECIAL -> "special_prepare";
        };
        apex.playVisual(animation, windup + (attack == AttackType.MELEE || attack == AttackType.BLADE_COMBO ? 4 : 1));
        switch (attack) {
            case CHARGE, FEINT -> {
                effect(EffectKind.CHARGE_PREPARE, origin, direction, 2, windup);
                ApexSounds.play(apex, "charge_start");
            }
            case JUMP -> areaEffect(EffectKind.WARNING_RING, windup + 45);
            case SHOCKWAVE, PULL -> areaEffect(EffectKind.WARNING_RING, windup);
            case TELEPORT, MIRAGE -> effect(EffectKind.TELEPORT_OUT, origin, direction, 2, windup);
            case RANGED -> effect(EffectKind.RANGED_PREPARE, origin, direction, 2, windup);
            case SPECIAL -> {
                areaEffect(EffectKind.SPECIAL_PREPARE, windup);
                ApexSounds.play(apex, "special_charge");
            }
            default -> {
                effect(EffectKind.RANGED_PREPARE, origin, direction, 1, windup);
                ApexSounds.play(apex, "blade_swing");
            }
        }
    }

    /** Includes the windup. Returns true only when this attack's active interval ends. */
    public boolean tick(AttackType attack, LivingEntity target, int elapsed) {
        if (apex.level().isClientSide || !validVictim(target)) {
            restoreJumpGravity();
            return true;
        }
        apex.getNavigation().stop();
        if (attack == AttackType.JUMP) stopOrdinaryMovement();
        int windup = attack == AttackType.MELEE
                ? Math.max(4, attack.telegraphTicks() - 2 * (apex.getPhase() - 1))
                : attack.telegraphTicks();
        if (attack == AttackType.JUMP && slamTarget != null) {
            apex.getLookControl().setLookAt(slamTarget.x, slamTarget.y + 1, slamTarget.z, 30, 30);
        } else if (attack != AttackType.SPECIAL && attack != AttackType.CHARGE && attack != AttackType.FEINT) {
            apex.getLookControl().setLookAt(target, 30, 30);
        } else if (attack != AttackType.SPECIAL) face(direction);
        if (elapsed < windup) {
            stopHorizontalMotion();
            telegraph(attack, elapsed, windup);
            // A feint has a visible sideways step before it commits to its fixed dash direction.
            if (attack == AttackType.FEINT && elapsed >= 5 && elapsed <= 9) {
                Vec3 side = new Vec3(-direction.z, 0, direction.x).scale(strafeSign * 0.4);
                move(side);
            }
            return false;
        }
        return switch (attack) {
            case MELEE -> melee(target, elapsed, windup);
            case BLADE_COMBO -> bladeCombo(target, elapsed, windup);
            case CHARGE, FEINT -> charge(target, elapsed, windup);
            case JUMP -> jump(target, elapsed, windup);
            case TELEPORT -> teleportAttack(target, elapsed, windup);
            case SHOCKWAVE -> shockwaveAttack(target, elapsed, windup);
            case RANGED -> ranged(target, elapsed, windup);
            case PULL -> pull(target, elapsed, windup);
            case MIRAGE -> mirage(target, elapsed, windup);
            case SPECIAL -> special(target, elapsed, windup);
        };
    }

    public void cancel() {
        restoreJumpGravity();
        apex.getNavigation().stop();
        stopHorizontalMotion();
        struck.clear();
        executionDamage.clear();
        launched = false;
    }

    public AttackArea getAttackArea() { return attackArea; }
    public Vec3 getSlamTarget() { return slamTarget; }
    public Vec3 getActualLanding() { return actualLanding; }
    public Vec3 getDamageCenter() { return damageCenter; }
    public double getLandingError() {
        return actualLanding == null || slamTarget == null ? Double.NaN : actualLanding.distanceTo(slamTarget);
    }
    public boolean isJumpCommitted() { return committedAttack == AttackType.JUMP && slamTargetValid && actualLanding == null; }

    public String debugSummary(LivingEntity target) {
        if (attackArea == null) return "";
        String area = String.format(Locale.ROOT,
                " Attack=%s WarningCenter=%s DamageCenter=%s radius=%.2f playerDistance=%.2f YDifference=%.2f InsideHitbox=%s",
                committedAttack, coordinates(attackArea.center()), coordinates(damageCenter), attackArea.radius(),
                target == null ? Double.NaN : attackArea.horizontalDistance(target.getBoundingBox()),
                target == null ? Double.NaN : target.getY() - attackArea.center().y,
                target != null && attackArea.intersects(target.getBoundingBox()));
        return slamTarget == null ? area : area + String.format(Locale.ROOT,
                " SlamTarget=%s ActualLanding=%s LandingError=%.3f", coordinates(slamTarget),
                coordinates(actualLanding), getLandingError());
    }

    private boolean melee(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup) {
            apex.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            effect(meleeLeft ? EffectKind.BLADE_LEFT : EffectKind.BLADE_RIGHT,
                    apex.position(), direction, 3.4F, 8);
            if (inReach(target, 3.4) && apex.hasLineOfSight(target)) {
                if (apex.doHurtTarget(target)) hitEffect(target, 2);
                // A readable counter pushes sustained close-range attackers out of combo range.
                if (apex.getPhase() >= 2) knockback(target, 0.7);
            }
            ApexSounds.play(apex, "blade_swing");
        }
        return elapsed >= windup + 2;
    }

    /** Three distinct server strikes, spaced beyond vanilla's ten-tick hurt cooldown. */
    private boolean bladeCombo(LivingEntity target, int elapsed, int windup) {
        int active = elapsed - windup;
        if (active == 4) apex.playVisual("blade_combo_2", 12);
        if (active == 16) apex.playVisual("blade_combo_3", 13);
        if (active == 0 || active == 12 || active == 24) {
            int strike = active / 12;
            effect(strike == 0 ? EffectKind.BLADE_RIGHT : strike == 1
                            ? EffectKind.BLADE_LEFT : EffectKind.BLADE_VERTICAL,
                    apex.position(), direction, strike == 2 ? 4.2F : 3.6F, strike == 2 ? 16 : 8);
            apex.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            ApexSounds.play(apex, "blade_swing");
            if (inReach(target, strike == 2 ? 4.2 : 3.6) && apex.hasLineOfSight(target)
                    && target.hurt(apex.damageSources().mobAttack(apex), strike == 2 ? 18 : 14)) {
                hitEffect(target, strike == 2 ? 3 : 1.5F);
                if (strike == 2) knockback(target, 0.8);
            }
        }
        return active >= 28;
    }

    private boolean charge(LivingEntity target, int elapsed, int windup) {
        int active = elapsed - windup;
        int duration = apex.getPhase() == 3 ? 11 : 12;
        if (active >= duration || (active > 1 && apex.horizontalCollision)) {
            stopHorizontalMotion();
            apex.playVisual("charge_end", 8);
            return true;
        }
        if (active == 0) {
            apex.playVisual("charge", duration);
            ApexSounds.play(apex, "charge_dash");
            effect(EffectKind.CHARGE_TRAIL, apex.position(), direction, 2, duration);
        }
        Vec3 velocity = direction.scale(apex.getPhase() == 3 ? 1.9 : 1.35);
        move(velocity);
        AABB path = apex.getBoundingBox().expandTowards(velocity).inflate(0.35, 0.2, 0.35);
        for (LivingEntity victim : victims(path, target)) {
            if (struck.add(victim.getId()) && apex.hasLineOfSight(victim)) {
                if (victim.hurt(apex.damageSources().mobAttack(apex), apex.getPhase() == 3 ? 22 : 18)) {
                    hitEffect(victim, 3);
                    knockback(victim, 1.2);
                }
            }
        }
        return false;
    }

    private boolean jump(LivingEntity target, int elapsed, int windup) {
        int active = elapsed - windup;
        // The deliberate landing attack should not trigger health-loss evasion against itself.
        apex.fallDistance = 0;
        if (!launched) {
            if (!slamTargetValid) {
                logSlam(target);
                return true;
            }
            launched = true;
            Vec3 delta = slamTarget.subtract(apex.position());
            double lift = Math.min(1.25, 0.85 + Math.max(0, delta.y) * 0.07);
            previousNoGravity = apex.isNoGravity();
            jumpOwnsGravity = true;
            apex.setNoGravity(false);
            apex.setDeltaMovement(0, lift, 0);
            apex.hurtMarked = true;
            apex.playVisual("jump", 45);
            sound(SoundEvents.ENDER_DRAGON_FLAP, 1, 0.7F);
        }
        if (active >= 5 && apex.onGround()) {
            actualLanding = apex.position();
            restoreJumpGravity();
            // A wall or newly placed obstacle may stop the body far from the warning.
            // Cancel that blocked landing rather than damaging an unreachable remote area.
            if (getLandingError() > 1.25) {
                stopHorizontalMotion();
                logSlam(target);
                return true;
            }
            apex.playVisual("slam", 12);
            areaEffect(EffectKind.SLAM, 18);
            ApexSounds.play(apex, "slam");
            ApexTerrainEffects.slam(apex, slamTarget);
            shockwave(target, 16, 1.2);
            stopHorizontalMotion();
            logSlam(target);
            return true;
        }
        if (active > 45) {
            restoreJumpGravity();
            stopHorizontalMotion();
            logSlam(target);
            return true;
        }
        // Recompute remaining flight from actual height/vertical speed and Forge gravity.
        // Only the horizontal correction changes: the committed target never follows a player.
        int remaining = remainingFlightTicks(active);
        Vec3 delta = slamTarget.subtract(apex.position()).multiply(1, 0, 1);
        Vec3 horizontal = delta.scale(1.0 / remaining);
        if (horizontal.lengthSqr() > 1.4 * 1.4) horizontal = horizontal.normalize().scale(1.4);
        move(horizontal);
        return false;
    }

    private int remainingFlightTicks(int active) {
        double y = apex.getY();
        double velocity = apex.getDeltaMovement().y;
        double gravity = Math.max(0.001, apex.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get()));
        for (int step = 1; step <= Math.max(1, 46 - active); step++) {
            y += velocity;
            if (velocity < 0 && y <= slamTarget.y + 0.001) return step;
            velocity = (velocity - gravity) * 0.98;
        }
        return Math.max(1, 46 - active);
    }

    private void stopOrdinaryMovement() {
        apex.getNavigation().stop();
        apex.getMoveControl().setWantedPosition(apex.getX(), apex.getY(), apex.getZ(), 0);
        apex.setSpeed(0);
        apex.xxa = 0;
        apex.zza = 0;
    }

    private void restoreJumpGravity() {
        if (jumpOwnsGravity) {
            apex.setNoGravity(previousNoGravity);
            jumpOwnsGravity = false;
        }
    }

    private boolean teleportAttack(LivingEntity target, int elapsed, int windup) {
        int active = elapsed - windup;
        int count = apex.getPhase() == 3 ? 3 : 1;
        if (active >= count * 14) return true;
        if (active % 14 == 0) {
            struck.clear();
            apex.playVisual("teleport_start", 4);
            reposition(target);
        }
        if (active % 14 == 6) {
            apex.playVisual(active % 28 == 6 ? "melee_right" : "melee_left", 8);
            effect(active % 28 == 6 ? EffectKind.BLADE_RIGHT : EffectKind.BLADE_LEFT,
                    apex.position(), target.position().subtract(apex.position()).normalize(), 3.6F, 8);
            apex.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (inReach(target, 3.6) && apex.hasLineOfSight(target)) {
                if (apex.doHurtTarget(target)) hitEffect(target, 2);
                knockback(target, 0.45);
            }
            sound(SoundEvents.IRON_GOLEM_ATTACK, 0.9F, 1.1F);
        }
        return false;
    }

    private boolean shockwaveAttack(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup) {
            apex.playVisual("shockwave_attack", 10);
            shockwave(target, apex.getPhase() == 3 ? 18 : 14, 1.3);
        }
        return elapsed >= windup + 4;
    }

    private boolean ranged(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup && apex.level() instanceof ServerLevel server) {
            apex.playVisual("ranged_attack", 8);
            effect(EffectKind.RANGED_LAUNCH, apex.position(), direction, 2, 10);
            ApexBoltEntity bolt = new ApexBoltEntity(ApexRegistries.APEX_BOLT.get(), server, apex, target);
            server.addFreshEntity(bolt);
            sound(SoundEvents.BLAZE_SHOOT, 1, 0.7F);
        }
        return elapsed >= windup + 2;
    }

    private boolean pull(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup) {
            damageCenter = attackArea.center();
            logArea(target);
            for (LivingEntity victim : victims(attackArea.bounds(), target)) {
                Vec3 delta = attackArea.center().subtract(victim.position()).multiply(1, 0, 1);
                double distance = delta.length();
                // Stop at a four-block gap and leave the full recovery window to escape.
                if (victim != target) logArea(victim);
                if (distance > 4 && attackArea.intersects(victim.getBoundingBox()) && areaHasLineOfSight(victim)) {
                    victim.setDeltaMovement(victim.getDeltaMovement().add(
                            delta.normalize().scale(Math.min(0.75, (distance - 4) * 0.12))).add(0, 0.12, 0));
                    victim.hurtMarked = true;
                }
            }
            sound(SoundEvents.ENDERMAN_SCREAM, 1, 0.65F);
            apex.playVisual("shockwave_attack", 8);
            areaEffect(EffectKind.SHOCKWAVE, 16);
        }
        return elapsed >= windup + 3;
    }

    private boolean mirage(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup) {
            for (int copy = 0; copy < 3; copy++) {
                double angle = copy * Math.PI * 2 / 3 + strafeSign * 0.4;
                Vec3 feet = origin.add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
                effect(EffectKind.MIRAGE, feet, direction, 1.5F, 30);
            }
        }
        if (elapsed == windup + 16) reposition(target);
        return elapsed >= windup + 18;
    }

    private boolean special(LivingEntity target, int elapsed, int windup) {
        int active = elapsed - windup;
        if (active == 0) {
            apex.playVisual("special_attack", EXECUTION_END_TICK);
            ApexSounds.play(apex, "special_attack");
        }
        // Lanes are locked when the attack begins; the target is never tracked during the execution.
        if (active < 36) {
            int stage = active / 12;
            int stageTick = active % 12;
            Vec3 lane = executionDirection(stage);
            face(lane);
            if (stageTick == 0) {
                if (stage > 0) safeTeleport(origin, false);
                effect(EffectKind.SPECIAL_SLASH, attackArea.center(), lane, (float) attackArea.radius(), 10);
                effect(EffectKind.CHARGE_TRAIL, apex.position(), lane, 2, 6);
                ApexSounds.play(apex, "blade_swing");
                damageExecution(target, lane, 0.93, 8);
            }
            if (stageTick < 5 && !apex.horizontalCollision) move(lane.scale(1.55));
            else stopHorizontalMotion();
        } else {
            stopHorizontalMotion();
            face(direction);
            if (active == 36) safeTeleport(origin, false);
            if (active == EXECUTION_X_TICK) {
                areaEffect(EffectKind.SPECIAL_FINISH, 18);
                ApexSounds.play(apex, "special_attack");
                // An X has two diagonal axes, leaving four visible gaps between the blades.
                damageExecution(target, rotateHorizontal(direction, Math.PI / 4), 0.96, 10, true);
            }
            if (active == EXECUTION_SHOCKWAVE_TICK) {
                areaEffect(EffectKind.SHOCKWAVE, 18);
                ApexSounds.play(apex, "shockwave");
                damageExecution(target, direction, -1, 8);
            }
        }
        return active >= EXECUTION_END_TICK;
    }

    private Vec3 executionDirection(int stage) {
        return rotateHorizontal(direction, stage == 0 ? 0 : stage == 1 ? Math.PI / 3 : -Math.PI / 3);
    }

    private static Vec3 rotateHorizontal(Vec3 vector, double angle) {
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        return new Vec3(vector.x * cosine - vector.z * sine, 0,
                vector.x * sine + vector.z * cosine).normalize();
    }

    private void damageExecution(LivingEntity target, Vec3 lane, double minimumDot, float damage) {
        damageExecution(target, lane, minimumDot, damage, false);
    }

    private void damageExecution(LivingEntity target, Vec3 lane, double minimumDot, float damage, boolean cross) {
        damageCenter = attackArea.center();
        logArea(target);
        for (LivingEntity victim : victims(attackArea.bounds(), target)) {
            Vec3 delta = victim.position().subtract(attackArea.center());
            Vec3 horizontal = delta.multiply(1, 0, 1);
            if (victim != target) logArea(victim);
            if (!attackArea.intersects(victim.getBoundingBox()) || !areaHasLineOfSight(victim)) continue;
            Vec3 offset = horizontal.normalize();
            boolean inLane = horizontal.lengthSqr() < 0.01 || offset.dot(lane) >= minimumDot;
            if (cross && horizontal.lengthSqr() >= 0.01) {
                Vec3 perpendicular = new Vec3(-lane.z, 0, lane.x);
                inLane = Math.max(Math.abs(offset.dot(lane)), Math.abs(offset.dot(perpendicular))) >= minimumDot;
            }
            if (!inLane) continue;
            float remaining = EXECUTION_MAXIMUM_DAMAGE - executionDamage.getOrDefault(victim.getId(), 0F);
            float bounded = Math.min(damage, remaining);
            // Respect shields, armor, Forge events and vanilla hurt cooldowns; never clear iFrames.
            if (bounded > 0 && victim.hurt(apex.damageSources().mobAttack(apex), bounded)) {
                executionDamage.merge(victim.getId(), bounded, Float::sum);
                hitEffect(victim, cross || minimumDot < 0 ? 3 : 1.5F);
                if (minimumDot < 0) areaKnockback(victim, 1.2);
            }
        }
    }

    /** Short-range, collision-safe warps never load chunks or place Apex in fluid/unsupported air. */
    public boolean reposition(LivingEntity target) {
        if (apex.level().isClientSide || !validVictim(target) || apex.distanceToSqr(target) > 24 * 24) return false;
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = apex.getRandom().nextDouble() * Math.PI * 2;
            double radius = 3.0 + apex.getRandom().nextDouble() * 1.4;
            double x = target.getX() + Math.cos(angle) * radius;
            double z = target.getZ() + Math.sin(angle) * radius;
            for (int offset = 2; offset >= -3; offset--) {
                double y = Math.floor(target.getY()) + offset;
                if (y < apex.level().getMinBuildHeight() + 1
                        || y + apex.getBbHeight() >= apex.level().getMaxBuildHeight()) continue;
                Vec3 destination = new Vec3(x, y, z);
                if (destination.distanceToSqr(apex.position()) > 14 * 14 || !safeDestination(destination)) continue;
                return safeTeleport(destination, true);
            }
        }
        return false;
    }

    private boolean safeTeleport(Vec3 requested, boolean animate) {
        if (requested.distanceToSqr(apex.position()) > 14 * 14 || !safeDestination(requested)) return false;
        var event = net.minecraftforge.event.ForgeEventFactory.onEnderTeleport(apex, requested.x, requested.y, requested.z);
        if (event.isCanceled()) return false;
        Vec3 destination = event.getTarget();
        if (!Double.isFinite(destination.x) || !Double.isFinite(destination.y)
                || !Double.isFinite(destination.z) || destination.distanceToSqr(apex.position()) > 14 * 14
                || !safeDestination(destination)) return false;
        Vec3 before = apex.position();
        effect(EffectKind.TELEPORT_OUT, before, direction, 2, 8);
        apex.teleportTo(destination.x, destination.y, destination.z);
        stopHorizontalMotion();
        if (animate) apex.playVisual("teleport_end", 6);
        effect(EffectKind.TELEPORT_IN, apex.position(), direction, 2, 8);
        ApexSounds.play(apex, "teleport");
        return true;
    }

    private boolean safeDestination(Vec3 destination) {
        AABB box = apex.getBoundingBox().move(destination.subtract(apex.position()));
        // Check the full footprint rather than just the central supporting block.
        for (double x : new double[]{box.minX + 0.02, box.maxX - 0.02}) {
            for (double z : new double[]{box.minZ + 0.02, box.maxZ - 0.02}) {
                BlockPos feet = BlockPos.containing(x, destination.y, z);
                if (!apex.level().hasChunkAt(feet) || !apex.level().getWorldBorder().isWithinBounds(feet)) return false;
                BlockPos floor = feet.below();
                BlockState state = apex.level().getBlockState(floor);
                if (!state.isFaceSturdy(apex.level(), floor, Direction.UP) || !state.getFluidState().isEmpty()) return false;
            }
        }
        return apex.level().noCollision(apex, box) && !apex.level().containsAnyLiquid(box);
    }

    public void dodge(LivingEntity target, int elapsed) {
        if (target == null || apex.level().isClientSide) return;
        apex.getNavigation().stop();
        if (elapsed == 0) {
            apex.beginVisualDodge(4);
            strafeSign = apex.getRandom().nextBoolean() ? 1 : -1;
            dodgeBackstep = apex.distanceToSqr(target) < 16 && apex.getRandom().nextBoolean();
            apex.playVisual(dodgeBackstep ? "backstep" : strafeSign > 0 ? "dodge_left" : "dodge_right", 9);
            effect(EffectKind.DODGE, apex.position(), direction, 1.5F, 10);
        }
        if (elapsed > 0 && elapsed % 9 == 0) {
            strafeSign = -strafeSign;
            dodgeBackstep = false;
            apex.playVisual(strafeSign > 0 ? "dodge_left" : "dodge_right", 9);
            effect(EffectKind.DODGE, apex.position(), direction, 1.5F, 9);
        }
        Vec3 away = apex.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x).scale(strafeSign);
        Vec3 velocity = dodgeBackstep ? away.scale(0.65)
                : side.scale(0.58).add(away.scale(apex.distanceToSqr(target) < 36 ? 0.2 : 0));
        move(velocity);
        // Only one warp during this evasion interval; phase one retains purely physical dodges.
        if (elapsed == 18 && apex.getPhase() >= 2) reposition(target);
    }

    private void telegraph(AttackType attack, int elapsed, int windup) {
        // Client packets own finite, quality-scaled warnings and the committed directional lanes.
        if (attack == AttackType.SPECIAL && elapsed == 24) ApexSounds.play(apex, "special_charge");
    }

    private void shockwave(LivingEntity target, float damage, double strength) {
        damageCenter = attackArea.center();
        logArea(target);
        areaEffect(EffectKind.SHOCKWAVE, 18);
        ApexSounds.play(apex, "shockwave");
        for (LivingEntity victim : victims(attackArea.bounds(), target)) {
            if (victim != target) logArea(victim);
            if (attackArea.intersects(victim.getBoundingBox()) && areaHasLineOfSight(victim)) {
                if (victim.hurt(apex.damageSources().mobAttack(apex), damage)) {
                    hitEffect(victim, 2);
                    areaKnockback(victim, strength);
                }
            }
        }
    }

    private List<LivingEntity> victims(AABB box, LivingEntity target) {
        List<LivingEntity> result = new ArrayList<>(apex.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != apex && validVictim(entity) && (entity == target || entity instanceof Player)));
        // Mock players and Forge-controlled primary targets need not be inserted in the world's query index.
        if (target != apex && validVictim(target) && box.intersects(target.getBoundingBox()) && !result.contains(target)) {
            result.add(target);
        }
        return result;
    }

    private boolean validVictim(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && target.level() == apex.level()
                && (!(target instanceof Player player) || !player.isCreative());
    }

    private boolean inReach(LivingEntity target, double radius) {
        return apex.distanceToSqr(target) <= radius * radius;
    }

    private boolean areaHasLineOfSight(LivingEntity target) {
        Vec3 source = attackArea.center().add(0, 1.2, 0);
        return apex.level().clip(new ClipContext(source, target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, apex)).getType()
                == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private void knockback(LivingEntity target, double strength) {
        target.knockback(strength, apex.getX() - target.getX(), apex.getZ() - target.getZ());
        target.hurtMarked = true;
    }

    private void areaKnockback(LivingEntity target, double strength) {
        target.knockback(strength, attackArea.center().x - target.getX(), attackArea.center().z - target.getZ());
        target.hurtMarked = true;
    }

    private void areaEffect(EffectKind kind, int duration) {
        effect(kind, attackArea.center(), direction, (float) attackArea.radius(), duration);
    }

    private void logArea(LivingEntity target) {
        if (ApexConfig.DEBUG.get()) LogUtils.getLogger().info("[APEX AREA DEBUG] Apex {}{}", apex.getId(), debugSummary(target));
    }

    private void logSlam(LivingEntity target) {
        if (ApexConfig.DEBUG.get()) LogUtils.getLogger().info("[APEX SLAM DEBUG] Apex {}{}", apex.getId(), debugSummary(target));
    }

    private static String coordinates(Vec3 position) {
        return position == null ? "pending" : String.format(Locale.ROOT, "(%.3f,%.3f,%.3f)", position.x, position.y, position.z);
    }

    private void stopHorizontalMotion() {
        Vec3 motion = apex.getDeltaMovement();
        apex.setDeltaMovement(0, motion.y, 0);
    }

    private void move(Vec3 horizontal) {
        apex.setDeltaMovement(horizontal.x, apex.getDeltaMovement().y, horizontal.z);
        apex.hurtMarked = true;
    }

    private void face(Vec3 vector) {
        apex.getLookControl().setLookAt(apex.getX() + vector.x * 10, apex.getEyeY(),
                apex.getZ() + vector.z * 10, 30, 30);
    }

    private void sound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        apex.level().playSound(null, apex.getX(), apex.getY(), apex.getZ(), sound,
                SoundSource.HOSTILE, volume, pitch);
    }

    private void hitEffect(LivingEntity victim, float strength) {
        effect(EffectKind.HIT, victim.position(), direction, strength, 10);
        ApexSounds.play(apex, "blade_hit");
    }

    private void effect(EffectKind kind, Vec3 position, Vec3 vector, float radius, int duration) {
        ApexEffects.emit(apex, kind, position, vector, radius, duration);
    }

}
