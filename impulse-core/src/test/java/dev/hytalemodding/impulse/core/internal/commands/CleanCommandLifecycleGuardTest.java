package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CleanCommandLifecycleGuardTest {

    @Test
    void cleanCommandDoesNotRemoveEveryBodyAttachmentEntity() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/commands/CleanCommand.java"));

        assertTrue(source.contains("cleanAttachedEntity("));
        assertTrue(source.contains("shouldRemoveEntityWhenBodyMissing()"));
        assertFalse(source.contains("removedEntities.incrementAndGet(REMOVED_BODY_ENTITIES);\n"
            + "                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), "
            + "RemoveReason.REMOVE);"));
    }
}
