package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Latest copied PhysicsStore snapshot frame for projection, debug, and queries.
 */
public final class PhysicsSnapshotResource implements Resource<PhysicsStore> {

    @Nonnull
    private volatile CompactSnapshot snapshot = CompactSnapshot.EMPTY;

    public PhysicsSnapshotResource() {
    }

    @Nonnull
    public PhysicsSnapshotFrame getLatestFrame() {
        return snapshot.frame();
    }

    public long latestSequence() {
        return snapshot.sequence();
    }

    public int bodyCount() {
        return snapshot.bodyCount();
    }

    /**
     * Iterates the compact published body frame without materializing snapshot objects.
     *
     * <p>The cursor instance is reused during iteration. Consumers must read the needed values
     * synchronously and must not retain the cursor after the callback returns.</p>
     */
    public void forEachBodyCursor(@Nonnull Consumer<? super BodyCursor> consumer) {
        snapshot.forEachBodyCursor(Objects.requireNonNull(consumer, "consumer"));
    }

    @Nullable
    public PhysicsBodySnapshot getBody(@Nonnull UUID bodyUuid) {
        return snapshot.body(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    public boolean containsBody(@Nonnull UUID bodyUuid) {
        return snapshot.containsBody(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nullable
    public PhysicsBodySnapshot getBody(@Nonnull Ref<PhysicsStore> bodyRef) {
        return snapshot.body(Objects.requireNonNull(bodyRef, "bodyRef"));
    }

    public void publish(@Nonnull PhysicsSnapshotFrame frame) {
        snapshot = CompactSnapshot.fromFrame(Objects.requireNonNull(frame, "frame"));
    }

    public void publish(long sequence,
        float dt,
        @Nonnull CompactSnapshot compactSnapshot) {
        snapshot = Objects.requireNonNull(compactSnapshot, "compactSnapshot")
            .withFrame(sequence, dt);
    }

    public void removeBody(@Nonnull UUID bodyUuid) {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        removeBodies(List.of(bodyUuid));
    }

    public void removeBodies(@Nonnull Collection<UUID> bodyUuids) {
        Objects.requireNonNull(bodyUuids, "bodyUuids");
        CompactSnapshot current = snapshot;
        if (bodyUuids.isEmpty()) {
            return;
        }
        ObjectOpenHashSet<UUID> removedBodyUuids = new ObjectOpenHashSet<>(bodyUuids.size());
        for (UUID bodyUuid : bodyUuids) {
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            if (current.containsBody(bodyUuid)) {
                removedBodyUuids.add(bodyUuid);
            }
        }
        if (removedBodyUuids.isEmpty()) {
            return;
        }
        snapshot = withoutBodies(current, removedBodyUuids);
    }

    public void clear() {
        snapshot = CompactSnapshot.EMPTY;
    }

    @Nonnull
    private static CompactSnapshot withoutBodies(@Nonnull CompactSnapshot current,
        @Nonnull ObjectOpenHashSet<UUID> bodyUuids) {
        int bodyCount = Math.max(0, current.bodyCount() - bodyUuids.size());
        CompactSnapshotBuilder builder = compactBuilder(bodyCount);
        for (int index = 0; index < current.bodyCount(); index++) {
            if (bodyUuids.contains(current.bodyUuids[index])) {
                continue;
            }
            builder.addBody(current.bodyRefs[index],
                current.bodyUuids[index],
                current.spaceUuids[index],
                current.bodyTypes[index],
                current.value(index, CompactSnapshot.POSITION_X),
                current.value(index, CompactSnapshot.POSITION_Y),
                current.value(index, CompactSnapshot.POSITION_Z),
                current.value(index, CompactSnapshot.ROTATION_X),
                current.value(index, CompactSnapshot.ROTATION_Y),
                current.value(index, CompactSnapshot.ROTATION_Z),
                current.value(index, CompactSnapshot.ROTATION_W),
                current.value(index, CompactSnapshot.LINEAR_VELOCITY_X),
                current.value(index, CompactSnapshot.LINEAR_VELOCITY_Y),
                current.value(index, CompactSnapshot.LINEAR_VELOCITY_Z),
                current.value(index, CompactSnapshot.ANGULAR_VELOCITY_X),
                current.value(index, CompactSnapshot.ANGULAR_VELOCITY_Y),
                current.value(index, CompactSnapshot.ANGULAR_VELOCITY_Z),
                current.value(index, CompactSnapshot.CENTER_OF_MASS_OFFSET_Y),
                current.sleeping.get(index));
        }
        return builder.build(current.sequence(), current.dt());
    }

    @Nonnull
    @Override
    public PhysicsSnapshotResource clone() {
        PhysicsSnapshotResource copy = new PhysicsSnapshotResource();
        copy.snapshot = snapshot;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSnapshotResource> getResourceType() {
        return PhysicsResourceTypes.snapshotResourceType();
    }

    @Nonnull
    public static CompactSnapshotBuilder compactBuilder(int expectedBodies) {
        return new CompactSnapshotBuilder(expectedBodies);
    }

    @Nonnull
    public static CompactSnapshot emptyCompactSnapshot() {
        return CompactSnapshot.EMPTY;
    }

    public static final class CompactSnapshot {

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
        private static final int FLOAT_STRIDE = 14;

        private static final CompactSnapshot EMPTY = empty();

        private final long sequence;
        private final float dt;
        @Nonnull
        private final Ref<PhysicsStore>[] bodyRefs;
        @Nonnull
        private final UUID[] bodyUuids;
        @Nonnull
        private final UUID[] spaceUuids;
        @Nonnull
        private final PhysicsBodyType[] bodyTypes;
        @Nonnull
        private final float[] values;
        @Nonnull
        private final BitSet sleeping;
        @Nonnull
        private final Object2IntOpenHashMap<UUID> bodiesByUuid;
        @Nonnull
        private final Int2IntOpenHashMap bodiesByRowIndex;

        private CompactSnapshot(long sequence,
            float dt,
            @Nonnull Ref<PhysicsStore>[] bodyRefs,
            @Nonnull UUID[] bodyUuids,
            @Nonnull UUID[] spaceUuids,
            @Nonnull PhysicsBodyType[] bodyTypes,
            @Nonnull float[] values,
            @Nonnull BitSet sleeping,
            @Nonnull Object2IntOpenHashMap<UUID> bodiesByUuid,
            @Nonnull Int2IntOpenHashMap bodiesByRowIndex) {
            this.sequence = Math.max(0L, sequence);
            this.dt = Float.isFinite(dt) ? Math.max(0.0f, dt) : 0.0f;
            this.bodyRefs = Objects.requireNonNull(bodyRefs, "bodyRefs");
            this.bodyUuids = Objects.requireNonNull(bodyUuids, "bodyUuids");
            this.spaceUuids = Objects.requireNonNull(spaceUuids, "spaceUuids");
            this.bodyTypes = Objects.requireNonNull(bodyTypes, "bodyTypes");
            this.values = Objects.requireNonNull(values, "values");
            this.sleeping = Objects.requireNonNull(sleeping, "sleeping");
            this.bodiesByUuid = Objects.requireNonNull(bodiesByUuid, "bodiesByUuid");
            this.bodiesByRowIndex = Objects.requireNonNull(bodiesByRowIndex, "bodiesByRowIndex");
        }

        @Nonnull
        private static CompactSnapshot empty() {
            return new CompactSnapshot(PhysicsSnapshotFrame.EMPTY.sequence(),
                PhysicsSnapshotFrame.EMPTY.dt(),
                emptyRefs(),
                new UUID[0],
                new UUID[0],
                new PhysicsBodyType[0],
                new float[0],
                new BitSet(0),
                uuidIndex(0),
                rowIndex(0));
        }

        @SuppressWarnings("unchecked")
        @Nonnull
        private static Ref<PhysicsStore>[] emptyRefs() {
            return (Ref<PhysicsStore>[]) new Ref<?>[0];
        }

        @Nonnull
        private static CompactSnapshot fromFrame(@Nonnull PhysicsSnapshotFrame frame) {
            CompactSnapshotBuilder builder = compactBuilder(frame.bodies().size());
            for (PhysicsBodySnapshot body : frame.bodies()) {
                builder.addBody(body.bodyRef(),
                    body.bodyUuid(),
                    body.spaceUuid(),
                    body.bodyType(),
                    body.positionX(),
                    body.positionY(),
                    body.positionZ(),
                    body.rotationX(),
                    body.rotationY(),
                    body.rotationZ(),
                    body.rotationW(),
                    body.linearVelocityX(),
                    body.linearVelocityY(),
                    body.linearVelocityZ(),
                    body.angularVelocityX(),
                    body.angularVelocityY(),
                    body.angularVelocityZ(),
                    body.centerOfMassOffsetY(),
                    body.sleeping());
            }
            return builder.build(frame.sequence(), frame.dt());
        }

        @Nonnull
        private static Object2IntOpenHashMap<UUID> uuidIndex(int expected) {
            Object2IntOpenHashMap<UUID> index = new Object2IntOpenHashMap<>(expected);
            index.defaultReturnValue(-1);
            return index;
        }

        @Nonnull
        private static Int2IntOpenHashMap rowIndex(int expected) {
            Int2IntOpenHashMap index = new Int2IntOpenHashMap(expected);
            index.defaultReturnValue(-1);
            return index;
        }

        @Nonnull
        private CompactSnapshot withFrame(long sequence, float dt) {
            if (bodyCount() == 0
                && sequence == PhysicsSnapshotFrame.EMPTY.sequence()
                && dt == PhysicsSnapshotFrame.EMPTY.dt()) {
                return EMPTY;
            }
            return new CompactSnapshot(sequence,
                dt,
                bodyRefs,
                bodyUuids,
                spaceUuids,
                bodyTypes,
                values,
                sleeping,
                bodiesByUuid,
                bodiesByRowIndex);
        }

        private long sequence() {
            return sequence;
        }

        private float dt() {
            return dt;
        }

        @Nonnull
        private PhysicsSnapshotFrame frame() {
            if (bodyCount() == 0 && sequence == PhysicsSnapshotFrame.EMPTY.sequence()
                && dt == PhysicsSnapshotFrame.EMPTY.dt()) {
                return PhysicsSnapshotFrame.EMPTY;
            }
            List<PhysicsBodySnapshot> bodies = new java.util.ArrayList<>(bodyCount());
            for (int index = 0; index < bodyCount(); index++) {
                bodies.add(body(index));
            }
            return new PhysicsSnapshotFrame(sequence, dt, bodies);
        }

        public int bodyCount() {
            return bodyUuids.length;
        }

        private boolean containsBody(@Nonnull UUID bodyUuid) {
            return bodiesByUuid.getInt(bodyUuid) >= 0;
        }

        @Nullable
        private PhysicsBodySnapshot body(@Nonnull UUID bodyUuid) {
            int index = bodiesByUuid.getInt(bodyUuid);
            return index >= 0 ? body(index) : null;
        }

        @Nullable
        private PhysicsBodySnapshot body(@Nonnull Ref<PhysicsStore> bodyRef) {
            int index = bodiesByRowIndex.get(bodyRef.getIndex());
            if (index < 0 || !sameRef(bodyRefs[index], bodyRef)) {
                return null;
            }
            return body(index);
        }

        @Nonnull
        private PhysicsBodySnapshot body(int index) {
            return PhysicsBodySnapshot.of(bodyRefs[index],
                bodyUuids[index],
                spaceUuids[index],
                bodyTypes[index],
                value(index, POSITION_X),
                value(index, POSITION_Y),
                value(index, POSITION_Z),
                value(index, ROTATION_X),
                value(index, ROTATION_Y),
                value(index, ROTATION_Z),
                value(index, ROTATION_W),
                value(index, LINEAR_VELOCITY_X),
                value(index, LINEAR_VELOCITY_Y),
                value(index, LINEAR_VELOCITY_Z),
                value(index, ANGULAR_VELOCITY_X),
                value(index, ANGULAR_VELOCITY_Y),
                value(index, ANGULAR_VELOCITY_Z),
                value(index, CENTER_OF_MASS_OFFSET_Y),
                sleeping.get(index));
        }

        private void forEachBodyCursor(@Nonnull Consumer<? super BodyCursor> consumer) {
            PublishedBodyCursor cursor = new PublishedBodyCursor(this);
            for (int index = 0; index < bodyCount(); index++) {
                cursor.index = index;
                consumer.accept(cursor);
            }
        }

        private float value(int bodyIndex, int valueIndex) {
            return values[bodyIndex * FLOAT_STRIDE + valueIndex];
        }
    }

    public static final class CompactSnapshotBuilder {

        @Nonnull
        private Ref<PhysicsStore>[] bodyRefs;
        @Nonnull
        private UUID[] bodyUuids;
        @Nonnull
        private UUID[] spaceUuids;
        @Nonnull
        private PhysicsBodyType[] bodyTypes;
        @Nonnull
        private float[] values;
        @Nonnull
        private BitSet sleeping;
        private int size;

        @SuppressWarnings("unchecked")
        private CompactSnapshotBuilder(int expectedBodies) {
            int capacity = Math.max(0, expectedBodies);
            bodyRefs = (Ref<PhysicsStore>[]) new Ref<?>[capacity];
            bodyUuids = new UUID[capacity];
            spaceUuids = new UUID[capacity];
            bodyTypes = new PhysicsBodyType[capacity];
            values = new float[capacity * CompactSnapshot.FLOAT_STRIDE];
            sleeping = new BitSet(capacity);
        }

        public void addBody(@Nullable Ref<PhysicsStore> bodyRef,
            @Nonnull UUID bodyUuid,
            @Nonnull UUID spaceUuid,
            @Nonnull PhysicsBodyType bodyType,
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
            float centerOfMassOffsetY,
            boolean sleeping) {
            ensureCapacity(size + 1);
            int index = size++;
            bodyRefs[index] = bodyRef;
            bodyUuids[index] = Objects.requireNonNull(bodyUuid, "bodyUuid");
            spaceUuids[index] = Objects.requireNonNull(spaceUuid, "spaceUuid");
            bodyTypes[index] = Objects.requireNonNull(bodyType, "bodyType");
            this.sleeping.set(index, sleeping);
            put(index, CompactSnapshot.POSITION_X, positionX);
            put(index, CompactSnapshot.POSITION_Y, positionY);
            put(index, CompactSnapshot.POSITION_Z, positionZ);
            put(index, CompactSnapshot.ROTATION_X, rotationX);
            put(index, CompactSnapshot.ROTATION_Y, rotationY);
            put(index, CompactSnapshot.ROTATION_Z, rotationZ);
            put(index, CompactSnapshot.ROTATION_W, rotationW);
            put(index, CompactSnapshot.LINEAR_VELOCITY_X, linearVelocityX);
            put(index, CompactSnapshot.LINEAR_VELOCITY_Y, linearVelocityY);
            put(index, CompactSnapshot.LINEAR_VELOCITY_Z, linearVelocityZ);
            put(index, CompactSnapshot.ANGULAR_VELOCITY_X, angularVelocityX);
            put(index, CompactSnapshot.ANGULAR_VELOCITY_Y, angularVelocityY);
            put(index, CompactSnapshot.ANGULAR_VELOCITY_Z, angularVelocityZ);
            put(index, CompactSnapshot.CENTER_OF_MASS_OFFSET_Y, centerOfMassOffsetY);
        }

        @Nonnull
        public CompactSnapshot build() {
            return build(PhysicsSnapshotFrame.EMPTY.sequence(), PhysicsSnapshotFrame.EMPTY.dt());
        }

        @Nonnull
        private CompactSnapshot build(long sequence, float dt) {
            if (size == 0) {
                return CompactSnapshot.EMPTY.withFrame(sequence, dt);
            }
            Ref<PhysicsStore>[] builtBodyRefs = trimRefs(bodyRefs, size);
            UUID[] builtBodyUuids = Arrays.copyOf(bodyUuids, size);
            UUID[] builtSpaceUuids = Arrays.copyOf(spaceUuids, size);
            PhysicsBodyType[] builtBodyTypes = Arrays.copyOf(bodyTypes, size);
            float[] builtValues = Arrays.copyOf(values, size * CompactSnapshot.FLOAT_STRIDE);
            BitSet builtSleeping = sleeping.get(0, size);
            Object2IntOpenHashMap<UUID> bodiesByUuid = CompactSnapshot.uuidIndex(size);
            Int2IntOpenHashMap bodiesByRowIndex = CompactSnapshot.rowIndex(size);
            for (int index = 0; index < size; index++) {
                bodiesByUuid.put(builtBodyUuids[index], index);
                Ref<PhysicsStore> bodyRef = builtBodyRefs[index];
                if (bodyRef != null) {
                    bodiesByRowIndex.put(bodyRef.getIndex(), index);
                }
            }
            return new CompactSnapshot(sequence,
                dt,
                builtBodyRefs,
                builtBodyUuids,
                builtSpaceUuids,
                builtBodyTypes,
                builtValues,
                builtSleeping,
                bodiesByUuid,
                bodiesByRowIndex);
        }

        private void put(int bodyIndex, int valueIndex, float value) {
            values[bodyIndex * CompactSnapshot.FLOAT_STRIDE + valueIndex] = value;
        }

        private void ensureCapacity(int required) {
            if (required <= bodyUuids.length) {
                return;
            }
            int nextCapacity = Math.max(required,
                Math.max(2, bodyUuids.length + (bodyUuids.length >> 1)));
            bodyRefs = Arrays.copyOf(bodyRefs, nextCapacity);
            bodyUuids = Arrays.copyOf(bodyUuids, nextCapacity);
            spaceUuids = Arrays.copyOf(spaceUuids, nextCapacity);
            bodyTypes = Arrays.copyOf(bodyTypes, nextCapacity);
            values = Arrays.copyOf(values, nextCapacity * CompactSnapshot.FLOAT_STRIDE);
        }

        @SuppressWarnings("unchecked")
        @Nonnull
        private static Ref<PhysicsStore>[] trimRefs(@Nonnull Ref<PhysicsStore>[] refs, int size) {
            return Arrays.copyOf(refs, size);
        }
    }

    public interface BodyCursor {

        @Nullable
        Ref<PhysicsStore> bodyRef();

        @Nonnull
        UUID bodyUuid();

        @Nonnull
        UUID spaceUuid();

        @Nonnull
        PhysicsBodyType bodyType();

        float positionX();

        float positionY();

        float positionZ();

        float rotationX();

        float rotationY();

        float rotationZ();

        float rotationW();

        float linearVelocityX();

        float linearVelocityY();

        float linearVelocityZ();

        float angularVelocityX();

        float angularVelocityY();

        float angularVelocityZ();

        float centerOfMassOffsetY();

        boolean sleeping();
    }

    private static final class PublishedBodyCursor implements BodyCursor {

        @Nonnull
        private final CompactSnapshot snapshot;
        private int index;

        private PublishedBodyCursor(@Nonnull CompactSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Nullable
        @Override
        public Ref<PhysicsStore> bodyRef() {
            return snapshot.bodyRefs[index];
        }

        @Nonnull
        @Override
        public UUID bodyUuid() {
            return snapshot.bodyUuids[index];
        }

        @Nonnull
        @Override
        public UUID spaceUuid() {
            return snapshot.spaceUuids[index];
        }

        @Nonnull
        @Override
        public PhysicsBodyType bodyType() {
            return snapshot.bodyTypes[index];
        }

        @Override
        public float positionX() {
            return snapshot.value(index, CompactSnapshot.POSITION_X);
        }

        @Override
        public float positionY() {
            return snapshot.value(index, CompactSnapshot.POSITION_Y);
        }

        @Override
        public float positionZ() {
            return snapshot.value(index, CompactSnapshot.POSITION_Z);
        }

        @Override
        public float rotationX() {
            return snapshot.value(index, CompactSnapshot.ROTATION_X);
        }

        @Override
        public float rotationY() {
            return snapshot.value(index, CompactSnapshot.ROTATION_Y);
        }

        @Override
        public float rotationZ() {
            return snapshot.value(index, CompactSnapshot.ROTATION_Z);
        }

        @Override
        public float rotationW() {
            return snapshot.value(index, CompactSnapshot.ROTATION_W);
        }

        @Override
        public float linearVelocityX() {
            return snapshot.value(index, CompactSnapshot.LINEAR_VELOCITY_X);
        }

        @Override
        public float linearVelocityY() {
            return snapshot.value(index, CompactSnapshot.LINEAR_VELOCITY_Y);
        }

        @Override
        public float linearVelocityZ() {
            return snapshot.value(index, CompactSnapshot.LINEAR_VELOCITY_Z);
        }

        @Override
        public float angularVelocityX() {
            return snapshot.value(index, CompactSnapshot.ANGULAR_VELOCITY_X);
        }

        @Override
        public float angularVelocityY() {
            return snapshot.value(index, CompactSnapshot.ANGULAR_VELOCITY_Y);
        }

        @Override
        public float angularVelocityZ() {
            return snapshot.value(index, CompactSnapshot.ANGULAR_VELOCITY_Z);
        }

        @Override
        public float centerOfMassOffsetY() {
            return snapshot.value(index, CompactSnapshot.CENTER_OF_MASS_OFFSET_Y);
        }

        @Override
        public boolean sleeping() {
            return snapshot.sleeping.get(index);
        }
    }

    private static boolean sameRef(@Nullable Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first != null
            && first.getIndex() == second.getIndex()
            && first.getStore() == second.getStore();
    }
}
