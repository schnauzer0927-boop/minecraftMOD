package dev.apexpredator.gametest;

import com.mojang.logging.LogUtils;
import dev.apexpredator.ApexPredator;
import dev.apexpredator.combat.AttackArea;
import dev.apexpredator.combat.AttackManager;
import dev.apexpredator.combat.AttackType;
import dev.apexpredator.combat.CombatState;
import dev.apexpredator.combat.SlamTargetResolver;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Warning geometry and committed jump targets exercised by a real Forge dedicated server. */
@GameTestHolder(ApexPredator.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CircularAttackGameTests {
    private CircularAttackGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void shockwaveHitsCenterHalfRadiusAndPlayerBoxBoundaryButNotOutside(GameTestHelper helper) {
        ApexEntity apex = stillBoss(helper);
        double radius = 6;
        double[] distances = {0, radius * .5, radius + .25, radius + 1};
        boolean[] hits = {true, true, true, false};
        for (int index = 0; index < distances.length; index++) {
            Player player = player(helper, new Vec3(5.5 + distances[index], 2, 5.5));
            AttackManager attacks = new AttackManager(apex);
            attacks.begin(AttackType.SHOCKWAVE, player);
            AttackArea warning = attacks.getAttackArea();
            helper.assertTrue(warning.radius() == radius, "Phase-one warning changed its original six-block radius");
            for (int tick = 0; tick < AttackType.SHOCKWAVE.telegraphTicks(); tick++) {
                attacks.tick(AttackType.SHOCKWAVE, player, tick);
                helper.assertTrue(player.getHealth() == 1000, "Shockwave damaged the player before its original warning ended");
            }
            attacks.tick(AttackType.SHOCKWAVE, player, AttackType.SHOCKWAVE.telegraphTicks());
            assertHit(helper, player, hits[index], "distance=" + distances[index] + " " + attacks.debugSummary(player));
            helper.assertTrue(warning.center().equals(attacks.getDamageCenter()), "Warning and damage center diverged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void groundShockwaveCoversSlabStepAndLowJumpButExcludesHighPlayers(GameTestHelper helper) {
        ApexEntity apex = stillBoss(helper);
        double[] offsets = {.5, 1, 2.75, 4.25};
        boolean[] hits = {true, true, true, false};
        for (int index = 0; index < offsets.length; index++) {
            Player player = player(helper, new Vec3(8.5, 2 + offsets[index], 5.5));
            AttackManager attacks = new AttackManager(apex);
            attacks.begin(AttackType.SHOCKWAVE, player);
            attacks.tick(AttackType.SHOCKWAVE, player, AttackType.SHOCKWAVE.telegraphTicks());
            assertHit(helper, player, hits[index], "feetY offset=" + offsets[index] + " " + attacks.debugSummary(player));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void shockwaveDamageStaysAtItsWarningEvenIfTheBossIsMoved(GameTestHelper helper) {
        ApexEntity apex = stillBoss(helper);
        Player player = player(helper, new Vec3(5.5, 2, 5.5));
        AttackManager attacks = new AttackManager(apex);
        attacks.begin(AttackType.SHOCKWAVE, player);
        AttackArea warning = attacks.getAttackArea();
        apex.moveTo(helper.absoluteVec(new Vec3(20.5, 2, 5.5)));
        attacks.tick(AttackType.SHOCKWAVE, player, AttackType.SHOCKWAVE.telegraphTicks());
        assertHit(helper, player, true, "Committed center was left behind by the moved boss");
        helper.assertTrue(attacks.getAttackArea().equals(warning), "A warned area was replaced after boss displacement");
        helper.assertTrue(warning.center().equals(attacks.getDamageCenter()), "Actual damage followed the displaced boss");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void finalPhaseShockwaveAndExecutionUseTheirWarnedRadius(GameTestHelper helper) {
        ApexEntity apex = stillBoss(helper);
        // Keep the original failed setup as a diagnostic ray, not as a damage expectation.
        // Its ten-block target lies outside the 12-block structure and can meet another test's blocks.
        Player beyondTemplate = player(helper, new Vec3(15.75, 2, 5.5));
        var originalClip = helper.getLevel().clip(new ClipContext(apex.position().add(0, 1.2, 0),
                beyondTemplate.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, apex));
        LogUtils.getLogger().info("[APEX AREA FIXTURE] original outside-template ray={} block={} state={}",
                originalClip.getType(), originalClip.getBlockPos(), helper.getLevel().getBlockState(originalClip.getBlockPos()));
        // A diagonal from this interior corner fits even a fifteen-block radius in the same template.
        Vec3 localCenter = new Vec3(.9, 2, .9);
        apex.moveTo(helper.absoluteVec(localCenter));
        CompoundTag phase = apex.saveWithoutId(new CompoundTag());
        phase.putInt("ApexPhase", 3);
        phase.putFloat("Health", 200);
        apex.load(phase);
        for (AttackType type : new AttackType[]{AttackType.SHOCKWAVE, AttackType.SPECIAL}) {
            double radius = type == AttackType.SPECIAL ? AttackManager.EXECUTION_RADIUS : 10;
            for (boolean inside : new boolean[]{true, false}) {
                double diagonal = (radius + (inside ? .25 : 1)) / Math.sqrt(2);
                Player player = player(helper, localCenter.add(diagonal, 0, diagonal));
                AttackManager attacks = new AttackManager(apex);
                attacks.begin(type, player);
                helper.assertTrue(attacks.getAttackArea().radius() == radius, "Final-phase warning radius differs from its attack");
                if (inside) {
                    var clip = helper.getLevel().clip(new ClipContext(attacks.getAttackArea().center().add(0, 1.2, 0),
                            player.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, apex));
                    helper.assertTrue(clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS,
                            "Boundary fixture intersects solid cover: " + clip.getBlockPos());
                }
                int damageTick = type == AttackType.SPECIAL
                        ? AttackManager.EXECUTION_WARNING_TICKS + AttackManager.EXECUTION_SHOCKWAVE_TICK
                        : type.telegraphTicks();
                attacks.tick(type, player, damageTick);
                assertHit(helper, player, inside, type + " final-circle boundary " + attacks.debugSummary(player));
                helper.assertTrue(attacks.getAttackArea().center().equals(attacks.getDamageCenter()),
                        "Execution/phase shockwave did not use its committed circle center");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pullUsesItsCommittedCenterAndKeepsTheFourBlockEscapeGap(GameTestHelper helper) {
        double[] distances = {3, 8, 10.25, 11};
        boolean[] pulled = {false, true, true, false};
        for (int index = 0; index < distances.length; index++) {
            ApexEntity apex = stillBoss(helper);
            // Keep every complete target box inside the 12-block arena instead of natural terrain outside it.
            Vec3 localCenter = new Vec3(.9, 2, .9);
            apex.moveTo(helper.absoluteVec(localCenter));
            double diagonal = distances[index] / Math.sqrt(2);
            Player player = player(helper, localCenter.add(diagonal, 0, diagonal));
            AttackManager attacks = new AttackManager(apex);
            attacks.begin(AttackType.PULL, player);
            AttackArea warning = attacks.getAttackArea();
            var ray = helper.getLevel().clip(new ClipContext(warning.center().add(0, 1.2, 0),
                    player.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, apex));
            helper.assertTrue(ray.getType() == net.minecraft.world.phys.HitResult.Type.MISS,
                    "Pull fixture must not hide behind solid cover: " + ray.getBlockPos());
            apex.moveTo(helper.absoluteVec(new Vec3(10.5, 2, .9)));
            attacks.tick(AttackType.PULL, player, AttackType.PULL.telegraphTicks());
            helper.assertTrue((player.getDeltaMovement().horizontalDistanceSqr() > 1.0e-9) == pulled[index],
                    "Pull did not respect its original center, body boundary, or four-block gap: " + distances[index]);
            if (pulled[index]) helper.assertTrue(player.getDeltaMovement().x < 0 && player.getDeltaMovement().z < 0,
                    "Pull direction must point toward the committed center, rather than the relocated boss");
            helper.assertTrue(warning.center().equals(attacks.getDamageCenter()), "Pull moved its displayed center");
            helper.assertTrue(player.getHealth() == 1000, "Pull must preserve its original non-damaging behavior");
            apex.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aPhaseUpdateCannotEnlargeAnAlreadyWarnedShockwave(GameTestHelper helper) {
        ApexEntity apex = stillBoss(helper);
        Vec3 localCenter = new Vec3(.9, 2, .9);
        apex.moveTo(helper.absoluteVec(localCenter));
        double diagonal = 8 / Math.sqrt(2);
        Player outsideOriginalCircle = player(helper, localCenter.add(diagonal, 0, diagonal));
        AttackManager attacks = new AttackManager(apex);
        attacks.begin(AttackType.SHOCKWAVE, outsideOriginalCircle);
        AttackArea warning = attacks.getAttackArea();
        var ray = helper.getLevel().clip(new ClipContext(warning.center().add(0, 1.2, 0),
                outsideOriginalCircle.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, apex));
        helper.assertTrue(ray.getType() == net.minecraft.world.phys.HitResult.Type.MISS,
                "Phase-radius fixture must be exposed so solid cover cannot hide an incorrectly enlarged circle");
        CompoundTag phase = apex.saveWithoutId(new CompoundTag());
        phase.putInt("ApexPhase", 3);
        phase.putFloat("Health", 200);
        apex.load(phase);
        attacks.tick(AttackType.SHOCKWAVE, outsideOriginalCircle, AttackType.SHOCKWAVE.telegraphTicks());
        helper.assertTrue(attacks.getAttackArea().equals(warning) && warning.radius() == 6,
                "Changing phase enlarged a circle after the player had already seen its warning");
        assertHit(helper, outsideOriginalCircle, false, "Player outside the six-block warning should not be hit by a ten-block phase radius");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void shockwaveHitsPlayersOnRealSlabStairAndOneBlockSurfaces(GameTestHelper helper) {
        ApexEntity apex = stillBoss(helper);
        for (int surface = 0; surface < 3; surface++) {
            double feetY = surface == 0 ? 2.5 : 3;
            for (int x = 7; x <= 9; x++) for (int z = 4; z <= 6; z++) {
                helper.setBlock(x, 2, z, surface == 0 ? Blocks.STONE_SLAB.defaultBlockState()
                        : surface == 1 ? Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)
                        : Blocks.STONE.defaultBlockState());
            }
            Player player = player(helper, new Vec3(8.75, feetY, 5.5));
            helper.assertTrue(helper.getLevel().noCollision(player, player.getBoundingBox()),
                    "Step test player intersects the slab/stair/block fixture");
            AttackManager attacks = new AttackManager(apex);
            attacks.begin(AttackType.SHOCKWAVE, player);
            attacks.tick(AttackType.SHOCKWAVE, player, AttackType.SHOCKWAVE.telegraphTicks());
            assertHit(helper, player, true, "Surface=" + surface + " must not turn normal terrain into cover");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void slamGroundUsesTheLocalStoreyAndRejectsALowCeiling(GameTestHelper helper) {
        ApexEntity apex = stillBoss(helper);
        for (int x = 7; x <= 9; x++) for (int z = 4; z <= 6; z++) helper.setBlock(x, 4, z, Blocks.STONE);
        Vec3 upperFeet = helper.absoluteVec(new Vec3(8.5, 5, 5.5));
        helper.assertTrue(SlamTargetResolver.resolve(apex, upperFeet).orElseThrow().equals(upperFeet),
                "Upper-floor target was resolved to the separate floor below it");
        helper.assertTrue(SlamTargetResolver.resolve(apex, helper.absoluteVec(new Vec3(8.5, 2, 5.5))).isEmpty(),
                "A low ceiling cannot fit Apex; resolver must not choose the roof or search an unrelated lower floor");
        for (int x = 7; x <= 9; x++) for (int z = 4; z <= 6; z++) helper.setBlock(x, 4, z, Blocks.AIR);
        helper.assertTrue(SlamTargetResolver.resolve(apex, upperFeet).isEmpty(),
                "Resolver searched three blocks down into a different storey after the local platform was removed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void anAirborneTargetWarnsTheGroundBelowAndDoesNotMoveTheLandingUpward(GameTestHelper helper) {
        ApexEntity apex = SlamGameTestSupport.boss(helper, new Vec3(2.5, 2, 5.5));
        Player player = player(helper, new Vec3(8.5, 3.25, 5.5));
        AttackManager attacks = SlamGameTestSupport.run(helper, apex, player,
                landed -> assertHit(helper, player, true, "An ordinary airborne player remains within the ground attack's vertical band"));
        helper.assertTrue(attacks.getSlamTarget().equals(helper.absoluteVec(new Vec3(8.5, 2, 5.5))),
                "Jumping target produced a warning in empty air instead of on its local ground");
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cancelingAnAirborneSlamRestoresItsOriginalGravitySetting(GameTestHelper helper) {
        checkGravityRestoration(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void losingTheTargetDuringAnAirborneSlamRestoresItsOriginalGravitySetting(GameTestHelper helper) {
        checkGravityRestoration(helper, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theRealControllerDefersBurstEvasionUntilItsCommittedSlamLands(GameTestHelper helper) {
        ApexEntity apex = helper.spawn(ApexRegistries.APEX.get(), new Vec3(2.5, 2, 5.5));
        apex.setNoAi(false);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        Player player = player(helper, new Vec3(7, 2, 5.5));
        apex.setTarget(player);
        Vec3 center = player.position();
        boolean[] burst = {false};
        boolean[] landed = {false};
        long[] evasionDeadline = {0};
        helper.onEachTick(() -> {
            if (landed[0]) return;
            if (!burst[0] && apex.getCombatState() == CombatState.JUMP && !apex.onGround()
                    && apex.combatController().getAttackElapsed() > AttackType.JUMP.telegraphTicks() + 1) {
                burst[0] = true;
                for (int hit = 0; hit < 3; hit++) {
                    apex.invulnerableTime = 0;
                    helper.assertTrue(apex.hurt(apex.damageSources().generic(), 35), "Committed jump's burst hit was unexpectedly blocked");
                }
                helper.assertTrue(apex.damageTracker().isEvading(helper.getLevel().getGameTime()),
                        "Burst must activate the existing immediate anti-DPS protection during the jump");
                helper.assertTrue(apex.getHealth() == 495, "Deferring movement must not discard the three actual capped hits");
                long now = helper.getLevel().getGameTime();
                evasionDeadline[0] = now + apex.damageTracker().remainingEvasionTicks(now);
            }
            if (burst[0] && apex.getVisualAnimation().equals("slam")) {
                landed[0] = true;
                helper.assertTrue(apex.position().distanceTo(center) < .5,
                        "Queued dodge/warp moved the real controller's committed landing: " + apex.debugSummary());
                assertHit(helper, player, true, "Real-controller slam missed its stationary warned player");
                LogUtils.getLogger().info("[APEX SLAM CONTROLLER TEST] actualLanding={} expected={} LandingError={} {}",
                        apex.position(), center, apex.position().distanceTo(center), apex.debugSummary());
                helper.runAfterDelay(1, () -> {
                    helper.assertTrue(apex.getCombatState() == CombatState.DODGE,
                            "Queued burst evasion was lost instead of resuming after actual landing: " + apex.debugSummary());
                    long afterOriginalDeadline = Math.max(1, evasionDeadline[0] - helper.getLevel().getGameTime() + 1);
                    helper.runAfterDelay(afterOriginalDeadline, () -> {
                        helper.assertFalse(apex.damageTracker().isEvading(helper.getLevel().getGameTime()),
                                "Committed jump extended the original anti-DPS evasion timer");
                        helper.assertTrue(apex.getCombatState() != CombatState.DODGE,
                                "Deferred evasion restarted its full duration after landing: " + apex.debugSummary());
                        helper.succeed();
                    });
                });
            } else if (burst[0]) {
                helper.assertTrue(apex.getCombatState() == CombatState.JUMP,
                        "Damage evasion interrupted the real controller before the marked slam landed: " + apex.debugSummary());
            }
        });
    }

    private static void checkGravityRestoration(GameTestHelper helper, boolean loseTarget) {
        ApexEntity apex = SlamGameTestSupport.boss(helper, new Vec3(2.5, 2, 5.5));
        apex.setNoGravity(true);
        Player player = player(helper, new Vec3(8.5, 2, 5.5));
        AttackManager attacks = new AttackManager(apex);
        attacks.begin(AttackType.JUMP, player);
        int[] elapsed = {0};
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) return;
            int tick = elapsed[0]++;
            attacks.tick(AttackType.JUMP, player, tick);
            if (tick == AttackType.JUMP.telegraphTicks() + 2) {
                helper.assertTrue(!apex.onGround() && !apex.isNoGravity(), "Fixture must be genuinely airborne under temporary jump gravity");
                if (loseTarget) {
                    player.discard();
                    helper.assertTrue(attacks.tick(AttackType.JUMP, player, tick + 1), "Removed target did not cancel its committed jump");
                } else attacks.cancel();
                helper.assertTrue(apex.isNoGravity(), "Canceled jump did not restore the pre-attack NoGravity setting");
                helper.assertTrue(attacks.getDamageCenter() == null, "Canceled airborne jump applied untelegraphed landing damage");
                finished[0] = true;
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void jumpSlamLandsAtTheWarningAndHitsAStationaryPlayer(GameTestHelper helper) {
        ApexEntity apex = SlamGameTestSupport.boss(helper, new Vec3(2.5, 2, 5.5));
        Player player = player(helper, new Vec3(8.5, 2, 5.5));
        AttackManager attacks = SlamGameTestSupport.run(helper, apex, player,
                landed -> assertHit(helper, player, true, "Stationary player remained in the committed slam circle"));
        helper.assertTrue(attacks.getSlamTarget().equals(helper.absoluteVec(new Vec3(8.5, 2, 5.5))),
                "Flat-ground warning must select the actual supporting floor");
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void movingOutsideAfterTheWarningEscapesWithoutRetargetingTheSlam(GameTestHelper helper) {
        ApexEntity apex = SlamGameTestSupport.boss(helper, new Vec3(2.5, 2, 5.5));
        Player player = player(helper, new Vec3(8.5, 2, 5.5));
        AttackManager attacks = SlamGameTestSupport.run(helper, apex, player,
                landed -> assertHit(helper, player, false, "Player escaped the original warning circle"));
        Vec3 original = attacks.getSlamTarget();
        helper.runAfterDelay(3, () -> {
            player.moveTo(helper.absoluteVec(new Vec3(11.5, 2, 11.5)));
            helper.assertTrue(attacks.getSlamTarget().equals(original), "Moving target changed the warning's saved center");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void movingWithinTheWarningStillHitsAtTheOriginalLanding(GameTestHelper helper) {
        ApexEntity apex = SlamGameTestSupport.boss(helper, new Vec3(2.5, 2, 5.5));
        Player player = player(helper, new Vec3(8.5, 2, 5.5));
        SlamGameTestSupport.run(helper, apex, player,
                landed -> assertHit(helper, player, true, "Moving inside the original warning should remain vulnerable"));
        helper.runAfterDelay(3, () -> player.moveTo(helper.absoluteVec(new Vec3(8.5, 2, 7.5))));
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void jumpSlamSelectsAndLandsOnTheHalfSlabSurface(GameTestHelper helper) {
        for (int x = 7; x <= 9; x++) for (int z = 4; z <= 6; z++) helper.setBlock(x, 2, z, Blocks.STONE_SLAB);
        landOnRaisedSurface(helper, new Vec3(8.5, 2.5, 5.5), 2.5);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void jumpSlamSelectsTheOneBlockStepInsteadOfTheLowerFloor(GameTestHelper helper) {
        for (int x = 7; x <= 9; x++) for (int z = 4; z <= 6; z++) helper.setBlock(x, 2, z, Blocks.STONE);
        landOnRaisedSurface(helper, new Vec3(8.5, 3, 5.5), 3);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void jumpSlamUsesStairCollisionHeightForItsWarningAndLanding(GameTestHelper helper) {
        for (int x = 7; x <= 9; x++) for (int z = 4; z <= 6; z++) {
            helper.setBlock(x, 2, z, Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        }
        // The player fits over a lower tread with air beside it; Apex's wider footprint reaches its upper half.
        for (int z = 4; z <= 6; z++) helper.setBlock(7, 2, z, Blocks.AIR);
        Vec3 lowerFeet = new Vec3(8.15, 2.5, 5.5);
        Player lowerPlayer = player(helper, lowerFeet);
        helper.assertTrue(helper.getLevel().noCollision(lowerPlayer, lowerPlayer.getBoundingBox()),
                "Lower-tread player fixture must fit below the upper stair half");
        ApexEntity footprintProbe = stillBoss(helper);
        Vec3 resolved = SlamTargetResolver.resolve(footprintProbe, helper.absoluteVec(lowerFeet)).orElseThrow();
        helper.assertTrue(Math.abs(resolved.y - helper.absoluteVec(new Vec3(0, 3, 0)).y) < .001,
                "Apex's wider stair footprint must resolve the upper tread instead of intersecting it");
        footprintProbe.discard();
        landOnRaisedSurface(helper, new Vec3(8.75, 3, 5.5), 3);
    }

    private static void landOnRaisedSurface(GameTestHelper helper, Vec3 targetFeet, double expectedY) {
        ApexEntity apex = SlamGameTestSupport.boss(helper, new Vec3(2.5, 2, 5.5));
        Player player = player(helper, targetFeet);
        AttackManager attacks = SlamGameTestSupport.run(helper, apex, player,
                landed -> assertHit(helper, player, true, "Player on the warned elevated surface must be hit"));
        helper.assertTrue(Math.abs(attacks.getSlamTarget().y - helper.absoluteVec(new Vec3(0, expectedY, 0)).y) < .001,
                "Warning selected a different floor from the slab/stair/step supporting the target: " + attacks.getSlamTarget());
    }

    private static ApexEntity stillBoss(GameTestHelper helper) {
        ApexEntity apex = helper.spawnWithNoFreeWill(ApexRegistries.APEX.get(), new Vec3(5.5, 2, 5.5));
        apex.setNoAi(true);
        return apex;
    }

    private static Player player(GameTestHelper helper, Vec3 relativeFeet) {
        Player player = helper.makeMockSurvivalPlayer();
        player.moveTo(helper.absoluteVec(relativeFeet));
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        return player;
    }

    private static void assertHit(GameTestHelper helper, Player player, boolean expected, String description) {
        helper.assertTrue((player.getHealth() < 1000) == expected,
                "Expected hit=" + expected + " health=" + player.getHealth() + ": " + description);
    }
}
