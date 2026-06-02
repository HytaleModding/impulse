package dev.hytalemodding.impulse.api.runtime.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import org.junit.jupiter.api.Test;

class LegacyPhysicsBackendRuntimeTest {

    @Test
    void wrapsLegacyBackendWithNumericBodyAndJointIds() {
        LegacyPhysicsBackendRuntime runtime =
            new LegacyPhysicsBackendRuntime(new FakePhysicsBackend("impulse:test"));

        int spaceId = runtime.createSpace(new SpaceId(9001));
        long bodyA = runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_SPHERE,
            0.0f,
            0.0f,
            0.0f,
            0.5f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            0.0f,
            3.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        long bodyB = runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            1.0f,
            3.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        long joint = runtime.createJoint(spaceId,
            BackendRuntimeCodes.JOINT_POINT,
            bodyA,
            bodyB,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            false,
            0.0f,
            0.0f);

        assertEquals(9001, spaceId);
        assertNotEquals(bodyA, bodyB);
        assertTrue(bodyA > 0L);
        assertTrue(bodyB > 0L);
        assertTrue(joint > 0L);
        assertEquals(2, runtime.bodyCount(spaceId));
        assertEquals(1, runtime.jointCount(spaceId));

        int[] snapshotShape = { BackendRuntimeCodes.SHAPE_UNKNOWN };
        int[] snapshotAxis = { -1 };
        boolean snapshotPresent = runtime.bodySnapshot(spaceId,
            bodyA,
            (bodyId,
                shapeTypeCode,
                bodyTypeCode,
                positionX,
                positionY,
                positionZ,
                rotationX,
                rotationY,
                rotationZ,
                rotationW,
                linearVelocityX,
                linearVelocityY,
                linearVelocityZ,
                angularVelocityX,
                angularVelocityY,
                angularVelocityZ,
                sleeping,
                sensor,
                centerOfMassOffsetY,
                hasBoxHalfExtents,
                halfExtentX,
                halfExtentY,
                halfExtentZ,
                radius,
                halfHeight,
                axisCode) -> {
                snapshotShape[0] = shapeTypeCode;
                snapshotAxis[0] = axisCode;
            });

        assertTrue(snapshotPresent);
        assertEquals(ShapeType.SPHERE, BackendRuntimeCodes.shapeType(snapshotShape[0]));
        assertEquals(BackendRuntimeCodes.AXIS_Y, snapshotAxis[0]);
        assertEquals(BackendRuntimeCodes.jointTypeCode(BackendJointType.POINT), runtime.jointType(spaceId, joint));
        assertEquals(bodyA, runtime.jointBodyA(spaceId, joint));
        assertEquals(bodyB, runtime.jointBodyB(spaceId, joint));
    }

    @Test
    void legacyRuntimeDoesNotExposeVoxelTerrainHooks() {
        LegacyPhysicsBackendRuntime runtime =
            new LegacyPhysicsBackendRuntime(new FakePhysicsBackend("impulse:test-voxel"));
        int spaceId = runtime.createSpace(new SpaceId(9002));

        assertFalse(runtime.supportsVoxelTerrain(spaceId));
        assertThrows(UnsupportedOperationException.class,
            () -> runtime.createVoxelTerrain(spaceId,
                1.0f,
                1.0f,
                1.0f,
                new int[] { 0, 0, 0 },
                0.0f,
                0.0f,
                0.0f,
                0.75f,
                0.0f,
                1,
                -1));
        assertThrows(UnsupportedOperationException.class,
            () -> runtime.combineVoxelTerrains(spaceId, 1L, 2L, 16, 0, 0));
    }
}
