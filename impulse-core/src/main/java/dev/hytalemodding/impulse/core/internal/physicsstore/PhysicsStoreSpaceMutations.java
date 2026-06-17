package dev.hytalemodding.impulse.core.internal.physicsstore;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.PhysicsChunkTerrainComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Direct PhysicsStore space entity mutations for store-lane callers.
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

        PhysicsThreading.requireWorldThread(store, "add a PhysicsStore space entity");
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
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.spaceHolder(store,
            spaceUuid,
            new SpaceComponent(backendId, new Vector3f(0.0f, -9.81f, 0.0f)),
            new PhysicsChunkTerrainComponent(settings.getPhysicsChunkTerrainSettings()),
            new SolverSettingsComponent(settings.getSolverSettings()),
            new VisualSyncSettingsComponent(settings.getVisualSyncSettings()),
            new VisualMaterializationSettingsComponent(settings.getVisualMaterializationSettings()),
            new CollisionLodSettingsComponent(settings.getCollisionLodSettings()),
            new ExtensionSettingsComponent(settings.getExtensionSettings())), AddReason.SPAWN);
        assert ref != null;
        identity.putUuid(spaceUuid, ref);
        compatibility.putSpace(compatibilitySpaceId, spaceUuid);
        SpaceId.reserveAtLeast(compatibilitySpaceId.value());
        store.getResource(PhysicsRuntimeResource.getResourceType())
            .markSpaceSettingsPending(ref);
        return ref;
    }

    public static void putSpaceSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        UUID spaceUuid = requireSpaceUuid(store, spaceId);
        Ref<PhysicsStore> ref = requireSpaceRef(store, spaceUuid);
        putSpaceSettings(store, ref, settings);
    }

    public static void putSpaceGravity(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f gravity) {
        UUID spaceUuid = requireSpaceUuid(store, spaceId);
        Ref<PhysicsStore> ref = requireSpaceRef(store, spaceUuid);
        putSpaceGravity(store, ref, spaceUuid, gravity);
    }

    public static void putSpaceGravity(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull UUID spaceUuid,
        @Nonnull Vector3f gravity) {
        PhysicsThreading.requireWorldThread(store, "update PhysicsStore space gravity");
        SpaceComponent space = store.getComponent(ref, SpaceComponent.getComponentType());
        if (space == null) {
            throw new IllegalArgumentException("PhysicsStore space uuid=" + spaceUuid
                + " entity has no SpaceComponent");
        }
        SpaceComponent updated = space.clone();
        updated.setGravity(gravity);
        store.putComponent(ref, SpaceComponent.getComponentType(), updated);
        store.getResource(PhysicsRuntimeResource.getResourceType())
            .markSpaceSettingsPending(ref);
    }

    public static void putSpaceSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull PhysicsSpaceSettings settings) {
        requireSpaceUuid(store, ref);
        PhysicsThreading.requireWorldThread(store, "update a PhysicsStore space entity");
        store.putComponent(ref,
            PhysicsChunkTerrainComponent.getComponentType(),
            new PhysicsChunkTerrainComponent(settings.getPhysicsChunkTerrainSettings()));
        PhysicsEntities.putSpaceSettingsComponents(store,
            ref,
            new SolverSettingsComponent(settings.getSolverSettings()),
            new VisualSyncSettingsComponent(settings.getVisualSyncSettings()),
            new VisualMaterializationSettingsComponent(settings.getVisualMaterializationSettings()),
            new CollisionLodSettingsComponent(settings.getCollisionLodSettings()),
            new ExtensionSettingsComponent(settings.getExtensionSettings()));
        store.getResource(PhysicsRuntimeResource.getResourceType())
            .markSpaceSettingsPending(ref);
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
        PhysicsThreading.requireBackendIdle(store, "remove a PhysicsStore space entity");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        Ref<PhysicsStore> ref = identity.getByUuid(spaceUuid);
        BackendSpaceHandle handle = ref != null && ref.isValid()
            ? runtime.getSpaceHandle(ref)
            : null;
        if (handle != null) {
            BackendId backendId = runtime.getSpaceBackendId(ref);
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
        if (ref != null && ref.isValid()) {
            identity.removeUuid(spaceUuid, ref);
            store.removeEntity(ref, store.getRegistry().newHolder(), RemoveReason.REMOVE);
        }
    }

    @Nonnull
    public static UUID requireSpaceUuid(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        PhysicsThreading.requireWorldThread(store, "resolve a PhysicsStore space UUID");
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
        PhysicsThreading.requireWorldThread(store, "resolve a PhysicsStore space entity");
        Ref<PhysicsStore> ref = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(Objects.requireNonNull(spaceUuid, "spaceUuid"));
        if (ref == null || !ref.isValid()) {
            throw new IllegalArgumentException("PhysicsStore space uuid=" + spaceUuid
                + " row is not registered");
        }
        return ref;
    }

    @Nonnull
    private static UUID requireSpaceUuid(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        Objects.requireNonNull(ref, "ref");
        PhysicsThreading.requireWorldThread(store, "resolve a PhysicsStore space UUID");
        if (ref.getStore() != store || !ref.isValid()) {
            throw new IllegalArgumentException("PhysicsStore space entity is not valid: " + ref);
        }
        if (store.getComponent(ref, SpaceComponent.getComponentType()) == null) {
            throw new IllegalArgumentException("PhysicsStore entity is not a space entity: " + ref);
        }
        UuidComponent uuid = store.getComponent(ref, UuidComponent.getComponentType());
        if (uuid == null) {
            throw new IllegalArgumentException("PhysicsStore space entity has no durable UUID: " + ref);
        }
        return uuid.getUuid();
    }
}
