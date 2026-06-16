package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public compatibility reads for PhysicsStore space entities.
 */
public final class PhysicsStoreSpaces {

    private PhysicsStoreSpaces() {
    }

    @Nullable
    public static Ref<PhysicsStore> resolveRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store, "resolve a PhysicsStore space ref");
        UUID spaceUuid = checkedStore
            .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(Objects.requireNonNull(spaceId, "spaceId"));
        if (spaceUuid == null) {
            return null;
        }
        Ref<PhysicsStore> ref = checkedStore
            .getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);
        return ref != null && ref.getStore() == checkedStore && ref.isValid() ? ref : null;
    }

    public static boolean hasSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store, "check a PhysicsStore space");
        return checkedStore.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .hasSpace(Objects.requireNonNull(spaceId, "spaceId"));
    }

    @Nonnull
    public static Collection<SpaceId> spaceIds(@Nonnull Store<PhysicsStore> store) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store, "list PhysicsStore spaces");
        return List.copyOf(checkedStore
            .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .spaceIds());
    }

    @Nonnull
    private static Store<PhysicsStore> requireWorldThread(@Nonnull Store<PhysicsStore> store,
        @Nonnull String operation) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsStoreThreading.requireWorldThread(checkedStore, operation);
        return checkedStore;
    }
}
