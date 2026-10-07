package dev.apexpredator.combat;

import dev.apexpredator.entity.ApexBoltEntity;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Executes one committed attack. Decisions and cooldowns belong to CombatController. */
public final class AttackManager {
    private final ApexEntity apex;
    private final Set<Integer> struck = new HashSet<>();
    private Vec3 targetSnapshot = Vec3.ZERO;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 direction = Vec3.ZERO;
    private int strafeSign;
    private boolean launched;

    public AttackManager(ApexEntity apex) {
        this.apex = apex;
    }

    public void begin(AttackType attack, LivingEntity target) {
        struck.clear();
        launched = false;
        apex.getNavigation().stop();
        stopHorizontalMotion();
        origin = apex.position();
        targetSnapshot = target.position();
        direction = targetSnapshot.subtract(origin).multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < 0.001) direction = apex.getLookAngle().multiply(1, 0, 1).normalize();
        strafeSign = apex.getRandom().nextBoolean() ? 1 : -1;
        sound(attack == AttackType.SPECIAL ? SoundEvents.WITHER_SPAWN : SoundEvents.IRON_GOLEM_REPAIR,
                attack == AttackType.SPECIAL ? 1.4F : 0.7F, 0.6F);
    }

    /** Includes the windup. Returns true only when this attack's active interval ends. */
    public boolean tick(AttackType attack, LivingEntity target, int elapsed) {
        if (apex.level().isClientSide || !validVictim(target)) return true;
        apex.getNavigation().stop();
        int windup = attack == AttackType.MELEE
                ? Math.max(4, attack.telegraphTicks() - 2 * (apex.getPhase() - 1))
                : attack.telegraphTicks();
        if (attack != AttackType.SPECIAL) apex.getLookControl().setLookAt(target, 30, 30);
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
        apex.getNavigation().stop();
        stopHorizontalMotion();
        struck.clear();
        launched = false;
    }

    private boolean melee(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup) {
            apex.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (inReach(target, 3.4) && apex.hasLineOfSight(target)) {
                apex.doHurtTarget(target);
                // A readable counter pushes sustained close-range attackers out of combo range.
                if (apex.getPhase() >= 2) knockback(target, 0.7);
            }
            sound(SoundEvents.IRON_GOLEM_ATTACK, 1, 0.6F);
        }
        return elapsed >= windup + 2;
    }

    private boolean charge(LivingEntity target, int elapsed, int windup) {
        int active = elapsed - windup;
        int duration = apex.getPhase() == 3 ? 11 : 12;
        if (active >= duration || (active > 1 && apex.horizontalCollision)) {
            stopHorizontalMotion();
            return true;
        }
        if (active == 0) sound(SoundEvents.ENDER_DRAGON_FLAP, 1, 0.55F);
        Vec3 velocity = direction.scale(apex.getPhase() == 3 ? 1.9 : 1.35);
        move(velocity);
        trail();
        AABB path = apex.getBoundingBox().expandTowards(velocity).inflate(0.35, 0.2, 0.35);
        for (LivingEntity victim : victims(path, target)) {
            if (struck.add(victim.getId()) && apex.hasLineOfSight(victim)) {
                victim.hurt(apex.damageSources().mobAttack(apex), apex.getPhase() == 3 ? 22 : 18);
                knockback(victim, 1.2);
            }
        }
        return false;
    }

    private boolean jump(LivingEntity target, int elapsed, int windup) {
        int active = elapsed - windup;
        // The deliberate landing attack should not trigger health-loss evasion against itself.
        apex.fallDistance = 0;
        if (!launched) {
            launched = true;
            Vec3 delta = targetSnapshot.subtract(apex.position());
            Vec3 horizontal = delta.multiply(1, 0, 1);
            double speed = Math.min(0.9, horizontal.length() / 13.0);
            Vec3 velocity = horizontal.normalize().scale(speed)
                    .add(0, Math.min(1.25, 0.85 + Math.max(0, delta.y) * 0.07), 0);
            apex.setDeltaMovement(velocity);
            apex.hurtMarked = true;
            sound(SoundEvents.ENDER_DRAGON_FLAP, 1, 0.7F);
        }
        trail();
        if (active >= 5 && apex.onGround()) {
            shockwave(target, 5, 16, 1.2);
            stopHorizontalMotion();
            return true;
        }
        return active > 45;
    }

    private boolean teleportAttack(LivingEntity target, int elapsed, int windup) {
        int active = elapsed - windup;
        int count = apex.getPhase() == 3 ? 3 : 1;
        if (active >= count * 14) return true;
        if (active % 14 == 0) {
            struck.clear();
            reposition(target);
        }
        if (active % 14 == 6) {
            apex.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (inReach(target, 3.6) && apex.hasLineOfSight(target)) {
                apex.doHurtTarget(target);
                knockback(target, 0.45);
            }
            sound(SoundEvents.IRON_GOLEM_ATTACK, 0.9F, 1.1F);
        }
        if (active % 14 < 6) ring(apex.position(), 2.2, ParticleTypes.PORTAL, 16);
        return false;
    }

    private boolean shockwaveAttack(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup) shockwave(target, apex.getPhase() == 3 ? 10 : 6,
                apex.getPhase() == 3 ? 18 : 14, 1.3);
        return elapsed >= windup + 4;
    }

    private boolean ranged(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup && apex.level() instanceof ServerLevel server) {
            ApexBoltEntity bolt = new ApexBoltEntity(ApexRegistries.APEX_BOLT.get(), server, apex, target);
            server.addFreshEntity(bolt);
            sound(SoundEvents.BLAZE_SHOOT, 1, 0.7F);
        }
        return elapsed >= windup + 2;
    }

    private boolean pull(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup) {
            for (LivingEntity victim : victims(apex.getBoundingBox().inflate(10, 5, 10), target)) {
                Vec3 delta = apex.position().subtract(victim.position()).multiply(1, 0, 1);
                double distance = delta.length();
                // Stop at a four-block gap and leave the full recovery window to escape.
                if (distance > 4 && distance <= 10 && apex.hasLineOfSight(victim)) {
                    victim.setDeltaMovement(victim.getDeltaMovement().add(
                            delta.normalize().scale(Math.min(0.75, (distance - 4) * 0.12))).add(0, 0.12, 0));
                    victim.hurtMarked = true;
                }
            }
            sound(SoundEvents.ENDERMAN_SCREAM, 1, 0.65F);
            ring(apex.position(), 10, ParticleTypes.REVERSE_PORTAL, 64);
        }
        return elapsed >= windup + 3;
    }

    private boolean mirage(LivingEntity target, int elapsed, int windup) {
        if (apex.level() instanceof ServerLevel server && elapsed % 2 == 0) {
            for (int copy = 0; copy < 3; copy++) {
                double angle = copy * Math.PI * 2 / 3 + strafeSign * 0.4;
                Vec3 feet = origin.add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
                // Visible humanoid silhouettes: head, torso, shoulders and two separated legs.
                for (int row = 0; row < 7; row++) {
                    double y = row * 0.5;
                    double width = row < 3 ? 0.32 : row < 6 ? 0.62 : 0.25;
                    server.sendParticles(ParticleTypes.SOUL, feet.x - width, feet.y + y, feet.z,
                            1, 0, 0, 0, 0);
                    server.sendParticles(ParticleTypes.SOUL, feet.x + width, feet.y + y, feet.z,
                            1, 0, 0, 0, 0);
                }
            }
        }
        if (elapsed == windup + 16) reposition(target);
        return elapsed >= windup + 18;
    }

    private boolean special(LivingEntity target, int elapsed, int windup) {
        if (elapsed == windup) {
            sound(SoundEvents.WARDEN_SONIC_BOOM, 1.6F, 0.6F);
            for (LivingEntity victim : victims(new AABB(origin, origin).inflate(18, 5, 18), target)) {
                Vec3 delta = victim.position().subtract(origin);
                Vec3 horizontal = delta.multiply(1, 0, 1);
                double distance = horizontal.length();
                // A committed narrow cone, blocked by cover; no damage outside the telegraphed lane.
                if (distance <= 18 && Math.abs(delta.y) <= 4
                        && (distance < 0.01 || horizontal.normalize().dot(direction) >= 0.94)
                        && specialHasLineOfSight(victim)) {
                    victim.hurt(apex.damageSources().mobAttack(apex), 42);
                    knockback(victim, 1.5);
                }
            }
            if (apex.level() instanceof ServerLevel server) {
                for (int step = 1; step <= 35; step++) {
                    Vec3 point = origin.add(direction.scale(step * 0.5)).add(0, 1.2, 0);
                    server.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z,
                            1, 0, 0, 0, 0);
                }
            }
        }
        return elapsed >= windup + 8;
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
                var event = net.minecraftforge.event.ForgeEventFactory.onEnderTeleport(apex, x, y, z);
                if (event.isCanceled()) return false;
                destination = event.getTarget();
                if (!Double.isFinite(destination.x) || !Double.isFinite(destination.y)
                        || !Double.isFinite(destination.z) || destination.distanceToSqr(apex.position()) > 14 * 14
                        || !safeDestination(destination)) return false;
                Vec3 before = apex.position();
                burst(before, ParticleTypes.PORTAL, 36);
                apex.teleportTo(destination.x, destination.y, destination.z);
                stopHorizontalMotion();
                burst(apex.position(), ParticleTypes.PORTAL, 36);
                sound(SoundEvents.ENDERMAN_TELEPORT, 1, 0.8F);
                return true;
            }
        }
        return false;
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
            burst(apex.position(), ParticleTypes.SMOKE, 24);
        }
        if (elapsed > 0 && elapsed % 9 == 0) strafeSign = -strafeSign;
        Vec3 away = apex.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x).scale(strafeSign);
        Vec3 velocity = side.scale(0.58).add(away.scale(apex.distanceToSqr(target) < 36 ? 0.2 : 0));
        move(velocity);
        if (elapsed % 2 == 0) trail();
        // Only one warp during this evasion interval; phase one retains purely physical dodges.
        if (elapsed == 18 && apex.getPhase() >= 2) reposition(target);
    }

    private void telegraph(AttackType attack, int elapsed, int windup) {
        if (elapsed % 3 != 0) return;
        switch (attack) {
            case SHOCKWAVE -> ring(apex.position(), apex.getPhase() == 3 ? 10 : 6, ParticleTypes.FLAME, 48);
            case JUMP -> ring(targetSnapshot, 5, ParticleTypes.FLAME, 36);
            case PULL -> ring(apex.position(), 10, ParticleTypes.REVERSE_PORTAL, 48);
            case TELEPORT, MIRAGE -> ring(apex.position(), 3, ParticleTypes.PORTAL, 24);
            case SPECIAL -> {
                ring(origin, 18, ParticleTypes.SOUL_FIRE_FLAME, 64);
                if (apex.level() instanceof ServerLevel server) {
                    Vec3 side = new Vec3(-direction.z, 0, direction.x);
                    for (int step = 1; step <= 18; step++) {
                        Vec3 middle = origin.add(direction.scale(step));
                        for (int sign : new int[]{-1, 1}) {
                            Vec3 edge = middle.add(side.scale(sign * step * 0.36));
                            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, edge.x, edge.y + 0.2,
                                    edge.z, 1, 0, 0, 0, 0);
                        }
                    }
                }
                if (elapsed == 24 || elapsed == 42) sound(SoundEvents.WARDEN_HEARTBEAT, 1.4F, 0.55F);
            }
            case CHARGE, FEINT -> {
                if (apex.level() instanceof ServerLevel server) {
                    for (int step = 1; step <= 12; step++) {
                        Vec3 point = apex.position().add(direction.scale(step));
                        server.sendParticles(ParticleTypes.CRIT, point.x, point.y + 0.2, point.z,
                                1, 0, 0, 0, 0);
                    }
                }
            }
            default -> burst(apex.position(), ParticleTypes.CRIT, 3);
        }
    }

    private void shockwave(LivingEntity target, double radius, float damage, double strength) {
        ring(apex.position(), radius, ParticleTypes.EXPLOSION, 32);
        sound(SoundEvents.GENERIC_EXPLODE, 1.1F, 0.65F);
        for (LivingEntity victim : victims(apex.getBoundingBox().inflate(radius, 3, radius), target)) {
            Vec3 delta = victim.position().subtract(apex.position());
            if (delta.horizontalDistanceSqr() <= radius * radius && Math.abs(delta.y) < 3.5
                    && apex.hasLineOfSight(victim)) {
                victim.hurt(apex.damageSources().mobAttack(apex), damage);
                knockback(victim, strength);
            }
        }
    }

    private List<LivingEntity> victims(AABB box, LivingEntity target) {
        return apex.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != apex && validVictim(entity) && (entity == target || entity instanceof Player));
    }

    private boolean validVictim(LivingEntity target) {
        return target != null && target.isAlive() && !target.isSpectator()
                && (!(target instanceof Player player) || !player.isCreative());
    }

    private boolean inReach(LivingEntity target, double radius) {
        return apex.distanceToSqr(target) <= radius * radius;
    }

    private boolean specialHasLineOfSight(LivingEntity target) {
        Vec3 source = origin.add(0, 1.2, 0);
        return apex.level().clip(new ClipContext(source, target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, apex)).getType()
                == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private void knockback(LivingEntity target, double strength) {
        target.knockback(strength, apex.getX() - target.getX(), apex.getZ() - target.getZ());
        target.hurtMarked = true;
    }

    private void stopHorizontalMotion() {
        Vec3 motion = apex.getDeltaMovement();
        apex.setDeltaMovement(0, motion.y, 0);
    }

    private void move(Vec3 horizontal) {
        apex.setDeltaMovement(horizontal.x, apex.getDeltaMovement().y, horizontal.z);
        apex.hurtMarked = true;
    }

    private void sound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        apex.level().playSound(null, apex.getX(), apex.getY(), apex.getZ(), sound,
                SoundSource.HOSTILE, volume, pitch);
    }

    private void trail() {
        burst(apex.position(), ParticleTypes.SMOKE, 3);
    }

    private void burst(Vec3 position, net.minecraft.core.particles.SimpleParticleType particle, int count) {
        if (apex.level() instanceof ServerLevel server) server.sendParticles(particle,
                position.x, position.y + 1.5, position.z, count, 0.7, 1.2, 0.7, 0.02);
    }

    private void ring(Vec3 position, double radius, net.minecraft.core.particles.SimpleParticleType particle, int count) {
        if (!(apex.level() instanceof ServerLevel server)) return;
        for (int index = 0; index < count; index++) {
            double angle = index * Math.PI * 2 / count;
            server.sendParticles(particle, position.x + Math.cos(angle) * radius,
                    position.y + 0.15, position.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }
}
