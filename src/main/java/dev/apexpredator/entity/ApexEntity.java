package dev.apexpredator.entity;

import com.mojang.logging.LogUtils;
import dev.apexpredator.ApexConfig;
import dev.apexpredator.combat.*;
import dev.apexpredator.effect.ApexParticles;
import dev.apexpredator.effect.ApexSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ApexEntity extends Monster {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.INT);
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.literal("APEX"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final ApexPhaseManager phases = new ApexPhaseManager();
    private final PlayerBehaviorTracker behavior = new PlayerBehaviorTracker();
    private final DamageTracker damage;
    private final CombatController combat;
    private int visualImmunityTicks;
    private long projectileBudgetTick = Long.MIN_VALUE;
    private final Map<UUID, Float> projectileBudgets = new HashMap<>();
    private Vec3 investigationPoint;
    private long investigateUntil;

    public ApexEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        damage = new DamageTracker(new DamageTracker.Settings(ApexConfig.MAXIMUM_HIT_DAMAGE.get().floatValue(),
                ApexConfig.BURST_THRESHOLD.get().floatValue(), ApexConfig.REPOSITION_THRESHOLD.get().floatValue(), ApexConfig.EVASION_TICKS.get(), 20));
        combat = new CombatController(this);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 600).add(Attributes.ATTACK_DAMAGE, 14)
                .add(Attributes.MOVEMENT_SPEED, .34).add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.KNOCKBACK_RESISTANCE, .8).add(Attributes.ARMOR, 18);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, 1); entityData.define(STATE, CombatState.IDLE.ordinal());
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 64));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        long tick = level().getGameTime();
        damage.tick(tick); behavior.prune(tick); updatePhase();
        combat.tick();
        if (getTarget() == null && investigationPoint != null && tick < investigateUntil) {
            getNavigation().moveTo(investigationPoint.x, investigationPoint.y, investigationPoint.z, 1.2);
            if (position().distanceToSqr(investigationPoint) < 4) investigationPoint = null;
        }
        bossBar.setProgress(Math.max(0, getHealth()) / getMaxHealth());
        if (ApexConfig.DEBUG.get() && tick % 20 == 0) LogUtils.getLogger().info("Apex {}: {}", getId(), debugSummary());
    }
    @Override public void tick() {
        super.tick();
        // Short immunity must also expire when vanilla AI is disabled through NBT.
        if (!level().isClientSide && visualImmunityTicks > 0) {
            visualImmunityTicks--;
            ApexParticles.warp(this);
        }
    }
    private void updatePhase() {
        if (!phases.update(getHealth())) return;
        entityData.set(PHASE, phases.getPhaseIndex());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.34 * phases.getPhase().movementMultiplier());
        bossBar.setColor(getPhase() == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE);
        ApexSounds.phase(this); ApexParticles.burst(this);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        // Preserve administrative /kill and out-of-world handling.
        if (isAdministrativeDamage(source)) return super.hurt(source, amount);
        if (visualImmunityTicks > 0 || isDeadOrDying()) return false;
        float capped = damage.capIncomingDamage(amount);
        if (damage.isEvading(level().getGameTime())) capped = Math.min(capped, ApexConfig.EVASION_HIT_DAMAGE.get().floatValue());
        UUID projectile = projectileBudgetKey(source);
        long tick = level().getGameTime();
        if (tick != projectileBudgetTick) { projectileBudgets.clear(); projectileBudgetTick = tick; }
        if (projectile != null) capped = Math.min(capped, Math.max(0, damage.getSettings().maximumHitDamage() - projectileBudgets.getOrDefault(projectile, 0F)));
        if (capped <= 0) return false;
        float before = getHealth();
        boolean accepted = super.hurt(source, capped);
        if (accepted) {
            float actualLoss = Math.max(0, before - getHealth());
            if (projectile != null) projectileBudgets.merge(projectile, Math.max(capped, actualLoss), Float::sum);
            var reaction = damage.recordDamage(tick, actualLoss);
            if (source.getEntity() instanceof LivingEntity attacker) {
                if (!(attacker instanceof Player p) || !p.isCreative() && !p.isSpectator()) setTarget(attacker);
                if (attacker instanceof Player p) behavior.recordAttack(p.getUUID(), tick,
                        projectile != null || distanceToSqr(p) > 36 ? PlayerBehaviorTracker.AttackObservation.RANGED : PlayerBehaviorTracker.AttackObservation.MELEE);
            }
            if (isAlive()) {
                updatePhase();
                if (damage.isEvading(tick) && actualLoss > 0) beginVisualDodge(6);
                combat.onDamage(reaction);
            }
        }
        return accepted;
    }
    public void beginVisualDodge(int ticks) {
        visualImmunityTicks = Math.max(visualImmunityTicks, Math.min(6, Math.max(0, ticks)));
        ApexSounds.warp(this); ApexParticles.warp(this);
    }
    public static boolean isAdministrativeDamage(DamageSource source) {
        return source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD);
    }
    private static UUID projectileBudgetKey(DamageSource source) {
        if (source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity()) return source.getDirectEntity().getUUID();
        // TaCZ datapacks may deliberately present gun damage as melee: bound split hits there too.
        boolean gun = source.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals("tacz")
                && k.location().getPath().startsWith("bullet")).orElse(false);
        return gun && source.getEntity() != null ? source.getEntity().getUUID() : null;
    }
    /** Final event cap prevents other damage multipliers from undoing the incoming hit budget. */
    public float maximumFinalDamage(DamageSource source) {
        float maximum = damage.getSettings().maximumHitDamage();
        if (damage.isEvading(level().getGameTime())) maximum = Math.min(maximum, ApexConfig.EVASION_HIT_DAMAGE.get().floatValue());
        UUID projectile = projectileBudgetKey(source);
        if (projectileBudgetTick == level().getGameTime() && projectile != null) {
            maximum -= projectileBudgets.getOrDefault(projectile, 0F);
        }
        return Math.max(0, maximum);
    }
    public void onGunfire(LivingEntity shooter) {
        if (level().isClientSide || shooter.level() != level() || distanceToSqr(shooter) > 4096) return;
        if (shooter instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return;
            behavior.recordAttack(p.getUUID(), level().getGameTime(), PlayerBehaviorTracker.AttackObservation.GUN);
        }
        investigationPoint = shooter.position(); investigateUntil = level().getGameTime() + 100;
        if (getSensing().hasLineOfSight(shooter)) setTarget(shooter);
    }
    public void onGunHit(LivingEntity shooter, Vec3 source) {
        if (level().isClientSide) return;
        investigationPoint = source; investigateUntil = level().getGameTime() + 100;
        if (shooter != null) onGunfire(shooter);
    }
    public DamageTracker damageTracker() { return damage; }
    public PlayerBehaviorTracker behaviorTracker() { return behavior; }
    public CombatController combatController() { return combat; }
    public int getPhase() { return entityData.get(PHASE); }
    public CombatState getCombatState() { return CombatState.values()[entityData.get(STATE)]; }
    public void setCombatState(CombatState state) { entityData.set(STATE, state.ordinal()); }
    public AnimationState getAnimationState() {
        if (isDeadOrDying()) return AnimationState.DEATH;
        if (hurtTime > 0 && getCombatState() != CombatState.DODGE) return AnimationState.HURT;
        return switch (getCombatState()) {
            case MELEE, SHOCKWAVE -> AnimationState.ATTACK;
            case CHARGE, FEINT -> AnimationState.CHARGE;
            case JUMP -> AnimationState.JUMP;
            case DODGE, STRAFE -> AnimationState.DODGE;
            case TELEPORT -> AnimationState.TELEPORT;
            case SPECIAL, PULL, MIRAGE, RANGED -> AnimationState.SPECIAL;
            case CHASE -> AnimationState.RUN;
            default -> getDeltaMovement().horizontalDistanceSqr() > .002 ? AnimationState.WALK : AnimationState.IDLE;
        };
    }
    public String debugSummary() { return combat.debugSummary(); }
    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bossBar.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bossBar.removePlayer(p); }
    @Override public void die(DamageSource s) { super.die(s); bossBar.removeAllPlayers(); }
    @Override public void remove(RemovalReason r) { bossBar.removeAllPlayers(); super.remove(r); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("ApexPhase", getPhase()); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phases.restore(Math.max(1, Math.min(3, t.getInt("ApexPhase")))); phases.update(getHealth());
        entityData.set(PHASE, phases.getPhaseIndex());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.34*phases.getPhase().movementMultiplier());
        bossBar.setColor(getPhase()==1 ? BossEvent.BossBarColor.RED : getPhase()==2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE);
    }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WITHER_DEATH; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
