package dev.hytalemodding.impulse.core.internal.simulation.batch;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Packed execution encoding for repeated rigid body spawns that share spawn properties.
 */
public final class RigidBodySpawnTemplateBatch {

    private static final int POSITION_STRIDE = 3;

    @Nonnull
    private final SpaceId spaceId;
    @Nonnull
    private final PhysicsShapeSpec shape;
    private final float mass;
    @Nonnull
    private final PhysicsBodyType bodyType;
    @Nonnull
    private final RigidBodySpawnSettings settings;
    @Nonnull
    private final PhysicsBodyKind kind;
    @Nonnull
    private final PhysicsBodyPersistenceMode persistenceMode;
    private long[] bodyKeyMostSignificantBits;
    private long[] bodyKeyLeastSignificantBits;
    private float[] positions;
    private int size;

    public RigidBodySpawnTemplateBatch(int expectedBodies,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        int capacity = Math.max(1, expectedBodies);
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.shape = Objects.requireNonNull(shape, "shape");
        this.mass = mass;
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
        bodyKeyMostSignificantBits = new long[capacity];
        bodyKeyLeastSignificantBits = new long[capacity];
        positions = new float[capacity * POSITION_STRIDE];
    }

    private RigidBodySpawnTemplateBatch(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull long[] bodyKeyMostSignificantBits,
        @Nonnull long[] bodyKeyLeastSignificantBits,
        @Nonnull float[] positions,
        int size) {
        this.spaceId = spaceId;
        this.shape = shape;
        this.mass = mass;
        this.bodyType = bodyType;
        this.settings = settings;
        this.kind = kind;
        this.persistenceMode = persistenceMode;
        this.bodyKeyMostSignificantBits = bodyKeyMostSignificantBits;
        this.bodyKeyLeastSignificantBits = bodyKeyLeastSignificantBits;
        this.positions = positions;
        this.size = size;
    }

    public void add(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ) {
        Objects.requireNonNull(bodyKey, "bodyKey");
        add(bodyKey.mostSignificantBits(),
            bodyKey.leastSignificantBits(),
            positionX,
            positionY,
            positionZ);
    }

    public void add(long bodyKeyMostSignificantBits,
        long bodyKeyLeastSignificantBits,
        float positionX,
        float positionY,
        float positionZ) {
        ensureCapacity(size + 1);
        this.bodyKeyMostSignificantBits[size] = bodyKeyMostSignificantBits;
        this.bodyKeyLeastSignificantBits[size] = bodyKeyLeastSignificantBits;
        int positionOffset = size * POSITION_STRIDE;
        positions[positionOffset] = positionX;
        positions[positionOffset + 1] = positionY;
        positions[positionOffset + 2] = positionZ;
        size++;
    }

    @Nonnull
    public RigidBodySpawnTemplateBatch freeze() {
        int positionSize = size * POSITION_STRIDE;
        return new RigidBodySpawnTemplateBatch(spaceId,
            shape,
            mass,
            bodyType,
            settings,
            kind,
            persistenceMode,
            this.bodyKeyMostSignificantBits.length == size
                ? this.bodyKeyMostSignificantBits
                : Arrays.copyOf(this.bodyKeyMostSignificantBits, size),
            this.bodyKeyLeastSignificantBits.length == size
                ? this.bodyKeyLeastSignificantBits
                : Arrays.copyOf(this.bodyKeyLeastSignificantBits, size),
            positions.length == positionSize ? positions : Arrays.copyOf(positions, positionSize),
            size);
    }

    public int size() {
        return size;
    }

    @Nonnull
    public SpaceId spaceId() {
        return spaceId;
    }

    @Nonnull
    public PhysicsShapeSpec shape() {
        return shape;
    }

    public float mass() {
        return mass;
    }

    @Nonnull
    public PhysicsBodyType bodyType() {
        return bodyType;
    }

    @Nonnull
    public RigidBodySpawnSettings settings() {
        return settings;
    }

    @Nonnull
    public PhysicsBodyKind kind() {
        return kind;
    }

    @Nonnull
    public PhysicsBodyPersistenceMode persistenceMode() {
        return persistenceMode;
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
    public float[] positions() {
        return positions;
    }

    public float positionX(int index) {
        return positionAt(index, 0);
    }

    public float positionY(int index) {
        return positionAt(index, 1);
    }

    public float positionZ(int index) {
        return positionAt(index, 2);
    }

    private float positionAt(int index,
        int slot) {
        checkIndex(index);
        return positions[index * POSITION_STRIDE + slot];
    }

    private void ensureCapacity(int required) {
        if (required <= bodyKeyMostSignificantBits.length) {
            return;
        }
        int nextCapacity = Math.max(required,
            bodyKeyMostSignificantBits.length + (bodyKeyMostSignificantBits.length >> 1));
        bodyKeyMostSignificantBits = Arrays.copyOf(bodyKeyMostSignificantBits, nextCapacity);
        bodyKeyLeastSignificantBits = Arrays.copyOf(bodyKeyLeastSignificantBits, nextCapacity);
        positions = Arrays.copyOf(positions, nextCapacity * POSITION_STRIDE);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }
}
