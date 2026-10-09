package dev.apexpredator;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client preferences never affect the server's combat timing or damage. */
public final class ApexClientConfig {
    public enum ParticleQuality {
        LOW(.35), MEDIUM(.65), HIGH(1.0);
        public final double multiplier;
        ParticleQuality(double multiplier) { this.multiplier = multiplier; }
    }
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<ParticleQuality> PARTICLE_QUALITY;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE, AFTER_IMAGES, SCREEN_EFFECTS, BOSS_MUSIC;
    static {
        var b = new ForgeConfigSpec.Builder();
        PARTICLE_QUALITY = b.comment("Decorative particle density: LOW 35%, MEDIUM 65%, HIGH 100%. Attack warnings remain visible at every quality.").defineEnum("particleQuality", ParticleQuality.HIGH);
        CAMERA_SHAKE = b.comment("Short, distance-scaled camera shake. Does not change player aim on the server.").define("cameraShake", true);
        AFTER_IMAGES = b.comment("Short translucent silhouettes during fast movements.").define("afterImages", true);
        SCREEN_EFFECTS = b.comment("Brief edge glow for phase changes and major attacks; never changes game speed.").define("screenEffects", true);
        BOSS_MUSIC = b.comment("Loop The Stalking Motif during nearby Apex player encounters. Volume follows Master and Jukebox/Note Blocks (RECORDS).").define("bossMusic", true);
        SPEC = b.build();
    }
    private ApexClientConfig() { }
}
