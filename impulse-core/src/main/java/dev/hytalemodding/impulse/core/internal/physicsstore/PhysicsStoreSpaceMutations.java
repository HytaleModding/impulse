package dev.hytalemodding.impulse.core.internal.physicsstore;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Direct PhysicsStore space row mutations for store-lane callers.
 */
public final class PhysicsStoreSpaceMutations {

    private PhysicsStoreSpaceMutations() {
    }

    @Nonnull
    public static Ref<PhysicsStore> addSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId compatibilitySpaceId,
        @Nonnull BackendId backendId,
        @Nonnull PhysicsSpaceSettings settings) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(compatibilitySpaceId, "compatibilitySpaceId");
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(settings, "settings");
        PhysicsStoreThreading.requireWorldThread(store, "add a PhysicsStore space row");
        if (backendId.value().isBlank()) {
            throw new IllegalArgumentException("PhysicsStore space backend id is blank: "
                + spaceUuid);
        }
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        if (compatibility.getSpaceUuid(compatibilitySpaceId) != null) {
            throw new IllegalArgumentException("PhysicsStore space id="
                + compatibilitySpaceId.value() + " is already registered");
        }
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        Ref<PhysicsStore> existing = identity.getByUuid(spaceUuid);
        if (existing != null && existing.isValid()) {
            throw new IllegalArgumentException("PhysicsStore space uuid=" + spaceUuid
                + " is already registered");
        }
        Ref<PhysicsStore> ref = store.addEntity(PhysicsStoreEntities.spaceHolder(store,
            spaceUuid,
            new SpaceComponent(backendId, new Vector3f(0.0f, -9.81f, 0.0f)),
            new WorldCollisionComponent(settings.getWorldCollisionSettings()),
            new SolverSettingsComponent(settings.getSolverSettings()),
            new VisualSyncSettingsComponent(settings.getVisualSyncSettings()),
            new VisualMaterializationSettingsComponent(settings.getVisualMaterializationSettings()),
            new CollisionLodSettingsComponent(settings.getCollisionLodSettings()),
            new ExtensionSettingsComponent(settings.getExtensionSettings())), AddReason.SPAWN);
        identity.putUuid(spaceUuid, ref);
        compatibility.putSpace(compatibilitySpaceId, spaceUuid);
        SpaceId.reserveAtLeast(compatibilitySpaceId.value());
        store.getResource(PhysicsRuntimeResource.getResourceType())
            .markSpaceSettingsPending(spaceUuid);
        return ref;
    }

    public static void putSpaceSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        UUID spaceUuid = requireSpaceUuid(store, spaceId);
        Ref<PhysicsStore> ref = requireSpaceRef(store, spaceUuid);
        putSpaceSettings(store, ref, spaceUuid, settings);
    }

    public static void putSpaceSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsSpaceSettings settings) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(settings, "settings");
        PhysicsStoreThreading.requireWorldThread(store, "update a PhysicsStore space row");
        store.putComponent(ref,
            WorldCollisionComponent.getComponentType(),
            new WorldCollisionComponent(settings.getWorldCollisionSettings()));
        PhysicsStoreEntities.putSpaceSettingsComponents(store,
            ref,
            new SolverSettingsComponent(settings.getSolverSettings()),
            new VisualSyncSettingsComponent(settings.getVisualSyncSettings()),
            new VisualMaterializationSettingsComponent(settings.getVisualMaterializationSettings()),
            new CollisionLodSettingsComponent(settings.getCollisionLodSettings()),
            new ExtensionSettingsComponent(settings.getExtensionSettings()));
        store.getResource(PhysicsRuntimeResource.getResourceType())
            .markSpaceSettingsPending(spaceUuid);
    }

    public static void removeEmptySpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        UUID spaceUuid = requireSpaceUuid(store, spaceId);
        removeEmptySpace(store, spaceUuid);
    }

    public static void removeEmptySpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        PhysicsStoreThreading.requireWorldThread(store, "remove a PhysicsStore space row");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        BackendSpaceHandle handle = runtime.getSpaceHandle(spaceUuid);
        if (handle != null) {
            BackendId backendId = runtime.getSpaceBackendId(spaceUuid);
            PhysicsBackendRuntime backendRuntime =
                backendId != null ? runtime.getRuntime(backendId) : null;
            if (backendRuntime == null) {
                throw new IllegalStateException("PhysicsStore space backend runtime is missing: "
                    + spaceUuid);
            }
            if (backendRuntime.bodyCount(handle.value()) > 0
                || backendRuntime.jointCount(handle.value()) > 0) {
                throw new IllegalStateException("PhysicsStore space is not empty: " + spaceUuid);
            }
            backendRuntime.destroySpace(handle.value());
            identity.removeSpaceHandle(handle);
            runtime.removeSpaceHandle(spaceUuid);
        }
        compatibility.removeBySpaceUuid(spaceUuid);
        Ref<PhysicsStore> ref = identity.getByUuid(spaceUuid);
        if (ref != null && ref.isValid()) {
            identity.removeUuid(spaceUuid, ref);
            store.removeEntity(ref, store.getRegistry().newHolder(), RemoveReason.REMOVE);
        }
    }

    @Nonnull
    public static UUID requireSpaceUuid(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        PhysicsStoreThreading.requireWorldThread(store, "resolve a PhysicsStore space UUID");
        UUID spaceUuid = store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(Objects.requireNonNull(spaceId, "spaceId"));
        if (spaceUuid == null) {
            throw new IllegalArgumentException("PhysicsStore space id=" + spaceId.value()
                + " is not registered");
        }
        return spaceUuid;
    }

    @Nonnull
    private static Ref<PhysicsStore> requireSpaceRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        PhysicsStoreThreading.requireWorldThread(store, "resolve a PhysicsStore space row");
        Ref<PhysicsStore> ref = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(Objects.requireNonNull(spaceUuid, "spaceUuid"));
        if (ref == null || !ref.isValid()) {
            throw new IllegalArgumentException("PhysicsStore space uuid=" + spaceUuid
                + " row is not registered");
        }
        return ref;
    }
}
