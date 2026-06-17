package dev.hytalemodding.impulse.core.internal.systems.visual;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PhysicsGeneratedProxyCleanupSystemTest {

    @Test
    void cleanupDoesNotRemoveDurableGeneratedProxyAttachments() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/systems/visual/"
                + "PhysicsGeneratedProxyCleanupSystem.java"));

        assertTrue(source.contains("removeOrphanGeneratedVisualProxyMarkers"));
        assertFalse(source.contains("AttachmentLifecycle.GENERATED_PROXY"));
    }
}
