package dev.apexpredator.gametest;

import com.mojang.authlib.GameProfile;
import dev.apexpredator.ApexPredator;
import dev.apexpredator.combat.CombatState;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Dedicated-server verification of the encounter metadata; no client classes or audio device required. */
@GameTestHolder(ApexPredator.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BossMusicGameTests {
    private BossMusicGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void actualTargetStartsAndDisengagementClearsEncounter(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 4, 5);
        Player player = survivalPlayer(helper, 8, 5);
        helper.assertTrue(apex.getMusicTargetUuid().isEmpty(), "Idle nearby Apex must not start battle music");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(apex.getMusicTargetUuid().isEmpty(), "Proximity alone must remain silent");
            apex.setTarget(player);
        });
        helper.runAfterDelay(4, () -> {
            assertEngaged(helper, apex, player);
            helper.assertTrue(apex.isMusicEncounterFor(player), "Engaged nearby player must receive the soundtrack");
            apex.setCombatState(CombatState.RECOVERY);
        });
        helper.runAfterDelay(6, () -> {
            assertEngaged(helper, apex, player);
            apex.setTarget(null);
        });
        helper.runAfterDelay(8, () -> {
            assertSilent(helper, apex, "Ending combat must clear the synchronized target");
            helper.assertFalse(apex.isMusicEncounterFor(player), "A living but idle boss must stop its soundtrack");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void nonPlayerCreativeSpectatorAndDeadTargetsCannotStartMusic(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 4, 5);
        Player creative = helper.makeMockPlayer();
        creative.moveTo(helper.absoluteVec(new Vec3(8, 2, 5)));
        Player spectator = new ModePlayer(helper.getLevel(), false, true);
        spectator.moveTo(creative.position());
        Player dead = survivalPlayer(helper, 8, 5);
        dead.setHealth(0);
        var pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(8, 2, 7));
        apex.setTarget(pig);
        helper.runAfterDelay(2, () -> {
            assertSilent(helper, apex, "Non-player mob fights must not start the player boss soundtrack");
            apex.setTarget(creative);
        });
        helper.runAfterDelay(4, () -> {
            assertSilent(helper, apex, "Creative target must be excluded");
            apex.setTarget(spectator);
        });
        helper.runAfterDelay(6, () -> {
            assertSilent(helper, apex, "Spectator target must be excluded");
            apex.setTarget(dead);
        });
        helper.runAfterDelay(8, () -> {
            assertSilent(helper, apex, "Dead target must be excluded");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void disablingAiAndBossDeathRetirePreviouslyActiveEncounters(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 4, 5);
        Player player = survivalPlayer(helper, 8, 5);
        apex.setTarget(player);
        helper.runAfterDelay(2, () -> {
            assertEngaged(helper, apex, player);
            apex.setNoAi(true);
        });
        helper.runAfterDelay(4, () -> {
            assertSilent(helper, apex, "NoAI must clear a stale active combat target on a real entity tick");
            apex.setNoAi(false);
        });
        helper.runAfterDelay(6, () -> {
            assertEngaged(helper, apex, player);
            helper.assertTrue(apex.hurt(apex.damageSources().genericKill(), Float.MAX_VALUE), "Boss death fixture must die");
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(apex.isDeadOrDying(), "Fixture must remain dead");
            assertSilent(helper, apex, "Death animation must not keep the encounter music alive");
            helper.assertFalse(apex.isMusicEncounterFor(player), "Dead boss must be filtered even before despawn");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void listenerRangeIsIndependentOfExistingNinetySixBlockPursuit(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 4, 5);
        Player target = survivalPlayer(helper, 8, 5);
        Player listener = survivalPlayer(helper, 7, 5);
        Player creativeListener = helper.makeMockPlayer();
        creativeListener.moveTo(listener.position());
        apex.setTarget(target);
        helper.runAfterDelay(2, () -> {
            assertEngaged(helper, apex, target);
            helper.assertTrue(apex.isMusicEncounterFor(listener), "A nearby ally hears another player's ongoing battle");
            helper.assertTrue(apex.isMusicEncounterFor(creativeListener),
                    "Creative listeners may hear a nearby real fight even though they cannot initiate one");
            listener.moveTo(apex.position().add(64, 0, 0));
        });
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(apex.isMusicEncounterFor(listener), "Exact 64-block listener boundary must be included");
            listener.moveTo(apex.position().add(64.1, 0, 0));
            target.moveTo(apex.position().add(96, 0, 0));
        });
        helper.runAfterDelay(6, () -> {
            assertEngaged(helper, apex, target);
            helper.assertFalse(apex.isMusicEncounterFor(listener), "Leaving the acoustic range stops local music");
            target.moveTo(apex.position().add(96.1, 0, 0));
        });
        helper.runAfterDelay(8, () -> {
            assertSilent(helper, apex, "Targets beyond the existing AI pursuit cutoff must not keep combat metadata active");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void anotherDimensionAndPlayerDeathEndTheEncounter(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 4, 5);
        Player player = survivalPlayer(helper, 8, 5);
        ServerLevel otherLevel = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(otherLevel != null && otherLevel != helper.getLevel(), "Different-dimension fixture must exist");
        Player otherDimension = new ModePlayer(otherLevel, false, false);
        otherDimension.moveTo(player.position());
        apex.setTarget(player);
        helper.runAfterDelay(2, () -> {
            assertEngaged(helper, apex, player);
            helper.assertFalse(apex.isMusicEncounterFor(otherDimension), "Same coordinates in another dimension are not nearby");
            apex.setTarget(otherDimension);
        });
        helper.runAfterDelay(4, () -> {
            assertSilent(helper, apex, "Changed target dimension must clear synchronized engagement");
            apex.setTarget(player);
        });
        helper.runAfterDelay(6, () -> {
            assertEngaged(helper, apex, player);
            player.setHealth(0);
        });
        helper.runAfterDelay(8, () -> {
            assertSilent(helper, apex, "Player death must stop a previously active boss fight");
            helper.assertFalse(apex.isMusicEncounterFor(player), "Dead local player must never listen to an encounter");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void multipleBossesRetainIndependentTargetsAndRemovalIsFiltered(GameTestHelper helper) {
        ApexEntity first = boss(helper, 3, 4);
        ApexEntity second = boss(helper, 8, 8);
        Player original = survivalPlayer(helper, 6, 5);
        Player replacement = survivalPlayer(helper, 7, 6);
        first.setTarget(original);
        second.setTarget(original);
        helper.runAfterDelay(2, () -> {
            assertEngaged(helper, first, original);
            assertEngaged(helper, second, original);
            first.setTarget(null);
            second.setTarget(replacement);
        });
        helper.runAfterDelay(4, () -> {
            assertSilent(helper, first, "First boss ending combat must not touch the second boss's metadata");
            assertEngaged(helper, second, replacement);
            helper.assertFalse(second.isMusicTarget(original), "Target switch must replace the synchronized UUID");
            helper.assertTrue(second.isMusicEncounterFor(original), "Switching attack targets must not silence a nearby living ally");
            second.discard();
            helper.assertFalse(second.isMusicEncounterFor(original), "Despawn must invalidate the encounter immediately");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void finalPhaseEntryPreservesBattleMusicAndExistingHealing(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 4, 5);
        Player player = survivalPlayer(helper, 8, 5);
        apex.setTarget(player);
        helper.runAfterDelay(2, () -> {
            assertEngaged(helper, apex, player);
            apex.setHealth(201);
            apex.invulnerableTime = 0;
            helper.assertTrue(apex.hurt(apex.damageSources().generic(), 1), "Real phase-three transition hit must land");
            helper.assertTrue(apex.getPhase() == 3, "Final phase must still activate");
            helper.assertTrue(Math.abs(apex.getHealth() - 260) < .001, "Existing 60HP phase-entry healing must be preserved");
        });
        helper.runAfterDelay(4, () -> {
            assertEngaged(helper, apex, player);
            helper.assertTrue(apex.isMusicEncounterFor(player), "Phase change must not interrupt the stable soundtrack");
            helper.succeed();
        });
    }

    private static void assertEngaged(GameTestHelper helper, ApexEntity apex, Player player) {
        helper.assertTrue(apex.getMusicTargetUuid().filter(player.getUUID()::equals).isPresent(),
                "Server tick must publish the actual valid target UUID: target=" + apex.getTarget()
                        + " synchronized=" + apex.getMusicTargetUuid());
        helper.assertTrue(apex.isMusicTarget(player), "Authoritative target predicate must match synchronized UUID");
    }

    private static void assertSilent(GameTestHelper helper, ApexEntity apex, String reason) {
        helper.assertTrue(apex.getMusicTargetUuid().isEmpty(), reason + ": " + apex.getMusicTargetUuid());
    }

    private static Player survivalPlayer(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockSurvivalPlayer();
        player.moveTo(helper.absoluteVec(new Vec3(x, 2, z)));
        return player;
    }

    private static ApexEntity boss(GameTestHelper helper, double x, double z) {
        ApexEntity apex = new StationaryApex(helper.getLevel());
        apex.moveTo(helper.absoluteVec(new Vec3(x, 2, z)));
        // Disable goal scheduling in the fixture, rather than NoAI: production music intentionally rejects NoAI.
        apex.setNoAi(false);
        // Eligibility tests need exact distance boundaries; floor settling must not introduce vertical drift.
        apex.setNoGravity(true);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        helper.assertTrue(helper.getLevel().addFreshEntity(apex), "Music fixture must join the real dedicated-server level");
        helper.assertTrue(helper.getLevel().noCollision(apex, apex.getBoundingBox()), "Music fixture must stand in clear air");
        return apex;
    }

    private static final class StationaryApex extends ApexEntity {
        private StationaryApex(Level level) { super(ApexRegistries.APEX.get(), level); }
        @Override protected void registerGoals() { }
        @Override protected void customServerAiStep() { }
    }

    private static final class ModePlayer extends Player {
        private final boolean creative;
        private final boolean spectator;
        private ModePlayer(Level level, boolean creative, boolean spectator) {
            super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "apex-music-test"));
            this.creative = creative;
            this.spectator = spectator;
        }
        @Override public boolean isCreative() { return creative; }
        @Override public boolean isSpectator() { return spectator; }
    }
}
