package dev.apexpredator.gametest;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ApexPredator.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommandGameTests {
    private CommandGameTests() { }
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void shortAndNamespacedSummonCommandsWork(GameTestHelper helper) {
        Vec3 point = helper.absoluteVec(new Vec3(5, 2, 5));
        var source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPosition(point).withPermission(2);
        var commands = helper.getLevel().getServer().getCommands();
        helper.assertTrue(commands.performPrefixedCommand(source, "summon apexpredator") == 1,
                "The requested /summon apexpredator alias must succeed");
        helper.assertTrue(commands.performPrefixedCommand(source, "summon apexpredator:apex ~2 ~ ~") == 1,
                "The standard namespaced summon must remain available");
        helper.runAfterDelay(3, () -> {
            var entities = helper.getLevel().getEntitiesOfClass(ApexEntity.class,
                    new net.minecraft.world.phys.AABB(point, point).inflate(4, 4, 4));
            helper.assertTrue(entities.size() == 2, "Both summon commands must create actual registered entities");
            helper.assertTrue(entities.stream().allMatch(e -> e.getMaxHealth() == 600), "Summoned entities must have boss attributes");
            helper.assertTrue(commands.performPrefixedCommand(source, "apex debug") == 1,
                    "The debug command must find a nearby boss");
            helper.succeed();
        });
    }
}
