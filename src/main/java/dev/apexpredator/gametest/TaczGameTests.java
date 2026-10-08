package dev.apexpredator.gametest;

import dev.apexpredator.combat.PlayerBehaviorTracker;
import dev.apexpredator.compat.TaczCompat;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;

/** Optional integration tests: absent TaCZ produces no tests, rather than false successes. */
@GameTestHolder("apexpredator")
@PrefixGameTestTemplate(false)
public final class TaczGameTests {
    private static final ResourceLocation GUN_ID = new ResourceLocation("tacz", "ak47");

    private TaczGameTests() { }

    @GameTestGenerator
    public static Collection<TestFunction> optionalGunEventTests() {
        if (!ModList.get().isLoaded("tacz")) return List.of();
        return List.of(
                test("taczgametests.servergunfireeventreachesapex", TaczGameTests::serverGunfireEventReachesApex),
                test("taczgametests.posthiteventdoesnotcountdamagetwice", TaczGameTests::postHitEventDoesNotCountDamageTwice),
                new TestFunction("apexpredator_tacz", "taczgametests.actualgunhitinterruptshealingbutobservationsdonot",
                        "apexpredator:empty", 240, 0, true, TaczGameTests::actualGunHitInterruptsHealingButObservationsDoNot));
    }

    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> body) {
        return new TestFunction("apexpredator_tacz", name, "apexpredator:empty", 40, 0, true, body);
    }

    private static void serverGunfireEventReachesApex(GameTestHelper helper) {
        helper.assertTrue(TaczCompat.isActive(), "Installed TaCZ must have registered its optional event bridge");
        ApexEntity apex = boss(helper);
        Player shooter = shooter(helper);
        Item gunItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "modern_kinetic_gun"));
        helper.assertTrue(gunItem != null && gunItem != Items.AIR, "TaCZ's real gun item must be registered");
        ItemStack gun = new ItemStack(gunItem);
        gun.getOrCreateTag().putString("GunId", GUN_ID.toString());
        try {
            // Verified against TaCZ 1.1.8-hotfix2: the constructor has three arguments.
            Constructor<?> constructor = Class.forName("com.tacz.guns.api.event.common.GunFireEvent")
                    .getConstructor(LivingEntity.class, ItemStack.class, LogicalSide.class);
            MinecraftForge.EVENT_BUS.post((Event) constructor.newInstance(shooter, gun, LogicalSide.CLIENT));
            helper.assertTrue(snapshot(apex, shooter, helper).gunAttacks() == 0,
                    "Client-side gun events must never alter authoritative combat behavior");

            MinecraftForge.EVENT_BUS.post((Event) constructor.newInstance(shooter, gun, LogicalSide.SERVER));
            helper.assertTrue(TaczCompat.isActive(), "A real gunfire callback must not disable the bridge");
            helper.assertTrue(snapshot(apex, shooter, helper).gunAttacks() == 1,
                    "Forge dispatch of TaCZ's server gunfire event must reach the nearby Apex");
            helper.assertTrue(apex.getTarget() == shooter, "Visible gunfire must identify the shooter as a target");
            near(helper, apex.getHealth(), 600, "Observing a gunfire event must not deal damage");
            near(helper, apex.damageTracker().getDamagePerSecond(helper.getLevel().getGameTime()), 0,
                    "Gunfire sound observations must not be treated as damage");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Installed TaCZ gunfire event API does not match the optional bridge", exception);
        }
        helper.succeed();
    }

    private static void postHitEventDoesNotCountDamageTwice(GameTestHelper helper) {
        helper.assertTrue(TaczCompat.isActive(), "Installed TaCZ must have an active event bridge");
        ApexEntity apex = boss(helper);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        Player shooter = shooter(helper);
        EntityType<?> bulletType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("tacz", "bullet"));
        helper.assertTrue(bulletType != null, "TaCZ's real bullet entity must be registered");
        Entity bullet = bulletType.create(helper.getLevel());
        helper.assertTrue(bullet instanceof Projectile, "TaCZ's bullet factory must create a projectile");
        ((Projectile) bullet).setOwner(shooter);
        bullet.setPos(shooter.getEyePosition());
        // No world insertion: event smoke tests must not introduce another physical collision.
        var damageType = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("tacz", "bullet")));
        DamageSource source = new DamageSource(damageType, bullet, shooter);
        helper.assertTrue(apex.hurt(source, 20), "Actual TaCZ bullet damage type must work with Apex's damage hook");
        float healthAfterHit = apex.getHealth();
        float dpsAfterHit = apex.damageTracker().getDamagePerSecond(helper.getLevel().getGameTime());
        near(helper, dpsAfterHit, 20, "The original hit must record its real health loss exactly once");
        helper.assertTrue(snapshot(apex, shooter, helper).gunAttacks() == 0,
                "A vanilla damage observation must not pretend that the TaCZ Post event was delivered");

        try {
            Constructor<?> constructor = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post")
                    .getConstructor(Entity.class, Entity.class, LivingEntity.class,
                            ResourceLocation.class, ResourceLocation.class, float.class, Pair.class,
                            boolean.class, float.class, LogicalSide.class);
            MinecraftForge.EVENT_BUS.post((Event) constructor.newInstance(bullet, apex, shooter,
                    GUN_ID, GUN_ID, 20.0F, Pair.of(source, source), false, 1.0F, LogicalSide.SERVER));
            helper.assertTrue(TaczCompat.isActive(), "A real post-hit callback must not disable the bridge");
            PlayerBehaviorTracker.BehaviorSnapshot behavior = snapshot(apex, shooter, helper);
            helper.assertTrue(behavior.gunAttacks() == 1 && behavior.totalAttacks() == 1,
                    "TaCZ Post must classify the shot as gunfire without counting another attack");
            near(helper, apex.getHealth(), healthAfterHit, "TaCZ Post must not apply the same damage twice");
            near(helper, apex.damageTracker().getDamagePerSecond(helper.getLevel().getGameTime()), dpsAfterHit,
                    "TaCZ Post must not count the same health loss twice in DPS");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Installed TaCZ post-hit API does not match the optional bridge", exception);
        }
        helper.succeed();
    }

    private static void actualGunHitInterruptsHealingButObservationsDoNot(GameTestHelper helper) {
        helper.assertTrue(TaczCompat.isActive(), "Installed TaCZ must retain the optional event bridge");
        ApexEntity apex = boss(helper);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        apex.setHealth(201);
        helper.assertTrue(apex.hurt(apex.damageSources().generic(), 1), "Final-phase trigger must cause actual health loss");
        near(helper, apex.getHealth(), 260, "TaCZ fixture must retain final-phase entry healing");
        Player shooter = shooter(helper);
        EntityType<?> bulletType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("tacz", "bullet"));
        helper.assertTrue(bulletType != null, "TaCZ's real bullet entity must be registered");
        Entity bullet = bulletType.create(helper.getLevel());
        helper.assertTrue(bullet instanceof Projectile, "TaCZ's bullet factory must create a projectile");
        ((Projectile) bullet).setOwner(shooter);
        bullet.setPos(shooter.getEyePosition());
        var damageType = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("tacz", "bullet")));
        DamageSource source = new DamageSource(damageType, bullet, shooter);
        long originalDamageTick = apex.damageTracker().getLastDamageTick();
        long[] gunDamageTick = {-1};
        helper.runAfterDelay(81, () -> {
            helper.assertTrue(apex.getHealingVisualState() == 2, "Gunfire observation fixture must be in its heal warning");
            postGunfire(shooter);
            helper.assertTrue(apex.damageTracker().getLastDamageTick() == originalDamageTick,
                    "Hearing TaCZ gunfire alone must not restart the actual damage clock");
            helper.assertTrue(apex.getHealingVisualState() == 2, "Hearing gunfire must not stop healing without health loss");
        });
        helper.runAfterDelay(83, () -> {
            postGunHit(bullet, apex, shooter, source, 20);
            helper.assertTrue(apex.damageTracker().getLastDamageTick() == originalDamageTick,
                    "TaCZ Post observation alone must not invent health damage");
            helper.assertTrue(apex.getHealingVisualState() == 2, "TaCZ Post alone must not interrupt the warning");
        });
        helper.runAfterDelay(101, () -> {
            near(helper, apex.getHealth(), 264, "Observations alone must allow the normal five-second healing pulse");
            helper.assertTrue(apex.getHealingVisualState() == 3, "The real bullet must interrupt active healing");
            apex.invulnerableTime = 0;
            helper.assertTrue(apex.hurt(source, 20), "TaCZ bullet's real damage hook must land");
            gunDamageTick[0] = helper.getLevel().getGameTime();
            near(helper, apex.getHealth(), 244, "A TaCZ hit must remove its actual 20 HP");
            helper.assertTrue(apex.getHealingVisualState() == 0, "TaCZ damage must stop healing in the same hurt hook");
            helper.assertTrue(apex.damageTracker().getLastDamageTick() == gunDamageTick[0],
                    "TaCZ hit must update the same DamageTracker damage clock");
        });
        helper.runAfterDelay(106, () -> {
            postGunHit(bullet, apex, shooter, source, 20);
            near(helper, apex.getHealth(), 244, "TaCZ Post must not deal or restore HP a second time");
            helper.assertTrue(apex.damageTracker().getLastDamageTick() == gunDamageTick[0],
                    "A delayed TaCZ Post callback must not restart the five-second delay");
            near(helper, apex.damageTracker().getDamagePerSecond(helper.getLevel().getGameTime()), 20,
                    "TaCZ Post must not count the actual health loss twice");
        });
        helper.runAfterDelay(180, () -> near(helper, apex.getHealth(), 244, "Actual gun damage must suppress later pulses"));
        helper.runAfterDelay(183, () -> helper.assertTrue(apex.getHealingVisualState() == 2,
                "A new warning must follow five quiet seconds after gun damage"));
        helper.runAfterDelay(203, () -> {
            near(helper, apex.getHealth(), 248, "Recovery must resume from the actual bullet damage clock");
            helper.succeed();
        });
    }

    private static void postGunfire(Player shooter) {
        Item gunItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "modern_kinetic_gun"));
        if (gunItem == null || gunItem == Items.AIR) throw new IllegalStateException("TaCZ gun item is absent");
        ItemStack gun = new ItemStack(gunItem);
        gun.getOrCreateTag().putString("GunId", GUN_ID.toString());
        try {
            Constructor<?> constructor = Class.forName("com.tacz.guns.api.event.common.GunFireEvent")
                    .getConstructor(LivingEntity.class, ItemStack.class, LogicalSide.class);
            MinecraftForge.EVENT_BUS.post((Event) constructor.newInstance(shooter, gun, LogicalSide.SERVER));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("TaCZ gunfire event API does not match the optional bridge", exception);
        }
    }

    private static void postGunHit(Entity bullet, ApexEntity apex, Player shooter, DamageSource source, float damage) {
        try {
            Constructor<?> constructor = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post")
                    .getConstructor(Entity.class, Entity.class, LivingEntity.class,
                            ResourceLocation.class, ResourceLocation.class, float.class, Pair.class,
                            boolean.class, float.class, LogicalSide.class);
            MinecraftForge.EVENT_BUS.post((Event) constructor.newInstance(bullet, apex, shooter,
                    GUN_ID, GUN_ID, damage, Pair.of(source, source), false, 1.0F, LogicalSide.SERVER));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("TaCZ Post event API does not match the optional bridge", exception);
        }
    }

    private static ApexEntity boss(GameTestHelper helper) {
        ApexEntity apex = helper.spawnWithNoFreeWill(ApexRegistries.APEX.get(), new BlockPos(5, 2, 5));
        apex.setNoAi(true);
        helper.assertTrue(helper.getLevel().noCollision(apex, apex.getBoundingBox()),
                "TaCZ test boss must spawn above the fixture floor, without block collisions");
        return apex;
    }

    private static Player shooter(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.moveTo(helper.absoluteVec(new Vec3(8.5, 2, 5.5)));
        return player;
    }

    private static PlayerBehaviorTracker.BehaviorSnapshot snapshot(ApexEntity apex, Player shooter, GameTestHelper helper) {
        return apex.behaviorTracker().snapshot(shooter.getUUID(), helper.getLevel().getGameTime());
    }

    private static void near(GameTestHelper helper, double actual, double expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < .001,
                description + ": expected " + expected + ", actual " + actual);
    }
}
