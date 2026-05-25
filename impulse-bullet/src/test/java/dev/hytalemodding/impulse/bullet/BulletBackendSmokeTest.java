package dev.hytalemodding.impulse.bullet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final float STEP_DT = 1.0f / 60.0f;
    private static final float LOW_TPS_FRAME_DT = 0.5f;
    private static final int LOW_TPS_SUBSTEPS = 16;
    private static final int MAX_SETTLE_STEPS = 900;
    private static final float POSITION_EPSILON = 0.05f;

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

    @Test
    void newBodiesAreNotReportedAsSpaceBodiesUntilAdded() {
        space = createSpace();

        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);

        assertEquals(0, space.bodyCount());
        assertFalse(space.getBodies().contains(body));

        space.addBody(body);

        assertEquals(1, space.bodyCount());
        assertTrue(space.getBodies().contains(body));
    }

    @Test
    void staticPlaneHeightIsStoredAsBodyPosition() {
        space = createSpace();

        PhysicsBody plane = space.createStaticPlane(12.0f);
        PhysicsBody body = space.createSphere(0.5f, 1.0f);
        body.setPosition(0.0f, 16.0f, 0.0f);

        assertEquals(12.0f, plane.getPosition().y, 0.0001f);

        space.addBody(plane);
        space.addBody(body);
        stepSpace(space, MAX_SETTLE_STEPS);

        assertEquals(12.5f, body.getPosition().y, POSITION_EPSILON,
            "Dynamic sphere should settle on the plane surface derived from plane position");
    }

    @Test
    void setPositionMovesStaticPlaneSurface() {
        space = createSpace();

        PhysicsBody plane = space.createStaticPlane(0.0f);
        space.addBody(plane);

        plane.setPosition(0.0f, 5.0f, 0.0f);
        PhysicsBody body = space.createSphere(0.5f, 1.0f);
        body.setPosition(0.0f, 8.0f, 0.0f);
        space.addBody(body);
        stepSpace(space, MAX_SETTLE_STEPS);

        assertEquals(5.0f, plane.getPosition().y, 0.0001f);
        assertEquals(5.5f, body.getPosition().y, POSITION_EPSILON,
            "Moving the plane body should move the collision surface");
    }

    @Test
    void splitLowTpsFramePreventsFastBodyFromTunnelingThroughPlane() {
        space = createSpace();
        space.setGravity(0.0f, 0.0f, 0.0f);

        PhysicsBody plane = space.createStaticPlane(0.0f);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(0.0f, 6.0f, 0.0f);
        body.setLinearVelocity(0.0f, -25.0f, 0.0f);
        body.setContinuousCollisionEnabled(true);

        space.addBody(plane);
        space.addBody(body);

        stepFrameWithSubsteps(space, LOW_TPS_FRAME_DT, LOW_TPS_SUBSTEPS);

        assertTrue(body.getPosition().y > 0.2f,
            "Substepped low-TPS frame should keep the fast body above the plane");
    }

    private PhysicsSpace createSpace() {
        BulletBackend backend = new BulletBackend();
        backend.setDataDirectory(tempDir);
        backend.setInternalLoggingLevel(Level.OFF);
        Impulse.registerBackend(backend);
        return Impulse.createSpace(backend.getId());
    }

    private static void stepSpace(PhysicsSpace space, int steps) {
        for (int i = 0; i < steps; i++) {
            space.step(STEP_DT);
        }
    }

    private static void stepFrameWithSubsteps(PhysicsSpace space, float frameDt, int substeps) {
        float stepDt = frameDt / substeps;
        for (int i = 0; i < substeps; i++) {
            space.step(stepDt);
        }
    }
}
