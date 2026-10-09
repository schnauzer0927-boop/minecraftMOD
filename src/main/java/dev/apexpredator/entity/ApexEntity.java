package dev.apexpredator.entity;

import com.mojang.logging.LogUtils;
import dev.apexpredator.ApexConfig;
import dev.apexpredator.combat.*;
import dev.apexpredator.effect.ApexParticles;
import dev.apexpredator.effect.ApexSounds;
import dev.apexpredator.effect.ApexEffects;
import dev.apexpredator.effect.EffectKind;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
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
import net.minecraftforge.event.ForgeEventFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ApexEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> VISUAL = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> VISUAL_START = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> VISUAL_UNTIL = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> VISUAL_SEQUENCE = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEALING_VISUAL = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> HEALING_VISUAL_START = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Optional<UUID>> MUSIC_TARGET = SynchedEntityData.defineId(ApexEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int renderedSequence = -1;
    private String renderedAnimation = "";
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.literal("APEX"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final ApexPhaseManager phases = new ApexPhaseManager();
    private final PlayerBehaviorTracker behavior = new PlayerBehaviorTracker();
    private final DamageTracker damage;
    private final CombatController combat;
    private final Phase3HealingController healing = new Phase3HealingController();
    private long entryHealingUntil = Long.MIN_VALUE;
    private int healingPulseCount;
    private boolean healingSuppressed;
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
        entityData.define(VISUAL, ""); entityData.define(VISUAL_START, 0L);
        entityData.define(VISUAL_UNTIL, 0L); entityData.define(VISUAL_SEQUENCE, 0);
        entityData.define(HEALING_VISUAL, 0); entityData.define(HEALING_VISUAL_START, 0L);
        entityData.define(MUSIC_TARGET, Optional.empty());
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
        // Mob targets are server-only; announce only an actual eligible player encounter.
        if (!level().isClientSide) synchronizeMusicTarget();
        // This remains server-authoritative even when vanilla AI is disabled through NBT.
        if (!level().isClientSide) tickPhase3Healing();
        // Short immunity must also expire when vanilla AI is disabled through NBT.
        if (!level().isClientSide && visualImmunityTicks > 0) {
            visualImmunityTicks--;
            if (visualImmunityTicks % 3 == 0) ApexParticles.warp(this);
        }
        if (!level().isClientSide && isAlive() && getPhase() == 3 && tickCount % 8 == 0) {
            ApexEffects.emit(this, EffectKind.CORE_AMBIENT, position().add(0, 1.7, 0), getLookAngle(), .6F, 8);
        }
    }
    private void updatePhase() {
        if (!phases.update(getHealth())) return;
        entityData.set(PHASE, phases.getPhaseIndex());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.34 * phases.getPhase().movementMultiplier());
        bossBar.setColor(getPhase() == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE);
        combat.onPhaseChange(36);
        ApexSounds.play(this, getPhase() == 3 ? "rage" : "phase_change");
        ApexEffects.emit(this, getPhase() == 3 ? EffectKind.PHASE_THREE : EffectKind.PHASE_TWO,
                position(), getLookAngle(), getPhase() == 3 ? 12 : 8, 36);
        if (getPhase() == 3 && isAlive()) {
            var settings = phase3HealingSettings();
            float restored = applyHealing(Phase3HealingController.entryHealAmount(settings, getHealth(), getMaxHealth()), false);
            if (restored > 0) {
                long now = level().getGameTime();
                entryHealingUntil = now + 20;
                setHealingVisual(1, now);
                ApexEffects.emit(this, EffectKind.HEAL_ENTRY, position().add(0, 1.7, 0), getLookAngle(), .6F, 20);
                playSound(ApexSounds.event("heal_entry"), .6F, 1F);
            }
        }
    }

    /** Live configuration also permits a test fixture to exercise settings without global mutations. */
    protected Phase3HealingController.Settings phase3HealingSettings() {
        return new Phase3HealingController.Settings(ApexConfig.PHASE3_HEALING.get(),
                ApexConfig.PHASE3_ENTRY_HEAL.get().floatValue(), ApexConfig.PHASE3_HEAL_DELAY_SECONDS.get() * 20,
                ApexConfig.PHASE3_HEAL_PER_SECOND.get().floatValue(), ApexConfig.PHASE3_HEAL_CAP.get().floatValue());
    }

    private void tickPhase3Healing() {
        long now = level().getGameTime();
        damage.tick(now);
        if (isAlive()) updatePhase();
        var settings = phase3HealingSettings();
        if (!settings.enabled() || !isAlive() || getPhase() != 3) {
            cancelHealingVisuals();
            return;
        }
        boolean blocked = isHealingBlocked();
        var result = healing.tick(now, getPhase(), isAlive(), blocked, getHealth(), getMaxHealth(), damage, settings);
        if (result.state() == Phase3HealingController.State.INACTIVE) {
            healingPulseCount = 0;
            healingSuppressed = false;
        }
        if (result.warningStarted()) {
            healingSuppressed = false;
            ApexEffects.emit(this, EffectKind.HEAL_PREPARE, position().add(0, 1.7, 0), getLookAngle(), .6F, 20);
            playSound(ApexSounds.event("heal_hum"), .25F, 1F);
        }
        if (result.healAmount() > 0) {
            float restored = applyHealing(result.healAmount(), true);
            healingSuppressed = restored <= 0;
            if (restored > 0) {
                ApexEffects.emit(this, EffectKind.HEAL_PULSE, position().add(0, 1.7, 0), getLookAngle(), .6F, 20);
                if (++healingPulseCount % 5 == 0) playSound(ApexSounds.event("heal_hum"), .25F, 1F);
            }
        }
        if (now < entryHealingUntil) return;
        int visual = healingSuppressed ? 0 : switch (healing.getState()) {
            case INACTIVE -> 0;
            case WARNING -> 2;
            case HEALING -> 3;
        };
        setHealingVisual(visual, healing.getStateStartTick());
    }

    private boolean isHealingBlocked() {
        String animation = getVisualAnimation();
        return combat.getPhasePauseRemaining() > 0 || animation.equals("phase_change")
                || getCombatState() == CombatState.SPECIAL || combat.getState() == CombatState.SPECIAL
                || animation.equals("special_prepare") || animation.equals("special_attack")
                || animation.equals("stagger") || animation.equals("death");
    }

    /** Dispatch the same event as vanilla heal, then validate before applying its mutable amount. */
    private float applyHealing(float amount, boolean passive) {
        if (amount <= 0 || !isAlive()) return 0;
        long revision = damage.getDamageRevision();
        float eventAmount = ForgeEventFactory.onLivingHeal(this, amount);
        var settings = phase3HealingSettings();
        if (!Float.isFinite(eventAmount) || eventAmount <= 0 || !isAlive() || getPhase() != 3
                || !settings.enabled() || revision != damage.getDamageRevision()
                || passive && isHealingBlocked()) return 0;
        float ceiling = passive ? Math.min(getMaxHealth(), settings.healCap()) : getMaxHealth();
        float before = getHealth();
        if (before >= ceiling) return 0;
        setHealth(before + Math.min(eventAmount, ceiling - before));
        float restored = Math.max(0, getHealth() - before);
        if (restored > 0) bossBar.setProgress(Math.max(0, getHealth()) / getMaxHealth());
        return restored;
    }

    private void setHealingVisual(int state, long startTick) {
        if (entityData.get(HEALING_VISUAL) != state) {
            entityData.set(HEALING_VISUAL, state);
            entityData.set(HEALING_VISUAL_START, state == 0 ? 0L : startTick);
        }
    }

    private void cancelHealingVisuals() {
        healing.onDamage();
        entryHealingUntil = Long.MIN_VALUE;
        healingPulseCount = 0;
        healingSuppressed = false;
        setHealingVisual(0, 0);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        // Preserve administrative /kill and out-of-world handling.
        if (isAdministrativeDamage(source)) {
            float before = getHealth();
            boolean accepted = super.hurt(source, amount);
            float actualLoss = Math.max(0, before - getHealth());
            if (accepted && Float.isFinite(actualLoss) && actualLoss > 0) {
                damage.recordDamageForRecovery(level().getGameTime(), actualLoss);
                cancelHealingVisuals();
            }
            return accepted;
        }
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
            if (actualLoss > 0) cancelHealingVisuals();
            if (source.getEntity() instanceof LivingEntity attacker) {
                if (!(attacker instanceof Player p) || !p.isCreative() && !p.isSpectator()) setTarget(attacker);
                if (attacker instanceof Player p) behavior.recordAttack(p.getUUID(), tick,
                        projectile != null || distanceToSqr(p) > 36 ? PlayerBehaviorTracker.AttackObservation.RANGED : PlayerBehaviorTracker.AttackObservation.MELEE);
            }
            if (isAlive()) {
                updatePhase();
                if (actualLoss > 0 && (getCombatState() == CombatState.IDLE || getCombatState() == CombatState.CHASE
                        || getCombatState() == CombatState.RECOVERY)) {
                    playVisual(actualLoss >= 18 ? "hurt_heavy" : "hurt_light", actualLoss >= 18 ? 11 : 6);
                }
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
    public Phase3HealingController healingController() { return healing; }
    public int getHealingVisualState() { return entityData.get(HEALING_VISUAL); }
    public long getHealingVisualStartTick() { return entityData.get(HEALING_VISUAL_START); }
    public float getBossBarProgress() { return bossBar.getProgress(); }
    public PlayerBehaviorTracker behaviorTracker() { return behavior; }
    public CombatController combatController() { return combat; }
    public int getPhase() { return entityData.get(PHASE); }
    public Optional<UUID> getMusicTargetUuid() { return entityData.get(MUSIC_TARGET); }

    public boolean isMusicTarget(Player player) {
        return player != null && getMusicTargetUuid().filter(player.getUUID()::equals).isPresent()
                && isEligibleMusicPlayer(player);
    }

    /** Nearby listeners share one track even when another player is the current target. */
    public boolean isMusicEncounterFor(Player player) {
        return player != null && ApexMusicEncounter.eligibleListener(isAlive(), isRemoved(), isNoAi(),
                getMusicTargetUuid().isPresent(), player.isAlive(), player.isRemoved(), player.isSpectator(),
                player.level() == level(), distanceToSqr(player));
    }

    private boolean isEligibleMusicPlayer(Player player) {
        return ApexMusicEncounter.eligibleTarget(isAlive(), isRemoved(), isNoAi(), player.isAlive(),
                player.isCreative(), player.isSpectator(), player.level() == level(), distanceToSqr(player));
    }

    private void synchronizeMusicTarget() {
        LivingEntity target = getTarget();
        Optional<UUID> next = target instanceof Player player && isEligibleMusicPlayer(player)
                ? Optional.of(player.getUUID()) : Optional.empty();
        entityData.set(MUSIC_TARGET, next);
    }
    public CombatState getCombatState() { return CombatState.values()[entityData.get(STATE)]; }
    public void setCombatState(CombatState state) { entityData.set(STATE, state.ordinal()); }
    /** Server-authored pose cues share the combat clock and replicate to every tracking client. */
    public void playVisual(String animation, int durationTicks) {
        if (level().isClientSide) return;
        long now = level().getGameTime();
        if (entityData.get(VISUAL).equals("phase_change") && now < entityData.get(VISUAL_UNTIL)
                && !animation.equals("death") && !animation.equals("phase_change")) return;
        if (!AnimationState.hasAnimation(animation)) throw new IllegalArgumentException("Unknown Apex animation: " + animation);
        entityData.set(VISUAL, animation);
        entityData.set(VISUAL_START, now);
        entityData.set(VISUAL_UNTIL, now + Math.max(1, Math.min(120, durationTicks)));
        entityData.set(VISUAL_SEQUENCE, entityData.get(VISUAL_SEQUENCE) + 1);
    }
    /** Movement controllers call this while maintaining a pose without restarting its clip each tick. */
    public void maintainVisual(String animation, int durationTicks) {
        if (!level().isClientSide && entityData.get(VISUAL).equals(animation) && AnimationState.isLooping(animation)) {
            entityData.set(VISUAL_UNTIL, level().getGameTime() + Math.max(1, Math.min(120, durationTicks)));
            return;
        }
        if (entityData.get(VISUAL).equals(animation) && level().getGameTime() < entityData.get(VISUAL_UNTIL)) return;
        playVisual(animation, durationTicks);
    }
    public int getVisualSequence() { return entityData.get(VISUAL_SEQUENCE); }
    public long getVisualStartTick() { return entityData.get(VISUAL_START); }
    public String getVisualAnimation() {
        if (isDeadOrDying()) return "death";
        if (level().getGameTime() < entityData.get(VISUAL_UNTIL)) return entityData.get(VISUAL);
        return switch (getCombatState()) {
            case CHASE -> getDeltaMovement().horizontalDistanceSqr() > .14 ? "sprint"
                    : getPhase() == 3 ? "rage_run" : "run";
            case STRAFE -> "strafe_left";
            case DODGE -> "dodge_left";
            case RECOVERY -> "recovery";
            default -> getDeltaMovement().horizontalDistanceSqr() > .002 ? "walk" : getPhase() == 3 ? "rage_idle" : "idle";
        };
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "combat", 2, state -> {
            String animation = getVisualAnimation();
            if (renderedSequence != getVisualSequence() || !renderedAnimation.equals(animation)) {
                state.getController().forceAnimationReset();
                renderedSequence = getVisualSequence(); renderedAnimation = animation;
            }
            RawAnimation raw = RawAnimation.begin();
            state.getController().setAnimationSpeed(animation.startsWith("melee_")
                    ? 8.0 / Math.max(4, 8 - 2 * (getPhase() - 1)) : 1.0);
            if (AnimationState.isLooping(animation)) raw.thenLoop("animation.apex." + animation);
            else raw.thenPlayAndHold("animation.apex." + animation);
            return state.setAndContinue(raw);
        }));
    }
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
    public String debugSummary() {
        long now = level().getGameTime();
        return combat.debugSummary() + " health=" + getHealth() + " healing=" + healing.getState()
                + " healingVisual=" + getHealingVisualState() + " healingTick=" + now + " lastDamageTick=" + damage.getLastDamageTick()
                + " healingReadyTick=" + damage.getRecoveryReadyTick(now, phase3HealingSettings().healDelayTicks())
                + " nextHealTick=" + healing.getNextHealTick();
    }
    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bossBar.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bossBar.removePlayer(p); }
    @Override public void die(DamageSource s) {
        if (!level().isClientSide) cancelHealingVisuals();
        playVisual("death", 40);
        super.die(s); bossBar.removeAllPlayers();
        if (!level().isClientSide) ApexEffects.emit(this, EffectKind.DEATH, position(), getLookAngle(), 3, 40);
    }
    @Override protected void tickDeath() {
        // Keep the entity until the complete two-second authored collapse has played.
        deathTime++;
        if (deathTime >= 40 && !level().isClientSide && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }
    @Override public void remove(RemovalReason r) { bossBar.removeAllPlayers(); super.remove(r); }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("ApexPhase", getPhase());
        t.putLong("ApexHealingNoDamageTicks", Math.min(DamageTracker.MAX_PERSISTED_IDLE_TICKS,
                damage.getTicksSinceLastDamage(level().getGameTime())));
        t.putBoolean("ApexHealingHasDamageHistory", damage.hasDamageHistory());
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phases.restore(Math.max(1, Math.min(3, t.getInt("ApexPhase")))); phases.update(getHealth());
        entityData.set(PHASE, phases.getPhaseIndex());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.34*phases.getPhase().movementMultiplier());
        bossBar.setColor(getPhase()==1 ? BossEvent.BossBarColor.RED : getPhase()==2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE);
        damage.restoreNoDamageTime(level().getGameTime(), t.getLong("ApexHealingNoDamageTicks"), t.getBoolean("ApexHealingHasDamageHistory"));
        cancelHealingVisuals();
        bossBar.setProgress(Math.max(0, getHealth()) / getMaxHealth());
    }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return ApexSounds.event("death"); }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
