package dev.hytalemodding.impulse.builtin.control;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.common.plugin.PluginManifest;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImpulseControlManifestTest {

    @Test
    void controlBuiltinDoesNotDeclareStandalonePluginManifest() throws IOException {
        InputStream stream = ImpulseControlManifestTest.class
            .getClassLoader()
            .getResourceAsStream("manifest.json");
        if (stream == null) {
            return;
        }

        try (InputStreamReader input = new InputStreamReader(stream, StandardCharsets.UTF_8);
             RawJsonReader reader = new RawJsonReader(input, RawJsonReader.READ_BUFFER.get())) {
            PluginManifest manifest = PluginManifest.CODEC.decodeJson(reader, new ExtraInfo());
            assertNotEquals("ImpulseControl", manifest.getName());
        }
    }
}
