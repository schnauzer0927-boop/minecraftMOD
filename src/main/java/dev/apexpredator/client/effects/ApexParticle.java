package dev.apexpredator.client.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Small emissive sprites; arcs and warning rings are actual ribbons rendered separately. */
final class ApexParticle extends TextureSheetParticle {
    enum Style { SLASH, ENERGY, SMOKE, SPARK, AFTERIMAGE, SHOCKWAVE, CORE }
    private final SpriteSet sprites;
    private final boolean emissive;
    private final float initialAlpha;

    ApexParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Style style) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        xd = vx; yd = vy; zd = vz;
        friction = .92F;
        hasPhysics = style == Style.SPARK;
        lifetime = switch (style) { case SMOKE -> 26; case SPARK -> 13; case AFTERIMAGE -> 8; case CORE -> 22; default -> 15; };
        quadSize = switch (style) { case SMOKE -> .42F; case SLASH -> .3F; case AFTERIMAGE -> .23F; case SHOCKWAVE -> .2F; default -> .105F; };
        emissive = style != Style.SMOKE;
        if (style == Style.SMOKE) { setColor(.10F, .09F, .08F); initialAlpha = .38F; }
        else { setColor(1F, style == Style.CORE ? .48F : .26F, .045F); initialAlpha = style == Style.AFTERIMAGE ? .32F : .88F; }
        alpha = initialAlpha;
        gravity = style == Style.SPARK ? .16F : -.012F;
        setSpriteFromAge(sprites);
    }
    @Override public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        alpha = initialAlpha * (1F - (float)age / lifetime);
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override public int getLightColor(float partialTick) { return emissive ? 0xF000F0 : super.getLightColor(partialTick); }

    record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new ApexParticle(level, x, y, z, vx, vy, vz, sprites, style);
        }
    }
}
