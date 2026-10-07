package dev.apexpredator;

import net.minecraftforge.fml.common.Mod;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import dev.apexpredator.compat.TaczCompat;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(ApexPredator.MOD_ID)
public final class ApexPredator {
    public static final String MOD_ID = "apexpredator";
    public ApexPredator() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ApexRegistries.ENTITIES.register(bus);
        ApexRegistries.ITEMS.register(bus);
        bus.addListener(ApexRegistries::attributes);
        bus.addListener(this::creativeItems);
        bus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ApexConfig.SPEC);
    }
    private void commonSetup(FMLCommonSetupEvent event) { event.enqueueWork(TaczCompat::init); }
    private void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(ApexRegistries.APEX_SPAWN_EGG);
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(ApexRegistries.APEX_CORE);
    }
}
