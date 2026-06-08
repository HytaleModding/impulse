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
import java.util.concurrent.atomic.AtomicIntegerArray;
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
    private static final int BODY_PHYSICS_BODIES = 0;
    private static final int BODY_WITH_TRANSFORM = 1;
    private static final int BODY_WITH_NETWORK_ID = 2;
    private static final int BODY_WITH_VISIBLE = 3;
    private static final int BODY_MATERIALIZED = 4;
    private static final int BODY_COUNTERS = 5;
    private static final int VISUAL_VISUALS = 0;
    private static final int VISUAL_MATERIALIZED = 1;
    private static final int VISUAL_COUNTERS = 2;

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
        AtomicIntegerArray counters = new AtomicIntegerArray(BODY_COUNTERS);
        store.forEachEntityParallel(attachmentType, (index, chunk, _) -> {
            counters.incrementAndGet(BODY_PHYSICS_BODIES);
            boolean transform = chunk.getComponent(index, transformType) != null;
            boolean networkId = chunk.getComponent(index, networkIdType) != null;
            if (transform) {
                counters.incrementAndGet(BODY_WITH_TRANSFORM);
            }
            if (networkId) {
                counters.incrementAndGet(BODY_WITH_NETWORK_ID);
            }
            if (chunk.getComponent(index, visibleType) != null) {
                counters.incrementAndGet(BODY_WITH_VISIBLE);
            }
            if (transform && networkId) {
                counters.incrementAndGet(BODY_MATERIALIZED);
            }
        });
        return new EntityFootprint(counters.get(BODY_PHYSICS_BODIES),
            counters.get(BODY_WITH_TRANSFORM),
            counters.get(BODY_WITH_NETWORK_ID),
            counters.get(BODY_WITH_VISIBLE),
            counters.get(BODY_MATERIALIZED));
    }

    private static VisualFootprint collectVisualFootprint(@Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, PhysicsBodyAttachmentComponent> attachmentType,
        @Nonnull ComponentType<EntityStore, TransformComponent> transformType,
        @Nonnull ComponentType<EntityStore, NetworkId> networkIdType) {
        AtomicIntegerArray counters = new AtomicIntegerArray(VISUAL_COUNTERS);
        store.forEachEntityParallel(attachmentType, (index, chunk, _) -> {
            PhysicsBodyAttachmentComponent attachment = chunk.getComponent(index, attachmentType);
            if (attachment == null || attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                return;
            }
            counters.incrementAndGet(VISUAL_VISUALS);
            if (chunk.getComponent(index, transformType) != null
                && chunk.getComponent(index, networkIdType) != null) {
                counters.incrementAndGet(VISUAL_MATERIALIZED);
            }
        });
        return new VisualFootprint(counters.get(VISUAL_VISUALS),
            counters.get(VISUAL_MATERIALIZED));
    }

    private static int count(@Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, ?> componentType) {
        AtomicInteger entities = new AtomicInteger();
        store.forEachEntityParallel(componentType,
            (_, _, _) -> entities.incrementAndGet());
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
