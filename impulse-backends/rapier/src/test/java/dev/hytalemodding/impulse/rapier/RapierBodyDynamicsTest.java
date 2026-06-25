package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RapierBodyDynamicsTest {

    @Test
    void dynamicBodyFallsUnderGravity() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(800));
        try {
            runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
            long bodyId = createBox(runtime,
                spaceId,
                1.0f,
                PhysicsBodyType.DYNAMIC,
                0.0f,
                20.0f,
                0.0f);

            step(runtime, spaceId, 60);

            CapturedSnapshot snapshot = requireSnapshot(runtime, spaceId, bodyId);
            assertTrue(snapshot.position.y < 20.0f);
            assertTrue(snapshot.linearVelocity.y < 0.0f);
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void staticBodyDoesNotMoveUnderGravity() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(801));
        try {
            runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
            long bodyId = createBox(runtime,
                spaceId,
                0.0f,
                PhysicsBodyType.STATIC,
                0.0f,
                10.0f,
                0.0f);

            step(runtime, spaceId, 60);

            CapturedSnapshot snapshot = requireSnapshot(runtime, spaceId, bodyId);
            assertEquals(10.0f, snapshot.position.y, 0.01f);
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void dynamicBodyLandsOnGroundPlane() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(802));
        try {
            runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
            createPlane(runtime, spaceId, 0.0f);
            long bodyId = createBox(runtime,
                spaceId,
                1.0f,
                PhysicsBodyType.DYNAMIC,
                0.0f,
                5.0f,
                0.0f);

            step(runtime, spaceId, 300);

            CapturedSnapshot snapshot = requireSnapshot(runtime, spaceId, bodyId);
            assertTrue(snapshot.position.y < 3.0f);
            assertTrue(snapshot.position.y > -0.5f);
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void dynamicBodySettlesOnGroundPlane() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(803));
        try {
            runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
            createPlane(runtime, spaceId, 0.0f);
            long bodyId = createBox(runtime,
                spaceId,
                1.0f,
                PhysicsBodyType.DYNAMIC,
                0.0f,
                2.0f,
                0.0f);

            step(runtime, spaceId, 300);

            CapturedSnapshot snapshot = requireSnapshot(runtime, spaceId, bodyId);
            assertTrue(snapshot.linearVelocity.length() < 0.5f);
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void raycastHitsGroundPlane() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(804));
        try {
            createPlane(runtime, spaceId, 0.0f);

            int hits = runtime.raycastAll(spaceId,
                0.0f,
                10.0f,
                0.0f,
                0.0f,
                -10.0f,
                0.0f,
                (_, _, _, _, _, _, _, _, _) -> {
                });

            assertTrue(hits > 0);
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static PhysicsBackendRuntime runtime() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        return provider.createRuntime();
    }

    private static void step(PhysicsBackendRuntime runtime, int spaceId, int steps) {
        for (int i = 0; i < steps; i++) {
            runtime.step(spaceId, 1.0f / 60.0f);
        }
    }

    private static CapturedSnapshot requireSnapshot(PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId) {
        CapturedSnapshot snapshot = new CapturedSnapshot();
        if (!runtime.bodySnapshot(spaceId, bodyId, snapshot)) {
            throw new AssertionError("Missing backend snapshot for body " + bodyId);
        }
        return snapshot;
    }

    private static long createBox(PhysicsBackendRuntime runtime,
        int spaceId,
        float mass,
        PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ) {
        return runtime.createBody(spaceId,
            BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
            0.5f,
            0.5f,
            0.5f,
            -1.0f,
            -1.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            mass,
            BackendRuntimeCodes.bodyTypeCode(bodyType),
            positionX,
            positionY,
            positionZ,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static long createPlane(PhysicsBackendRuntime runtime, int spaceId, float groundY) {
        return runtime.createBody(spaceId,
            BackendRuntimeCodes.shapeTypeCode(ShapeType.PLANE),
            -1.0f,
            -1.0f,
            -1.0f,
            -1.0f,
            -1.0f,
            BackendRuntimeCodes.AXIS_Y,
            groundY,
            0.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.STATIC),
            0.0f,
            groundY,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static final class CapturedSnapshot implements BackendBodySnapshotSink {

        private final Vector3f position = new Vector3f();
        private final Vector3f linearVelocity = new Vector3f();

        @Override
        public void accept(long bodyId,
            int shapeTypeCode,
            int bodyTypeCode,
            float positionX,
            float positionY,
            float positionZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW,
            float linearVelocityX,
            float linearVelocityY,
            float linearVelocityZ,
            float angularVelocityX,
            float angularVelocityY,
            float angularVelocityZ,
            boolean sleeping,
            boolean sensor,
            float mass,
            float friction,
            float restitution,
            float linearDamping,
            float angularDamping,
            int collisionGroup,
            int collisionMask,
            boolean continuousCollisionEnabled,
            float centerOfMassOffsetY,
            boolean hasBoxHalfExtents,
            float halfExtentX,
            float halfExtentY,
            float halfExtentZ,
            float radius,
            float halfHeight,
            int axisCode) {
            position.set(positionX, positionY, positionZ);
            linearVelocity.set(linearVelocityX, linearVelocityY, linearVelocityZ);
        }
    }
}
