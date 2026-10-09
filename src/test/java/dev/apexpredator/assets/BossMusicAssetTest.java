package dev.apexpredator.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Verify the resource paths that Minecraft's streamed audio loader will actually open. */
class BossMusicAssetTest {
    @Test
    void dedicatedMusicEventUsesOneStreamedTrackWithConstantGainAndPitch() throws IOException {
        JsonObject event = musicEvent();
        assertTrue(event.has("sounds"), "BGM event must contain its converted audio resource");
        assertEquals(1, event.getAsJsonArray("sounds").size(), "Random alternative songs would break stable replay");
        JsonObject sound = event.getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertTrue(sound.get("stream").getAsBoolean(), "A full song must use Minecraft streaming instead of a resident effect buffer");
        assertEquals("apexpredator:boss_music", sound.get("name").getAsString(), "The dedicated event must resolve the packaged song");
        if (sound.has("volume")) assertEquals(1, sound.get("volume").getAsDouble(), "Apply encounter gain only once in the sound instance");
        if (sound.has("pitch")) assertEquals(1, sound.get("pitch").getAsDouble(), "Do not retune the uploaded composition");
    }

    @Test
    void referencedSongIsPackagedOggVorbisRatherThanAnMp3WithAnOggExtension() throws IOException {
        String name = musicEvent().getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString();
        String[] identifier = name.split(":", 2);
        assertEquals(2, identifier.length, "Sound resource must name its mod namespace");
        try (InputStream stream = resource("/assets/" + identifier[0] + "/sounds/" + identifier[1] + ".ogg")) {
            byte[] page = stream.readNBytes(27);
            assertEquals(27, page.length, "Ogg container header is truncated");
            assertEquals("OggS", new String(page, 0, 4, StandardCharsets.US_ASCII), "Not an Ogg container");
            assertEquals(0, page[4], "Unsupported Ogg container version");
            assertTrue((page[5] & 2) != 0, "First Ogg page must identify the beginning of a logical stream");
            int segments = page[26] & 255;
            assertTrue(segments > 0, "First Ogg page has no codec packet");
            assertEquals(segments, stream.readNBytes(segments).length, "Ogg lacing table is truncated");
            byte[] codec = stream.readNBytes(7);
            assertEquals(7, codec.length, "Missing Vorbis identification packet");
            assertEquals(1, codec[0], "Expected Vorbis identification packet");
            assertEquals("vorbis", new String(codec, 1, 6, StandardCharsets.US_ASCII),
                    "Minecraft expects Vorbis; renamed MP3/Opus must not pass packaging validation");
        }
    }

    private static JsonObject musicEvent() throws IOException {
        try (InputStream stream = resource("/assets/apexpredator/sounds.json");
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject event = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("boss_music");
            assertNotNull(event, "Missing apexpredator:boss_music registration");
            return event;
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = BossMusicAssetTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing packaged BGM resource: " + path);
        return stream;
    }
}
