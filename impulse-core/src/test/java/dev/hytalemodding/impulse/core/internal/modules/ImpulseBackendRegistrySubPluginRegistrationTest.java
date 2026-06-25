package dev.hytalemodding.impulse.core.internal.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.semver.SemverRange;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class ImpulseBackendRegistrySubPluginRegistrationTest {

    @Test
    void generatedManifestSubPluginsSupportHytalePendingLoadInheritance() throws IOException {
        PluginManifest parent = decodeGeneratedManifest();
        PluginIdentifier parentId = new PluginIdentifier(parent);

        List<PluginManifest> prepared = ImpulseSubPluginRegistration.prepareSubPluginManifests(parent);
        for (PluginManifest subPlugin : prepared) {
            assertTrue(subPlugin.getDependencies().containsKey(parentId));
        }
        assertSubPluginLoadsBefore(parent, "ImpulsePhysicsEntity", "ImpulseControl");
        assertSubPluginLoadsBefore(parent, "ImpulsePhysicsEntity", "ImpulsePhysicsChunk");
        assertSubPluginMain(parent,
            "ImpulseControl",
            "dev.hytalemodding.impulse.builtin.control.ImpulseControlPlugin");
        assertSubPluginMain(parent,
            "ImpulsePhysicsEntity",
            "dev.hytalemodding.impulse.core.internal.modules.physicsentity.PhysicsEntityModule");
        assertSubPluginMain(parent,
            "ImpulsePhysicsChunk",
            "dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkSubPlugin");
    }

    @Test
    void preparesEverySubPluginManifestForDynamicLoad() {
        PluginManifest parent = manifest("HytaleModding",
            "Impulse",
            "dev.hytalemodding.impulse.core.ImpulsePlugin",
            List.of(
                manifest(null,
                    "FixtureSubPlugin",
                    "example.FixtureSubPlugin",
                    List.of(),
                    false)),
            false);

        List<PluginManifest> prepared =
            ImpulseSubPluginRegistration.prepareSubPluginManifests(parent);

        assertEquals(1, prepared.size());
        assertPreparedSubPlugin(prepared.getFirst(), "FixtureSubPlugin", false);
    }

    private static void assertPreparedSubPlugin(PluginManifest manifest,
        String expectedName,
        boolean expectedDisabledByDefault) {
        PluginIdentifier parentId = new PluginIdentifier("HytaleModding", "Impulse");

        assertEquals("HytaleModding", manifest.getGroup());
        assertEquals(expectedName, manifest.getName());
        assertEquals(expectedDisabledByDefault, manifest.isDisabledByDefault());
        assertTrue(manifest.getDependencies().containsKey(parentId));
    }

    private static void assertSubPluginLoadsBefore(@Nonnull PluginManifest parent,
        @Nonnull String subPluginName,
        @Nonnull String dependencyName) {
        PluginIdentifier dependencyId = new PluginIdentifier("HytaleModding", dependencyName);
        for (PluginManifest subPlugin : parent.getSubPlugins()) {
            if (subPluginName.equals(subPlugin.getName())) {
                assertTrue(subPlugin.getLoadBefore().containsKey(dependencyId),
                    subPluginName + " should load before " + dependencyName);
                return;
            }
        }
        throw new AssertionError("Missing subplugin " + subPluginName);
    }

    private static void assertSubPluginDoesNotLoadBefore(@Nonnull PluginManifest parent,
        @Nonnull String subPluginName,
        @Nonnull String dependencyName) {
        PluginIdentifier dependencyId = new PluginIdentifier("HytaleModding", dependencyName);
        for (PluginManifest subPlugin : parent.getSubPlugins()) {
            if (subPluginName.equals(subPlugin.getName())) {
                assertFalse(subPlugin.getLoadBefore().containsKey(dependencyId),
                    subPluginName + " should not order against " + dependencyName);
                return;
            }
        }
        throw new AssertionError("Missing subplugin " + subPluginName);
    }

    private static void assertMissingSubPlugin(@Nonnull PluginManifest parent,
        @Nonnull String subPluginName) {
        for (PluginManifest subPlugin : parent.getSubPlugins()) {
            assertFalse(subPluginName.equals(subPlugin.getName()),
                "Unexpected bundled subplugin " + subPluginName);
        }
    }

    private static void assertSubPluginMain(@Nonnull PluginManifest parent,
        @Nonnull String subPluginName,
        @Nonnull String expectedMain) {
        for (PluginManifest subPlugin : parent.getSubPlugins()) {
            if (subPluginName.equals(subPlugin.getName())) {
                assertEquals(expectedMain, subPlugin.getMain());
                return;
            }
        }
        throw new AssertionError("Missing subplugin " + subPluginName);
    }

    private static PluginManifest manifest(String group,
        String name,
        String main,
        List<PluginManifest> subPlugins,
        boolean disabledByDefault) {
        return new PluginManifest(group,
            name,
            Semver.fromString("1.2.0"),
            "test manifest",
            new ArrayList<>(),
            "https://example.invalid",
            main,
            SemverRange.fromString("0.6.0-pre.1"),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new ArrayList<>(subPlugins),
            disabledByDefault);
    }

    private static PluginManifest decodeGeneratedManifest() throws IOException {
        InputStream stream = ImpulseBackendRegistrySubPluginRegistrationTest.class
            .getClassLoader()
            .getResourceAsStream("manifest.json");
        assertNotNull(stream);
        try (InputStreamReader input = new InputStreamReader(stream, StandardCharsets.UTF_8);
             RawJsonReader reader = new RawJsonReader(input, RawJsonReader.READ_BUFFER.get())) {
            return PluginManifest.CODEC.decodeJson(reader, new ExtraInfo());
        }
    }

}
