package dev.apexpredator.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Verify editable source projects against the assets that Minecraft actually loads. */
class BlockbenchAssetTest {
    private static final String ASSETS = "/assets/apexpredator/";
    private static final Set<String> FACES = Set.of("north", "south", "east", "west", "up", "down");
    private static final String[] AXES = {"x", "y", "z"};

    @Test
    void apexProjectKeepsTheRealRigParentsPivotsAndAllArmorCubes() throws IOException {
        JsonObject project = project("apex");
        assertEquals("geckolib_model", project.getAsJsonObject("meta").get("model_format").getAsString());
        assertEquals("Entity", project.get("geckolib_model_type").getAsString());
        assertEquals("apexpredator", project.get("geckolib_modid").getAsString());
        Hierarchy hierarchy = hierarchy(project);
        JsonArray bones = resourceJson("geo/apex.geo.json").getAsJsonArray("minecraft:geometry")
                .get(0).getAsJsonObject().getAsJsonArray("bones");
        assertEquals(28, bones.size());
        assertEquals(bones.size(), hierarchy.groups.size());
        assertEquals(333, project.getAsJsonArray("elements").size());
        Map<String, JsonObject> sourceGroups = new HashMap<>();
        for (JsonObject group : hierarchy.groups.values()) assertNull(sourceGroups.put(group.get("name").getAsString(), group));
        Set<String> boneNames = new HashSet<>();
        for (JsonElement value : bones) {
            JsonObject bone = value.getAsJsonObject();
            String name = bone.get("name").getAsString();
            boneNames.add(name);
            JsonObject group = sourceGroups.get(name);
            assertNotNull(group, "Missing editable bone " + name);
            String uuid = group.get("uuid").getAsString();
            String parentUuid = hierarchy.parents.get(uuid);
            String expectedParent = bone.has("parent") ? bone.get("parent").getAsString() : null;
            assertEquals(expectedParent, parentUuid == null ? null : hierarchy.groups.get(parentUuid).get("name").getAsString());
            vectorEquals(bone.getAsJsonArray("pivot"), group.getAsJsonArray("origin"), new double[]{-1, 1, 1});
            JsonArray rotation = bone.has("rotation") ? bone.getAsJsonArray("rotation") : zeroVector();
            vectorEquals(rotation, group.getAsJsonArray("rotation"), new double[]{-1, -1, 1});
            int expectedCubes = bone.has("cubes") ? bone.getAsJsonArray("cubes").size() : 0;
            assertEquals(expectedCubes, hierarchy.cubeCounts.getOrDefault(uuid, 0), "Detached or missing armor in " + name);
        }
        assertEquals(boneNames, sourceGroups.keySet());
    }

    @Test
    void allThirtySevenEditableClipsKeepTimesTransformsAndGeckoEasing() throws IOException {
        JsonObject project = project("apex");
        Hierarchy hierarchy = hierarchy(project);
        JsonObject runtime = resourceJson("animations/apex.animation.json").getAsJsonObject("animations");
        Map<String, JsonObject> clips = byName(project.getAsJsonArray("animations"));
        assertEquals(37, clips.size());
        assertEquals(runtime.keySet(), clips.keySet());
        int frameCount = 0;
        for (String name : runtime.keySet()) {
            JsonObject original = runtime.getAsJsonObject(name);
            JsonObject clip = clips.get(name);
            assertEquals(original.get("animation_length").getAsDouble(), clip.get("length").getAsDouble(), 1e-9, name);
            String loop = original.has("loop") && original.get("loop").isJsonPrimitive()
                    && original.get("loop").getAsJsonPrimitive().isBoolean() && original.get("loop").getAsBoolean() ? "loop"
                    : original.has("loop") && original.get("loop").getAsString().equals("hold_on_last_frame") ? "hold" : "once";
            assertEquals(loop, clip.get("loop").getAsString(), name);
            JsonObject originalBones = original.getAsJsonObject("bones");
            Map<String, JsonObject> animators = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : clip.getAsJsonObject("animators").entrySet()) {
                JsonObject group = hierarchy.groups.get(entry.getKey());
                assertNotNull(group, "Animation targets a removed bone: " + name);
                JsonObject animator = entry.getValue().getAsJsonObject();
                String boneName = group.get("name").getAsString();
                assertEquals(boneName, animator.get("name").getAsString());
                assertEquals("bone", animator.get("type").getAsString());
                assertNull(animators.put(boneName, animator));
            }
            assertEquals(originalBones.keySet(), animators.keySet(), name);
            for (String boneName : originalBones.keySet()) {
                JsonObject channels = originalBones.getAsJsonObject(boneName);
                Map<String, JsonObject> frames = new HashMap<>();
                for (JsonElement value : animators.get(boneName).getAsJsonArray("keyframes")) {
                    JsonObject frame = value.getAsJsonObject();
                    String key = frame.get("channel").getAsString() + ":" + frame.get("time").getAsDouble();
                    assertNull(frames.put(key, frame), "Duplicate animation keyframe " + key);
                }
                int expectedCount = 0;
                for (Map.Entry<String, JsonElement> channel : channels.entrySet()) {
                    Map<Double, JsonElement> originalFrames = new LinkedHashMap<>();
                    if (channel.getValue().isJsonObject()) {
                        channel.getValue().getAsJsonObject().entrySet().forEach(frame -> originalFrames.put(Double.parseDouble(frame.getKey()), frame.getValue()));
                    } else originalFrames.put(0.0, channel.getValue());
                    for (Map.Entry<Double, JsonElement> originalFrame : originalFrames.entrySet()) {
                        expectedCount++;
                        JsonObject editable = frames.get(channel.getKey() + ":" + originalFrame.getKey());
                        assertNotNull(editable, "Changed attack/pose keyframe time in " + name + "/" + boneName);
                        JsonElement raw = originalFrame.getValue();
                        JsonArray vector = raw.isJsonObject() ? raw.getAsJsonObject().getAsJsonArray("vector") : raw.getAsJsonArray();
                        JsonArray data = editable.getAsJsonArray("data_points");
                        assertEquals(1, data.size());
                        double[] signs = switch (channel.getKey()) {
                            case "rotation" -> new double[]{-1, -1, 1};
                            case "position" -> new double[]{-1, 1, 1};
                            case "scale" -> new double[]{1, 1, 1};
                            default -> throw new AssertionError("Unexpected skeletal channel " + channel.getKey());
                        };
                        JsonObject point = data.get(0).getAsJsonObject();
                        for (int axis = 0; axis < 3; axis++) assertEquals(vector.get(axis).getAsDouble() * signs[axis],
                                Double.parseDouble(point.get(AXES[axis]).getAsString()), 1e-9, name + "/" + boneName);
                        if (raw.isJsonObject() && raw.getAsJsonObject().has("easing"))
                            assertEquals(raw.getAsJsonObject().get("easing"), editable.get("easing"), "Lost GeckoLib easing in " + name);
                    }
                }
                assertEquals(expectedCount, frames.size(), name + "/" + boneName);
                frameCount += expectedCount;
            }
        }
        assertEquals(5316, frameCount);
    }

    @Test
    void projectsEmbedTheExactRuntimePngsAndEveryFaceHasAtlasUv() throws IOException {
        for (String name : Set.of("apex", "apex_fang")) {
            JsonObject project = project(name);
            JsonObject meta = project.getAsJsonObject("meta");
            assertEquals("5.0", meta.get("format_version").getAsString());
            assertFalse(meta.get("box_uv").getAsBoolean(), "Per-face armor atlas must not be rebuilt as automatic box UV");
            int width = project.getAsJsonObject("resolution").get("width").getAsInt();
            int height = project.getAsJsonObject("resolution").get("height").getAsInt();
            Set<String> expected = name.equals("apex") ? Set.of("apex.png", "apex_glow.png") : Set.of("apex_fang.png");
            Map<String, JsonObject> textures = byName(project.getAsJsonArray("textures"));
            assertEquals(expected, textures.keySet());
            for (String textureName : expected) {
                JsonObject texture = textures.get(textureName);
                String source = texture.get("source").getAsString();
                assertTrue(source.startsWith("data:image/png;base64,"), "Portable project must contain its actual PNG pixels");
                byte[] embedded = Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
                String folder = name.equals("apex") ? "entity" : "item";
                try (InputStream stream = resource("textures/" + folder + "/" + textureName)) {
                    assertArrayEquals(stream.readAllBytes(), embedded, "Texture editor source differs from shipped atlas " + textureName);
                }
                var image = ImageIO.read(new ByteArrayInputStream(embedded));
                assertNotNull(image);
                assertEquals(width, image.getWidth());
                assertEquals(height, image.getHeight());
            }
            for (JsonElement value : project.getAsJsonArray("elements")) {
                JsonObject cube = value.getAsJsonObject();
                assertEquals("cube", cube.get("type").getAsString());
                assertFalse(cube.get("box_uv").getAsBoolean());
                assertEquals(FACES, cube.getAsJsonObject("faces").keySet());
                for (JsonElement faceValue : cube.getAsJsonObject("faces").asMap().values()) {
                    JsonObject face = faceValue.getAsJsonObject();
                    assertEquals(0, face.get("texture").getAsInt(), "Faces must use the base atlas rather than painting the entire emissive mask");
                    JsonArray uv = face.getAsJsonArray("uv");
                    assertEquals(4, uv.size());
                    for (int axis = 0; axis < 4; axis++) assertTrue(uv.get(axis).getAsDouble() >= 0
                            && uv.get(axis).getAsDouble() <= (axis % 2 == 0 ? width : height), "Out-of-atlas UV coordinate");
                }
            }
        }
    }

    @Test
    void fangProjectKeepsHeldItemTransformsMeshAndRecognizableEnergyGroups() throws IOException {
        JsonObject project = project("apex_fang");
        assertEquals("java_block", project.getAsJsonObject("meta").get("model_format").getAsString());
        JsonObject model = resourceJson("models/item/apex_fang.json");
        JsonArray cubes = project.getAsJsonArray("elements");
        JsonArray elements = model.getAsJsonArray("elements");
        assertEquals(56, cubes.size());
        assertEquals(elements.size(), cubes.size());
        assertEquals(model.getAsJsonObject("display"), project.getAsJsonObject("display"), "First/third person, menu and dropped-item transforms must survive editing");
        Hierarchy hierarchy = hierarchy(project);
        Set<String> names = new HashSet<>();
        hierarchy.groups.values().forEach(group -> names.add(group.get("name").getAsString()));
        assertTrue(names.containsAll(Set.of("energy_channel", "core", "accent_left", "accent_right")), "Forge fullbright selection must survive a native editor save");
        int energy = 0;
        for (int index = 0; index < cubes.size(); index++) {
            JsonObject cube = cubes.get(index).getAsJsonObject();
            JsonObject element = elements.get(index).getAsJsonObject();
            vectorEquals(element.getAsJsonArray("from"), cube.getAsJsonArray("from"), new double[]{1, 1, 1});
            vectorEquals(element.getAsJsonArray("to"), cube.getAsJsonArray("to"), new double[]{1, 1, 1});
            String parent = hierarchy.parents.get(cube.get("uuid").getAsString());
            boolean emissive = false;
            while (parent != null) {
                JsonObject group = hierarchy.groups.get(parent);
                emissive |= Set.of("energy_channel", "core", "accent_left", "accent_right").contains(group.get("name").getAsString());
                parent = hierarchy.parents.get(parent);
            }
            assertEquals(element.has("forge_data"), emissive, "Lost glow semantics in cube " + index);
            if (emissive) energy++;
        }
        assertEquals(9, energy);
    }

    private static Hierarchy hierarchy(JsonObject project) {
        Map<String, JsonObject> groups = new HashMap<>();
        Map<String, JsonObject> cubes = new HashMap<>();
        for (JsonElement value : project.getAsJsonArray("groups")) {
            JsonObject group = value.getAsJsonObject();
            assertNull(groups.put(group.get("uuid").getAsString(), group));
        }
        for (JsonElement value : project.getAsJsonArray("elements")) {
            JsonObject cube = value.getAsJsonObject();
            String uuid = cube.get("uuid").getAsString();
            assertFalse(groups.containsKey(uuid));
            assertNull(cubes.put(uuid, cube));
        }
        Map<String, String> parents = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Set<String> visited = new HashSet<>();
        walk(project.getAsJsonArray("outliner"), null, groups, cubes, parents, counts, visited);
        Set<String> expected = new HashSet<>(groups.keySet());
        expected.addAll(cubes.keySet());
        assertEquals(expected, visited, "Unreachable/detached editor parts");
        return new Hierarchy(groups, parents, counts);
    }

    private static void walk(JsonArray nodes, String parent, Map<String, JsonObject> groups, Map<String, JsonObject> cubes,
                             Map<String, String> parents, Map<String, Integer> counts, Set<String> visited) {
        for (JsonElement value : nodes) {
            String uuid = value.isJsonObject() ? value.getAsJsonObject().get("uuid").getAsString() : value.getAsString();
            assertTrue(visited.add(uuid), "Duplicate/cyclic outliner part " + uuid);
            if (parent != null) parents.put(uuid, parent);
            if (value.isJsonObject()) {
                assertTrue(groups.containsKey(uuid), "Outliner references an absent group");
                walk(value.getAsJsonObject().getAsJsonArray("children"), uuid, groups, cubes, parents, counts, visited);
            } else {
                assertTrue(cubes.containsKey(uuid), "Outliner references an absent cube");
                if (parent != null) counts.merge(parent, 1, Integer::sum);
            }
        }
    }

    private static Map<String, JsonObject> byName(JsonArray values) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (JsonElement value : values) {
            JsonObject object = value.getAsJsonObject();
            assertNull(result.put(object.get("name").getAsString(), object), "Duplicate editor asset name");
        }
        return result;
    }

    private static void vectorEquals(JsonArray expected, JsonArray actual, double[] signs) {
        assertNotNull(actual);
        assertEquals(3, actual.size());
        for (int axis = 0; axis < 3; axis++) assertEquals(expected.get(axis).getAsDouble() * signs[axis], actual.get(axis).getAsDouble(), 1e-9);
    }

    private static JsonArray zeroVector() { return JsonParser.parseString("[0,0,0]").getAsJsonArray(); }

    private static JsonObject project(String name) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of("models", "blockbench", name + ".bbmodel"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = BlockbenchAssetTest.class.getResourceAsStream(ASSETS + path);
        assertNotNull(stream, "Missing packaged runtime resource " + path);
        return stream;
    }

    private record Hierarchy(Map<String, JsonObject> groups, Map<String, String> parents, Map<String, Integer> cubeCounts) { }
}
