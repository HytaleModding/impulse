package dev.hytalemodding.impulse.core.plugin.snapshot;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

final class PublishedPhysicsBodyFrameStorage {

    private static final int POSITION_X = 0;
    private static final int POSITION_Y = 1;
    private static final int POSITION_Z = 2;
    private static final int ROTATION_X = 3;
    private static final int ROTATION_Y = 4;
    private static final int ROTATION_Z = 5;
    private static final int ROTATION_W = 6;
    private static final int LINEAR_VELOCITY_X = 7;
    private static final int LINEAR_VELOCITY_Y = 8;
    private static final int LINEAR_VELOCITY_Z = 9;
    private static final int ANGULAR_VELOCITY_X = 10;
    private static final int ANGULAR_VELOCITY_Y = 11;
    private static final int ANGULAR_VELOCITY_Z = 12;
    private static final int CENTER_OF_MASS_OFFSET_Y = 13;
    private static final int BOX_HALF_EXTENT_X = 14;
    private static final int BOX_HALF_EXTENT_Y = 15;
    private static final int BOX_HALF_EXTENT_Z = 16;
    private static final int SPHERE_RADIUS = 17;
    private static final int HALF_HEIGHT = 18;
    private static final int FLOAT_STRIDE = 19;

    private static final byte SLEEPING = 1;
    private static final byte SENSOR = 1 << 1;
    private static final byte HAS_BOX_HALF_EXTENTS = 1 << 2;

    private final long frameEpoch;
    private final long worldEpoch;
    private final SpaceId[] spaceIds;
    private final long[] spaceEpochs;
    private final int[] spaceBodyStarts;
    private final int[] spaceBodyCounts;
    private final RigidBodyKey[] bodyKeys;
    private final SpaceId[] bodySpaceIds;
    private final long[] bodySpaceEpochs;
    private final long[] registrationGenerations;
    private final PhysicsBodyKind[] kinds;
    private final PhysicsBodyPersistenceMode[] persistenceModes;
    private final PhysicsBodyType[] bodyTypes;
    private final ShapeType[] shapeTypes;
    private final PhysicsAxis[] shapeAxes;
    private final byte[] flags;
    private final float[] values;

    private PublishedPhysicsBodyFrameStorage(long frameEpoch,
        long worldEpoch,
        SpaceId[] spaceIds,
        long[] spaceEpochs,
        int[] spaceBodyStarts,
        int[] spaceBodyCounts,
        RigidBodyKey[] bodyKeys,
        SpaceId[] bodySpaceIds,
        long[] bodySpaceEpochs,
        long[] registrationGenerations,
        PhysicsBodyKind[] kinds,
        PhysicsBodyPersistenceMode[] persistenceModes,
        PhysicsBodyType[] bodyTypes,
        ShapeType[] shapeTypes,
        PhysicsAxis[] shapeAxes,
        byte[] flags,
        float[] values) {
        this.frameEpoch = frameEpoch;
        this.worldEpoch = worldEpoch;
        this.spaceIds = spaceIds;
        this.spaceEpochs = spaceEpochs;
        this.spaceBodyStarts = spaceBodyStarts;
        this.spaceBodyCounts = spaceBodyCounts;
        this.bodyKeys = bodyKeys;
        this.bodySpaceIds = bodySpaceIds;
        this.bodySpaceEpochs = bodySpaceEpochs;
        this.registrationGenerations = registrationGenerations;
        this.kinds = kinds;
        this.persistenceModes = persistenceModes;
        this.bodyTypes = bodyTypes;
        this.shapeTypes = shapeTypes;
        this.shapeAxes = shapeAxes;
        this.flags = flags;
        this.values = values;
    }

    static Builder builder(long frameEpoch, long worldEpoch, int expectedSpaces, int expectedBodies) {
        return new Builder(frameEpoch, worldEpoch, expectedSpaces, expectedBodies);
    }

    int spaceCount() {
        return spaceIds.length;
    }

    int bodyCount() {
        return bodyKeys.length;
    }

    SpaceId spaceId(int spaceIndex) {
        return spaceIds[spaceIndex];
    }

    long spaceEpoch(int spaceIndex) {
        return spaceEpochs[spaceIndex];
    }

    int spaceBodyStart(int spaceIndex) {
        return spaceBodyStarts[spaceIndex];
    }

    int spaceBodyCount(int spaceIndex) {
        return spaceBodyCounts[spaceIndex];
    }

    PublishedPhysicsBodySnapshot bodySnapshot(int bodyIndex) {
        return new PublishedPhysicsBodySnapshot(bodyKey(bodyIndex),
            bodySpaceId(bodyIndex),
            frameEpoch,
            worldEpoch,
            bodySpaceEpoch(bodyIndex),
            registrationGeneration(bodyIndex),
            kind(bodyIndex),
            persistenceMode(bodyIndex),
            positionX(bodyIndex),
            positionY(bodyIndex),
            positionZ(bodyIndex),
            rotationX(bodyIndex),
            rotationY(bodyIndex),
            rotationZ(bodyIndex),
            rotationW(bodyIndex),
            linearVelocityX(bodyIndex),
            linearVelocityY(bodyIndex),
            linearVelocityZ(bodyIndex),
            angularVelocityX(bodyIndex),
            angularVelocityY(bodyIndex),
            angularVelocityZ(bodyIndex),
            bodyType(bodyIndex),
            sleeping(bodyIndex),
            sensor(bodyIndex),
            centerOfMassOffsetY(bodyIndex),
            shapeType(bodyIndex),
            hasBoxHalfExtents(bodyIndex),
            boxHalfExtentX(bodyIndex),
            boxHalfExtentY(bodyIndex),
            boxHalfExtentZ(bodyIndex),
            sphereRadius(bodyIndex),
            halfHeight(bodyIndex),
            shapeAxis(bodyIndex));
    }

    void forEachBodyCursor(@Nonnull Consumer<? super PublishedPhysicsBodySnapshotCursor> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        FrameBodyCursor cursor = new FrameBodyCursor();
        for (int bodyIndex = 0; bodyIndex < bodyKeys.length; bodyIndex++) {
            cursor.index = bodyIndex;
            consumer.accept(cursor);
        }
    }

    private RigidBodyKey bodyKey(int bodyIndex) {
        return bodyKeys[bodyIndex];
    }

    private SpaceId bodySpaceId(int bodyIndex) {
        return bodySpaceIds[bodyIndex];
    }

    private long bodySpaceEpoch(int bodyIndex) {
        return bodySpaceEpochs[bodyIndex];
    }

    private long registrationGeneration(int bodyIndex) {
        return registrationGenerations[bodyIndex];
    }

    private PhysicsBodyKind kind(int bodyIndex) {
        return kinds[bodyIndex];
    }

    private PhysicsBodyPersistenceMode persistenceMode(int bodyIndex) {
        return persistenceModes[bodyIndex];
    }

    private PhysicsBodyType bodyType(int bodyIndex) {
        return bodyTypes[bodyIndex];
    }

    private ShapeType shapeType(int bodyIndex) {
        return shapeTypes[bodyIndex];
    }

    private PhysicsAxis shapeAxis(int bodyIndex) {
        return shapeAxes[bodyIndex];
    }

    private boolean sleeping(int bodyIndex) {
        return (flags[bodyIndex] & SLEEPING) != 0;
    }

    private boolean sensor(int bodyIndex) {
        return (flags[bodyIndex] & SENSOR) != 0;
    }

    private boolean hasBoxHalfExtents(int bodyIndex) {
        return (flags[bodyIndex] & HAS_BOX_HALF_EXTENTS) != 0;
    }

    private float positionX(int bodyIndex) {
        return value(bodyIndex, POSITION_X);
    }

    private float positionY(int bodyIndex) {
        return value(bodyIndex, POSITION_Y);
    }

    private float positionZ(int bodyIndex) {
        return value(bodyIndex, POSITION_Z);
    }

    private float rotationX(int bodyIndex) {
        return value(bodyIndex, ROTATION_X);
    }

    private float rotationY(int bodyIndex) {
        return value(bodyIndex, ROTATION_Y);
    }

    private float rotationZ(int bodyIndex) {
        return value(bodyIndex, ROTATION_Z);
    }

    private float rotationW(int bodyIndex) {
        return value(bodyIndex, ROTATION_W);
    }

    private float linearVelocityX(int bodyIndex) {
        return value(bodyIndex, LINEAR_VELOCITY_X);
    }

    private float linearVelocityY(int bodyIndex) {
        return value(bodyIndex, LINEAR_VELOCITY_Y);
    }

    private float linearVelocityZ(int bodyIndex) {
        return value(bodyIndex, LINEAR_VELOCITY_Z);
    }

    private float angularVelocityX(int bodyIndex) {
        return value(bodyIndex, ANGULAR_VELOCITY_X);
    }

    private float angularVelocityY(int bodyIndex) {
        return value(bodyIndex, ANGULAR_VELOCITY_Y);
    }

    private float angularVelocityZ(int bodyIndex) {
        return value(bodyIndex, ANGULAR_VELOCITY_Z);
    }

    private float centerOfMassOffsetY(int bodyIndex) {
        return value(bodyIndex, CENTER_OF_MASS_OFFSET_Y);
    }

    private float boxHalfExtentX(int bodyIndex) {
        return value(bodyIndex, BOX_HALF_EXTENT_X);
    }

    private float boxHalfExtentY(int bodyIndex) {
        return value(bodyIndex, BOX_HALF_EXTENT_Y);
    }

    private float boxHalfExtentZ(int bodyIndex) {
        return value(bodyIndex, BOX_HALF_EXTENT_Z);
    }

    private float sphereRadius(int bodyIndex) {
        return value(bodyIndex, SPHERE_RADIUS);
    }

    private float halfHeight(int bodyIndex) {
        return value(bodyIndex, HALF_HEIGHT);
    }

    private float value(int bodyIndex, int offset) {
        return values[bodyIndex * FLOAT_STRIDE + offset];
    }

    static final class Builder {

        private final long frameEpoch;
        private final long worldEpoch;
        private final SpaceId[] spaceIds;
        private final long[] spaceEpochs;
        private final int[] spaceBodyStarts;
        private final int[] spaceBodyCounts;
        private final RigidBodyKey[] bodyKeys;
        private final SpaceId[] bodySpaceIds;
        private final long[] bodySpaceEpochs;
        private final long[] registrationGenerations;
        private final PhysicsBodyKind[] kinds;
        private final PhysicsBodyPersistenceMode[] persistenceModes;
        private final PhysicsBodyType[] bodyTypes;
        private final ShapeType[] shapeTypes;
        private final PhysicsAxis[] shapeAxes;
        private final byte[] flags;
        private final float[] values;
        private int nextSpace;
        private int nextBody;
        private int currentSpace = -1;
        private int currentSpaceBodyCount;

        private Builder(long frameEpoch, long worldEpoch, int expectedSpaces, int expectedBodies) {
            if (expectedSpaces < 0) {
                throw new IllegalArgumentException("expectedSpaces cannot be negative");
            }
            if (expectedBodies < 0) {
                throw new IllegalArgumentException("expectedBodies cannot be negative");
            }
            this.frameEpoch = frameEpoch;
            this.worldEpoch = worldEpoch;
            this.spaceIds = new SpaceId[expectedSpaces];
            this.spaceEpochs = new long[expectedSpaces];
            this.spaceBodyStarts = new int[expectedSpaces];
            this.spaceBodyCounts = new int[expectedSpaces];
            this.bodyKeys = new RigidBodyKey[expectedBodies];
            this.bodySpaceIds = new SpaceId[expectedBodies];
            this.bodySpaceEpochs = new long[expectedBodies];
            this.registrationGenerations = new long[expectedBodies];
            this.kinds = new PhysicsBodyKind[expectedBodies];
            this.persistenceModes = new PhysicsBodyPersistenceMode[expectedBodies];
            this.bodyTypes = new PhysicsBodyType[expectedBodies];
            this.shapeTypes = new ShapeType[expectedBodies];
            this.shapeAxes = new PhysicsAxis[expectedBodies];
            this.flags = new byte[expectedBodies];
            this.values = new float[expectedBodies * FLOAT_STRIDE];
        }

        void addSpace(@Nonnull SpaceId spaceId, long spaceEpoch, int bodyCount) {
            if (bodyCount < 0) {
                throw new IllegalArgumentException("bodyCount cannot be negative");
            }
            requireCurrentSpaceFilled();
            if (nextSpace >= spaceIds.length) {
                throw new IllegalStateException("too many spaces added to published frame");
            }
            spaceIds[nextSpace] = Objects.requireNonNull(spaceId, "spaceId");
            spaceEpochs[nextSpace] = spaceEpoch;
            spaceBodyStarts[nextSpace] = nextBody;
            spaceBodyCounts[nextSpace] = bodyCount;
            currentSpace = nextSpace;
            currentSpaceBodyCount = 0;
            nextSpace++;
        }

        void addBody(@Nonnull RigidBodyKey bodyKey,
            @Nonnull SpaceId spaceId,
            long spaceEpoch,
            long registrationGeneration,
            @Nonnull PhysicsBodyKind kind,
            @Nonnull PhysicsBodyPersistenceMode persistenceMode,
            @Nonnull PhysicsBodySnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            if (currentSpace < 0) {
                throw new IllegalStateException("add a space before adding bodies to a published frame");
            }
            if (!spaceIds[currentSpace].equals(spaceId)) {
                throw new IllegalArgumentException("body space id does not match current space frame");
            }
            if (currentSpaceBodyCount >= spaceBodyCounts[currentSpace]) {
                throw new IllegalStateException("too many bodies added to current published space frame");
            }
            if (nextBody >= bodyKeys.length) {
                throw new IllegalStateException("too many bodies added to published frame");
            }
            bodyKeys[nextBody] = Objects.requireNonNull(bodyKey, "bodyKey");
            bodySpaceIds[nextBody] = Objects.requireNonNull(spaceId, "spaceId");
            bodySpaceEpochs[nextBody] = spaceEpoch;
            registrationGenerations[nextBody] = registrationGeneration;
            kinds[nextBody] = Objects.requireNonNull(kind, "kind");
            persistenceModes[nextBody] = Objects.requireNonNull(persistenceMode, "persistenceMode");
            bodyTypes[nextBody] = snapshot.bodyType();
            shapeTypes[nextBody] = snapshot.shapeType();
            shapeAxes[nextBody] = snapshot.shapeAxis();
            byte bodyFlags = 0;
            if (snapshot.sleeping()) {
                bodyFlags |= SLEEPING;
            }
            if (snapshot.sensor()) {
                bodyFlags |= SENSOR;
            }
            if (snapshot.hasBoxHalfExtents()) {
                bodyFlags |= HAS_BOX_HALF_EXTENTS;
            }
            flags[nextBody] = bodyFlags;
            put(nextBody, POSITION_X, snapshot.positionX());
            put(nextBody, POSITION_Y, snapshot.positionY());
            put(nextBody, POSITION_Z, snapshot.positionZ());
            put(nextBody, ROTATION_X, snapshot.rotationX());
            put(nextBody, ROTATION_Y, snapshot.rotationY());
            put(nextBody, ROTATION_Z, snapshot.rotationZ());
            put(nextBody, ROTATION_W, snapshot.rotationW());
            put(nextBody, LINEAR_VELOCITY_X, snapshot.linearVelocityX());
            put(nextBody, LINEAR_VELOCITY_Y, snapshot.linearVelocityY());
            put(nextBody, LINEAR_VELOCITY_Z, snapshot.linearVelocityZ());
            put(nextBody, ANGULAR_VELOCITY_X, snapshot.angularVelocityX());
            put(nextBody, ANGULAR_VELOCITY_Y, snapshot.angularVelocityY());
            put(nextBody, ANGULAR_VELOCITY_Z, snapshot.angularVelocityZ());
            put(nextBody, CENTER_OF_MASS_OFFSET_Y, snapshot.centerOfMassOffsetY());
            put(nextBody, BOX_HALF_EXTENT_X, snapshot.boxHalfExtentX());
            put(nextBody, BOX_HALF_EXTENT_Y, snapshot.boxHalfExtentY());
            put(nextBody, BOX_HALF_EXTENT_Z, snapshot.boxHalfExtentZ());
            put(nextBody, SPHERE_RADIUS, snapshot.sphereRadius());
            put(nextBody, HALF_HEIGHT, snapshot.halfHeight());
            nextBody++;
            currentSpaceBodyCount++;
        }

        PublishedPhysicsBodyFrameStorage build() {
            requireCurrentSpaceFilled();
            if (nextSpace != spaceIds.length) {
                throw new IllegalStateException("published frame space count mismatch");
            }
            if (nextBody != bodyKeys.length) {
                throw new IllegalStateException("published frame body count mismatch");
            }
            return new PublishedPhysicsBodyFrameStorage(frameEpoch,
                worldEpoch,
                spaceIds,
                spaceEpochs,
                spaceBodyStarts,
                spaceBodyCounts,
                bodyKeys,
                bodySpaceIds,
                bodySpaceEpochs,
                registrationGenerations,
                kinds,
                persistenceModes,
                bodyTypes,
                shapeTypes,
                shapeAxes,
                flags,
                values);
        }

        private void put(int bodyIndex, int offset, float value) {
            values[bodyIndex * FLOAT_STRIDE + offset] = value;
        }

        private void requireCurrentSpaceFilled() {
            if (currentSpace >= 0 && currentSpaceBodyCount != spaceBodyCounts[currentSpace]) {
                throw new IllegalStateException("published frame body count mismatch for current space");
            }
        }
    }

    private final class FrameBodyCursor implements PublishedPhysicsBodySnapshotCursor {

        private int index;

        @Nonnull
        @Override
        public RigidBodyKey bodyKey() {
            return PublishedPhysicsBodyFrameStorage.this.bodyKey(index);
        }

        @Nonnull
        @Override
        public SpaceId spaceId() {
            return bodySpaceId(index);
        }

        @Override
        public long frameEpoch() {
            return frameEpoch;
        }

        @Override
        public long worldEpoch() {
            return worldEpoch;
        }

        @Override
        public long spaceEpoch() {
            return bodySpaceEpoch(index);
        }

        @Override
        public long registrationGeneration() {
            return PublishedPhysicsBodyFrameStorage.this.registrationGeneration(index);
        }

        @Nonnull
        @Override
        public PhysicsBodyKind kind() {
            return PublishedPhysicsBodyFrameStorage.this.kind(index);
        }

        @Nonnull
        @Override
        public PhysicsBodyPersistenceMode persistenceMode() {
            return PublishedPhysicsBodyFrameStorage.this.persistenceMode(index);
        }

        @Override
        public float positionX() {
            return PublishedPhysicsBodyFrameStorage.this.positionX(index);
        }

        @Override
        public float positionY() {
            return PublishedPhysicsBodyFrameStorage.this.positionY(index);
        }

        @Override
        public float positionZ() {
            return PublishedPhysicsBodyFrameStorage.this.positionZ(index);
        }

        @Override
        public float rotationX() {
            return PublishedPhysicsBodyFrameStorage.this.rotationX(index);
        }

        @Override
        public float rotationY() {
            return PublishedPhysicsBodyFrameStorage.this.rotationY(index);
        }

        @Override
        public float rotationZ() {
            return PublishedPhysicsBodyFrameStorage.this.rotationZ(index);
        }

        @Override
        public float rotationW() {
            return PublishedPhysicsBodyFrameStorage.this.rotationW(index);
        }

        @Override
        public float linearVelocityX() {
            return PublishedPhysicsBodyFrameStorage.this.linearVelocityX(index);
        }

        @Override
        public float linearVelocityY() {
            return PublishedPhysicsBodyFrameStorage.this.linearVelocityY(index);
        }

        @Override
        public float linearVelocityZ() {
            return PublishedPhysicsBodyFrameStorage.this.linearVelocityZ(index);
        }

        @Override
        public float angularVelocityX() {
            return PublishedPhysicsBodyFrameStorage.this.angularVelocityX(index);
        }

        @Override
        public float angularVelocityY() {
            return PublishedPhysicsBodyFrameStorage.this.angularVelocityY(index);
        }

        @Override
        public float angularVelocityZ() {
            return PublishedPhysicsBodyFrameStorage.this.angularVelocityZ(index);
        }

        @Nonnull
        @Override
        public PhysicsBodyType bodyType() {
            return PublishedPhysicsBodyFrameStorage.this.bodyType(index);
        }

        @Override
        public boolean sleeping() {
            return PublishedPhysicsBodyFrameStorage.this.sleeping(index);
        }

        @Override
        public boolean sensor() {
            return PublishedPhysicsBodyFrameStorage.this.sensor(index);
        }

        @Override
        public float centerOfMassOffsetY() {
            return PublishedPhysicsBodyFrameStorage.this.centerOfMassOffsetY(index);
        }

        @Nonnull
        @Override
        public ShapeType shapeType() {
            return PublishedPhysicsBodyFrameStorage.this.shapeType(index);
        }

        @Override
        public boolean hasBoxHalfExtents() {
            return PublishedPhysicsBodyFrameStorage.this.hasBoxHalfExtents(index);
        }

        @Override
        public float boxHalfExtentX() {
            return PublishedPhysicsBodyFrameStorage.this.boxHalfExtentX(index);
        }

        @Override
        public float boxHalfExtentY() {
            return PublishedPhysicsBodyFrameStorage.this.boxHalfExtentY(index);
        }

        @Override
        public float boxHalfExtentZ() {
            return PublishedPhysicsBodyFrameStorage.this.boxHalfExtentZ(index);
        }

        @Override
        public float sphereRadius() {
            return PublishedPhysicsBodyFrameStorage.this.sphereRadius(index);
        }

        @Override
        public float halfHeight() {
            return PublishedPhysicsBodyFrameStorage.this.halfHeight(index);
        }

        @Nonnull
        @Override
        public PhysicsAxis shapeAxis() {
            return PublishedPhysicsBodyFrameStorage.this.shapeAxis(index);
        }
    }
}
