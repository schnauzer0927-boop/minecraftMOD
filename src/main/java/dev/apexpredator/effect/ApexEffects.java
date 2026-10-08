package dev.apexpredator.effect;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ApexEffects {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ApexPredator.MOD_ID);
    public static final RegistryObject<SimpleParticleType> SLASH = particle("apex_slash"), ENERGY = particle("apex_energy"),
            SMOKE = particle("apex_smoke"), SPARK = particle("apex_spark"), AFTERIMAGE = particle("apex_afterimage"),
            SHOCKWAVE = particle("apex_shockwave"), CORE = particle("apex_core");
    // Recovery adds entity data and effect kinds; require the updated mod on both sides.
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(ApexPredator.MOD_ID, "effects"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean initialized;
    private ApexEffects() { }
    private static RegistryObject<SimpleParticleType> particle(String name) { return PARTICLES.register(name, () -> new SimpleParticleType(false)); }
    public static void register(IEventBus bus) { PARTICLES.register(bus); ApexSounds.SOUNDS.register(bus); }
    public static void initNetwork() {
        if (initialized) return;
        initialized = true;
        CHANNEL.messageBuilder(ApexEffectPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ApexEffectPacket::encode).decoder(ApexEffectPacket::decode)
                .consumerMainThread((packet, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.apexpredator.client.effects.ApexClientEffects.accept(packet));
                    context.get().setPacketHandled(true);
                }).add();
    }
    public static void emit(ApexEntity apex, EffectKind kind, Vec3 origin, Vec3 direction, float radius, int durationTicks) { emit((Entity)apex, kind, origin, direction, radius, durationTicks); }
    public static void emit(Entity entity, EffectKind kind, Vec3 origin, Vec3 direction, float radius, int durationTicks) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        ApexEffectPacket packet = new ApexEffectPacket(entity.getId(), kind, origin, direction, radius, durationTicks);
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(origin.x, origin.y, origin.z, 80, level.dimension())), packet);
    }
    public static void projectileImpact(Entity projectile) { emit(projectile, EffectKind.PROJECTILE_IMPACT, projectile.position(), projectile.getDeltaMovement(), 2.2F, 14); }
}
