package dev.hytalemodding.impulse.jolt;

import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JoltTestNativeLibrary implements JoltNativeLibrary {

    private final Map<Long, SpaceState> spaces = new HashMap<>();
    private long nextSpaceHandle = 1L;
    private long nextBodyHandle = 1001L;
    private long lastRemovedBodyHandle;

    @Override
    public long createSpace() {
        long handle = nextSpaceHandle++;
        spaces.put(handle, new SpaceState());
        return handle;
    }

    @Override
    public void destroySpace(long spaceHandle) {
        spaces.remove(spaceHandle);
    }

    @Override
    public void step(long spaceHandle, float dt) {
        requireSpace(spaceHandle);
    }

    @Override
    public void setGravity(long spaceHandle, float x, float y, float z) {
        SpaceState space = requireSpace(spaceHandle);
        space.gravity[0] = x;
        space.gravity[1] = y;
        space.gravity[2] = z;
    }

    @Override
    public void getGravity(long spaceHandle, float[] out) {
        SpaceState space = requireSpace(spaceHandle);
        out[0] = space.gravity[0];
        out[1] = space.gravity[1];
        out[2] = space.gravity[2];
    }

    @Override
    public long createBody(long spaceHandle,
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
        SpaceState space = requireSpace(spaceHandle);
        long bodyHandle = nextBodyHandle++;
        BodyState body = new BodyState();
        body.snapshot.shapeTypeCode = shapeTypeCode;
        body.snapshot.bodyTypeCode = bodyTypeCode;
        body.snapshot.positionX = positionX;
        body.snapshot.positionY = positionY;
        body.snapshot.positionZ = positionZ;
        body.snapshot.rotationX = rotationX;
        body.snapshot.rotationY = rotationY;
        body.snapshot.rotationZ = rotationZ;
        body.snapshot.rotationW = rotationW;
        body.snapshot.mass = mass;
        body.snapshot.centerOfMassOffsetY = centerOfMassOffsetY(shapeTypeCode,
            halfExtentY,
            radius,
            halfHeight,
            axisCode);
        body.snapshot.hasBoxHalfExtents = halfExtentX > 0.0f
            && halfExtentY > 0.0f
            && halfExtentZ > 0.0f;
        body.snapshot.halfExtentX = halfExtentX;
        body.snapshot.halfExtentY = halfExtentY;
        body.snapshot.halfExtentZ = halfExtentZ;
        body.snapshot.radius = radius;
        body.snapshot.halfHeight = halfHeight;
        body.snapshot.axisCode = axisCode;
        body.groundY = groundY;
        space.bodies.put(bodyHandle, body);
        return bodyHandle;
    }

    @Override
    public void removeBody(long spaceHandle, long bodyHandle) {
        requireSpace(spaceHandle).bodies.remove(bodyHandle);
        lastRemovedBodyHandle = bodyHandle;
    }

    @Override
    public boolean containsBody(long spaceHandle, long bodyHandle) {
        return requireSpace(spaceHandle).bodies.containsKey(bodyHandle);
    }

    @Override
    public boolean bodySnapshot(long spaceHandle, long bodyHandle, JoltBodySnapshot out) {
        BodyState body = requireSpace(spaceHandle).bodies.get(bodyHandle);
        if (body == null) {
            return false;
        }
        out.copyFrom(body.snapshot);
        return true;
    }

    @Override
    public void setBodyTransform(long spaceHandle,
        long bodyHandle,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW) {
        JoltBodySnapshot snapshot = requireBody(spaceHandle, bodyHandle).snapshot;
        snapshot.positionX = positionX;
        snapshot.positionY = positionY;
        snapshot.positionZ = positionZ;
        snapshot.rotationX = rotationX;
        snapshot.rotationY = rotationY;
        snapshot.rotationZ = rotationZ;
        snapshot.rotationW = rotationW;
    }

    @Override
    public void setBodyPosition(long spaceHandle, long bodyHandle, float x, float y, float z) {
        JoltBodySnapshot snapshot = requireBody(spaceHandle, bodyHandle).snapshot;
        snapshot.positionX = x;
        snapshot.positionY = y;
        snapshot.positionZ = z;
    }

    @Override
    public void setBodyVelocity(long spaceHandle,
        long bodyHandle,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ) {
        JoltBodySnapshot snapshot = requireBody(spaceHandle, bodyHandle).snapshot;
        snapshot.linearVelocityX = linearX;
        snapshot.linearVelocityY = linearY;
        snapshot.linearVelocityZ = linearZ;
        snapshot.angularVelocityX = angularX;
        snapshot.angularVelocityY = angularY;
        snapshot.angularVelocityZ = angularZ;
    }

    @Override
    public void setBodyType(long spaceHandle, long bodyHandle, int bodyTypeCode) {
        requireBody(spaceHandle, bodyHandle).snapshot.bodyTypeCode = bodyTypeCode;
    }

    @Override
    public void setBodyDamping(long spaceHandle, long bodyHandle, float linearDamping, float angularDamping) {
        JoltBodySnapshot snapshot = requireBody(spaceHandle, bodyHandle).snapshot;
        snapshot.linearDamping = linearDamping;
        snapshot.angularDamping = angularDamping;
    }

    @Override
    public void setBodyFriction(long spaceHandle, long bodyHandle, float friction) {
        requireBody(spaceHandle, bodyHandle).snapshot.friction = friction;
    }

    @Override
    public void setBodyRestitution(long spaceHandle, long bodyHandle, float restitution) {
        requireBody(spaceHandle, bodyHandle).snapshot.restitution = restitution;
    }

    @Override
    public void setBodyCollisionFilter(long spaceHandle, long bodyHandle, int group, int mask) {
        JoltBodySnapshot snapshot = requireBody(spaceHandle, bodyHandle).snapshot;
        snapshot.collisionGroup = group;
        snapshot.collisionMask = mask;
    }

    @Override
    public void setBodySensor(long spaceHandle, long bodyHandle, boolean sensor) {
        requireBody(spaceHandle, bodyHandle).snapshot.sensor = sensor;
    }

    @Override
    public void setBodyContinuousCollision(long spaceHandle, long bodyHandle, boolean enabled) {
        requireBody(spaceHandle, bodyHandle).snapshot.continuousCollisionEnabled = enabled;
    }

    @Override
    public boolean isBodyContinuousCollisionEnabled(long spaceHandle, long bodyHandle) {
        return requireBody(spaceHandle, bodyHandle).snapshot.continuousCollisionEnabled;
    }

    @Override
    public void activateBody(long spaceHandle, long bodyHandle) {
        requireBody(spaceHandle, bodyHandle).snapshot.sleeping = false;
    }

    @Override
    public void sleepBody(long spaceHandle, long bodyHandle) {
        requireBody(spaceHandle, bodyHandle).snapshot.sleeping = true;
    }

    @Override
    public void applyBodyImpulse(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        requireBody(spaceHandle, bodyHandle).impulses++;
    }

    @Override
    public void applyBodyForce(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        requireBody(spaceHandle, bodyHandle).forces++;
    }

    @Override
    public int bodyCount(long spaceHandle) {
        return requireSpace(spaceHandle).bodies.size();
    }

    @Override
    public int jointCount(long spaceHandle) {
        return 0;
    }

    @Override
    public int raycastClosest(long spaceHandle,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        long[] bodyHandleOut,
        float[] hitOut) {
        SpaceState space = requireSpace(spaceHandle);
        if (space.rayHits.isEmpty()) {
            return 0;
        }
        RayHit hit = space.rayHits.getFirst();
        bodyHandleOut[0] = hit.bodyHandle;
        hit.copyTo(hitOut, 0);
        return 1;
    }

    @Override
    public int raycastAll(long spaceHandle,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        int maxHits,
        long[] bodyHandles,
        float[] hits) {
        SpaceState space = requireSpace(spaceHandle);
        int emitted = Math.min(Math.max(maxHits, 0), space.rayHits.size());
        for (int index = 0; index < emitted; index++) {
            RayHit hit = space.rayHits.get(index);
            bodyHandles[index] = hit.bodyHandle;
            hit.copyTo(hits, index * 8);
        }
        return emitted;
    }

    @Override
    public int contacts(long spaceHandle, int maxContacts, long[] bodyHandles, float[] contacts) {
        SpaceState space = requireSpace(spaceHandle);
        int emitted = Math.min(Math.max(maxContacts, 0), space.contacts.size());
        for (int index = 0; index < emitted; index++) {
            Contact contact = space.contacts.get(index);
            bodyHandles[index * 2] = contact.bodyAHandle;
            bodyHandles[index * 2 + 1] = contact.bodyBHandle;
            contact.copyTo(contacts, index * 11);
        }
        return emitted;
    }

    @Override
    public int contactCount(long spaceHandle) {
        return requireSpace(spaceHandle).contacts.size();
    }

    void addRayHit(long spaceHandle,
        long bodyHandle,
        float pointX,
        float pointY,
        float pointZ,
        float fraction,
        float distance) {
        requireSpace(spaceHandle).rayHits.add(new RayHit(bodyHandle,
            pointX,
            pointY,
            pointZ,
            0.0f,
            1.0f,
            0.0f,
            fraction,
            distance));
    }

    void addContact(long spaceHandle,
        long bodyAHandle,
        long bodyBHandle,
        float pointY,
        float distance) {
        requireSpace(spaceHandle).contacts.add(new Contact(bodyAHandle,
            bodyBHandle,
            0.0f,
            pointY,
            0.0f,
            0.0f,
            pointY,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            distance,
            0.0f));
    }

    long nativeBodyHandle(long spaceHandle, int index) {
        return requireSpace(spaceHandle).bodies.keySet().stream()
            .skip(index)
            .findFirst()
            .orElseThrow();
    }

    long firstSpaceHandle() {
        return spaces.keySet().iterator().next();
    }

    long lastRemovedBodyHandle() {
        return lastRemovedBodyHandle;
    }

    BodyState body(long spaceHandle, long bodyHandle) {
        return requireBody(spaceHandle, bodyHandle);
    }

    private SpaceState requireSpace(long spaceHandle) {
        SpaceState space = spaces.get(spaceHandle);
        if (space == null) {
            throw new IllegalArgumentException("Unknown native space handle: " + spaceHandle);
        }
        return space;
    }

    private BodyState requireBody(long spaceHandle, long bodyHandle) {
        BodyState body = requireSpace(spaceHandle).bodies.get(bodyHandle);
        if (body == null) {
            throw new IllegalArgumentException("Unknown native body handle: " + bodyHandle);
        }
        return body;
    }

    private static float centerOfMassOffsetY(int shapeTypeCode,
        float halfExtentY,
        float radius,
        float halfHeight,
        int axisCode) {
        return switch (shapeTypeCode) {
            case 1 -> halfExtentY;
            case 2 -> radius;
            case 3, 4 -> axisCode == 2 ? radius + halfHeight : radius;
            case 5 -> axisCode == 2 ? halfHeight : radius;
            default -> 0.0f;
        };
    }

    private static final class SpaceState {

        private final Map<Long, BodyState> bodies = new LinkedHashMap<>();
        private final float[] gravity = new float[] {0.0f, -9.81f, 0.0f};
        private final List<RayHit> rayHits = new ArrayList<>();
        private final List<Contact> contacts = new ArrayList<>();
    }

    static final class BodyState {

        private final JoltBodySnapshot snapshot = new JoltBodySnapshot();
        private float groundY;
        private int impulses;
        private int forces;

        JoltBodySnapshot snapshot() {
            return snapshot;
        }

        float groundY() {
            return groundY;
        }

        int impulses() {
            return impulses;
        }

        int forces() {
            return forces;
        }
    }

    static final class CapturedBodySnapshot implements BackendBodySnapshotSink {

        long bodyId;
        int shapeTypeCode;
        int bodyTypeCode;
        float positionX;
        float positionY;
        float positionZ;
        float rotationX;
        float rotationY;
        float rotationZ;
        float rotationW;
        float linearVelocityX;
        float linearVelocityY;
        float linearVelocityZ;
        float angularVelocityX;
        float angularVelocityY;
        float angularVelocityZ;
        boolean sleeping;
        boolean sensor;
        float mass;
        float friction;
        float restitution;
        float linearDamping;
        float angularDamping;
        int collisionGroup;
        int collisionMask;
        boolean continuousCollisionEnabled;
        float centerOfMassOffsetY;
        boolean hasBoxHalfExtents;
        float halfExtentX;
        float halfExtentY;
        float halfExtentZ;
        float radius;
        float halfHeight;
        int axisCode;
        int captures;

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
            this.bodyId = bodyId;
            this.shapeTypeCode = shapeTypeCode;
            this.bodyTypeCode = bodyTypeCode;
            this.positionX = positionX;
            this.positionY = positionY;
            this.positionZ = positionZ;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.rotationW = rotationW;
            this.linearVelocityX = linearVelocityX;
            this.linearVelocityY = linearVelocityY;
            this.linearVelocityZ = linearVelocityZ;
            this.angularVelocityX = angularVelocityX;
            this.angularVelocityY = angularVelocityY;
            this.angularVelocityZ = angularVelocityZ;
            this.sleeping = sleeping;
            this.sensor = sensor;
            this.mass = mass;
            this.friction = friction;
            this.restitution = restitution;
            this.linearDamping = linearDamping;
            this.angularDamping = angularDamping;
            this.collisionGroup = collisionGroup;
            this.collisionMask = collisionMask;
            this.continuousCollisionEnabled = continuousCollisionEnabled;
            this.centerOfMassOffsetY = centerOfMassOffsetY;
            this.hasBoxHalfExtents = hasBoxHalfExtents;
            this.halfExtentX = halfExtentX;
            this.halfExtentY = halfExtentY;
            this.halfExtentZ = halfExtentZ;
            this.radius = radius;
            this.halfHeight = halfHeight;
            this.axisCode = axisCode;
            captures++;
        }
    }

    private static final class RayHit {

        private final long bodyHandle;
        private final float pointX;
        private final float pointY;
        private final float pointZ;
        private final float normalX;
        private final float normalY;
        private final float normalZ;
        private final float fraction;
        private final float distance;

        private RayHit(long bodyHandle,
            float pointX,
            float pointY,
            float pointZ,
            float normalX,
            float normalY,
            float normalZ,
            float fraction,
            float distance) {
            this.bodyHandle = bodyHandle;
            this.pointX = pointX;
            this.pointY = pointY;
            this.pointZ = pointZ;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
            this.fraction = fraction;
            this.distance = distance;
        }

        private void copyTo(float[] values, int offset) {
            values[offset] = pointX;
            values[offset + 1] = pointY;
            values[offset + 2] = pointZ;
            values[offset + 3] = normalX;
            values[offset + 4] = normalY;
            values[offset + 5] = normalZ;
            values[offset + 6] = fraction;
            values[offset + 7] = distance;
        }
    }

    private static final class Contact {

        private final long bodyAHandle;
        private final long bodyBHandle;
        private final float pointAX;
        private final float pointAY;
        private final float pointAZ;
        private final float pointBX;
        private final float pointBY;
        private final float pointBZ;
        private final float normalBX;
        private final float normalBY;
        private final float normalBZ;
        private final float distance;
        private final float impulse;

        private Contact(long bodyAHandle,
            long bodyBHandle,
            float pointAX,
            float pointAY,
            float pointAZ,
            float pointBX,
            float pointBY,
            float pointBZ,
            float normalBX,
            float normalBY,
            float normalBZ,
            float distance,
            float impulse) {
            this.bodyAHandle = bodyAHandle;
            this.bodyBHandle = bodyBHandle;
            this.pointAX = pointAX;
            this.pointAY = pointAY;
            this.pointAZ = pointAZ;
            this.pointBX = pointBX;
            this.pointBY = pointBY;
            this.pointBZ = pointBZ;
            this.normalBX = normalBX;
            this.normalBY = normalBY;
            this.normalBZ = normalBZ;
            this.distance = distance;
            this.impulse = impulse;
        }

        private void copyTo(float[] values, int offset) {
            values[offset] = pointAX;
            values[offset + 1] = pointAY;
            values[offset + 2] = pointAZ;
            values[offset + 3] = pointBX;
            values[offset + 4] = pointBY;
            values[offset + 5] = pointBZ;
            values[offset + 6] = normalBX;
            values[offset + 7] = normalBY;
            values[offset + 8] = normalBZ;
            values[offset + 9] = distance;
            values[offset + 10] = impulse;
        }
    }
}
