package dev.apexpredator.summoning;

import dev.apexpredator.item.ApexBeaconItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/** A ritual follows its Beacon through ordinary inventory moves, not the selected hotbar slot. */
final class BeaconRitualInventory {
    static final String TOKEN_KEY = "ApexBeaconRitual";

    private BeaconRitualInventory() { }

    static UUID token(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().hasUUID(TOKEN_KEY)
                ? stack.getTag().getUUID(TOKEN_KEY) : null;
    }

    static void mark(ItemStack stack, UUID token) {
        stack.getOrCreateTag().putUUID(TOKEN_KEY, token);
    }

    static void unmark(ItemStack stack, UUID token) {
        if (token.equals(token(stack))) stack.removeTagKey(TOKEN_KEY);
    }

    static Optional<ItemStack> find(ServerPlayer player, UUID token) {
        for (ItemStack stack : player.getInventory().items) if (matches(stack, token)) return Optional.of(stack);
        for (ItemStack stack : player.getInventory().offhand) if (matches(stack, token)) return Optional.of(stack);
        return Optional.empty();
    }

    static void clear(ServerPlayer player, UUID token) {
        for (ItemStack stack : player.getInventory().items) unmark(stack, token);
        for (ItemStack stack : player.getInventory().offhand) unmark(stack, token);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static boolean matches(ItemStack stack, UUID token) {
        return !stack.isEmpty() && stack.getItem() instanceof ApexBeaconItem && token.equals(token(stack));
    }
}
