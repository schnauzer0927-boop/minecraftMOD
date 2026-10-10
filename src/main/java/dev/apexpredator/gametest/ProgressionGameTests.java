package dev.apexpredator.gametest;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.registry.ApexRegistries;
import dev.apexpredator.summoning.ApexSummoningRitual;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Consumer;

/** Crafting-table, loot and timed summoning checks through real registered items on Forge. */
@GameTestHolder(ApexPredator.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressionGameTests {
    private ProgressionGameTests() { }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 40)
    public static void beaconCraftsFromTheSpecifiedNineSlotRecipeAndConsumesItsIngredients(GameTestHelper helper) {
        try (ProgressionGameTestArena arena = new ProgressionGameTestArena(helper)) {
            ServerPlayer player = arena.player(false, 3.5, 8.5);
            Item n = Items.NETHERITE_INGOT, e = Items.ENDER_EYE, c = Items.ECHO_SHARD;
            craft(helper, arena, player, new Item[]{n, e, n, c, Items.NETHER_STAR, c,
                    Items.CRYING_OBSIDIAN, e, Items.CRYING_OBSIDIAN}, ApexRegistries.APEX_BEACON.get());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 40)
    public static void fangCraftsFromFourCoresTwoIngotsOneSwordAndTwoRods(GameTestHelper helper) {
        try (ProgressionGameTestArena arena = new ProgressionGameTestArena(helper)) {
            ServerPlayer player = arena.player(false, 3.5, 8.5);
            Item c = ApexRegistries.APEX_CORE.get(), n = Items.NETHERITE_INGOT;
            craft(helper, arena, player, new Item[]{c, n, c, c, Items.NETHERITE_SWORD, c,
                    Items.BLAZE_ROD, n, Items.BLAZE_ROD}, ApexRegistries.APEX_FANG.get());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 40)
    public static void anActualBossKillDropsExactlyOneCoreEvenWithLootingThree(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.enchant(Enchantments.MOB_LOOTING, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        ApexEntity apex = ApexRegistries.APEX.get().create(arena.level);
        helper.assertTrue(apex != null, "Apex registry factory failed");
        apex.moveTo(arena.point(8.5, 0, 8.5));
        apex.setNoAi(true);
        apex.setHealth(1);
        helper.assertTrue(arena.level.addFreshEntity(apex), "Boss fixture failed to enter its arena");
        arena.own(apex);
        helper.assertTrue(apex.hurt(apex.damageSources().playerAttack(player), 100), "Player kill must reach real entity loot handling");
        helper.runAfterDelay(2, () -> {
            int count = arena.level.getEntitiesOfClass(ItemEntity.class, arena.bounds()).stream()
                    .filter(entity -> entity.getItem().is(ApexRegistries.APEX_CORE.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(count == 1, "Looting III changed the guaranteed one-Core drop: " + count);
            arena.succeed(helper);
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 140)
    public static void survivalBeaconSpawnsOnlyAfterFiveSecondsAndConsumesExactlyItsReservedItem(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack beacon = equipBeacon(player);
        ItemStack spare = new ItemStack(ApexRegistries.APEX_BEACON.get());
        player.getInventory().setItem(5, spare);
        helper.runAfterDelay(4, () -> {
            helper.assertFalse(player.isCreative(), "Survival fixture must use real survival rules");
            start(helper, arena, player);
            helper.runAfterDelay(99, () -> {
                helper.assertTrue(bosses(arena).isEmpty(), "Beacon spawned Apex before five seconds elapsed");
                helper.assertTrue(beacon.getCount() == 1 && spare.getCount() == 1, "Beacon paid its cost during the warning");
            });
            helper.runAfterDelay(102, () -> {
                List<ApexEntity> bosses = bosses(arena);
                helper.assertTrue(bosses.size() == 1, "Completed ritual must add exactly one real Apex");
                helper.assertTrue(beacon.isEmpty() && spare.getCount() == 1, "Ritual consumed an unreserved Beacon or failed to pay");
                helper.assertTrue(bosses.get(0).getTarget() == player, "Survival ritual must target its actual summoner");
                helper.assertTrue(ApexSummoningRitual.status(arena.level, player.getUUID()).isEmpty(), "Completed ritual was not cleaned up");
                arena.succeed(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 140)
    public static void creativeBeaconSpawnsWithoutConsumingTheItem(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(true, 3.5, 8.5);
        ItemStack beacon = equipBeacon(player);
        helper.runAfterDelay(4, () -> {
            start(helper, arena, player);
            helper.runAfterDelay(102, () -> {
                helper.assertTrue(bosses(arena).size() == 1, "Creative ritual failed to create Apex");
                helper.assertTrue(beacon.getCount() == 1, "Creative ritual consumed its Beacon");
                helper.assertTrue(bosses(arena).get(0).getTarget() == null, "Creative summoner must not become a combat target");
                arena.succeed(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 140)
    public static void reservedBeaconCanMoveInventorySlotsAndTheSummonerCanSwitchItems(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack reserved = equipBeacon(player);
        helper.runAfterDelay(4, () -> {
            start(helper, arena, player);
            helper.runAfterDelay(20, () -> {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                player.getInventory().setItem(7, reserved);
                helper.assertTrue(ApexSummoningRitual.status(arena.level, player.getUUID()).isPresent(),
                        "Switching hands/slots canceled a still-owned reserved Beacon");
            });
            helper.runAfterDelay(102, () -> {
                helper.assertTrue(bosses(arena).size() == 1, "Slot movement interrupted a valid ritual");
                helper.assertTrue(reserved.isEmpty() && player.getMainHandItem().is(Items.IRON_SWORD),
                        "Payment failed to find the reserved inventory item or consumed the replacement sword");
                arena.succeed(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 40)
    public static void aLivingNearbyApexRejectsASecondRitualWithoutPayment(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack beacon = equipBeacon(player);
        ApexEntity existing = ApexRegistries.APEX.get().create(arena.level);
        helper.assertTrue(existing != null, "Boss registry factory failed");
        existing.moveTo(arena.point(12.5, 0, 8.5));
        existing.setNoAi(true);
        arena.level.addFreshEntity(existing);
        arena.own(existing);
        helper.runAfterDelay(4, () -> {
            helper.assertFalse(useBeacon(arena, player).consumesAction(), "Beacon ignored a living boss within 64 blocks");
            helper.assertTrue(beacon.getCount() == 1 && ApexSummoningRitual.status(arena.level, player.getUUID()).isEmpty(),
                    "Rejected duplicate boss ritual consumed or reserved a Beacon");
            arena.succeed(helper);
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 40)
    public static void aPendingNearbyRitualRejectsAnotherSummoner(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer first = arena.player(false, 3.5, 8.5), second = arena.player(false, 4.5, 8.5);
        equipBeacon(first);
        ItemStack rejected = equipBeacon(second);
        helper.runAfterDelay(4, () -> {
            start(helper, arena, first);
            helper.assertFalse(useBeacon(arena, second).consumesAction(), "Nearby ritual allowed another concurrent summoner");
            helper.assertTrue(rejected.getCount() == 1 && ApexSummoningRitual.status(arena.level, second.getUUID()).isEmpty(),
                    "Rejected concurrent ritual consumed or reserved the second player's item");
            arena.succeed(helper);
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 40)
    public static void aBlockedSpawnVolumeRejectsTheBeaconWithoutPayment(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack beacon = equipBeacon(player);
        arena.level.setBlock(arena.block(8, 2, 8), Blocks.STONE.defaultBlockState(), 2);
        helper.runAfterDelay(4, () -> {
            helper.assertFalse(useBeacon(arena, player).consumesAction(), "Apex's 2.7-block body fitted through a two-block ceiling");
            helper.assertTrue(beacon.getCount() == 1 && bosses(arena).isEmpty(), "Blocked placement consumed or spawned anyway");
            arena.succeed(helper);
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 140)
    public static void destroyingTheClickedFloorDuringTheWarningCancelsWithoutPayment(GameTestHelper helper) {
        canceledRitual(helper, (arena, player) -> arena.level.setBlock(arena.block(8, -1, 8), Blocks.AIR.defaultBlockState(), 2));
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 140)
    public static void losingTheReservedBeaconDuringTheWarningCancelsWithoutSpawn(GameTestHelper helper) {
        canceledRitual(helper, (arena, player) -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 140)
    public static void loggingOutDuringTheWarningCancelsWithoutSpawnOrPayment(GameTestHelper helper) {
        canceledRitual(helper, (arena, player) -> {
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            player.discard();
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 140)
    public static void theForgeDimensionChangeEventCancelsTheOriginalWorldRitual(GameTestHelper helper) {
        canceledRitual(helper, (arena, player) -> MinecraftForge.EVENT_BUS.post(
                new PlayerEvent.PlayerChangedDimensionEvent(player, arena.level.dimension(), Level.NETHER)));
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 140)
    public static void aForgeCanceledBossJoinDoesNotConsumeTheReservedBeacon(GameTestHelper helper) {
        rejectedFinalJoin(helper, true);
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 140)
    public static void terrainChangedByABossJoinCallbackRollsBackTheSpawnAndPayment(GameTestHelper helper) {
        rejectedFinalJoin(helper, false);
    }

    private static void rejectedFinalJoin(GameTestHelper helper, boolean cancelJoin) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack reserved = equipBeacon(player);
        boolean[] observedJoin = {false};
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getLevel() != arena.level || !(event.getEntity() instanceof ApexEntity)
                    || !arena.bounds().contains(event.getEntity().position())) return;
            observedJoin[0] = true;
            if (cancelJoin) event.setCanceled(true);
            else arena.level.setBlock(arena.block(8, -1, 8), Blocks.AIR.defaultBlockState(), 2);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, listener);
        arena.onClose(() -> MinecraftForge.EVENT_BUS.unregister(listener));
        helper.runAfterDelay(4, () -> {
            start(helper, arena, player);
            helper.runAfterDelay(102, () -> {
                helper.assertTrue(observedJoin[0], "Fixture never exercised the actual Forge boss join callback");
                helper.assertTrue(bosses(arena).isEmpty(), "Rejected final placement left a live boss in the world");
                helper.assertTrue(reserved.getCount() == 1, "Rejected final placement consumed its reserved Beacon");
                helper.assertTrue(ApexSummoningRitual.status(arena.level, player.getUUID()).isEmpty(),
                        "Rejected final placement retained the ritual state");
                arena.succeed(helper);
            });
        });
    }

    private static void canceledRitual(GameTestHelper helper, java.util.function.BiConsumer<ProgressionGameTestArena, ServerPlayer> cancel) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack reserved = equipBeacon(player);
        helper.runAfterDelay(4, () -> {
            start(helper, arena, player);
            helper.runAfterDelay(20, () -> cancel.accept(arena, player));
            helper.runAfterDelay(24, () -> helper.assertTrue(ApexSummoningRitual.status(arena.level, player.getUUID()).isEmpty(),
                    "Invalid ritual remained active after its cancellation condition"));
            helper.runAfterDelay(102, () -> {
                helper.assertTrue(bosses(arena).isEmpty(), "Canceled warning still created Apex after its original deadline");
                helper.assertTrue(reserved.getCount() == 1, "Canceled ritual consumed the reserved Beacon");
                arena.succeed(helper);
            });
        });
    }

    private static ItemStack equipBeacon(ServerPlayer player) {
        ItemStack beacon = new ItemStack(ApexRegistries.APEX_BEACON.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, beacon);
        return beacon;
    }

    private static void start(GameTestHelper helper, ProgressionGameTestArena arena, ServerPlayer player) {
        helper.assertTrue(useBeacon(arena, player).consumesAction(), "Registered Beacon useOn failed on a clear supported platform");
        var state = ApexSummoningRitual.status(arena.level, player.getUUID());
        helper.assertTrue(state.isPresent() && state.get().duration() == 100, "UseOn did not establish the five-second server ritual");
        helper.assertTrue(state.get().center().equals(arena.point(8.5, 0, 8.5)), "Ritual did not commit the clicked surface center");
        helper.assertTrue(bosses(arena).isEmpty(), "Beacon spawned immediately instead of warning first");
    }

    private static InteractionResult useBeacon(ProgressionGameTestArena arena, ServerPlayer player) {
        var clicked = arena.block(8, -1, 8);
        var hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, .5, 0), Direction.UP, clicked, false);
        ItemStack held = player.getMainHandItem();
        return held.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static List<ApexEntity> bosses(ProgressionGameTestArena arena) {
        return arena.level.getEntitiesOfClass(ApexEntity.class, arena.bounds(), ApexEntity::isAlive);
    }

    private static void craft(GameTestHelper helper, ProgressionGameTestArena arena, ServerPlayer player,
                              Item[] ingredients, Item result) {
        arena.level.setBlock(arena.block(3, -1, 8), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(arena.level, arena.block(3, -1, 8)));
        CraftingContainer grid = (CraftingContainer) menu.getSlot(1).container;
        for (int slot = 0; slot < 9; slot++) grid.setItem(slot, new ItemStack(ingredients[slot]));
        menu.slotsChanged(grid);
        var recipe = arena.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, arena.level);
        helper.assertTrue(recipe.isPresent(), "Real RecipeManager did not match the requested crafting-table pattern");
        ItemStack assembled = recipe.orElseThrow().assemble(grid, arena.level.registryAccess());
        helper.assertTrue(assembled.is(result) && assembled.getCount() == 1, "Loaded recipe has the wrong result/count");
        helper.assertTrue(menu.getSlot(0).getItem().is(result), "Crafting-table result slot disagrees with the loaded recipe");
        ItemStack middle = grid.removeItemNoUpdate(4);
        menu.slotsChanged(grid);
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Recipe crafted without its required center ingredient");
        grid.setItem(4, middle);
        menu.slotsChanged(grid);
        ItemStack taken = menu.getSlot(0).remove(1);
        menu.getSlot(0).onTake(player, taken);
        helper.assertTrue(taken.is(result) && taken.getCount() == 1, "Taking the crafting result failed");
        for (int slot = 0; slot < 9; slot++) helper.assertTrue(grid.getItem(slot).isEmpty(), "Craft left a specified ingredient unconsumed: slot=" + slot);
    }
}
