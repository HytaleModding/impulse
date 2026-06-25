package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RapierVoxelTerrainTest {

    @Test
    void dynamicBoxRestsOnNativeVoxelFloor() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(10));
        try {
            runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
            addVoxelFloor(runtime, spaceId, 0.0f);
            long boxId = addDynamicBox(runtime, spaceId, 8.0f, 3.0f, 8.0f);

            StepResult result = stepAndTrackMinimumY(runtime, spaceId, boxId, 240);

            assertFalse(result.fellThrough());
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void dynamicBoxRestsNearNativeVoxelSectionEdge() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(11));
        try {
            runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
            addVoxelFloor(runtime, spaceId, 0.0f);
            long boxId = addDynamicBox(runtime, spaceId, 15.75f, 3.0f, 8.0f);

            StepResult result = stepAndTrackMinimumY(runtime, spaceId, boxId, 240);

            assertFalse(result.fellThrough());
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void dynamicBoxRestsAcrossStitchedNativeVoxelSections() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(12));
        try {
            runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
            long first = addVoxelFloor(runtime, spaceId, 0.0f);
            long second = addVoxelFloor(runtime, spaceId, 16.0f);
            runtime.combineVoxelTerrains(spaceId, first, second, 16, 0, 0);
            long boxId = addDynamicBox(runtime, spaceId, 16.0f, 3.0f, 8.0f);

            StepResult result = stepAndTrackMinimumY(runtime, spaceId, boxId, 240);

            assertFalse(result.fellThrough());
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void combineVoxelTerrainNativeReportsInvalidSpaceHandle() {
        RapierNative.load();

        assertFalse(RapierNative.combineVoxelTerrainNative(0L, 1L, 2L, 1, 0, 0));
    }

    @Test
    void combineVoxelTerrainsRejectsNonVoxelBodies() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(2));
        try {
            long voxelBody = addVoxelFloor(runtime, spaceId, 0.0f);
            long box = addDynamicBox(runtime, spaceId, 0.0f, 1.0f, 0.0f);

            assertThrows(IllegalArgumentException.class,
                () -> runtime.combineVoxelTerrains(spaceId, voxelBody, box, 1, 0, 0));
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void combineVoxelTerrainsRejectsSameBody() {
        PhysicsBackendRuntime runtime = runtime();
        int spaceId = runtime.createSpace(new SpaceId(3));
        try {
            long voxelBody = addVoxelFloor(runtime, spaceId, 0.0f);

            assertThrows(IllegalArgumentException.class,
                () -> runtime.combineVoxelTerrains(spaceId, voxelBody, voxelBody, 1, 0, 0));
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static PhysicsBackendRuntime runtime() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        return provider.createRuntime();
    }

    private static long addVoxelFloor(PhysicsBackendRuntime runtime, int spaceId, float originX) {
        return runtime.createVoxelTerrain(spaceId,
            1.0f,
            1.0f,
            1.0f,
            voxelFloorCoordinates(16, 16),
            originX,
            0.0f,
            0.0f,
            0.5f,
            0.0f,
            1,
            1);
    }

    private static int[] voxelFloorCoordinates(int width, int depth) {
        int[] coordinates = new int[width * depth * 3];
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                coordinates[index++] = x;
                coordinates[index++] = 0;
                coordinates[index++] = z;
            }
        }
        return coordinates;
    }

    private static long addDynamicBox(PhysicsBackendRuntime runtime, int spaceId, float x, float y, float z) {
        return runtime.createBody(spaceId,
            BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
            0.45f,
            0.45f,
            0.45f,
            -1.0f,
            -1.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            x,
            y,
            z,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static StepResult stepAndTrackMinimumY(PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId,
        int steps) {
        CapturedSnapshot snapshot = new CapturedSnapshot();
        float minY = Float.POSITIVE_INFINITY;
        for (int i = 0; i < steps; i++) {
            runtime.step(spaceId, 1.0f / 30.0f);
            runtime.bodySnapshot(spaceId, bodyId, snapshot);
            minY = Math.min(minY, snapshot.position.y);
        }
        return new StepResult(new Vector3f(snapshot.position), minY);
    }

    private record StepResult(Vector3f finalPosition, float minY) {

        private boolean fellThrough() {
            return finalPosition.y < 1.0f || minY < 0.75f;
        }
    }

    private static final class CapturedSnapshot implements BackendBodySnapshotSink {

        private final Vector3f position = new Vector3f();

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
        }
    }
}
