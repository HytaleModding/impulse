package dev.hytalemodding.impulse.bullet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BulletBackendSmokeTest {

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

        assertTrue(space.getCapability(PhysicsContinuousCollisionCapability.class).isPresent());
        assertEquals(BulletBackend.ID, space.backendId());
    }

    private PhysicsSpace createSpace() {
        BulletBackend backend = new BulletBackend();
        backend.setInternalLoggingLevel(Level.OFF);
        Impulse.registerBackend(backend);
        return Impulse.createSpace(backend.getId());
    }
}
