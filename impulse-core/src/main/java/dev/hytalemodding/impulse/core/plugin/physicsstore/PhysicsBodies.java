package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public copied body reads for PhysicsStore entities.
 */
public final class PhysicsBodies {

    private PhysicsBodies() {
    }

    @Nullable
    public static PhysicsBodyRegistrationView registrationView(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read copied PhysicsStore body registration");
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(bodyRef, "bodyRef");
        if (!sameValidStore(checkedStore, checkedRef)) {
            return null;
        }
        return checkedStore.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .getBodyRegistrationView(checkedRef);
    }

    @Nullable
    public static PhysicsBodyRegistrationView registrationView(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read copied PhysicsStore body registration");
        return checkedStore.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .getBodyRegistrationView(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nonnull
    public static Collection<PhysicsBodyRegistrationView> registrationViews(
        @Nonnull Store<PhysicsStore> store) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read copied PhysicsStore body registrations");
        return checkedStore.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .getBodyRegistrationViews();
    }

    @Nonnull
    public static Collection<PhysicsBodyRegistrationView> registrationViews(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsBodyKind kind) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read copied PhysicsStore body registrations");
        return checkedStore.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .getBodyRegistrationViews(Objects.requireNonNull(kind, "kind"));
    }

    public static int registrationCount(@Nonnull Store<PhysicsStore> store) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "count copied PhysicsStore body registrations");
        return checkedStore.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .getBodyRegistrationCount();
    }

    public static int registrationCount(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "count copied PhysicsStore body registrations");
        return checkedStore.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .getBodyRegistrationCount(Objects.requireNonNull(persistenceMode, "persistenceMode"));
    }

    @Nullable
    public static PhysicsBodySnapshot snapshot(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read copied PhysicsStore body snapshot");
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(bodyRef, "bodyRef");
        if (!sameValidStore(checkedStore, checkedRef)) {
            return null;
        }
        return checkedStore.getResource(PhysicsSnapshotResource.getResourceType())
            .getBody(checkedRef);
    }

    @Nullable
    public static PhysicsBodySnapshot snapshot(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read copied PhysicsStore body snapshot");
        return checkedStore.getResource(PhysicsSnapshotResource.getResourceType())
            .getBody(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nonnull
    public static PhysicsSnapshotFrame snapshotFrame(@Nonnull Store<PhysicsStore> store) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read copied PhysicsStore body snapshot frame");
        return checkedStore.getResource(PhysicsSnapshotResource.getResourceType())
            .getLatestFrame();
    }

    public static int snapshotCount(@Nonnull Store<PhysicsStore> store) {
        return snapshotFrame(store).bodies().size();
    }

    @Nonnull
    private static Store<PhysicsStore> requireWorldThread(@Nonnull Store<PhysicsStore> store,
        @Nonnull String operation) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore, operation);
        return checkedStore;
    }

    private static boolean sameValidStore(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        return ref.getStore() == store && ref.isValid();
    }
}
