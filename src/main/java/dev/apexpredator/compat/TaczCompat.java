package dev.apexpredator.compat;

import com.mojang.logging.LogUtils;
import dev.apexpredator.entity.ApexEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Optional TaCZ bridge. No TaCZ class is referenced by this class's bytecode,
 * so the mod can load on a dedicated server without TaCZ or its dependencies.
 *
 * Call {@link #init()} from FMLCommonSetupEvent.enqueueWork. API signatures are
 * resolved once; an unsupported TaCZ version disables only this bridge.
 */
public final class TaczCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double HEARING_RANGE = 64.0;
    private static boolean initialized;
    private static volatile boolean active;

    private TaczCompat() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!ModList.get().isLoaded("tacz")) {
            LOGGER.info("Apex Predator: TaCZ is absent; optional gun event bridge is disabled.");
            return;
        }

        try {
            Class<Event> fireType = eventClass("com.tacz.guns.api.event.common.GunFireEvent");
            Method fireShooter = fireType.getMethod("getShooter");
            Method fireSide = fireType.getMethod("getLogicalSide");

            Class<Event> hitType = eventClass("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post");
            Method hitTarget = hitType.getMethod("getHurtEntity");
            Method hitAttacker = hitType.getMethod("getAttacker");
            Method hitBullet = hitType.getMethod("getBullet");
            Method hitSide = hitType.getMethod("getLogicalSide");

            // An explicit event Class is required here: lambda generic type
            // inference alone would register Event rather than the TaCZ type.
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, fireType,
                    event -> onFire(event, fireShooter, fireSide));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, hitType,
                    event -> onHit(event, hitTarget, hitAttacker, hitBullet, hitSide));
            active = true;
            LOGGER.info("Apex Predator: optional TaCZ gunfire and gun-hit listeners registered.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            disable(exception);
        }
    }

    public static boolean isActive() {
        return active;
    }

    @SuppressWarnings("unchecked")
    private static Class<Event> eventClass(String name) throws ClassNotFoundException {
        return (Class<Event>) Class.forName(name, false, TaczCompat.class.getClassLoader()).asSubclass(Event.class);
    }

    private static void onFire(Event event, Method shooterGetter, Method sideGetter) {
        if (!active) {
            return;
        }
        try {
            if (sideGetter.invoke(event) != LogicalSide.SERVER
                    || !(shooterGetter.invoke(event) instanceof LivingEntity shooter)
                    || shooter.level().isClientSide || !shooter.isAlive()) {
                return;
            }
            for (ApexEntity apex : shooter.level().getEntitiesOfClass(ApexEntity.class,
                    shooter.getBoundingBox().inflate(HEARING_RANGE), ApexEntity::isAlive)) {
                if (apex.distanceToSqr(shooter) <= HEARING_RANGE * HEARING_RANGE) {
                    apex.onGunfire(shooter);
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            disable(exception);
        }
    }

    private static void onHit(Event event, Method targetGetter, Method attackerGetter,
                              Method bulletGetter, Method sideGetter) {
        if (!active) {
            return;
        }
        try {
            if (sideGetter.invoke(event) != LogicalSide.SERVER
                    || !(targetGetter.invoke(event) instanceof ApexEntity apex)
                    || apex.level().isClientSide || !apex.isAlive()) {
                return;
            }
            Object attacker = attackerGetter.invoke(event);
            LivingEntity shooter = attacker instanceof LivingEntity living ? living : null;
            Object bullet = bulletGetter.invoke(event);
            Vec3 source = shooter != null ? shooter.getEyePosition()
                    : bullet instanceof Entity entity ? entity.position() : apex.position();
            // This event describes behavior only. All damage accounting stays
            // in ApexEntity.hurt; TaCZ Post must never count the same hit twice.
            apex.onGunHit(shooter, source);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            disable(exception);
        }
    }

    private static void disable(Throwable exception) {
        active = false;
        LOGGER.warn("Apex Predator: TaCZ event bridge disabled because its API is unavailable or incompatible. "
                + "Normal damage protection and combat AI remain available.", exception);
    }
}
