package dev.apexpredator.effect;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Self-contained original samples, replaceable through ordinary resource packs. */
public final class ApexSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ApexPredator.MOD_ID);
    private static final Map<String, RegistryObject<SoundEvent>> EVENTS = new LinkedHashMap<>();
    static {
        for (String name : new String[]{"blade_swing", "blade_hit", "charge_start", "charge_dash", "slam", "shockwave", "teleport",
                "phase_change", "rage", "special_charge", "special_attack", "death", "boss_music"}) {
            EVENTS.put(name, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApexPredator.MOD_ID, name))));
        }
    }
    private ApexSounds() { }
    public static SoundEvent event(String name) {
        RegistryObject<SoundEvent> sound = EVENTS.get(name);
        if (sound == null) throw new IllegalArgumentException("Unknown Apex sound: " + name);
        return sound.get();
    }
    public static void play(ApexEntity apex, String name) { apex.playSound(event(name), name.startsWith("special") ? 1.8F : 1.2F, 1F); }
    public static void telegraph(ApexEntity apex) { play(apex, "charge_start"); }
    public static void attack(ApexEntity apex) { play(apex, "blade_swing"); }
    public static void warp(ApexEntity apex) { play(apex, "teleport"); }
    public static void phase(ApexEntity apex) { play(apex, apex.getPhase() >= 3 ? "rage" : "phase_change"); }
    public static void special(ApexEntity apex) { play(apex, "special_charge"); }
}
