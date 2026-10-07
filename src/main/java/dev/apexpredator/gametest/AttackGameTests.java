package dev.apexpredator.gametest;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.combat.AttackManager;
import dev.apexpredator.combat.AttackType;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/** Server integration checks for telegraphs, genuine counterplay, and mod-safe warps. */
@GameTestHolder(ApexPredator.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AttackGameTests {
    private AttackGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void chargeHasFourteenTickWindup(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 3, 2, 6);
        Pig victim = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(5, 2, 6));
        AttackManager attacks = new AttackManager(apex);
        attacks.begin(AttackType.CHARGE, victim);
        float initialHealth = victim.getHealth();
        for (int tick = 0; tick < 14; tick++) {
            helper.assertFalse(attacks.tick(AttackType.CHARGE, victim, tick), "Charge completed during windup");
            helper.assertTrue(victim.getHealth() == initialHealth, "Charge damaged target before its telegraph ended");
            helper.assertTrue(apex.getDeltaMovement().horizontalDistanceSqr() == 0,
                    "Charge moved before its telegraph ended");
        }
        attacks.tick(AttackType.CHARGE, victim, 14);
        helper.assertTrue(victim.getHealth() < initialHealth, "Committed dash failed to hit its close, visible target: "
                + diagnostics(helper, apex, victim));
        helper.assertTrue(apex.getDeltaMovement().horizontalDistanceSqr() > 1,
                "Committed dash failed to accelerate");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void specialKeepsCommittedDirection(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 3, 2, 5);
        Pig victim = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(9, 2, 5));
        AttackManager attacks = new AttackManager(apex);
        attacks.begin(AttackType.SPECIAL, victim);
        float initialHealth = victim.getHealth();
        for (int tick = 0; tick < 50; tick++) {
            attacks.tick(AttackType.SPECIAL, victim, tick);
            helper.assertTrue(victim.getHealth() == initialHealth, "Special damaged a target during its 50-tick telegraph");
        }
        // The target leaves the marked cone after Apex has committed to its direction.
        Vec3 escape = helper.absoluteVec(new Vec3(9, 2, 9));
        victim.setPos(escape.x, escape.y, escape.z);
        attacks.tick(AttackType.SPECIAL, victim, 50);
        helper.assertTrue(victim.getHealth() == initialHealth, "Special tracked a target outside its committed cone");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void specialRespectsSolidCover(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 3, 2, 6);
        Pig victim = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(9, 2, 6));
        AttackManager attacks = new AttackManager(apex);
        for (int z = 5; z <= 7; z++) {
            for (int y = 2; y <= 5; y++) helper.setBlock(6, y, z, Blocks.STONE);
        }
        float initialHealth = victim.getHealth();
        attacks.begin(AttackType.SPECIAL, victim);
        attacks.tick(AttackType.SPECIAL, victim, 50);
        helper.assertTrue(victim.getHealth() == initialHealth, "Special passed through solid cover");
        for (int z = 5; z <= 7; z++) {
            for (int y = 2; y <= 5; y++) helper.setBlock(6, y, z, Blocks.AIR);
        }
        attacks.begin(AttackType.SPECIAL, victim);
        attacks.tick(AttackType.SPECIAL, victim, 50);
        helper.assertTrue(victim.getHealth() < initialHealth, "Special failed to damage an exposed target in its cone: "
                + diagnostics(helper, apex, victim));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void warpRespectsCancellationAndSafeDestination(GameTestHelper helper) {
        ApexEntity apex = boss(helper, 3, 2, 6);
        Pig victim = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(6, 2, 6));
        AttackManager attacks = new AttackManager(apex);
        Vec3 initial = apex.position();
        Consumer<EntityTeleportEvent.EnderEntity> deny = event -> {
            if (event.getEntity() == apex) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTeleportEvent.EnderEntity.class, deny);
        try {
            helper.assertFalse(attacks.reposition(victim), "Warp ignored a Forge teleport cancellation");
            helper.assertTrue(apex.position().equals(initial), "Canceled warp changed position");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);
        }
        helper.assertTrue(attacks.reposition(victim), "Warp could not find a safe position on a clear stone platform");
        helper.assertTrue(apex.position().distanceToSqr(initial) <= 14 * 14, "Short-range warp exceeded its range");
        helper.assertTrue(helper.getLevel().noCollision(apex, apex.getBoundingBox()), "Warp destination intersects blocks");
        helper.assertFalse(helper.getLevel().containsAnyLiquid(apex.getBoundingBox()), "Warp destination contains fluid");
        BlockPos below = apex.blockPosition().below();
        helper.assertTrue(helper.getLevel().getBlockState(below).is(Blocks.STONE), "Warp destination has no supporting floor");
        helper.succeed();
    }

    private static ApexEntity boss(GameTestHelper helper, double x, double y, double z) {
        // GameTest structure loading offsets the template by (0, 1, 0) from the helper's origin.
        // The template's stone floor at y=0 occupies helper-relative y=1; feet belong at y=2.
        ApexEntity apex = helper.spawnWithNoFreeWill(ApexRegistries.APEX.get(), new Vec3(x, y, z));
        apex.setNoAi(true);
        helper.assertTrue(helper.getLevel().noCollision(apex, apex.getBoundingBox()),
                "Boss fixture intersects the arena: " + apex.position());
        helper.assertTrue(helper.getLevel().getBlockState(apex.blockPosition().below()).is(Blocks.STONE),
                "Boss fixture must stand directly above the template's stone floor");
        return apex;
    }

    private static String diagnostics(GameTestHelper helper, ApexEntity apex, Pig victim) {
        Vec3 source = apex.position().add(0, 1.2, 0);
        var clip = helper.getLevel().clip(new ClipContext(source, victim.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, apex));
        var path = apex.getBoundingBox().expandTowards(apex.getDeltaMovement()).inflate(0.35, 0.2, 0.35);
        return "boss=" + apex.position() + " victim=" + victim.position() + " hp=" + victim.getHealth()
                + " LOS=" + apex.hasLineOfSight(victim) + " invulnerable=" + victim.isInvulnerable()
                + " valid=" + victim.isAlive() + "," + victim.isSpectator() + "," + victim.isRemoved()
                + " coneRay=" + clip.getType() + "," + clip.getBlockPos() + " path=" + path
                + " victimBox=" + victim.getBoundingBox() + " intersection=" + path.intersects(victim.getBoundingBox())
                + " query=" + helper.getLevel().getEntitiesOfClass(Pig.class, path).size()
                + " entities=" + helper.getLevel().getEntitiesOfClass(Pig.class, apex.getBoundingBox().inflate(18)).size();
    }
}
