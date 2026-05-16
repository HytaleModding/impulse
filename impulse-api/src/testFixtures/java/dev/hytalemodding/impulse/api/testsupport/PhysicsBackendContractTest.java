package dev.hytalemodding.impulse.api.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Shared backend-level headless contract tests for Bullet and Rapier.
 *
 * <p>These tests exercise the public physics API directly without booting a
 * Hytale server/runtime.</p>
 */
public abstract class PhysicsBackendContractTest {

    protected static final float STEP_DT = 1.0f / 60.0f;
    protected static final float LOW_TPS_FRAME_DT = 0.5f;
    protected static final int LOW_TPS_SUBSTEPS = 16;
    protected static final int MAX_SETTLE_STEPS = 900;
    protected static final float POSITION_EPSILON = 0.05f;

    @TempDir
    Path tempDir;

    private final List<PhysicsSpace> spaces = new ArrayList<>();

    @Nonnull
    protected abstract PhysicsBackend createBackend();

    @AfterEach
    void tearDownSpaces() {
        for (PhysicsSpace space : spaces) {
            space.close();
        }
        spaces.clear();
    }

    @Test
    void createsSpaceAndConfiguresGravity() {
        PhysicsSpace space = createHeadlessSpace();

        assertEquals(0, space.bodyCount());
        assertEquals(0, space.jointCount());

        verifyGravityConfiguration(space);
    }

    @Test
    void dynamicBodyFallsOntoPlaneWithoutImmediatelyTunnelingThrough() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody plane = space.createStaticPlane(0.0f);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(0.0f, 4.0f, 0.0f);

        space.addBody(plane);
        space.addBody(body);

        assertDynamicBodySettlesWithoutImmediateTunneling(space, body);
    }

    @Test
    void raycastClosestHitsDynamicBodyBeforePlane() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody plane = space.createStaticPlane(0.0f);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(0.0f, 2.0f, 0.0f);

        space.addBody(plane);
        space.addBody(body);
        space.step(STEP_DT);

        Optional<PhysicsRayHit> closest = space.raycastClosest(new Vector3f(0.0f, 4.0f, 0.0f),
            new Vector3f(0.0f, -2.0f, 0.0f));
        assertTrue(closest.isPresent(), "Expected a closest raycast hit");
        assertSame(body, closest.orElseThrow().body(),
            "Closest raycast hit should be the dynamic body before the plane");

        boolean foundDynamicHit = space.raycastAll(new Vector3f(0.0f, 4.0f, 0.0f),
            new Vector3f(0.0f, -2.0f, 0.0f)).stream()
            .anyMatch(hit -> hit.body() == body);
        assertTrue(foundDynamicHit, "Expected the dynamic body to appear in raycastAll hits");
    }

    @Test
    void reportsContactsAfterImpact() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody plane = space.createStaticPlane(0.0f);
        PhysicsBody body = space.createSphere(0.5f, 1.0f);
        body.setPosition(0.0f, 3.0f, 0.0f);

        space.addBody(plane);
        space.addBody(body);

        boolean sawContact = stepUntil(space, MAX_SETTLE_STEPS, current -> !current.getContacts().isEmpty());
        assertTrue(sawContact, "Expected at least one contact after the body impacts the plane");
    }

    @Test
    void splitLowTpsFramePreventsFastBodyFromTunnelingThroughPlane() {
        PhysicsSpace space = createHeadlessSpace();
        space.setGravity(0.0f, 0.0f, 0.0f);

        PhysicsBody plane = space.createStaticPlane(0.0f);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(0.0f, 6.0f, 0.0f);
        body.setLinearVelocity(0.0f, -25.0f, 0.0f);
        if (space.supportsContinuousCollision()) {
            body.setContinuousCollisionEnabled(true);
        }

        space.addBody(plane);
        space.addBody(body);

        stepFrameWithSubsteps(space, LOW_TPS_FRAME_DT, LOW_TPS_SUBSTEPS);

        assertTrue(body.getPosition().y > 0.2f,
            "Substepped low-TPS frame should keep the fast body above the plane");
    }

    @Test
    void createsAndRemovesPointJoints() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody bodyA = space.createSphere(0.5f, 1.0f);
        PhysicsBody bodyB = space.createSphere(0.5f, 1.0f);
        bodyA.setPosition(-1.0f, 2.0f, 0.0f);
        bodyB.setPosition(1.0f, 2.0f, 0.0f);

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
    void settledBodyEventuallySleeps() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody plane = space.createStaticPlane(0.0f);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(0.0f, 3.0f, 0.0f);

        space.addBody(plane);
        space.addBody(body);

        boolean wentToSleep = stepUntil(space, MAX_SETTLE_STEPS, current -> body.isSleeping());
        assertTrue(wentToSleep, "Expected a simple resting body to eventually sleep");
        assertFalse(body.isActive(), "Sleeping body should not remain active");
    }

    @Nonnull
    protected PhysicsSpace createHeadlessSpace() {
        PhysicsBackend backend = createBackend();
        backend.setDataDirectory(tempDir);
        backend.setInternalLoggingLevel(Level.OFF);
        Impulse.registerBackend(backend);

        PhysicsSpace space = Impulse.createSpace(backend.getId());
        space.setGravity(0.0f, -9.81f, 0.0f);
        spaces.add(space);
        return space;
    }

    protected void stepSpace(@Nonnull PhysicsSpace space, int steps) {
        for (int i = 0; i < steps; i++) {
            space.step(STEP_DT);
        }
    }

    protected void stepFrameWithSubsteps(@Nonnull PhysicsSpace space, float frameDt, int substeps) {
        float stepDt = frameDt / substeps;
        for (int i = 0; i < substeps; i++) {
            space.step(stepDt);
        }
    }

    protected boolean stepUntil(@Nonnull PhysicsSpace space,
        int maxSteps,
        @Nonnull SpaceCondition condition) {
        for (int i = 0; i < maxSteps; i++) {
            space.step(STEP_DT);
            if (condition.test(space)) {
                return true;
            }
        }
        return false;
    }

    protected void assertVectorNear(@Nonnull Vector3f expected,
        @Nonnull Vector3f actual,
        float epsilon) {
        assertEquals(expected.x, actual.x, epsilon, "Unexpected x component");
        assertEquals(expected.y, actual.y, epsilon, "Unexpected y component");
        assertEquals(expected.z, actual.z, epsilon, "Unexpected z component");
    }

    protected void assertDynamicBodySettlesWithoutImmediateTunneling(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body) {
        float startY = body.getPosition().y;
        stepSpace(space, 240);

        float endY = body.getPosition().y;
        assertTrue(endY < startY - 1.0f,
            "Dynamic body should fall noticeably under gravity");
        assertTrue(endY > 0.2f,
            "Dynamic body should remain above the plane instead of falling straight through");
    }

    protected void verifyGravityConfiguration(@Nonnull PhysicsSpace space) {
        space.setGravity(0.25f, -13.5f, 1.75f);
        assertVectorNear(new Vector3f(0.25f, -13.5f, 1.75f), space.getGravity(), 0.0001f);
    }

    @FunctionalInterface
    protected interface SpaceCondition {

        boolean test(@Nonnull PhysicsSpace space);
    }
}
