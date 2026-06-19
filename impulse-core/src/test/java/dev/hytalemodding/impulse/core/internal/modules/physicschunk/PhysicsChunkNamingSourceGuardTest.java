package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PhysicsChunkNamingSourceGuardTest {

    @Test
    void physicsChunkTerrainFacadeDoesNotDelegateThroughRemovedCompatibilityFacade()
        throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/plugin/modules/physicschunk/PhysicsChunkTerrain.java"));

        assertFalse(source.contains(removedPhysicsTerrainFacade() + "."),
            "PhysicsChunk terrain facade must own the implementation path");
    }

    @Test
    void physicsChunkCommandsUseTerrainNamedProfilingApi() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/modules/physicschunk/commands"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains(removedPhysicsTerrainFacade() + "Profiling"),
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
                assertFalse(source.contains("import dev.hytalemodding.impulse.core.plugin.settings.*;"),
                    file + " should not wildcard-import flat settings");
                assertRemovedApiAbsent(source,
                    file,
                    "dev.hytalemodding.impulse.core.plugin.modules.physicschunk.",
                    removedPhysicsTerrainFacade());
                assertRemovedApiAbsent(source,
                    file,
                    "dev.hytalemodding.impulse.core.plugin.modules.physicschunk.",
                    removedTerrainPrefix() + "Mode");
                assertRemovedApiAbsent(source,
                    file,
                    "dev.hytalemodding.impulse.core.plugin.settings.",
                    removedPhysicsTerrainFacade() + "Settings");
                assertRemovedApiAbsent(source,
                    file,
                    "dev.hytalemodding.impulse.core.plugin.settings.",
                    "PhysicsChunkTerrainSettings");
                assertRemovedApiAbsent(source,
                    file,
                    "dev.hytalemodding.impulse.core.plugin.settings.",
                    "PhysicsChunkCollisionSettings");
                assertRemovedApiAbsent(source,
                    file,
                    "dev.hytalemodding.impulse.core.plugin.settings.",
                    "PhysicsCollisionLodSettings");
                assertRemovedApiAbsent(source,
                    file,
                    "dev.hytalemodding.impulse.core.plugin.settings.",
                    "PhysicsVisualSyncSettings");
                assertRemovedApiAbsent(source,
                    file,
                    "dev.hytalemodding.impulse.core.plugin.settings.",
                    "PhysicsVisualMaterializationSettings");
            }
        }
    }

    private static void assertRemovedApiAbsent(String source,
        Path file,
        String packageName,
        String typeName) {
        assertFalse(source.contains("import " + packageName + typeName),
            file + " should use canonical module APIs");
        assertFalse(source.contains(packageName + typeName),
            file + " should use canonical module APIs");
    }

    private static String removedPhysicsTerrainFacade() {
        return "Physics" + removedTerrainPrefix();
    }

    private static String removedTerrainPrefix() {
        return "World" + "Collision";
    }
}
