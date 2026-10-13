import com.google.gson.*;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.platform.PlatformsRegisteredEvent;
import com.sk89q.worldedit.extension.platform.*;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.clipboard.*;
import com.sk89q.worldedit.extent.clipboard.io.*;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.*;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.*;
import com.sk89q.worldedit.world.registry.*;
import org.enginehub.piston.CommandManager;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Runs the unmodified official WorldEdit 7.3.8 schematic reader.
 * A standalone platform supplies vanilla 1.21.1 property schemas from mcmeta.
 * This checks the real reader and clipboard, not an in-game paste operation.
 */
public final class ReadSchem {
    private static final Map<String, Map<String, Property<?>>> PROPERTIES = new HashMap<>();
    private static final Map<String, Map<String, String>> DEFAULTS = new HashMap<>();

    private static final class HarnessPlatform extends AbstractPlatform {
        private final LocalConfiguration config = new LocalConfiguration() {
            @Override public void load() {}
        };
        private final BlockRegistry blocks = new BundledBlockRegistry() {
            @Override public Map<String, ? extends Property<?>> getProperties(BlockType type) {
                return PROPERTIES.getOrDefault(type.id(), Collections.emptyMap());
            }
        };
        private final Registries registries = new BundledRegistries() {
            @Override public BlockRegistry getBlockRegistry() { return blocks; }
        };
        @Override public Registries getRegistries() { return registries; }
        @Override public int getDataVersion() { return 3955; }
        @Override public boolean isValidMobType(String id) { return false; }
        @Override public com.sk89q.worldedit.entity.Player matchPlayer(com.sk89q.worldedit.entity.Player p) {
            return null;
        }
        @Override public World matchWorld(World w) { return null; }
        @Override public void registerCommands(CommandManager commands) {}
        @Override public void setGameHooksEnabled(boolean enabled) {}
        @Override public LocalConfiguration getConfiguration() { return config; }
        @Override public String getVersion() { return "7.3.8"; }
        @Override public String getPlatformName() { return "Standalone reader validation"; }
        @Override public String getPlatformVersion() { return "1.21.1"; }
        @Override public Map<Capability, Preference> getCapabilities() {
            return Map.of(Capability.WORLD_EDITING, Preference.NORMAL,
                Capability.GAME_HOOKS, Preference.NORMAL,
                Capability.CONFIGURATION, Preference.NORMAL);
        }
        @Override public Set<SideEffect> getSupportedSideEffects() { return Collections.emptySet(); }
    }

    private static <T> BlockState with(BlockState state, Property<T> property, String value) {
        return state.with(property, property.getValueFor(value));
    }

    private static void initialize(Path registryFile) throws IOException {
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(registryFile)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (var entry : json.entrySet()) {
            String id = "minecraft:" + entry.getKey();
            JsonArray info = entry.getValue().getAsJsonArray();
            Map<String, Property<?>> properties = new LinkedHashMap<>();
            for (var property : info.get(0).getAsJsonObject().entrySet()) {
                List<String> values = new ArrayList<>();
                property.getValue().getAsJsonArray().forEach(value -> values.add(value.getAsString()));
                properties.put(property.getKey(), new EnumProperty(property.getKey(), values));
            }
            PROPERTIES.put(id, properties);
            Map<String, String> defaults = new LinkedHashMap<>();
            for (var property : info.get(1).getAsJsonObject().entrySet()) {
                defaults.put(property.getKey(), property.getValue().getAsString());
            }
            DEFAULTS.put(id, defaults);
        }
        for (var entry : DEFAULTS.entrySet()) {
            String id = entry.getKey();
            BlockType.REGISTRY.register(id, new BlockType(id, state -> {
                for (var property : entry.getValue().entrySet()) {
                    state = with(state, PROPERTIES.get(id).get(property.getKey()), property.getValue());
                }
                return state;
            }));
        }
        WorldEdit.getInstance().getPlatformManager().register(new HarnessPlatform());
        WorldEdit.getInstance().getEventBus().post(new PlatformsRegisteredEvent());
    }

    private static List<Integer> vector(BlockVector3 vector) {
        return List.of(vector.x(), vector.y(), vector.z());
    }

    private static Map<String, String> canonicalize(Path paletteFile) throws Exception {
        JsonArray palette;
        try (Reader reader = Files.newBufferedReader(paletteFile)) {
            palette = JsonParser.parseReader(reader).getAsJsonArray();
        }
        ParserContext context = new ParserContext();
        context.setRestricted(false);
        context.setTryLegacy(false);
        context.setPreferringWildcard(false);
        Map<String, String> result = new TreeMap<>();
        for (JsonElement entry : palette) {
            String state = entry.getAsString();
            // Match ReaderUtil.decodePalette's parser options, but propagate an
            // invalid state instead of letting the reader replace it with air.
            result.put(state, WorldEdit.getInstance().getBlockFactory()
                .parseFromInput(state, context).toImmutableState().getAsString());
        }
        return result;
    }

    private static Map<String, Object> read(File file) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IOException("WorldEdit did not recognize " + file);
        try (InputStream input = new FileInputStream(file);
             ClipboardReader reader = format.getReader(input)) {
            Clipboard clipboard = reader.read();
            Map<String, Long> counts = new TreeMap<>();
            Map<BlockState, String> strings = new HashMap<>();
            MessageDigest coordinates = MessageDigest.getInstance("SHA-256");
            long nonAir = 0;
            long volume = 0;
            BlockVector3 minimum = clipboard.getMinimumPoint();
            BlockVector3 maximum = clipboard.getMaximumPoint();
            for (int y = minimum.y(); y <= maximum.y(); y++) {
                for (int z = minimum.z(); z <= maximum.z(); z++) {
                    for (int x = minimum.x(); x <= maximum.x(); x++) {
                        BlockState state = clipboard.getBlock(BlockVector3.at(x, y, z));
                        String text = strings.computeIfAbsent(state, BlockState::getAsString);
                        counts.merge(text, 1L, Long::sum);
                        String id = state.getBlockType().id();
                        if (!Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air").contains(id)) {
                            nonAir++;
                            coordinates.update((x + "," + y + "," + z + "=" + text + "\n").getBytes(StandardCharsets.UTF_8));
                        }
                        volume++;
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("file", file.getCanonicalPath());
            result.put("format", format.getName());
            result.put("reader", reader.getClass().getName());
            result.put("dimensions", vector(clipboard.getDimensions()));
            result.put("minimum", vector(clipboard.getMinimumPoint()));
            result.put("maximum", vector(clipboard.getMaximumPoint()));
            result.put("origin", vector(clipboard.getOrigin()));
            result.put("volume", volume);
            result.put("nonAir", nonAir);
            result.put("decodedStateCount", counts.size());
            result.put("counts", counts);
            result.put("nonAirCoordinateSha256", HexFormat.of().formatHex(coordinates.digest()));
            return result;
        }
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            if (args.length < 3) throw new IllegalArgumentException("Usage: ReadSchem registry.json palette.json file.schem [...]");
            initialize(Path.of(args[0]));
            Map<String, String> canonicalStates = canonicalize(Path.of(args[1]));
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 2; i < args.length; i++) results.add(read(new File(args[i])));
            System.out.println(new Gson().toJson(Map.of("canonicalStates", canonicalStates, "results", results)));
        } catch (Exception error) {
            System.err.println(error.getClass().getName() + ": " + error.getMessage());
            exitCode = 1;
        } finally {
            // WorldEdit's session scheduler holds a non-daemon thread.
            System.exit(exitCode);
        }
    }
}
