package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreTopologyMutations;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public compatibility reads for PhysicsStore space entities.
 */
public final class PhysicsSpaces {

    private PhysicsSpaces() {
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
    public static SpaceId create(@Nonnull Store<PhysicsStore> store,
        @Nonnull BackendId backendId) {
        return create(store, backendId, PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    public static SpaceId create(@Nonnull Store<PhysicsStore> store,
        @Nonnull BackendId backendId,
        @Nonnull PhysicsSpaceSettings settings) {
        SpaceId spaceId = SpaceId.next();
        create(store, UUID.randomUUID(), spaceId, backendId, settings);
        return spaceId;
    }

    @Nonnull
    public static Ref<PhysicsStore> create(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendId backendId,
        @Nonnull PhysicsSpaceSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "create a PhysicsStore space");
        return PhysicsStoreSpaceMutations.addSpace(checkedStore,
            Objects.requireNonNull(spaceUuid, "spaceUuid"),
            Objects.requireNonNull(spaceId, "spaceId"),
            Objects.requireNonNull(backendId, "backendId"),
            Objects.requireNonNull(settings, "settings"));
    }

    @Nonnull
    public static Collection<SpaceId> spaceIds(@Nonnull Store<PhysicsStore> store) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store, "list PhysicsStore spaces");
        return List.copyOf(checkedStore
            .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .spaceIds());
    }

    public static int count(@Nonnull Store<PhysicsStore> store) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store, "count PhysicsStore spaces");
        return checkedStore.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .size();
    }

    @Nullable
    public static PhysicsSpaceSettings settings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Ref<PhysicsStore> ref = resolveRef(store, spaceId);
        return ref != null ? settings(store, ref) : null;
    }

    @Nullable
    public static PhysicsSpaceSettings settings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read PhysicsStore space settings");
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(spaceRef, "spaceRef");
        if (checkedRef.getStore() != checkedStore || !checkedRef.isValid()) {
            return null;
        }
        if (checkedStore.getComponent(checkedRef, SpaceComponent.getComponentType()) == null) {
            return null;
        }
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        WorldCollisionComponent terrainSettings = checkedStore.getComponent(checkedRef,
            WorldCollisionComponent.getComponentType());
        if (terrainSettings != null) {
            terrainSettings.copyTo(settings);
        }
        SolverSettingsComponent solverSettings = checkedStore.getComponent(checkedRef,
            SolverSettingsComponent.getComponentType());
        if (solverSettings != null) {
            solverSettings.copyTo(settings);
        }
        VisualSyncSettingsComponent visualSyncSettings = checkedStore.getComponent(checkedRef,
            VisualSyncSettingsComponent.getComponentType());
        if (visualSyncSettings != null) {
            visualSyncSettings.copyTo(settings);
        }
        VisualMaterializationSettingsComponent visualMaterializationSettings =
            checkedStore.getComponent(checkedRef,
                VisualMaterializationSettingsComponent.getComponentType());
        if (visualMaterializationSettings != null) {
            visualMaterializationSettings.copyTo(settings);
        }
        CollisionLodSettingsComponent collisionLodSettings = checkedStore.getComponent(checkedRef,
            CollisionLodSettingsComponent.getComponentType());
        if (collisionLodSettings != null) {
            collisionLodSettings.copyTo(settings);
        }
        ExtensionSettingsComponent extensionSettings = checkedStore.getComponent(checkedRef,
            ExtensionSettingsComponent.getComponentType());
        if (extensionSettings != null) {
            extensionSettings.copyTo(settings);
        }
        return settings;
    }

    public static void putSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore space settings");
        PhysicsStoreSpaceMutations.putSpaceSettings(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull PhysicsSpaceSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore space settings");
        PhysicsStoreSpaceMutations.putSpaceSettings(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void removeEmpty(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "remove an empty PhysicsStore space");
        PhysicsStoreSpaceMutations.removeEmptySpace(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"));
    }

    public static void removeWithContents(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "remove a PhysicsStore space with contents");
        PhysicsStoreTopologyMutations.removeSpaceWithContents(checkedStore,
            PhysicsStoreSpaceMutations.requireSpaceUuid(checkedStore,
                Objects.requireNonNull(spaceId, "spaceId")));
    }

    @Nonnull
    private static Store<PhysicsStore> requireWorldThread(@Nonnull Store<PhysicsStore> store,
        @Nonnull String operation) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore, operation);
        return checkedStore;
    }
}
