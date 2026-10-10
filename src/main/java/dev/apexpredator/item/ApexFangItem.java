package dev.apexpredator.item;

import dev.apexpredator.weapon.PredatorWeaponAbilities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A conventional sword with independent server-owned dash and slash cooldowns. */
public final class ApexFangItem extends SwordItem {
    public ApexFangItem(Properties properties) {
        // Sword modifier 10 + tier bonus 5 + player base damage 1 = 16.
        // Player attack-speed base 4 - 2.4 = 1.6 attacks per second.
        super(ApexFangTier.INSTANCE, 10, -2.4F, properties.fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return player.getCooldowns().isOnCooldown(this)
                    ? InteractionResultHolder.fail(stack) : InteractionResultHolder.success(stack);
        }
        return PredatorWeaponAbilities.tryActivate(player, hand, player.isShiftKeyDown())
                ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (result && attacker instanceof Player player && !attacker.level().isClientSide) {
            PredatorWeaponAbilities.normalHitEffect(player, target);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.apexpredator.apex_fang").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.apexpredator.apex_fang.dash").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.apexpredator.apex_fang.slash").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.apexpredator.apex_fang.cooldown").withStyle(ChatFormatting.DARK_GRAY));
    }
}
