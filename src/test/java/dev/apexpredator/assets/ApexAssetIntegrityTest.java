package dev.apexpredator.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the shipped assets themselves: a renderer cannot repair a broken rig or missing animation. */
class ApexAssetIntegrityTest {
    private static final String ASSETS = "/assets/apexpredator/";
    private static final Set<String> REQUIRED_BONES = Set.of(
            "root", "body", "chest", "waist", "head", "neck", "left_shoulder", "right_shoulder",
            "left_arm", "right_arm", "left_forearm", "right_forearm", "left_blade", "right_blade",
            "left_hand", "right_hand", "left_leg", "right_leg", "left_lower_leg", "right_lower_leg",
            "left_foot", "right_foot", "back_spines", "back_blade_left", "back_blade_right",
            "tail_or_rear_spine", "eye_glow");
    private static final Set<String> REQUIRED_ANIMATIONS = Set.of(
            "idle", "walk", "run", "sprint", "strafe_left", "strafe_right", "melee_left", "melee_right",
            "blade_combo_1", "blade_combo_2", "blade_combo_3", "charge_prepare", "charge", "charge_end",
            "jump_prepare", "jump", "slam", "dodge_left", "dodge_right", "backstep", "teleport_start",
            "teleport_end", "shockwave_prepare", "shockwave_attack", "ranged_prepare", "ranged_attack",
            "special_prepare", "special_attack", "phase_change", "hurt_light", "hurt_heavy", "stagger",
            "recovery", "death", "rage_idle", "rage_run", "rage_attack");

    @Test
    void articulatedRigHasCompleteAcyclicHierarchyAndFiniteGeometry() throws IOException {
        JsonObject geometry = geometry();
        Map<String, JsonObject> bones = bones(geometry);
        assertTrue(bones.keySet().containsAll(REQUIRED_BONES), "The articulated predator rig is incomplete");
        assertTrue(bones.containsKey("jaw") || bones.containsKey("face_plate"), "Missing movable face armor");
        assertFalse(bones.get("root").has("parent"), "Root must anchor the skeleton");
        JsonObject description = geometry.getAsJsonObject("description");
        int textureWidth = description.get("texture_width").getAsInt();
        int textureHeight = description.get("texture_height").getAsInt();
        int cubes = 0;
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, JsonObject> entry : bones.entrySet()) {
            JsonObject bone = entry.getValue();
            assertFiniteVector(bone.getAsJsonArray("pivot"), "pivot " + entry.getKey());
            if (bone.has("rotation")) assertFiniteVector(bone.getAsJsonArray("rotation"), "rotation " + entry.getKey());
            Set<String> visited = new HashSet<>();
            String current = entry.getKey();
            while (bones.get(current).has("parent")) {
                assertTrue(visited.add(current), "Cyclic bone parent hierarchy at " + current);
                current = bones.get(current).get("parent").getAsString();
                assertTrue(bones.containsKey(current), "Bone references absent parent " + current);
            }
            if (!bone.has("cubes")) continue;
            for (JsonElement element : bone.getAsJsonArray("cubes")) {
                JsonObject cube = element.getAsJsonObject();
                assertFiniteVector(cube.getAsJsonArray("origin"), "cube origin");
                JsonArray size = cube.getAsJsonArray("size");
                assertFiniteVector(size, "cube size");
                for (JsonElement dimension : size) assertTrue(dimension.getAsDouble() >= 0, "Negative cube dimension");
                assertTrue(size.asList().stream().anyMatch(axis -> axis.getAsDouble() > 0), "Empty cube");
                if (cube.has("rotation")) assertFiniteVector(cube.getAsJsonArray("rotation"), "cube rotation");
                if (cube.has("pivot")) assertFiniteVector(cube.getAsJsonArray("pivot"), "cube pivot");
                assertTrue(cube.has("uv"), "Armor cube has no texture coordinates");
                validateUv(cube.get("uv"), size, textureWidth, textureHeight);
                double originY = cube.getAsJsonArray("origin").get(1).getAsDouble();
                lowest = Math.min(lowest, originY);
                highest = Math.max(highest, originY + size.get(1).getAsDouble());
                cubes++;
            }
        }
        assertTrue(cubes >= 40, "Dedicated armored model requires its silhouette geometry");
        assertTrue(lowest <= 1 && lowest >= -1, "Feet must meet the ground at model origin");
        assertTrue(highest >= 2.6 * 16 && highest <= 2.8 * 16,
                "Nominal model crown must match the 2.6–2.8-block unscaled combat rig, height=" + highest / 16);
        assertTrue(description.get("visible_bounds_height").getAsDouble() >= 2.8,
                "Animated blades and fins must remain inside render bounds");
    }

    @Test
    void everyRequiredAnimationHasValidKeyframesAndExistingBoneTargets() throws IOException {
        Set<String> rig = bones(geometry()).keySet();
        JsonObject animations = json("animations/apex.animation.json").getAsJsonObject("animations");
        for (String required : REQUIRED_ANIMATIONS) {
            String name = "animation.apex." + required;
            assertTrue(animations.has(name), "Missing " + name);
            JsonObject animation = animations.getAsJsonObject(name);
            double length = animation.get("animation_length").getAsDouble();
            assertTrue(Double.isFinite(length) && length > 0 && length <= 10, "Invalid duration: " + name);
            JsonObject transforms = animation.getAsJsonObject("bones");
            assertNotNull(transforms, "Animation has no skeletal movement: " + name);
            assertTrue(transforms.size() >= 3, "Animation must move the body and supporting limbs: " + name);
            int movingBones = 0;
            for (Map.Entry<String, JsonElement> bone : transforms.entrySet()) {
                assertTrue(rig.contains(bone.getKey()), name + " references absent bone " + bone.getKey());
                boolean moving = false;
                for (Map.Entry<String, JsonElement> channel : bone.getValue().getAsJsonObject().entrySet()) {
                    if (channel.getValue().isJsonObject()) {
                        Set<JsonElement> poses = new HashSet<>();
                        for (Map.Entry<String, JsonElement> frame : channel.getValue().getAsJsonObject().entrySet()) {
                            double time = Double.parseDouble(frame.getKey());
                            assertTrue(Double.isFinite(time) && time >= 0 && time <= length + .00001,
                                    "Keyframe lies outside " + name + ": " + time);
                            assertFiniteNumbers(frame.getValue(), name);
                            JsonElement value = frame.getValue();
                            poses.add(value.isJsonObject() ? value.getAsJsonObject().get("vector") : value);
                        }
                        moving |= poses.size() > 1;
                    } else assertFiniteNumbers(channel.getValue(), name);
                }
                if (moving) movingBones++;
            }
            assertTrue(movingBones >= 3, "Clip contains only static reset poses: " + name);
        }
        assertTrue(animations.getAsJsonObject("animation.apex.phase_change").getAsJsonObject("bones").has("back_spines"),
                "Phase transition must visibly deploy the rear armor");
        assertTrue(animations.getAsJsonObject("animation.apex.blade_combo_3").getAsJsonObject("bones").has("right_blade")
                        || animations.getAsJsonObject("animation.apex.blade_combo_3").getAsJsonObject("bones").has("left_blade"),
                "Combo finisher must animate a blade, rather than only the torso");
    }

    @Test
    void emissiveMaskMatchesAtlasAndOnlyIlluminatesTexturedArmor() throws IOException {
        BufferedImage base = png("textures/entity/apex.png");
        BufferedImage glow = png("textures/entity/apex_glow.png");
        JsonObject description = geometry().getAsJsonObject("description");
        assertEquals(description.get("texture_width").getAsInt(), base.getWidth(), "Atlas width differs from geometry");
        assertEquals(description.get("texture_height").getAsInt(), base.getHeight(), "Atlas height differs from geometry");
        assertEquals(base.getWidth(), glow.getWidth(), "Emissive and armor atlas widths differ");
        assertEquals(base.getHeight(), glow.getHeight(), "Emissive and armor atlas heights differ");
        assertTrue(base.getWidth() >= 128 && base.getHeight() >= 128, "Armor must have a dedicated detailed atlas");
        int glowingPixels = 0;
        int darkPixels = 0;
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int armor = base.getRGB(x, y);
                int emissive = glow.getRGB(x, y);
                int armorAlpha = armor >>> 24;
                int glowAlpha = emissive >>> 24;
                if (glowAlpha > 0) {
                    assertTrue(armorAlpha > 0, "Emissive mask illuminates transparent texels at " + x + "," + y);
                    int red = (emissive >>> 16) & 255;
                    int green = (emissive >>> 8) & 255;
                    int blue = emissive & 255;
                    assertTrue(red > blue && green > blue, "Core and armor seams must glow orange/red at " + x + "," + y);
                    glowingPixels++;
                }
                if (armorAlpha > 0 && ((armor >>> 16) & 255) < 100 && ((armor >>> 8) & 255) < 100 && (armor & 255) < 100) darkPixels++;
            }
        }
        int pixels = base.getWidth() * base.getHeight();
        assertTrue(glowingPixels >= 32, "Eyes/core/blades have no meaningful emissive texture");
        assertTrue(glowingPixels < pixels / 2, "Emissive mask must preserve dark armor contrast");
        assertTrue(darkPixels > pixels / 4, "Missing dark gunmetal armor material");
    }

    private static JsonObject geometry() throws IOException {
        return json("geo/apex.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static Map<String, JsonObject> bones(JsonObject geometry) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            assertNull(result.put(name, bone), "Duplicate skeleton bone " + name);
        }
        return result;
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = resource(path); InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static BufferedImage png(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            BufferedImage result = ImageIO.read(stream);
            assertNotNull(result, "Not a valid PNG: " + path);
            return result;
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = ApexAssetIntegrityTest.class.getResourceAsStream(ASSETS + path);
        assertNotNull(stream, "Missing shipped asset: " + path);
        return stream;
    }

    private static void assertFiniteVector(JsonArray vector, String description) {
        assertNotNull(vector, "Missing " + description);
        assertEquals(3, vector.size(), description);
        for (JsonElement value : vector) assertTrue(Double.isFinite(value.getAsDouble()), description);
    }

    private static void assertFiniteNumbers(JsonElement element, String description) {
        if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) assertFiniteNumbers(child, description);
        else if (element.isJsonObject()) for (JsonElement child : element.getAsJsonObject().asMap().values()) assertFiniteNumbers(child, description);
        else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            assertTrue(Double.isFinite(element.getAsDouble()), "Nonfinite transform in " + description);
        }
    }

    private static void validateUv(JsonElement uv, JsonArray size, int width, int height) {
        if (uv.isJsonArray()) {
            JsonArray start = uv.getAsJsonArray();
            assertEquals(2, start.size(), "Box UV must have u/v coordinates");
            double u = start.get(0).getAsDouble();
            double v = start.get(1).getAsDouble();
            assertTrue(u >= 0 && v >= 0 && u + 2 * (size.get(0).getAsDouble() + size.get(2).getAsDouble()) <= width + .001
                            && v + size.get(1).getAsDouble() + size.get(2).getAsDouble() <= height + .001,
                    "Box UV escapes the material atlas");
        } else {
            for (Map.Entry<String, JsonElement> face : uv.getAsJsonObject().entrySet()) {
                JsonObject faceUv = face.getValue().getAsJsonObject();
                JsonArray start = faceUv.getAsJsonArray("uv");
                JsonArray extent = faceUv.getAsJsonArray("uv_size");
                assertNotNull(start, "Face UV is missing coordinates");
                assertNotNull(extent, "Face UV is missing size");
                double u = start.get(0).getAsDouble();
                double v = start.get(1).getAsDouble();
                double endU = u + extent.get(0).getAsDouble();
                double endV = v + extent.get(1).getAsDouble();
                assertTrue(Math.min(u, endU) >= 0 && Math.max(u, endU) <= width + .001
                                && Math.min(v, endV) >= 0 && Math.max(v, endV) <= height + .001,
                        "Face UV escapes atlas: " + face.getKey());
            }
        }
    }
}
