package dev.spacemountain.verify;

import com.google.gson.*;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Independent test MOD. Never included in the schematic distribution. */
@Mod("space_mountain_verifier")
public final class SpaceMountainVerifier {
    public SpaceMountainVerifier() {
        NeoForge.EVENT_BUS.addListener(this::started);
    }

    private static int integer(JsonArray a, int i) { return a.get(i).getAsInt(); }
    private static String hash(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }
    private static JsonArray xyz(int x, int y, int z) {
        JsonArray a = new JsonArray(); a.add(x); a.add(y); a.add(z); return a;
    }

    private void started(ServerStartedEvent event) {
        var server = event.getServer();
        JsonObject report = new JsonObject();
        report.addProperty("validation_method", "Minecraft_server_NeoForge_WorldEdit_paste_API");
        report.addProperty("minecraft_version", "1.21.1");
        report.addProperty("neoforge_version", "21.1.228");
        report.addProperty("worldedit_version", "7.3.8");
        report.addProperty("players_connected", server.getPlayerCount());
        report.addProperty("client_gameplay_verified", false);
        report.addProperty("human_walk_verified", false);
        report.addProperty("full_user_mod_environment_verified", false);
        report.addProperty("online_mode", server.usesAuthentication());
        report.addProperty("server_bound_ip", server.getLocalIp());
        report.addProperty("world_name", server.getWorldData().getLevelName());
        report.addProperty("passed", false);
        try {
            Path manifestPath = Path.of("verification-manifest.json");
            JsonObject manifest = JsonParser.parseString(Files.readString(manifestPath)).getAsJsonObject();
            Path file = Path.of(manifest.get("schematic_path").getAsString());
            String expectedHash = manifest.get("sha256").getAsString();
            String actualHash = hash(file);
            if (!actualHash.equals(expectedHash)) throw new IllegalStateException("Schematic SHA256 changed after approval");
            report.addProperty("schematic_path", file.toString());
            report.addProperty("schematic_sha256", actualHash);
            report.addProperty("manifest_sha256", hash(manifestPath));
            Clipboard clipboard;
            var format = ClipboardFormats.findByFile(file.toFile());
            if (format == null) throw new IllegalStateException("WorldEdit could not select schematic reader");
            try (var input = Files.newInputStream(file); var reader = format.getReader(input)) {
                clipboard = reader.read();
            }
            ServerLevel level = server.overworld();
            JsonArray anchor = manifest.getAsJsonArray("paste_anchor");
            int ax = integer(anchor, 0), ay = integer(anchor, 1), az = integer(anchor, 2);
            BlockVector3 to = BlockVector3.at(ax, ay, az);
            report.add("paste_anchor", xyz(ax, ay, az));
            report.add("clipboard_origin", xyz(clipboard.getOrigin().x(), clipboard.getOrigin().y(), clipboard.getOrigin().z()));
            long started = System.nanoTime();
            try (EditSession edit = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(NeoForgeAdapter.adapt(level)).maxBlocks(-1).build()) {
                Operations.complete(new ClipboardHolder(clipboard).createPaste(edit)
                    .to(to).ignoreAirBlocks(true).copyEntities(false).copyBiomes(false).build());
            }
            report.addProperty("paste_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
            report.addProperty("ignore_air_blocks", true);
            long checked = 0, mismatched = 0;
            JsonArray examples = new JsonArray();
            for (BlockVector3 p : clipboard.getRegion()) {
                var expected = clipboard.getBlock(p);
                if (expected.getBlockType().getMaterial().isAir()) continue;
                BlockVector3 dest = p.subtract(clipboard.getOrigin()).add(to);
                var actual = NeoForgeAdapter.adapt(level.getBlockState(NeoForgeAdapter.toBlockPos(dest)));
                checked++;
                if (!actual.equals(expected)) {
                    mismatched++;
                    if (examples.size() < 20) {
                        JsonObject e = new JsonObject(); e.add("position", xyz(dest.x(), dest.y(), dest.z()));
                        e.addProperty("expected", expected.getAsString()); e.addProperty("actual", actual.getAsString()); examples.add(e);
                    }
                }
            }
            report.addProperty("non_air_blocks_checked", checked);
            report.addProperty("blockstate_mismatches", mismatched);
            report.add("blockstate_mismatch_examples", examples);
            long protectedChecked = 0, protectedOccupied = 0;
            for (JsonElement volume : manifest.getAsJsonArray("protected_air_volumes")) {
                JsonObject v = volume.getAsJsonObject(); JsonArray lo = v.getAsJsonArray("min"), hi = v.getAsJsonArray("max");
                for (int x = integer(lo, 0); x <= integer(hi, 0); x++)
                    for (int y = integer(lo, 1); y <= integer(hi, 1); y++)
                        for (int z = integer(lo, 2); z <= integer(hi, 2); z++) {
                            protectedChecked++;
                            if (!level.getBlockState(new BlockPos(x + ax, y + ay, z + az)).isAir()) protectedOccupied++;
                        }
            }
            report.addProperty("protected_air_cells_checked", protectedChecked);
            report.addProperty("protected_air_occupied", protectedOccupied);
            JsonArray walks = new JsonArray(); long blocked = 0, unsupported = 0;
            for (JsonElement item : manifest.getAsJsonArray("walking_checkpoints")) {
                JsonArray p = item.getAsJsonArray(); int x = integer(p, 0) + ax, y = integer(p, 1) + ay, z = integer(p, 2) + az;
                BlockPos below = new BlockPos(x, y - 1, z);
                boolean support = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
                boolean clear = level.noCollision(new AABB(x + .2, y, z + .2, x + .8, y + 1.8, z + .8));
                if (!support) unsupported++; if (!clear) blocked++;
                JsonObject w = new JsonObject(); w.add("relative_feet", p);
                w.addProperty("solid_collision_support", support); w.addProperty("player_size_collision_clear", clear); walks.add(w);
            }
            report.add("walking_checkpoints", walks);
            report.addProperty("blocked_walking_checkpoints", blocked);
            report.addProperty("unsupported_walking_checkpoints", unsupported);
            report.addProperty("passed", mismatched == 0 && protectedOccupied == 0 && blocked == 0 && unsupported == 0);
            server.saveEverything(false, true, true);
            report.addProperty("world_save_requested", true);
        } catch (Throwable failure) {
            report.addProperty("exception", failure.toString());
            failure.printStackTrace();
        } finally {
            try {
                Files.writeString(Path.of("runtime-verification.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
                System.out.println("SPACE_MOUNTAIN_RUNTIME_VERIFICATION passed=" + report.get("passed"));
            } catch (Exception error) { error.printStackTrace(); }
            server.halt(false);
        }
    }
}
