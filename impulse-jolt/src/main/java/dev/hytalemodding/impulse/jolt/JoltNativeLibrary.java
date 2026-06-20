package dev.hytalemodding.impulse.jolt;

interface JoltNativeLibrary {

    int RAY_HIT_FLOAT_COUNT = 8;
    int CONTACT_BODY_HANDLE_COUNT = 2;
    int CONTACT_FLOAT_COUNT = 11;

    long createSpace();

    void destroySpace(long spaceHandle);

    void step(long spaceHandle, float dt);

    void setGravity(long spaceHandle, float x, float y, float z);

    void getGravity(long spaceHandle, float[] out);

    default long createBody(long spaceHandle,
        int shapeTypeCode,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        int axisCode,
        float groundY,
        float mass,
        int bodyTypeCode,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW) {
        throw unsupportedBodyAbi();
    }

    default void removeBody(long spaceHandle, long bodyHandle) {
        throw unsupportedBodyAbi();
    }

    default boolean containsBody(long spaceHandle, long bodyHandle) {
        throw unsupportedBodyAbi();
    }

    default boolean bodySnapshot(long spaceHandle, long bodyHandle, JoltBodySnapshot out) {
        throw unsupportedBodyAbi();
    }

    default void setBodyTransform(long spaceHandle,
        long bodyHandle,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW) {
        throw unsupportedBodyAbi();
    }

    default void setBodyPosition(long spaceHandle, long bodyHandle, float x, float y, float z) {
        throw unsupportedBodyAbi();
    }

    default void setBodyVelocity(long spaceHandle,
        long bodyHandle,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ) {
        throw unsupportedBodyAbi();
    }

    default void setBodyType(long spaceHandle, long bodyHandle, int bodyTypeCode) {
        throw unsupportedBodyAbi();
    }

    default void setBodyDamping(long spaceHandle, long bodyHandle, float linearDamping, float angularDamping) {
        throw unsupportedBodyAbi();
    }

    default void setBodyFriction(long spaceHandle, long bodyHandle, float friction) {
        throw unsupportedBodyAbi();
    }

    default void setBodyRestitution(long spaceHandle, long bodyHandle, float restitution) {
        throw unsupportedBodyAbi();
    }

    default void setBodyCollisionFilter(long spaceHandle, long bodyHandle, int group, int mask) {
        throw unsupportedBodyAbi();
    }

    default void setBodySensor(long spaceHandle, long bodyHandle, boolean sensor) {
        throw unsupportedBodyAbi();
    }

    default void setBodyContinuousCollision(long spaceHandle, long bodyHandle, boolean enabled) {
        throw unsupportedBodyAbi();
    }

    default boolean isBodyContinuousCollisionEnabled(long spaceHandle, long bodyHandle) {
        throw unsupportedBodyAbi();
    }

    default void activateBody(long spaceHandle, long bodyHandle) {
        throw unsupportedBodyAbi();
    }

    default void sleepBody(long spaceHandle, long bodyHandle) {
        throw unsupportedBodyAbi();
    }

    default void applyBodyImpulse(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        throw unsupportedBodyAbi();
    }

    default void applyBodyForce(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        throw unsupportedBodyAbi();
    }

    default int raycastClosest(long spaceHandle,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        long[] bodyHandleOut,
        float[] hitOut) {
        throw unsupportedQueryAbi();
    }

    default int raycastAll(long spaceHandle,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        int maxHits,
        long[] bodyHandles,
        float[] hits) {
        throw unsupportedQueryAbi();
    }

    default int contacts(long spaceHandle,
        int maxContacts,
        long[] bodyHandles,
        float[] contacts) {
        throw unsupportedQueryAbi();
    }

    default int contactCount(long spaceHandle) {
        throw unsupportedQueryAbi();
    }

    int bodyCount(long spaceHandle);

    int jointCount(long spaceHandle);

    private static UnsupportedOperationException unsupportedBodyAbi() {
        return new UnsupportedOperationException("Jolt native body ABI is not implemented");
    }

    private static UnsupportedOperationException unsupportedQueryAbi() {
        return new UnsupportedOperationException("Jolt native query ABI is not implemented");
    }
}
