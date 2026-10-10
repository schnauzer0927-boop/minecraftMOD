package dev.apexpredator.weapon;

import com.mojang.logging.LogUtils;
import dev.apexpredator.ApexConfig;
import dev.apexpredator.ApexPredator;
import dev.apexpredator.effect.ApexEffects;
import dev.apexpredator.effect.ApexSounds;
import dev.apexpredator.effect.EffectKind;
import dev.apexpredator.item.ApexFangItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned, finite weapon actions. No client action timers or entity AI are changed. */
@Mod.EventBusSubscriber(modid = ApexPredator.MOD_ID)
public final class PredatorWeaponAbilities {
    public static final int COOLDOWN_TICKS = 200;
    public static final int DASH_TICKS = 8, SLASH_TICKS = 10;
    public static final int DASH_DURABILITY = 4, SLASH_DURABILITY = 6;
    public static final double DASH_DISTANCE = 7, SLASH_DISTANCE = 9;
    public static final float DASH_DAMAGE = 12, SLASH_DAMAGE = 10;
    private static final Map<UUID, Action> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NORMAL_EFFECT_AT = new HashMap<>();

    public enum Mode { PREDATOR_DASH, APEX_SLASH }

    private static final class Action {
        final Mode mode;
        final ServerLevel level;
        final InteractionHand hand;
        final ItemStack weapon;
        final Vec3 origin;
        final Vec3 direction;
        final Set<UUID> struck = new HashSet<>();
        Vec3 committedPosition;
        Vec3 wave;
        int elapsed;
        int acceptedHits;
        int visualHitsThisTick;
        long lastAdvancedTick = Long.MIN_VALUE;

        Action(Player player, InteractionHand hand, Mode mode, Vec3 direction) {
            this.mode = mode;
            this.level = (ServerLevel) player.level();
            this.hand = hand;
            this.weapon = player.getItemInHand(hand);
            this.origin = player.position();
            this.direction = direction;
            this.committedPosition = origin;
            this.wave = origin;
        }
    }

    private PredatorWeaponAbilities() { }

    /** Returns true only for an accepted server activation; rejection consumes no resources. */
    public static boolean tryActivate(Player player, InteractionHand hand, boolean slash) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) return false;
        ItemStack weapon = player.getItemInHand(hand);
        if (!(weapon.getItem() instanceof ApexFangItem)) return false;
        if (ACTIVE.containsKey(player.getUUID()) || player.getCooldowns().isOnCooldown(weapon.getItem())) {
            player.displayClientMessage(Component.translatable("message.apexpredator.fang.cooldown"), true);
            return false;
        }
        Vec3 direction = player.getLookAngle().multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < 0.001) {
            double yaw = Math.toRadians(player.getYRot());
            direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        if (player.isPassenger() || player.isSwimming() || player.isInWaterOrBubble() || player.isInLava()) {
            groundMessage(player);
            return false;
        }
        if (!slash) {
            Vec3 firstStep = direction.scale(DASH_DISTANCE / DASH_TICKS);
            if (!grounded(player) || !loaded(level, player.getBoundingBox().expandTowards(firstStep))
                    || !supported(player, player.getBoundingBox().move(firstStep))
                    || blockCollision(level, player, player.getBoundingBox().move(direction.scale(0.12)))) {
                groundMessage(player);
                return false;
            }
        }
        Action action = new Action(player, hand, slash ? Mode.APEX_SLASH : Mode.PREDATOR_DASH, direction);
        ACTIVE.put(player.getUUID(), action);
        player.getCooldowns().addCooldown(weapon.getItem(), COOLDOWN_TICKS);
        weapon.hurtAndBreak(slash ? SLASH_DURABILITY : DASH_DURABILITY, player,
                owner -> owner.broadcastBreakEvent(hand));
        if (!slash && player instanceof ServerPlayer server && server.connection != null) {
            // Anchor existing movement packets once before starting prediction; the dash itself
            // still advances only through collision-tested move() calls.
            server.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            server.connection.resetPosition();
        }
        player.swing(hand, true);
        sound(player, slash ? "blade_swing" : "charge_dash", 0.85F);
        ApexEffects.emit(player, slash ? EffectKind.BLADE_LEFT : EffectKind.CHARGE_TRAIL,
                player.position(), direction, slash ? 1.5F : 1.1F, slash ? 8 : 10);
        if (ApexConfig.DEBUG.get()) LogUtils.getLogger().info(
                "Apex Fang accept mode={} player={} start={} direction={} cooldown={} wear={}",
                action.mode, player.getName().getString(), action.origin, action.direction,
                COOLDOWN_TICKS, slash ? SLASH_DURABILITY : DASH_DURABILITY);
        return true;
    }

    /** Public for integration tests; advances at most once per server game tick. */
    public static void tick(Player player) {
        Action action = ACTIVE.get(player.getUUID());
        if (action == null) return;
        if (!(player.level() instanceof ServerLevel) || player.level() != action.level || !player.isAlive()
                || player.isSpectator() || player.isPassenger() || player.isSwimming()
                || player.isInWaterOrBubble() || player.isInLava()
                || player.getItemInHand(action.hand) != action.weapon || action.weapon.isEmpty()) {
            finish(player, action, "invalid_owner_state");
            return;
        }
        long now = action.level.getGameTime();
        if (action.lastAdvancedTick == now) return;
        action.lastAdvancedTick = now;
        action.visualHitsThisTick = 0;
        if (action.mode == Mode.PREDATOR_DASH) dash(player, action);
        else slash(player, action);
    }

    private static void dash(Player player, Action action) {
        if (!grounded(player) || Math.abs(player.getY() - action.committedPosition.y) > 0.7) {
            finish(player, action, "airborne_or_ground_gap");
            return;
        }
        // Commit to seven horizontal blocks from the activation point. Client prediction/input
        // does not add another seven blocks on top of this server movement.
        double planned = Math.min(DASH_DISTANCE, (action.elapsed + 1) * DASH_DISTANCE / DASH_TICKS);
        Vec3 next = action.origin.add(action.direction.scale(planned));
        Vec3 movement = new Vec3(next.x - player.getX(), 0, next.z - player.getZ());
        if (player.position().subtract(action.committedPosition).horizontalDistanceSqr() > 2.1 * 2.1) {
            finish(player, action, "external_displacement"); // Do not undo a teleport/large knockback.
            return;
        }
        AABB swept = player.getBoundingBox().expandTowards(movement);
        if (!loaded(action.level, swept)) {
            finish(player, action, "unloaded_path");
            return;
        }
        double supportedFraction = supportedFraction(player, movement);
        boolean shortened = supportedFraction < 1;
        if (shortened) {
            // Check the entire route, including a larger step correcting stale owner packets.
            // A supported destination beyond a short gap must not let the dash cross the gap.
            movement = movement.scale(supportedFraction);
            if (movement.horizontalDistanceSqr() < 0.0025) {
                finish(player, action, "edge_no_progress");
                return;
            }
        }
        Vec3 before = player.position();
        // A small downward component lets vanilla collision keep onGround true on flat terrain.
        player.move(MoverType.SELF, movement.add(0, -0.08, 0));
        Vec3 actual = player.position().subtract(before);
        // Retain a downward ground probe for the next native Player.travel tick. The collision
        // above clips the vertical velocity to zero; a zero-motion travel would then clear
        // onGround even while the feet remain on a solid floor and cancel a valid dash.
        player.setDeltaMovement(0, Math.min(-0.08, player.getDeltaMovement().y), 0);
        player.fallDistance = 0;
        double committedProgress = player.position().subtract(action.committedPosition).horizontalDistanceSqr();
        AABB contact = new AABB(action.committedPosition.x, Math.min(action.committedPosition.y, player.getY()),
                action.committedPosition.z, player.getX(), Math.max(action.committedPosition.y, player.getY()) + player.getBbHeight(),
                player.getZ()).inflate(player.getBbWidth() * 0.5 + 0.35, 0.08, player.getBbWidth() * 0.5 + 0.35);
        hit(player, action, contact, action.committedPosition.add(0, 1, 0), DASH_DAMAGE);
        if (ACTIVE.get(player.getUUID()) != action) return;
        action.committedPosition = player.position();
        action.elapsed++;
        synchronizeStep(player, actual.multiply(1, 0, 1));
        if (action.elapsed % 2 == 0) ApexEffects.emit(player, EffectKind.CHARGE_TRAIL,
                player.position(), action.direction, 1.1F, 7);
        if (shortened || player.horizontalCollision || committedProgress < 0.001
                || action.elapsed >= DASH_TICKS || !supported(player, player.getBoundingBox())) {
            String reason = shortened ? "ledge" : player.horizontalCollision ? "collision"
                    : committedProgress < 0.001 ? "no_progress" : action.elapsed >= DASH_TICKS ? "complete" : "lost_support";
            finish(player, action, reason);
        }
    }

    private static void slash(Player player, Action action) {
        Vec3 next = action.wave.add(action.direction.scale(SLASH_DISTANCE / SLASH_TICKS));
        AABB wave = new AABB(action.wave.x, action.origin.y + 0.15, action.wave.z,
                next.x, action.origin.y + 1.95, next.z).inflate(0.85, 0, 0.85);
        if (!loaded(action.level, wave)) {
            finish(player, action, "unloaded_wave");
            return;
        }
        if (blockCollision(action.level, player, wave)) {
            finish(player, action, "wave_blocked");
            return;
        }
        hit(player, action, wave, action.wave.add(0, 1, 0), SLASH_DAMAGE);
        if (ACTIVE.get(player.getUUID()) != action) return;
        action.wave = next;
        action.elapsed++;
        if (action.elapsed % 2 == 0) ApexEffects.emit(player, EffectKind.BLADE_LEFT,
                next, action.direction, 1.45F, 7);
        if (action.elapsed >= SLASH_TICKS) finish(player, action, "complete");
    }

    private static void hit(Player player, Action action, AABB area, Vec3 source, float baseDamage) {
        for (Mob target : action.level.getEntitiesOfClass(Mob.class, area,
                mob -> eligibleTarget(player, mob) && !action.struck.contains(mob.getUUID()))) {
            if (!player.isAlive() || ACTIVE.get(player.getUUID()) != action) return;
            Vec3 aim = target.getBoundingBox().getCenter();
            double forward = aim.subtract(action.origin).dot(action.direction);
            if (forward < 0 || (action.mode == Mode.APEX_SLASH && forward > SLASH_DISTANCE)) continue;
            if (action.level.clip(new ClipContext(source, aim, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
            action.struck.add(target.getUUID());
            float damage = baseDamage + EnchantmentHelper.getDamageBonus(action.weapon, target.getMobType());
            if (!target.hurt(player.damageSources().playerAttack(player), damage)) continue;
            action.acceptedHits++;
            int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, action.weapon);
            if (fire > 0) target.setSecondsOnFire(fire * 4);
            int knockback = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, action.weapon);
            target.knockback(0.35 + knockback * 0.4, -action.direction.x, -action.direction.z);
            target.hurtMarked = true;
            if (action.visualHitsThisTick < 4) {
                ApexEffects.emit(player, EffectKind.HIT, target.position(), action.direction, 0.55F, 7);
                if (action.visualHitsThisTick == 0) sound(player, "blade_hit", 0.55F);
                action.visualHitsThisTick++;
            }
        }
    }

    public static boolean eligibleTarget(Player player, LivingEntity target) {
        return target instanceof Mob && target instanceof Enemy && target.isAlive() && !target.isSpectator()
                && !(target instanceof TamableAnimal animal && animal.isTame())
                && !target.isAlliedTo(player) && !player.isAlliedTo(target);
    }

    private static boolean grounded(Player player) {
        return player.onGround() && !player.getAbilities().flying && player.getDeltaMovement().y <= 0.08
                && supported(player, player.getBoundingBox());
    }

    private static double supportedFraction(Player player, Vec3 movement) {
        int steps = Math.max(1, (int) Math.ceil(movement.horizontalDistance() / 0.2));
        AABB body = player.getBoundingBox();
        for (int step = 1; step <= steps; step++) {
            double next = (double) step / steps;
            if (supported(player, body.move(movement.scale(next)))) continue;
            double low = (double) (step - 1) / steps, high = next;
            for (int attempt = 0; attempt < 9; attempt++) {
                double middle = (low + high) * .5;
                if (supported(player, body.move(movement.scale(middle)))) low = middle;
                else high = middle;
            }
            return low;
        }
        return 1;
    }

    private static boolean supported(Player player, AABB body) {
        ServerLevel level = (ServerLevel) player.level();
        if (!loaded(level, body)) return false;
        for (double x : new double[]{body.minX + 0.001, body.maxX - 0.001}) {
            for (double z : new double[]{body.minZ + 0.001, body.maxZ - 0.001}) {
                // Maximum accepted ground gap is 0.6 blocks; full cliffs/gaps terminate the dash.
                AABB column = new AABB(x - 0.0005, body.minY - 0.6, z - 0.0005,
                        x + 0.0005, body.minY + 0.01, z + 0.0005);
                if (!blockCollision(level, player, column)) return false;
                BlockPos feet = BlockPos.containing(x, body.minY - 0.05, z);
                if (!level.getFluidState(feet).isEmpty()) return false;
            }
        }
        return true;
    }

    private static boolean loaded(ServerLevel level, AABB box) {
        if (!level.getWorldBorder().isWithinBounds(box) || box.minY < level.getMinBuildHeight()
                || box.maxY >= level.getMaxBuildHeight()) return false;
        int minX = Math.floorDiv((int) Math.floor(box.minX), 16);
        int maxX = Math.floorDiv((int) Math.floor(box.maxX), 16);
        int minZ = Math.floorDiv((int) Math.floor(box.minZ), 16);
        int maxZ = Math.floorDiv((int) Math.floor(box.maxZ), 16);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) if (!level.hasChunk(x, z)) return false;
        }
        return true;
    }

    private static boolean blockCollision(ServerLevel level, Player player, AABB box) {
        for (var shape : level.getBlockCollisions(player, box)) if (!shape.isEmpty()) return true;
        return false;
    }

    private static void synchronizeStep(Player player, Vec3 motion) {
        if (player instanceof ServerPlayer server && server.connection != null) {
            server.connection.resetPosition();
            server.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), motion));
        }
    }

    private static void finish(Player player, Action action, String reason) {
        if (!ACTIVE.remove(player.getUUID(), action)) return;
        if (ApexConfig.DEBUG.get()) LogUtils.getLogger().info(
                "Apex Fang finish mode={} player={} reason={} ticks={} position={} distance={} waveDistance={} struck={} accepted={}",
                action.mode, player.getName().getString(), reason, action.elapsed, player.position(),
                player.position().subtract(action.origin).horizontalDistance(),
                action.wave.subtract(action.origin).horizontalDistance(), action.struck.size(), action.acceptedHits);
        if (action.mode != Mode.PREDATOR_DASH) return;
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        if (player instanceof ServerPlayer server && server.connection != null) {
            server.connection.send(new ClientboundSetEntityMotionPacket(player));
            if (player.isAlive() && player.level() == action.level) {
                // One native final correction anchors prediction to the collision-tested endpoint.
                server.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                server.connection.resetPosition();
            }
        }
    }

    public static boolean isActive(UUID playerId) { return ACTIVE.containsKey(playerId); }
    public static int activeTick(UUID playerId) {
        Action action = ACTIVE.get(playerId);
        return action == null ? -1 : action.elapsed;
    }
    public static Mode activeMode(UUID playerId) {
        Action action = ACTIVE.get(playerId);
        return action == null ? null : action.mode;
    }
    public static void clear(Player player) {
        Action action = ACTIVE.get(player.getUUID());
        if (action != null) finish(player, action, "cleanup");
        NORMAL_EFFECT_AT.remove(player.getUUID());
    }

    public static void normalHitEffect(Player player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now - NORMAL_EFFECT_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < 4) return;
        NORMAL_EFFECT_AT.put(player.getUUID(), now);
        ApexEffects.emit(player, EffectKind.BLADE_LEFT, player.position(), player.getLookAngle(), 1.35F, 6);
        ApexEffects.emit(player, EffectKind.HIT, target.position(), player.getLookAngle(), 0.4F, 6);
        sound(player, "blade_hit", 0.6F);
    }

    private static void sound(Player player, String name, float volume) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ApexSounds.event(name), SoundSource.PLAYERS, volume, 1);
    }
    private static void groundMessage(Player player) {
        player.displayClientMessage(Component.translatable("message.apexpredator.fang.ground_required"), true);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) tick(event.player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) clear(player);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { ACTIVE.clear(); NORMAL_EFFECT_AT.clear(); }
}
