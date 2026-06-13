package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTargetRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderPayload;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderRequest;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies copied boundary requests before backend reconciliation.
 */
public final class RequestDrainSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistenceHydrationSystem.class),
        new SystemDependency<>(Order.BEFORE, IdentityIndexSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRequestQueueResource queue = store.getResource(
            PhysicsRequestQueueResource.getResourceType());
        List<PhysicsStoreRequest> requests = queue.drain();
        if (requests.isEmpty()) {
            return;
        }
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        PhysicsTerrainPayloadResource terrainPayloads = store.getResource(
            PhysicsTerrainPayloadResource.getResourceType());
        Map<UUID, Ref<PhysicsStore>> terrainRefsThisDrain = new Object2ObjectOpenHashMap<>();
        for (PhysicsStoreRequest request : requests) {
            if (request instanceof BodyTargetRequest targetRequest) {
                applyTargetRequest(store, identity, restore, targetRequest);
                continue;
            }
            if (request instanceof TerrainColliderRequest terrainRequest) {
                applyTerrainRequest(store,
                    identity,
                    terrainPayloads,
                    terrainRefsThisDrain,
                    restore,
                    terrainRequest);
                continue;
            }
            restore.recordSoftSkip("Unsupported PhysicsStore request "
                + request.getClass().getName());
        }
    }

    private static void applyTargetRequest(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull BodyTargetRequest request) {
        Ref<PhysicsStore> bodyRef = PhysicsStoreSystemSupport.refForUuid(identity,
            request.bodyUuid());
        if (bodyRef == null) {
            restore.recordSoftSkip("Target request body is missing: " + request.bodyUuid());
            return;
        }
        TargetComponent target = new TargetComponent();
        target.setActive(true);
        target.setPosition(request.position());
        target.setRotation(request.rotation());
        target.setLinearVelocity(request.linearVelocity());
        target.setAngularVelocity(request.angularVelocity());
        store.putComponent(bodyRef, TargetComponent.getComponentType(), target);
    }

    private static void applyTerrainRequest(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> terrainRefsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull TerrainColliderRequest request) {
        UUID terrainUuid = request.terrainColliderUuid();
        Ref<PhysicsStore> ref = terrainRefsThisDrain.get(terrainUuid);
        if (ref == null) {
            ref = PhysicsStoreSystemSupport.refForUuid(identity, terrainUuid);
        }
        if (request.remove()) {
            if (ref != null) {
                TerrainColliderComponent existing = store.getComponent(ref,
                    TerrainColliderComponent.getComponentType());
                if (existing != null) {
                    removePayload(terrainPayloads, existing.getPayloadResourceKey());
                }
                store.putComponent(ref,
                    TerrainColliderComponent.getComponentType(),
                    removedTerrainComponent(request));
            }
            removePayload(terrainPayloads, request.payloadResourceKey());
            return;
        }
        TerrainColliderPayload payload = request.payload();
        if (payload == null || payload.isEmpty()) {
            restore.recordSoftSkip("Terrain upsert payload is missing: " + request.sourceKey());
            return;
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
            store.putComponent(ref, TerrainColliderComponent.getComponentType(), component);
            terrainRefsThisDrain.put(terrainUuid, ref);
            return;
        }
        Holder<PhysicsStore> holder = store.getRegistry().newHolder();
        holder.addComponent(UuidComponent.getComponentType(), new UuidComponent(terrainUuid));
        holder.addComponent(TerrainColliderComponent.getComponentType(), component);
        terrainRefsThisDrain.put(terrainUuid, store.addEntity(holder, AddReason.SPAWN));
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
}
