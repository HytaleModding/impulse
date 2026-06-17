package dev.hytalemodding.impulse.core.internal.physicsstore;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PhysicsStoreOwnerLaneSnapshotBoundaryTest {

    @Test
    void completedStepPublicationDoesNotReadBackendBodiesOnWorldThread() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/systems/CompletedStepPublicationSystem.java"));

        assertFalse(source.contains("backendRuntime.snapshotBodies"));
    }
}
