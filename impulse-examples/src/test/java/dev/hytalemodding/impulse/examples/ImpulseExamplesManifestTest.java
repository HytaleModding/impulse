package dev.hytalemodding.impulse.examples;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImpulseExamplesManifestTest {

    @Test
    void generatedManifestDependsOnBundledControlSubPlugin() throws IOException {
        PluginManifest manifest = decodeGeneratedManifest();

        assertTrue(manifest.getDependencies()
            .containsKey(new PluginIdentifier("HytaleModding", "Impulse")));
        assertTrue(manifest.getDependencies()
            .containsKey(new PluginIdentifier("HytaleModding", "ImpulsePhysicsEntity")));
        assertTrue(manifest.getDependencies()
            .containsKey(new PluginIdentifier("HytaleModding", "ImpulsePhysicsChunk")));
        assertTrue(manifest.getDependencies()
            .containsKey(new PluginIdentifier("HytaleModding", "ImpulseControl")));
    }

    private static PluginManifest decodeGeneratedManifest() throws IOException {
        InputStream stream = ImpulseExamplesManifestTest.class
            .getClassLoader()
            .getResourceAsStream("manifest.json");
        assertNotNull(stream);
        try (InputStreamReader input = new InputStreamReader(stream, StandardCharsets.UTF_8);
             RawJsonReader reader = new RawJsonReader(input, RawJsonReader.READ_BUFFER.get())) {
            return PluginManifest.CODEC.decodeJson(reader, new ExtraInfo());
        }
    }
}
