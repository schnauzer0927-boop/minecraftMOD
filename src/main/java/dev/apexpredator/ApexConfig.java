package dev.apexpredator;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ApexConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue MAXIMUM_HIT_DAMAGE, BURST_THRESHOLD, REPOSITION_THRESHOLD, EVASION_HIT_DAMAGE;
    public static final ForgeConfigSpec.IntValue EVASION_TICKS;
    public static final ForgeConfigSpec.BooleanValue DEBUG;
    public static final ForgeConfigSpec.BooleanValue DESTROY_TERRAIN;
    public static final ForgeConfigSpec.BooleanValue PHASE3_HEALING;
    public static final ForgeConfigSpec.DoubleValue PHASE3_ENTRY_HEAL, PHASE3_HEAL_PER_SECOND, PHASE3_HEAL_CAP;
    public static final ForgeConfigSpec.IntValue PHASE3_HEAL_DELAY_SECONDS;
    static {
        var b = new ForgeConfigSpec.Builder();
        MAXIMUM_HIT_DAMAGE = b.comment("Maximum incoming damage per hit. Same-projectile split hits share this budget. Normal armor still applies.").defineInRange("maximumHitDamage", 35.0, 1.0, 600.0);
        BURST_THRESHOLD = b.comment("Actual health lost in a rolling 20-tick window to trigger evasion.").defineInRange("burstDamageThreshold", 100.0, 1.0, 600.0);
        REPOSITION_THRESHOLD = b.defineInRange("repositionDamageThreshold", 75.0, 1.0, 600.0);
        EVASION_HIT_DAMAGE = b.comment("Temporary hit ceiling during the finite evasion mode. Repeated hits grant only 6 visibly signaled ticks of immunity; normal combat is unchanged.").defineInRange("evasionMaximumHitDamage", 14.0, 1.0, 35.0);
        EVASION_TICKS = b.comment("Finite evasion duration. Each visibly signaled immunity interval is limited to 6 ticks.").defineInRange("evasionTicks", 50, 40, 60);
        DEBUG = b.comment("Log combat diagnostics once per second.").define("debug", false);
        DESTROY_TERRAIN = b.comment("Allow a slam to break a few soft surface blocks. Default false. Also requires mobGriefing and respects Forge block protection events.").define("destroyTerrain", false);
        PHASE3_HEALING = b.comment("Enable both the once-only Phase 3 entry heal and interruptible passive recovery.").define("phase3Healing", true);
        PHASE3_ENTRY_HEAL = b.comment("Health restored on a real Phase 3 transition, limited by maximum health. Loading an existing Phase 3 boss never repeats it.").defineInRange("phase3EntryHeal", 60.0, 0.0, 600.0);
        PHASE3_HEAL_DELAY_SECONDS = b.comment("Seconds without actual health loss before the first passive heal. The final second is a visible warning; resumed recovery always gives a fresh warning.").defineInRange("phase3HealDelaySeconds", 5, 0, 600);
        PHASE3_HEAL_PER_SECOND = b.comment("Passive health restored every 20 ticks. Recovery continues during ordinary combat and pauses during execution, stagger and phase changes.").defineInRange("phase3HealPerSecond", 4.0, 0.0, 600.0);
        PHASE3_HEAL_CAP = b.comment("Passive Phase 3 recovery cannot raise health above this value or maximum health. This cap does not apply to the once-only entry heal.").defineInRange("phase3HealCap", 300.0, 0.0, 600.0);
        SPEC = b.build();
    }
    private ApexConfig() { }
}
