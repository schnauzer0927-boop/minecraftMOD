package dev.apexpredator;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ApexConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue MAXIMUM_HIT_DAMAGE, BURST_THRESHOLD, REPOSITION_THRESHOLD, EVASION_HIT_DAMAGE;
    public static final ForgeConfigSpec.IntValue EVASION_TICKS;
    public static final ForgeConfigSpec.BooleanValue DEBUG;
    static {
        var b = new ForgeConfigSpec.Builder();
        MAXIMUM_HIT_DAMAGE = b.comment("Maximum incoming damage per hit. Same-projectile split hits share this budget. Normal armor still applies.").defineInRange("maximumHitDamage", 35.0, 1.0, 600.0);
        BURST_THRESHOLD = b.comment("Actual health lost in a rolling 20-tick window to trigger evasion.").defineInRange("burstDamageThreshold", 100.0, 1.0, 600.0);
        REPOSITION_THRESHOLD = b.defineInRange("repositionDamageThreshold", 75.0, 1.0, 600.0);
        EVASION_HIT_DAMAGE = b.comment("Temporary hit ceiling during the finite evasion mode. Repeated hits grant only 6 visibly signaled ticks of immunity; normal combat is unchanged.").defineInRange("evasionMaximumHitDamage", 14.0, 1.0, 35.0);
        EVASION_TICKS = b.comment("Finite evasion duration. Each visibly signaled immunity interval is limited to 6 ticks.").defineInRange("evasionTicks", 50, 40, 60);
        DEBUG = b.comment("Log combat diagnostics once per second.").define("debug", false);
        SPEC = b.build();
    }
    private ApexConfig() { }
}
