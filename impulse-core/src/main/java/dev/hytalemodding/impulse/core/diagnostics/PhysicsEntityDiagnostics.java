package dev.hytalemodding.impulse.core.diagnostics;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyVisualComponent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Counts the Hytale entity-side footprint of Impulse physics.
 */
public final class PhysicsEntityDiagnostics {

    private PhysicsEntityDiagnostics() {
    }

    @Nonnull
    public static Snapshot collect(@Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, PhysicsBodyComponent> physicsBodyType =
            PhysicsBodyComponent.getComponentType();
        ComponentType<EntityStore, PersistentPhysicsBodyComponent> persistentBodyType =
            PersistentPhysicsBodyComponent.getComponentType();
        ComponentType<EntityStore, PhysicsBodyVisualComponent> visualType =
            PhysicsBodyVisualComponent.getComponentType();
        ComponentType<EntityStore, TransformComponent> transformType =
            TransformComponent.getComponentType();
        ComponentType<EntityStore, NetworkId> networkIdType = NetworkId.getComponentType();
        ComponentType<EntityStore, Visible> visibleType = Visible.getComponentType();
        ComponentType<EntityStore, EntityViewer> viewerType = EntityViewer.getComponentType();

        EntityFootprint bodyFootprint = collectBodyFootprint(store,
            physicsBodyType,
            transformType,
            networkIdType,
            visibleType);
        VisualFootprint visualFootprint = collectVisualFootprint(store,
            visualType,
            transformType,
            networkIdType);

        return new Snapshot(bodyFootprint.physicsBodies(),
            count(store, persistentBodyType),
            visualFootprint.visuals(),
            count(store, transformType),
            count(store, networkIdType),
            count(store, visibleType),
            count(store, viewerType),
            bodyFootprint.withTransform(),
            bodyFootprint.withNetworkId(),
            bodyFootprint.withVisible(),
            bodyFootprint.materialized(),
            visualFootprint.materialized());
    }

    private static EntityFootprint collectBodyFootprint(@Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, PhysicsBodyComponent> physicsBodyType,
        @Nonnull ComponentType<EntityStore, TransformComponent> transformType,
        @Nonnull ComponentType<EntityStore, NetworkId> networkIdType,
        @Nonnull ComponentType<EntityStore, Visible> visibleType) {
        AtomicInteger physicsBodies = new AtomicInteger();
        AtomicInteger withTransform = new AtomicInteger();
        AtomicInteger withNetworkId = new AtomicInteger();
        AtomicInteger withVisible = new AtomicInteger();
        AtomicInteger materialized = new AtomicInteger();
        store.forEachEntityParallel(physicsBodyType, (index, chunk, commandBuffer) -> {
            physicsBodies.incrementAndGet();
            boolean transform = chunk.getComponent(index, transformType) != null;
            boolean networkId = chunk.getComponent(index, networkIdType) != null;
            if (transform) {
                withTransform.incrementAndGet();
            }
            if (networkId) {
                withNetworkId.incrementAndGet();
            }
            if (chunk.getComponent(index, visibleType) != null) {
                withVisible.incrementAndGet();
            }
            if (transform && networkId) {
                materialized.incrementAndGet();
            }
        });
        return new EntityFootprint(physicsBodies.get(),
            withTransform.get(),
            withNetworkId.get(),
            withVisible.get(),
            materialized.get());
    }

    private static VisualFootprint collectVisualFootprint(@Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, PhysicsBodyVisualComponent> visualType,
        @Nonnull ComponentType<EntityStore, TransformComponent> transformType,
        @Nonnull ComponentType<EntityStore, NetworkId> networkIdType) {
        AtomicInteger visuals = new AtomicInteger();
        AtomicInteger materialized = new AtomicInteger();
        store.forEachEntityParallel(visualType, (index, chunk, commandBuffer) -> {
            visuals.incrementAndGet();
            if (chunk.getComponent(index, transformType) != null
                && chunk.getComponent(index, networkIdType) != null) {
                materialized.incrementAndGet();
            }
        });
        return new VisualFootprint(visuals.get(), materialized.get());
    }

    private static int count(@Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, ?> componentType) {
        AtomicInteger entities = new AtomicInteger();
        store.forEachEntityParallel(componentType,
            (index, chunk, commandBuffer) -> entities.incrementAndGet());
        return entities.get();
    }

    private record EntityFootprint(int physicsBodies,
        int withTransform,
        int withNetworkId,
        int withVisible,
        int materialized) {
    }

    private record VisualFootprint(int visuals, int materialized) {
    }

    public record Snapshot(int physicsBodyEntities,
        int persistentPhysicsBodyEntities,
        int physicsVisualEntities,
        int transformEntities,
        int networkIdEntities,
        int visibleEntities,
        int entityViewers,
        int physicsBodyWithTransform,
        int physicsBodyWithNetworkId,
        int physicsBodyWithVisible,
        int physicsBodyMaterialized,
        int physicsVisualMaterialized) {

        @Nonnull
        public String hytaleSummary() {
            return "transformEntities=" + transformEntities
                + " networkIdEntities=" + networkIdEntities
                + " visibleEntities=" + visibleEntities
                + " entityViewers=" + entityViewers;
        }

        @Nonnull
        public String impulseSummary() {
            return "physicsBodies=" + physicsBodyEntities
                + " persistentBodies=" + persistentPhysicsBodyEntities
                + " visualFollowers=" + physicsVisualEntities
                + " bodyTransform=" + physicsBodyWithTransform
                + " bodyNetworkId=" + physicsBodyWithNetworkId
                + " bodyVisible=" + physicsBodyWithVisible
                + " bodyMaterialized=" + physicsBodyMaterialized
                + " visualMaterialized=" + physicsVisualMaterialized;
        }
    }
}
