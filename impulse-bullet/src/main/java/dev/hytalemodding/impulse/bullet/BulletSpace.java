package dev.hytalemodding.impulse.bullet;

import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PersistentManifolds;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.ConeCollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.PlaneCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.joints.HingeJoint;
import com.jme3.bullet.joints.Point2PointJoint;
import com.jme3.bullet.joints.SixDofJoint;
import com.jme3.bullet.joints.SixDofSpringJoint;
import com.jme3.bullet.joints.SliderJoint;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Plane;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

public final class BulletSpace implements PhysicsSpace {

    private final SpaceId id;
    private final BulletBackend backend;
    private final com.jme3.bullet.PhysicsSpace space;
    private final Map<PhysicsRigidBody, BulletBody> bodiesByRigidBody = new IdentityHashMap<>();
    private final Map<Long, BulletBody> bodiesByNativeId = new HashMap<>();
    private final List<BulletBody> bodies = new ArrayList<>();
    private final List<BulletJoint> joints = new ArrayList<>();
    private boolean closed;

    BulletSpace(@Nonnull SpaceId id,
        @Nonnull BulletBackend backend,
        @Nonnull com.jme3.bullet.PhysicsSpace space) {
        this.id = id;
        this.backend = backend;
        this.space = space;
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
        requireOpen();
        space.update(dt, 0);
    }

    @Override
    public void setGravity(float x, float y, float z) {
        requireOpen();
        space.setGravity(toJme(x, y, z));
    }

    @Nonnull
    @Override
    public Vector3f getGravity() {
        requireOpen();
        return fromJme(space.getGravity(new com.jme3.math.Vector3f()));
    }

    @Override
    public void addBody(@Nonnull PhysicsBody body) {
        requireOpen();
        if (!(body instanceof BulletBody bulletBody)) {
            throw new IllegalArgumentException("Body does not belong to bullet backend");
        }
        requireBodyAddable(bulletBody);
        space.addCollisionObject(bulletBody.getRigidBody());
        trackBody(bulletBody);
        bulletBody.markAttachedTo(this);
    }

    @Override
    public void removeBody(@Nonnull PhysicsBody body) {
        requireOpen();
        if (!(body instanceof BulletBody bulletBody)) {
            return;
        }
        if (!ownsBody(bulletBody)) {
            return;
        }

        removeAttachedJoints(bulletBody);
        if (bulletBody.isAttachedToSpace()) {
            space.removeCollisionObject(bulletBody.getRigidBody());
        }
        untrackBody(bulletBody);
    }

    @Nonnull
    @Override
    public List<PhysicsBody> getBodies() {
        requireOpen();
        for (PhysicsRigidBody rigidBody : space.getRigidBodyList()) {
            wrapBody(rigidBody);
        }
        List<PhysicsBody> attachedBodies = new ArrayList<>(space.getRigidBodyList().size());
        for (BulletBody body : bodies) {
            if (body.isAttachedToSpace()) {
                attachedBodies.add(body);
            }
        }
        return attachedBodies;
    }

    @Override
    public int bodyCount() {
        requireOpen();
        return space.getRigidBodyList().size();
    }

    @Override
    public void forEachBody(@Nonnull Consumer<PhysicsBody> consumer) {
        requireOpen();
        for (PhysicsRigidBody rigidBody : space.getRigidBodyList()) {
            consumer.accept(wrapBody(rigidBody));
        }
    }

    @Override
    public void snapshotBodies(@Nonnull Consumer<PhysicsBodySnapshot> consumer) {
        snapshotBodies(_ -> null, consumer);
    }

    @Override
    public void snapshotBodies(@Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull Consumer<PhysicsBodySnapshot> consumer) {
        requireOpen();
        for (BulletBody body : bodies) {
            if (body.isAttachedToSpace()) {
                consumer.accept(PhysicsBodySnapshot.from(body, previousSnapshots.apply(body)));
            }
        }
    }

    @Override
    public boolean supportsContinuousCollision() {
        requireOpen();
        return true;
    }

    @Nonnull
    @Override
    public PhysicsBody createStaticPlane(float groundY) {
        requireOpen();
        Plane plane = new Plane(com.jme3.math.Vector3f.UNIT_Y, groundY);
        CollisionShape shape = new PlaneCollisionShape(plane);
        PhysicsRigidBody body = new PhysicsRigidBody(shape,
            com.jme3.bullet.objects.PhysicsBody.massForStatic);
        return trackBody(new BulletBody(body, groundY));
    }

    @Nonnull
    @Override
    public PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass) {
        requireOpen();
        CollisionShape shape = new BoxCollisionShape(toJme(halfX, halfY, halfZ));
        return trackBody(new BulletBody(new PhysicsRigidBody(shape, mass)));
    }

    @Nonnull
    @Override
    public PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass) {
        return createBox(halfExtents.x, halfExtents.y, halfExtents.z, mass);
    }

    @Nonnull
    @Override
    public PhysicsBody createSphere(float radius, float mass) {
        requireOpen();
        return trackBody(new BulletBody(new PhysicsRigidBody(new SphereCollisionShape(radius), mass)));
    }

    @Nonnull
    @Override
    public PhysicsBody createCapsule(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float mass) {
        requireOpen();
        CollisionShape shape = new CapsuleCollisionShape(radius, halfHeight * 2f, axis.index());
        return trackBody(new BulletBody(new PhysicsRigidBody(shape, mass)));
    }

    @Nonnull
    @Override
    public PhysicsBody createCylinder(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float mass) {
        requireOpen();
        CollisionShape shape = new CylinderCollisionShape(radius, halfHeight * 2f, axis.index());
        return trackBody(new BulletBody(new PhysicsRigidBody(shape, mass)));
    }

    @Nonnull
    @Override
    public PhysicsBody createCone(float radius, float halfHeight, @Nonnull PhysicsAxis axis, float mass) {
        requireOpen();
        CollisionShape shape = new ConeCollisionShape(radius, halfHeight * 2f, axis.index());
        return trackBody(new BulletBody(new PhysicsRigidBody(shape, mass)));
    }

    @Nonnull
    @Override
    public Optional<PhysicsRayHit> raycastClosest(@Nonnull Vector3f from, @Nonnull Vector3f to) {
        requireOpen();
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
        requireOpen();
        List<PhysicsRayTestResult> results = space.rayTest(toJme(from), toJme(to));
        List<PhysicsRayHit> hits = new ArrayList<>(results.size());
        Vector3f delta = new Vector3f(to).sub(from);
        float distance = delta.length();
        for (PhysicsRayTestResult result : results) {
            PhysicsCollisionObject collisionObject = result.getCollisionObject();
            if (!(collisionObject instanceof PhysicsRigidBody rigidBody)) {
                continue;
            }
            BulletBody body = wrapBody(rigidBody);
            float fraction = result.getHitFraction();
            Vector3f point = new Vector3f(from).fma(fraction, delta);
            Vector3f normal = fromJme(result.getHitNormalLocal(new com.jme3.math.Vector3f()));
            hits.add(new PhysicsRayHit(body, point, normal, fraction, distance * fraction));
        }
        return hits;
    }

    @Nonnull
    @Override
    public List<PhysicsContact> getContacts() {
        requireOpen();
        long[] manifolds = space.listManifoldIds();
        List<PhysicsContact> contacts = new ArrayList<>();
        for (long manifold : manifolds) {
            BulletBody bodyA = bodiesByNativeId.get(PersistentManifolds.getBodyAId(manifold));
            BulletBody bodyB = bodiesByNativeId.get(PersistentManifolds.getBodyBId(manifold));
            if (bodyA == null || bodyB == null) {
                continue;
            }
            int pointCount = PersistentManifolds.countPoints(manifold);
            for (int i = 0; i < pointCount; i++) {
                long pointId = PersistentManifolds.getPointId(manifold, i);
                Vector3f pointOnA = fromJmeVector(ManifoldPoints::getPositionWorldOnA, pointId);
                Vector3f pointOnB = fromJmeVector(ManifoldPoints::getPositionWorldOnB, pointId);
                Vector3f normal = fromJmeVector(ManifoldPoints::getNormalWorldOnB, pointId);
                contacts.add(new PhysicsContact(bodyA, bodyB, pointOnA, pointOnB, normal,
                    ManifoldPoints.getDistance1(pointId),
                    ManifoldPoints.getAppliedImpulse(pointId)));
            }
        }
        return contacts;
    }

    @Nonnull
    @Override
    public PhysicsJoint createFixedJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB) {
        requireOpen();
        BulletBody bulletA = requireBody(bodyA);
        BulletBody bulletB = requireBody(bodyB);
        SixDofJoint joint = new SixDofJoint(bulletA.getRigidBody(), bulletB.getRigidBody(),
            toJme(anchorA), toJme(anchorB), true);
        joint.setLinearLowerLimit(toJme(0f, 0f, 0f));
        joint.setLinearUpperLimit(toJme(0f, 0f, 0f));
        joint.setAngularLowerLimit(toJme(0f, 0f, 0f));
        joint.setAngularUpperLimit(toJme(0f, 0f, 0f));
        return addJoint(new BulletJoint(PhysicsJointType.FIXED, bulletA, bulletB, joint,
            anchorA, anchorB, null));
    }

    @Nonnull
    @Override
    public PhysicsJoint createPointJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB) {
        requireOpen();
        BulletBody bulletA = requireBody(bodyA);
        BulletBody bulletB = requireBody(bodyB);
        Point2PointJoint joint = new Point2PointJoint(bulletA.getRigidBody(), bulletB.getRigidBody(),
            toJme(anchorA), toJme(anchorB));
        return addJoint(new BulletJoint(PhysicsJointType.POINT, bulletA, bulletB, joint,
            anchorA, anchorB, null));
    }

    @Nonnull
    @Override
    public PhysicsJoint createHingeJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        requireOpen();
        BulletBody bulletA = requireBody(bodyA);
        BulletBody bulletB = requireBody(bodyB);
        Vector3f normalizedAxis = normalizedOrDefault(axis);
        HingeJoint joint = new HingeJoint(bulletA.getRigidBody(), bulletB.getRigidBody(),
            toJme(anchorA), toJme(anchorB), toJme(normalizedAxis), toJme(normalizedAxis));
        return addJoint(new BulletJoint(PhysicsJointType.HINGE, bulletA, bulletB, joint,
            anchorA, anchorB, normalizedAxis));
    }

    @Nonnull
    @Override
    public PhysicsJoint createSliderJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        requireOpen();
        BulletBody bulletA = requireBody(bodyA);
        BulletBody bulletB = requireBody(bodyB);
        Vector3f normalizedAxis = normalizedOrDefault(axis);
        com.jme3.math.Matrix3f basis = basisForAxis(normalizedAxis);
        SliderJoint joint = new SliderJoint(bulletA.getRigidBody(), bulletB.getRigidBody(),
            toJme(anchorA), toJme(anchorB), basis, basis, true);
        return addJoint(new BulletJoint(PhysicsJointType.SLIDER, bulletA, bulletB, joint,
            anchorA, anchorB, normalizedAxis));
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
        requireOpen();
        BulletBody bulletA = requireBody(bodyA);
        BulletBody bulletB = requireBody(bodyB);
        SixDofSpringJoint joint = new SixDofSpringJoint(bulletA.getRigidBody(), bulletB.getRigidBody(),
            toJme(anchorA), toJme(anchorB), com.jme3.math.Matrix3f.IDENTITY,
            com.jme3.math.Matrix3f.IDENTITY, true);
        joint.enableSpring(0, true);
        joint.setStiffness(0, stiffness);
        joint.setDamping(0, damping);
        joint.setEquilibriumPoint(0);
        BulletJoint wrapped = new BulletJoint(PhysicsJointType.SPRING, bulletA, bulletB, joint,
            anchorA, anchorB, new Vector3f(1f, 0f, 0f), restLength, stiffness, damping);
        wrapped.setLimits(-restLength, restLength);
        return addJoint(wrapped);
    }

    @Override
    public void removeJoint(@Nonnull PhysicsJoint joint) {
        requireOpen();
        if (!(joint instanceof BulletJoint bulletJoint)) {
            return;
        }
        removeJointInternal(bulletJoint);
    }

    @Nonnull
    @Override
    public List<PhysicsJoint> getJoints() {
        requireOpen();
        return new ArrayList<>(joints);
    }

    @Override
    public int jointCount() {
        requireOpen();
        return joints.size();
    }

    @Override
    public void forEachJoint(@Nonnull Consumer<PhysicsJoint> consumer) {
        requireOpen();
        for (BulletJoint joint : joints) {
            consumer.accept(joint);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        try {
            for (BulletJoint joint : new ArrayList<>(joints)) {
                removeJointInternal(joint);
            }

            for (BulletBody body : new ArrayList<>(bodies)) {
                if (body.isAttachedToSpace()) {
                    space.removeCollisionObject(body.getRigidBody());
                }
                body.invalidateFrom(this);
            }
            bodies.clear();
            bodiesByRigidBody.clear();
            bodiesByNativeId.clear();
        } finally {
            space.destroy();
        }
    }

    private BulletJoint addJoint(@Nonnull BulletJoint joint) {
        space.addJoint(joint.getNativeJoint());
        joints.add(joint);
        return joint;
    }

    private BulletBody requireBody(@Nonnull PhysicsBody body) {
        if (!(body instanceof BulletBody bulletBody)) {
            throw new IllegalArgumentException("Body does not belong to bullet backend");
        }
        if (!ownsBody(bulletBody)) {
            throw new IllegalArgumentException("Body does not belong to this bullet space");
        }
        bulletBody.requireNotInvalidated();
        return bulletBody;
    }

    private BulletBody trackBody(@Nonnull BulletBody body) {
        body.bindTo(this);
        if (!bodiesByRigidBody.containsKey(body.getRigidBody())) {
            bodiesByRigidBody.put(body.getRigidBody(), body);
            bodiesByNativeId.put(body.getNativeId(), body);
            bodies.add(body);
        }
        return body;
    }

    private void untrackBody(@Nonnull BulletBody body) {
        bodiesByRigidBody.remove(body.getRigidBody());
        bodiesByNativeId.remove(body.getNativeId());
        bodies.remove(body);
        body.detachFrom(this);
    }

    private void removeAttachedJoints(@Nonnull BulletBody body) {
        for (BulletJoint joint : new ArrayList<>(joints)) {
            if (joint.getBodyA() != body && joint.getBodyB() != body) {
                continue;
            }

            removeJointInternal(joint);
        }
    }

    private BulletBody wrapBody(@Nonnull PhysicsRigidBody rigidBody) {
        BulletBody existing = bodiesByRigidBody.get(rigidBody);
        if (existing != null) {
            return existing;
        }
        BulletBody body = trackBody(new BulletBody(rigidBody));
        body.markAttachedTo(this);
        return body;
    }

    private void removeJointInternal(@Nonnull BulletJoint joint) {
        if (!joints.remove(joint)) {
            return;
        }
        space.removeJoint(joint.getNativeJoint());
    }

    private void requireBodyAddable(@Nonnull BulletBody body) {
        body.requireNotInvalidated();
        BulletSpace owner = body.getOwner();
        if (owner != null && owner != this) {
            throw new IllegalArgumentException("Body belongs to another bullet space");
        }
        if (body.isAttachedToSpace()) {
            throw new IllegalStateException("Body is already attached to a bullet space");
        }
    }

    private boolean ownsBody(@Nonnull BulletBody body) {
        return body.isOwnedBy(this) && bodiesByRigidBody.get(body.getRigidBody()) == body;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Bullet space is closed: " + id);
        }
    }

    private static Vector3f normalizedOrDefault(@Nonnull Vector3f axis) {
        Vector3f normalized = new Vector3f(axis);
        if (normalized.lengthSquared() == 0f) {
            normalized.set(0f, 1f, 0f);
        }
        return normalized.normalize();
    }

    private static com.jme3.math.Matrix3f basisForAxis(@Nonnull Vector3f axis) {
        Vector3f x = normalizedOrDefault(axis);
        Vector3f up = Math.abs(x.y) < 0.9f
            ? new Vector3f(0f, 1f, 0f)
            : new Vector3f(1f, 0f, 0f);
        Vector3f z = new Vector3f(x).cross(up).normalize();
        Vector3f y = new Vector3f(z).cross(x).normalize();
        com.jme3.math.Matrix3f basis = new com.jme3.math.Matrix3f();
        basis.fromAxes(toJme(x), toJme(y), toJme(z));
        return basis;
    }

    private static com.jme3.math.Vector3f toJme(@Nonnull Vector3f vector) {
        return toJme(vector.x, vector.y, vector.z);
    }

    private static com.jme3.math.Vector3f toJme(float x, float y, float z) {
        return new com.jme3.math.Vector3f(x, y, z);
    }

    private static Vector3f fromJme(@Nonnull com.jme3.math.Vector3f vector) {
        return new Vector3f(vector.x, vector.y, vector.z);
    }

    private static Vector3f fromJmeVector(@Nonnull ManifoldVectorReader reader, long pointId) {
        com.jme3.math.Vector3f out = new com.jme3.math.Vector3f();
        reader.read(pointId, out);
        return fromJme(out);
    }

    @FunctionalInterface
    private interface ManifoldVectorReader {
        void read(long pointId, com.jme3.math.Vector3f out);
    }
}
