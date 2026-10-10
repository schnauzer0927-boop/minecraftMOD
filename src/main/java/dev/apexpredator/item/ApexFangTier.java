package dev.apexpredator.item;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/** Netherite-compatible maintenance with ordinary sword enchanting. */
public enum ApexFangTier implements Tier {
    INSTANCE;

    @Override public int getUses() { return 3000; }
    @Override public float getSpeed() { return 9; }
    @Override public float getAttackDamageBonus() { return 5; }
    @Override public int getLevel() { return 4; }
    @Override public int getEnchantmentValue() { return 15; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.NETHERITE_INGOT); }
}
