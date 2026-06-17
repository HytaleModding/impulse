package dev.hytalemodding.impulse.core.plugin.modules.physicsentity;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public EntityStore projection reads for PhysicsStore body attachments.
 */
public final class PhysicsEntityAttachments {

    private PhysicsEntityAttachments() {
    }

    @Nonnull
    public static Collection<Ref<EntityStore>> attachments(@Nonnull Store<EntityStore> store,
        @Nonnull UUID bodyUuid) {
        return requireWorldThread(store, "read PhysicsStore body attachments")
            .getResource(PhysicsProjectionIndexResource.getResourceType())
            .getAttachments(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nonnull
    public static Collection<Ref<EntityStore>> attachments(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return requireWorldThread(store, "read PhysicsStore body attachments")
            .getResource(PhysicsProjectionIndexResource.getResourceType())
            .getAttachments(Objects.requireNonNull(bodyRef, "bodyRef"));
    }

    @Nonnull
    public static Collection<Ref<EntityStore>> attachments(@Nonnull Store<EntityStore> store,
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        PhysicsProjectionIndexResource projection =
            requireWorldThread(store, "read PhysicsStore body attachments")
                .getResource(PhysicsProjectionIndexResource.getResourceType());
        return bodyRef != null && bodyRef.isValid()
            ? projection.getAttachments(bodyRef)
            : projection.getAttachments(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    public static boolean hasAttachments(@Nonnull Store<EntityStore> store,
        @Nonnull UUID bodyUuid) {
        return requireWorldThread(store, "check PhysicsStore body attachments")
            .getResource(PhysicsProjectionIndexResource.getResourceType())
            .hasAttachments(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    public static boolean hasAttachments(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return requireWorldThread(store, "check PhysicsStore body attachments")
            .getResource(PhysicsProjectionIndexResource.getResourceType())
            .hasAttachments(Objects.requireNonNull(bodyRef, "bodyRef"));
    }

    public static boolean hasAttachments(@Nonnull Store<EntityStore> store,
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        PhysicsProjectionIndexResource projection =
            requireWorldThread(store, "check PhysicsStore body attachments")
                .getResource(PhysicsProjectionIndexResource.getResourceType());
        return bodyRef != null && bodyRef.isValid()
            ? projection.hasAttachments(bodyRef)
            : projection.hasAttachments(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nullable
    public static Ref<EntityStore> generatedVisualProxy(@Nonnull Store<EntityStore> store,
        @Nonnull UUID bodyUuid) {
        return requireWorldThread(store, "read PhysicsStore generated visual proxy")
            .getResource(PhysicsProjectionIndexResource.getResourceType())
            .getGeneratedVisualProxy(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nullable
    public static Ref<EntityStore> generatedVisualProxy(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return requireWorldThread(store, "read PhysicsStore generated visual proxy")
            .getResource(PhysicsProjectionIndexResource.getResourceType())
            .getGeneratedVisualProxy(Objects.requireNonNull(bodyRef, "bodyRef"));
    }

    @Nonnull
    private static Store<EntityStore> requireWorldThread(@Nonnull Store<EntityStore> store,
        @Nonnull String operation) {
        Store<EntityStore> checkedStore = Objects.requireNonNull(store, "store");
        if (!checkedStore.getExternalData().getWorld().isInThread()) {
            throw new IllegalStateException("Cannot " + operation
                + " outside the owning EntityStore world thread");
        }
        return checkedStore;
    }
}
