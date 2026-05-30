package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Fluent recorder for one copied rigid body spawn request.
 */
public final class MutableRigidBodySpawnRecorder implements RigidBodySpawnRecorder {

    @Nonnull
    private final RigidBodySpawnSink sink;
    @Nonnull
    private final RigidBodyKey bodyKey;
    private SpaceId spaceId;
    private PhysicsShapeSpec shape;
    private float mass = 1.0f;
    @Nonnull
    private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
    private float positionX;
    private float positionY;
    private float positionZ;
    private RigidBodySpawnSettings settings;
    @Nonnull
    private PhysicsBodyKind kind = PhysicsBodyKind.BODY;
    @Nonnull
    private PhysicsBodyPersistenceMode persistenceMode = PhysicsBodyPersistenceMode.RUNTIME_ONLY;
    private boolean sealed;

    MutableRigidBodySpawnRecorder(@Nonnull RigidBodySpawnSink sink,
        @Nonnull RigidBodyKey bodyKey) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.bodyKey = Objects.requireNonNull(bodyKey, "bodyKey");
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder space(@Nonnull SpaceId spaceId) {
        assertMutable();
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder box(float halfX,
        float halfY,
        float halfZ) {
        assertMutable();
        this.shape = PhysicsShapeSpec.box(halfX, halfY, halfZ);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder shape(@Nonnull PhysicsShapeSpec shape) {
        assertMutable();
        this.shape = Objects.requireNonNull(shape, "shape");
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder sphere(float radius) {
        assertMutable();
        this.shape = PhysicsShapeSpec.sphere(radius);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder capsule(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis) {
        assertMutable();
        this.shape = PhysicsShapeSpec.capsule(radius, halfHeight, axis);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder cylinder(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis) {
        assertMutable();
        this.shape = PhysicsShapeSpec.cylinder(radius, halfHeight, axis);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder cone(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis) {
        assertMutable();
        this.shape = PhysicsShapeSpec.cone(radius, halfHeight, axis);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder plane(float groundY) {
        assertMutable();
        this.shape = PhysicsShapeSpec.plane(groundY);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder mass(float mass) {
        assertMutable();
        this.mass = mass;
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder type(@Nonnull PhysicsBodyType bodyType) {
        assertMutable();
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder dynamic() {
        return type(PhysicsBodyType.DYNAMIC);
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder kinematic() {
        return type(PhysicsBodyType.KINEMATIC);
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder position(@Nonnull Vector3f position) {
        Objects.requireNonNull(position, "position");
        return position(position.x, position.y, position.z);
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder position(float x,
        float y,
        float z) {
        assertMutable();
        this.positionX = x;
        this.positionY = y;
        this.positionZ = z;
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder settings(@Nonnull RigidBodySpawnSettings settings) {
        assertMutable();
        this.settings = Objects.requireNonNull(settings, "settings");
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder sensor(boolean sensor) {
        assertMutable();
        this.settings = settingsOrDefaults().withSensor(sensor);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder collisionFilter(int group,
        int mask) {
        assertMutable();
        this.settings = settingsOrDefaults().withCollisionFilter(group, mask);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder kind(@Nonnull PhysicsBodyKind kind) {
        assertMutable();
        this.kind = Objects.requireNonNull(kind, "kind");
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder temporary() {
        return kind(PhysicsBodyKind.TEMPORARY);
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder persistence(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        assertMutable();
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder runtimeOnly() {
        return persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY);
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder persistent() {
        return persistence(PhysicsBodyPersistenceMode.PERSISTENT);
    }

    void record() {
        assertMutable();
        if (spaceId == null) {
            throw new IllegalStateException("Spawn command requires a physics space");
        }
        if (shape == null) {
            throw new IllegalStateException("Spawn command requires a shape");
        }
        sink.accept(bodyKey,
            spaceId,
            shape,
            mass,
            bodyType,
            positionX,
            positionY,
            positionZ,
            settingsOrDefaults(),
            kind,
            persistenceMode);
    }

    void seal() {
        sealed = true;
    }

    @Nonnull
    private RigidBodySpawnSettings settingsOrDefaults() {
        return settings != null ? settings : RigidBodySpawnSettings.defaults();
    }

    private void assertMutable() {
        if (sealed) {
            throw new IllegalStateException("Rigid body spawn recorder is no longer active");
        }
    }
}
