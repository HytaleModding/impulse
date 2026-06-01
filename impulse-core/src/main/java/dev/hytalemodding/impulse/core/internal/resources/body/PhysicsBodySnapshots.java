package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Internal conversion helpers for primitive backend-runtime body snapshots.
 */
public final class PhysicsBodySnapshots {

    private PhysicsBodySnapshots() {
    }

    @Nullable
    public static PhysicsBodySnapshot read(@Nonnull PhysicsSpaceBinding space,
        long backendBodyId) {
        PhysicsBodySnapshot[] snapshot = new PhysicsBodySnapshot[1];
        space.runtime().bodySnapshot(space.backendSpaceHandle().value(),
            backendBodyId,
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
                axisCode) -> snapshot[0] = fromRuntimeFields(shapeTypeCode,
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
                    axisCode));
        return snapshot[0];
    }

    @Nonnull
    public static PhysicsBodySnapshot fromRuntimeFields(int shapeTypeCode,
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
        float centerOfMassOffsetY,
        boolean hasBoxHalfExtents,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        int axisCode) {
        return PhysicsBodySnapshot.of(positionX,
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
            BackendRuntimeCodes.bodyType(bodyTypeCode),
            sleeping,
            sensor,
            centerOfMassOffsetY,
            BackendRuntimeCodes.shapeType(shapeTypeCode),
            hasBoxHalfExtents,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            BackendRuntimeCodes.axis(axisCode));
    }
}
