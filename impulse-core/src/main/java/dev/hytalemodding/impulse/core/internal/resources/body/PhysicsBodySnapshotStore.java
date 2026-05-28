package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Cached body snapshots and spatial lookup state for world-level readers.
 */
public final class PhysicsBodySnapshotStore {

    private final Map<PhysicsBodyId, PhysicsBodySnapshot> snapshots =
        new Object2ObjectLinkedOpenHashMap<>();
    private final PhysicsBodySpatialIndex spatialIndex = new PhysicsBodySpatialIndex();

    public int refresh(@Nonnull Iterable<PhysicsSpace> spaces, @Nonnull PhysicsBodyRegistry bodyRegistry) {
        Set<PhysicsBodyId> liveBodies = new ObjectOpenHashSet<>(bodyRegistry.getRegistrationCount());
        ObjectArrayList<PhysicsBody> registeredBodies = new ObjectArrayList<>(bodyRegistry.getRegistrationCount());
        for (PhysicsSpace space : spaces) {
            registeredBodies.clear();
            SpaceId spaceId = space.getId();
            bodyRegistry.forEachRegistration(registration -> {
                if (registration.spaceId().equals(spaceId)
                    && space.containsBody(registration.body())) {
                    registeredBodies.add(registration.body());
                }
            });
            if (registeredBodies.isEmpty()) {
                continue;
            }

            space.snapshotBodies(registeredBodies, body -> {
                PhysicsBodyRegistration registration = bodyRegistry.getRegistration(body);
                return registration != null ? snapshots.get(registration.id()) : null;
            }, (body, snapshot) -> {
                PhysicsBodyRegistration registration = bodyRegistry.getRegistration(body);
                if (registration == null || !registration.spaceId().equals(spaceId)) {
                    return;
                }

                PhysicsBodyId bodyId = registration.id();
                liveBodies.add(bodyId);
                PhysicsBodySnapshot previous = snapshots.get(bodyId);
                if (snapshot != previous) {
                    snapshots.put(bodyId, snapshot);
                }
                spatialIndex.update(bodyId,
                    snapshot,
                    spaceId,
                    registration.kind(),
                    registration.persistenceMode());
            });
        }

        spatialIndex.retainOnly(liveBodies);
        snapshots.keySet().removeIf(bodyId -> !liveBodies.contains(bodyId));
        return liveBodies.size();
    }

    public void put(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        snapshots.put(bodyId, snapshot);
        spatialIndex.update(bodyId, snapshot, spaceId, kind, persistenceMode);
    }

    @Nullable
    public PhysicsBodySnapshot get(@Nonnull PhysicsBodyId bodyId) {
        return snapshots.get(bodyId);
    }

    public void remove(@Nonnull PhysicsBodyId bodyId) {
        snapshots.remove(bodyId);
        spatialIndex.remove(bodyId);
    }

    public void clear() {
        snapshots.clear();
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

    public int forEachNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        return spatialIndex.forEachNear(spaceId, center, radius, consumer);
    }
}
