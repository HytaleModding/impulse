package dev.hytalemodding.impulse.rapier;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RapierSpace implements PhysicsSpace {

    private static final Cleaner CLEANER = Cleaner.create();
    private static final int RAY_HIT_FLOATS = 9;
    private static final int CONTACT_FLOATS = 13;

    private final SpaceId id;
    private final RapierBackend backend;
    private final long nativeSpaceHandle;
    private final Cleaner.Cleanable cleanable;
    private final List<RapierBody> bodies = new ArrayList<>();
    private final Map<Long, RapierBody> bodiesByHandle = new HashMap<>();
    private final List<RapierJoint> joints = new ArrayList<>();

    RapierSpace(@Nonnull SpaceId id, @Nonnull RapierBackend backend, long nativeSpaceHandle) {
        if (nativeSpaceHandle == 0L) {
            throw new IllegalStateException("Rapier returned a null native space handle");
        }
        this.id = id;
        this.backend = backend;
        this.nativeSpaceHandle = nativeSpaceHandle;
        this.cleanable = CLEANER.register(this, new NativeSpaceCleanup(nativeSpaceHandle));
    }

    @Nonnull
    @Override
    public SpaceId getId() {
        return id;
    }

    @Nonnull
    @Override
    public BackendId getBackendId() {
        return backend.getId();
    }

    @Override
    public void step(float dt) {
        if (dt <= 0f) {
            return;
        }
        RapierNative.stepNative(nativeSpaceHandle, dt);
    }

    @Override
    public void setGravity(float x, float y, float z) {
        RapierNative.setGravityNative(nativeSpaceHandle, x, y, z);
    }

    @Nonnull
    @Override
    public Vector3f getGravity() {
        float[] out = new float[3];
        RapierNative.getGravityNative(nativeSpaceHandle, out);
        return new Vector3f(out[0], out[1], out[2]);
    }

    @Override
    public void addBody(@Nonnull PhysicsBody body) {
        if (!(body instanceof RapierBody rapierBody)) {
            throw new IllegalArgumentException("Body does not belong to rapier backend");
        }
        if (rapierBody.isAttached()) {
            throw new IllegalStateException("Rapier body is already attached to a space");
        }

        long nativeBodyHandle = addNativeBody(rapierBody);
        if (nativeBodyHandle == 0L) {
            throw new IllegalStateException("Rapier returned a null native body handle");
        }

        rapierBody.attach(this, nativeBodyHandle);
        bodies.add(rapierBody);
        bodiesByHandle.put(nativeBodyHandle, rapierBody);
    }

    @Override
    public void removeBody(@Nonnull PhysicsBody body) {
        if (!(body instanceof RapierBody rapierBody)) {
            return;
        }
        if (!rapierBody.isAttached()) {
            return;
        }

        long handle = rapierBody.getBodyHandle();
        RapierNative.removeBodyNative(nativeSpaceHandle, handle);
        rapierBody.detach(this);
        bodies.remove(rapierBody);
        bodiesByHandle.remove(handle);
    }

    @Nonnull
    @Override
    public List<PhysicsBody> getBodies() {
        return new ArrayList<>(bodies);
    }

    @Nonnull
    @Override
    public PhysicsBody createStaticPlane(float groundY) {
        return RapierBody.staticPlane(groundY);
    }

    @Nonnull
    @Override
    public PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass) {
        return RapierBody.box(halfX, halfY, halfZ, mass);
    }

    @Nonnull
    @Override
    public PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass) {
        return createBox(halfExtents.x, halfExtents.y, halfExtents.z, mass);
    }

    @Override
    public boolean supportsVoxelTerrain() {
        return true;
    }

    @Nonnull
    @Override
    public PhysicsBody createVoxelTerrain(float voxelSizeX,
        float voxelSizeY,
        float voxelSizeZ,
        @Nonnull int[] voxelCoordinates) {
        return RapierBody.voxelTerrain(voxelSizeX, voxelSizeY, voxelSizeZ, voxelCoordinates);
    }

    @Override
    public void combineVoxelTerrains(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        int shiftX,
        int shiftY,
        int shiftZ) {
        RapierBody rapierBodyA = requireAttachedBody(bodyA);
        RapierBody rapierBodyB = requireAttachedBody(bodyB);
        RapierNative.combineVoxelTerrainNative(nativeSpaceHandle,
            rapierBodyA.getBodyHandle(),
            rapierBodyB.getBodyHandle(),
            shiftX,
            shiftY,
            shiftZ);
    }

    @Nonnull
    @Override
    public PhysicsBody createSphere(float radius, float mass) {
        return RapierBody.sphere(radius, mass);
    }

    @Nonnull
    @Override
    public PhysicsBody createCapsule(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float mass) {
        return RapierBody.capsule(radius, halfHeight, axis, mass);
    }

    @Nonnull
    @Override
    public PhysicsBody createCylinder(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float mass) {
        return RapierBody.cylinder(radius, halfHeight, axis, mass);
    }

    @Nonnull
    @Override
    public PhysicsBody createCone(float radius, float halfHeight, @Nonnull PhysicsAxis axis, float mass) {
        return RapierBody.cone(radius, halfHeight, axis, mass);
    }

    @Nonnull
    @Override
    public Optional<PhysicsRayHit> raycastClosest(@Nonnull Vector3f from, @Nonnull Vector3f to) {
        List<PhysicsRayHit> hits = raycastAll(from, to);
        PhysicsRayHit closest = null;
        for (PhysicsRayHit hit : hits) {
            if (closest == null || hit.fraction() < closest.fraction()) {
                closest = hit;
            }
        }
        return Optional.ofNullable(closest);
    }

    @Nonnull
    @Override
    public List<PhysicsRayHit> raycastAll(@Nonnull Vector3f from, @Nonnull Vector3f to) {
        float[] raw = RapierNative.raycastAllNative(nativeSpaceHandle,
            from.x, from.y, from.z, to.x, to.y, to.z);
        List<PhysicsRayHit> hits = new ArrayList<>(raw.length / RAY_HIT_FLOATS);
        for (int i = 0; i + RAY_HIT_FLOATS <= raw.length; i += RAY_HIT_FLOATS) {
            RapierBody body = bodiesByHandle.get((long) raw[i]);
            if (body == null) {
                continue;
            }
            Vector3f point = new Vector3f(raw[i + 1], raw[i + 2], raw[i + 3]);
            Vector3f normal = new Vector3f(raw[i + 4], raw[i + 5], raw[i + 6]);
            hits.add(new PhysicsRayHit(body, point, normal, raw[i + 7], raw[i + 8]));
        }
        return hits;
    }

    @Nonnull
    @Override
    public List<PhysicsContact> getContacts() {
        float[] raw = RapierNative.getContactsNative(nativeSpaceHandle);
        List<PhysicsContact> contacts = new ArrayList<>(raw.length / CONTACT_FLOATS);
        for (int i = 0; i + CONTACT_FLOATS <= raw.length; i += CONTACT_FLOATS) {
            RapierBody bodyA = bodiesByHandle.get((long) raw[i]);
            RapierBody bodyB = bodiesByHandle.get((long) raw[i + 1]);
            if (bodyA == null || bodyB == null) {
                continue;
            }
            Vector3f pointA = new Vector3f(raw[i + 2], raw[i + 3], raw[i + 4]);
            Vector3f pointB = new Vector3f(raw[i + 5], raw[i + 6], raw[i + 7]);
            Vector3f normal = new Vector3f(raw[i + 8], raw[i + 9], raw[i + 10]);
            contacts.add(new PhysicsContact(bodyA, bodyB, pointA, pointB, normal,
                raw[i + 11], raw[i + 12]));
        }
        return contacts;
    }

    @Nonnull
    @Override
    public PhysicsJoint createFixedJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB) {
        return createJoint(PhysicsJointType.FIXED, bodyA, bodyB, anchorA, anchorB,
            new Vector3f(0f, 1f, 0f), 0f, 0f, 0f);
    }

    @Nonnull
    @Override
    public PhysicsJoint createPointJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB) {
        return createJoint(PhysicsJointType.POINT, bodyA, bodyB, anchorA, anchorB,
            new Vector3f(0f, 1f, 0f), 0f, 0f, 0f);
    }

    @Nonnull
    @Override
    public PhysicsJoint createHingeJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        return createJoint(PhysicsJointType.HINGE, bodyA, bodyB, anchorA, anchorB,
            axis, 0f, 0f, 0f);
    }

    @Nonnull
    @Override
    public PhysicsJoint createSliderJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        return createJoint(PhysicsJointType.SLIDER, bodyA, bodyB, anchorA, anchorB,
            axis, 0f, 0f, 0f);
    }

    @Nonnull
    @Override
    public PhysicsJoint createSpringJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        float restLength,
        float stiffness,
        float damping) {
        return createJoint(PhysicsJointType.SPRING, bodyA, bodyB, anchorA, anchorB,
            new Vector3f(0f, 1f, 0f), restLength, stiffness, damping);
    }

    @Override
    public void removeJoint(@Nonnull PhysicsJoint joint) {
        if (!(joint instanceof RapierJoint rapierJoint)) {
            return;
        }
        RapierNative.removeJointNative(nativeSpaceHandle, rapierJoint.getJointHandle());
        joints.remove(rapierJoint);
    }

    @Nonnull
    @Override
    public List<PhysicsJoint> getJoints() {
        return new ArrayList<>(joints);
    }

    long getNativeSpaceHandle() {
        return nativeSpaceHandle;
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private long addNativeBody(@Nonnull RapierBody body) {
        if (body.getShapeType() == ShapeType.VOXELS) {
            Vector3f position = body.getStoredPosition();
            Vector3f voxelSize = body.getVoxelSize();
            return RapierNative.addVoxelTerrainNative(nativeSpaceHandle,
                voxelSize.x,
                voxelSize.y,
                voxelSize.z,
                body.getVoxelCoordinates(),
                position.x,
                position.y,
                position.z,
                body.getStoredFriction(),
                body.getStoredRestitution(),
                body.getStoredCollisionGroup(),
                body.getStoredCollisionMask());
        }

        Vector3f halfExtents = body.getBoxHalfExtents();
        if (halfExtents == null) {
            halfExtents = new Vector3f();
        }
        Vector3f position = body.getStoredPosition();
        Quaternionf rotation = body.getStoredRotation();
        Vector3f linearVelocity = body.getStoredLinearVelocity();
        Vector3f angularVelocity = body.getStoredAngularVelocity();

        return RapierNative.addBodyNative(nativeSpaceHandle,
            body.getShapeType().ordinal(),
            halfExtents.x,
            halfExtents.y,
            halfExtents.z,
            body.getSphereRadius(),
            body.getHalfHeight(),
            body.getShapeAxis().index(),
            body.getStoredBodyType().ordinal(),
            body.getStoredMass(),
            position.x,
            position.y,
            position.z,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w,
            linearVelocity.x,
            linearVelocity.y,
            linearVelocity.z,
            angularVelocity.x,
            angularVelocity.y,
            angularVelocity.z,
            body.getStoredFriction(),
            body.getStoredRestitution(),
            body.getStoredLinearDamping(),
            body.getStoredAngularDamping(),
            body.getStoredSensor(),
            body.getStoredCollisionGroup(),
            body.getStoredCollisionMask(),
            body.getStoredContinuousCollisionEnabled());
    }

    private PhysicsJoint createJoint(@Nonnull PhysicsJointType type,
        @Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis,
        float restLength,
        float stiffness,
        float damping) {
        RapierBody rapierA = requireAttachedBody(bodyA);
        RapierBody rapierB = requireAttachedBody(bodyB);
        Vector3f normalizedAxis = normalizedOrDefault(axis);
        long handle = RapierNative.addJointNative(nativeSpaceHandle,
            type.ordinal(),
            rapierA.getBodyHandle(),
            rapierB.getBodyHandle(),
            anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            normalizedAxis.x,
            normalizedAxis.y,
            normalizedAxis.z,
            restLength,
            stiffness,
            damping);
        if (handle == 0L) {
            throw new IllegalStateException("Rapier returned a null native joint handle");
        }
        RapierJoint joint = new RapierJoint(this, type, rapierA, rapierB, handle,
            anchorA, anchorB, normalizedAxis, restLength, stiffness, damping);
        joints.add(joint);
        return joint;
    }

    private RapierBody requireAttachedBody(@Nonnull PhysicsBody body) {
        if (!(body instanceof RapierBody rapierBody)) {
            throw new IllegalArgumentException("Body does not belong to rapier backend");
        }
        if (!rapierBody.isAttached()) {
            throw new IllegalStateException("Rapier joint bodies must be added to a space first");
        }
        return rapierBody;
    }

    private static Vector3f normalizedOrDefault(@Nonnull Vector3f axis) {
        Vector3f normalized = new Vector3f(axis);
        if (normalized.lengthSquared() == 0f) {
            normalized.set(0f, 1f, 0f);
        }
        return normalized.normalize();
    }

    private static final class NativeSpaceCleanup implements Runnable {

        private final long nativeSpaceHandle;

        private NativeSpaceCleanup(long nativeSpaceHandle) {
            this.nativeSpaceHandle = nativeSpaceHandle;
        }

        @Override
        public void run() {
            RapierNative.destroySpaceNative(nativeSpaceHandle);
        }
    }
}
