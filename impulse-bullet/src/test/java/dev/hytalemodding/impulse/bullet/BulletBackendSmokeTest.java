package dev.hytalemodding.impulse.bullet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import java.nio.file.Path;
import java.util.logging.Level;
import org.joml.Vector3f;
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
    void createsSpacesAndTracksBodyCountsThroughTheImpulseRegistry() {
        space = createSpace();

        assertEquals(0, space.bodyCount());
        assertEquals(0, space.jointCount());

        PhysicsBody plane = space.createStaticPlane(0.0f);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(0.0f, 2.0f, 0.0f);

        space.addBody(plane);
        space.addBody(body);
        assertEquals(2, space.bodyCount());

        space.removeBody(body);
        assertEquals(1, space.bodyCount());
    }

    @Test
    void createsAndRemovesPointJointsForManagedBodies() {
        space = createSpace();

        PhysicsBody bodyA = space.createSphere(0.5f, 1.0f);
        PhysicsBody bodyB = space.createSphere(0.5f, 1.0f);
        bodyA.setPosition(-1.0f, 1.0f, 0.0f);
        bodyB.setPosition(1.0f, 1.0f, 0.0f);

        space.addBody(bodyA);
        space.addBody(bodyB);

        PhysicsJoint joint = space.createPointJoint(bodyA,
            bodyB,
            new Vector3f(0.5f, 0.0f, 0.0f),
            new Vector3f(-0.5f, 0.0f, 0.0f));

        assertNotNull(joint);
        assertEquals(1, space.jointCount());

        space.removeJoint(joint);
        assertEquals(0, space.jointCount());
    }

    @Test
    void exposesExpectedBackendCapabilities() {
        space = createSpace();

        assertTrue(space.supportsContinuousCollision());
        assertEquals(BulletBackend.ID, space.getBackendId());
    }

    private PhysicsSpace createSpace() {
        BulletBackend backend = new BulletBackend();
        backend.setDataDirectory(tempDir);
        backend.setInternalLoggingLevel(Level.OFF);
        Impulse.registerBackend(backend);
        return Impulse.createSpace(backend.getId());
    }
}
