package dev.hytalemodding.impulse.examples.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DropCommandTest {

    @Test
    void dropCommandOwnsBodyAndVisualAssembly() throws IOException {
        String source = Files.readString(Path.of("src/main/java/dev/hytalemodding/impulse/examples/commands/DropCommand.java"));

        assertTrue(source.contains("PhysicsBodyEntities.dynamicBody("));
        assertTrue(source.contains("PhysicsEntities.bodyHolder("));
        assertTrue(source.contains("BodyAttachmentComponent.impulseOwnedVisual("));
        assertFalse(source.contains("ExamplePhysicsUtils.bodyEntity("));
        assertFalse(source.contains("ExamplePhysicsUtils.toVector3f("));
        assertFalse(source.contains("ExamplePhysicsUtils.addPhysicsStoreBody("));
        assertFalse(source.contains("ExamplePhysicsUtils.attachedPhysicsStoreBlockEntityHolder("));
    }
}
