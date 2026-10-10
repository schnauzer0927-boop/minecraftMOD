package dev.apexpredator.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Owned remote fixtures prevent the production 64-block summoning exclusion from seeing other tests. */
final class ProgressionGameTestArena implements AutoCloseable {
    private static final AtomicInteger NEXT = new AtomicInteger();
    private static final Set<ProgressionGameTestArena> OPEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Entity> owned = new ArrayList<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Runnable> cleanup = new ArrayList<>();
    private final Map<BlockPos, BlockState> previousBlocks = new HashMap<>();
    final ServerLevel level;
    final BlockPos origin;
    private final ChunkPos chunk;
    private final boolean previouslyForced;
    private boolean closed;

    static {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppingEvent.class, event -> {
            for (ProgressionGameTestArena arena : List.copyOf(OPEN)) {
                if (arena.level.getServer() == event.getServer()) arena.close();
            }
        });
    }

    ProgressionGameTestArena(GameTestHelper helper) {
        level = helper.getLevel();
        int index = NEXT.incrementAndGet();
        origin = new BlockPos(30_000 + index * 256, 120, 30_000);
        chunk = new ChunkPos(origin);
        previouslyForced = level.getForcedChunks().contains(chunk.toLong());
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunk(chunk.x, chunk.z);
        OPEN.add(this);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            for (int y = -1; y <= 6; y++) {
                BlockPos pos = block(x, y, z);
                previousBlocks.put(pos, level.getBlockState(pos));
                level.setBlock(pos, y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // A real connection normally calls doTick(); these inert test connections have no inbound network loop.
        // Retain the actual Player physics, equipment updates, attack ticker, cooldowns and Forge PlayerTick events.
        helper.onEachTick(() -> {
            if (closed) return;
            for (ServerPlayer player : List.copyOf(players)) {
                if (!player.isRemoved() && player.serverLevel() == level) player.doTick();
            }
        });
    }

    Vec3 point(double x, double y, double z) { return new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z); }
    BlockPos block(int x, int y, int z) { return origin.offset(x, y, z); }
    AABB bounds() { return new AABB(origin).inflate(18); }

    ServerPlayer player(boolean creative, double x, double z) {
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "apxt" + NEXT.get() + "p" + players.size()));
        // Register an ordinary ServerPlayer in the world without running a nonexistent Netty login.
        // Only packet delivery is inert: native movement, survival, inventory and attributes remain intact.
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { }
        };
        players.add(player);
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        Vec3 feet = point(x, 0, z);
        player.moveTo(feet.x, feet.y, feet.z, -90, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(false);
        level.addNewPlayer(player);
        return player;
    }

    <T extends Mob> T mob(EntityType<T> type, double x, double z) {
        T mob = type.create(level);
        if (mob == null) throw new IllegalStateException("Registered entity factory returned null");
        mob.moveTo(point(x, 0, z));
        mob.setNoAi(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        mob.setHealth(1000);
        if (mob.getAttribute(Attributes.ARMOR) != null) mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
        if (!level.addFreshEntity(mob)) throw new IllegalStateException("Test mob could not enter its owned arena");
        owned.add(mob);
        return mob;
    }

    void own(Entity entity) { owned.add(entity); }
    void onClose(Runnable action) { cleanup.add(action); }

    void succeed(GameTestHelper helper) {
        close();
        helper.succeed();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        cleanup.forEach(Runnable::run);
        for (ServerPlayer player : players) {
            if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                level.getServer().getPlayerList().remove(player);
            }
            if (!player.isRemoved()) player.discard();
        }
        for (Entity entity : owned) if (!entity.isRemoved()) entity.discard();
        // Only entities inside this fixture's one chunk are ours; never touch another test's Apex.
        for (Entity entity : level.getEntities((Entity) null, new AABB(origin, origin.offset(16, 8, 16)))) {
            entity.discard();
        }
        previousBlocks.forEach((pos, state) -> level.setBlock(pos, state, 2));
        level.setChunkForced(chunk.x, chunk.z, previouslyForced);
        OPEN.remove(this);
    }
}
