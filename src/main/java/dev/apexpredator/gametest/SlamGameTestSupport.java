package dev.apexpredator.gametest;

import com.mojang.logging.LogUtils;
import dev.apexpredator.combat.AttackManager;
import dev.apexpredator.combat.AttackType;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** Real server travel, collision and gravity, with attack selection isolated from a committed slam. */
final class SlamGameTestSupport {
    private SlamGameTestSupport() { }

    static ApexEntity boss(GameTestHelper helper, Vec3 relativeFeet) {
        ApexEntity apex = new PhysicsApex(helper.getLevel());
        apex.moveTo(helper.absoluteVec(relativeFeet));
        apex.setNoAi(false);
        helper.assertTrue(helper.getLevel().noCollision(apex, apex.getBoundingBox()),
                "Physics fixture must start outside solid terrain: " + apex.position());
        helper.assertTrue(helper.getLevel().addFreshEntity(apex), "Physics fixture failed to enter the server level");
        return apex;
    }

    static AttackManager run(GameTestHelper helper, ApexEntity apex, LivingEntity target,
                             Consumer<AttackManager> atLanding) {
        AttackManager attacks = new AttackManager(apex);
        attacks.begin(AttackType.JUMP, target);
        Vec3 committed = attacks.getSlamTarget();
        helper.assertTrue(committed != null, "Jump must commit a ground target before showing its warning");
        Vec3 start = apex.position();
        int[] elapsed = {0};
        boolean[] finished = {false};
        boolean[] airborne = {false};
        helper.onEachTick(() -> {
            if (finished[0]) return;
            int tick = elapsed[0]++;
            boolean complete = attacks.tick(AttackType.JUMP, target, tick);
            helper.assertTrue(attacks.getSlamTarget().equals(committed),
                    "A committed slam target changed during actual server travel");
            if (!apex.onGround() && apex.getY() > start.y + .15) airborne[0] = true;
            if (tick == AttackType.JUMP.telegraphTicks()) {
                helper.assertTrue(apex.getDeltaMovement().y > 0, "Jump never launched after its original windup");
            }
            if (complete) {
                finished[0] = true;
                helper.assertTrue(airborne[0], "A slam completed without genuine airborne server travel");
                helper.assertTrue(attacks.getActualLanding() != null,
                        "Jump timed out without actual collision-backed ground contact: " + attacks.debugSummary(target));
                helper.assertTrue(apex.onGround(), "Landing callback fired while the entity was still airborne");
                helper.assertTrue(attacks.getLandingError() < .5,
                        "Actual landing missed the warning center by half a block: " + attacks.debugSummary(target));
                helper.assertTrue(committed.equals(attacks.getDamageCenter()),
                        "Landing damage used the body position instead of the committed warning center");
                helper.assertTrue(apex.getVisualAnimation().equals("slam"), "Actual landing did not publish the existing slam clip");
                LogUtils.getLogger().info("[APEX SLAM TEST] {}", attacks.debugSummary(target));
                atLanding.accept(attacks);
                helper.succeed();
            }
        });
        return attacks;
    }

    /** NoAi suppresses vanilla travel itself; disable scheduling through these hooks instead. */
    private static final class PhysicsApex extends ApexEntity {
        private PhysicsApex(Level level) { super(ApexRegistries.APEX.get(), level); }
        @Override protected void registerGoals() { }
        @Override protected void customServerAiStep() { }
    }
}
