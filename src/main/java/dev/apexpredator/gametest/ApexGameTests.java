package dev.apexpredator.gametest;

import dev.apexpredator.combat.AttackType;
import dev.apexpredator.combat.CombatState;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.function.Consumer;

/** Integration checks run against Forge's actual registries, entities, damage and loot. */
@GameTestHolder("apexpredator")
@PrefixGameTestTemplate(false)
public final class ApexGameTests {
    // The GameTest structure block loads template y=0 at helper-relative y=1.
    // Our stone floor is therefore y=1 and clear standing space starts at y=2.
    private static final BlockPos BOSS_POS = new BlockPos(5, 2, 5);

    private ApexGameTests() { }

    @GameTest(template = "empty")
    public static void registeredAttributesAndSpawnEgg(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        near(helper, apex.getMaxHealth(), 600, "Maximum health");
        near(helper, apex.getHealth(), 600, "Spawn health");
        near(helper, apex.getAttributeValue(Attributes.ATTACK_DAMAGE), 14, "Attack damage");
        near(helper, apex.getAttributeValue(Attributes.MOVEMENT_SPEED), .34, "Movement speed");
        near(helper, apex.getAttributeValue(Attributes.FOLLOW_RANGE), 64, "Follow range");
        near(helper, apex.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), .8, "Knockback resistance");
        near(helper, apex.getAttributeValue(Attributes.ARMOR), 18, "Armor");
        helper.assertTrue(apex.getBbHeight() >= 2.6 && apex.getBbHeight() <= 2.8 && apex.isPersistenceRequired(),
                "Boss must retain its fast-combat 2.6–2.8-block size and persistence");
        helper.assertTrue(((SpawnEggItem) ApexRegistries.APEX_SPAWN_EGG.get()).getType(null) == ApexRegistries.APEX.get(),
                "Spawn egg must create the registered Apex entity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cappedDamageStillUsesVanillaArmor(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        Player attacker = target(helper, new Vec3(8.5, 2, 5.5));
        float before = apex.getHealth();
        helper.assertTrue(apex.hurt(apex.damageSources().mobAttack(attacker), 10_000), "First hit must be accepted");
        float lost = before - apex.getHealth();
        float expected = CombatRules.getDamageAfterAbsorb(35, 18, 0);
        near(helper, lost, expected, "Damage after the per-hit cap and vanilla armor");
        helper.assertTrue(lost > 0 && lost < 35, "Armor must remain effective after applying the cap");
        near(helper, apex.damageTracker().getDamagePerSecond(helper.getLevel().getGameTime()), lost,
                "Tracker must count health lost, rather than the raw 10000 damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void burstFireTriggersDodgeWithoutInstantKill(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        Player target = target(helper, new Vec3(8.5, 2, 5.5));
        apex.setTarget(target);
        for (int shot = 0; shot < 3; shot++) {
            apex.invulnerableTime = 0;
            helper.assertTrue(apex.hurt(apex.damageSources().generic(), 35), "Burst hit " + shot + " must land");
        }
        near(helper, apex.getHealth(), 495, "Three capped hits must deal 105 actual damage");
        helper.assertTrue(apex.isAlive(), "Burst damage must not instantly kill Apex");
        helper.assertTrue(apex.damageTracker().isEvading(helper.getLevel().getGameTime()), "Burst must request evasion");
        apex.combatController().tick();
        helper.assertTrue(apex.getCombatState() == CombatState.DODGE, "Damage reaction must reach the real controller");
        apex.invulnerableTime = 0;
        helper.assertFalse(apex.hurt(apex.damageSources().generic(), 35), "Visible dodge grants a short immunity window");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void splitProjectileDamageSharesOneHitBudget(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        Player shooter = target(helper, new Vec3(8.5, 2, 5.5));
        Arrow bullet = helper.spawn(EntityType.ARROW, 2, 2, 5);
        var source = apex.damageSources().arrow(bullet, shooter);
        helper.assertTrue(apex.hurt(source, 20), "First projectile damage component must land");
        apex.invulnerableTime = 0;
        helper.assertTrue(apex.hurt(source, 20), "Second component may use the remaining 15 damage");
        apex.invulnerableTime = 0;
        helper.assertFalse(apex.hurt(source, 5), "A third component must not exceed the shared bullet budget");
        near(helper, apex.getHealth(), 565, "Same projectile and tick must deal no more than 35 total damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void phaseThresholdsSynchronizeAndSurviveNbtLoad(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        apex.setHealth(401);
        apex.hurt(apex.damageSources().generic(), 1);
        near(helper, apex.getHealth(), 400, "First phase boundary");
        helper.assertTrue(apex.getPhase() == 2, "Health 400 must synchronize phase two");
        apex.invulnerableTime = 0;
        apex.setHealth(201);
        apex.hurt(apex.damageSources().generic(), 1);
        helper.assertTrue(apex.getPhase() == 3, "Health 200 must synchronize phase three");
        helper.assertTrue(apex.getAttributeValue(Attributes.MOVEMENT_SPEED) > .34, "Later phases must increase speed");
        CompoundTag saved = apex.saveWithoutId(new CompoundTag());
        ApexEntity restored = ApexRegistries.APEX.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "Registered entity factory must create Apex");
        restored.load(saved);
        helper.assertTrue(restored.getPhase() == 3, "Phase three must survive entity NBT loading");
        near(helper, restored.getHealth(), 260, "Saved health including the one-time final-phase entry heal");
        near(helper, restored.getAttributeValue(Attributes.MOVEMENT_SPEED),
                apex.getAttributeValue(Attributes.MOVEMENT_SPEED), "Saved phase speed");
        restored.setHealth(600);
        restored.invulnerableTime = 0;
        restored.hurt(restored.damageSources().generic(), 1);
        helper.assertTrue(restored.getPhase() == 3, "Healing after loading must not reset the final phase");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void deathDropsApexCore(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        helper.assertTrue(apex.hurt(apex.damageSources().genericKill(), Float.MAX_VALUE), "Administrative kill must work");
        helper.runAfterDelay(1, () -> {
            helper.assertItemEntityPresent(ApexRegistries.APEX_CORE.get(), BOSS_POS, 3);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void meleeWindupRecoveryAndCooldown(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        Player target = target(helper, new Vec3(6.5, 2, 5.5));
        float rawDamage = (float) apex.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float expectedDamage = switch (helper.getLevel().getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> Math.min(rawDamage / 2 + 1, rawDamage);
            case NORMAL -> rawDamage;
            case HARD -> rawDamage * 1.5F;
        };
        apex.setTarget(target);
        apex.combatController().tick();
        helper.assertTrue(apex.getCombatState() == CombatState.MELEE, "Close phase-one target must select melee");
        for (int tick = 1; tick <= 23; tick++) {
            int elapsed = tick;
            helper.runAfterDelay(tick, () -> {
                apex.combatController().tick();
                if (elapsed == 6) near(helper, target.getHealth(), 1000, "Melee must not hit during its windup");
                if (elapsed == 11) {
                    near(helper, target.getHealth(), 1000 - expectedDamage, "Melee damage after its windup and vanilla difficulty scaling");
                    helper.assertTrue(apex.getCombatState() == CombatState.RECOVERY, "Committed melee must leave an attack opening");
                    helper.assertTrue(apex.combatController().getLastAttack() == AttackType.MELEE, "Debug attack state must be melee");
                }
                if (elapsed == 18) helper.assertTrue(apex.getCombatState() == CombatState.RECOVERY,
                        "Apex must keep its promised recovery window");
                if (elapsed == 23) {
                    near(helper, target.getHealth(), 1000 - expectedDamage, "Melee must strike only once before the cooldown ends");
                    helper.assertTrue(apex.getCombatState() != CombatState.MELEE, "Cooldown must prevent immediately restarting melee");
                    helper.succeed();
                }
            });
        }
    }

    @GameTest(template = "empty")
    public static void changingTargetCancelsCommittedMelee(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        Player original = target(helper, new Vec3(6.5, 2, 5.5));
        Player replacement = target(helper, new Vec3(5.5, 2, 7.5));
        apex.setTarget(original);
        apex.combatController().tick();
        helper.assertTrue(apex.getCombatState() == CombatState.MELEE, "First target must have a committed melee windup");
        apex.setTarget(replacement);
        apex.combatController().tick();
        helper.assertTrue(apex.getCombatState() == CombatState.RECOVERY, "Target changes must cancel the committed attack");
        near(helper, original.getHealth(), 1000, "Canceled strike must not hurt the original target");
        near(helper, replacement.getHealth(), 1000, "Canceled strike must not transfer to the replacement target");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void sustainedUnavoidableFireRemainsFiniteAndSurvivable(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        apex.setNoAi(false);
        // Zero armor isolates the temporary defenses from ordinary armor mitigation.
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        float[] damageTaken = {0};
        boolean[] sawTemporaryHitLimit = {false};
        for (int tick = 1; tick <= 120; tick++) {
            int elapsed = tick;
            helper.runAfterDelay(tick, () -> {
                helper.assertTrue(apex.isAlive(), "Unavoidable firing must not kill Apex before six seconds");
                Arrow bullet = EntityType.ARROW.create(helper.getLevel());
                helper.assertTrue(bullet != null, "Each shot needs a distinct projectile identity");
                // No world insertion: fixtures must not add extra physical arrow collisions.
                boolean evading = apex.damageTracker().isEvading(helper.getLevel().getGameTime());
                apex.invulnerableTime = 0;
                float dpsBefore = apex.damageTracker().getDamagePerSecond(helper.getLevel().getGameTime());
                boolean accepted = apex.hurt(apex.damageSources().arrow(bullet, null), 10_000);
                // Entry healing can raise HP on the same hit; damage accounting still records the actual loss first.
                float actual = apex.damageTracker().getDamagePerSecond(helper.getLevel().getGameTime()) - dpsBefore;
                damageTaken[0] += actual;
                helper.assertTrue(actual <= (evading ? 14.001F : 35.001F), "Hit ceiling must follow the finite evasion mode");
                if (accepted && evading) {
                    helper.assertTrue(actual > 0, "Evasion must allow damage between short visible immunity windows");
                    sawTemporaryHitLimit[0] = true;
                }
                if (elapsed == 120) {
                    helper.assertTrue(apex.isAlive(), "Apex must survive six seconds of extreme unavoidable fire");
                    helper.assertTrue(damageTaken[0] >= 200, "Temporary defenses must not make sustained fire harmless");
                    helper.assertTrue(apex.getPhase() >= 2, "Actual sustained damage must still advance combat phases");
                    helper.assertTrue(sawTemporaryHitLimit[0], "The temporary hit ceiling must be exercised by accepted shots");
                }
            });
        }
        helper.runAfterDelay(180, () -> {
            helper.assertTrue(apex.isAlive(), "Boss must remain alive after firing stops");
            helper.assertFalse(apex.damageTracker().isEvading(helper.getLevel().getGameTime()),
                    "Repeated hits must not make the evasion mode permanent");
            apex.invulnerableTime = 0;
            float before = apex.getHealth();
            helper.assertTrue(apex.hurt(apex.damageSources().generic(), 35), "A normal hit must land once evasion finishes");
            near(helper, before - apex.getHealth(), 35, "Normal hit ceiling must return after the finite evasion mode");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void externalDamageMultiplierCannotBypassProjectileBudget(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        Arrow bullet = EntityType.ARROW.create(helper.getLevel());
        helper.assertTrue(bullet != null, "A projectile identity is required");
        var source = apex.damageSources().arrow(bullet, null);
        Consumer<LivingDamageEvent> multiplier = event -> {
            if (event.getEntity() == apex) event.setAmount(event.getAmount() * 10);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingDamageEvent.class, multiplier);
        try {
            helper.assertTrue(apex.hurt(source, 10), "An amplified first component must still land");
            near(helper, apex.getHealth(), 565, "External multiplier must be bounded by the final 35 damage cap");
            apex.invulnerableTime = 0;
            helper.assertFalse(apex.hurt(source, 25), "The amplified component must consume the projectile's whole budget");
            near(helper, apex.getHealth(), 565, "Split hit must not exceed its budget after a Forge damage multiplier");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(multiplier);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void visualImmunityExpiresWithDisabledAi(GameTestHelper helper) {
        ApexEntity apex = spawnBoss(helper);
        apex.beginVisualDodge(6);
        helper.assertFalse(apex.hurt(apex.damageSources().generic(), 10), "Visual dodge initially blocks a hit");
        helper.runAfterDelay(8, () -> {
            apex.invulnerableTime = 0;
            helper.assertTrue(apex.hurt(apex.damageSources().generic(), 10), "Disabling AI must not make a short dodge permanent");
            near(helper, apex.getHealth(), 590, "The hit must land after the six-tick visual immunity expires");
            helper.succeed();
        });
    }

    private static ApexEntity spawnBoss(GameTestHelper helper) {
        ApexEntity apex = helper.spawnWithNoFreeWill(ApexRegistries.APEX.get(), BOSS_POS);
        apex.setNoAi(true);
        helper.assertTrue(helper.getLevel().noCollision(apex, apex.getBoundingBox()), "Boss fixture must spawn in clear air");
        helper.assertTrue(helper.getLevel().getBlockState(apex.blockPosition().below()).is(Blocks.STONE),
                "Boss fixture must stand above its stone floor");
        return apex;
    }

    private static Player target(GameTestHelper helper, Vec3 relativePosition) {
        Player player = helper.makeMockSurvivalPlayer();
        player.moveTo(helper.absoluteVec(relativePosition));
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        return player;
    }

    private static void near(GameTestHelper helper, double actual, double expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < .001,
                description + ": expected " + expected + ", actual " + actual);
    }
}
