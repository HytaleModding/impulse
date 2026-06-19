package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreTopologyMutations;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsExtensionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
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
        SpaceId spaceId = SpaceId.next();
        create(store, UUID.randomUUID(), spaceId, backendId);
        return spaceId;
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
        @Nonnull BackendId backendId) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "create a PhysicsStore space");
        return PhysicsStoreSpaceMutations.addSpace(checkedStore,
            Objects.requireNonNull(spaceUuid, "spaceUuid"),
            Objects.requireNonNull(spaceId, "spaceId"),
            Objects.requireNonNull(backendId, "backendId"));
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
        ChunkCollisionSettingsComponent chunkCollisionSettings = checkedStore.getComponent(checkedRef,
            ChunkCollisionSettingsComponent.getComponentType());
        if (chunkCollisionSettings != null) {
            chunkCollisionSettings.copyTo(settings);
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

    @Nullable
    public static PhysicsChunkCollisionSettings chunkCollisionSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Ref<PhysicsStore> ref = resolveRef(store, spaceId);
        return ref != null ? chunkCollisionSettings(store, ref) : null;
    }

    @Nullable
    public static PhysicsChunkCollisionSettings chunkCollisionSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read PhysicsStore chunk collision settings");
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(spaceRef, "spaceRef");
        if (!isSpaceRef(checkedStore, checkedRef)) {
            return null;
        }
        PhysicsChunkCollisionSettings settings = new PhysicsChunkCollisionSettings();
        ChunkCollisionSettingsComponent component = checkedStore.getComponent(checkedRef,
            ChunkCollisionSettingsComponent.getComponentType());
        if (component != null) {
            component.copyTo(settings);
        }
        return settings;
    }

    @Nullable
    public static PhysicsSolverSettings solverSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Ref<PhysicsStore> ref = resolveRef(store, spaceId);
        return ref != null ? solverSettings(store, ref) : null;
    }

    @Nullable
    public static PhysicsSolverSettings solverSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read PhysicsStore solver settings");
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(spaceRef, "spaceRef");
        if (!isSpaceRef(checkedStore, checkedRef)) {
            return null;
        }
        PhysicsSolverSettings settings = new PhysicsSolverSettings();
        SolverSettingsComponent component = checkedStore.getComponent(checkedRef,
            SolverSettingsComponent.getComponentType());
        if (component != null) {
            component.copyTo(settings);
        }
        return settings;
    }

    @Nullable
    public static PhysicsVisualSyncSettings visualSyncSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Ref<PhysicsStore> ref = resolveRef(store, spaceId);
        return ref != null ? visualSyncSettings(store, ref) : null;
    }

    @Nullable
    public static PhysicsVisualSyncSettings visualSyncSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read PhysicsStore visual sync settings");
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(spaceRef, "spaceRef");
        if (!isSpaceRef(checkedStore, checkedRef)) {
            return null;
        }
        PhysicsVisualSyncSettings settings = new PhysicsVisualSyncSettings();
        VisualSyncSettingsComponent component = checkedStore.getComponent(checkedRef,
            VisualSyncSettingsComponent.getComponentType());
        if (component != null) {
            component.copyTo(settings);
        }
        return settings;
    }

    @Nullable
    public static PhysicsVisualMaterializationSettings visualMaterializationSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Ref<PhysicsStore> ref = resolveRef(store, spaceId);
        return ref != null ? visualMaterializationSettings(store, ref) : null;
    }

    @Nullable
    public static PhysicsVisualMaterializationSettings visualMaterializationSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read PhysicsStore visual materialization settings");
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(spaceRef, "spaceRef");
        if (!isSpaceRef(checkedStore, checkedRef)) {
            return null;
        }
        PhysicsVisualMaterializationSettings settings =
            new PhysicsVisualMaterializationSettings();
        VisualMaterializationSettingsComponent component = checkedStore.getComponent(checkedRef,
            VisualMaterializationSettingsComponent.getComponentType());
        if (component != null) {
            component.copyTo(settings);
        }
        return settings;
    }

    @Nullable
    public static PhysicsCollisionLodSettings collisionLodSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Ref<PhysicsStore> ref = resolveRef(store, spaceId);
        return ref != null ? collisionLodSettings(store, ref) : null;
    }

    @Nullable
    public static PhysicsCollisionLodSettings collisionLodSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read PhysicsStore collision LOD settings");
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(spaceRef, "spaceRef");
        if (!isSpaceRef(checkedStore, checkedRef)) {
            return null;
        }
        PhysicsCollisionLodSettings settings = new PhysicsCollisionLodSettings();
        CollisionLodSettingsComponent component = checkedStore.getComponent(checkedRef,
            CollisionLodSettingsComponent.getComponentType());
        if (component != null) {
            component.copyTo(settings);
        }
        return settings;
    }

    @Nullable
    public static PhysicsExtensionSettings extensionSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Ref<PhysicsStore> ref = resolveRef(store, spaceId);
        return ref != null ? extensionSettings(store, ref) : null;
    }

    @Nullable
    public static PhysicsExtensionSettings extensionSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read PhysicsStore extension settings");
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(spaceRef, "spaceRef");
        if (!isSpaceRef(checkedStore, checkedRef)) {
            return null;
        }
        PhysicsExtensionSettings settings = new PhysicsExtensionSettings();
        ExtensionSettingsComponent component = checkedStore.getComponent(checkedRef,
            ExtensionSettingsComponent.getComponentType());
        if (component != null) {
            component.copyTo(settings);
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

    public static void putChunkCollisionSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsChunkCollisionSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore chunk collision settings");
        PhysicsStoreSpaceMutations.putChunkCollisionSettings(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putChunkCollisionSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull PhysicsChunkCollisionSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore chunk collision settings");
        PhysicsStoreSpaceMutations.putChunkCollisionSettings(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putSolverSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSolverSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore solver settings");
        PhysicsStoreSpaceMutations.putSolverSettings(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putSolverSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull PhysicsSolverSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore solver settings");
        PhysicsStoreSpaceMutations.putSolverSettings(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putVisualSyncSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsVisualSyncSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore visual sync settings");
        PhysicsStoreSpaceMutations.putVisualSyncSettings(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putVisualSyncSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull PhysicsVisualSyncSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore visual sync settings");
        PhysicsStoreSpaceMutations.putVisualSyncSettings(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putVisualMaterializationSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsVisualMaterializationSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore visual materialization settings");
        PhysicsStoreSpaceMutations.putVisualMaterializationSettings(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putVisualMaterializationSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull PhysicsVisualMaterializationSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore visual materialization settings");
        PhysicsStoreSpaceMutations.putVisualMaterializationSettings(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putCollisionLodSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsCollisionLodSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore collision LOD settings");
        PhysicsStoreSpaceMutations.putCollisionLodSettings(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putCollisionLodSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull PhysicsCollisionLodSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore collision LOD settings");
        PhysicsStoreSpaceMutations.putCollisionLodSettings(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putExtensionSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsExtensionSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore extension settings");
        PhysicsStoreSpaceMutations.putExtensionSettings(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"),
            Objects.requireNonNull(settings, "settings"));
    }

    public static void putExtensionSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull PhysicsExtensionSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore extension settings");
        PhysicsStoreSpaceMutations.putExtensionSettings(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"),
            Objects.requireNonNull(settings, "settings"));
    }

    @Nullable
    public static <T extends Component<PhysicsStore>> T getSpaceComponent(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull ComponentType<PhysicsStore, T> componentType) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read a PhysicsStore space component");
        Ref<PhysicsStore> ref = requireSpaceRef(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"));
        return checkedStore.getComponent(ref, Objects.requireNonNull(componentType,
            "componentType"));
    }

    @Nullable
    public static <T extends Component<PhysicsStore>> T getSpaceComponent(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull ComponentType<PhysicsStore, T> componentType) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read a PhysicsStore space component");
        Ref<PhysicsStore> ref = requireSpaceRef(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"));
        return checkedStore.getComponent(ref, Objects.requireNonNull(componentType,
            "componentType"));
    }

    public static <T extends Component<PhysicsStore>> void putSpaceComponent(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull ComponentType<PhysicsStore, T> componentType,
        @Nonnull T component) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "put a PhysicsStore space component");
        putSpaceComponent(checkedStore,
            requireSpaceRef(checkedStore, Objects.requireNonNull(spaceId, "spaceId")),
            componentType,
            component);
    }

    public static <T extends Component<PhysicsStore>> void putSpaceComponent(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull ComponentType<PhysicsStore, T> componentType,
        @Nonnull T component) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "put a PhysicsStore space component");
        Ref<PhysicsStore> ref = requireSpaceRef(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"));
        checkedStore.putComponent(ref,
            Objects.requireNonNull(componentType, "componentType"),
            copy(Objects.requireNonNull(component, "component")));
        checkedStore.getResource(PhysicsRuntimeResource.getResourceType())
            .markSpaceSettingsPending(ref);
    }

    public static <T extends Component<PhysicsStore>> boolean removeSpaceComponent(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull ComponentType<PhysicsStore, T> componentType) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "remove a PhysicsStore space component");
        return removeSpaceComponent(checkedStore,
            requireSpaceRef(checkedStore, Objects.requireNonNull(spaceId, "spaceId")),
            componentType);
    }

    public static <T extends Component<PhysicsStore>> boolean removeSpaceComponent(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull ComponentType<PhysicsStore, T> componentType) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "remove a PhysicsStore space component");
        Ref<PhysicsStore> ref = requireSpaceRef(checkedStore,
            Objects.requireNonNull(spaceRef, "spaceRef"));
        boolean removed = checkedStore.removeComponentIfExists(ref,
            Objects.requireNonNull(componentType, "componentType"));
        if (removed) {
            checkedStore.getResource(PhysicsRuntimeResource.getResourceType())
                .markSpaceSettingsPending(ref);
        }
        return removed;
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

    private static boolean isSpaceRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        return ref.getStore() == store
            && ref.isValid()
            && store.getComponent(ref, SpaceComponent.getComponentType()) != null;
    }

    @Nonnull
    private static Ref<PhysicsStore> requireSpaceRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Ref<PhysicsStore> ref = resolveRef(store, spaceId);
        if (ref == null) {
            throw new IllegalArgumentException("PhysicsStore space id=" + spaceId.value()
                + " is not registered");
        }
        return ref;
    }

    @Nonnull
    private static Ref<PhysicsStore> requireSpaceRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        if (ref.getStore() != store || !ref.isValid()) {
            throw new IllegalArgumentException("PhysicsStore space entity is not valid: " + ref);
        }
        if (store.getComponent(ref, SpaceComponent.getComponentType()) == null) {
            throw new IllegalArgumentException("PhysicsStore entity is not a space entity: " + ref);
        }
        return ref;
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static <T extends Component<PhysicsStore>> T copy(@Nonnull T component) {
        return (T) component.clone();
    }
}
