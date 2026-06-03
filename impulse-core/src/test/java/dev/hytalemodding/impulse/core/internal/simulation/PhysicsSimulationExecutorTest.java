package dev.hytalemodding.impulse.core.internal.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend.InMemoryPhysicsSpace;
import dev.hytalemodding.impulse.core.internal.simulation.query.BenchmarkSpaceStatsQuery;
import dev.hytalemodding.impulse.core.internal.simulation.query.PhysicsDebugContactsQuery;
import dev.hytalemodding.impulse.core.internal.simulation.query.PhysicsDebugJointsQuery;
import dev.hytalemodding.impulse.core.internal.simulation.view.BenchmarkSpaceStatsView;
import dev.hytalemodding.impulse.core.internal.simulation.view.PhysicsDebugContactView;
import dev.hytalemodding.impulse.core.internal.simulation.view.PhysicsDebugJointView;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastClosestBatchResult;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RaycastClosestBatchQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastSegment;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsSimulationExecutorTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void templatedBulkSpawnExecutesAsSingleCommandResult() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:bulk-spawn-executor-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        SpaceId spaceId = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults())
            .id();
        RigidBodyKey first = RigidBodyKey.random();
        RigidBodyKey second = RigidBodyKey.random();

        List<PhysicsCommandResult> results = resource.submitCommands(1L, 1, commands -> commands.spawnBodies(2,
                spaceId,
                PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
                1.0f,
                PhysicsBodyType.DYNAMIC,
                RigidBodySpawnSettings.defaults(),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                spawns -> spawns
                    .body(first, 1.0f, 2.0f, 3.0f)
                    .body(second, 4.0f, 5.0f, 6.0f)))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(1, results.size());
        assertEquals(PhysicsCommandResult.Status.APPLIED, results.getFirst().status());
        assertEquals(2, resource.getBodyRegistrationCount());
    }

    @Test
    void forceAndTorqueCommandsUseScalarBodyApi() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:scalar-body-force-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        SpaceId spaceId = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults())
            .id();
        RigidBodyKey bodyKey = RigidBodyKey.random();
        ScalarRecordingBody body = new ScalarRecordingBody();
        resource.addBodyOnOwner(bodyKey,
            spaceId,
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        resource.submitCommands(1L, 4, commands -> commands
                .applyBodyImpulse(bodyKey, 1.0f, 2.0f, 3.0f, 0.1f, 0.2f, 0.3f)
                .applyBodyTorqueImpulse(bodyKey, 4.0f, 5.0f, 6.0f)
                .applyBodyForce(bodyKey, 7.0f, 8.0f, 9.0f, 0.4f, 0.5f, 0.6f)
                .applyBodyTorque(bodyKey, 10.0f, 11.0f, 12.0f))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(1, body.scalarImpulseCalls);
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), body.lastImpulse);
        assertEquals(new Vector3f(0.1f, 0.2f, 0.3f), body.lastImpulseOffset);
        assertEquals(1, body.scalarTorqueImpulseCalls);
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), body.lastTorqueImpulse);
        assertEquals(1, body.scalarForceCalls);
        assertEquals(new Vector3f(7.0f, 8.0f, 9.0f), body.lastForce);
        assertEquals(new Vector3f(0.4f, 0.5f, 0.6f), body.lastForceOffset);
        assertEquals(1, body.scalarTorqueCalls);
        assertEquals(new Vector3f(10.0f, 11.0f, 12.0f), body.lastTorque);
        assertEquals(4, body.activateCalls);
    }

    @Test
    void transformCommandsUseScalarRotationApi() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:scalar-transform-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        SpaceId spaceId = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults())
            .id();
        RigidBodyKey bodyKey = RigidBodyKey.random();
        ScalarRecordingBody body = new ScalarRecordingBody();
        resource.addBodyOnOwner(bodyKey,
            spaceId,
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        Quaternionf rotation = new Quaternionf().rotateXYZ(0.1f, 0.2f, 0.3f);
        resource.submitCommands(1L, 1, commands -> commands.setBodyTransform(bodyKey,
            1.0f,
            2.0f,
            3.0f,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w,
            true)).completion().toCompletableFuture().join();

        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), body.position);
        assertEquals(1, body.scalarRotationCalls);
        assertEquals(0, body.objectRotationCalls);
        assertEquals(1, body.activateCalls);
    }

    @Test
    void positionCommandsUseScalarBodyApiWithoutTouchingRotation() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:scalar-position-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        SpaceId spaceId = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults())
            .id();
        RigidBodyKey bodyKey = RigidBodyKey.random();
        ScalarRecordingBody body = new ScalarRecordingBody();
        resource.addBodyOnOwner(bodyKey,
            spaceId,
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        resource.submitCommands(1L, 1, commands -> commands.setBodyPosition(bodyKey,
                5.0f,
                6.0f,
                7.0f,
                true))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(new Vector3f(5.0f, 6.0f, 7.0f), body.position);
        assertEquals(1, body.scalarPositionCalls);
        assertEquals(0, body.objectPositionCalls);
        assertEquals(0, body.scalarRotationCalls);
        assertEquals(0, body.objectRotationCalls);
        assertEquals(1, body.activateCalls);
    }

    @Test
    void debugContactQueryReturnsPrimitiveCopiedViewsWithinRadius() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:debug-contact-query-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        InMemoryPhysicsSpace space = (InMemoryPhysicsSpace) resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        PhysicsBody bodyA = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody bodyB = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        resource.addBody(space.id(),
            bodyA,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        resource.addBody(space.id(),
            bodyB,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        space.addContact(new PhysicsContact(bodyA,
            bodyB,
            new Vector3f(0.0f, 0.0f, 0.0f),
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Vector3f(0.0f, 2.0f, 0.0f),
            -0.1f,
            20.0f));

        List<PhysicsDebugContactView> visible = resource.queryInternal(new PhysicsDebugContactsQuery(space.id(),
                1.0f,
                2.0f,
                3.0f,
                0.5f,
                4))
            .toCompletableFuture()
            .join();

        assertEquals(1, visible.size());
        PhysicsDebugContactView contact = visible.getFirst();
        assertEquals(1.0f, contact.pointX(), 0.00001f);
        assertEquals(2.0f, contact.pointY(), 0.00001f);
        assertEquals(3.0f, contact.pointZ(), 0.00001f);
        assertTrue(contact.hasNormal());
        assertEquals(0.0f, contact.normalX(), 0.00001f);
        assertEquals(1.0f, contact.normalY(), 0.00001f);
        assertEquals(0.0f, contact.normalZ(), 0.00001f);

        assertTrue(resource.queryInternal(new PhysicsDebugContactsQuery(space.id(),
                3.0f,
                2.0f,
                3.0f,
                0.5f,
                4))
            .toCompletableFuture()
            .join()
            .isEmpty());
        assertTrue(resource.queryInternal(new PhysicsDebugContactsQuery(space.id(),
                1.0f,
                2.0f,
                3.0f,
                0.5f,
                0))
            .toCompletableFuture()
            .join()
            .isEmpty());
    }

    @Test
    void debugJointQueryReturnsPrimitiveCopiedViewsWithinRadius() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:debug-joint-query-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        InMemoryPhysicsSpace space = (InMemoryPhysicsSpace) resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        PhysicsBody bodyA = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody bodyB = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        bodyA.setPosition(1.0f, 0.0f, 0.0f);
        bodyB.setPosition(5.0f, 0.0f, 0.0f);
        resource.addBody(space.id(),
            bodyA,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        resource.addBody(space.id(),
            bodyB,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        var liveJoint = space.createHingeJoint(bodyA,
            bodyB,
            new Vector3f(1.0f, 0.0f, 0.0f),
            new Vector3f(-1.0f, 0.0f, 0.0f),
            new Vector3f(0.0f, 1.0f, 0.0f));
        resource.addJoint(space.id(), liveJoint);

        List<PhysicsDebugJointView> visible = resource.queryInternal(new PhysicsDebugJointsQuery(space.id(),
                3.0f,
                0.0f,
                0.0f,
                1.0f,
                4))
            .toCompletableFuture()
            .join();

        assertEquals(1, visible.size());
        PhysicsDebugJointView joint = visible.getFirst();
        assertEquals(2.0f, joint.anchorAX(), 0.00001f);
        assertEquals(0.0f, joint.anchorAY(), 0.00001f);
        assertEquals(0.0f, joint.anchorAZ(), 0.00001f);
        assertEquals(4.0f, joint.anchorBX(), 0.00001f);
        assertEquals(0.0f, joint.anchorBY(), 0.00001f);
        assertEquals(0.0f, joint.anchorBZ(), 0.00001f);
        assertTrue(joint.hasAxis());
        assertEquals(0.0f, joint.axisX(), 0.00001f);
        assertEquals(0.9f, joint.axisY(), 0.00001f);
        assertEquals(0.0f, joint.axisZ(), 0.00001f);

        assertTrue(resource.queryInternal(new PhysicsDebugJointsQuery(space.id(),
                8.0f,
                0.0f,
                0.0f,
                1.0f,
                4))
            .toCompletableFuture()
            .join()
            .isEmpty());
        assertTrue(resource.queryInternal(new PhysicsDebugJointsQuery(space.id(),
                3.0f,
                0.0f,
                0.0f,
                1.0f,
                0))
            .toCompletableFuture()
            .join()
            .isEmpty());
    }

    @Test
    void raycastClosestBatchReturnsMissesByIndex() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:raycast-batch-result-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        SpaceId spaceId = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults())
            .id();

        RaycastClosestBatchResult result = resource.query(new RaycastClosestBatchQuery(spaceId,
                List.of(new RaycastSegment(0.0f, 1.0f, 0.0f, 0.0f, -1.0f, 0.0f),
                    new RaycastSegment(1.0f, 1.0f, 0.0f, 1.0f, -1.0f, 0.0f))))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(2, result.rayCount());
        assertEquals(0, result.hitCount());
        assertFalse(result.hasHit(0));
        assertNull(result.hit(0));
    }

    @Test
    void benchmarkSpaceStatsQueryClassifiesBodiesWithOneOwnerAggregate() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:benchmark-space-stats-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        InMemoryPhysicsSpace space = (InMemoryPhysicsSpace) resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        PhysicsBody registered = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        registered.setPosition(0.0f, 124.0f, 0.0f);
        resource.addBodyOnOwner(RigidBodyKey.random(),
            space.id(),
            registered,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsBody raw = space.createSphere(1.0f, 1.0f);
        raw.setPosition(0.0f, -40.0f, 0.0f);
        resource.addBody(space.id(),
            raw,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        resource.addBody(space.id(),
            space.createStaticPlane(122.0f),
            PhysicsBodyKind.WORLD_COLLISION,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        BenchmarkSpaceStatsView stats = resource.queryInternal(new BenchmarkSpaceStatsQuery(space.id(),
                122.0f,
                1.0f,
                -32.0f,
                -128.0f,
                false))
            .toCompletableFuture()
            .join();

        assertEquals(3, stats.bodies());
        assertEquals(2, stats.dynamicBodies());
        assertEquals(2, stats.awakeDynamicBodies());
        assertEquals(0, stats.sleepingDynamicBodies());
        assertEquals(1, stats.detachedBodies());
        assertEquals(1, stats.rawBodies());
        assertEquals(0, stats.worldCollisionBodies());
        assertEquals(1, stats.belowPlaneBodies());
        assertEquals(1, stats.belowWorldMinBodies());
        assertEquals(0, stats.belowVoidBodies());
        assertEquals(-40.0, stats.minDynamicBodyY(), 0.00001);
        assertEquals(124.0, stats.maxDynamicBodyY(), 0.00001);
    }

    private static final class ScalarRecordingBody implements PhysicsBody {

        private final Vector3f position = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f linearVelocity = new Vector3f();
        private final Vector3f angularVelocity = new Vector3f();
        private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
        private int activateCalls;
        private int scalarForceCalls;
        private int scalarImpulseCalls;
        private int scalarPositionCalls;
        private int scalarRotationCalls;
        private int scalarTorqueCalls;
        private int scalarTorqueImpulseCalls;
        private int objectPositionCalls;
        private int objectRotationCalls;
        private final Vector3f lastForce = new Vector3f();
        private final Vector3f lastForceOffset = new Vector3f();
        private final Vector3f lastImpulse = new Vector3f();
        private final Vector3f lastImpulseOffset = new Vector3f();
        private final Vector3f lastTorque = new Vector3f();
        private final Vector3f lastTorqueImpulse = new Vector3f();

        @Override
        public void setPosition(float x, float y, float z) {
            scalarPositionCalls++;
            position.set(x, y, z);
        }

        @Override
        public void setPosition(@Nonnull Vector3f pos) {
            objectPositionCalls++;
            position.set(pos);
        }

        @Nonnull
        @Override
        public Vector3f getPosition() {
            return new Vector3f(position);
        }

        @Override
        public void setRotation(float x, float y, float z, float w) {
            scalarRotationCalls++;
            rotation.set(x, y, z, w);
        }

        @Override
        public void setRotation(@Nonnull Quaternionf rot) {
            objectRotationCalls++;
            rotation.set(rot);
        }

        @Nonnull
        @Override
        public Quaternionf getRotation() {
            return new Quaternionf(rotation);
        }

        @Override
        public void setRestitution(float restitution) {
        }

        @Override
        public float getRestitution() {
            return 0.0f;
        }

        @Override
        public void setFriction(float friction) {
        }

        @Override
        public float getFriction() {
            return 0.0f;
        }

        @Nonnull
        @Override
        public PhysicsBodyType getBodyType() {
            return bodyType;
        }

        @Override
        public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
            this.bodyType = bodyType;
        }

        @Override
        public boolean isStatic() {
            return bodyType == PhysicsBodyType.STATIC;
        }

        @Override
        public boolean isKinematic() {
            return bodyType == PhysicsBodyType.KINEMATIC;
        }

        @Override
        public void setKinematic(boolean kinematic) {
            bodyType = kinematic ? PhysicsBodyType.KINEMATIC : PhysicsBodyType.DYNAMIC;
        }

        @Override
        public void activate() {
            activateCalls++;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public boolean isSleeping() {
            return false;
        }

        @Override
        public void sleep() {
        }

        @Override
        public float getMass() {
            return 1.0f;
        }

        @Override
        public void setMass(float mass) {
        }

        @Nonnull
        @Override
        public Vector3f getLinearVelocity() {
            return new Vector3f(linearVelocity);
        }

        @Override
        public void setLinearVelocity(@Nonnull Vector3f vel) {
            linearVelocity.set(vel);
        }

        @Override
        public void setLinearVelocity(float x, float y, float z) {
            linearVelocity.set(x, y, z);
        }

        @Nonnull
        @Override
        public Vector3f getAngularVelocity() {
            return new Vector3f(angularVelocity);
        }

        @Override
        public void setAngularVelocity(@Nonnull Vector3f vel) {
            angularVelocity.set(vel);
        }

        @Override
        public void setAngularVelocity(float x, float y, float z) {
            angularVelocity.set(x, y, z);
        }

        @Override
        public float getLinearDamping() {
            return 0.0f;
        }

        @Override
        public void setLinearDamping(float damping) {
        }

        @Override
        public float getAngularDamping() {
            return 0.0f;
        }

        @Override
        public void setAngularDamping(float damping) {
        }

        @Override
        public void applyCentralForce(@Nonnull Vector3f force) {
            throw new AssertionError("central vector force should not be used");
        }

        @Override
        public void applyCentralForce(float x, float y, float z) {
        }

        @Override
        public void applyForce(@Nonnull Vector3f force, @Nonnull Vector3f offset) {
            throw new AssertionError("offset vector force should not be used");
        }

        @Override
        public void applyForce(float x,
            float y,
            float z,
            float offsetX,
            float offsetY,
            float offsetZ) {
            scalarForceCalls++;
            lastForce.set(x, y, z);
            lastForceOffset.set(offsetX, offsetY, offsetZ);
        }

        @Override
        public void applyCentralImpulse(@Nonnull Vector3f impulse) {
            throw new AssertionError("central vector impulse should not be used");
        }

        @Override
        public void applyCentralImpulse(float x, float y, float z) {
        }

        @Override
        public void applyImpulse(@Nonnull Vector3f impulse, @Nonnull Vector3f offset) {
            throw new AssertionError("offset vector impulse should not be used");
        }

        @Override
        public void applyImpulse(float x,
            float y,
            float z,
            float offsetX,
            float offsetY,
            float offsetZ) {
            scalarImpulseCalls++;
            lastImpulse.set(x, y, z);
            lastImpulseOffset.set(offsetX, offsetY, offsetZ);
        }

        @Override
        public void applyTorque(@Nonnull Vector3f torque) {
            throw new AssertionError("vector torque should not be used");
        }

        @Override
        public void applyTorque(float x, float y, float z) {
            scalarTorqueCalls++;
            lastTorque.set(x, y, z);
        }

        @Override
        public void applyTorqueImpulse(@Nonnull Vector3f torqueImpulse) {
            throw new AssertionError("vector torque impulse should not be used");
        }

        @Override
        public void applyTorqueImpulse(float x, float y, float z) {
            scalarTorqueImpulseCalls++;
            lastTorqueImpulse.set(x, y, z);
        }

        @Override
        public void clearForces() {
        }

        @Override
        public boolean isSensor() {
            return false;
        }

        @Override
        public void setSensor(boolean sensor) {
        }

        @Override
        public int getCollisionGroup() {
            return 0;
        }

        @Override
        public int getCollisionMask() {
            return 0;
        }

        @Override
        public void setCollisionFilter(int group, int mask) {
        }

        @Override
        public boolean isContinuousCollisionEnabled() {
            return false;
        }

        @Override
        public void setContinuousCollisionEnabled(boolean enabled) {
        }

        @Nonnull
        @Override
        public ShapeType getShapeType() {
            return ShapeType.SPHERE;
        }

        @Nullable
        @Override
        public Vector3f getBoxHalfExtents() {
            return null;
        }

        @Override
        public float getSphereRadius() {
            return 0.5f;
        }

        @Override
        public float getHalfHeight() {
            return 0.0f;
        }

        @Nonnull
        @Override
        public PhysicsAxis getShapeAxis() {
            return PhysicsAxis.Y;
        }

        @Override
        public float getCenterOfMassOffsetY() {
            return 0.0f;
        }
    }
}
