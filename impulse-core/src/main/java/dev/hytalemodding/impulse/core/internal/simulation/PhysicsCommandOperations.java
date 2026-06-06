package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RigidBodySpawnBatch;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RigidBodySpawnTemplateBatch;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandContext;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Arrays;
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
 *
 * <p>Each operation occupies one row in {@code opcodes}, {@code flags}, {@code objectOffsets},
 * and {@code floatOffsets}. The row stores only the operation kind, flag bits, and the starting
 * offsets into two dense payload pools: {@code objects} for reference values and {@code floats}
 * for scalar values. When a recorder appends an operation, {@link #add(byte, int, int, int)}
 * reserves the opcode row plus the exact object/float slot counts for that opcode, then advances
 * the payload sizes. A field read later resolves as {@code objects[objectOffsets[index] + slot]}
 * or {@code floats[floatOffsets[index] + slot]}.</p>
 *
 * <p>The {@code *_SLOT} and {@code *_SLOTS} constants below define that per-opcode layout.
 * Required object fields are decoded through {@link #requiredObjectAt(int, int, Class)}, while
 * intentionally optional fields, such as the preferred joint key in
 * {@link #DESTROY_JOINT_BETWEEN_BODIES}, stay nullable through {@link #objectAt(int, int)}.</p>
 */
public final class PhysicsCommandOperations {

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

    static final int SPAWN_OBJECT_SLOTS = 7;
    static final int SPAWN_BODY_KEY_OBJECT_SLOT = 0;
    static final int SPAWN_SPACE_ID_OBJECT_SLOT = 1;
    static final int SPAWN_SHAPE_OBJECT_SLOT = 2;
    static final int SPAWN_BODY_TYPE_OBJECT_SLOT = 3;
    static final int SPAWN_SETTINGS_OBJECT_SLOT = 4;
    static final int SPAWN_KIND_OBJECT_SLOT = 5;
    static final int SPAWN_PERSISTENCE_MODE_OBJECT_SLOT = 6;
    static final int SPAWN_FLOAT_SLOTS = 4;
    static final int SPAWN_MASS_FLOAT_SLOT = 0;
    static final int SPAWN_POSITION_X_FLOAT_SLOT = 1;
    static final int SPAWN_POSITION_Y_FLOAT_SLOT = 2;
    static final int SPAWN_POSITION_Z_FLOAT_SLOT = 3;

    static final int SPAWN_BATCH_OBJECT_SLOTS = 1;
    static final int SPAWN_BATCH_OBJECT_SLOT = 0;
    static final int SPAWN_TEMPLATE_BATCH_OBJECT_SLOTS = 1;
    static final int SPAWN_TEMPLATE_BATCH_OBJECT_SLOT = 0;

    static final int BODY_COMMAND_OBJECT_SLOTS = 1;
    static final int BODY_COMMAND_BODY_KEY_OBJECT_SLOT = 0;

    static final int SET_SPACE_GRAVITY_OBJECT_SLOTS = 1;
    static final int SET_SPACE_GRAVITY_SPACE_ID_OBJECT_SLOT = 0;
    static final int SET_SPACE_GRAVITY_FLOAT_SLOTS = 3;
    static final int SET_SPACE_GRAVITY_X_FLOAT_SLOT = 0;
    static final int SET_SPACE_GRAVITY_Y_FLOAT_SLOT = 1;
    static final int SET_SPACE_GRAVITY_Z_FLOAT_SLOT = 2;

    static final int SET_TRANSFORM_FLOAT_SLOTS = 7;
    static final int SET_TRANSFORM_POSITION_X_FLOAT_SLOT = 0;
    static final int SET_TRANSFORM_POSITION_Y_FLOAT_SLOT = 1;
    static final int SET_TRANSFORM_POSITION_Z_FLOAT_SLOT = 2;
    static final int SET_TRANSFORM_ROTATION_X_FLOAT_SLOT = 3;
    static final int SET_TRANSFORM_ROTATION_Y_FLOAT_SLOT = 4;
    static final int SET_TRANSFORM_ROTATION_Z_FLOAT_SLOT = 5;
    static final int SET_TRANSFORM_ROTATION_W_FLOAT_SLOT = 6;

    static final int SET_POSITION_FLOAT_SLOTS = 3;
    static final int SET_POSITION_X_FLOAT_SLOT = 0;
    static final int SET_POSITION_Y_FLOAT_SLOT = 1;
    static final int SET_POSITION_Z_FLOAT_SLOT = 2;

    static final int SET_VELOCITY_FLOAT_SLOTS = 6;
    static final int SET_VELOCITY_LINEAR_X_FLOAT_SLOT = 0;
    static final int SET_VELOCITY_LINEAR_Y_FLOAT_SLOT = 1;
    static final int SET_VELOCITY_LINEAR_Z_FLOAT_SLOT = 2;
    static final int SET_VELOCITY_ANGULAR_X_FLOAT_SLOT = 3;
    static final int SET_VELOCITY_ANGULAR_Y_FLOAT_SLOT = 4;
    static final int SET_VELOCITY_ANGULAR_Z_FLOAT_SLOT = 5;

    static final int SET_TYPE_OBJECT_SLOTS = 2;
    static final int SET_TYPE_BODY_KEY_OBJECT_SLOT = 0;
    static final int SET_TYPE_BODY_TYPE_OBJECT_SLOT = 1;

    static final int VECTOR_COMMAND_FLOAT_SLOTS = 3;
    static final int OFFSET_VECTOR_COMMAND_FLOAT_SLOTS = 6;
    static final int VECTOR_COMMAND_X_FLOAT_SLOT = 0;
    static final int VECTOR_COMMAND_Y_FLOAT_SLOT = 1;
    static final int VECTOR_COMMAND_Z_FLOAT_SLOT = 2;
    static final int VECTOR_COMMAND_OFFSET_X_FLOAT_SLOT = 3;
    static final int VECTOR_COMMAND_OFFSET_Y_FLOAT_SLOT = 4;
    static final int VECTOR_COMMAND_OFFSET_Z_FLOAT_SLOT = 5;

    static final int CREATE_JOINT_OBJECT_SLOTS = 5;
    static final int CREATE_JOINT_JOINT_KEY_OBJECT_SLOT = 0;
    static final int CREATE_JOINT_SPACE_ID_OBJECT_SLOT = 1;
    static final int CREATE_JOINT_BODY_A_OBJECT_SLOT = 2;
    static final int CREATE_JOINT_BODY_B_OBJECT_SLOT = 3;
    static final int CREATE_JOINT_TYPE_OBJECT_SLOT = 4;
    static final int CREATE_JOINT_FLOAT_SLOTS = 16;
    static final int CREATE_JOINT_ANCHOR_A_X_FLOAT_SLOT = 0;
    static final int CREATE_JOINT_ANCHOR_A_Y_FLOAT_SLOT = 1;
    static final int CREATE_JOINT_ANCHOR_A_Z_FLOAT_SLOT = 2;
    static final int CREATE_JOINT_ANCHOR_B_X_FLOAT_SLOT = 3;
    static final int CREATE_JOINT_ANCHOR_B_Y_FLOAT_SLOT = 4;
    static final int CREATE_JOINT_ANCHOR_B_Z_FLOAT_SLOT = 5;
    static final int CREATE_JOINT_AXIS_X_FLOAT_SLOT = 6;
    static final int CREATE_JOINT_AXIS_Y_FLOAT_SLOT = 7;
    static final int CREATE_JOINT_AXIS_Z_FLOAT_SLOT = 8;
    static final int CREATE_JOINT_REST_LENGTH_FLOAT_SLOT = 9;
    static final int CREATE_JOINT_STIFFNESS_FLOAT_SLOT = 10;
    static final int CREATE_JOINT_DAMPING_FLOAT_SLOT = 11;
    static final int CREATE_JOINT_LOWER_LIMIT_FLOAT_SLOT = 12;
    static final int CREATE_JOINT_UPPER_LIMIT_FLOAT_SLOT = 13;
    static final int CREATE_JOINT_MOTOR_TARGET_VELOCITY_FLOAT_SLOT = 14;
    static final int CREATE_JOINT_MOTOR_MAX_FORCE_FLOAT_SLOT = 15;

    static final int DESTROY_JOINT_OBJECT_SLOTS = 1;
    static final int DESTROY_JOINT_KEY_OBJECT_SLOT = 0;
    static final int DESTROY_JOINT_BETWEEN_OBJECT_SLOTS = 4;
    static final int DESTROY_JOINT_BETWEEN_PREFERRED_KEY_OBJECT_SLOT = 0;
    static final int DESTROY_JOINT_BETWEEN_SPACE_ID_OBJECT_SLOT = 1;
    static final int DESTROY_JOINT_BETWEEN_BODY_A_OBJECT_SLOT = 2;
    static final int DESTROY_JOINT_BETWEEN_BODY_B_OBJECT_SLOT = 3;

    private byte[] opcodes;
    private int[] objectOffsets;
    private int[] floatOffsets;
    private Object[] objects;
    private float[] floats;
    private int[] flags;
    private int size;
    private int objectSize;
    private int floatSize;

    public PhysicsCommandOperations(int expectedOps) {
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
    public PhysicsCommandOperations freeze() {
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

    public void addSpawn(@Nonnull RigidBodyKey bodyKey,
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
        int index = add(SPAWN_RIGID_BODY, 0, SPAWN_OBJECT_SLOTS, SPAWN_FLOAT_SLOTS);
        object(index, SPAWN_BODY_KEY_OBJECT_SLOT, bodyKey);
        object(index, SPAWN_SPACE_ID_OBJECT_SLOT, spaceId);
        object(index, SPAWN_SHAPE_OBJECT_SLOT, shape);
        object(index, SPAWN_BODY_TYPE_OBJECT_SLOT, bodyType);
        object(index, SPAWN_SETTINGS_OBJECT_SLOT, settings);
        object(index, SPAWN_KIND_OBJECT_SLOT, kind);
        object(index, SPAWN_PERSISTENCE_MODE_OBJECT_SLOT, persistenceMode);
        floatAt(index, SPAWN_MASS_FLOAT_SLOT, mass);
        floatAt(index, SPAWN_POSITION_X_FLOAT_SLOT, positionX);
        floatAt(index, SPAWN_POSITION_Y_FLOAT_SLOT, positionY);
        floatAt(index, SPAWN_POSITION_Z_FLOAT_SLOT, positionZ);
    }

    public void addSpawnBatch(@Nonnull RigidBodySpawnBatch batch) {
        if (batch.size() <= 0) {
            return;
        }
        int index = add(SPAWN_RIGID_BODY_BATCH, 0, SPAWN_BATCH_OBJECT_SLOTS, 0);
        object(index, SPAWN_BATCH_OBJECT_SLOT, batch.freeze());
    }

    public void addSpawnTemplateBatch(@Nonnull RigidBodySpawnTemplateBatch batch) {
        if (batch.size() <= 0) {
            return;
        }
        int index = add(SPAWN_RIGID_BODY_TEMPLATE_BATCH, 0, SPAWN_TEMPLATE_BATCH_OBJECT_SLOTS, 0);
        object(index, SPAWN_TEMPLATE_BATCH_OBJECT_SLOT, batch.freeze());
    }

    public void addDestroyBody(@Nonnull RigidBodyKey bodyKey) {
        int index = add(DESTROY_RIGID_BODY, 0, BODY_COMMAND_OBJECT_SLOTS, 0);
        object(index, BODY_COMMAND_BODY_KEY_OBJECT_SLOT, bodyKey);
    }

    public void addSetSpaceGravity(@Nonnull SpaceId spaceId,
        float x,
        float y,
        float z) {
        int index = add(SET_SPACE_GRAVITY, 0, SET_SPACE_GRAVITY_OBJECT_SLOTS, SET_SPACE_GRAVITY_FLOAT_SLOTS);
        object(index, SET_SPACE_GRAVITY_SPACE_ID_OBJECT_SLOT, spaceId);
        floatAt(index, SET_SPACE_GRAVITY_X_FLOAT_SLOT, x);
        floatAt(index, SET_SPACE_GRAVITY_Y_FLOAT_SLOT, y);
        floatAt(index, SET_SPACE_GRAVITY_Z_FLOAT_SLOT, z);
    }

    public void addSetTransform(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        boolean activate) {
        int index = add(SET_RIGID_BODY_TRANSFORM,
            activate ? FLAG_ACTIVATE : 0,
            BODY_COMMAND_OBJECT_SLOTS,
            SET_TRANSFORM_FLOAT_SLOTS);
        object(index, BODY_COMMAND_BODY_KEY_OBJECT_SLOT, bodyKey);
        floatAt(index, SET_TRANSFORM_POSITION_X_FLOAT_SLOT, positionX);
        floatAt(index, SET_TRANSFORM_POSITION_Y_FLOAT_SLOT, positionY);
        floatAt(index, SET_TRANSFORM_POSITION_Z_FLOAT_SLOT, positionZ);
        floatAt(index, SET_TRANSFORM_ROTATION_X_FLOAT_SLOT, rotationX);
        floatAt(index, SET_TRANSFORM_ROTATION_Y_FLOAT_SLOT, rotationY);
        floatAt(index, SET_TRANSFORM_ROTATION_Z_FLOAT_SLOT, rotationZ);
        floatAt(index, SET_TRANSFORM_ROTATION_W_FLOAT_SLOT, rotationW);
    }

    public void addSetPosition(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        boolean activate) {
        int index = add(SET_RIGID_BODY_POSITION,
            activate ? FLAG_ACTIVATE : 0,
            BODY_COMMAND_OBJECT_SLOTS,
            SET_POSITION_FLOAT_SLOTS);
        object(index, BODY_COMMAND_BODY_KEY_OBJECT_SLOT, bodyKey);
        floatAt(index, SET_POSITION_X_FLOAT_SLOT, positionX);
        floatAt(index, SET_POSITION_Y_FLOAT_SLOT, positionY);
        floatAt(index, SET_POSITION_Z_FLOAT_SLOT, positionZ);
    }

    public void addSetVelocity(@Nonnull RigidBodyKey bodyKey,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ,
        boolean activate) {
        int index = add(SET_RIGID_BODY_VELOCITY,
            activate ? FLAG_ACTIVATE : 0,
            BODY_COMMAND_OBJECT_SLOTS,
            SET_VELOCITY_FLOAT_SLOTS);
        object(index, BODY_COMMAND_BODY_KEY_OBJECT_SLOT, bodyKey);
        floatAt(index, SET_VELOCITY_LINEAR_X_FLOAT_SLOT, linearX);
        floatAt(index, SET_VELOCITY_LINEAR_Y_FLOAT_SLOT, linearY);
        floatAt(index, SET_VELOCITY_LINEAR_Z_FLOAT_SLOT, linearZ);
        floatAt(index, SET_VELOCITY_ANGULAR_X_FLOAT_SLOT, angularX);
        floatAt(index, SET_VELOCITY_ANGULAR_Y_FLOAT_SLOT, angularY);
        floatAt(index, SET_VELOCITY_ANGULAR_Z_FLOAT_SLOT, angularZ);
    }

    public void addSetType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        int index = add(SET_RIGID_BODY_TYPE, activate ? FLAG_ACTIVATE : 0, SET_TYPE_OBJECT_SLOTS, 0);
        object(index, SET_TYPE_BODY_KEY_OBJECT_SLOT, bodyKey);
        object(index, SET_TYPE_BODY_TYPE_OBJECT_SLOT, bodyType);
    }

    public void addActivate(@Nonnull RigidBodyKey bodyKey) {
        int index = add(ACTIVATE_RIGID_BODY, 0, BODY_COMMAND_OBJECT_SLOTS, 0);
        object(index, BODY_COMMAND_BODY_KEY_OBJECT_SLOT, bodyKey);
    }

    public void addImpulse(@Nonnull RigidBodyKey bodyKey,
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
            BODY_COMMAND_OBJECT_SLOTS,
            (operationFlags & FLAG_OFFSET) != 0 ? OFFSET_VECTOR_COMMAND_FLOAT_SLOTS : VECTOR_COMMAND_FLOAT_SLOTS);
        object(index, BODY_COMMAND_BODY_KEY_OBJECT_SLOT, bodyKey);
        floatAt(index, VECTOR_COMMAND_X_FLOAT_SLOT, x);
        floatAt(index, VECTOR_COMMAND_Y_FLOAT_SLOT, y);
        floatAt(index, VECTOR_COMMAND_Z_FLOAT_SLOT, z);
        if ((operationFlags & FLAG_OFFSET) != 0) {
            floatAt(index, VECTOR_COMMAND_OFFSET_X_FLOAT_SLOT, offsetX);
            floatAt(index, VECTOR_COMMAND_OFFSET_Y_FLOAT_SLOT, offsetY);
            floatAt(index, VECTOR_COMMAND_OFFSET_Z_FLOAT_SLOT, offsetZ);
        }
    }

    public void addForce(@Nonnull RigidBodyKey bodyKey,
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
            BODY_COMMAND_OBJECT_SLOTS,
            (operationFlags & FLAG_OFFSET) != 0 ? OFFSET_VECTOR_COMMAND_FLOAT_SLOTS : VECTOR_COMMAND_FLOAT_SLOTS);
        object(index, BODY_COMMAND_BODY_KEY_OBJECT_SLOT, bodyKey);
        floatAt(index, VECTOR_COMMAND_X_FLOAT_SLOT, x);
        floatAt(index, VECTOR_COMMAND_Y_FLOAT_SLOT, y);
        floatAt(index, VECTOR_COMMAND_Z_FLOAT_SLOT, z);
        if ((operationFlags & FLAG_OFFSET) != 0) {
            floatAt(index, VECTOR_COMMAND_OFFSET_X_FLOAT_SLOT, offsetX);
            floatAt(index, VECTOR_COMMAND_OFFSET_Y_FLOAT_SLOT, offsetY);
            floatAt(index, VECTOR_COMMAND_OFFSET_Z_FLOAT_SLOT, offsetZ);
        }
    }

    public void addJoint(@Nonnull JointKey jointKey,
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
        int index = add(CREATE_JOINT,
            motorEnabled ? FLAG_MOTOR_ENABLED : 0,
            CREATE_JOINT_OBJECT_SLOTS,
            CREATE_JOINT_FLOAT_SLOTS);
        object(index, CREATE_JOINT_JOINT_KEY_OBJECT_SLOT, jointKey);
        object(index, CREATE_JOINT_SPACE_ID_OBJECT_SLOT, spaceId);
        object(index, CREATE_JOINT_BODY_A_OBJECT_SLOT, bodyA);
        object(index, CREATE_JOINT_BODY_B_OBJECT_SLOT, bodyB);
        object(index, CREATE_JOINT_TYPE_OBJECT_SLOT, type);
        floatAt(index, CREATE_JOINT_ANCHOR_A_X_FLOAT_SLOT, anchorAX);
        floatAt(index, CREATE_JOINT_ANCHOR_A_Y_FLOAT_SLOT, anchorAY);
        floatAt(index, CREATE_JOINT_ANCHOR_A_Z_FLOAT_SLOT, anchorAZ);
        floatAt(index, CREATE_JOINT_ANCHOR_B_X_FLOAT_SLOT, anchorBX);
        floatAt(index, CREATE_JOINT_ANCHOR_B_Y_FLOAT_SLOT, anchorBY);
        floatAt(index, CREATE_JOINT_ANCHOR_B_Z_FLOAT_SLOT, anchorBZ);
        floatAt(index, CREATE_JOINT_AXIS_X_FLOAT_SLOT, axisX);
        floatAt(index, CREATE_JOINT_AXIS_Y_FLOAT_SLOT, axisY);
        floatAt(index, CREATE_JOINT_AXIS_Z_FLOAT_SLOT, axisZ);
        floatAt(index, CREATE_JOINT_REST_LENGTH_FLOAT_SLOT, restLength);
        floatAt(index, CREATE_JOINT_STIFFNESS_FLOAT_SLOT, stiffness);
        floatAt(index, CREATE_JOINT_DAMPING_FLOAT_SLOT, damping);
        floatAt(index, CREATE_JOINT_LOWER_LIMIT_FLOAT_SLOT, lowerLimit);
        floatAt(index, CREATE_JOINT_UPPER_LIMIT_FLOAT_SLOT, upperLimit);
        floatAt(index, CREATE_JOINT_MOTOR_TARGET_VELOCITY_FLOAT_SLOT, motorTargetVelocity);
        floatAt(index, CREATE_JOINT_MOTOR_MAX_FORCE_FLOAT_SLOT, motorMaxForce);
    }

    public void addDestroyJoint(@Nonnull JointKey jointKey) {
        int index = add(DESTROY_JOINT, 0, DESTROY_JOINT_OBJECT_SLOTS, 0);
        object(index, DESTROY_JOINT_KEY_OBJECT_SLOT, jointKey);
    }

    public void addDestroyJointBetween(@Nullable JointKey preferredJointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        int index = add(DESTROY_JOINT_BETWEEN_BODIES, 0, DESTROY_JOINT_BETWEEN_OBJECT_SLOTS, 0);
        object(index, DESTROY_JOINT_BETWEEN_PREFERRED_KEY_OBJECT_SLOT, preferredJointKey);
        object(index, DESTROY_JOINT_BETWEEN_SPACE_ID_OBJECT_SLOT, spaceId);
        object(index, DESTROY_JOINT_BETWEEN_BODY_A_OBJECT_SLOT, bodyA);
        object(index, DESTROY_JOINT_BETWEEN_BODY_B_OBJECT_SLOT, bodyB);
    }

    public int size() {
        return size;
    }

    @Nonnull
    public RecordedBodyCreationKeys bodyCreationKeys() {
        RecordedBodyCreationKeys.Builder keys = RecordedBodyCreationKeys.builder();
        for (int index = 0; index < size; index++) {
            switch (opcode(index)) {
                case SPAWN_RIGID_BODY -> keys.add(
                    requiredObjectAt(index, SPAWN_BODY_KEY_OBJECT_SLOT, RigidBodyKey.class));
                case SPAWN_RIGID_BODY_BATCH -> {
                    RigidBodySpawnBatch batch =
                        requiredObjectAt(index, SPAWN_BATCH_OBJECT_SLOT, RigidBodySpawnBatch.class);
                    keys.addAll(batch.bodyKeyMostSignificantBits(),
                        batch.bodyKeyLeastSignificantBits(),
                        batch.size());
                }
                case SPAWN_RIGID_BODY_TEMPLATE_BATCH -> {
                    RigidBodySpawnTemplateBatch batch =
                        requiredObjectAt(index,
                            SPAWN_TEMPLATE_BATCH_OBJECT_SLOT,
                            RigidBodySpawnTemplateBatch.class);
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
    public EntityReferences entityReferences() {
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
                    requiredObjectAt(index, BODY_COMMAND_BODY_KEY_OBJECT_SLOT, RigidBodyKey.class));
                case SPAWN_RIGID_BODY_BATCH ->
                    accumulator.addSpawnBatch(
                        requiredObjectAt(index, SPAWN_BATCH_OBJECT_SLOT, RigidBodySpawnBatch.class));
                case SPAWN_RIGID_BODY_TEMPLATE_BATCH ->
                    accumulator.addSpawnTemplateBatch(
                        requiredObjectAt(index,
                            SPAWN_TEMPLATE_BATCH_OBJECT_SLOT,
                            RigidBodySpawnTemplateBatch.class));
                case CREATE_JOINT -> {
                    accumulator.addJoint(requiredObjectAt(index,
                        CREATE_JOINT_JOINT_KEY_OBJECT_SLOT,
                        JointKey.class));
                    accumulator.addBody(requiredObjectAt(index,
                        CREATE_JOINT_BODY_A_OBJECT_SLOT,
                        RigidBodyKey.class));
                    accumulator.addBody(requiredObjectAt(index,
                        CREATE_JOINT_BODY_B_OBJECT_SLOT,
                        RigidBodyKey.class));
                }
                case DESTROY_JOINT -> accumulator.addJoint(
                    requiredObjectAt(index, DESTROY_JOINT_KEY_OBJECT_SLOT, JointKey.class));
                case DESTROY_JOINT_BETWEEN_BODIES -> {
                    JointKey jointKey = (JointKey) objectAt(index,
                        DESTROY_JOINT_BETWEEN_PREFERRED_KEY_OBJECT_SLOT);
                    if (jointKey != null) {
                        accumulator.addJoint(jointKey);
                    }
                    accumulator.addBody(requiredObjectAt(index,
                        DESTROY_JOINT_BETWEEN_BODY_A_OBJECT_SLOT,
                        RigidBodyKey.class));
                    accumulator.addBody(requiredObjectAt(index,
                        DESTROY_JOINT_BETWEEN_BODY_B_OBJECT_SLOT,
                        RigidBodyKey.class));
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

    @Nonnull
    public <T> T requiredObjectAt(int index,
        int slot,
        @Nonnull Class<T> type) {
        T value = objectAt(index, slot, type);
        if (value == null) {
            throw new IllegalArgumentException("Missing physics command object at index="
                + index
                + " slot="
                + slot
                + " type="
                + type.getSimpleName());
        }
        return value;
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

    public static final class EntityReferences {

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

        public int bodyKeyReferenceCount() {
            return bodyKeyReferenceCount;
        }

        @Nullable
        public RigidBodyKey firstBodyKey() {
            return firstBodyKey;
        }

        public int jointKeyReferenceCount() {
            return jointKeyReferenceCount;
        }

        @Nullable
        public JointKey firstJointKey() {
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
