package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
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
        return read(space.runtime(), space.backendSpaceHandle().value(), backendBodyId);
    }

    @Nullable
    public static PhysicsBodySnapshot read(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        long backendBodyId) {
        SnapshotCapture capture = new SnapshotCapture();
        boolean present = runtime.bodySnapshot(spaceId,
            backendBodyId,
            capture);
        return present ? capture.snapshot : null;
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
            mass,
            friction,
            restitution,
            linearDamping,
            angularDamping,
            collisionGroup,
            collisionMask,
            continuousCollisionEnabled,
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

    private static final class SnapshotCapture implements BackendBodySnapshotSink {

        @Nullable
        private PhysicsBodySnapshot snapshot;

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
            snapshot = fromRuntimeFields(shapeTypeCode,
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
                mass,
                friction,
                restitution,
                linearDamping,
                angularDamping,
                collisionGroup,
                collisionMask,
                continuousCollisionEnabled,
                centerOfMassOffsetY,
                hasBoxHalfExtents,
                halfExtentX,
                halfExtentY,
                halfExtentZ,
                radius,
                halfHeight,
                axisCode);
        }
    }
}
