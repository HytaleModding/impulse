package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.PhysicsControlRuntimeState;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreRuntimeCleaner;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreTopologyMutations;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState.BodySyncState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntime;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshots;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotRefVisitor;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotVisitor;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.BodyVisualInterestState;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.VisualInterest;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsWorldCollisionRuntime;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkLifecycle;
import dev.hytalemodding.impulse.core.internal.PhysicsStoreEarlyPluginProbe;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Internal ECS resource implementation behind {@link PhysicsWorldResource}.
 */
public class PhysicsWorldRuntimeResource extends PhysicsWorldResource {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private final PhysicsSpaceRuntime spaceRuntime = new PhysicsSpaceRuntime();

    private final PhysicsBodyRegistry bodyRegistry = new PhysicsBodyRegistry();

    private final PhysicsWorldCollisionRuntime collisionRuntime =
        new PhysicsWorldCollisionRuntime();

    @Nonnull
    private final PhysicsSimulationRuntime simulationRuntime = new PhysicsSimulationRuntime();

    private final PhysicsBodyRuntimeState runtimeState = new PhysicsBodyRuntimeState();
    private final PhysicsControlRuntimeState controlRuntime = new PhysicsControlRuntimeState();
    private final PhysicsJointRegistry jointRegistry = new PhysicsJointRegistry();
    private final PhysicsVisualRuntime visualRuntime = new PhysicsVisualRuntime(this::clearBodySyncState);
    private final PhysicsWorldLifecycleState lifecycleState = new PhysicsWorldLifecycleState();
    private final PhysicsBodyRuntime bodyRuntime = new PhysicsBodyRuntime(spaceRuntime,
        bodyRegistry,
        runtimeState,
        controlRuntime,
        jointRegistry,
        visualRuntime,
        lifecycleState,
        this::markWorldChanged);

    private final AtomicLong visualInterestTick = new AtomicLong();
    @Nullable
    private Store<EntityStore> owningStore;

    public PhysicsWorldRuntimeResource() {
        ControlLifecycle.registerResource(this);
        PhysicsChunkLifecycle.registerResource(this);
    }

    @Nonnull
    public static PhysicsWorldRuntimeResource require(@Nonnull Store<EntityStore> store) {
        PhysicsWorldRuntimeResource resource =
            require(store.getResource(PhysicsWorldResource.getResourceType()));
        resource.attachEntityStore(store);
        return resource;
    }

    @Nonnull
    public static PhysicsWorldRuntimeResource require(@Nonnull PhysicsWorldResource resource) {
        if (resource instanceof PhysicsWorldRuntimeResource runtime) {
            return runtime;
        }
        throw new IllegalStateException(
            "Physics world resource is not the Impulse runtime implementation");
    }

    public void attachEntityStore(@Nonnull Store<EntityStore> store) {
        owningStore = Objects.requireNonNull(store, "store");
    }

    public void detachEntityStore(@Nonnull Store<EntityStore> store) {
        if (owningStore == store) {
            owningStore = null;
        }
    }

    @Nonnull
    @Override
    public PhysicsEventFrame getLatestEventFrame() {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativePhysicsStore("read latest physics event frame")
                .getResource(PhysicsEventResource.getResourceType())
                .getLatestFrame();
        }
        return lifecycleState.latestEventFrame();
    }

    private void assertCanAccessLiveBackendDirectly(@Nonnull String operation) {
        Objects.requireNonNull(operation, "operation");
    }

    private void requireLegacyMutationAllowed(@Nonnull String operation) {
        if (!isAuthoritativePhysicsStoreActive()) {
            return;
        }
        throw new IllegalStateException("Legacy PhysicsWorldResource mutation is disabled while "
            + "authoritative PhysicsStore is active: " + operation
            + ". Route this operation through PhysicsStore entities or a PhysicsStore-backed "
            + "compatibility bridge.");
    }

    private boolean isAuthoritativePhysicsStoreActive() {
        return PhysicsStoreEarlyPluginProbe.isAvailable();
    }

    private boolean hasAttachedAuthoritativePhysicsStore() {
        return isAuthoritativePhysicsStoreActive() && owningStore != null;
    }

    @Nonnull
    private World requireAuthoritativeWorld(@Nonnull String operation) {
        Store<EntityStore> entityStore = owningStore;
        if (entityStore == null) {
            throw new IllegalStateException("Cannot " + operation
                + " through authoritative PhysicsStore before this resource is attached to an "
                + "EntityStore");
        }
        return entityStore.getExternalData().getWorld();
    }

    @Nonnull
    private Store<PhysicsStore> authoritativePhysicsStore(@Nonnull String operation) {
        Store<PhysicsStore> store = physicsStore(requireAuthoritativeWorld(operation));
        PhysicsThreading.requireWorldThread(store, operation);
        return store;
    }

    @Nonnull
    private static Store<PhysicsStore> physicsStore(@Nonnull World world) {
        return PhysicsThreading.store(world);
    }

    private static boolean sameRef(@Nullable Ref<EntityStore> first,
        @Nullable Ref<EntityStore> second) {
        return first == second
            || (first != null
                && second != null
                && first.getStore() != null
                && first.getStore() == second.getStore()
                && first.getIndex() == second.getIndex());
    }

    @Nonnull
    private PhysicsProjectionIndexResource authoritativeProjectionIndex(@Nonnull String operation) {
        Store<EntityStore> entityStore = owningStore;
        if (entityStore == null) {
            throw new IllegalStateException("Cannot " + operation
                + " through authoritative PhysicsStore projection before this resource is attached "
                + "to an EntityStore");
        }
        return entityStore.getResource(PhysicsProjectionIndexResource.getResourceType());
    }

    @Nonnull
    private static UUID requireSpaceUuid(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        return PhysicsStoreSpaceMutations.requireSpaceUuid(store, spaceId);
    }

    @Nullable
    private static PhysicsSpaceSettings getPhysicsStoreSpaceSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        UUID spaceUuid = store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(Objects.requireNonNull(spaceId, "spaceId"));
        if (spaceUuid == null) {
            return null;
        }
        Ref<PhysicsStore> ref = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return getPhysicsStoreSpaceSettings(store, ref);
    }

    @Nullable
    private static PhysicsSpaceSettings getPhysicsStoreSpaceSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(ref, "ref");
        if (ref.getStore() != store || !ref.isValid()) {
            return null;
        }
        SpaceComponent space = store.getComponent(ref, SpaceComponent.getComponentType());
        if (space == null) {
            return null;
        }
        WorldCollisionComponent worldCollision = store.getComponent(ref,
            WorldCollisionComponent.getComponentType());
        SolverSettingsComponent solverSettings = store.getComponent(ref,
            SolverSettingsComponent.getComponentType());
        VisualSyncSettingsComponent visualSyncSettings = store.getComponent(ref,
            VisualSyncSettingsComponent.getComponentType());
        VisualMaterializationSettingsComponent visualMaterializationSettings =
            store.getComponent(ref, VisualMaterializationSettingsComponent.getComponentType());
        CollisionLodSettingsComponent collisionLodSettings = store.getComponent(ref,
            CollisionLodSettingsComponent.getComponentType());
        ExtensionSettingsComponent extensionSettings = store.getComponent(ref,
            ExtensionSettingsComponent.getComponentType());
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        if (worldCollision != null) {
            worldCollision.copyTo(settings);
        }
        if (solverSettings != null) {
            solverSettings.copyTo(settings);
        }
        if (visualSyncSettings != null) {
            visualSyncSettings.copyTo(settings);
        }
        if (visualMaterializationSettings != null) {
            visualMaterializationSettings.copyTo(settings);
        }
        if (collisionLodSettings != null) {
            collisionLodSettings.copyTo(settings);
        }
        if (extensionSettings != null) {
            extensionSettings.copyTo(settings);
        }
        return settings;
    }

    private static void validateAuthoritativeStepModeSupported(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsStepMode stepMode) {
        if (stepMode != PhysicsStepMode.CCD) {
            return;
        }
        List<String> unsupportedSpaces = new ArrayList<>();
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        runtime.forEachRuntimeSpaceBinding((spaceRef, backendId, spaceHandle, backendRuntime) -> {
            if (!backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                UUID spaceUuid = runtime.getSpaceUuid(spaceRef);
                unsupportedSpaces.add((spaceUuid != null ? spaceUuid : spaceRef)
                    + " backend=" + backendId.value());
            }
        });
        if (!unsupportedSpaces.isEmpty()) {
            throw new IllegalArgumentException("CCD step mode is not supported by PhysicsStore "
                + "spaces: " + unsupportedSpaces);
        }
    }

    @Nonnull
    private <T> PhysicsMutationHandle<T> enqueueAuthoritativePhysicsStoreMutation(
        @Nonnull String operation,
        @Nullable T value,
        @Nonnull Consumer<Store<PhysicsStore>> mutation) {
        World world = requireAuthoritativeWorld(operation);
        return PhysicsMutationHandle.fromCompletion(operation,
            value,
            PhysicsThreading.executeOnWorldThread(world, operation, mutation));
    }

    private void runDirectRuntimeMutation(@Nonnull String operation,
        @Nonnull DirectRuntimeMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        try {
            mutation.run();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Physics operation " + operation + " failed",
                exception);
        }
    }

    @Nonnull
    private PhysicsMutationHandle<Void> enqueueDirectRuntimeMutation(@Nonnull String operation,
        @Nonnull DirectRuntimeMutation mutation) {
        return enqueueDirectRuntimeMutation(operation, null, mutation);
    }


    @Nonnull
    private <T> PhysicsMutationHandle<T> enqueueDirectRuntimeMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull DirectRuntimeMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        try {
            mutation.run();
            return PhysicsMutationHandle.completed(operation, value);
        } catch (Throwable throwable) {
            return PhysicsMutationHandle.failed(operation, value, throwable);
        }
    }


    @Nonnull
    private <T> T callDirectRuntime(@Nonnull String operation,
        @Nonnull DirectRuntimeCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        try {
            return callable.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Physics operation " + operation + " failed",
                exception);
        }
    }


    @Nonnull
    @Override
    public PhysicsWorldSettings getWorldSettings() {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativePhysicsStore("read physics world settings")
                .getResource(PhysicsWorldSettingsResource.getResourceType())
                .getSettings();
        }
        return simulationRuntime.getWorldSettings();
    }

    @Override
    public void setWorldSettings(@Nonnull PhysicsWorldSettings settings) {
        PhysicsWorldSettings requested = new PhysicsWorldSettings(settings);
        if (hasAttachedAuthoritativePhysicsStore()) {
            setAuthoritativeWorldSettings(
                authoritativePhysicsStore("set physics world settings"),
                requested);
            return;
        }
        if (isAuthoritativePhysicsStoreActive()) {
            setWorldSettingsDirect(requested);
            return;
        }
        requireLegacyMutationAllowed("set physics world settings");
        runDirectRuntimeMutation("set physics world settings", () -> setWorldSettingsDirect(requested));
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<Void> setWorldSettingsAsync(
        @Nonnull PhysicsWorldSettings settings) {
        PhysicsWorldSettings requested = new PhysicsWorldSettings(settings);
        if (hasAttachedAuthoritativePhysicsStore()) {
            return enqueueAuthoritativePhysicsStoreMutation("set physics world settings",
                null,
                store -> setAuthoritativeWorldSettings(store, requested));
        }
        if (isAuthoritativePhysicsStoreActive()) {
            setWorldSettingsDirect(requested);
            return PhysicsMutationHandle.completed("set physics world settings", null);
        }
        requireLegacyMutationAllowed("set physics world settings");
        return enqueueDirectRuntimeMutation("set physics world settings",
            () -> setWorldSettingsDirect(requested));
    }

    private void setWorldSettingsDirect(@Nonnull PhysicsWorldSettings settings) {
        validateStepModeSupported(settings.getStepMode());
        simulationRuntime.setWorldSettings(settings);
    }

    private void setAuthoritativeWorldSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsWorldSettings settings) {
        validateAuthoritativeStepModeSupported(store, settings.getStepMode());
        store.getResource(PhysicsWorldSettingsResource.getResourceType()).setSettings(settings);
        simulationRuntime.setWorldSettings(settings);
    }

    @Nonnull
    @Override
    public SpaceId createSpace(@Nonnull BackendId backendId) {
        return createSpace(backendId, "<unknown>", PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    @Override
    public SpaceId createSpace(@Nonnull BackendId backendId, @Nonnull String worldName) {
        return createSpace(backendId, worldName, PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    @Override
    public SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        return createSpace(backendId, SpaceId.next(), worldName, settings);
    }

    @Nonnull
    @Override
    public SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        if (isAuthoritativePhysicsStoreActive()) {
            Impulse.getRuntimeProvider(backendId);
            PhysicsStoreSpaceMutations.addSpace(authoritativePhysicsStore("create physics space"),
                UUID.randomUUID(),
                spaceId,
                backendId,
                settings);
            return spaceId;
        }
        requireLegacyMutationAllowed("create physics space");
        callDirectRuntime("create physics space",
            () -> createSpaceDirect(backendId, spaceId, worldName, settings));
        return spaceId;
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<SpaceId> createSpaceAsync(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        SpaceId spaceId = SpaceId.next();
        return createSpaceAsync(backendId, spaceId, worldName, settings);
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<SpaceId> createSpaceAsync(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        if (isAuthoritativePhysicsStoreActive()) {
            Impulse.getRuntimeProvider(backendId);
            PhysicsSpaceSettings requested = new PhysicsSpaceSettings(settings);
            return enqueueAuthoritativePhysicsStoreMutation("create physics space",
                spaceId,
                store -> PhysicsStoreSpaceMutations.addSpace(store,
                    UUID.randomUUID(),
                    spaceId,
                    backendId,
                    requested));
        }
        requireLegacyMutationAllowed("create physics space");
        return enqueueDirectRuntimeMutation("create physics space",
            spaceId,
            () -> createSpaceDirect(backendId, spaceId, worldName, settings));
    }

    @Nonnull
    private PhysicsSpaceBinding createSpaceDirect(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsSpaceBinding binding = spaceRuntime.createSpace(backendId,
            spaceId,
            worldName,
            settings,
            simulationRuntime.getWorldSettings().getStepMode());
        collisionRuntime.registerSpace(spaceId);
        markWorldChanged();
        return binding;
    }

    @Nullable
    private PhysicsSpaceBinding getSpaceBinding(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getBinding(spaceId);
    }

    @Override
    public boolean hasSpace(@Nonnull SpaceId spaceId) {
        if (isAuthoritativePhysicsStoreActive()) {
            return authoritativePhysicsStore("check physics space")
                .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
                .hasSpace(spaceId);
        }
        return spaceRuntime.getBinding(spaceId) != null;
    }

    @Nonnull
    private PhysicsSpaceBinding requireSpaceBinding(@Nonnull SpaceId spaceId) {
        return spaceRuntime.requireBinding(spaceId);
    }

    @Nonnull
    @Override
    public Collection<SpaceId> getSpaceIds() {
        if (isAuthoritativePhysicsStoreActive()) {
            return List.copyOf(authoritativePhysicsStore("list physics spaces")
                .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
                .spaceIds());
        }
        return spaceRuntime.getSpaceIds();
    }

    @Override
    public int getSpaceCount() {
        if (isAuthoritativePhysicsStoreActive()) {
            return authoritativePhysicsStore("count physics spaces")
                .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
                .size();
        }
        return spaceRuntime.getSpaceCount();
    }

    @Override
    public int refreshBodySnapshots() {
        if (isAuthoritativePhysicsStoreActive()) {
            return authoritativePhysicsStore("refresh copied physics body snapshots")
                .getResource(PhysicsSnapshotResource.getResourceType())
                .getLatestFrame()
                .bodies()
                .size();
        }
        return callDirectRuntime("refresh physics body snapshots", () -> {
            PublishedPhysicsSnapshotFrame frame = capturePublishedSnapshotFrameDirect(0L,
                0L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            return applyPublishedSnapshotFrame(frame);
        });
    }

    @Nonnull
    @Override
    public dev.hytalemodding.impulse.api.PhysicsBodySnapshot getBodySnapshot(@Nonnull UUID bodyUuid) {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        if (isAuthoritativePhysicsStoreActive()) {
            Store<PhysicsStore> store =
                authoritativePhysicsStore("read copied physics body snapshot");
            dev.hytalemodding.impulse.api.PhysicsBodySnapshot snapshot = getAuthoritativeBodySnapshot(store, bodyUuid);
            if (snapshot == null) {
                throw new IllegalStateException("No copied PhysicsStore body snapshot is available for "
                    + bodyUuid);
            }
            return snapshot;
        }
        dev.hytalemodding.impulse.api.PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyUuid);
        if (snapshot != null) {
            return snapshot;
        }
        return callDirectRuntime("refresh missing physics body snapshot",
            () -> getBodySnapshotDirect(bodyUuid));
    }

    @Nullable
    public dev.hytalemodding.impulse.api.PhysicsBodySnapshot getBodySnapshotIfRegistered(@Nonnull UUID bodyUuid) {
        return getBodySnapshotIfRegistered(bodyUuid, null);
    }

    @Nullable
    public dev.hytalemodding.impulse.api.PhysicsBodySnapshot getBodySnapshotIfRegistered(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (isAuthoritativePhysicsStoreActive()) {
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            Store<PhysicsStore> store =
                authoritativePhysicsStore("read optional copied physics body snapshot");
            PhysicsBodySnapshot snapshot = bodyRef != null && bodyRef.isValid()
                ? store.getResource(PhysicsSnapshotResource.getResourceType()).getBody(bodyRef)
                : store.getResource(PhysicsSnapshotResource.getResourceType()).getBody(bodyUuid);
            return snapshot != null ? toPublicBodySnapshot(store, snapshot) : null;
        }
        dev.hytalemodding.impulse.api.PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyUuid);
        if (snapshot != null) {
            return snapshot;
        }
        return callDirectRuntime("refresh optional physics body snapshot",
            () -> getBodySnapshotIfRegisteredDirect(bodyUuid));
    }

    @Nullable
    public dev.hytalemodding.impulse.api.PhysicsBodySnapshot getBodySnapshotIfRegistered(@Nonnull Ref<PhysicsStore> bodyRef) {
        Store<PhysicsStore> store = Objects.requireNonNull(bodyRef, "bodyRef").getStore();
        PhysicsThreading.requireWorldThread(store, "read optional copied physics body snapshot");
        PhysicsBodySnapshot snapshot = store.getResource(PhysicsSnapshotResource.getResourceType())
            .getBody(bodyRef);
        return snapshot != null ? toPublicBodySnapshot(store, snapshot) : null;
    }

    @Nonnull
    private dev.hytalemodding.impulse.api.PhysicsBodySnapshot getBodySnapshotDirect(@Nonnull UUID bodyUuid) {
        dev.hytalemodding.impulse.api.PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyUuid);
        if (snapshot != null) {
            return snapshot;
        }
        PhysicsBodyRegistration registration = bodyRegistry.getRegistration(bodyUuid);
        if (registration == null) {
            throw new IllegalArgumentException("Physics body uuid=" + bodyUuid + " is not registered");
        }
        return captureLiveBodySnapshot(registration);
    }

    @Nullable
    private dev.hytalemodding.impulse.api.PhysicsBodySnapshot getBodySnapshotIfRegisteredDirect(@Nonnull UUID bodyUuid) {
        dev.hytalemodding.impulse.api.PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyUuid);
        if (snapshot != null) {
            return snapshot;
        }
        PhysicsBodyRegistration registration = bodyRegistry.getRegistration(bodyUuid);
        return registration != null ? captureLiveBodySnapshot(registration) : null;
    }

    @Nullable
    private static dev.hytalemodding.impulse.api.PhysicsBodySnapshot getAuthoritativeBodySnapshot(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid) {
        PhysicsBodySnapshot snapshot = store.getResource(PhysicsSnapshotResource.getResourceType())
            .getBody(Objects.requireNonNull(bodyUuid, "bodyUuid"));
        return snapshot != null ? toPublicBodySnapshot(store, snapshot) : null;
    }

    private static int countAuthoritativeBodySnapshots(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        UUID spaceUuid = store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(Objects.requireNonNull(spaceId, "spaceId"));
        if (spaceUuid == null) {
            return 0;
        }
        int count = 0;
        for (PhysicsBodySnapshot body : store.getResource(PhysicsSnapshotResource.getResourceType())
            .getLatestFrame()
            .bodies()) {
            if (spaceUuid.equals(body.spaceUuid())) {
                count++;
            }
        }
        return count;
    }

    private static void forEachAuthoritativeBodySnapshot(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        UUID spaceUuid = authoritativeSpaceUuid(store, spaceId);
        if (spaceUuid == null) {
            return;
        }
        PhysicsBodyRegistrationResource registrations =
            store.getResource(PhysicsBodyRegistrationResource.getResourceType());
        for (PhysicsBodySnapshot body : authoritativeSnapshotFrame(store).bodies()) {
            if (!spaceUuid.equals(body.spaceUuid())) {
                continue;
            }
            PhysicsBodySnapshotEntry entry =
                authoritativeSnapshotEntry(store, registrations, body);
            if (entry != null) {
                consumer.accept(entry);
            }
        }
    }

    private static void forEachIndexedAuthoritativeBodySnapshot(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        UUID spaceUuid = authoritativeSpaceUuid(store, spaceId);
        if (spaceUuid == null) {
            return;
        }
        PhysicsBodyRegistrationResource registrations =
            store.getResource(PhysicsBodyRegistrationResource.getResourceType());
        for (PhysicsBodySnapshot body : authoritativeSnapshotFrame(store).bodies()) {
            if (!spaceUuid.equals(body.spaceUuid())) {
                continue;
            }
            PhysicsBodySnapshotEntry entry =
                authoritativeSnapshotEntry(store, registrations, body);
            if (entry != null) {
                visitor.accept(entry.bodyUuid(),
                    entry.snapshot(),
                    entry.spaceId(),
                    entry.kind(),
                    entry.persistenceMode());
            }
        }
    }

    private static int forEachAuthoritativeBodySnapshotNear(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        UUID spaceUuid = authoritativeSpaceUuid(store, spaceId);
        if (spaceUuid == null || radius < 0.0f || Float.isNaN(radius)) {
            return 0;
        }
        float radiusSquared = radius * radius;
        int candidates = 0;
        PhysicsBodyRegistrationResource registrations =
            store.getResource(PhysicsBodyRegistrationResource.getResourceType());
        for (PhysicsBodySnapshot body : authoritativeSnapshotFrame(store).bodies()) {
            if (!spaceUuid.equals(body.spaceUuid())) {
                continue;
            }
            PhysicsBodySnapshotEntry entry =
                authoritativeSnapshotEntry(store, registrations, body);
            if (entry == null) {
                continue;
            }
            candidates++;
            if (withinRadius(entry.snapshot(), center, radiusSquared)) {
                consumer.accept(entry);
            }
        }
        return candidates;
    }

    private static int forEachIndexedAuthoritativeBodySnapshotNear(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        UUID spaceUuid = authoritativeSpaceUuid(store, spaceId);
        if (spaceUuid == null || radius < 0.0f || Float.isNaN(radius)) {
            return 0;
        }
        float radiusSquared = radius * radius;
        int candidates = 0;
        PhysicsBodyRegistrationResource registrations =
            store.getResource(PhysicsBodyRegistrationResource.getResourceType());
        for (PhysicsBodySnapshot body : authoritativeSnapshotFrame(store).bodies()) {
            if (!spaceUuid.equals(body.spaceUuid())) {
                continue;
            }
            PhysicsBodySnapshotEntry entry =
                authoritativeSnapshotEntry(store, registrations, body);
            if (entry == null) {
                continue;
            }
            candidates++;
            if (withinRadius(entry.snapshot(), center, radiusSquared)) {
                visitor.accept(entry.bodyUuid(),
                    entry.snapshot(),
                    entry.spaceId(),
                    entry.kind(),
                    entry.persistenceMode());
            }
        }
        return candidates;
    }

    private static int forEachIndexedAuthoritativeBodySnapshotNearWithRefs(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotRefVisitor visitor) {
        UUID spaceUuid = authoritativeSpaceUuid(store, spaceId);
        if (spaceUuid == null || radius < 0.0f || Float.isNaN(radius)) {
            return 0;
        }
        float radiusSquared = radius * radius;
        int candidates = 0;
        PhysicsBodyRegistrationResource registrations =
            store.getResource(PhysicsBodyRegistrationResource.getResourceType());
        for (PhysicsBodySnapshot body : authoritativeSnapshotFrame(store).bodies()) {
            if (!spaceUuid.equals(body.spaceUuid())) {
                continue;
            }
            PhysicsBodySnapshotEntry entry =
                authoritativeSnapshotEntry(store, registrations, body);
            if (entry == null) {
                continue;
            }
            candidates++;
            if (withinRadius(entry.snapshot(), center, radiusSquared)) {
                visitor.accept(entry.bodyUuid(),
                    validSnapshotBodyRef(store, body),
                    entry.snapshot(),
                    entry.spaceId(),
                    entry.kind(),
                    entry.persistenceMode());
            }
        }
        return candidates;
    }

    @Nullable
    private static UUID authoritativeSpaceUuid(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        return store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(Objects.requireNonNull(spaceId, "spaceId"));
    }

    @Nonnull
    private static PhysicsSnapshotFrame authoritativeSnapshotFrame(
        @Nonnull Store<PhysicsStore> store) {
        return store.getResource(PhysicsSnapshotResource.getResourceType()).getLatestFrame();
    }

    @Nullable
    private static PhysicsBodySnapshotEntry authoritativeSnapshotEntry(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsBodyRegistrationResource registrations,
        @Nonnull PhysicsBodySnapshot body) {
        PhysicsBodyRegistrationView registration = registrations.getBodyRegistrationView(body.bodyUuid());
        if (registration == null) {
            return null;
        }
        return new PhysicsBodySnapshotEntry(body.bodyUuid(),
            toPublicBodySnapshot(store, body),
            registration.spaceId(),
            registration.kind(),
            registration.persistenceMode());
    }

    @Nullable
    private static Ref<PhysicsStore> validSnapshotBodyRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsBodySnapshot body) {
        Ref<PhysicsStore> bodyRef = body.bodyRef();
        return bodyRef != null && bodyRef.getStore() == store && bodyRef.isValid()
            ? bodyRef
            : null;
    }

    private static boolean withinRadius(@Nonnull dev.hytalemodding.impulse.api.PhysicsBodySnapshot snapshot,
        @Nonnull Vector3f center,
        float radiusSquared) {
        Objects.requireNonNull(center, "center");
        float dx = snapshot.positionX() - center.x;
        float dy = snapshot.positionY() - center.y;
        float dz = snapshot.positionZ() - center.z;
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    @Nonnull
    private static dev.hytalemodding.impulse.api.PhysicsBodySnapshot toPublicBodySnapshot(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsBodySnapshot body) {
        Ref<PhysicsStore> ref = body.bodyRef();
        if (ref == null || ref.getStore() != store || !ref.isValid()) {
            ref = store.getResource(PhysicsIdentityIndexResource.getResourceType())
                .getByUuid(body.bodyUuid());
        }
        boolean validRef = ref != null && ref.isValid();
        DynamicsComponent dynamics = validRef
            ? store.getComponent(ref, DynamicsComponent.getComponentType())
            : null;
        ColliderComponent collider = validRef
            ? store.getComponent(ref, ColliderComponent.getComponentType())
            : null;
        MaterialComponent material = validRef
            ? store.getComponent(ref, MaterialComponent.getComponentType())
            : null;
        CollisionFilterComponent filter = validRef
            ? store.getComponent(ref, CollisionFilterComponent.getComponentType())
            : null;
        ShapeComponent shape = validRef
            ? store.getComponent(ref, ShapeComponent.getComponentType())
            : null;

        Vector3f position = body.position();
        Quaternionf rotation = body.rotation();
        Vector3f linearVelocity = body.linearVelocity();
        Vector3f angularVelocity = body.angularVelocity();
        PhysicsBodyType bodyType = body.bodyType();
        ShapeType shapeType = shape != null ? shape.getShapeType() : ShapeType.UNKNOWN;
        boolean hasBoxHalfExtents = shapeType == ShapeType.BOX && shape != null;

        return dev.hytalemodding.impulse.api.PhysicsBodySnapshot.of(position.x,
            position.y,
            position.z,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w,
            linearVelocity.x,
            linearVelocity.y,
            linearVelocity.z,
            angularVelocity.x,
            angularVelocity.y,
            angularVelocity.z,
            bodyType,
            body.sleeping(),
            collider != null && collider.isSensor(),
            bodyType == PhysicsBodyType.DYNAMIC ? authoredMass(dynamics) : 0.0f,
            material != null ? material.getFriction() : 0.5f,
            material != null ? material.getRestitution() : 0.0f,
            dynamics != null ? dynamics.getLinearDamping() : 0.0f,
            dynamics != null ? dynamics.getAngularDamping() : 0.0f,
            filter != null ? filter.getCollisionGroup() : PhysicsCollisionFilters.DYNAMIC_BODY,
            filter != null ? filter.getCollisionMask() : PhysicsCollisionFilters.ALL,
            dynamics != null && dynamics.isContinuousCollisionEnabled(),
            body.centerOfMassOffsetY(),
            shapeType,
            hasBoxHalfExtents,
            hasBoxHalfExtents ? shape.getHalfExtentX() : 0.0f,
            hasBoxHalfExtents ? shape.getHalfExtentY() : 0.0f,
            hasBoxHalfExtents ? shape.getHalfExtentZ() : 0.0f,
            shape != null ? shape.getRadius() : 0.0f,
            shape != null ? shape.getHalfHeight() : 0.0f,
            shape != null ? shape.getAxis() : PhysicsAxis.Y);
    }

    private static float authoredMass(@Nullable DynamicsComponent dynamics) {
        return dynamics != null ? dynamics.getMass() : 1.0f;
    }

    @Nonnull
    private dev.hytalemodding.impulse.api.PhysicsBodySnapshot captureLiveBodySnapshot(@Nonnull PhysicsBodyRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        assertCanAccessLiveBackendDirectly("capture live physics body snapshot");
        PhysicsSpaceBinding space = requireSpaceBinding(registration.spaceId());
        dev.hytalemodding.impulse.api.PhysicsBodySnapshot snapshot = PhysicsBodySnapshots.read(space,
            registration.backendBodyHandle().value());
        if (snapshot == null) {
            throw new IllegalStateException(
                "No live physics body snapshot is available for " + registration.bodyUuid());
        }
        return snapshot;
    }

    /**
     * Captures an immutable snapshot frame on the store tick lane.
     *
     * <p>The generated {@code frameEpoch} and current {@code worldEpoch} govern
     * publication ordering and stale-frame rejection. {@code stepSequence} and
     * {@code serverTick} are copied through as external correlation metadata.</p>
     */
    @Nonnull
    private PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrameDirect(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled) {
        return capturePublishedSnapshotFrameDirect(stepSequence,
            serverTick,
            status,
            stepNanos,
            profilingEnabled,
            List.of(),
            0);
    }

    @Nonnull
    private PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrameDirect(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled,
        @Nonnull List<PhysicsFrameEvent> physicsEvents,
        int droppedBackendEventCount) {

        assertCanAccessLiveBackendDirectly("capture published physics snapshot frame");
        return lifecycleState.capturePublishedSnapshotFrame(spaceRuntime.getBindings(),
            bodyRegistry,
            stepSequence,
            serverTick,
            status,
            stepNanos,
            profilingEnabled,
            physicsEvents,
            droppedBackendEventCount);
    }

    private int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        return lifecycleState.applyPublishedSnapshotFrame(frame, bodyRegistry, 0L);
    }

    @Override
    public int getBodySnapshotCount() {
        if (isAuthoritativePhysicsStoreActive()) {
            return authoritativePhysicsStore("count copied physics body snapshots")
                .getResource(PhysicsSnapshotResource.getResourceType())
                .getLatestFrame()
                .bodies()
                .size();
        }
        return lifecycleState.bodySnapshotCount();
    }

    @Override
    public int getBodySnapshotCount(@Nonnull SpaceId spaceId) {
        if (isAuthoritativePhysicsStoreActive()) {
            return countAuthoritativeBodySnapshots(
                authoritativePhysicsStore("count copied physics body snapshots"),
                spaceId);
        }
        return lifecycleState.bodySnapshotCount(spaceId);
    }

    @Override
    public int getBodySnapshotCellCount() {
        if (isAuthoritativePhysicsStoreActive()) {
            return 0;
        }
        return lifecycleState.bodySnapshotCellCount();
    }

    @Nonnull
    private PhysicsStoreWorldCollisionStreamingResource authoritativeWorldCollisionStreaming() {
        Store<EntityStore> entityStore = owningStore;
        if (entityStore == null) {
            throw new IllegalStateException("Cannot access PhysicsStore world-collision streaming "
                + "before this resource is attached to an EntityStore");
        }
        return entityStore.getResource(PhysicsStoreWorldCollisionStreamingResource.getResourceType());
    }

    private void clearAuthoritativeWorldCollisionStreaming(@Nonnull Store<PhysicsStore> store) {
        if (!PhysicsChunkLifecycle.isEnabled() || owningStore == null) {
            return;
        }
        PhysicsTerrainMutationQueueResource queue =
            store.getResource(PhysicsTerrainMutationQueueResource.getResourceType());
        authoritativeWorldCollisionStreaming().retainSpaces(Set.of(), queue);
        queue.clear();
    }

    private int clearAuthoritativeWorldCollisionSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        int removed = 0;
        if (PhysicsChunkLifecycle.isEnabled() && owningStore != null) {
            PhysicsTerrainMutationQueueResource queue =
                store.getResource(PhysicsTerrainMutationQueueResource.getResourceType());
            removed = authoritativeWorldCollisionStreaming().clearSpace(spaceUuid, queue);
        }
        int directlyRemoved =
            PhysicsStoreTopologyMutations.clearTerrainForSpace(store, spaceUuid);
        return removed != 0 ? removed : directlyRemoved;
    }

    public void disablePhysicsChunkLifecycle() {
        if (isAuthoritativePhysicsStoreActive()) {
            return;
        }
        try {
            runDirectRuntimeMutation("disable PhysicsChunk lifecycle", this::disablePhysicsChunkLifecycleDirect);
        } catch (RejectedExecutionException ignored) {
            // The server can unload the subplugin after the store tick lane has already closed.
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log("Failed to disable PhysicsChunk lifecycle: %s",
                exception.getMessage());
        }
    }

    private void disablePhysicsChunkLifecycleDirect() {
        collisionRuntime.clearRetainedTerrain(spaceRuntime.getBindings());
        restoreCollisionLodFiltersDirect();
    }

    private void restoreCollisionLodFiltersDirect() {
        int fullDynamicMask = PhysicsCollisionFilters.TERRAIN
            | PhysicsCollisionFilters.DYNAMIC_BODY;
        for (PhysicsBodyRegistration registration : bodyRegistry.getRegistrations(PhysicsBodyKind.BODY)) {
            PhysicsSpaceBinding space = getSpaceBinding(registration.spaceId());
            if (space == null) {
                continue;
            }
            space.runtime().setBodyCollisionFilter(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value(),
                PhysicsCollisionFilters.DYNAMIC_BODY,
                fullDynamicMask);
            space.runtime().activateBody(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value());
        }
    }

    @Override
    public void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        if (isAuthoritativePhysicsStoreActive()) {
            forEachAuthoritativeBodySnapshot(
                authoritativePhysicsStore("iterate copied physics body snapshots"),
                spaceId,
                consumer);
            return;
        }
        lifecycleState.forEachBodySnapshot(spaceId, consumer);
    }

    public void forEachIndexedBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        if (isAuthoritativePhysicsStoreActive()) {
            forEachIndexedAuthoritativeBodySnapshot(
                authoritativePhysicsStore("iterate copied physics body snapshots"),
                spaceId,
                visitor);
            return;
        }
        lifecycleState.forEachIndexedBodySnapshot(spaceId, visitor);
    }

    @Override
    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        if (isAuthoritativePhysicsStoreActive()) {
            return forEachAuthoritativeBodySnapshotNear(
                authoritativePhysicsStore("iterate nearby copied physics body snapshots"),
                spaceId,
                center,
                radius,
                consumer);
        }
        return lifecycleState.forEachBodySnapshotNear(spaceId, center, radius, consumer);
    }

    public int forEachIndexedBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        if (isAuthoritativePhysicsStoreActive()) {
            return forEachIndexedAuthoritativeBodySnapshotNear(
                authoritativePhysicsStore("iterate nearby copied physics body snapshots"),
                spaceId,
                center,
                radius,
                visitor);
        }
        return lifecycleState.forEachIndexedBodySnapshotNear(spaceId, center, radius, visitor);
    }

    public int forEachIndexedBodySnapshotNearWithRefs(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotRefVisitor visitor) {
        if (isAuthoritativePhysicsStoreActive()) {
            return forEachIndexedAuthoritativeBodySnapshotNearWithRefs(
                authoritativePhysicsStore("iterate nearby copied physics body snapshots"),
                spaceId,
                center,
                radius,
                visitor);
        }
        return lifecycleState.forEachIndexedBodySnapshotNear(spaceId,
            center,
            radius,
            (bodyUuid, snapshot, bodySpaceId, kind, persistenceMode) ->
                visitor.accept(bodyUuid, null, snapshot, bodySpaceId, kind, persistenceMode));
    }

    @Override
    public void removeSpace(@Nonnull SpaceId spaceId) {
        removeSpace(spaceId, "<unknown>");
    }

    @Override
    public void removeSpace(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        if (isAuthoritativePhysicsStoreActive()) {
            Store<PhysicsStore> store = authoritativePhysicsStore("remove physics space");
            UUID spaceUuid = requireSpaceUuid(store, spaceId);
            clearAuthoritativeWorldCollisionSpace(store, spaceUuid);
            PhysicsStoreTopologyMutations.removeSpaceWithContents(store, spaceUuid);
            return;
        }
        requireLegacyMutationAllowed("remove physics space");
        runDirectRuntimeMutation("remove physics space", () -> removeSpaceDirect(spaceId, worldName));
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<SpaceId> removeSpaceAsync(@Nonnull SpaceId spaceId,
        @Nonnull String worldName) {
        if (isAuthoritativePhysicsStoreActive()) {
            return enqueueAuthoritativePhysicsStoreMutation("remove physics space",
                spaceId,
                store -> {
                    UUID spaceUuid = requireSpaceUuid(store, spaceId);
                    clearAuthoritativeWorldCollisionSpace(store, spaceUuid);
                    PhysicsStoreTopologyMutations.removeSpaceWithContents(store, spaceUuid);
                });
        }
        requireLegacyMutationAllowed("remove physics space");
        return enqueueDirectRuntimeMutation("remove physics space",
            spaceId,
            () -> removeSpaceDirect(spaceId, worldName));
    }

    private void removeSpaceDirect(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        PhysicsSpaceBinding removed = spaceRuntime.removeSpace(spaceId);
        if (removed == null) {
            collisionRuntime.clear(spaceId, null);
            return;
        }

        try {
            collisionRuntime.clear(spaceId, removed);
            jointRegistry.unregisterSpace(spaceId);
            for (PhysicsBodyRegistration registration : new ArrayList<>(bodyRegistry.getRegistrations())) {
                if (registration.spaceId().equals(spaceId)) {
                    destroyBody(registration.bodyUuid(), false);
                }
            }
            LOGGER.at(Level.FINE).log(
                "World %s removed physics space id=%s backend=%s",
                worldName,
                removed.spaceId(),
                removed.backendId());
            markWorldChanged();
        } finally {
            PhysicsSpaceRuntime.closeBindingSilently(removed, worldName, "removed physics space");
        }
    }

    @Override
    public void clearAllSpaces(@Nonnull String worldName) {
        if (isAuthoritativePhysicsStoreActive()) {
            Store<PhysicsStore> store = authoritativePhysicsStore("clear physics spaces");
            clearAuthoritativeWorldCollisionStreaming(store);
            PhysicsStoreRuntimeCleaner.clearAll(store);
            return;
        }
        requireLegacyMutationAllowed("clear physics spaces");
        runDirectRuntimeMutation("clear physics spaces", () -> clearAllSpacesDirect(worldName));
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<Void> clearAllSpacesAsync(@Nonnull String worldName) {
        if (isAuthoritativePhysicsStoreActive()) {
            return enqueueAuthoritativePhysicsStoreMutation("clear physics spaces",
                null,
                store -> {
                    clearAuthoritativeWorldCollisionStreaming(store);
                    PhysicsStoreRuntimeCleaner.clearAll(store);
                });
        }
        requireLegacyMutationAllowed("clear physics spaces");
        return enqueueDirectRuntimeMutation("clear physics spaces",
            () -> clearAllSpacesDirect(worldName));
    }

    private void clearAllSpacesDirect(@Nonnull String worldName) {
        RuntimeException failure = null;
        for (SpaceId spaceId : spaceRuntime.getSpaceIds()) {
            try {
                removeSpaceDirect(spaceId, worldName);
            } catch (RuntimeException exception) {
                failure = collectFailure(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Nonnull
    private static RuntimeException collectFailure(@Nullable RuntimeException failure,
        @Nonnull RuntimeException exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }

    /**
     * Clears runtime physics state by replacing each native backend space with an empty
     * space that keeps the same logical id, backend, settings, and gravity.
     */
    @Nonnull
    public PhysicsRuntimeResetResult resetRuntimeStateKeepingSpaces(@Nonnull String worldName) {
        if (isAuthoritativePhysicsStoreActive()) {
            Store<PhysicsStore> store = authoritativePhysicsStore("reset physics runtime state");
            clearAuthoritativeWorldCollisionStreaming(store);
            return PhysicsStoreTopologyMutations.clearBodiesKeepingSpaces(store);
        }
        requireLegacyMutationAllowed("reset physics runtime state");
        return callDirectRuntime("reset physics runtime state",
            () -> resetRuntimeStateKeepingSpacesDirect(worldName));
    }

    @Nonnull
    public CompletionStage<PhysicsRuntimeResetResult> resetRuntimeStateKeepingSpacesAsync(
        @Nonnull String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        if (isAuthoritativePhysicsStoreActive()) {
            World world = requireAuthoritativeWorld("reset physics runtime state");
            return PhysicsThreading.callWhenBackendIdleOnWorldThread(world,
                "reset physics runtime state",
                store -> {
                    clearAuthoritativeWorldCollisionStreaming(store);
                    return PhysicsStoreTopologyMutations.clearBodiesKeepingSpaces(store);
                });
        }
        CompletableFuture<PhysicsRuntimeResetResult> completion = new CompletableFuture<>();
        try {
            requireLegacyMutationAllowed("reset physics runtime state");
            completion.complete(resetRuntimeStateKeepingSpacesDirect(worldName));
        } catch (RuntimeException exception) {
            completion.completeExceptionally(exception);
        }
        return completion.minimalCompletionStage();
    }

    @Nonnull
    private PhysicsRuntimeResetResult resetRuntimeStateKeepingSpacesDirect(@Nonnull String worldName) {
        PhysicsRuntimeResetResult reset = spaceRuntime.resetKeepingSpaces(worldName,
            simulationRuntime.getWorldSettings().getStepMode());
        collisionRuntime.clearAll();
        clearRuntimeTopologyDirect(false);
        markWorldChanged();
        return reset;
    }

    @Nonnull
    @Override
    public PhysicsSpaceSettings getSpaceSettings(@Nonnull SpaceId spaceId) {
        if (isAuthoritativePhysicsStoreActive()) {
            PhysicsSpaceSettings settings = getPhysicsStoreSpaceSettings(
                authoritativePhysicsStore("read physics space settings"),
                spaceId);
            if (settings == null) {
                throw new IllegalArgumentException("PhysicsStore space id=" + spaceId.value()
                    + " is not registered");
            }
            return settings;
        }
        return spaceRuntime.getSpaceSettings(spaceId);
    }

    @Nonnull
    @Override
    public PhysicsSpaceSettings getSpaceSettings(@Nonnull Ref<PhysicsStore> spaceRef) {
        if (!isAuthoritativePhysicsStoreActive()) {
            throw new IllegalStateException("Cannot read PhysicsStore space settings by entity ref "
                + "when authoritative PhysicsStore mode is unavailable");
        }
        PhysicsSpaceSettings settings = getPhysicsStoreSpaceSettings(
            authoritativePhysicsStore("read physics space settings"),
            spaceRef);
        if (settings == null) {
            throw new IllegalArgumentException("PhysicsStore space ref=" + spaceRef
                + " is not registered");
        }
        return settings;
    }

    @Nonnull
    public PhysicsSpaceSettings getLiveSpaceSettings(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getLiveSpaceSettings(spaceId);
    }

    @Override
    public void setSpaceSettings(@Nonnull SpaceId spaceId, @Nonnull PhysicsSpaceSettings settings) {
        if (isAuthoritativePhysicsStoreActive()) {
            PhysicsStoreSpaceMutations.putSpaceSettings(
                authoritativePhysicsStore("set physics space settings"),
                spaceId,
                settings);
            return;
        }
        requireLegacyMutationAllowed("set physics space settings");
        PhysicsSpaceSettings requested = new PhysicsSpaceSettings(settings);
        runDirectRuntimeMutation("set physics space settings", () -> setSpaceSettingsDirect(spaceId, requested));
    }

    @Override
    public void setSpaceSettings(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull PhysicsSpaceSettings settings) {
        if (!isAuthoritativePhysicsStoreActive()) {
            throw new IllegalStateException("Cannot set PhysicsStore space settings by entity ref "
                + "when authoritative PhysicsStore mode is unavailable");
        }
        PhysicsStoreSpaceMutations.putSpaceSettings(
            authoritativePhysicsStore("set physics space settings"),
            spaceRef,
            settings);
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<SpaceId> setSpaceSettingsAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        if (isAuthoritativePhysicsStoreActive()) {
            PhysicsSpaceSettings requested = new PhysicsSpaceSettings(settings);
            return enqueueAuthoritativePhysicsStoreMutation("set physics space settings",
                spaceId,
                store -> PhysicsStoreSpaceMutations.putSpaceSettings(store, spaceId, requested));
        }
        requireLegacyMutationAllowed("set physics space settings");
        PhysicsSpaceSettings requested = new PhysicsSpaceSettings(settings);
        return enqueueDirectRuntimeMutation("set physics space settings",
            spaceId,
            () -> setSpaceSettingsDirect(spaceId, requested));
    }

    private void setSpaceSettingsDirect(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsWorldCollisionSettings previousCollisionSettings =
            spaceRuntime.getLiveSpaceSettings(spaceId).getWorldCollisionSettings();
        boolean worldCollisionSettingsChanged =
            worldCollisionStreamingSettingsChanged(previousCollisionSettings,
                settings.getWorldCollisionSettings());
        boolean terrainRepresentationChanged =
            previousCollisionSettings.isNativeVoxelTerrainEnabled()
                != settings.getWorldCollisionSettings().isNativeVoxelTerrainEnabled();
        boolean terrainMaterialChanged =
            Float.compare(previousCollisionSettings.getTerrainFriction(),
                settings.getWorldCollisionSettings().getTerrainFriction()) != 0
                || Float.compare(previousCollisionSettings.getTerrainRestitution(),
                    settings.getWorldCollisionSettings().getTerrainRestitution()) != 0;
        boolean worldCollisionDisabled =
            settings.getWorldCollisionSettings().getWorldCollisionMode() == WorldCollisionMode.NONE
                && previousCollisionSettings.getWorldCollisionMode() != WorldCollisionMode.NONE;
        spaceRuntime.setSpaceSettings(spaceId, settings);
        if (worldCollisionDisabled || terrainRepresentationChanged || terrainMaterialChanged) {
            collisionRuntime.clear(requireSpaceBinding(spaceId));
        } else if (worldCollisionSettingsChanged) {
            collisionRuntime.incrementStreamingRevision(spaceId);
        }
    }

    private static boolean worldCollisionStreamingSettingsChanged(
        @Nonnull PhysicsWorldCollisionSettings previous,
        @Nonnull PhysicsWorldCollisionSettings next) {
        return previous.getWorldCollisionMode() != next.getWorldCollisionMode()
            || previous.getWorldCollisionRadius() != next.getWorldCollisionRadius()
            || previous.getWorldCollisionBodyRadius() != next.getWorldCollisionBodyRadius()
            || previous.getWorldCollisionTtlTicks() != next.getWorldCollisionTtlTicks()
            || previous.isNativeVoxelTerrainEnabled() != next.isNativeVoxelTerrainEnabled();
    }

    private void validateStepModeSupported(@Nonnull PhysicsStepMode stepMode) {
        spaceRuntime.validateStepModeSupported(stepMode);
    }

    @Override
    public void destroyBody(@Nonnull UUID bodyUuid) {
        UUID checkedBodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
        if (isAuthoritativePhysicsStoreActive()) {
            PhysicsStoreTopologyMutations.destroyBody(
                authoritativePhysicsStore("destroy physics body"),
                checkedBodyUuid);
            return;
        }
        requireLegacyMutationAllowed("destroy physics body");
        destroyBody(checkedBodyUuid, true);
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<UUID> destroyBodyAsync(@Nonnull UUID bodyUuid) {
        UUID checkedBodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
        if (isAuthoritativePhysicsStoreActive()) {
            World world = requireAuthoritativeWorld("destroy physics body");
            return PhysicsMutationHandle.fromCompletion("destroy physics body",
                checkedBodyUuid,
                PhysicsThreading.callWhenBackendIdleOnWorldThread(world,
                    "destroy physics body",
                    store -> {
                        PhysicsStoreTopologyMutations.destroyBody(store, checkedBodyUuid);
                        return null;
                    }));
        }
        requireLegacyMutationAllowed("destroy physics body");
        return enqueueDirectRuntimeMutation("destroy physics body",
            checkedBodyUuid,
            () -> destroyBodyDirect(checkedBodyUuid, true));
    }

    private void destroyBody(@Nonnull UUID bodyUuid, boolean removeFromSpace) {
        requireLegacyMutationAllowed("destroy physics body");
        runDirectRuntimeMutation("destroy physics body", () -> destroyBodyDirect(bodyUuid, removeFromSpace));
    }

    private void destroyBodyDirect(@Nonnull UUID bodyUuid, boolean removeFromSpace) {
        bodyRuntime.destroyBody(bodyUuid, removeFromSpace);
    }

    @Nullable
    @Override
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull UUID bodyUuid) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativePhysicsStore("read physics body registration view")
                .getResource(PhysicsBodyRegistrationResource.getResourceType())
                .getBodyRegistrationView(bodyUuid);
        }
        return bodyRegistry.getPublishedRegistrationView(bodyUuid);
    }

    @Nullable
    @Override
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull Ref<PhysicsStore> bodyRef) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativePhysicsStore("read physics body registration view")
                .getResource(PhysicsBodyRegistrationResource.getResourceType())
                .getBodyRegistrationView(bodyRef);
        }
        return null;
    }

    @Nonnull
    @Override
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews() {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativePhysicsStore("read physics body registration views")
                .getResource(PhysicsBodyRegistrationResource.getResourceType())
                .getBodyRegistrationViews();
        }
        return bodyRegistry.getPublishedRegistrationViews();
    }

    @Override
    public int getBodyRegistrationCount() {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativePhysicsStore("read physics body registration count")
                .getResource(PhysicsBodyRegistrationResource.getResourceType())
                .getBodyRegistrationCount();
        }
        return bodyRegistry.getPublishedRegistrationCount();
    }

    @Override
    public int getBodyRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativePhysicsStore("read physics body registration count")
                .getResource(PhysicsBodyRegistrationResource.getResourceType())
                .getBodyRegistrationCount(persistenceMode);
        }
        return bodyRegistry.getPublishedRegistrationCount(persistenceMode);
    }

    @Nonnull
    @Override
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews(@Nonnull PhysicsBodyKind kind) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativePhysicsStore("read physics body registration views")
                .getResource(PhysicsBodyRegistrationResource.getResourceType())
                .getBodyRegistrationViews(kind);
        }
        return bodyRegistry.getPublishedRegistrationViews(kind);
    }

    @Nonnull
    @Override
    public Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativeProjectionIndex("read physics body attachments")
                .getAttachments(bodyRef);
        }
        return visualRuntime.getAttachments(bodyRef);
    }

    @Nonnull
    @Override
    public Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            PhysicsProjectionIndexResource projection =
                authoritativeProjectionIndex("read physics body attachments");
            return bodyRef != null && bodyRef.isValid()
                ? projection.getAttachments(bodyRef)
                : projection.getAttachments(bodyUuid);
        }
        return visualRuntime.getAttachments(bodyUuid, bodyRef);
    }

    @Override
    public boolean hasBodyAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativeProjectionIndex("check physics body attachments")
                .hasAttachments(bodyRef);
        }
        return visualRuntime.hasAttachments(bodyRef);
    }

    @Override
    public boolean hasBodyAttachments(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            PhysicsProjectionIndexResource projection =
                authoritativeProjectionIndex("check physics body attachments");
            return bodyRef != null && bodyRef.isValid()
                ? projection.hasAttachments(bodyRef)
                : projection.hasAttachments(bodyUuid);
        }
        return visualRuntime.hasAttachments(bodyUuid, bodyRef);
    }

    public void unregisterBodyAttachment(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> attachment) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            authoritativeProjectionIndex("unregister physics body attachment")
                .unregisterAttachment(bodyUuid, bodyRef, attachment);
            return;
        }
        visualRuntime.unregisterAttachment(bodyUuid, bodyRef, attachment);
    }

    public void setGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> proxy) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            authoritativeProjectionIndex("set generated visual proxy")
                .setGeneratedVisualProxy(bodyUuid, bodyRef, proxy);
            return;
        }
        visualRuntime.setGeneratedVisualProxy(bodyUuid, bodyRef, proxy);
    }

    public void clearGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            authoritativeProjectionIndex("clear generated visual proxy")
                .clearGeneratedVisualProxyForBodyRef(bodyUuid, bodyRef);
            return;
        }
        visualRuntime.clearGeneratedVisualProxy(bodyUuid, bodyRef);
    }

    public boolean clearGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> expectedProxy) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            PhysicsProjectionIndexResource projection =
                authoritativeProjectionIndex("clear generated visual proxy");
            Ref<EntityStore> registered = bodyRef != null
                ? projection.getGeneratedVisualProxy(bodyRef)
                : projection.getGeneratedVisualProxy(bodyUuid);
            if (!sameRef(registered, expectedProxy)) {
                return false;
            }
            projection.clearGeneratedVisualProxy(bodyUuid, bodyRef, expectedProxy);
            return true;
        }
        return visualRuntime.clearGeneratedVisualProxy(bodyUuid, bodyRef, expectedProxy);
    }

    public void setSyntheticVisualInterests(@Nonnull Collection<VisualInterest> interests) {
        visualRuntime.setSyntheticVisualInterests(interests);
    }

    @Nonnull
    public List<VisualInterest> getSyntheticVisualInterests() {
        return visualRuntime.getSyntheticVisualInterests();
    }

    public void clearSyntheticVisualInterests() {
        visualRuntime.clearSyntheticVisualInterests();
    }

    private void clearBodyStateDirect() {
        clearRuntimeTopologyDirect(false);
        markWorldChanged();
    }

    @Nonnull
    public BodySyncState getOrCreateBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return runtimeState.getOrCreateBodySyncState(entityRef);
    }

    @Nullable
    public BodySyncState getBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return runtimeState.getBodySyncState(entityRef);
    }

    public void clearBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        runtimeState.clearBodySyncState(entityRef);
    }

    @Nonnull
    public BodyVisualInterestState getOrCreateBodyVisualInterestState(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        BodyVisualInterestState state =
            visualRuntime.getOrCreateBodyVisualInterestState(bodyUuid, bodyRef);
        state.advanceVisualInterestTick(visualInterestTick.get());
        return state;
    }

    @Nullable
    public BodyVisualInterestState getBodyVisualInterestState(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        BodyVisualInterestState state = visualRuntime.getBodyVisualInterestState(bodyUuid,
            bodyRef);
        if (state != null) {
            state.advanceVisualInterestTick(visualInterestTick.get());
        }
        return state;
    }

    public long advanceVisualInterestTick() {
        return visualInterestTick.incrementAndGet();
    }

    public void markBodyControlled(@Nonnull Ref<PhysicsStore> bodyRef) {
        controlRuntime.markBodyControlled(bodyRef);
    }

    public void clearControlledBody(@Nonnull Ref<PhysicsStore> bodyRef) {
        controlRuntime.clearControlledBody(bodyRef);
    }

    public boolean isBodyControlled(@Nonnull Ref<PhysicsStore> bodyRef) {
        return controlRuntime.isBodyControlled(bodyRef);
    }

    public void disableControlLifecycle() {
        controlRuntime.clear();
    }

    private void copyFrom(@Nonnull PhysicsWorldResource other) {
        runDirectRuntimeMutation("copy physics world resource", () -> copyFromDirect(other));
    }

    private void copyFromDirect(@Nonnull PhysicsWorldResource other) {
        if (this == other) {
            return;
        }
        PhysicsWorldRuntimeResource otherRuntime = require(other);
        spaceRuntime.clearLiveTopology("<copy>");
        clearRuntimeTopologyDirect(true);
        simulationRuntime.copyFrom(otherRuntime.simulationRuntime);
        markWorldChanged();
    }

    private void clearRuntimeTopologyDirect(boolean clearCollision) {
        bodyRuntime.clearBodyStateWithoutMarkingWorldChanged();
        if (clearCollision) {
            collisionRuntime.clearAllAndUnregisterSpaces();
        }
    }

    private void markWorldChanged() {
        lifecycleState.markWorldChanged(bodyRegistry, false);
    }

    @FunctionalInterface
    private interface DirectRuntimeMutation {

        void run() throws Exception;
    }

    @FunctionalInterface
    private interface DirectRuntimeCallable<T> {

        T call() throws Exception;
    }

    @Nonnull
    @Override
    public PhysicsWorldResource clone() {
        PhysicsWorldRuntimeResource copy = new PhysicsWorldRuntimeResource();
        copy.copyFrom(this);
        return copy;
    }
}
