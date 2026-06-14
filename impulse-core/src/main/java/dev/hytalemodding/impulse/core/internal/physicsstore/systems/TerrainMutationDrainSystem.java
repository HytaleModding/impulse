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
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.terrain.TerrainColliderMutation;
import dev.hytalemodding.impulse.core.internal.physicsstore.terrain.TerrainColliderPayload;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies copied terrain mutations before backend reconciliation.
 */
public final class TerrainMutationDrainSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistenceHydrationSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsTerrainMutationQueueResource queue = store.getResource(
            PhysicsTerrainMutationQueueResource.getResourceType());
        List<TerrainColliderMutation> mutations = queue.drain();
        if (mutations.isEmpty()) {
            return;
        }
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        PhysicsTerrainPayloadResource terrainPayloads = store.getResource(
            PhysicsTerrainPayloadResource.getResourceType());
        Map<UUID, Ref<PhysicsStore>> refsThisDrain = new Object2ObjectOpenHashMap<>();

        applyRemovals(store,
            identity,
            terrainPayloads,
            refsThisDrain,
            restore,
            mutations);
        applyUpserts(store,
            identity,
            terrainPayloads,
            refsThisDrain,
            restore,
            mutations);
    }

    private static void applyRemovals(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<TerrainColliderMutation> mutations) {
        for (TerrainColliderMutation mutation : mutations) {
            if (mutation.remove()) {
                applyTerrainMutation(store,
                    identity,
                    terrainPayloads,
                    refsThisDrain,
                    restore,
                    mutation);
            }
        }
    }

    private static void applyUpserts(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<TerrainColliderMutation> mutations) {
        for (TerrainColliderMutation mutation : mutations) {
            if (!mutation.remove()) {
                applyTerrainMutation(store,
                    identity,
                    terrainPayloads,
                    refsThisDrain,
                    restore,
                    mutation);
            }
        }
    }

    private static void applyTerrainMutation(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull TerrainColliderMutation mutation) {
        UUID terrainUuid = mutation.terrainColliderUuid();
        Ref<PhysicsStore> ref = refForUuid(identity, refsThisDrain, terrainUuid);
        if (mutation.remove()) {
            if (ref != null) {
                TerrainColliderComponent existing = store.getComponent(ref,
                    TerrainColliderComponent.getComponentType());
                if (existing != null) {
                    removePayload(terrainPayloads, existing.getPayloadResourceKey());
                }
                PhysicsStoreEntities.putTerrainColliderComponent(store,
                    ref,
                    removedTerrainComponent(mutation));
            }
            removePayload(terrainPayloads, mutation.payloadResourceKey());
            return;
        }
        TerrainColliderPayload payload = mutation.payload();
        if (payload == null || payload.isEmpty()) {
            restore.recordSoftSkip("Terrain upsert payload is missing: " + mutation.sourceKey());
            return;
        }
        terrainPayloads.put(mutation.payloadResourceKey(), payload);
        TerrainColliderComponent component = activeTerrainComponent(mutation);
        if (ref != null) {
            TerrainColliderComponent existing = store.getComponent(ref,
                TerrainColliderComponent.getComponentType());
            if (existing != null
                && !existing.getPayloadResourceKey().equals(component.getPayloadResourceKey())) {
                removePayload(terrainPayloads, existing.getPayloadResourceKey());
            }
            PhysicsStoreEntities.putTerrainColliderComponent(store, ref, component);
            refsThisDrain.put(terrainUuid, ref);
            return;
        }
        refsThisDrain.put(terrainUuid,
            store.addEntity(PhysicsStoreEntities.terrainColliderHolder(store,
                terrainUuid,
                component),
                AddReason.SPAWN));
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
        @Nonnull TerrainColliderMutation mutation) {
        return terrainComponent(mutation, mutation.payloadResourceKey(), true);
    }

    @Nonnull
    private static TerrainColliderComponent removedTerrainComponent(
        @Nonnull TerrainColliderMutation mutation) {
        return terrainComponent(mutation, mutation.payloadResourceKey(), false);
    }

    @Nonnull
    private static TerrainColliderComponent terrainComponent(
        @Nonnull TerrainColliderMutation mutation,
        @Nullable String payloadResourceKey,
        boolean retained) {
        return new TerrainColliderComponent(mutation.spaceUuid(),
            mutation.sourceKey(),
            mutation.chunkX(),
            mutation.sectionY(),
            mutation.chunkZ(),
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
