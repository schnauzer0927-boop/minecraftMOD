package dev.apexpredator.gametest;

import dev.apexpredator.combat.AttackManager;
import dev.apexpredator.combat.AttackType;
import dev.apexpredator.combat.CombatState;
import dev.apexpredator.combat.Phase3HealingController;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/** Actual server ticks, damage hooks, phase transitions, NBT, BossBar and Forge healing events. */
@GameTestHolder("apexpredator")
@PrefixGameTestTemplate(false)
public final class HealingGameTests {
    private static final BlockPos BOSS_POS = new BlockPos(5, 2, 5);
    private HealingGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void earlierPhasesNeverHeal(GameTestHelper helper) {
        ApexEntity first = boss(helper);
        first.setHealth(500);
        ApexEntity second = boss(helper, new BlockPos(8, 2, 8));
        second.setHealth(401);
        helper.assertTrue(second.hurt(second.damageSources().generic(), 1), "Phase-two trigger must land");
        helper.assertTrue(second.getPhase() == 2, "Fixture must really enter phase two");
        helper.runAfterDelay(150, () -> {
            near(helper, first.getHealth(), 500, "Phase one must not recover");
            near(helper, second.getHealth(), 400, "Phase two must not recover");
            helper.assertTrue(first.getHealingVisualState() == 0 && second.getHealingVisualState() == 0,
                    "Earlier phases must not emit healing cues");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void finalPhaseEntryHealsOnceAndPreservesActualDamageAccounting(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        near(helper, apex.getHealth(), 260, "Entry must heal 60 after the triggering hit");
        near(helper, apex.damageTracker().getDamagePerSecond(helper.getLevel().getGameTime()), 1,
                "Entry healing must not hide the triggering health loss from DamageTracker");
        helper.assertTrue(apex.damageTracker().getLastDamageTick() == helper.getLevel().getGameTime(),
                "Triggering damage must start the existing recovery clock");
        near(helper, apex.getBossBarProgress(), 260.0 / 600, "BossBar must update in the transition hook");
        helper.assertTrue(apex.getHealingVisualState() == 1, "Phase entry must publish the separate entry cue");
        apex.invulnerableTime = 0;
        helper.assertTrue(apex.hurt(apex.damageSources().generic(), 1), "A second small hit must land");
        near(helper, apex.getHealth(), 259, "Staying in phase three must not replay the entry heal");
        helper.assertTrue(apex.getPhase() == 3, "Healing above 200 must not regress the final phase");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void quietDelayWarningDamageStopAndRestartUseRealServerTicks(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        helper.runAfterDelay(79, () -> {
            near(helper, apex.getHealth(), 260, "Healing must wait through four quiet seconds");
            helper.assertTrue(apex.getHealingVisualState() == 0, "Warning must not start before tick 80");
        });
        helper.runAfterDelay(81, () -> {
            near(helper, apex.getHealth(), 260, "Warning itself must not restore health");
            helper.assertTrue(apex.getHealingVisualState() == 2, "Final quiet second must show warning");
        });
        helper.runAfterDelay(99, () -> near(helper, apex.getHealth(), 260, "No passive HP before five seconds"));
        helper.runAfterDelay(101, () -> {
            near(helper, apex.getHealth(), 264, "First passive pulse after five quiet seconds");
            near(helper, apex.getBossBarProgress(), 264.0 / 600, "NoAi BossBar must update on the healing tick");
            helper.assertTrue(apex.getHealingVisualState() == 3, "Passive healing must be visible");
            apex.invulnerableTime = 0;
            helper.assertTrue(apex.hurt(apex.damageSources().generic(), 1), "Interrupting hit must land");
            near(helper, apex.getHealth(), 263, "Actual damage must immediately remove HP");
            helper.assertTrue(apex.getHealingVisualState() == 0,
                    "Damage hook must immediately stop the synchronized healing cue");
        });
        helper.runAfterDelay(180, () -> {
            near(helper, apex.getHealth(), 263, "A hit must restart the entire no-damage delay");
            helper.assertTrue(apex.getHealingVisualState() == 0, "Restart warning must not be early");
        });
        helper.runAfterDelay(183, () -> helper.assertTrue(apex.getHealingVisualState() == 2,
                "Five-second restart must have its own warning"));
        helper.runAfterDelay(200, () -> near(helper, apex.getHealth(), 263, "No healing before the new deadline"));
        helper.runAfterDelay(203, () -> {
            near(helper, apex.getHealth(), 267, "Healing must resume after five more quiet seconds");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void passiveHealingStopsAtThreeHundred(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        apex.setHealth(299);
        helper.runAfterDelay(101, () -> near(helper, apex.getHealth(), 300, "Last pulse must be clipped to the cap"));
        helper.runAfterDelay(170, () -> {
            near(helper, apex.getHealth(), 300, "Additional pulses must not exceed the passive cap");
            near(helper, apex.getBossBarProgress(), .5, "Capped BossBar must remain one half full");
            helper.assertTrue(apex.getPhase() == 3, "Reaching 300 must not undo phase three");
            helper.assertTrue(apex.getHealingVisualState() == 0, "Capped boss must stop its healing cue");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void deathCannotRegenerateOrKeepHealingCues(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        helper.assertTrue(apex.hurt(apex.damageSources().genericKill(), Float.MAX_VALUE), "Administrative death must work");
        helper.assertTrue(apex.isDeadOrDying(), "Fixture must actually die");
        near(helper, apex.getHealth(), 0, "Death health");
        helper.assertTrue(apex.getHealingVisualState() == 0, "Death must clear healing cues immediately");
        helper.runAfterDelay(20, () -> {
            near(helper, apex.getHealth(), 0, "The full death animation must not revive Apex");
            helper.assertTrue(apex.getHealingVisualState() == 0, "Death animation must not start a warning");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void nonfatalOutOfWorldDamageInterruptsHealingWithoutChangingAdministrativeBypass(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        long[] hitTick = {-1};
        helper.runAfterDelay(101, () -> {
            near(helper, apex.getHealth(), 264, "Administrative interrupt fixture must be actively healing");
            helper.assertTrue(apex.getHealingVisualState() == 3, "Passive healing cue must be active before the hit");
            near(helper, apex.damageTracker().getDamagePerSecond(helper.getLevel().getGameTime()), 0,
                    "Entry damage must have expired from the rolling DPS window");
            apex.invulnerableTime = 0;
            helper.assertTrue(apex.hurt(apex.damageSources().fellOutOfWorld(), 4),
                    "Nonfatal out-of-world damage must still use the existing administrative bypass");
            hitTick[0] = helper.getLevel().getGameTime();
            near(helper, apex.getHealth(), 260, "Out-of-world damage must remove its real four health");
            helper.assertTrue(apex.getHealingVisualState() == 0
                            && apex.healingController().getState() == Phase3HealingController.State.INACTIVE,
                    "Even administrative health loss must stop recovery immediately");
            helper.assertTrue(apex.damageTracker().getLastDamageTick() == hitTick[0],
                    "Administrative health loss must reset the same no-damage recovery clock");
            near(helper, apex.damageTracker().getDamagePerSecond(hitTick[0]), 0,
                    "Administrative bypass must remain excluded from combat DPS and evasion reactions");
            helper.assertFalse(apex.damageTracker().isEvading(hitTick[0]),
                    "Administrative bypass must not newly request combat evasion");
        });
        helper.runAfterDelay(180, () -> near(helper, apex.getHealth(), 260,
                "Administrative health loss must restart the full quiet delay"));
        helper.runAfterDelay(183, () -> helper.assertTrue(apex.getHealingVisualState() == 2,
                "Administrative interrupt must have a fresh warning before recovery"));
        helper.runAfterDelay(200, () -> near(helper, apex.getHealth(), 260, "No recovery before the administrative-hit deadline"));
        helper.runAfterDelay(203, () -> {
            near(helper, apex.getHealth(), 264, "Recovery may resume after another five quiet seconds");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void masterSwitchDisablesEntryPassiveAndVisuals(GameTestHelper helper) {
        ApexEntity apex = configuredBoss(helper, new Phase3HealingController.Settings(false, 60, 100, 4, 300));
        triggerFinalPhase(helper, apex);
        near(helper, apex.getHealth(), 200, "Disabled entry heal");
        helper.assertTrue(apex.getHealingVisualState() == 0, "Disabled entry must have no healing cue");
        helper.runAfterDelay(150, () -> {
            near(helper, apex.getHealth(), 200, "Disabled passive healing");
            helper.assertTrue(apex.getHealingVisualState() == 0, "Disabled healing must emit no warning or ongoing cue");
            helper.assertTrue(apex.getPhase() == 3, "The master switch must not disable phase three itself");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void entryHealCannotExceedEntityMaximumHealth(GameTestHelper helper) {
        ApexEntity apex = boss(helper);
        apex.getAttribute(Attributes.MAX_HEALTH).setBaseValue(210);
        triggerFinalPhase(helper, apex);
        near(helper, apex.getHealth(), 210, "Entry heal must use the entity's actual maximum health");
        near(helper, apex.getBossBarProgress(), 1, "Maximum-health entry BossBar");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void ordinaryCombatStatesCanHealWithoutBeingReplaced(GameTestHelper helper) {
        ApexEntity chaser = enterFinalPhase(helper);
        ApexEntity striker = boss(helper, new BlockPos(8, 2, 8));
        triggerFinalPhase(helper, striker);
        helper.runAfterDelay(40, () -> {
            chaser.setCombatState(CombatState.CHASE);
            striker.setCombatState(CombatState.MELEE);
        });
        helper.runAfterDelay(101, () -> {
            near(helper, chaser.getHealth(), 264, "Chase must permit passive recovery");
            near(helper, striker.getHealth(), 264, "Ordinary melee must permit passive recovery");
            helper.assertTrue(chaser.getCombatState() == CombatState.CHASE && striker.getCombatState() == CombatState.MELEE,
                    "Healing must not replace normal combat states or attack motions");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 230)
    public static void completeExecutionPausesHealingAndRequiresFreshWarningAfterward(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        Player victim = helper.makeMockSurvivalPlayer();
        victim.moveTo(helper.absoluteVec(new Vec3(7.5, 2, 5.5)));
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        victim.setHealth(1000);
        AttackManager attacks = new AttackManager(apex);
        helper.runAfterDelay(80, () -> {
            apex.setCombatState(CombatState.SPECIAL);
            attacks.begin(AttackType.SPECIAL, victim);
        });
        for (int tick = 81; tick <= 174; tick++) {
            int elapsed = tick - 80;
            helper.runAfterDelay(tick, () -> {
                boolean complete = attacks.tick(AttackType.SPECIAL, victim, elapsed);
                near(helper, apex.getHealth(), 260, "Execution must not accumulate passive heals at elapsed " + elapsed);
                helper.assertTrue(apex.getHealingVisualState() == 0, "Execution must suppress ongoing healing cues");
                if (elapsed == AttackManager.EXECUTION_WARNING_TICKS + AttackManager.EXECUTION_END_TICK) {
                    helper.assertTrue(complete, "Execution must still finish at its original attack timing");
                    helper.assertTrue(victim.getHealth() < 1000, "The real Execution attack must still damage its target");
                    apex.setCombatState(CombatState.RECOVERY);
                    apex.playVisual("recovery", 8);
                }
            });
        }
        helper.runAfterDelay(177, () -> {
            near(helper, apex.getHealth(), 260, "Execution completion must not cash in skipped heals");
            helper.assertTrue(apex.getHealingVisualState() == 2, "Post-Execution recovery must show a new warning");
        });
        helper.runAfterDelay(193, () -> near(helper, apex.getHealth(), 260, "A full warning must precede resumed healing"));
        helper.runAfterDelay(197, () -> {
            near(helper, apex.getHealth(), 264, "Only one normal pulse may follow the fresh warning");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void staggerVisualPausesHealingWithoutEditingItsAnimation(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        helper.runAfterDelay(90, () -> apex.playVisual("stagger", 30));
        helper.runAfterDelay(110, () -> {
            near(helper, apex.getHealth(), 260, "Stagger must pause the pending heal");
            helper.assertTrue(apex.getHealingVisualState() == 0, "Stagger must clear the healing cue");
            helper.assertTrue(apex.getVisualAnimation().equals("stagger"), "Healing must preserve the stagger animation");
        });
        helper.runAfterDelay(122, () -> helper.assertTrue(apex.getHealingVisualState() == 2,
                "Stagger ending must restart a warning"));
        helper.runAfterDelay(139, () -> near(helper, apex.getHealth(), 260, "Resumed warning must not heal early"));
        helper.runAfterDelay(143, () -> {
            near(helper, apex.getHealth(), 264, "Recovery may resume after the fresh warning");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void nbtPreservesRemainingQuietDelayWithoutReplayingEntryHeal(GameTestHelper helper) {
        ApexEntity original = enterFinalPhase(helper);
        ApexEntity[] restored = {null};
        helper.runAfterDelay(40, () -> {
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            ApexEntity replacement = ApexRegistries.APEX.get().create(helper.getLevel());
            helper.assertTrue(replacement != null, "Entity factory must create the reload fixture");
            original.discard();
            replacement.load(saved);
            replacement.setNoAi(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(replacement), "Reloaded boss must join the server level");
            restored[0] = replacement;
            near(helper, replacement.getHealth(), 260, "Loading must not replay the entry heal");
            helper.assertTrue(replacement.getPhase() == 3, "Final phase must survive load");
        });
        helper.runAfterDelay(79, () -> near(helper, restored[0].getHealth(), 260, "Saved remaining quiet delay must survive load"));
        helper.runAfterDelay(99, () -> near(helper, restored[0].getHealth(), 260, "Reload must not skip the five-second deadline"));
        helper.runAfterDelay(103, () -> {
            near(helper, restored[0].getHealth(), 264, "Reload should preserve the remaining delay rather than replay the entry heal");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void legacyFinalPhaseNbtGetsAConservativeDelayAndNoEntryReplay(GameTestHelper helper) {
        ApexEntity apex = boss(helper);
        CompoundTag oldSave = apex.saveWithoutId(new CompoundTag());
        oldSave.putInt("ApexPhase", 3);
        oldSave.putFloat("Health", 180);
        oldSave.remove("ApexHealing");
        // Removing all healing metadata also models a genuine pre-healing 1.3.0 save.
        for (String key : java.util.Set.copyOf(oldSave.getAllKeys())) {
            if (key.startsWith("ApexHeal") || key.startsWith("ApexRecovery")) oldSave.remove(key);
        }
        apex.load(oldSave);
        apex.setNoAi(true);
        near(helper, apex.getHealth(), 180, "Legacy load must not grant a new entry heal");
        helper.runAfterDelay(79, () -> near(helper, apex.getHealth(), 180, "Legacy load must begin a conservative quiet delay"));
        helper.runAfterDelay(99, () -> near(helper, apex.getHealth(), 180, "Legacy load must not fast-forward recovery"));
        helper.runAfterDelay(103, () -> {
            near(helper, apex.getHealth(), 184, "Legacy final-phase save may heal after a full quiet delay");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void forgeHealingMultiplierCannotBypassPassiveCap(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        apex.setHealth(298);
        int[] events = {0};
        Consumer<LivingHealEvent> amplifier = event -> {
            if (event.getEntity() == apex) {
                events[0]++;
                event.setAmount(event.getAmount() * 10);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingHealEvent.class, amplifier);
        helper.runAfterDelay(103, () -> {
            try {
                helper.assertTrue(events[0] == 1, "Healing must fire Forge's ordinary event once per actual pulse");
                near(helper, apex.getHealth(), 300, "External heal multiplier must still respect the passive cap");
                near(helper, apex.getBossBarProgress(), .5, "Amplified event must not overflow BossBar or cap");
                helper.succeed();
            } finally {
                MinecraftForge.EVENT_BUS.unregister(amplifier);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void cancelledForgeHealingEventRestoresNoHealth(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        int[] events = {0};
        Consumer<LivingHealEvent> cancellation = event -> {
            if (event.getEntity() == apex) {
                events[0]++;
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingHealEvent.class, cancellation);
        helper.runAfterDelay(123, () -> {
            try {
                helper.assertTrue(events[0] == 2, "Canceled pulses must still obey the normal one-second cadence");
                near(helper, apex.getHealth(), 260, "A canceled heal event must not restore HP");
                near(helper, apex.getBossBarProgress(), 260.0 / 600, "Canceled healing must preserve BossBar health");
                helper.succeed();
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancellation);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void damageDuringForgeHealEventCancelsThatPulseImmediately(GameTestHelper helper) {
        ApexEntity apex = enterFinalPhase(helper);
        long[] interruptTick = {-1};
        Consumer<LivingHealEvent> interrupt = event -> {
            if (event.getEntity() == apex && interruptTick[0] < 0) {
                apex.invulnerableTime = 0;
                helper.assertTrue(apex.hurt(apex.damageSources().generic(), 1), "Reentrant interrupting hit must land");
                interruptTick[0] = helper.getLevel().getGameTime();
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingHealEvent.class, interrupt);
        helper.runAfterDelay(123, () -> {
            try {
                helper.assertTrue(interruptTick[0] >= 0, "The real Forge healing event must be exercised");
                near(helper, apex.getHealth(), 259, "A hit during the heal event must cancel that heal and later pulses");
                helper.assertTrue(apex.damageTracker().getLastDamageTick() == interruptTick[0],
                        "Reentrant hit must update the same actual-damage clock");
                helper.assertTrue(apex.getHealingVisualState() == 0, "Reentrant hit must stop the replicated recovery cue");
                helper.succeed();
            } finally {
                MinecraftForge.EVENT_BUS.unregister(interrupt);
            }
        });
    }

    private static ApexEntity enterFinalPhase(GameTestHelper helper) {
        ApexEntity apex = boss(helper);
        triggerFinalPhase(helper, apex);
        return apex;
    }

    private static void triggerFinalPhase(GameTestHelper helper, ApexEntity apex) {
        apex.setHealth(201);
        apex.invulnerableTime = 0;
        helper.assertTrue(apex.hurt(apex.damageSources().generic(), 1), "Final-phase trigger must cause real health loss");
        helper.assertTrue(apex.getPhase() == 3, "Fixture must really enter phase three");
    }

    private static ApexEntity boss(GameTestHelper helper) { return boss(helper, BOSS_POS); }

    private static ApexEntity boss(GameTestHelper helper, BlockPos position) {
        ApexEntity apex = helper.spawnWithNoFreeWill(ApexRegistries.APEX.get(), position);
        apex.setNoAi(true);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        helper.assertTrue(helper.getLevel().noCollision(apex, apex.getBoundingBox()), "Healing fixture must stand in clear air");
        return apex;
    }

    private static ApexEntity configuredBoss(GameTestHelper helper, Phase3HealingController.Settings settings) {
        ApexEntity apex = new ConfiguredApex(helper.getLevel(), settings);
        apex.moveTo(helper.absoluteVec(new Vec3(5.5, 2, 5.5)));
        apex.setNoAi(true);
        apex.getAttribute(Attributes.ARMOR).setBaseValue(0);
        helper.assertTrue(helper.getLevel().addFreshEntity(apex), "Configured test boss must enter the real server level");
        return apex;
    }

    private static void near(GameTestHelper helper, double actual, double expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < .001, description + ": expected " + expected + ", actual " + actual);
    }

    /** Override the real settings boundary without mutating global config during parallel GameTests. */
    private static final class ConfiguredApex extends ApexEntity {
        private final Phase3HealingController.Settings settings;
        private ConfiguredApex(Level level, Phase3HealingController.Settings settings) {
            super(ApexRegistries.APEX.get(), level);
            this.settings = settings;
        }
        @Override protected Phase3HealingController.Settings phase3HealingSettings() {
            return settings == null ? Phase3HealingController.Settings.defaults() : settings;
        }
    }
}
