package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource.QueuedRequest;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequestFenceResult;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderPayload;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderRequest;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies copied terrain boundary requests before backend reconciliation.
 */
public final class RequestDrainSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistenceHydrationSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRequestQueueResource queue = store.getResource(
            PhysicsRequestQueueResource.getResourceType());
        List<QueuedRequest> queuedRequests = queue.drain();
        if (queuedRequests.isEmpty()) {
            return;
        }
        List<PhysicsStoreRequest> requests = requests(queuedRequests);
        RequestFenceTracker fences = new RequestFenceTracker(queuedRequests);
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        PhysicsTerrainPayloadResource terrainPayloads = store.getResource(
            PhysicsTerrainPayloadResource.getResourceType());
        Set<UUID> structuralConflicts = structuralConflicts(requests, restore);
        Map<UUID, Ref<PhysicsStore>> refsThisDrain = new Object2ObjectOpenHashMap<>();

        try {
            applyRemovals(store,
                identity,
                terrainPayloads,
                refsThisDrain,
                restore,
                fences,
                structuralConflicts,
                requests);
            applyUpserts(store,
                identity,
                terrainPayloads,
                refsThisDrain,
                restore,
                fences,
                structuralConflicts,
                requests);
            recordUnsupported(restore, fences, requests);
        } catch (RuntimeException | Error exception) {
            fences.failUnfinished();
            queue.completeFences(fences.results(currentServerTick(store)));
            throw exception;
        }
        queue.completeFences(fences.results(currentServerTick(store)));
    }

    private static void applyRemovals(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull RequestFenceTracker fences,
        @Nonnull Set<UUID> structuralConflicts,
        @Nonnull List<PhysicsStoreRequest> requests) {
        for (PhysicsStoreRequest request : requests) {
            if (request instanceof TerrainColliderRequest terrainRequest
                && terrainRequest.remove()) {
                if (structuralConflicts.contains(terrainRequest.terrainColliderUuid())) {
                    fences.rejected(request);
                } else {
                    trackRequest(fences,
                        request,
                        applyTerrainRequest(store,
                            identity,
                            terrainPayloads,
                            refsThisDrain,
                            restore,
                            terrainRequest));
                }
            }
        }
    }

    private static void applyUpserts(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull RequestFenceTracker fences,
        @Nonnull Set<UUID> structuralConflicts,
        @Nonnull List<PhysicsStoreRequest> requests) {
        for (PhysicsStoreRequest request : requests) {
            if (request instanceof TerrainColliderRequest terrainRequest
                && !terrainRequest.remove()) {
                if (structuralConflicts.contains(terrainRequest.terrainColliderUuid())) {
                    fences.rejected(request);
                } else {
                    trackRequest(fences,
                        request,
                        applyTerrainRequest(store,
                            identity,
                            terrainPayloads,
                            refsThisDrain,
                            restore,
                            terrainRequest));
                }
            }
        }
    }

    private static void recordUnsupported(@Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull RequestFenceTracker fences,
        @Nonnull List<PhysicsStoreRequest> requests) {
        for (PhysicsStoreRequest request : requests) {
            if (!isSupportedRequest(request)) {
                restore.recordSoftSkip("Unsupported PhysicsStore request "
                    + request.getClass().getName());
                fences.rejected(request);
            }
        }
    }

    @Nonnull
    private static RequestApplicationStatus applyTerrainRequest(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull TerrainColliderRequest request) {
        UUID terrainUuid = request.terrainColliderUuid();
        Ref<PhysicsStore> ref = refForUuid(identity, refsThisDrain, terrainUuid);
        if (request.remove()) {
            if (ref != null) {
                TerrainColliderComponent existing = store.getComponent(ref,
                    TerrainColliderComponent.getComponentType());
                if (existing != null) {
                    removePayload(terrainPayloads, existing.getPayloadResourceKey());
                }
                PhysicsStoreEntities.putTerrainColliderComponent(store,
                    ref,
                    removedTerrainComponent(request));
            }
            removePayload(terrainPayloads, request.payloadResourceKey());
            return RequestApplicationStatus.APPLIED;
        }
        TerrainColliderPayload payload = request.payload();
        if (payload == null || payload.isEmpty()) {
            restore.recordSoftSkip("Terrain upsert payload is missing: " + request.sourceKey());
            return RequestApplicationStatus.SOFT_SKIPPED;
        }
        terrainPayloads.put(request.payloadResourceKey(), payload);
        TerrainColliderComponent component = activeTerrainComponent(request);
        if (ref != null) {
            TerrainColliderComponent existing = store.getComponent(ref,
                TerrainColliderComponent.getComponentType());
            if (existing != null
                && !existing.getPayloadResourceKey().equals(component.getPayloadResourceKey())) {
                removePayload(terrainPayloads, existing.getPayloadResourceKey());
            }
            PhysicsStoreEntities.putTerrainColliderComponent(store, ref, component);
            refsThisDrain.put(terrainUuid, ref);
            return RequestApplicationStatus.APPLIED;
        }
        refsThisDrain.put(terrainUuid,
            store.addEntity(PhysicsStoreEntities.terrainColliderHolder(store,
                terrainUuid,
                component),
                AddReason.SPAWN));
        return RequestApplicationStatus.APPLIED;
    }

    @Nonnull
    private static Set<UUID> structuralConflicts(
        @Nonnull List<PhysicsStoreRequest> requests,
        @Nonnull PhysicsRestoreStatusResource restore) {
        Map<UUID, String> operationsByUuid = new Object2ObjectOpenHashMap<>();
        Set<UUID> conflicts = new ObjectOpenHashSet<>();
        for (PhysicsStoreRequest request : requests) {
            UUID uuid = structuralUuid(request);
            if (uuid == null) {
                continue;
            }
            String operation = structuralOperation(request);
            String previous = operationsByUuid.putIfAbsent(uuid, operation);
            if (previous != null) {
                conflicts.add(uuid);
            }
        }
        for (UUID conflict : conflicts) {
            restore.recordSoftSkip("Conflicting structural PhysicsStore requests for uuid: "
                + conflict);
        }
        return conflicts;
    }

    @Nullable
    private static UUID structuralUuid(@Nonnull PhysicsStoreRequest request) {
        if (request instanceof TerrainColliderRequest terrainRequest) {
            return terrainRequest.terrainColliderUuid();
        }
        return null;
    }

    @Nonnull
    private static String structuralOperation(@Nonnull PhysicsStoreRequest request) {
        if (request instanceof TerrainColliderRequest terrainRequest) {
            return terrainRequest.remove() ? "terrain-remove" : "terrain-upsert";
        }
        return request.getClass().getName();
    }

    private static boolean isSupportedRequest(@Nonnull PhysicsStoreRequest request) {
        return request instanceof TerrainColliderRequest;
    }

    @Nonnull
    private static List<PhysicsStoreRequest> requests(@Nonnull List<QueuedRequest> queuedRequests) {
        List<PhysicsStoreRequest> requests = new ArrayList<>(queuedRequests.size());
        for (QueuedRequest queuedRequest : queuedRequests) {
            requests.add(queuedRequest.request());
        }
        return requests;
    }

    private static void trackRequest(@Nonnull RequestFenceTracker fences,
        @Nonnull PhysicsStoreRequest request,
        @Nonnull RequestApplicationStatus status) {
        if (status == RequestApplicationStatus.APPLIED) {
            fences.applied(request);
        } else {
            fences.softSkipped(request);
        }
    }

    private static long currentServerTick(@Nonnull Store<PhysicsStore> store) {
        return Math.max(0L, store.getExternalData().getWorld().getTick());
    }

    @Nullable
    private static Ref<PhysicsStore> refForUuid(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull UUID uuid) {
        Ref<PhysicsStore> ref = refsThisDrain.get(uuid);
        if (ref != null && ref.isValid()) {
            return ref;
        }
        return PhysicsStoreSystemSupport.refForUuid(identity, uuid);
    }

    @Nonnull
    private static TerrainColliderComponent activeTerrainComponent(
        @Nonnull TerrainColliderRequest request) {
        return terrainComponent(request, request.payloadResourceKey(), true);
    }

    @Nonnull
    private static TerrainColliderComponent removedTerrainComponent(
        @Nonnull TerrainColliderRequest request) {
        return terrainComponent(request, request.payloadResourceKey(), false);
    }

    @Nonnull
    private static TerrainColliderComponent terrainComponent(@Nonnull TerrainColliderRequest request,
        @Nullable String payloadResourceKey,
        boolean retained) {
        return new TerrainColliderComponent(request.spaceUuid(),
            request.sourceKey(),
            request.chunkX(),
            request.sectionY(),
            request.chunkZ(),
            payloadResourceKey != null ? payloadResourceKey : "",
            retained);
    }

    private static void removePayload(@Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nullable String payloadResourceKey) {
        if (payloadResourceKey != null && !payloadResourceKey.isBlank()) {
            terrainPayloads.remove(payloadResourceKey);
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private enum RequestApplicationStatus {
        APPLIED,
        SOFT_SKIPPED
    }

    private static final class RequestFenceTracker {

        @Nonnull
        private final Map<UUID, FenceCounts> countsByFenceUuid = new Object2ObjectOpenHashMap<>();
        @Nonnull
        private final Map<PhysicsStoreRequest, Deque<FenceCounts>> countsByRequest =
            new IdentityHashMap<>();

        private RequestFenceTracker(@Nonnull List<QueuedRequest> queuedRequests) {
            for (QueuedRequest queuedRequest : queuedRequests) {
                UUID fenceUuid = queuedRequest.fenceUuid();
                if (fenceUuid == null) {
                    continue;
                }
                FenceCounts counts = countsByFenceUuid.computeIfAbsent(fenceUuid,
                    uuid -> new FenceCounts(uuid, queuedRequest.submittedServerTick()));
                counts.accepted++;
                countsByRequest.computeIfAbsent(queuedRequest.request(),
                    _ -> new ArrayDeque<>()).addLast(counts);
            }
        }

        private void applied(@Nonnull PhysicsStoreRequest request) {
            FenceCounts counts = countsFor(request);
            if (counts != null) {
                counts.applied++;
            }
        }

        private void softSkipped(@Nonnull PhysicsStoreRequest request) {
            FenceCounts counts = countsFor(request);
            if (counts != null) {
                counts.softSkipped++;
            }
        }

        private void rejected(@Nonnull PhysicsStoreRequest request) {
            FenceCounts counts = countsFor(request);
            if (counts != null) {
                counts.rejected++;
            }
        }

        @Nullable
        private FenceCounts countsFor(@Nonnull PhysicsStoreRequest request) {
            Deque<FenceCounts> counts = countsByRequest.get(request);
            if (counts == null) {
                return null;
            }
            FenceCounts next = counts.pollFirst();
            if (counts.isEmpty()) {
                countsByRequest.remove(request);
            }
            return next;
        }

        private void failUnfinished() {
            for (FenceCounts counts : countsByFenceUuid.values()) {
                int finished = counts.applied + counts.softSkipped + counts.rejected + counts.failed;
                if (finished < counts.accepted) {
                    counts.failed += counts.accepted - finished;
                }
            }
        }

        @Nonnull
        private Collection<PhysicsStoreRequestFenceResult> results(long consumedServerTick) {
            List<PhysicsStoreRequestFenceResult> results =
                new ArrayList<>(countsByFenceUuid.size());
            for (FenceCounts counts : countsByFenceUuid.values()) {
                results.add(new PhysicsStoreRequestFenceResult(counts.fenceUuid,
                    counts.submittedServerTick,
                    consumedServerTick,
                    counts.accepted,
                    counts.applied,
                    counts.softSkipped,
                    counts.rejected,
                    counts.failed));
            }
            return results;
        }
    }

    private static final class FenceCounts {

        @Nonnull
        private final UUID fenceUuid;
        private final long submittedServerTick;
        private int accepted;
        private int applied;
        private int softSkipped;
        private int rejected;
        private int failed;

        private FenceCounts(@Nonnull UUID fenceUuid, long submittedServerTick) {
            this.fenceUuid = fenceUuid;
            this.submittedServerTick = Math.max(0L, submittedServerTick);
        }
    }
}
