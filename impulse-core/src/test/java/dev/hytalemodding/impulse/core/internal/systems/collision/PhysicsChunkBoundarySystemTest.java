package dev.hytalemodding.impulse.core.internal.systems.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkBoundaryRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkBoundaryRuntime.ChunkBoundarySafeState;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsChunkBoundarySystemTest {

    @Test
    void recordSafePoseUsesSnapshotPoseWithoutReadingBody() {
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        RigidBodyKey bodyId = RigidBodyKey.random();
        CountingBody body = new CountingBody();
        Quaternionf rotation = new Quaternionf().rotateY(0.5f);
        PhysicsBodySnapshot snapshot = snapshot(body,
            new Vector3f(3.0f, 4.0f, 5.0f),
            rotation,
            new Vector3f(),
            new Vector3f(),
            PhysicsBodyType.DYNAMIC);

        PhysicsChunkBoundarySystem.recordSafePose(bodyId, snapshot, resource);

        ChunkBoundarySafeState safeState =
            resource.getChunkBoundarySafeState(bodyId);
        assertNotNull(safeState);
        assertEquals(3.0f, safeState.getPosition().x);
        assertEquals(4.0f, safeState.getPosition().y);
        assertEquals(5.0f, safeState.getPosition().z);
        assertEquals(rotation.x, safeState.getRotation().x);
        assertEquals(rotation.y, safeState.getRotation().y);
        assertEquals(rotation.z, safeState.getRotation().z);
        assertEquals(rotation.w, safeState.getRotation().w);
        assertEquals(0, body.liveGetterCalls());
    }

    @Test
    void pauseBodyUsesSnapshotVelocityAndTypeWithoutReadingBody() {
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        RigidBodyKey bodyId = RigidBodyKey.random();
        CountingBody body = new CountingBody();
        Quaternionf safeRotation = new Quaternionf().rotateX(0.25f);
        resource.updateChunkBoundarySafeState(bodyId,
            new Vector3f(8.0f, 9.0f, 10.0f),
            safeRotation);
        Vector3f linearVelocity = new Vector3f(1.0f, 2.0f, 3.0f);
        Vector3f angularVelocity = new Vector3f(4.0f, 5.0f, 6.0f);
        PhysicsBodySnapshot snapshot = snapshot(body,
            new Vector3f(24.0f, 0.0f, 24.0f),
            new Quaternionf(),
            linearVelocity,
            angularVelocity,
            PhysicsBodyType.DYNAMIC);

        resource.pauseChunkBoundaryBody(bodyId, 42L, snapshot);

        PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState pauseState =
            resource.getChunkBoundaryPauseState(bodyId);
        assertNotNull(pauseState);
        assertEquals(42L, pauseState.getTargetChunkIndex());
        assertEquals(PhysicsBodyType.DYNAMIC, pauseState.getOriginalBodyType());
        assertEquals(linearVelocity, pauseState.getLinearVelocity());
        assertEquals(angularVelocity, pauseState.getAngularVelocity());
        assertEquals(0, body.liveGetterCalls());
    }

    @Nonnull
    private static PhysicsBodySnapshot snapshot(@Nonnull PhysicsBody body,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity,
        @Nonnull PhysicsBodyType bodyType) {
        return new PhysicsBodySnapshot(position,
            rotation,
            linearVelocity,
            angularVelocity,
            bodyType,
            false,
            false,
            0.0f,
            ShapeType.BOX,
            new Vector3f(0.5f),
            -1.0f,
            -1.0f,
            PhysicsAxis.Y);
    }

    private static final class CountingBody implements PhysicsBody {

        private final Vector3f position = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f linearVelocity = new Vector3f();
        private final Vector3f angularVelocity = new Vector3f();
        private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
        private boolean forcesCleared;
        private int liveGetterCalls;

        private int liveGetterCalls() {
            return liveGetterCalls;
        }

        @Override
        public void setPosition(float x, float y, float z) {
            position.set(x, y, z);
        }

        @Override
        public void setPosition(@Nonnull Vector3f pos) {
            position.set(pos);
        }

        @Nonnull
        @Override
        public Vector3f getPosition() {
            liveGetterCalls++;
            return new Vector3f(position);
        }

        @Override
        public void setRotation(float x, float y, float z, float w) {
            rotation.set(x, y, z, w);
        }

        @Override
        public void setRotation(@Nonnull Quaternionf rot) {
            rotation.set(rot);
        }

        @Nonnull
        @Override
        public Quaternionf getRotation() {
            liveGetterCalls++;
            return new Quaternionf(rotation);
        }

        @Override
        public void setRestitution(float restitution) {
        }

        @Override
        public float getRestitution() {
            return 0.0f;
        }

        @Override
        public void setFriction(float friction) {
        }

        @Override
        public float getFriction() {
            return 0.0f;
        }

        @Nonnull
        @Override
        public PhysicsBodyType getBodyType() {
            liveGetterCalls++;
            return bodyType;
        }

        @Override
        public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
            this.bodyType = bodyType;
        }

        @Override
        public boolean isStatic() {
            liveGetterCalls++;
            return bodyType == PhysicsBodyType.STATIC;
        }

        @Override
        public boolean isKinematic() {
            liveGetterCalls++;
            return bodyType == PhysicsBodyType.KINEMATIC;
        }

        @Override
        public void setKinematic(boolean kinematic) {
            bodyType = kinematic ? PhysicsBodyType.KINEMATIC : PhysicsBodyType.DYNAMIC;
        }

        @Override
        public void activate() {
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public boolean isSleeping() {
            liveGetterCalls++;
            return false;
        }

        @Override
        public void sleep() {
        }

        @Override
        public float getMass() {
            return 1.0f;
        }

        @Override
        public void setMass(float mass) {
        }

        @Nonnull
        @Override
        public Vector3f getLinearVelocity() {
            liveGetterCalls++;
            return new Vector3f(linearVelocity);
        }

        @Override
        public void setLinearVelocity(@Nonnull Vector3f vel) {
            linearVelocity.set(vel);
        }

        @Override
        public void setLinearVelocity(float x, float y, float z) {
            linearVelocity.set(x, y, z);
        }

        @Nonnull
        @Override
        public Vector3f getAngularVelocity() {
            liveGetterCalls++;
            return new Vector3f(angularVelocity);
        }

        @Override
        public void setAngularVelocity(@Nonnull Vector3f vel) {
            angularVelocity.set(vel);
        }

        @Override
        public void setAngularVelocity(float x, float y, float z) {
            angularVelocity.set(x, y, z);
        }

        @Override
        public float getLinearDamping() {
            return 0.0f;
        }

        @Override
        public void setLinearDamping(float damping) {
        }

        @Override
        public float getAngularDamping() {
            return 0.0f;
        }

        @Override
        public void setAngularDamping(float damping) {
        }

        @Override
        public void applyCentralForce(@Nonnull Vector3f force) {
        }

        @Override
        public void applyCentralForce(float x, float y, float z) {
        }

        @Override
        public void applyForce(@Nonnull Vector3f force, @Nonnull Vector3f offset) {
        }

        @Override
        public void applyCentralImpulse(@Nonnull Vector3f impulse) {
        }

        @Override
        public void applyCentralImpulse(float x, float y, float z) {
        }

        @Override
        public void applyImpulse(@Nonnull Vector3f impulse, @Nonnull Vector3f offset) {
        }

        @Override
        public void applyTorque(@Nonnull Vector3f torque) {
        }

        @Override
        public void applyTorqueImpulse(@Nonnull Vector3f torqueImpulse) {
        }

        @Override
        public void clearForces() {
            forcesCleared = true;
        }

        @Override
        public boolean isSensor() {
            return false;
        }

        @Override
        public void setSensor(boolean sensor) {
        }

        @Override
        public int getCollisionGroup() {
            return 0;
        }

        @Override
        public int getCollisionMask() {
            return 0;
        }

        @Override
        public void setCollisionFilter(int group, int mask) {
        }

        @Override
        public boolean isContinuousCollisionEnabled() {
            return false;
        }

        @Override
        public void setContinuousCollisionEnabled(boolean enabled) {
        }

        @Nonnull
        @Override
        public ShapeType getShapeType() {
            return ShapeType.BOX;
        }

        @Nullable
        @Override
        public Vector3f getBoxHalfExtents() {
            return null;
        }

        @Override
        public float getSphereRadius() {
            return 0.0f;
        }

        @Override
        public float getHalfHeight() {
            return 0.0f;
        }

        @Nonnull
        @Override
        public PhysicsAxis getShapeAxis() {
            return PhysicsAxis.Y;
        }

        @Override
        public float getCenterOfMassOffsetY() {
            return 0.0f;
        }
    }
}
