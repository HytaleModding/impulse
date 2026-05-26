package dev.hytalemodding.impulse.bullet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import java.nio.file.Path;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BulletBackendSmokeTest {

    @TempDir
    Path tempDir;

    private PhysicsSpace space;

    @AfterEach
    void closeSpace() {
        if (space != null) {
            space.close();
            space = null;
        }
    }

    @Test
    void exposesExpectedBackendCapabilities() {
        space = createSpace();

        assertTrue(space.supportsContinuousCollision());
        assertEquals(BulletBackend.ID, space.getBackendId());
    }

    @Test
    void setDataDirectoryDoesNotCreateNativeDirectoryInProvidedDataDirectory() {
        BulletBackend backend = new BulletBackend();

        backend.setDataDirectory(tempDir);

        assertFalse(tempDir.resolve("native").toFile().exists());
    }

    private PhysicsSpace createSpace() {
        BulletBackend backend = new BulletBackend();
        backend.setDataDirectory(tempDir);
        backend.setInternalLoggingLevel(Level.OFF);
        Impulse.registerBackend(backend);
        return Impulse.createSpace(backend.getId());
    }
}
