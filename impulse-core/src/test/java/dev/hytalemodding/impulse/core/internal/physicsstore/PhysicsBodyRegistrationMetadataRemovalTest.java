package dev.hytalemodding.impulse.core.internal.physicsstore;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class PhysicsBodyRegistrationMetadataRemovalTest {

    @Test
    void legacyBodyRegistrationMetadataTypesAreRemoved() throws IOException {
        Path sourceRoot = Path.of("src/main/java/dev/hytalemodding/impulse/core");
        assertFalse(Files.exists(sourceRoot.resolve("plugin/body/PhysicsBodyRegistrationView.java")));
        assertFalse(Files.exists(sourceRoot.resolve("plugin/body/PhysicsBodyKind.java")));
        assertFalse(Files.exists(sourceRoot.resolve("plugin/body/PhysicsBodyPersistenceMode.java")));

        assertNoProductionSourceContains(sourceRoot,
            "PhysicsBodyRegistrationView",
            "PhysicsBodyKind",
            "PhysicsBodyPersistenceMode",
            "registrationView",
            "registrationViews");
    }

    private static void assertNoProductionSourceContains(@Nonnull Path sourceRoot,
        @Nonnull String... forbiddenValues) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .toList()) {
                String contents = Files.readString(source);
                for (String forbidden : forbiddenValues) {
                    assertFalse(contents.contains(forbidden),
                        () -> source + " still contains " + forbidden);
                }
            }
        }
    }
}
