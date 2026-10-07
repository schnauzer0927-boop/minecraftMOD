package dev.apexpredator.entity;

import dev.apexpredator.effect.ApexEffects;
import dev.apexpredator.effect.EffectKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/** Slow, visible, mildly homing projectile; it never destroys blocks or ignores cover. */
public final class ApexBoltEntity extends ThrowableItemProjectile {
    private UUID targetId;

    public ApexBoltEntity(EntityType<? extends ApexBoltEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public ApexBoltEntity(EntityType<? extends ApexBoltEntity> type, Level level,
                          ApexEntity owner, LivingEntity target) {
        this(type, level);
        setOwner(owner);
        targetId = target.getUUID();
        Vec3 start = owner.getEyePosition().add(owner.getLookAngle().scale(0.8));
        setPos(start.x, start.y, start.z);
        Vec3 direction = target.getEyePosition().subtract(start).normalize();
        setDeltaMovement(direction.scale(0.7));
    }

    @Override
    protected Item getDefaultItem() { return Items.ENDER_PEARL; }

    @Override
    public void tick() {
        if (!level().isClientSide && tickCount > 80) {
            discard();
            return;
        }
        if (level() instanceof ServerLevel server) {
            Entity target = targetId == null ? null : server.getEntity(targetId);
            if (tickCount < 30 && target instanceof LivingEntity living && living.isAlive()) {
                Vec3 desired = living.getEyePosition().subtract(position()).normalize().scale(0.7);
                // Four percent steering cannot turn sharply enough to negate a sidestep.
                setDeltaMovement(getDeltaMovement().lerp(desired, 0.04).normalize().scale(0.7));
            }
            if (tickCount % 3 == 0) ApexEffects.emit(this, EffectKind.PROJECTILE_TRAIL,
                    position(), getDeltaMovement(), .4F, 10);
        }
        super.tick();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity hit = result.getEntity();
        if (!level().isClientSide && hit instanceof LivingEntity living
                && !(living instanceof ApexEntity)
                && (!(living instanceof Player player) || (!player.isCreative() && !player.isSpectator()))) {
            living.hurt(damageSources().thrown(this, getOwner()), 10);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            ApexEffects.projectileImpact(this);
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetId != null) tag.putUUID("ApexTarget", targetId);
        tag.putInt("ApexBoltAge", tickCount);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        targetId = tag.hasUUID("ApexTarget") ? tag.getUUID("ApexTarget") : null;
        tickCount = tag.getInt("ApexBoltAge");
        setNoGravity(true);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
