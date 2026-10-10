package dev.apexpredator.item;

import dev.apexpredator.summoning.ApexSummoningRitual;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import javax.annotation.Nullable;

/** Begins a server-owned ritual; payment is deferred until Apex has actually spawned. */
public final class ApexBeaconItem extends Item {
    public ApexBeaconItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.apexpredator.beacon.unsafe_ground"), true);
            }
            return InteractionResult.FAIL;
        }
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        var result = ApexSummoningRitual.begin(player, context.getHand(), context.getClickedPos());
        player.displayClientMessage(Component.translatable(result.translationKey()), true);
        return result == ApexSummoningRitual.BeginResult.STARTED ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.apexpredator.apex_beacon.ritual").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.apexpredator.apex_beacon.danger").withStyle(ChatFormatting.DARK_RED));
    }
}
