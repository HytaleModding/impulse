package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PhysicsChunkNamingSourceGuardTest {

    @Test
    void physicsChunkTerrainFacadeDoesNotDelegateThroughDeprecatedWorldCollision()
        throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/plugin/modules/physicschunk/PhysicsChunkTerrain.java"));

        assertFalse(source.contains("PhysicsWorldCollision."),
            "new PhysicsChunk terrain facade must own the implementation path");
    }

    @Test
    void physicsChunkCommandsUseTerrainNamedProfilingApi() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/modules/physicschunk/commands"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("PhysicsWorldCollisionProfiling"),
                    file + " should use PhysicsChunkTerrainProfiling");
            }
        }
    }

    @Test
    void examplesDoNotUseInternalOrDeprecatedTerrainImports() throws IOException {
        Path examples = Path.of("../impulse-examples/src/main/java");
        if (!Files.exists(examples)) {
            return;
        }
        try (Stream<Path> files = Files.walk(examples)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("import dev.hytalemodding.impulse.core.internal."),
                    file + " should use exported plugin APIs");
                assertFalse(source.contains("import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsWorldCollision"),
                    file + " should use PhysicsChunkTerrain");
                assertFalse(source.contains("import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollision"),
                    file + " should use PhysicsChunkTerrain names");
                assertFalse(source.contains("import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings"),
                    file + " should use PhysicsChunkTerrainSettings");
                assertFalse(source.contains("import dev.hytalemodding.impulse.core.plugin.settings.PhysicsChunkTerrainSettings"),
                    file + " should use PhysicsChunk settings");
                assertFalse(source.contains("import dev.hytalemodding.impulse.core.plugin.settings.PhysicsCollisionLodSettings"),
                    file + " should use PhysicsChunk settings");
                assertFalse(source.contains("import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualSyncSettings"),
                    file + " should use PhysicsEntity settings");
                assertFalse(source.contains("import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings"),
                    file + " should use PhysicsEntity settings");
            }
        }
    }
}
