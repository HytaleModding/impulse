package dev.hytalemodding.impulse.core.internal.modules.physicsentity;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityAttachments;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicIntegerArray;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Module-owned cleanup policy for EntityStore physics projections.
 */
public final class PhysicsEntityProjectionCleanup {

    private static final int REMOVED_ATTACHMENT_ENTITIES = 0;
    private static final int DETACHED_EXTERNAL_ATTACHMENTS = 1;
    private static final int REMOVED_ORPHAN_VISUAL_ENTITIES = 2;
    private static final int COUNTERS = 3;

    private PhysicsEntityProjectionCleanup() {
    }

    @Nonnull
    public static Result cleanAll(@Nonnull Store<EntityStore> store,
        @Nullable ComponentType<EntityStore, ? extends Component<EntityStore>> detachableMarkerType) {
        return cleanAll(store, markerList(detachableMarkerType));
    }

    @Nonnull
    public static Result cleanAll(@Nonnull Store<EntityStore> store,
        @Nonnull Collection<ComponentType<EntityStore, ? extends Component<EntityStore>>>
            detachableMarkerTypes) {
        if (!PhysicsEntityAttachments.isAvailable()) {
            return Result.skippedResult();
        }
        Collection<ComponentType<EntityStore, ? extends Component<EntityStore>>> checkedMarkerTypes =
            Objects.requireNonNull(detachableMarkerTypes, "detachableMarkerTypes");
        AtomicIntegerArray counters = new AtomicIntegerArray(COUNTERS);
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        ComponentType<EntityStore, GeneratedVisualProxyComponent> generatedProxyType =
            GeneratedVisualProxyComponent.getComponentType();

        store.forEachEntityParallel(attachmentType,
            (index, archetypeChunk, commandBuffer) -> {
                BodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    attachmentType);
                if (attachment == null) {
                    return;
                }
                cleanAttachedEntity(counters,
                    commandBuffer,
                    archetypeChunk.getReferenceTo(index),
                    attachmentType,
                    checkedMarkerTypes,
                    attachment);
            });

        store.forEachEntityParallel(generatedProxyType,
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, attachmentType) != null) {
                    return;
                }
                removeOrphanProxy(counters, commandBuffer, archetypeChunk.getReferenceTo(index));
            });

        return Result.from(counters);
    }

    @Nonnull
    public static Result cleanSelected(@Nonnull Store<EntityStore> store,
        @Nonnull Set<UUID> selectedBodyUuids,
        @Nonnull Vector3d center,
        double radiusSquared,
        @Nullable ComponentType<EntityStore, ? extends Component<EntityStore>> detachableMarkerType) {
        return cleanSelected(store,
            selectedBodyUuids,
            center,
            radiusSquared,
            markerList(detachableMarkerType));
    }

    @Nonnull
    public static Result cleanSelected(@Nonnull Store<EntityStore> store,
        @Nonnull Set<UUID> selectedBodyUuids,
        @Nonnull Vector3d center,
        double radiusSquared,
        @Nonnull Collection<ComponentType<EntityStore, ? extends Component<EntityStore>>>
            detachableMarkerTypes) {
        if (!PhysicsEntityAttachments.isAvailable()) {
            return Result.skippedResult();
        }
        Set<UUID> checkedBodyUuids = Objects.requireNonNull(selectedBodyUuids,
            "selectedBodyUuids");
        Vector3d checkedCenter = Objects.requireNonNull(center, "center");
        Collection<ComponentType<EntityStore, ? extends Component<EntityStore>>> checkedMarkerTypes =
            Objects.requireNonNull(detachableMarkerTypes, "detachableMarkerTypes");
        AtomicIntegerArray counters = new AtomicIntegerArray(COUNTERS);
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        ComponentType<EntityStore, GeneratedVisualProxyComponent> generatedProxyType =
            GeneratedVisualProxyComponent.getComponentType();

        store.forEachEntityParallel(attachmentType,
            (index, archetypeChunk, commandBuffer) -> {
                BodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    attachmentType);
                if (attachment == null || !checkedBodyUuids.contains(attachment.getBodyUuid())) {
                    return;
                }
                cleanAttachedEntity(counters,
                    commandBuffer,
                    archetypeChunk.getReferenceTo(index),
                    attachmentType,
                    checkedMarkerTypes,
                    attachment);
            });

        store.forEachEntityParallel(generatedProxyType,
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, attachmentType) != null
                    || !entityWithinRadius(archetypeChunk, index, checkedCenter, radiusSquared)) {
                    return;
                }
                removeOrphanProxy(counters, commandBuffer, archetypeChunk.getReferenceTo(index));
            });

        return Result.from(counters);
    }

    private static void cleanAttachedEntity(
        @Nonnull AtomicIntegerArray counters,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentType<EntityStore, BodyAttachmentComponent> attachmentType,
        @Nonnull Collection<ComponentType<EntityStore, ? extends Component<EntityStore>>>
            detachableMarkerTypes,
        @Nonnull BodyAttachmentComponent attachment) {
        if (attachment.shouldRemoveEntityWhenBodyMissing()) {
            counters.incrementAndGet(REMOVED_ATTACHMENT_ENTITIES);
            commandBuffer.removeEntity(entityRef, RemoveReason.REMOVE);
            return;
        }
        counters.incrementAndGet(DETACHED_EXTERNAL_ATTACHMENTS);
        removeMarkers(commandBuffer, entityRef, detachableMarkerTypes);
        commandBuffer.removeComponent(entityRef, attachmentType);
    }

    private static void removeMarkers(@Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Collection<ComponentType<EntityStore, ? extends Component<EntityStore>>>
            markerTypes) {
        for (ComponentType<EntityStore, ? extends Component<EntityStore>> markerType : markerTypes) {
            if (commandBuffer.getComponent(entityRef, markerType) != null) {
                commandBuffer.removeComponent(entityRef, markerType);
            }
        }
    }

    @Nonnull
    private static List<ComponentType<EntityStore, ? extends Component<EntityStore>>> markerList(
        @Nullable ComponentType<EntityStore, ? extends Component<EntityStore>> markerType) {
        return markerType != null ? List.of(markerType) : List.of();
    }

    private static void removeOrphanProxy(@Nonnull AtomicIntegerArray counters,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> entityRef) {
        counters.incrementAndGet(REMOVED_ORPHAN_VISUAL_ENTITIES);
        commandBuffer.removeEntity(entityRef, RemoveReason.REMOVE);
    }

    private static boolean entityWithinRadius(@Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull Vector3d center,
        double radiusSquared) {
        TransformComponent transform =
            archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        return transform != null && transform.getPosition().distanceSquared(center) <= radiusSquared;
    }

    public record Result(boolean skipped,
                         int removedAttachmentEntities,
                         int detachedExternalAttachments,
                         int removedOrphanVisualEntities) {

        @Nonnull
        private static Result skippedResult() {
            return new Result(true, 0, 0, 0);
        }

        @Nonnull
        private static Result from(@Nonnull AtomicIntegerArray counters) {
            return new Result(false,
                counters.get(REMOVED_ATTACHMENT_ENTITIES),
                counters.get(DETACHED_EXTERNAL_ATTACHMENTS),
                counters.get(REMOVED_ORPHAN_VISUAL_ENTITIES));
        }
    }
}
