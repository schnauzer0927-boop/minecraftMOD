package dev.apexpredator.gametest;

import dev.apexpredator.ApexPredator;
import dev.apexpredator.registry.ApexRegistries;
import dev.apexpredator.weapon.PredatorWeaponAbilities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Native sword damage/repair and real item-use abilities, including player travel and collision. */
@GameTestHolder(ApexPredator.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FangGameTests {
    private FangGameTests() { }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 40)
    public static void fangRetainsSwordAttributesDurabilityNetheriteRepairAndNormalEnchantments(GameTestHelper helper) {
        try (ProgressionGameTestArena arena = new ProgressionGameTestArena(helper)) {
            ServerPlayer player = arena.player(false, 3.5, 8.5);
            ItemStack fang = new ItemStack(ApexRegistries.APEX_FANG.get());
            helper.assertTrue(fang.getItem() instanceof SwordItem, "Fang must remain compatible with ordinary SwordItem behavior");
            near(helper, attribute(player, fang, Attributes.ATTACK_DAMAGE), 16, "Total player attack damage");
            near(helper, attribute(player, fang, Attributes.ATTACK_SPEED), 1.6, "Total player attack speed");
            helper.assertTrue(fang.getMaxDamage() == 3000, "Fang lost its required 3000 durability");
            helper.assertTrue(fang.getItem().isValidRepairItem(fang, new ItemStack(Items.NETHERITE_INGOT)), "Netherite cannot repair Fang");
            helper.assertFalse(fang.getItem().isValidRepairItem(fang, new ItemStack(Items.IRON_INGOT)), "Wrong repair material is accepted");
            for (var enchantment : new net.minecraft.world.item.enchantment.Enchantment[]{Enchantments.SHARPNESS,
                    Enchantments.SWEEPING_EDGE, Enchantments.FIRE_ASPECT, Enchantments.MOB_LOOTING,
                    Enchantments.UNBREAKING, Enchantments.MENDING}) {
                helper.assertTrue(enchantment.canEnchant(fang), "Ordinary sword enchantment rejects Fang: " + enchantment.getDescriptionId());
            }
            fang.setDamageValue(1200);
            AnvilMenu repair = new AnvilMenu(1, player.getInventory());
            repair.getSlot(0).set(fang);
            repair.getSlot(1).set(new ItemStack(Items.NETHERITE_INGOT));
            repair.createResult();
            ItemStack repaired = repair.getSlot(2).getItem();
            helper.assertTrue(repaired.is(ApexRegistries.APEX_FANG.get()) && repaired.getDamageValue() < 1200,
                    "Actual anvil did not repair Fang with Netherite");
            AnvilMenu enchant = new AnvilMenu(2, player.getInventory());
            enchant.getSlot(0).set(repaired.copy());
            enchant.getSlot(1).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.SHARPNESS, 5)));
            enchant.createResult();
            ItemStack enchanted = enchant.getSlot(2).getItem();
            helper.assertTrue(enchanted.is(ApexRegistries.APEX_FANG.get())
                            && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, enchanted) == 5,
                    "Actual anvil could not apply the ordinary sword enchanted book");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 50)
    public static void ordinaryPlayerAttackDealsSixteenAndUsesOneDurability(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack fang = equip(player);
        var victim = arena.mob(EntityType.VINDICATOR, 5.5, 8.5);
        helper.runAfterDelay(20, () -> {
            near(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 16, "Actual equipped player's damage");
            near(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), 1.6, "Actual equipped player's attack speed");
            helper.assertTrue(player.getAttackStrengthScale(0) >= .99, "Fixture did not wait for vanilla attack recovery");
            player.attack(victim);
            near(helper, victim.getHealth(), 984, "Native unenchanted sword health loss");
            helper.assertTrue(fang.getDamageValue() == 1, "Ordinary sword hit did not consume exactly one durability");
            arena.succeed(helper);
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 80)
    public static void dashTravelsSevenBlocksHitsEachEnemyOnceAndBecomesReadyOnTickForty(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack fang = equip(player);
        var first = arena.mob(EntityType.VINDICATOR, 5.5, 8.5);
        var second = arena.mob(EntityType.VINDICATOR, 8.5, 8.5);
        var neutral = arena.mob(EntityType.PIG, 6.5, 8.5);
        helper.runAfterDelay(4, () -> {
            Vec3 start = player.position();
            long activated = arena.level.getGameTime();
            helper.assertTrue(player.onGround(), "Dash actor must naturally stand on its supporting floor");
            helper.assertTrue(use(arena, player, false), "Real Fang right-click failed to activate the grounded dash");
            helper.assertTrue(fang.getDamageValue() == 4, "Dash did not consume its four durability once");
            helper.runAfterDelay(12, () -> {
                double distance = player.position().subtract(start).horizontalDistance();
                helper.assertTrue(distance > 6.75 && distance <= 7.05, "Actual dash travel diverged from seven blocks: " + distance);
                near(helper, first.getHealth(), 988, "First enemy dash hit");
                near(helper, second.getHealth(), 988, "Second enemy dash hit");
                near(helper, neutral.getHealth(), 1000, "Dash must not damage a neutral animal");
                helper.assertFalse(PredatorWeaponAbilities.isActive(player.getUUID()), "Eight-tick dash remained active indefinitely");
                ItemStack replacement = equip(player);
                helper.assertFalse(use(arena, player, false), "Switching Fang stacks bypassed the player's dash cooldown");
                helper.assertTrue(replacement.getDamageValue() == 0, "Rejected cooldown use consumed durability");
            });
            helper.runAfterDelay(39, () -> {
                helper.assertTrue(arena.level.getGameTime() - activated == 39, "Fixture must exercise the exact 39-tick boundary");
                helper.assertTrue(PredatorWeaponAbilities.cooldownRemaining(player, PredatorWeaponAbilities.Mode.PREDATOR_DASH) == 1,
                        "Dash must have exactly one tick left after 39 ticks");
                helper.assertFalse(use(arena, player, false), "Dash was accepted one tick before its two-second deadline");
                helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "Rejected 39-tick use consumed durability");
            });
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(arena.level.getGameTime() - activated == 40, "Fixture must exercise the exact 40-tick boundary");
                player.setYRot(90); // Return across the same enemies with a new, independently bounded dash.
                helper.assertTrue(use(arena, player, false), "Native item use rejected Dash at exactly 40 ticks");
                helper.assertTrue(player.getMainHandItem().getDamageValue() == 4, "Accepted dash did not pay durability exactly once");
                helper.runAfterDelay(12, () -> {
                    near(helper, first.getHealth(), 976, "Each of two dashes must hit the first enemy exactly once");
                    near(helper, second.getHealth(), 976, "Each of two dashes must hit the second enemy exactly once");
                    PredatorWeaponAbilities.clear(player);
                    arena.succeed(helper);
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 50)
    public static void dashStopsAtASolidWallAndCannotHitTheEnemyBehindIt(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        equip(player);
        var exposed = arena.mob(EntityType.VINDICATOR, 5.5, 8.5);
        var covered = arena.mob(EntityType.VINDICATOR, 9.5, 8.5);
        for (int y = 0; y < 4; y++) for (int z = 6; z <= 10; z++) arena.level.setBlock(arena.block(7, y, z), Blocks.STONE.defaultBlockState(), 2);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(use(arena, player, false), "Dash could not begin on its clear side of the wall");
            helper.runAfterDelay(16, () -> {
                helper.assertTrue(player.getBoundingBox().maxX <= arena.block(7, 0, 8).getX() + .01,
                        "Dash crossed the wall with its real player body");
                helper.assertTrue(arena.level.noCollision(player, player.getBoundingBox()), "Dash ended inside the wall");
                near(helper, exposed.getHealth(), 988, "Exposed enemy must receive one dash hit");
                near(helper, covered.getHealth(), 1000, "Dash damage passed through the blocking wall");
                helper.assertFalse(PredatorWeaponAbilities.isActive(player.getUUID()), "Wall collision left the dash running");
                arena.succeed(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 50)
    public static void aSmallOwnerPositionCorrectionStillCompletesOneSevenBlockDash(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ItemStack fang = equip(player);
        var first = arena.mob(EntityType.VINDICATOR, 5.5, 8.5);
        var second = arena.mob(EntityType.VINDICATOR, 8.5, 8.5);
        helper.runAfterDelay(4, () -> {
            Vec3 start = player.position();
            helper.assertTrue(use(arena, player, false), "Correction fixture failed to start its grounded dash");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(PredatorWeaponAbilities.isActive(player.getUUID()), "Dash ended before the owner position correction");
                // Models a delayed owner movement position, without forging onGround or bypassing real travel.
                player.setPos(player.getX() - 1.5, player.getY(), player.getZ());
            });
            helper.runAfterDelay(12, () -> {
                near(helper, player.position().subtract(start).horizontalDistance(), 7,
                        "Small owner packet correction incorrectly canceled the committed seven-block dash");
                near(helper, first.getHealth(), 988, "Position correction repeated or lost the first enemy hit");
                near(helper, second.getHealth(), 988, "Position correction lost the second enemy hit");
                helper.assertTrue(fang.getDamageValue() == 4, "Correcting the owner position paid for the dash twice");
                helper.assertFalse(PredatorWeaponAbilities.isActive(player.getUUID()), "Corrected dash never completed");
                arena.succeed(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 50)
    public static void aCatchUpStepCannotSkipAShortUnsupportedGap(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        equip(player);
        var covered = arena.mob(EntityType.VINDICATOR, 9.5, 8.5);
        boolean[] corrected = {false};
        Consumer<TickEvent.PlayerTickEvent> correction = event -> {
            if (event.player != player || event.phase != TickEvent.Phase.END || corrected[0]
                    || PredatorWeaponAbilities.activeTick(player.getUUID()) != 5) return;
            corrected[0] = true;
            // The next planned endpoint (8.75) is supported; its route crosses the newly missing block (7).
            // A delayed owner position must not turn the correction into a jump across that gap.
            for (int z = 0; z < 16; z++) arena.level.setBlock(arena.block(7, -1, z), Blocks.AIR.defaultBlockState(), 2);
            player.setPos(arena.point(6.125, 0, 8.5).x, player.getY(), player.getZ());
        };
        // GameTest callback iteration is not ordered against the separate player travel callback.
        // Observe the real post-travel event after the weapon's NORMAL-priority listener instead.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TickEvent.PlayerTickEvent.class, correction);
        arena.onClose(() -> MinecraftForge.EVENT_BUS.unregister(correction));
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(use(arena, player, false), "Gap fixture failed to begin on its initially intact floor");
            helper.runAfterDelay(16, () -> {
                helper.assertTrue(corrected[0], "Fixture never exercised a live catch-up step");
                helper.assertTrue(player.getX() > arena.point(6.6, 0, 8.5).x
                                && player.getBoundingBox().maxX <= arena.block(7, 0, 8).getX() + .01,
                        "Catch-up must move to the first unsupported edge and stop, even with supported ground beyond it: " + player.position());
                helper.assertTrue(player.onGround() && Math.abs(player.getY() - arena.origin.getY()) < .05,
                        "Catch-up across a short gap made the player airborne");
                near(helper, covered.getHealth(), 1000, "Catch-up damaged an enemy beyond the unsupported gap");
                helper.assertFalse(PredatorWeaponAbilities.isActive(player.getUUID()), "Unsupported catch-up left the dash running");
                arena.succeed(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 50)
    public static void dashStopsBeforeACliffAndDoesNotLeaveFlightOrResidualMotion(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        equip(player);
        for (int x = 7; x < 16; x++) for (int z = 0; z < 16; z++) arena.level.setBlock(arena.block(x, -1, z), Blocks.AIR.defaultBlockState(), 2);
        helper.runAfterDelay(4, () -> {
            Vec3 start = player.position();
            helper.assertTrue(use(arena, player, false), "Dash could not begin on supported ground before the cliff");
            helper.runAfterDelay(20, () -> {
                double distance = player.position().subtract(start).horizontalDistance();
                helper.assertTrue(distance > 2.9 && distance < 3.35,
                        "Cliff fixture must travel to the edge before stopping, instead of ending immediately: " + distance);
                helper.assertTrue(player.getBoundingBox().maxX <= arena.block(7, 0, 8).getX() + .01,
                        "Dash carried the player's footprint beyond the cliff edge");
                helper.assertTrue(player.onGround() && Math.abs(player.getY() - arena.origin.getY()) < .05,
                        "Cliff dash left the player airborne or falling");
                helper.assertFalse(player.isNoGravity() || player.getAbilities().flying, "Dash granted persistent flight");
                helper.assertTrue(player.getDeltaMovement().horizontalDistanceSqr() < .01, "Stopped dash left unsafe residual horizontal motion");
                helper.assertFalse(PredatorWeaponAbilities.isActive(player.getUUID()), "Cliff stop left the dash active indefinitely");
                arena.succeed(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 50)
    public static void slashHitsMultipleEnemiesOnceWithinNineBlocksAndRespectsCoverWithoutBlockingDash(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ProgressionGameTestArena rangeArena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        ServerPlayer rangePlayer = rangeArena.player(false, 3.5, 8.5);
        ItemStack fang = equip(player);
        equip(rangePlayer);
        var first = arena.mob(EntityType.VINDICATOR, 6.5, 8.5);
        var second = arena.mob(EntityType.VINDICATOR, 9, 9);
        var covered = arena.mob(EntityType.VINDICATOR, 11.5, 8.5);
        var outside = arena.mob(EntityType.VINDICATOR, 13.5, 8.5);
        var neutral = arena.mob(EntityType.PIG, 7.5, 8.5);
        var nearLimit = rangeArena.mob(EntityType.VINDICATOR, 12.25, 8.5);
        var beyondLimit = rangeArena.mob(EntityType.VINDICATOR, 13.25, 8.5);
        for (int y = 0; y < 4; y++) for (int z = 6; z <= 10; z++) arena.level.setBlock(arena.block(10, y, z), Blocks.STONE.defaultBlockState(), 2);
        helper.runAfterDelay(4, () -> {
            Vec3 position = player.position();
            helper.assertTrue(use(arena, player, true), "Real crouching Fang use failed to launch its predator slash");
            helper.assertTrue(use(rangeArena, rangePlayer, true), "Clear-range slash fixture failed to activate");
            helper.assertTrue(fang.getDamageValue() == 6, "Slash did not consume six durability once");
            helper.runAfterDelay(16, () -> {
                near(helper, first.getHealth(), 990, "First slash target");
                near(helper, second.getHealth(), 990, "Second slash target");
                near(helper, covered.getHealth(), 1000, "Slash passed through a solid wall");
                near(helper, outside.getHealth(), 1000, "Slash exceeded its nine-block range");
                near(helper, neutral.getHealth(), 1000, "Slash damaged a neutral animal");
                near(helper, nearLimit.getHealth(), 990, "Clear slash must reach an enemy 8.75 blocks ahead");
                near(helper, beyondLimit.getHealth(), 1000, "Uncovered enemy 9.75 blocks ahead must remain outside slash range");
                helper.assertTrue(player.position().distanceTo(position) < .15, "Ranged slash unexpectedly dashed its owner");
                helper.assertFalse(PredatorWeaponAbilities.isActive(player.getUUID()), "Slash remained active beyond its ten-tick wave");
                helper.assertTrue(use(arena, player, false), "Slash's ten-second deadline must not block a ready Dash");
                helper.assertTrue(fang.getDamageValue() == 10, "Independent Dash did not consume exactly four more durability");
                PredatorWeaponAbilities.clear(player);
                PredatorWeaponAbilities.clear(rangePlayer);
                rangeArena.close();
                arena.succeed(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 225)
    public static void slashRetainsTwoHundredTicksAndNeverExtendsTheIndependentDashDeadline(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        ServerPlayer player = arena.player(false, 3.5, 8.5);
        equip(player);
        helper.runAfterDelay(4, () -> {
            long activated = arena.level.getGameTime();
            helper.assertTrue(use(arena, player, true), "Initial native Slash use was rejected");
            helper.runAfterDelay(12, () -> {
                ItemStack replacement = equip(player);
                helper.assertFalse(use(arena, player, true), "Another Fang stack bypassed Slash's per-player deadline");
                helper.assertTrue(replacement.getDamageValue() == 0, "Rejected Slash wore the replacement Fang");
                helper.assertTrue(use(arena, player, false), "Ready Dash was blocked by Slash's remaining ten-second cooldown");
                helper.assertTrue(PredatorWeaponAbilities.cooldownRemaining(player, PredatorWeaponAbilities.Mode.PREDATOR_DASH) == 40,
                        "Dash must use its own fresh forty-tick deadline");
                helper.assertTrue(PredatorWeaponAbilities.cooldownRemaining(player, PredatorWeaponAbilities.Mode.APEX_SLASH) == 188,
                        "Dash changed the already running Slash deadline");
            });
            helper.runAfterDelay(51, () -> {
                helper.assertFalse(use(arena, player, false), "Dash was usable 39 ticks after its activation during Slash cooldown");
            });
            helper.runAfterDelay(52, () -> {
                player.setYRot(90);
                helper.assertTrue(use(arena, player, false), "Dash was still blocked at its own forty-tick deadline");
                helper.assertTrue(PredatorWeaponAbilities.cooldownRemaining(player, PredatorWeaponAbilities.Mode.APEX_SLASH) == 148,
                        "Reusing Dash extended or shortened Slash's original deadline");
            });
            helper.runAfterDelay(199, () -> {
                helper.assertTrue(arena.level.getGameTime() - activated == 199, "Fixture must exercise Slash's exact 199-tick boundary");
                helper.assertTrue(PredatorWeaponAbilities.cooldownRemaining(player, PredatorWeaponAbilities.Mode.APEX_SLASH) == 1,
                        "Slash's deadline changed from its original 200 ticks");
                int wear = player.getMainHandItem().getDamageValue();
                helper.assertFalse(use(arena, player, true), "Slash was accepted one tick before its ten-second deadline");
                helper.assertTrue(player.getMainHandItem().getDamageValue() == wear, "Rejected Slash consumed durability");
            });
            helper.runAfterDelay(200, () -> {
                helper.assertTrue(arena.level.getGameTime() - activated == 200, "Fixture must exercise Slash's exact 200-tick boundary");
                helper.assertTrue(use(arena, player, true), "Native Slash use was rejected at exactly 200 ticks");
                PredatorWeaponAbilities.clear(player);
                arena.succeed(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "apexpredator_progression", timeoutTicks = 85)
    public static void vanillaHotbarPacketsFollowSelectedAbilityWithoutPerTickRestarts(GameTestHelper helper) {
        ProgressionGameTestArena arena = new ProgressionGameTestArena(helper);
        List<ClientboundCooldownPacket> packets = new ArrayList<>();
        ItemCooldowns clientCooldowns = new ItemCooldowns();
        ServerPlayer player = arena.player(false, 3.5, 8.5, packet -> {
            if (!(packet instanceof ClientboundCooldownPacket cooldown) || cooldown.getItem() != ApexRegistries.APEX_FANG.get()) return;
            packets.add(cooldown);
            // This is exactly the vanilla client packet handler's operation on ItemCooldowns.
            if (cooldown.getDuration() == 0) clientCooldowns.removeCooldown(cooldown.getItem());
            else clientCooldowns.addCooldown(cooldown.getItem(), cooldown.getDuration());
        });
        helper.onEachTick(clientCooldowns::tick);
        equip(player);
        int[] quietCount = {-1};
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(use(arena, player, false), "Native Dash did not start for hotbar packet verification");
            helper.assertTrue(lastDuration(packets) == 40 && clientCooldowns.isOnCooldown(ApexRegistries.APEX_FANG.get()),
                    "Dash did not start the vanilla forty-tick client item overlay");
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(packets.size() == 1, "Unchanged Dash cooldown was restarted or resent every tick");
                player.setShiftKeyDown(true);
            });
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(lastDuration(packets) == 0 && !clientCooldowns.isOnCooldown(ApexRegistries.APEX_FANG.get()),
                        "Selecting ready Slash must clear the Dash overlay without clearing Dash's deadline");
                helper.assertTrue(PredatorWeaponAbilities.cooldownRemaining(player, PredatorWeaponAbilities.Mode.PREDATOR_DASH) == 30,
                        "Changing selection bypassed Dash's server deadline");
                player.setShiftKeyDown(false);
            });
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(clientCooldowns.isOnCooldown(ApexRegistries.APEX_FANG.get()) && lastDuration(packets) <= 30,
                        "Returning to Dash did not restore its remaining vanilla cooldown");
                helper.assertTrue(use(arena, player, true), "Ready Slash was blocked by the previously selected Dash native gate");
                helper.assertTrue(lastDuration(packets) == 200, "Slash did not use a full independent vanilla ten-second overlay");
            });
            helper.runAfterDelay(16, () -> quietCount[0] = packets.size());
            helper.runAfterDelay(38, () -> {
                helper.assertTrue(packets.size() == quietCount[0], "A stable Slash overlay generated a cooldown packet every tick");
            });
            helper.runAfterDelay(42, () -> {
                helper.assertTrue(clientCooldowns.isOnCooldown(ApexRegistries.APEX_FANG.get())
                                && lastDuration(packets) >= 169 && lastDuration(packets) <= 173,
                        "The shorter server gate expiry erased Slash's still-active client overlay");
                player.setShiftKeyDown(false);
            });
            helper.runAfterDelay(44, () -> {
                helper.assertTrue(lastDuration(packets) == 0 && !clientCooldowns.isOnCooldown(ApexRegistries.APEX_FANG.get()),
                        "Ready Dash should display no cooldown while Slash is still cooling down");
                player.setShiftKeyDown(true);
            });
            helper.runAfterDelay(46, () -> {
                helper.assertTrue(clientCooldowns.isOnCooldown(ApexRegistries.APEX_FANG.get()) && lastDuration(packets) >= 165,
                        "Selecting Slash again failed to restore its remaining native overlay");
                helper.assertTrue(packets.size() < 15, "Selected cooldown synchronization sent excessive packets");
                PredatorWeaponAbilities.clear(player);
                helper.assertTrue(lastDuration(packets) == 0 && !clientCooldowns.isOnCooldown(ApexRegistries.APEX_FANG.get()),
                        "Lifecycle cleanup did not clear a client-only selected overlay");
                helper.assertTrue(PredatorWeaponAbilities.cooldownRemaining(player, PredatorWeaponAbilities.Mode.APEX_SLASH) == 0,
                        "Cleanup retained a stale per-player Slash deadline");
                arena.succeed(helper);
            });
        });
    }

    private static int lastDuration(List<ClientboundCooldownPacket> packets) {
        return packets.isEmpty() ? -1 : packets.get(packets.size() - 1).getDuration();
    }

    private static ItemStack equip(ServerPlayer player) {
        ItemStack fang = new ItemStack(ApexRegistries.APEX_FANG.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, fang);
        return fang;
    }

    private static boolean use(ProgressionGameTestArena arena, ServerPlayer player, boolean slash) {
        arena.advancePlayer(player);
        player.setShiftKeyDown(slash);
        return player.gameMode.useItem(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND).consumesAction();
    }

    private static double attribute(ServerPlayer player, ItemStack stack, Attribute attribute) {
        return player.getAttribute(attribute).getBaseValue() + stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute).stream()
                .filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADDITION)
                .mapToDouble(AttributeModifier::getAmount).sum();
    }

    private static void near(GameTestHelper helper, double actual, double expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < .001, description + ": expected=" + expected + " actual=" + actual);
    }
}
