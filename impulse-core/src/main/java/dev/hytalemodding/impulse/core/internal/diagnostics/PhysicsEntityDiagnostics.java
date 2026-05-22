package dev.hytalemodding.impulse.core.internal.diagnostics;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Counts the Hytale entity-side footprint of Impulse physics.
 */
public final class PhysicsEntityDiagnostics {

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, NetworkId> NETWORK_ID_TYPE =
        NetworkId.getComponentType();
    private static final ComponentType<EntityStore, Visible> VISIBLE_TYPE =
        Visible.getComponentType();
    private static final ComponentType<EntityStore, EntityViewer> ENTITY_VIEWER_TYPE =
        EntityViewer.getComponentType();

    private PhysicsEntityDiagnostics() {
    }

    @Nonnull
    public static Snapshot collect(@Nonnull Store<EntityStore> store) {
        EntityFootprint bodyFootprint = collectBodyFootprint(store,
            ATTACHMENT_TYPE,
            TRANSFORM_TYPE,
            NETWORK_ID_TYPE,
            VISIBLE_TYPE);
        VisualFootprint visualFootprint = collectVisualFootprint(store,
            ATTACHMENT_TYPE,
            TRANSFORM_TYPE,
            NETWORK_ID_TYPE);

        return new Snapshot(bodyFootprint.physicsBodies(),
            0,
            visualFootprint.visuals(),
            count(store, TRANSFORM_TYPE),
            count(store, NETWORK_ID_TYPE),
            count(store, VISIBLE_TYPE),
            count(store, ENTITY_VIEWER_TYPE),
            bodyFootprint.withTransform(),
            bodyFootprint.withNetworkId(),
            bodyFootprint.withVisible(),
            bodyFootprint.materialized(),
            visualFootprint.materialized());
    }

    private static EntityFootprint collectBodyFootprint(@Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, PhysicsBodyAttachmentComponent> attachmentType,
        @Nonnull ComponentType<EntityStore, TransformComponent> transformType,
        @Nonnull ComponentType<EntityStore, NetworkId> networkIdType,
        @Nonnull ComponentType<EntityStore, Visible> visibleType) {
        AtomicInteger physicsBodies = new AtomicInteger();
        AtomicInteger withTransform = new AtomicInteger();
        AtomicInteger withNetworkId = new AtomicInteger();
        AtomicInteger withVisible = new AtomicInteger();
        AtomicInteger materialized = new AtomicInteger();
        store.forEachEntityParallel(attachmentType, (index, chunk, commandBuffer) -> {
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
        @Nonnull ComponentType<EntityStore, PhysicsBodyAttachmentComponent> attachmentType,
        @Nonnull ComponentType<EntityStore, TransformComponent> transformType,
        @Nonnull ComponentType<EntityStore, NetworkId> networkIdType) {
        AtomicInteger visuals = new AtomicInteger();
        AtomicInteger materialized = new AtomicInteger();
        store.forEachEntityParallel(attachmentType, (index, chunk, commandBuffer) -> {
            PhysicsBodyAttachmentComponent attachment = chunk.getComponent(index, attachmentType);
            if (attachment == null || attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                return;
            }
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
