package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PhysicsChunkRegistrationOwnershipTest {

    @Test
    void physicsChunkSubPluginOwnsPhysicsStoreRegistrations() throws IOException {
        String coreRegistration = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/registration/PhysicsStoreRegistration.java"));
        String chunkSubPlugin = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/modules/physicschunk/PhysicsChunkSubPlugin.java"));

        assertFalse(coreRegistration.contains("PhysicsChunkStoreTypes.register"));
        assertTrue(chunkSubPlugin.contains("PhysicsStoreRegistration.physicsStoreRegistry(this)"));
        assertTrue(chunkSubPlugin.contains("PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes("));
        assertTrue(chunkSubPlugin.contains("PhysicsChunkStoreTypes.registerSpaceBindingSystems("));
        assertTrue(chunkSubPlugin.contains("PhysicsChunkStoreTypes.registerPreBodyBindingSystems("));
        assertTrue(chunkSubPlugin.contains("PhysicsChunkStoreTypes.registerPostBodyBindingSystems("));
    }
}
