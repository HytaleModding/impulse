package dev.hytalemodding.impulse.rapier;

import dev.hytalemodding.impulse.internal.nativelib.NativeLibraryLoader;
import dev.hytalemodding.impulse.internal.nativelib.NativeLibraryResource;

/**
 * JNI bridge for the Rapier backend.
 * <p>
 * The long {@code Java_dev_hytalemodding_impulse_...} function names are standard JNI mangled
 * symbols.
 */
final class RapierNative {

    private static final String LIBRARY_NAME = "impulse_rapier";
    private static volatile boolean loaded;

    private RapierNative() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }

        try {
            NativeLibraryLoader.load(RapierNative.class,
                "rapier",
                NativeLibraryResource.forCurrentPlatform(LIBRARY_NAME));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IllegalStateException("Failed to load the Rapier native library", exception);
        }

        loaded = true;
    }

    static native long createSpaceNative();

    static native void destroySpaceNative(long spaceHandle);

    static native void setGravityNative(long spaceHandle, float x, float y, float z);

    static native void getGravityNative(long spaceHandle, float[] out);

    static native boolean stepNative(long spaceHandle, float dt);

    static native float[] stepContactEventsNative(long spaceHandle, float dt);

    static native int[] getRuntimeStatsNative(long spaceHandle);

    static native void resetStepPhaseStatsNative(long spaceHandle);

    static native long[] getStepPhaseStatsNative(long spaceHandle);

    static native int snapshotBodiesNative(long spaceHandle, long[] bodyHandles, int bodyCount, float[] out);

    static native void setSolverTuningNative(long spaceHandle,
        int solverIterations,
        int internalPgsIterations,
        int stabilizationIterations,
        int minIslandSize);

    static native void setDynamicSleepTuningNative(long spaceHandle,
        float linearThreshold,
        float angularThreshold,
        float timeUntilSleep);

    static native long addBodyNative(long spaceHandle,
        int shapeType,
        float halfX,
        float halfY,
        float halfZ,
        float radius,
        float halfHeight,
        int axis,
        int bodyType,
        float mass,
        float posX,
        float posY,
        float posZ,
        float rotX,
        float rotY,
        float rotZ,
        float rotW,
        float linVelX,
        float linVelY,
        float linVelZ,
        float angVelX,
        float angVelY,
        float angVelZ,
        float friction,
        float restitution,
        float linearDamping,
        float angularDamping,
        boolean sensor,
        int collisionGroup,
        int collisionMask,
        boolean ccdEnabled);

    static native long addVoxelTerrainNative(long spaceHandle,
        float voxelSizeX,
        float voxelSizeY,
        float voxelSizeZ,
        int[] voxelCoordinates,
        float posX,
        float posY,
        float posZ,
        float friction,
        float restitution,
        int collisionGroup,
        int collisionMask);

    static native boolean combineVoxelTerrainNative(long spaceHandle,
        long bodyHandleA,
        long bodyHandleB,
        int shiftX,
        int shiftY,
        int shiftZ);

    static native int jointHandleCountNative(long spaceHandle);

    static native void removeBodyNative(long spaceHandle, long bodyHandle);

    static native void getBodyPositionNative(long spaceHandle, long bodyHandle, float[] out);

    static native void setBodyPositionNative(long spaceHandle, long bodyHandle, float x, float y, float z);

    static native void getBodyRotationNative(long spaceHandle, long bodyHandle, float[] out);

    static native void setBodyRotationNative(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z,
        float w);

    static native void getBodyLinearVelocityNative(long spaceHandle, long bodyHandle, float[] out);

    static native void setBodyLinearVelocityNative(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z);

    static native void getBodyAngularVelocityNative(long spaceHandle, long bodyHandle, float[] out);

    static native void setBodyAngularVelocityNative(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z);

    static native void setBodyTypeNative(long spaceHandle, long bodyHandle, int bodyType);

    static native int getBodyTypeNative(long spaceHandle, long bodyHandle);

    static native void setBodyMassNative(long spaceHandle, long bodyHandle, float mass);

    static native float getBodyMassNative(long spaceHandle, long bodyHandle);

    static native void setBodyDampingNative(long spaceHandle,
        long bodyHandle,
        float linearDamping,
        float angularDamping);

    static native void getBodyDampingNative(long spaceHandle, long bodyHandle, float[] out);

    static native boolean isBodySleepingNative(long spaceHandle, long bodyHandle);

    static native void sleepBodyNative(long spaceHandle, long bodyHandle);

    static native void activateBodyNative(long spaceHandle, long bodyHandle);

    static native void applyBodyCentralForceNative(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z);

    static native void applyBodyForceNative(long spaceHandle,
        long bodyHandle,
        float forceX,
        float forceY,
        float forceZ,
        float offsetX,
        float offsetY,
        float offsetZ);

    static native void applyBodyCentralImpulseNative(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z);

    static native void applyBodyImpulseNative(long spaceHandle,
        long bodyHandle,
        float impulseX,
        float impulseY,
        float impulseZ,
        float offsetX,
        float offsetY,
        float offsetZ);

    static native void applyBodyTorqueNative(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z);

    static native void applyBodyTorqueImpulseNative(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z);

    static native void clearBodyForcesNative(long spaceHandle, long bodyHandle);

    static native void setBodyFrictionNative(long spaceHandle, long bodyHandle, float friction);

    static native void setBodyRestitutionNative(long spaceHandle, long bodyHandle, float restitution);

    static native void setBodySensorNative(long spaceHandle, long bodyHandle, boolean sensor);

    static native boolean isBodySensorNative(long spaceHandle, long bodyHandle);

    static native void setBodyCollisionFilterNative(long spaceHandle,
        long bodyHandle,
        int group,
        int mask);

    static native void getBodyCollisionFilterNative(long spaceHandle, long bodyHandle, int[] out);

    static native void setBodyCcdNative(long spaceHandle, long bodyHandle, boolean enabled);

    static native boolean isBodyCcdNative(long spaceHandle, long bodyHandle);

    static native float[] raycastAllNative(long spaceHandle,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ);

    static native float[] getContactsNative(long spaceHandle);

    static native long addJointNative(long spaceHandle,
        int jointType,
        long bodyA,
        long bodyB,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ,
        float restLength,
        float stiffness,
        float damping);

    static native void removeJointNative(long spaceHandle, long jointHandle);

    static native void setJointEnabledNative(long spaceHandle, long jointHandle, boolean enabled);

    static native boolean isJointEnabledNative(long spaceHandle, long jointHandle);

    static native void setJointLimitsNative(long spaceHandle, long jointHandle, float lowerLimit, float upperLimit);

    static native void setJointMotorNative(long spaceHandle,
        long jointHandle,
        boolean enabled,
        float targetVelocity,
        float maxForce);
}
