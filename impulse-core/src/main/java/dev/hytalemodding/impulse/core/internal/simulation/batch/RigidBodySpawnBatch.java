package dev.hytalemodding.impulse.core.internal.simulation.batch;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandContext;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Packed execution encoding for repeated rigid body spawns.
 *
 * <p>This is an internal recorded-command representation. Plugin code should record
 * through {@link PhysicsCommandContext#spawnBodies} instead of depending on this type.</p>
 */
public final class RigidBodySpawnBatch {

    private static final int OBJECT_STRIDE = 6;
    private static final int FLOAT_STRIDE = 4;

    private long[] bodyKeyMostSignificantBits;
    private long[] bodyKeyLeastSignificantBits;
    private Object[] objects;
    private float[] floats;
    private int size;

    public RigidBodySpawnBatch(int expectedBodies) {
        int capacity = Math.max(1, expectedBodies);
        bodyKeyMostSignificantBits = new long[capacity];
        bodyKeyLeastSignificantBits = new long[capacity];
        objects = new Object[capacity * OBJECT_STRIDE];
        floats = new float[capacity * FLOAT_STRIDE];
    }

    private RigidBodySpawnBatch(@Nonnull long[] bodyKeyMostSignificantBits,
        @Nonnull long[] bodyKeyLeastSignificantBits,
        @Nonnull Object[] objects,
        @Nonnull float[] floats,
        int size) {
        this.bodyKeyMostSignificantBits = bodyKeyMostSignificantBits;
        this.bodyKeyLeastSignificantBits = bodyKeyLeastSignificantBits;
        this.objects = objects;
        this.floats = floats;
        this.size = size;
    }

    public void add(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        Objects.requireNonNull(bodyKey, "bodyKey");
        add(bodyKey.mostSignificantBits(),
            bodyKey.leastSignificantBits(),
            spaceId,
            shape,
            mass,
            bodyType,
            positionX,
            positionY,
            positionZ,
            settings,
            kind,
            persistenceMode);
    }

    public void add(long bodyKeyMostSignificantBits,
        long bodyKeyLeastSignificantBits,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        ensureCapacity(size + 1);
        this.bodyKeyMostSignificantBits[size] = bodyKeyMostSignificantBits;
        this.bodyKeyLeastSignificantBits[size] = bodyKeyLeastSignificantBits;
        int objectOffset = size * OBJECT_STRIDE;
        objects[objectOffset] = Objects.requireNonNull(spaceId, "spaceId");
        objects[objectOffset + 1] = Objects.requireNonNull(shape, "shape");
        objects[objectOffset + 2] = Objects.requireNonNull(bodyType, "bodyType");
        objects[objectOffset + 3] = Objects.requireNonNull(settings, "settings");
        objects[objectOffset + 4] = Objects.requireNonNull(kind, "kind");
        objects[objectOffset + 5] = Objects.requireNonNull(persistenceMode, "persistenceMode");

        int floatOffset = size * FLOAT_STRIDE;
        floats[floatOffset] = mass;
        floats[floatOffset + 1] = positionX;
        floats[floatOffset + 2] = positionY;
        floats[floatOffset + 3] = positionZ;
        size++;
    }

    @Nonnull
    public RigidBodySpawnBatch freeze() {
        int objectSize = size * OBJECT_STRIDE;
        int floatSize = size * FLOAT_STRIDE;
        return new RigidBodySpawnBatch(
            bodyKeyMostSignificantBits.length == size
                ? bodyKeyMostSignificantBits
                : Arrays.copyOf(bodyKeyMostSignificantBits, size),
            bodyKeyLeastSignificantBits.length == size
                ? bodyKeyLeastSignificantBits
                : Arrays.copyOf(bodyKeyLeastSignificantBits, size),
            objects.length == objectSize ? objects : Arrays.copyOf(objects, objectSize),
            floats.length == floatSize ? floats : Arrays.copyOf(floats, floatSize),
            size);
    }

    public int size() {
        return size;
    }

    @Nonnull
    public RigidBodyKey bodyKey(int index) {
        checkIndex(index);
        return RigidBodyKey.of(bodyKeyMostSignificantBits[index], bodyKeyLeastSignificantBits[index]);
    }

    @Nonnull
    public long[] bodyKeyMostSignificantBits() {
        return bodyKeyMostSignificantBits;
    }

    @Nonnull
    public long[] bodyKeyLeastSignificantBits() {
        return bodyKeyLeastSignificantBits;
    }

    @Nonnull
    public SpaceId spaceId(int index) {
        return object(index, 0, SpaceId.class);
    }

    @Nonnull
    public PhysicsShapeSpec shape(int index) {
        return object(index, 1, PhysicsShapeSpec.class);
    }

    @Nonnull
    public PhysicsBodyType bodyType(int index) {
        return object(index, 2, PhysicsBodyType.class);
    }

    @Nonnull
    public RigidBodySpawnSettings settings(int index) {
        return object(index, 3, RigidBodySpawnSettings.class);
    }

    @Nonnull
    public PhysicsBodyKind kind(int index) {
        return object(index, 4, PhysicsBodyKind.class);
    }

    @Nonnull
    public PhysicsBodyPersistenceMode persistenceMode(int index) {
        return object(index, 5, PhysicsBodyPersistenceMode.class);
    }

    public float mass(int index) {
        return floatAt(index, 0);
    }

    public float positionX(int index) {
        return floatAt(index, 1);
    }

    public float positionY(int index) {
        return floatAt(index, 2);
    }

    public float positionZ(int index) {
        return floatAt(index, 3);
    }

    private float floatAt(int index,
        int slot) {
        checkIndex(index);
        return floats[index * FLOAT_STRIDE + slot];
    }

    @Nonnull
    private <T> T object(int index,
        int slot,
        @Nonnull Class<T> type) {
        checkIndex(index);
        return type.cast(objects[index * OBJECT_STRIDE + slot]);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private void ensureCapacity(int required) {
        int capacity = objects.length / OBJECT_STRIDE;
        if (required <= capacity) {
            return;
        }
        int nextCapacity = Math.max(required, capacity + (capacity >> 1));
        bodyKeyMostSignificantBits = Arrays.copyOf(bodyKeyMostSignificantBits, nextCapacity);
        bodyKeyLeastSignificantBits = Arrays.copyOf(bodyKeyLeastSignificantBits, nextCapacity);
        objects = Arrays.copyOf(objects, nextCapacity * OBJECT_STRIDE);
        floats = Arrays.copyOf(floats, nextCapacity * FLOAT_STRIDE);
    }
}
