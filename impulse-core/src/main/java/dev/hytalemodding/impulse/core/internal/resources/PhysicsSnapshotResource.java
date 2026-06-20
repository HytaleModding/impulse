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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Latest copied PhysicsStore snapshot frame for projection, debug, and queries.
 */
public final class PhysicsSnapshotResource implements Resource<PhysicsStore> {

    @Nonnull
    private volatile PublishedSnapshot snapshot = PublishedSnapshot.EMPTY;

    public PhysicsSnapshotResource() {
    }

    @Nonnull
    public PhysicsSnapshotFrame getLatestFrame() {
        return snapshot.frame();
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
        snapshot = PublishedSnapshot.fromFrame(Objects.requireNonNull(frame, "frame"));
    }

    public void removeBody(@Nonnull UUID bodyUuid) {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        removeBodies(List.of(bodyUuid));
    }

    public void removeBodies(@Nonnull Collection<UUID> bodyUuids) {
        Objects.requireNonNull(bodyUuids, "bodyUuids");
        PublishedSnapshot current = snapshot;
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
        snapshot = PublishedSnapshot.EMPTY;
    }

    @Nonnull
    private static PublishedSnapshot withoutBodies(@Nonnull PublishedSnapshot current,
        @Nonnull ObjectOpenHashSet<UUID> bodyUuids) {
        int bodyCount = Math.max(0, current.bodyCount() - bodyUuids.size());
        List<PhysicsBodySnapshot> bodies = new ArrayList<>(bodyCount);
        for (int index = 0; index < current.bodyCount(); index++) {
            PhysicsBodySnapshot body = current.body(index);
            if (bodyUuids.contains(body.bodyUuid())) {
                continue;
            }
            bodies.add(body);
        }
        return PublishedSnapshot.fromFrame(new PhysicsSnapshotFrame(current.sequence(),
            current.dt(),
            bodies));
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

    private record PublishedSnapshot(long sequence,
                                     float dt,
                                     @Nonnull Ref<PhysicsStore>[] bodyRefs,
                                     @Nonnull UUID[] bodyUuids,
                                     @Nonnull UUID[] spaceUuids,
                                     @Nonnull PhysicsBodyType[] bodyTypes,
                                     @Nonnull float[] values,
                                     @Nonnull boolean[] sleeping,
                                     @Nonnull Object2IntOpenHashMap<UUID> bodiesByUuid,
                                     @Nonnull Int2IntOpenHashMap bodiesByRowIndex) {

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

        private static final PublishedSnapshot EMPTY = empty();

        @Nonnull
        private static PublishedSnapshot empty() {
            return new PublishedSnapshot(PhysicsSnapshotFrame.EMPTY.sequence(),
                PhysicsSnapshotFrame.EMPTY.dt(),
                emptyRefs(),
                new UUID[0],
                new UUID[0],
                new PhysicsBodyType[0],
                new float[0],
                new boolean[0],
                uuidIndex(0),
                rowIndex(0));
        }

        @SuppressWarnings("unchecked")
        @Nonnull
        private static Ref<PhysicsStore>[] emptyRefs() {
            return (Ref<PhysicsStore>[]) new Ref<?>[0];
        }

        @SuppressWarnings("unchecked")
        @Nonnull
        private static PublishedSnapshot fromFrame(@Nonnull PhysicsSnapshotFrame frame) {
            int bodyCount = frame.bodies().size();
            Ref<PhysicsStore>[] bodyRefs = (Ref<PhysicsStore>[]) new Ref<?>[bodyCount];
            UUID[] bodyUuids = new UUID[bodyCount];
            UUID[] spaceUuids = new UUID[bodyCount];
            PhysicsBodyType[] bodyTypes = new PhysicsBodyType[bodyCount];
            float[] values = new float[bodyCount * FLOAT_STRIDE];
            boolean[] sleeping = new boolean[bodyCount];
            Object2IntOpenHashMap<UUID> bodiesByUuid = uuidIndex(bodyCount);
            Int2IntOpenHashMap bodiesByRowIndex = rowIndex(bodyCount);
            for (int index = 0; index < bodyCount; index++) {
                PhysicsBodySnapshot body = frame.bodies().get(index);
                bodyRefs[index] = body.bodyRef();
                bodyUuids[index] = body.bodyUuid();
                spaceUuids[index] = body.spaceUuid();
                bodyTypes[index] = body.bodyType();
                sleeping[index] = body.sleeping();
                values[index * FLOAT_STRIDE + POSITION_X] = body.positionX();
                values[index * FLOAT_STRIDE + POSITION_Y] = body.positionY();
                values[index * FLOAT_STRIDE + POSITION_Z] = body.positionZ();
                values[index * FLOAT_STRIDE + ROTATION_X] = body.rotationX();
                values[index * FLOAT_STRIDE + ROTATION_Y] = body.rotationY();
                values[index * FLOAT_STRIDE + ROTATION_Z] = body.rotationZ();
                values[index * FLOAT_STRIDE + ROTATION_W] = body.rotationW();
                values[index * FLOAT_STRIDE + LINEAR_VELOCITY_X] = body.linearVelocityX();
                values[index * FLOAT_STRIDE + LINEAR_VELOCITY_Y] = body.linearVelocityY();
                values[index * FLOAT_STRIDE + LINEAR_VELOCITY_Z] = body.linearVelocityZ();
                values[index * FLOAT_STRIDE + ANGULAR_VELOCITY_X] = body.angularVelocityX();
                values[index * FLOAT_STRIDE + ANGULAR_VELOCITY_Y] = body.angularVelocityY();
                values[index * FLOAT_STRIDE + ANGULAR_VELOCITY_Z] = body.angularVelocityZ();
                values[index * FLOAT_STRIDE + CENTER_OF_MASS_OFFSET_Y] =
                    body.centerOfMassOffsetY();
                bodiesByUuid.put(body.bodyUuid(), index);
                Ref<PhysicsStore> bodyRef = body.bodyRef();
                if (bodyRef != null) {
                    bodiesByRowIndex.put(bodyRef.getIndex(), index);
                }
            }
            return new PublishedSnapshot(frame.sequence(),
                frame.dt(),
                bodyRefs,
                bodyUuids,
                spaceUuids,
                bodyTypes,
                values,
                sleeping,
                bodiesByUuid,
                bodiesByRowIndex);
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
        private PhysicsSnapshotFrame frame() {
            if (bodyCount() == 0 && sequence == PhysicsSnapshotFrame.EMPTY.sequence()
                && dt == PhysicsSnapshotFrame.EMPTY.dt()) {
                return PhysicsSnapshotFrame.EMPTY;
            }
            List<PhysicsBodySnapshot> bodies = new ArrayList<>(bodyCount());
            for (int index = 0; index < bodyCount(); index++) {
                bodies.add(body(index));
            }
            return new PhysicsSnapshotFrame(sequence, dt, bodies);
        }

        private int bodyCount() {
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
                sleeping[index]);
        }

        private float value(int bodyIndex, int valueIndex) {
            return values[bodyIndex * FLOAT_STRIDE + valueIndex];
        }
    }

    private static boolean sameRef(@Nullable Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first != null
            && first.getIndex() == second.getIndex()
            && first.getStore() == second.getStore();
    }
}
