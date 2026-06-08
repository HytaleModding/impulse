package dev.hytalemodding.impulse.core.internal.resources.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.Getter;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Transient per-entity sync state that is not part of body identity or persistence.
 */
public final class PhysicsBodyRuntimeState {

    private final Map<Ref<EntityStore>, BodySyncState> bodySyncStates =
        new Reference2ObjectOpenHashMap<>();

    @Nonnull
    public synchronized BodySyncState getOrCreateBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return bodySyncStates.computeIfAbsent(entityRef, _ -> new BodySyncState());
    }

    @Nullable
    public synchronized BodySyncState getBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return bodySyncStates.get(entityRef);
    }

    public synchronized void clearBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        bodySyncStates.remove(entityRef);
    }

    public synchronized void clear() {
        bodySyncStates.clear();
    }

    public static final class BodySyncState {

        @Nonnull
        private final Vector3f lastSyncedPosition = new Vector3f();
        @Nonnull
        private final Quaternionf lastSyncedRotation = new Quaternionf();
        @Nonnull
        private final Vector3f lastObservedSnapshotPosition = new Vector3f();
        @Getter
        private boolean initialized;
        @Getter
        private boolean sleeping;
        @Getter
        private boolean snapshotObserved;
        @Getter
        private float secondsSinceSync;

        public void recordSync(@Nonnull Vector3f position,
            @Nonnull Quaternionf rotation,
            boolean sleeping) {
            lastSyncedPosition.set(position);
            lastSyncedRotation.set(rotation);
            initialized = true;
            this.sleeping = sleeping;
            secondsSinceSync = 0.0f;
        }

        public float recordSnapshotObservation(@Nonnull Vector3f position) {
            if (!snapshotObserved) {
                lastObservedSnapshotPosition.set(position);
                snapshotObserved = true;
                return Float.NaN;
            }
            float distance = lastObservedSnapshotPosition.distance(position);
            lastObservedSnapshotPosition.set(position);
            return distance;
        }

        public void recordSkip(float dt) {
            secondsSinceSync += Math.max(dt, 0.0f);
        }

        @Nonnull
        public Vector3f getLastSyncedPosition() {
            return lastSyncedPosition;
        }

        @Nonnull
        public Quaternionf getLastSyncedRotation() {
            return lastSyncedRotation;
        }

        @Nonnull
        public Vector3f getLastObservedSnapshotPosition() {
            return lastObservedSnapshotPosition;
        }

    }
}
