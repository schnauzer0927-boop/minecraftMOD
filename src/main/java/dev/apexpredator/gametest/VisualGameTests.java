package dev.apexpredator.gametest;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.ApexConfig;
import dev.apexpredator.combat.AttackManager;
import dev.apexpredator.combat.AttackType;
import dev.apexpredator.combat.CombatState;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Server-side animation synchronization and playable timing checks; no client class loading. */
@GameTestHolder(ApexPredator.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VisualGameTests {
    private VisualGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void visualReplayHasSynchronizedSequenceAndServerStartTick(GameTestHelper helper) {
        ApexEntity apex = boss(helper);
        apex.playVisual("teleport_start", 8);
        int initialSequence = apex.getVisualSequence();
        helper.assertTrue(apex.getVisualAnimation().equals("teleport_start"), "Server failed to publish animation name");
        helper.assertTrue(apex.getVisualStartTick() == helper.getLevel().getGameTime(), "Animation start must use server time");
        apex.playVisual("teleport_start", 8);
        helper.assertTrue(apex.getVisualSequence() > initialSequence,
                "Repeated attack animations need a new synchronized sequence to replay on clients");
        apex.playVisual("teleport_end", 8);
        helper.assertTrue(apex.getVisualAnimation().equals("teleport_end"), "Arrival animation did not replace departure");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void phaseChangeCancelsAttackAndPausesCombatForItsAnimation(GameTestHelper helper) {
        ApexEntity apex = boss(helper);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        Player target = helper.makeMockSurvivalPlayer();
        target.moveTo(helper.absoluteVec(new Vec3(6.5, 2, 5.5)));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        apex.setTarget(target);
        apex.combatController().tick();
        helper.assertTrue(apex.getCombatState() == CombatState.MELEE, "Fixture must start a committed melee windup");
        apex.setHealth(401);
        helper.assertTrue(apex.hurt(apex.damageSources().generic(), 1), "Boundary hit must land");
        helper.assertTrue(apex.getPhase() == 2, "Boundary must enter phase two");
        helper.assertTrue(apex.getVisualAnimation().equals("phase_change"), "Phase deployment animation is missing");
        helper.assertTrue(apex.combatController().getPhasePauseRemaining() == 36, "Phase deployment must pause combat for 36 ticks");
        for (int elapsed = 1; elapsed <= 36; elapsed++) {
            int tick = elapsed;
            helper.runAfterDelay(elapsed, () -> {
                apex.combatController().tick();
                helper.assertTrue(target.getHealth() == 1000, "A canceled strike dealt damage during phase deployment");
                if (tick < 36) {
                    helper.assertTrue(apex.combatController().getPhasePauseRemaining() > 0,
                            "Combat resumed before phase deployment finished");
                    helper.assertTrue(apex.getDeltaMovement().horizontalDistanceSqr() == 0,
                            "Phase deployment must stop horizontal movement");
                } else {
                    helper.assertTrue(apex.combatController().getPhasePauseRemaining() == 0,
                            "Phase deployment pause became permanent");
                    helper.succeed();
                }
            });
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bladeComboHasThreeTelegraphedHitsWithoutDiscardingVanillaIframes(GameTestHelper helper) {
        ApexEntity apex = boss(helper);
        Pig victim = durableTarget(helper, new Vec3(6.5, 2, 5.5));
        AttackManager attacks = new AttackManager(apex);
        attacks.begin(AttackType.BLADE_COMBO, victim);
        helper.assertTrue(apex.getVisualAnimation().equals("blade_combo_1"), "Combo must begin with its first blade animation");
        int windup = AttackType.BLADE_COMBO.telegraphTicks();
        for (int elapsed = 0; elapsed < windup; elapsed++) {
            attacks.tick(AttackType.BLADE_COMBO, victim, elapsed);
            helper.assertTrue(victim.getHealth() == 1000, "Blade combo hit during its first warning");
        }
        attacks.tick(AttackType.BLADE_COMBO, victim, windup);
        near(helper, victim.getHealth(), 986, "First blade hit");
        helper.assertTrue(victim.invulnerableTime > 0, "Combo must preserve vanilla damage invulnerability");
        attacks.tick(AttackType.BLADE_COMBO, victim, windup + 1);
        near(helper, victim.getHealth(), 986, "Blade hit must occur once, rather than every active tick");
        attacks.tick(AttackType.BLADE_COMBO, victim, windup + 4);
        helper.assertTrue(apex.getVisualAnimation().equals("blade_combo_2"), "Second blade warning must precede the second hit");
        // Advance the vanilla invulnerability interval explicitly for this synchronous fixture.
        victim.invulnerableTime = 0;
        attacks.tick(AttackType.BLADE_COMBO, victim, windup + 12);
        near(helper, victim.getHealth(), 972, "Second blade hit");
        attacks.tick(AttackType.BLADE_COMBO, victim, windup + 16);
        helper.assertTrue(apex.getVisualAnimation().equals("blade_combo_3"), "Vertical finisher must have its own warning pose");
        victim.invulnerableTime = 0;
        attacks.tick(AttackType.BLADE_COMBO, victim, windup + 24);
        near(helper, victim.getHealth(), 954, "Vertical combo finisher");
        helper.assertTrue(attacks.tick(AttackType.BLADE_COMBO, victim, windup + 28), "Combo must finish and permit recovery");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void executionWarningAndDelayedShockwaveHaveBoundedDamage(GameTestHelper helper) {
        ApexEntity apex = boss(helper);
        CompoundTag finalPhase = apex.saveWithoutId(new CompoundTag());
        finalPhase.putInt("ApexPhase", 3);
        finalPhase.putFloat("Health", 200);
        apex.load(finalPhase);
        helper.assertTrue(apex.getPhase() == 3, "Execution fixture must enter the final phase");
        Pig victim = durableTarget(helper, new Vec3(7.5, 2, 5.5));
        AttackManager attacks = new AttackManager(apex);
        attacks.begin(AttackType.SPECIAL, victim);
        helper.assertTrue(apex.getVisualAnimation().equals("special_prepare"), "Execution must publish its warning animation");
        for (int elapsed = 0; elapsed < AttackManager.EXECUTION_WARNING_TICKS; elapsed++) {
            attacks.tick(AttackType.SPECIAL, victim, elapsed);
            near(helper, victim.getHealth(), 1000, "No damage during Execution warning");
            helper.assertTrue(apex.getDeltaMovement().horizontalDistanceSqr() == 0, "Execution warning must remain stationary");
        }
        float beforeShockwave = 1000;
        float afterCross = 1000;
        Vec3 fixedTarget = helper.absoluteVec(new Vec3(7.5, 2, 5.5));
        for (int active = 0; active <= AttackManager.EXECUTION_END_TICK; active++) {
            // Keep a durable target in the marked arena; natural knockback remains intact in production.
            victim.setPos(fixedTarget.x, fixedTarget.y, fixedTarget.z);
            victim.setDeltaMovement(Vec3.ZERO);
            victim.invulnerableTime = 0;
            if (active == AttackManager.EXECUTION_SHOCKWAVE_TICK) beforeShockwave = victim.getHealth();
            attacks.tick(AttackType.SPECIAL, victim, AttackManager.EXECUTION_WARNING_TICKS + active);
            if (active == AttackManager.EXECUTION_X_TICK) afterCross = victim.getHealth();
            if (active > AttackManager.EXECUTION_X_TICK && active < AttackManager.EXECUTION_SHOCKWAVE_TICK) {
                near(helper, victim.getHealth(), afterCross, "Execution shockwave must leave a delay after its X slash");
            }
            if (active == AttackManager.EXECUTION_SHOCKWAVE_TICK) {
                helper.assertTrue(victim.getHealth() < beforeShockwave, "Delayed Execution shockwave never landed");
            }
        }
        float lost = 1000 - victim.getHealth();
        helper.assertTrue(lost > 8 && lost <= 42, "Execution must be damaging yet bounded, actual health loss=" + lost);
        helper.assertTrue(victim.isAlive(), "Execution must not turn into an administrative one-shot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void deathKeepsTheCorpseLongEnoughForItsTwoSecondAnimation(GameTestHelper helper) {
        ApexEntity apex = boss(helper);
        helper.assertTrue(apex.hurt(apex.damageSources().genericKill(), Float.MAX_VALUE), "Administrative kill must remain supported");
        helper.assertTrue(apex.getVisualAnimation().equals("death"), "Death must publish its skeletal animation");
        helper.runAfterDelay(20, () -> {
            helper.assertFalse(apex.isRemoved(), "Default one-second despawn cut off the two-second death animation");
            helper.assertTrue(apex.getVisualAnimation().equals("death"), "Death animation was replaced while its corpse remained");
        });
        helper.runAfterDelay(42, () -> {
            helper.assertTrue(apex.isRemoved(), "Animated corpse must still be cleaned up after its animation ends");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void jumpSlamKeepsTerrainIntactWithTheDefaultConfiguration(GameTestHelper helper) {
        helper.assertFalse(ApexConfig.DESTROY_TERRAIN.get(), "Terrain destruction must default to disabled");
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) helper.setBlock(x, 1, z, Blocks.DIRT);
        }
        ApexEntity apex = SlamGameTestSupport.boss(helper, new Vec3(5.5, 2, 5.5));
        Pig victim = durableTarget(helper, new Vec3(7.5, 2, 5.5));
        SlamGameTestSupport.run(helper, apex, victim, landed -> {
            helper.assertTrue(victim.getHealth() < 1000, "Terrain-safe slam must still damage a target in its radius");
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) helper.assertBlockPresent(Blocks.DIRT, x, 1, z);
            }
        });
    }

    private static ApexEntity boss(GameTestHelper helper) {
        ApexEntity apex = helper.spawnWithNoFreeWill(ApexRegistries.APEX.get(), new Vec3(5.5, 2, 5.5));
        apex.setNoAi(true);
        return apex;
    }

    private static Pig durableTarget(GameTestHelper helper, Vec3 position) {
        Pig victim = helper.spawnWithNoFreeWill(EntityType.PIG, position);
        victim.setNoGravity(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        victim.setHealth(1000);
        return victim;
    }

    private static void near(GameTestHelper helper, float actual, float expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < .001,
                description + ": expected=" + expected + " actual=" + actual);
    }
}
