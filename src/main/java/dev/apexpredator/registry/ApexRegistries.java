package dev.apexpredator.registry;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.entity.ApexBoltEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ApexRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ApexPredator.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ApexPredator.MOD_ID);
    public static final RegistryObject<EntityType<ApexEntity>> APEX = ENTITIES.register("apex", () -> EntityType.Builder.of(ApexEntity::new, MobCategory.MONSTER)
            .sized(1.4F, 3.8F).clientTrackingRange(96).updateInterval(1).fireImmune().build("apexpredator:apex"));
    public static final RegistryObject<EntityType<ApexBoltEntity>> APEX_BOLT = ENTITIES.register("apex_bolt", () -> EntityType.Builder.<ApexBoltEntity>of(ApexBoltEntity::new, MobCategory.MISC)
            .sized(.35F, .35F).clientTrackingRange(64).updateInterval(1).build("apexpredator:apex_bolt"));
    public static final RegistryObject<Item> APEX_CORE = ITEMS.register("apex_core", () -> new Item(new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> APEX_SPAWN_EGG = ITEMS.register("apex_spawn_egg", () -> new ForgeSpawnEggItem(APEX, 0x181825, 0xDC3448, new Item.Properties()));
    private ApexRegistries() { }
    public static void attributes(EntityAttributeCreationEvent event) { event.put(APEX.get(), ApexEntity.createAttributes().build()); }
}
