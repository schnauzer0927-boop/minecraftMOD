package dev.apexpredator.command;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.entity.AnimationState;
import dev.apexpredator.effect.ApexEffects;
import dev.apexpredator.effect.EffectKind;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID)
public final class ApexCommands {
    private ApexCommands() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        // Brigadier merges this literal with vanilla summon; namespaced summon remains available.
        event.getDispatcher().register(Commands.literal("summon").requires(s -> s.hasPermission(2))
                .then(Commands.literal("apexpredator")
                        .executes(c -> summon(c.getSource(), c.getSource().getPosition()))
                        .then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> summon(c.getSource(), Vec3Argument.getVec3(c, "pos"))))));
        event.getDispatcher().register(Commands.literal("apex").requires(s -> s.hasPermission(2))
                .then(Commands.literal("debug").executes(c -> {
                    var source = c.getSource();
                    var nearest = source.getLevel().getEntitiesOfClass(ApexEntity.class,
                            new net.minecraft.world.phys.AABB(source.getPosition(), source.getPosition()).inflate(96)).stream()
                            .min(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(source.getPosition())));
                    source.sendSuccess(() -> Component.literal(nearest.map(ApexEntity::debugSummary).orElse("No Apex within 96 blocks.")), false);
                    return nearest.isPresent() ? 1 : 0;
                }))
                .then(Commands.literal("animate").then(Commands.argument("animation", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(AnimationState.values())
                                        .filter(a -> a.ordinal() <= AnimationState.RAGE_ATTACK.ordinal())
                                        .map(a -> a.name().toLowerCase(java.util.Locale.ROOT)), builder))
                        .executes(c -> {
                            String animation = StringArgumentType.getString(c, "animation");
                            if (!AnimationState.hasAnimation(animation)) {
                                c.getSource().sendFailure(Component.literal("Unknown Apex animation: " + animation));
                                return 0;
                            }
                            var nearest = c.getSource().getLevel().getEntitiesOfClass(ApexEntity.class,
                                    new net.minecraft.world.phys.AABB(c.getSource().getPosition(), c.getSource().getPosition()).inflate(96)).stream()
                                    .min(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(c.getSource().getPosition())));
                            if (nearest.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No Apex within 96 blocks."));
                                return 0;
                            }
                            nearest.get().playVisual(animation, 60);
                            c.getSource().sendSuccess(() -> Component.literal("APEX animation: " + animation), false);
                            return 1;
                        })))
                .then(Commands.literal("effect").then(Commands.argument("effect", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(EffectKind.values()).map(k -> k.name().toLowerCase(java.util.Locale.ROOT)), builder))
                        .executes(c -> {
                            EffectKind kind;
                            try { kind = EffectKind.valueOf(StringArgumentType.getString(c, "effect").toUpperCase(java.util.Locale.ROOT)); }
                            catch (IllegalArgumentException e) {
                                c.getSource().sendFailure(Component.literal("Unknown Apex effect.")); return 0;
                            }
                            var nearest = c.getSource().getLevel().getEntitiesOfClass(ApexEntity.class,
                                    new net.minecraft.world.phys.AABB(c.getSource().getPosition(), c.getSource().getPosition()).inflate(96)).stream()
                                    .min(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(c.getSource().getPosition())));
                            if (nearest.isEmpty()) return 0;
                            ApexEntity apex = nearest.get();
                            float radius = switch (kind) {
                                case SPECIAL_PREPARE, SPECIAL_SLASH, SPECIAL_FINISH -> 15;
                                case PHASE_THREE, SHOCKWAVE -> 10;
                                case PHASE_TWO, SLAM, WARNING_RING -> 6;
                                default -> 3;
                            };
                            ApexEffects.emit(apex, kind, apex.position(), apex.getLookAngle(), radius, 60);
                            c.getSource().sendSuccess(() -> Component.literal("APEX effect: " + kind.name()), false);
                            return 1;
                        }))));
    }
    private static int summon(net.minecraft.commands.CommandSourceStack source, Vec3 position) {
        var entity = ApexRegistries.APEX.get().create(source.getLevel());
        if (entity == null || !net.minecraft.world.level.Level.isInSpawnableBounds(net.minecraft.core.BlockPos.containing(position))) return 0;
        entity.moveTo(position.x, position.y, position.z, source.getRotation().y, 0);
        source.getLevel().addFreshEntity(entity);
        source.sendSuccess(() -> Component.literal("Summoned APEX"), true);
        return 1;
    }
}
