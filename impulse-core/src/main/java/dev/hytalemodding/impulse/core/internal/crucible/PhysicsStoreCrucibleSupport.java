package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStoreReadQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.physicsstore.BodyRowDescriptor;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodyRows;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Internal Crucible helpers for authoring and clearing live PhysicsStore rows.
 */
final class PhysicsStoreCrucibleSupport {

    private PhysicsStoreCrucibleSupport() {
    }

    @Nonnull
    static Store<PhysicsStore> physicsStore(@Nonnull World world) {
        return ((PhysicsStoreWorld) world).getPhysicsStore().getStore();
    }

    static void clearAll(@Nonnull Store<PhysicsStore> store) {
        PhysicsStoreThreading.requireWorldThread(store, "clear Crucible PhysicsStore rows");
        store.forEachEntityParallel(UuidComponent.getComponentType(),
            (index, chunk, commandBuffer) -> commandBuffer.removeEntity(
                chunk.getReferenceTo(index),
                RemoveReason.REMOVE));
        store.getResource(PhysicsTerrainMutationQueueResource.getResourceType()).clear();
        store.getResource(PhysicsRuntimeResource.getResourceType()).destroyBackendBindings();
        store.getResource(PhysicsIdentityIndexResource.getResourceType()).clear();
        store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType()).clear();
        store.getResource(PhysicsSnapshotResource.getResourceType()).clear();
        store.getResource(PhysicsStoreReadQueueResource.getResourceType()).clear();
        store.getResource(PhysicsTerrainPayloadResource.getResourceType()).clear();
        store.getResource(PhysicsWorldCollisionIndexResource.getResourceType()).clear();
    }

    @Nonnull
    static Ref<PhysicsStore> addBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsStoreThreading.requireWorldThread(store, "add Crucible PhysicsStore body row");
        BodyRowDescriptor row = PhysicsBodyRows.body(
            PhysicsStoreSpaceMutations.requireSpaceUuid(store, spaceId),
            bodyUuid,
            bodyCenter,
            shape,
            bodyType,
            mass,
            settings,
            linearVelocity,
            kind,
            persistenceMode);
        return store.addEntity(PhysicsStoreEntities.bodyHolder(store,
            row.bodyUuid(),
            row.body(),
            row.dynamics(),
            row.target(),
            row.collider(),
            row.shape(),
            row.material(),
            row.filter()), AddReason.SPAWN);
    }
}
