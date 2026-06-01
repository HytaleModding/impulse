package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandContext;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Packed execution encoding for a frozen physics command context.
 *
 * <p>This is not the plugin authoring API. Plugins should compose commands through
 * {@link PhysicsCommandContext} recipes and recorders.</p>
 *
 * <p>The representation keeps opcode, object, and float storage flat so bulk command recording
 * does not allocate a wrapper object for every operation. Preserve that property on hot paths such
 * as template spawns and batch ray setup.</p>
 */
final class PhysicsCommandOperations {

    public static final byte SPAWN_RIGID_BODY = 1;
    public static final byte SPAWN_RIGID_BODY_BATCH = 2;
    public static final byte DESTROY_RIGID_BODY = 3;
    public static final byte SET_RIGID_BODY_TRANSFORM = 4;
    public static final byte SET_RIGID_BODY_VELOCITY = 5;
    public static final byte SET_RIGID_BODY_TYPE = 6;
    public static final byte ACTIVATE_RIGID_BODY = 7;
    public static final byte APPLY_RIGID_BODY_IMPULSE = 8;
    public static final byte APPLY_RIGID_BODY_FORCE = 9;
    public static final byte CREATE_JOINT = 10;
    public static final byte DESTROY_JOINT = 11;
    public static final byte DESTROY_JOINT_BETWEEN_BODIES = 12;
    public static final byte SPAWN_RIGID_BODY_TEMPLATE_BATCH = 14;
    public static final byte SET_RIGID_BODY_POSITION = 15;
    public static final byte SET_SPACE_GRAVITY = 16;

    public static final int FLAG_ACTIVATE = 1;
    public static final int FLAG_OFFSET = 1 << 1;
    public static final int FLAG_TORQUE = 1 << 2;
    public static final int FLAG_MOTOR_ENABLED = 1 << 3;

    private byte[] opcodes;
    private int[] objectOffsets;
    private int[] floatOffsets;
    private Object[] objects;
    private float[] floats;
    private int[] flags;
    private int size;
    private int objectSize;
    private int floatSize;

    PhysicsCommandOperations(int expectedOps) {
        int capacity = Math.max(1, expectedOps);
        opcodes = new byte[capacity];
        objectOffsets = new int[capacity];
        floatOffsets = new int[capacity];
        objects = new Object[capacity * 2];
        floats = new float[capacity * 4];
        flags = new int[capacity];
    }

    private PhysicsCommandOperations(@Nonnull byte[] opcodes,
        @Nonnull int[] objectOffsets,
        @Nonnull int[] floatOffsets,
        @Nonnull Object[] objects,
        @Nonnull float[] floats,
        @Nonnull int[] flags,
        int size,
        int objectSize,
        int floatSize) {
        this.opcodes = opcodes;
        this.objectOffsets = objectOffsets;
        this.floatOffsets = floatOffsets;
        this.objects = objects;
        this.floats = floats;
        this.flags = flags;
        this.size = size;
        this.objectSize = objectSize;
        this.floatSize = floatSize;
    }

    @Nonnull
    PhysicsCommandOperations freeze() {
        return new PhysicsCommandOperations(
            freeze(opcodes, size),
            freeze(objectOffsets, size),
            freeze(floatOffsets, size),
            freeze(objects, objectSize),
            freeze(floats, floatSize),
            freeze(flags, size),
            size,
            objectSize,
            floatSize);
    }

    void addSpawn(@Nonnull RigidBodyKey bodyKey,
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
        int index = add(SPAWN_RIGID_BODY, 0, 7, 4);
        object(index, 0, bodyKey);
        object(index, 1, spaceId);
        object(index, 2, shape);
        object(index, 3, bodyType);
        object(index, 4, settings);
        object(index, 5, kind);
        object(index, 6, persistenceMode);
        floatAt(index, 0, mass);
        floatAt(index, 1, positionX);
        floatAt(index, 2, positionY);
        floatAt(index, 3, positionZ);
    }

    void addSpawnBatch(@Nonnull RigidBodySpawnBatch batch) {
        if (batch.size() <= 0) {
            return;
        }
        int index = add(SPAWN_RIGID_BODY_BATCH, 0, 1, 0);
        object(index, 0, batch.freeze());
    }

    void addSpawnTemplateBatch(@Nonnull RigidBodySpawnTemplateBatch batch) {
        if (batch.size() <= 0) {
            return;
        }
        int index = add(SPAWN_RIGID_BODY_TEMPLATE_BATCH, 0, 1, 0);
        object(index, 0, batch.freeze());
    }

    void addDestroyBody(@Nonnull RigidBodyKey bodyKey) {
        int index = add(DESTROY_RIGID_BODY, 0, 1, 0);
        object(index, 0, bodyKey);
    }

    void addSetSpaceGravity(@Nonnull SpaceId spaceId,
        float x,
        float y,
        float z) {
        int index = add(SET_SPACE_GRAVITY, 0, 1, 3);
        object(index, 0, spaceId);
        floatAt(index, 0, x);
        floatAt(index, 1, y);
        floatAt(index, 2, z);
    }

    void addSetTransform(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        boolean activate) {
        int index = add(SET_RIGID_BODY_TRANSFORM, activate ? FLAG_ACTIVATE : 0, 1, 7);
        object(index, 0, bodyKey);
        floatAt(index, 0, positionX);
        floatAt(index, 1, positionY);
        floatAt(index, 2, positionZ);
        floatAt(index, 3, rotationX);
        floatAt(index, 4, rotationY);
        floatAt(index, 5, rotationZ);
        floatAt(index, 6, rotationW);
    }

    void addSetPosition(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        boolean activate) {
        int index = add(SET_RIGID_BODY_POSITION, activate ? FLAG_ACTIVATE : 0, 1, 3);
        object(index, 0, bodyKey);
        floatAt(index, 0, positionX);
        floatAt(index, 1, positionY);
        floatAt(index, 2, positionZ);
    }

    void addSetVelocity(@Nonnull RigidBodyKey bodyKey,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ,
        boolean activate) {
        int index = add(SET_RIGID_BODY_VELOCITY, activate ? FLAG_ACTIVATE : 0, 1, 6);
        object(index, 0, bodyKey);
        floatAt(index, 0, linearX);
        floatAt(index, 1, linearY);
        floatAt(index, 2, linearZ);
        floatAt(index, 3, angularX);
        floatAt(index, 4, angularY);
        floatAt(index, 5, angularZ);
    }

    void addSetType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        int index = add(SET_RIGID_BODY_TYPE, activate ? FLAG_ACTIVATE : 0, 2, 0);
        object(index, 0, bodyKey);
        object(index, 1, bodyType);
    }

    void addActivate(@Nonnull RigidBodyKey bodyKey) {
        int index = add(ACTIVATE_RIGID_BODY, 0, 1, 0);
        object(index, 0, bodyKey);
    }

    void addImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        int operationFlags = torque ? FLAG_TORQUE : 0;
        if (hasOffset) {
            operationFlags |= FLAG_OFFSET;
        }
        int index = add(APPLY_RIGID_BODY_IMPULSE,
            operationFlags,
            1,
            (operationFlags & FLAG_OFFSET) != 0 ? 6 : 3);
        object(index, 0, bodyKey);
        floatAt(index, 0, x);
        floatAt(index, 1, y);
        floatAt(index, 2, z);
        if ((operationFlags & FLAG_OFFSET) != 0) {
            floatAt(index, 3, offsetX);
            floatAt(index, 4, offsetY);
            floatAt(index, 5, offsetZ);
        }
    }

    void addForce(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        int operationFlags = torque ? FLAG_TORQUE : 0;
        if (hasOffset) {
            operationFlags |= FLAG_OFFSET;
        }
        int index = add(APPLY_RIGID_BODY_FORCE,
            operationFlags,
            1,
            (operationFlags & FLAG_OFFSET) != 0 ? 6 : 3);
        object(index, 0, bodyKey);
        floatAt(index, 0, x);
        floatAt(index, 1, y);
        floatAt(index, 2, z);
        if ((operationFlags & FLAG_OFFSET) != 0) {
            floatAt(index, 3, offsetX);
            floatAt(index, 4, offsetY);
            floatAt(index, 5, offsetZ);
        }
    }

    void addJoint(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB,
        @Nonnull JointType type,
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
        float damping,
        float lowerLimit,
        float upperLimit,
        boolean motorEnabled,
        float motorTargetVelocity,
        float motorMaxForce) {
        int index = add(CREATE_JOINT, motorEnabled ? FLAG_MOTOR_ENABLED : 0, 5, 16);
        object(index, 0, jointKey);
        object(index, 1, spaceId);
        object(index, 2, bodyA);
        object(index, 3, bodyB);
        object(index, 4, type);
        floatAt(index, 0, anchorAX);
        floatAt(index, 1, anchorAY);
        floatAt(index, 2, anchorAZ);
        floatAt(index, 3, anchorBX);
        floatAt(index, 4, anchorBY);
        floatAt(index, 5, anchorBZ);
        floatAt(index, 6, axisX);
        floatAt(index, 7, axisY);
        floatAt(index, 8, axisZ);
        floatAt(index, 9, restLength);
        floatAt(index, 10, stiffness);
        floatAt(index, 11, damping);
        floatAt(index, 12, lowerLimit);
        floatAt(index, 13, upperLimit);
        floatAt(index, 14, motorTargetVelocity);
        floatAt(index, 15, motorMaxForce);
    }

    void addDestroyJoint(@Nonnull JointKey jointKey) {
        int index = add(DESTROY_JOINT, 0, 1, 0);
        object(index, 0, jointKey);
    }

    void addDestroyJointBetween(@Nullable JointKey preferredJointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        int index = add(DESTROY_JOINT_BETWEEN_BODIES, 0, 4, 0);
        object(index, 0, preferredJointKey);
        object(index, 1, spaceId);
        object(index, 2, bodyA);
        object(index, 3, bodyB);
    }

    public int size() {
        return size;
    }

    @Nonnull
    RecordedBodyCreationKeys bodyCreationKeys() {
        RecordedBodyCreationKeys.Builder keys = RecordedBodyCreationKeys.builder();
        for (int index = 0; index < size; index++) {
            switch (opcode(index)) {
                case SPAWN_RIGID_BODY -> keys.add(
                    Objects.requireNonNull(objectAt(index, 0, RigidBodyKey.class)));
                case SPAWN_RIGID_BODY_BATCH -> {
                    RigidBodySpawnBatch batch = objectAt(index, 0, RigidBodySpawnBatch.class);
                    assert batch != null;
                    keys.addAll(batch.bodyKeyMostSignificantBits(),
                        batch.bodyKeyLeastSignificantBits(),
                        batch.size());
                }
                case SPAWN_RIGID_BODY_TEMPLATE_BATCH -> {
                    RigidBodySpawnTemplateBatch batch =
                        objectAt(index, 0, RigidBodySpawnTemplateBatch.class);
                    assert batch != null;
                    keys.addAll(batch.bodyKeyMostSignificantBits(),
                        batch.bodyKeyLeastSignificantBits(),
                        batch.size());
                }
                default -> {
                }
            }
        }
        return keys.build();
    }

    @Nonnull
    EntityReferences entityReferences() {
        EntityReferenceAccumulator accumulator = new EntityReferenceAccumulator();
        for (int index = 0; index < size; index++) {
            switch (opcode(index)) {
                case SPAWN_RIGID_BODY,
                     DESTROY_RIGID_BODY,
                     SET_RIGID_BODY_TRANSFORM,
                     SET_RIGID_BODY_POSITION,
                     SET_RIGID_BODY_VELOCITY,
                     SET_RIGID_BODY_TYPE,
                     ACTIVATE_RIGID_BODY,
                     APPLY_RIGID_BODY_IMPULSE,
                     APPLY_RIGID_BODY_FORCE -> accumulator.addBody(
                    Objects.requireNonNull(objectAt(index, 0, RigidBodyKey.class)));
                case SPAWN_RIGID_BODY_BATCH ->
                    accumulator.addSpawnBatch(
                        Objects.requireNonNull(objectAt(index, 0, RigidBodySpawnBatch.class)));
                case SPAWN_RIGID_BODY_TEMPLATE_BATCH ->
                    accumulator.addSpawnTemplateBatch(Objects.requireNonNull(
                        objectAt(index, 0, RigidBodySpawnTemplateBatch.class)));
                case CREATE_JOINT -> {
                    accumulator.addJoint(Objects.requireNonNull(objectAt(index, 0, JointKey.class)));
                    accumulator.addBody(
                        Objects.requireNonNull(objectAt(index, 2, RigidBodyKey.class)));
                    accumulator.addBody(
                        Objects.requireNonNull(objectAt(index, 3, RigidBodyKey.class)));
                }
                case DESTROY_JOINT -> accumulator.addJoint(
                    Objects.requireNonNull(objectAt(index, 0, JointKey.class)));
                case DESTROY_JOINT_BETWEEN_BODIES -> {
                    JointKey jointKey = (JointKey) objectAt(index, 0);
                    if (jointKey != null) {
                        accumulator.addJoint(jointKey);
                    }
                    accumulator.addBody(
                        Objects.requireNonNull(objectAt(index, 2, RigidBodyKey.class)));
                    accumulator.addBody(
                        Objects.requireNonNull(objectAt(index, 3, RigidBodyKey.class)));
                }
                default -> {
                }
            }
        }
        return accumulator.references();
    }

    public byte opcode(int index) {
        checkIndex(index);
        return opcodes[index];
    }

    public int flags(int index) {
        checkIndex(index);
        return flags[index];
    }

    public float floatAt(int index,
        int slot) {
        checkIndex(index);
        return floats[floatOffsets[index] + slot];
    }

    @Nullable
    public Object objectAt(int index,
        int slot) {
        checkIndex(index);
        return objects[objectOffsets[index] + slot];
    }

    @Nullable
    public <T> T objectAt(int index,
        int slot,
        @Nonnull Class<T> type) {
        return type.cast(objectAt(index, slot));
    }

    private int add(byte opcode,
        int operationFlags,
        int objectSlots,
        int floatSlots) {
        ensureOperationCapacity(size + 1);
        ensureObjectCapacity(objectSize + objectSlots);
        ensureFloatCapacity(floatSize + floatSlots);
        int index = size++;
        opcodes[index] = opcode;
        flags[index] = operationFlags;
        objectOffsets[index] = objectSize;
        floatOffsets[index] = floatSize;
        objectSize += objectSlots;
        floatSize += floatSlots;
        return index;
    }

    private void object(int index,
        int slot,
        @Nullable Object value) {
        objects[objectOffsets[index] + slot] = value;
    }

    private void floatAt(int index,
        int slot,
        float value) {
        floats[floatOffsets[index] + slot] = value;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private void ensureOperationCapacity(int required) {
        if (required <= opcodes.length) {
            return;
        }
        int nextCapacity = Math.max(required, opcodes.length + (opcodes.length >> 1));
        opcodes = Arrays.copyOf(opcodes, nextCapacity);
        objectOffsets = Arrays.copyOf(objectOffsets, nextCapacity);
        floatOffsets = Arrays.copyOf(floatOffsets, nextCapacity);
        flags = Arrays.copyOf(flags, nextCapacity);
    }

    private void ensureObjectCapacity(int required) {
        if (required <= objects.length) {
            return;
        }
        int nextCapacity = Math.max(required, objects.length + (objects.length >> 1));
        objects = Arrays.copyOf(objects, nextCapacity);
    }

    private void ensureFloatCapacity(int required) {
        if (required <= floats.length) {
            return;
        }
        int nextCapacity = Math.max(required, floats.length + (floats.length >> 1));
        floats = Arrays.copyOf(floats, nextCapacity);
    }

    @Nonnull
    private static byte[] freeze(@Nonnull byte[] values,
        int size) {
        return values.length == size ? values : Arrays.copyOf(values, size);
    }

    @Nonnull
    private static int[] freeze(@Nonnull int[] values,
        int size) {
        return values.length == size ? values : Arrays.copyOf(values, size);
    }

    @Nonnull
    private static Object[] freeze(@Nonnull Object[] values,
        int size) {
        return values.length == size ? values : Arrays.copyOf(values, size);
    }

    @Nonnull
    private static float[] freeze(@Nonnull float[] values,
        int size) {
        return values.length == size ? values : Arrays.copyOf(values, size);
    }

    static final class EntityReferences {

        private final int bodyKeyReferenceCount;
        @Nullable
        private final RigidBodyKey firstBodyKey;
        private final int jointKeyReferenceCount;
        @Nullable
        private final JointKey firstJointKey;

        private EntityReferences(int bodyKeyReferenceCount,
            @Nullable RigidBodyKey firstBodyKey,
            int jointKeyReferenceCount,
            @Nullable JointKey firstJointKey) {
            this.bodyKeyReferenceCount = Math.max(0, bodyKeyReferenceCount);
            this.firstBodyKey = this.bodyKeyReferenceCount > 0 ? firstBodyKey : null;
            this.jointKeyReferenceCount = Math.max(0, jointKeyReferenceCount);
            this.firstJointKey = this.jointKeyReferenceCount > 0 ? firstJointKey : null;
        }

        int bodyKeyReferenceCount() {
            return bodyKeyReferenceCount;
        }

        @Nullable
        RigidBodyKey firstBodyKey() {
            return firstBodyKey;
        }

        int jointKeyReferenceCount() {
            return jointKeyReferenceCount;
        }

        @Nullable
        JointKey firstJointKey() {
            return firstJointKey;
        }
    }

    private static final class EntityReferenceAccumulator {

        private int bodyKeyReferenceCount;
        @Nullable
        private RigidBodyKey firstBodyKey;
        private int jointKeyReferenceCount;
        @Nullable
        private JointKey firstJointKey;

        void addBody(@Nonnull RigidBodyKey bodyKey) {
            if (firstBodyKey == null) {
                firstBodyKey = bodyKey;
            }
            bodyKeyReferenceCount++;
        }

        void addSpawnBatch(@Nonnull RigidBodySpawnBatch batch) {
            int batchSize = batch.size();
            if (batchSize <= 0) {
                return;
            }
            if (firstBodyKey == null) {
                firstBodyKey = batch.bodyKey(0);
            }
            bodyKeyReferenceCount += batchSize;
        }

        void addSpawnTemplateBatch(@Nonnull RigidBodySpawnTemplateBatch batch) {
            int batchSize = batch.size();
            if (batchSize <= 0) {
                return;
            }
            if (firstBodyKey == null) {
                firstBodyKey = batch.bodyKey(0);
            }
            bodyKeyReferenceCount += batchSize;
        }

        void addJoint(@Nonnull JointKey jointKey) {
            if (firstJointKey == null) {
                firstJointKey = jointKey;
            }
            jointKeyReferenceCount++;
        }

        @Nonnull
        EntityReferences references() {
            return new EntityReferences(bodyKeyReferenceCount,
                firstBodyKey,
                jointKeyReferenceCount,
                firstJointKey);
        }
    }
}
