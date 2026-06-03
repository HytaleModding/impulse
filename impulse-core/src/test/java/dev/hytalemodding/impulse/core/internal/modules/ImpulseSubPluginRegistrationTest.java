package dev.hytalemodding.impulse.core.internal.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.semver.SemverRange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImpulseSubPluginRegistrationTest {

    @Test
    void preparesEverySubPluginManifestForDynamicLoad() {
        PluginManifest parent = manifest("HytaleModding",
            "Impulse",
            "dev.hytalemodding.impulse.core.ImpulsePlugin",
            List.of(
                manifest(null,
                    "ImpulseWorldCollision",
                    "dev.hytalemodding.impulse.core.plugin.modules.worldcollision.ImpulseWorldCollisionPlugin",
                    List.of(),
                    false),
                manifest(null,
                    "ImpulseControl",
                    "dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControlPlugin",
                    List.of(),
                    false)),
            false);

        List<PluginManifest> prepared =
            ImpulseSubPluginRegistration.prepareSubPluginManifests(parent);

        assertEquals(3, prepared.size());
        assertPreparedSubPlugin(prepared.get(0), "ImpulseWorldCollision", false);
        assertPreparedSubPlugin(prepared.get(1), "ImpulseControl", false);
        assertPreparedSubPlugin(prepared.get(2), "ImpulsePersistence", true);
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
}
