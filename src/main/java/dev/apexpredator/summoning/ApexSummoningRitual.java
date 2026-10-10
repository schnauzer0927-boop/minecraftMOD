package dev.apexpredator.summoning;

import com.mojang.logging.LogUtils;
import dev.apexpredator.ApexConfig;
import dev.apexpredator.effect.ApexEffects;
import dev.apexpredator.effect.ApexSounds;
import dev.apexpredator.effect.EffectKind;
import dev.apexpredator.entity.ApexEntity;
import dev.apexpredator.item.ApexBeaconItem;
import dev.apexpredator.progression.ApexProgression;
import dev.apexpredator.registry.ApexRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-owned, finite rituals. A Beacon is paid for only after a successful entity insertion. */
public final class ApexSummoningRitual {
    public static final int DURATION_TICKS = 100;
    public static final double EXCLUSION_RADIUS = 64;
    public static final String TOKEN_KEY = "ApexBeaconRitual";
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Map<ServerLevel, Map<UUID, Ritual>> ACTIVE = new IdentityHashMap<>();

    private ApexSummoningRitual() { }

    public enum BeginResult {
        STARTED("started"), WRONG_ITEM("invalid_item"), INVALID_PLAYER("invalid_item"),
        PEACEFUL("peaceful"), APEX_NEARBY("apex_nearby"), RITUAL_NEARBY("busy"),
        UNSAFE_GROUND("unsafe_ground"), SPACE_BLOCKED("space_blocked");

        private final String translationKey;
        BeginResult(String key) { translationKey = "message.apexpredator.beacon." + key; }
        public String translationKey() { return translationKey; }
    }

    public record RitualStatus(UUID id, UUID summoner, Vec3 center, int elapsed, int duration, InteractionHand hand) {
        public int remaining() { return Math.max(0, duration - elapsed); }
    }

    private record Ritual(UUID id, ServerPlayer player, BlockPos floor, Vec3 center,
                          InteractionHand hand, long startedAt, ItemStack initialStack, ApexEntity apex) { }

    public static BeginResult begin(ServerPlayer player, InteractionHand hand, BlockPos floor) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return BeginResult.INVALID_PLAYER;
        ServerLevel level = player.serverLevel();
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof ApexBeaconItem)) return BeginResult.WRONG_ITEM;
        UUID oldToken = BeaconRitualInventory.token(stack);
        if (oldToken != null) {
            if (activeToken(oldToken)) return BeginResult.RITUAL_NEARBY;
            BeaconRitualInventory.unmark(stack, oldToken);
            player.getInventory().setChanged();
        }
        if (activePlayer(player.getUUID())) return BeginResult.RITUAL_NEARBY;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return BeginResult.PEACEFUL;
        ApexEntity apex = ApexRegistries.APEX.get().create(level);
        if (apex == null) return BeginResult.SPACE_BLOCKED;
        Optional<Vec3> candidate = BeaconSpawnSite.center(level, floor, apex);
        if (candidate.isEmpty()) return BeginResult.UNSAFE_GROUND;
        Vec3 center = candidate.get();
        BeginResult site = BeaconSpawnSite.validate(level, floor, center, apex);
        if (site != BeginResult.STARTED) return site;
        if (nearbyApex(level, center)) return BeginResult.APEX_NEARBY;
        if (nearbyRitual(level, center, null)) return BeginResult.RITUAL_NEARBY;

        UUID id = UUID.randomUUID();
        Ritual ritual = new Ritual(id, player, floor.immutable(), center, hand, level.getGameTime(), stack, apex);
        BeaconRitualInventory.mark(stack, id);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        ACTIVE.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(player.getUUID(), ritual);
        cue(ritual);
        sound(level, center, "special_charge", .75F, .7F);
        log("started", level, ritual, false);
        return BeginResult.STARTED;
    }

    public static Optional<RitualStatus> status(ServerLevel level, UUID player) {
        Map<UUID, Ritual> rituals = ACTIVE.get(level);
        Ritual ritual = rituals == null ? null : rituals.get(player);
        return ritual == null ? Optional.empty() : Optional.of(new RitualStatus(ritual.id(), player,
                ritual.center(), elapsed(level, ritual), DURATION_TICKS, ritual.hand()));
    }

    public static int activeCount(ServerLevel level) {
        Map<UUID, Ritual> rituals = ACTIVE.get(level);
        return rituals == null ? 0 : rituals.size();
    }

    public static boolean cancel(ServerPlayer player) {
        boolean canceled = false;
        for (ServerLevel level : List.copyOf(ACTIVE.keySet())) {
            Map<UUID, Ritual> rituals = ACTIVE.get(level);
            Ritual ritual = rituals == null ? null : rituals.get(player.getUUID());
            if (ritual != null) {
                remove(level, ritual);
                cleanup(level, ritual, true);
                canceled = true;
            }
        }
        return canceled;
    }

    /** Invoked once by LevelTickEvent.END; all decisions and payment stay on the server thread. */
    static void tick(ServerLevel level) {
        Map<UUID, Ritual> rituals = ACTIVE.get(level);
        if (rituals == null) return;
        // Spawn and logout callbacks may reenter event handlers; snapshots avoid iterator corruption.
        for (Ritual ritual : List.copyOf(rituals.values())) {
            if (rituals.get(ritual.player().getUUID()) != ritual) continue;
            if (!valid(level, ritual)) {
                remove(level, ritual);
                cleanup(level, ritual, true);
                continue;
            }
            int elapsed = elapsed(level, ritual);
            if (elapsed >= DURATION_TICKS) {
                boolean spawned = finish(level, ritual);
                remove(level, ritual);
                cleanup(level, ritual, !spawned);
            } else if (elapsed > 0 && elapsed % 10 == 0) {
                cue(ritual);
                if (elapsed == 40 || elapsed == 80) sound(level, ritual.center(), "special_charge", .55F, .7F + elapsed / 100F);
            }
        }
    }

    static void unload(ServerLevel level) {
        Map<UUID, Ritual> rituals = ACTIVE.remove(level);
        if (rituals != null) for (Ritual ritual : List.copyOf(rituals.values())) {
            log("cancelled", level, ritual, false);
            cleanup(level, ritual, false);
        }
    }

    static void stopServer() {
        for (ServerLevel level : List.copyOf(ACTIVE.keySet())) unload(level);
    }

    private static boolean valid(ServerLevel level, Ritual ritual) {
        ServerPlayer player = ritual.player();
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && player.level() == level
                && level.getDifficulty() != Difficulty.PEACEFUL
                && BeaconRitualInventory.find(player, ritual.id()).isPresent()
                && BeaconSpawnSite.validate(level, ritual.floor(), ritual.center(), ritual.apex()) == BeginResult.STARTED
                && !nearbyApex(level, ritual.center()) && !nearbyRitual(level, ritual.center(), ritual.id());
    }

    private static boolean finish(ServerLevel level, Ritual ritual) {
        if (!valid(level, ritual)) return false;
        ApexEntity apex = ritual.apex();
        apex.moveTo(ritual.center().x, ritual.center().y, ritual.center().z, ritual.player().getYRot(), 0);
        ForgeEventFactory.onFinalizeSpawn(apex, level, level.getCurrentDifficultyAt(apex.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);
        // Mod spawn callbacks may cancel, obstruct the site, or remove the reserved Beacon.
        if (apex.isSpawnCancelled() || !valid(level, ritual)) return false;
        if (!level.addFreshEntity(apex)) return false;
        Optional<ItemStack> payment = BeaconRitualInventory.find(ritual.player(), ritual.id());
        if (payment.isEmpty() || !ritual.player().isAlive() || ritual.player().isRemoved()
                || ritual.player().isSpectator() || ritual.player().level() != level
                || level.getDifficulty() == Difficulty.PEACEFUL || !apex.isAlive() || apex.isRemoved()
                || apex.isSpawnCancelled()
                || BeaconSpawnSite.validate(level, ritual.floor(), ritual.center(), apex) != BeginResult.STARTED) {
            apex.discard();
            return false;
        }
        boolean consumed = !ritual.player().isCreative();
        if (consumed) {
            payment.get().shrink(1);
            apex.setTarget(ritual.player());
        }
        ApexEffects.emit(apex, EffectKind.TELEPORT_IN, ritual.center(), UP, 2.4F, 16);
        ApexEffects.emit(apex, EffectKind.PHASE_TWO, ritual.center(), UP, 2.4F, 12);
        sound(level, ritual.center(), "teleport", 1.1F, .7F);
        ritual.player().displayClientMessage(Component.translatable("message.apexpredator.beacon.summoned"), true);
        ApexProgression.award(ritual.player(), "the_predator_awakens");
        log("finished", level, ritual, consumed);
        return true;
    }

    private static boolean nearbyApex(ServerLevel level, Vec3 center) {
        double radius = EXCLUSION_RADIUS;
        return !level.getEntitiesOfClass(ApexEntity.class, new AABB(center, center).inflate(radius),
                apex -> apex.isAlive() && !apex.isRemoved() && apex.position().distanceToSqr(center) <= radius * radius).isEmpty();
    }

    private static boolean nearbyRitual(ServerLevel level, Vec3 center, UUID excluded) {
        Map<UUID, Ritual> rituals = ACTIVE.get(level);
        if (rituals == null) return false;
        for (Ritual ritual : rituals.values()) {
            if (!ritual.id().equals(excluded) && ritual.center().distanceToSqr(center) <= EXCLUSION_RADIUS * EXCLUSION_RADIUS) return true;
        }
        return false;
    }

    private static boolean activePlayer(UUID player) {
        for (Map<UUID, Ritual> rituals : ACTIVE.values()) if (rituals.containsKey(player)) return true;
        return false;
    }

    private static boolean activeToken(UUID token) {
        for (Map<UUID, Ritual> rituals : ACTIVE.values()) {
            for (Ritual ritual : rituals.values()) if (ritual.id().equals(token)) return true;
        }
        return false;
    }

    private static int elapsed(ServerLevel level, Ritual ritual) {
        return (int) Math.min(DURATION_TICKS, Math.max(0, level.getGameTime() - ritual.startedAt()));
    }

    private static void remove(ServerLevel level, Ritual ritual) {
        Map<UUID, Ritual> rituals = ACTIVE.get(level);
        if (rituals != null) {
            rituals.remove(ritual.player().getUUID(), ritual);
            if (rituals.isEmpty()) ACTIVE.remove(level);
        }
    }

    private static void cleanup(ServerLevel level, Ritual ritual, boolean canceled) {
        BeaconRitualInventory.unmark(ritual.initialStack(), ritual.id());
        BeaconRitualInventory.clear(ritual.player(), ritual.id());
        if (canceled) log("cancelled", level, ritual, false);
        if (canceled && !ritual.player().isRemoved()) {
            ritual.player().displayClientMessage(Component.translatable("message.apexpredator.beacon.canceled"), true);
        }
    }

    private static void log(String transition, ServerLevel level, Ritual ritual, boolean consumed) {
        if (ApexConfig.DEBUG.get()) {
            LogUtils.getLogger().info("[APEX BEACON DEBUG] {} player={} dimension={} center={} elapsed={} consume={}",
                    transition, ritual.player().getGameProfile().getName(), level.dimension().location(),
                    ritual.center(), elapsed(level, ritual), consumed ? 1 : 0);
        }
    }

    private static void cue(Ritual ritual) {
        // Short refreshed cues expire promptly on cancellation without a new network protocol.
        ApexEffects.emit(ritual.player(), EffectKind.WARNING_RING, ritual.center(), UP, 2.4F, 11);
        ApexEffects.emit(ritual.player(), EffectKind.RANGED_PREPARE, ritual.center().add(0, .35, 0), UP, 1.2F, 11);
        if (ritual.player().level() instanceof ServerLevel level) {
            level.sendParticles(ApexEffects.SMOKE.get(), ritual.center().x, ritual.center().y + .15,
                    ritual.center().z, 2, .45, .1, .45, .01);
        }
    }

    private static void sound(ServerLevel level, Vec3 center, String name, float volume, float pitch) {
        level.playSound(null, center.x, center.y, center.z, ApexSounds.event(name), SoundSource.HOSTILE, volume, pitch);
    }
}
