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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Validate shipped progression data, including the constraints of the actual 1.20.1 item model parser. */
class ProgressionAssetTest {
    private static final String ASSETS = "/assets/apexpredator/";
    private static final String DATA = "/data/apexpredator/";
    private static final Set<String> FACES = Set.of("down", "up", "north", "south", "west", "east");
    private static final Set<Double> ROTATIONS = Set.of(-45.0, -22.5, 0.0, 22.5, 45.0);

    @Test
    void beaconRecipeRetainsTheFullSummoningCostAndPattern() throws IOException {
        JsonObject recipe = json(DATA + "recipes/apex_beacon.json");
        assertEquals(Set.of("NEN", "CSC", "OEO"), strings(recipe.getAsJsonArray("pattern")));
        assertEquals("NEN", recipe.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals("CSC", recipe.getAsJsonArray("pattern").get(1).getAsString());
        assertEquals("OEO", recipe.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals(Map.of("minecraft:netherite_ingot", 2, "minecraft:ender_eye", 2,
                "minecraft:echo_shard", 2, "minecraft:nether_star", 1,
                "minecraft:crying_obsidian", 2), ingredientCounts(recipe));
        assertResult(recipe, "apexpredator:apex_beacon");
    }

    @Test
    void fangRecipeConsumesFourRealApexCoresAndANetheriteSword() throws IOException {
        JsonObject recipe = json(DATA + "recipes/apex_fang.json");
        assertEquals("CNC", recipe.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals("CSC", recipe.getAsJsonArray("pattern").get(1).getAsString());
        assertEquals("BNB", recipe.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals(Map.of("apexpredator:apex_core", 4, "minecraft:netherite_ingot", 2,
                "minecraft:netherite_sword", 1, "minecraft:blaze_rod", 2), ingredientCounts(recipe));
        assertResult(recipe, "apexpredator:apex_fang");
    }

    @Test
    void dedicatedMeshesUseLegalVanillaElementsAndWorkingForgeFullbrightGrooves() throws IOException {
        for (String item : Set.of("apex_beacon", "apex_fang")) {
            JsonObject model = json(ASSETS + "models/item/" + item + ".json");
            assertFalse(model.has("loader"), "Ordinary held items must not require an entity renderer");
            assertFalse(model.has("parent"), "A generated-item parent can flatten the dedicated cuboid mesh");
            assertEquals("front", model.get("gui_light").getAsString());
            JsonObject textures = model.getAsJsonObject("textures");
            assertEquals("apexpredator:item/" + item, textures.get("atlas").getAsString());
            JsonArray elements = model.getAsJsonArray("elements");
            assertTrue(elements.size() >= 20, "Missing the dedicated segmented item silhouette: " + item);
            int glowElements = 0;
            int shadedElements = 0;
            for (JsonElement value : elements) {
                JsonObject element = value.getAsJsonObject();
                JsonArray from = vector(element, "from");
                JsonArray to = vector(element, "to");
                for (int axis = 0; axis < 3; axis++) {
                    double start = from.get(axis).getAsDouble();
                    double end = to.get(axis).getAsDouble();
                    assertTrue(start >= -16 && end <= 32 && start < end,
                            "Minecraft rejects empty/out-of-range item elements: " + item);
                }
                if (element.has("rotation")) {
                    JsonObject rotation = element.getAsJsonObject("rotation");
                    vector(rotation, "origin");
                    assertTrue(Set.of("x", "y", "z").contains(rotation.get("axis").getAsString()));
                    assertTrue(ROTATIONS.contains(rotation.get("angle").getAsDouble()),
                            "Minecraft 1.20.1 only permits 0, 22.5 and 45 degree element rotations");
                }
                JsonObject faces = element.getAsJsonObject("faces");
                assertEquals(FACES, faces.keySet(), "Solid blade/ritual cage section has missing faces");
                for (JsonElement faceValue : faces.asMap().values()) {
                    JsonObject face = faceValue.getAsJsonObject();
                    assertEquals("#atlas", face.get("texture").getAsString());
                    JsonArray uv = face.getAsJsonArray("uv");
                    assertEquals(4, uv.size());
                    for (JsonElement coordinate : uv) assertTrue(coordinate.getAsDouble() >= 0 && coordinate.getAsDouble() <= 16);
                }
                assertFalse(element.has("light_emission"), "light_emission is unsupported by the targeted Forge parser");
                if (element.has("forge_data")) {
                    glowElements++;
                    JsonObject fullbright = element.getAsJsonObject("forge_data");
                    assertEquals(15, fullbright.get("block_light").getAsInt());
                    assertEquals(15, fullbright.get("sky_light").getAsInt());
                    assertFalse(fullbright.get("ambient_occlusion").getAsBoolean());
                    assertFalse(element.get("shade").getAsBoolean());
                } else shadedElements++;
            }
            assertTrue(glowElements >= 4, "Eyes/core/grooves must remain visible in darkness");
            assertTrue(shadedElements > glowElements, "Dark armor must retain normal lighting rather than glowing as a whole");
            JsonObject display = model.getAsJsonObject("display");
            for (String context : Set.of("gui", "ground", "fixed", "firstperson_righthand", "firstperson_lefthand",
                    "thirdperson_righthand", "thirdperson_lefthand")) {
                JsonObject transform = display.getAsJsonObject(context);
                assertNotNull(transform, "Missing hand/menu item transform: " + context);
                vector(transform, "rotation");
                vector(transform, "translation");
                for (JsonElement scale : vector(transform, "scale")) assertTrue(scale.getAsDouble() > 0 && scale.getAsDouble() <= 1);
            }
        }
    }

    @Test
    void itemAtlasesContainDetailedDarkMetalAndReadableOrangeEnergy() throws IOException {
        for (String item : Set.of("apex_beacon", "apex_fang")) {
            BufferedImage image;
            try (InputStream stream = resource(ASSETS + "textures/item/" + item + ".png")) {
                image = ImageIO.read(stream);
            }
            assertNotNull(image, "Missing decoded PNG atlas: " + item);
            assertEquals(128, image.getWidth());
            assertEquals(128, image.getHeight());
            int dark = 0;
            int orange = 0;
            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                assertEquals(255, pixel >>> 24, "Atlas tiles must not make solid blade faces transparent");
                int red = (pixel >>> 16) & 255;
                int green = (pixel >>> 8) & 255;
                int blue = pixel & 255;
                colors.add(pixel);
                if (red < 100 && green < 100 && blue < 110) dark++;
                if (red > 180 && green > 35 && green < 235 && blue < 130 && red > green) orange++;
            }
            assertTrue(colors.size() > 128, "Missing brushed material, engraving and edge detail");
            assertTrue(dark > image.getWidth() * image.getHeight() / 2, "Armor atlas should retain dark gunmetal contrast");
            assertTrue(orange > 512, "Dedicated blade/core atlas lacks its orange energy material");
        }
    }

    @Test
    void advancementsObserveSuccessfulSummoningKillsAndActualFangCrafting() throws IOException {
        JsonObject summoning = json(DATA + "advancements/the_predator_awakens.json");
        assertEquals("minecraft:impossible", summoning.getAsJsonObject("criteria").getAsJsonObject("summoned").get("trigger").getAsString(),
                "Grant from the completed ritual, rather than item acquisition or ritual start");
        JsonObject defeat = json(DATA + "advancements/apex_predator.json");
        JsonObject killed = defeat.getAsJsonObject("criteria").getAsJsonObject("apex_defeated");
        assertEquals("minecraft:player_killed_entity", killed.get("trigger").getAsString());
        assertEquals("apexpredator:apex", killed.getAsJsonObject("conditions").getAsJsonObject("entity").get("type").getAsString());
        JsonObject crafting = json(DATA + "advancements/become_the_apex.json");
        JsonObject crafted = crafting.getAsJsonObject("criteria").getAsJsonObject("fang_crafted");
        assertEquals("minecraft:recipe_crafted", crafted.get("trigger").getAsString(), "Inventory grants/loot must not count as Fang crafting");
        assertEquals("apexpredator:apex_fang", crafted.getAsJsonObject("conditions").get("recipe_id").getAsString());
        assertEquals("apexpredator:the_predator_awakens", defeat.get("parent").getAsString());
        assertEquals("apexpredator:apex_predator", crafting.get("parent").getAsString());
        for (String item : Set.of("apex_beacon", "apex_fang")) {
            JsonObject unlock = json(DATA + "advancements/recipes/" + item + ".json");
            assertEquals(Set.of("apexpredator:" + item), strings(unlock.getAsJsonObject("rewards").getAsJsonArray("recipes")));
            assertEquals(1, unlock.getAsJsonArray("requirements").size(), "Ingredient discovery and known recipe should be alternatives");
        }
    }

    @Test
    void progressionTranslationsCoverRealItemMessagesAndAdvancementDisplaysInBothLocales() throws IOException {
        JsonObject english = json(ASSETS + "lang/en_us.json");
        JsonObject japanese = json(ASSETS + "lang/ja_jp.json");
        assertEquals(english.keySet(), japanese.keySet(), "Locale fallback should not conceal untranslated ritual feedback");
        Set<String> keys = new HashSet<>(Set.of("item.apexpredator.apex_beacon", "item.apexpredator.apex_fang",
                "tooltip.apexpredator.apex_beacon", "tooltip.apexpredator.apex_beacon.ritual", "tooltip.apexpredator.apex_beacon.danger",
                "tooltip.apexpredator.apex_fang", "tooltip.apexpredator.apex_fang.dash", "tooltip.apexpredator.apex_fang.slash",
                "tooltip.apexpredator.apex_fang.cooldown", "message.apexpredator.fang.ground_required", "message.apexpredator.fang.cooldown"));
        for (String suffix : Set.of("started", "busy", "apex_nearby", "peaceful", "unsafe_ground", "space_blocked", "invalid_item", "canceled", "summoned"))
            keys.add("message.apexpredator.beacon." + suffix);
        for (String advancement : Set.of("the_predator_awakens", "apex_predator", "become_the_apex")) {
            JsonObject display = json(DATA + "advancements/" + advancement + ".json").getAsJsonObject("display");
            keys.add(display.getAsJsonObject("title").get("translate").getAsString());
            keys.add(display.getAsJsonObject("description").get("translate").getAsString());
        }
        for (String key : keys) {
            assertTrue(english.has(key) && japanese.has(key), "Missing progression translation " + key);
            assertFalse(english.get(key).getAsString().isBlank());
            assertFalse(japanese.get(key).getAsString().isBlank());
            assertFalse(english.get(key).getAsString().contains("%"), "These item messages are emitted without formatting parameters");
            assertFalse(japanese.get(key).getAsString().contains("%"));
        }
    }

    private static Map<String, Integer> ingredientCounts(JsonObject recipe) {
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals(3, recipe.getAsJsonArray("pattern").size());
        JsonObject key = recipe.getAsJsonObject("key");
        Map<String, Integer> counts = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (JsonElement row : recipe.getAsJsonArray("pattern")) {
            String pattern = row.getAsString();
            assertEquals(3, pattern.length());
            for (char token : pattern.toCharArray()) {
                if (token == ' ') continue;
                String symbol = Character.toString(token);
                assertTrue(key.has(symbol), "Recipe has an undefined ingredient symbol");
                used.add(symbol);
                counts.merge(key.getAsJsonObject(symbol).get("item").getAsString(), 1, Integer::sum);
            }
        }
        assertEquals(used, key.keySet(), "Recipe contains unused ingredient definitions");
        return counts;
    }

    private static void assertResult(JsonObject recipe, String item) {
        assertEquals(item, recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    private static Set<String> strings(JsonArray array) {
        Set<String> result = new HashSet<>();
        for (JsonElement value : array) result.add(value.getAsString());
        return result;
    }

    private static JsonArray vector(JsonObject object, String field) {
        JsonArray vector = object.getAsJsonArray(field);
        assertNotNull(vector, "Missing vector " + field);
        assertEquals(3, vector.size());
        for (JsonElement value : vector) assertTrue(Double.isFinite(value.getAsDouble()), "Non-finite model transform");
        return vector;
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = resource(path); InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = ProgressionAssetTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing packaged progression resource " + path);
        return stream;
    }
}
