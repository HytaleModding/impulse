package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsBodySnapshotCursor;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Cached body snapshots and spatial lookup state for world-level readers.
 */
public final class PhysicsBodySnapshotStore {

    private final Map<RigidBodyKey, PhysicsBodySnapshot> snapshots =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Object2LongOpenHashMap<RigidBodyKey> livenessMarks =
        new Object2LongOpenHashMap<>();
    private final PhysicsBodySpatialIndex spatialIndex = new PhysicsBodySpatialIndex();
    private long livenessGeneration;

    public record ApplyStats(int applied, int inserted, int removed) {
    }

    public int refresh(@Nonnull Iterable<PhysicsSpaceBinding> spaces, @Nonnull PhysicsBodyRegistry bodyRegistry) {
        long generation = nextLivenessGeneration();
        MutableInt liveBodies = new MutableInt();
        for (PhysicsSpaceBinding space : spaces) {
            SpaceId spaceId = space.spaceId();
            if (bodyRegistry.getRegistrationCount(spaceId) == 0) {
                continue;
            }

            Iterator<PhysicsBodyRegistration> registrations = bodyRegistry.registrationIterator(spaceId);
            while (registrations.hasNext()) {
                PhysicsBodyRegistration registration = registrations.next();
                PhysicsBodySnapshot snapshot = PhysicsBodySnapshots.read(space, registration.backendBodyId());
                if (snapshot == null) {
                    continue;
                }
                RigidBodyKey bodyKey = registration.id();
                markLive(bodyKey, generation, liveBodies);
                PhysicsBodySnapshot previous = snapshots.get(bodyKey);
                if (snapshot != previous) {
                    snapshots.put(bodyKey, snapshot);
                }
                spatialIndex.update(bodyKey,
                    snapshot,
                    spaceId,
                    registration.kind(),
                    registration.persistenceMode());
            }
        }

        retainMarked(generation);
        return liveBodies.value();
    }

    @Nonnull
    public ApplyStats applyPublishedFrame(@Nonnull PublishedPhysicsSnapshotFrame frame,
        @Nonnull PhysicsBodyRegistry bodyRegistry) {
        PublishedFrameApplier applier = new PublishedFrameApplier(nextLivenessGeneration());
        frame.forEachBodyCursor(applier);
        return new ApplyStats(applier.applied(), applier.inserted(), retainMarked(applier.generation()));
    }

    public void put(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        snapshots.put(bodyKey, snapshot);
        livenessMarks.put(bodyKey, livenessGeneration);
        spatialIndex.update(bodyKey, snapshot, spaceId, kind, persistenceMode);
    }

    @Nullable
    public PhysicsBodySnapshot get(@Nonnull RigidBodyKey bodyKey) {
        return snapshots.get(bodyKey);
    }

    public void remove(@Nonnull RigidBodyKey bodyKey) {
        snapshots.remove(bodyKey);
        livenessMarks.removeLong(bodyKey);
        spatialIndex.remove(bodyKey);
    }

    public void clear() {
        snapshots.clear();
        livenessMarks.clear();
        spatialIndex.clear();
    }

    public int bodyCount() {
        return spatialIndex.bodyCount();
    }

    public int bodyCount(@Nonnull SpaceId spaceId) {
        return spatialIndex.bodyCount(spaceId);
    }

    public int cellCount() {
        return spatialIndex.cellCount();
    }

    public void forEach(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        spatialIndex.forEach(spaceId, consumer);
    }

    public void forEachIndexed(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        spatialIndex.forEachIndexed(spaceId, visitor);
    }

    public int forEachNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        return spatialIndex.forEachNear(spaceId, center, radius, consumer);
    }

    public int forEachIndexedNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        return spatialIndex.forEachIndexedNear(spaceId, center, radius, visitor);
    }

    private long nextLivenessGeneration() {
        livenessGeneration++;
        if (livenessGeneration == 0L) {
            livenessGeneration = 1L;
            livenessMarks.clear();
        }
        return livenessGeneration;
    }

    private void markLive(@Nonnull RigidBodyKey bodyKey,
        long generation,
        @Nonnull MutableInt liveBodies) {
        if (livenessMarks.put(bodyKey, generation) != generation) {
            liveBodies.increment();
        }
    }

    private int retainMarked(long generation) {
        int removed = 0;
        Iterator<RigidBodyKey> iterator = snapshots.keySet().iterator();
        while (iterator.hasNext()) {
            RigidBodyKey bodyKey = iterator.next();
            if (livenessMarks.getLong(bodyKey) != generation) {
                iterator.remove();
                livenessMarks.removeLong(bodyKey);
                spatialIndex.remove(bodyKey);
                removed++;
            }
        }
        return removed;
    }

    private final class PublishedFrameApplier implements Consumer<PublishedPhysicsBodySnapshotCursor> {

        private final long generation;
        private final MutableInt liveBodies = new MutableInt();
        private int applied;
        private int inserted;

        private PublishedFrameApplier(long generation) {
            this.generation = generation;
        }

        @Override
        public void accept(@Nonnull PublishedPhysicsBodySnapshotCursor bodyFrame) {
            RigidBodyKey bodyKey = bodyFrame.bodyKey();
            markLive(bodyKey, generation, liveBodies);
            PhysicsBodySnapshot snapshot = snapshots.get(bodyKey);
            if (snapshot == null) {
                inserted++;
                snapshot = bodyFrame.toBodySnapshot();
                snapshots.put(bodyKey, snapshot);
            } else if (!bodyFrame.matchesSnapshot(snapshot)) {
                snapshot = bodyFrame.toBodySnapshot();
                snapshots.put(bodyKey, snapshot);
            } else {
                applied++;
                return;
            }
            spatialIndex.update(bodyKey,
                snapshot,
                bodyFrame.spaceId(),
                bodyFrame.kind(),
                bodyFrame.persistenceMode());
            applied++;
        }

        private int applied() {
            return applied;
        }

        private int inserted() {
            return inserted;
        }

        private long generation() {
            return generation;
        }
    }

    private static final class MutableInt {

        private int value;

        private void increment() {
            value++;
        }

        private int value() {
            return value;
        }
    }
}
