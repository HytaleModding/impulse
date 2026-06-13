package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

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
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTargetRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderRequest;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Applies copied boundary requests before backend reconciliation.
 */
public final class RequestDrainSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PersistenceHydrationSystem.class)
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
        for (PhysicsStoreRequest request : requests) {
            if (request instanceof BodyTargetRequest targetRequest) {
                applyTargetRequest(store, identity, restore, targetRequest);
                continue;
            }
            if (request instanceof TerrainColliderRequest terrainRequest) {
                restore.recordSoftSkip("Terrain request authoring is deferred: "
                    + terrainRequest.sourceKey());
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

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
