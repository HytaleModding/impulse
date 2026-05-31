package dev.hytalemodding.impulse.api.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    protected static final int CONCURRENT_SPACE_CREATIONS = 4;

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
    void createsDistinctLogicalSpacesConcurrently() throws Exception {
        PhysicsBackend backend = createBackend();
        backend.setDataDirectory(tempDir);
        backend.setInternalLoggingLevel(Level.OFF);
        backend.init();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_SPACE_CREATIONS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_SPACE_CREATIONS);
        CountDownLatch start = new CountDownLatch(1);
        List<SpaceId> requestedIds = new ArrayList<>();
        List<Future<PhysicsSpace>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_SPACE_CREATIONS; i++) {
                SpaceId spaceId = new SpaceId(10_000 + i);
                requestedIds.add(spaceId);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS), "Timed out waiting to start space creation");
                    return backend.createSpace(spaceId);
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Timed out waiting for space creation tasks");
            start.countDown();

            Set<SpaceId> returnedIds = new HashSet<>();
            for (Future<PhysicsSpace> future : futures) {
                PhysicsSpace space = future.get(10, TimeUnit.SECONDS);
                spaces.add(space);
                assertEquals(backend.getId(), space.backendId());
                assertTrue(returnedIds.add(space.id()), "Each created space should have a distinct logical id");
            }

            assertEquals(Set.copyOf(requestedIds), returnedIds);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void createsGeneratedSpacesConcurrently() throws Exception {
        PhysicsBackend backend = createBackend();
        backend.setDataDirectory(tempDir);
        backend.setInternalLoggingLevel(Level.OFF);
        backend.init();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_SPACE_CREATIONS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_SPACE_CREATIONS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PhysicsSpace>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_SPACE_CREATIONS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS), "Timed out waiting to start space creation");
                    return backend.createSpace();
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Timed out waiting for space creation tasks");
            start.countDown();

            Set<SpaceId> returnedIds = new HashSet<>();
            for (Future<PhysicsSpace> future : futures) {
                PhysicsSpace space = future.get(10, TimeUnit.SECONDS);
                spaces.add(space);
                assertEquals(backend.getId(), space.backendId());
                assertTrue(returnedIds.add(space.id()), "Each created space should have a distinct logical id");
            }

            assertEquals(CONCURRENT_SPACE_CREATIONS, returnedIds.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void newBodiesAreNotReportedAsSpaceBodiesUntilAdded() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);

        assertEquals(0, space.bodyCount());
        assertFalse(space.getBodies().contains(body));
        assertFalse(space.containsBody(body));

        space.addBody(body);

        assertEquals(1, space.bodyCount());
        assertTrue(space.getBodies().contains(body));
        assertTrue(space.containsBody(body));

        space.removeBody(body);

        assertEquals(0, space.bodyCount());
        assertFalse(space.getBodies().contains(body));
        assertFalse(space.containsBody(body));
    }

    @Test
    void shapeFactoriesExposeExpectedStoredMetadata() {
        PhysicsSpace space = createHeadlessSpace();

        PhysicsBody box = space.createBox(0.5f, 1.5f, 2.5f, 3.0f);
        PhysicsBody sphere = space.createSphere(1.25f, 2.0f);
        PhysicsBody capsule = space.createCapsule(0.75f, 1.5f, PhysicsAxis.Z, 4.0f);
        PhysicsBody cylinder = space.createCylinder(0.8f, 1.2f, PhysicsAxis.X, 5.0f);
        PhysicsBody cone = space.createCone(0.6f, 0.9f, PhysicsAxis.Y, 6.0f);
        PhysicsBody plane = space.createStaticPlane(12.0f);

        assertEquals(ShapeType.BOX, box.getShapeType());
        assertVectorNear(new Vector3f(0.5f, 1.5f, 2.5f),
            Objects.requireNonNull(box.getBoxHalfExtents()), 0.0001f);
        assertEquals(3.0f, box.getMass(), 0.0001f);
        assertEquals(PhysicsBodyType.DYNAMIC, box.getBodyType());

        assertEquals(ShapeType.SPHERE, sphere.getShapeType());
        assertEquals(1.25f, sphere.getSphereRadius(), 0.0001f);

        assertEquals(ShapeType.CAPSULE, capsule.getShapeType());
        assertEquals(0.75f, capsule.getSphereRadius(), 0.0001f);
        assertEquals(1.5f, capsule.getHalfHeight(), 0.0001f);
        assertEquals(PhysicsAxis.Z, capsule.getShapeAxis());

        assertEquals(ShapeType.CYLINDER, cylinder.getShapeType());
        assertEquals(0.8f, cylinder.getSphereRadius(), 0.0001f);
        assertEquals(1.2f, cylinder.getHalfHeight(), 0.0001f);
        assertEquals(PhysicsAxis.X, cylinder.getShapeAxis());

        assertEquals(ShapeType.CONE, cone.getShapeType());
        assertEquals(0.6f, cone.getSphereRadius(), 0.0001f);
        assertEquals(0.9f, cone.getHalfHeight(), 0.0001f);
        assertEquals(PhysicsAxis.Y, cone.getShapeAxis());

        assertEquals(ShapeType.PLANE, plane.getShapeType());
        assertEquals(PhysicsBodyType.STATIC, plane.getBodyType());
        assertEquals(12.0f, plane.getPosition().y, 0.0001f);
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
    void staticPlaneHeightIsStoredAsBodyPosition() {
        PhysicsSpace space = createHeadlessSpace();
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
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody plane = space.createStaticPlane(12.0f);
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
    void spaceSnapshotUsesPlaneBodyPositionForHeight() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody plane = space.createStaticPlane(12.0f);
        space.addBody(plane);

        List<PhysicsBodySnapshot> snapshots = new ArrayList<>();
        space.snapshotBodies(snapshots::add);

        assertEquals(1, snapshots.size());
        PhysicsBodySnapshot initial = snapshots.getFirst();
        assertEquals(ShapeType.PLANE, initial.shapeType());
        assertEquals(PhysicsBodyType.STATIC, initial.bodyType());
        assertVectorNear(new Vector3f(0.0f, 12.0f, 0.0f), initial.position(), 0.0001f);
        assertTrue(initial.isStatic());
        assertFalse(initial.isDynamic());

        plane.setPosition(0.0f, 5.0f, 0.0f);
        snapshots.clear();
        space.snapshotBodies(snapshots::add);

        assertEquals(1, snapshots.size());
        PhysicsBodySnapshot moved = snapshots.getFirst();
        assertEquals(ShapeType.PLANE, moved.shapeType());
        assertEquals(PhysicsBodyType.STATIC, moved.bodyType());
        assertVectorNear(new Vector3f(0.0f, 5.0f, 0.0f), moved.position(), 0.0001f);
    }

    @Test
    void splitLowTpsFramePreventsFastBodyFromTunnelingThroughPlane() {
        PhysicsSpace space = createHeadlessSpace();
        space.setGravity(0.0f, 0.0f, 0.0f);

        PhysicsBody plane = space.createStaticPlane(0.0f);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(0.0f, 6.0f, 0.0f);
        body.setLinearVelocity(0.0f, -25.0f, 0.0f);
        if (space.getCapability(PhysicsContinuousCollisionCapability.class).isPresent()) {
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

        boolean wentToSleep = stepUntil(space, MAX_SETTLE_STEPS, _ -> body.isSleeping());
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
