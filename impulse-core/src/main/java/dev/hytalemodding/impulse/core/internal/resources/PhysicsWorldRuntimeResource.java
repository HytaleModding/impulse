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
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.PhysicsControlRuntimeState;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreRuntimeCleaner;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreTopologyMutations;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource.SpaceWorldCollisionSettings;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState.BodySyncState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntime;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshots;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotVisitor;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsChunkBoundaryRuntime;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsChunkBoundaryRuntime.ChunkBoundarySafeState;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistration;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistry;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerGateway;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerHandle;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.BodyVisualInterestState;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.VisualInterest;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionBuildOptions;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsWorldCollisionRuntime;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.internal.store.integration.PhysicsStoreEarlyPluginProbe;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreSnapshotFrame;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
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
    private final PhysicsChunkBoundaryRuntime chunkRuntime = new PhysicsChunkBoundaryRuntime();
    private final PhysicsVisualRuntime visualRuntime = new PhysicsVisualRuntime(this::clearBodySyncState);
    private final PhysicsWorldLifecycleState lifecycleState = new PhysicsWorldLifecycleState();
    private final PhysicsBodyRuntime bodyRuntime = new PhysicsBodyRuntime(spaceRuntime,
        bodyRegistry,
        runtimeState,
        controlRuntime,
        jointRegistry,
        chunkRuntime,
        visualRuntime,
        lifecycleState,
        this::markWorldChanged);

    private final AtomicLong visualInterestTick = new AtomicLong();
    private final PhysicsOwnerGateway ownerGateway = new PhysicsOwnerGateway();
    @Nullable
    private Store<EntityStore> owningStore;

    public PhysicsWorldRuntimeResource() {
        ControlLifecycle.registerResource(this);
        WorldCollisionLifecycle.registerResource(this);
    }

    @Nonnull
    public static PhysicsWorldRuntimeResource require(@Nonnull Store<EntityStore> store) {
        return require(store.getResource(PhysicsWorldResource.getResourceType()));
    }

    @Nonnull
    public static PhysicsWorldRuntimeResource require(@Nonnull PhysicsWorldResource resource) {
        if (resource instanceof PhysicsWorldRuntimeResource runtime) {
            return runtime;
        }
        throw new IllegalStateException(
            "Physics world resource is not the Impulse runtime implementation");
    }

    public void attachOwnerExecutor(@Nonnull PhysicsOwnerHandle ownerExecutor) {
        ownerGateway.attachOwnerExecutor(ownerExecutor);
    }

    public void attachEntityStore(@Nonnull Store<EntityStore> store) {
        owningStore = Objects.requireNonNull(store, "store");
    }

    public void detachOwnerExecutor(@Nonnull PhysicsOwnerHandle ownerExecutor) {
        ownerGateway.detachOwnerExecutor(ownerExecutor);
        if (!ownerGateway.hasOwnerExecutor()) {
            lifecycleState.publishDetachedOwnerRegistrationViews(bodyRegistry);
        }
    }

    public void detachEntityStore(@Nonnull Store<EntityStore> store) {
        if (owningStore == store) {
            owningStore = null;
        }
    }

    @Nonnull
    public World requireAuthoritativeWorldForPhysicsStore(@Nonnull String operation) {
        return requireAuthoritativeWorld(operation);
    }

    public boolean canAccessLiveBackendDirectly() {
        return ownerGateway.canAccessLiveBackendDirectly();
    }

    public void rejectSynchronousCompletionCallbackWait(@Nonnull String operation) {
        ownerGateway.rejectSynchronousCompletionCallbackWait(operation);
    }

    public long worldEpoch() {
        return lifecycleState.worldEpoch();
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

    public void assertCanAccessLiveBackendDirectly(@Nonnull String operation) {
        ownerGateway.assertCanAccessLiveBackendDirectly(operation);
    }

    private void requireLegacyMutationAllowed(@Nonnull String operation) {
        if (!isAuthoritativePhysicsStoreActive()) {
            return;
        }
        throw new IllegalStateException("Legacy PhysicsWorldResource mutation is disabled while "
            + "authoritative PhysicsStore is active: " + operation
            + ". Route this operation through PhysicsStore rows or a PhysicsStore-backed "
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
        PhysicsStoreThreading.requireWorldThread(store, operation);
        return store;
    }

    @Nonnull
    private static Store<PhysicsStore> physicsStore(@Nonnull World world) {
        return ((PhysicsStoreWorld) Objects.requireNonNull(world, "world")).getPhysicsStore()
            .getStore();
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
        store.getResource(PhysicsRuntimeResource.getResourceType())
            .forEachSpaceBinding((spaceUuid, backendId, spaceHandle, backendRuntime) -> {
                if (!backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                    unsupportedSpaces.add(spaceUuid + " backend=" + backendId.value());
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
            PhysicsStoreThreading.executeOnWorldThread(world, operation, mutation));
    }

    public void runOwnerMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        ownerGateway.run(operation, mutation);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> enqueueOwnerMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        return enqueueOwnerMutation(operation, null, mutation);
    }


    @Nonnull
    public <T> PhysicsMutationHandle<T> enqueueOwnerMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        return ownerGateway.enqueue(operation, value, mutation);
    }


    @Nonnull
    public <T> T callOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        return ownerGateway.call(operation, callable);
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
        runOwnerMutation("set physics world settings", () -> setWorldSettingsDirect(requested));
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
        return enqueueOwnerMutation("set physics world settings",
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
        callOwner("create physics space",
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
        return enqueueOwnerMutation("create physics space",
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
    public PhysicsSpaceBinding getSpaceBinding(@Nonnull SpaceId spaceId) {
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
    public PhysicsSpaceBinding requireSpaceBinding(@Nonnull SpaceId spaceId) {
        return spaceRuntime.requireBinding(spaceId);
    }

    @Nonnull
    public Collection<PhysicsSpaceBinding> getSpaceBindings() {
        return spaceRuntime.getBindings();
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

    /**
     * Iterate spaces without allocating a snapshot collection.
     * Use this from tick systems that do not mutate the space map while iterating.
     */
    @Nonnull
    public Iterable<PhysicsSpaceBinding> iterateSpaceBindings() {
        return spaceRuntime.iterateBindings();
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
        return callOwner("refresh physics body snapshots", () -> {
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
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull RigidBodyKey bodyKey) {
        if (isAuthoritativePhysicsStoreActive()) {
            Store<PhysicsStore> store =
                authoritativePhysicsStore("read copied physics body snapshot");
            PhysicsBodySnapshot snapshot = getAuthoritativeBodySnapshot(store, bodyKey);
            if (snapshot == null) {
                throw new IllegalStateException("No copied PhysicsStore body snapshot is available for "
                    + bodyKey);
            }
            return snapshot;
        }
        PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        return callOwner("refresh missing physics body snapshot",
            () -> getBodySnapshotDirect(bodyKey));
    }

    @Nullable
    public PhysicsBodySnapshot getBodySnapshotIfRegistered(@Nonnull RigidBodyKey bodyKey) {
        if (isAuthoritativePhysicsStoreActive()) {
            return getAuthoritativeBodySnapshot(
                authoritativePhysicsStore("read optional copied physics body snapshot"),
                bodyKey);
        }
        PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        return callOwner("refresh optional physics body snapshot",
            () -> getBodySnapshotIfRegisteredDirect(bodyKey));
    }

    @Nonnull
    private PhysicsBodySnapshot getBodySnapshotDirect(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        return captureLiveBodySnapshot(registration);
    }

    @Nullable
    private PhysicsBodySnapshot getBodySnapshotIfRegisteredDirect(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        PhysicsBodyRegistration registration = bodyRegistry.getRegistration(bodyKey);
        return registration != null ? captureLiveBodySnapshot(registration) : null;
    }

    @Nullable
    private static PhysicsBodySnapshot getAuthoritativeBodySnapshot(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull RigidBodyKey bodyKey) {
        PhysicsStoreBodySnapshot snapshot = store.getResource(PhysicsSnapshotResource.getResourceType())
            .getBody(Objects.requireNonNull(bodyKey, "bodyKey").value());
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
        for (PhysicsStoreBodySnapshot body : store.getResource(PhysicsSnapshotResource.getResourceType())
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
        for (PhysicsStoreBodySnapshot body : authoritativeSnapshotFrame(store).bodies()) {
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
        for (PhysicsStoreBodySnapshot body : authoritativeSnapshotFrame(store).bodies()) {
            if (!spaceUuid.equals(body.spaceUuid())) {
                continue;
            }
            PhysicsBodySnapshotEntry entry =
                authoritativeSnapshotEntry(store, registrations, body);
            if (entry != null) {
                visitor.accept(entry.bodyKey(),
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
        for (PhysicsStoreBodySnapshot body : authoritativeSnapshotFrame(store).bodies()) {
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
        for (PhysicsStoreBodySnapshot body : authoritativeSnapshotFrame(store).bodies()) {
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
                visitor.accept(entry.bodyKey(),
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
    private static PhysicsStoreSnapshotFrame authoritativeSnapshotFrame(
        @Nonnull Store<PhysicsStore> store) {
        return store.getResource(PhysicsSnapshotResource.getResourceType()).getLatestFrame();
    }

    @Nullable
    private static PhysicsBodySnapshotEntry authoritativeSnapshotEntry(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsBodyRegistrationResource registrations,
        @Nonnull PhysicsStoreBodySnapshot body) {
        RigidBodyKey bodyKey = RigidBodyKey.of(body.bodyUuid());
        PhysicsBodyRegistrationView registration = registrations.getBodyRegistrationView(bodyKey);
        if (registration == null) {
            return null;
        }
        return new PhysicsBodySnapshotEntry(bodyKey,
            toPublicBodySnapshot(store, body),
            registration.spaceId(),
            registration.kind(),
            registration.persistenceMode());
    }

    private static boolean withinRadius(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Vector3f center,
        float radiusSquared) {
        Objects.requireNonNull(center, "center");
        float dx = snapshot.positionX() - center.x;
        float dy = snapshot.positionY() - center.y;
        float dz = snapshot.positionZ() - center.z;
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    @Nonnull
    private static PhysicsBodySnapshot toPublicBodySnapshot(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsStoreBodySnapshot body) {
        Ref<PhysicsStore> ref = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(body.bodyUuid());
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

        return PhysicsBodySnapshot.of(position.x,
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
    public PhysicsBodySnapshot captureLiveBodySnapshot(@Nonnull PhysicsBodyRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        assertCanAccessLiveBackendDirectly("capture live physics body snapshot");
        PhysicsSpaceBinding space = requireSpaceBinding(registration.spaceId());
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshots.read(space,
            registration.backendBodyHandle().value());
        if (snapshot == null) {
            throw new IllegalStateException(
                "No live physics body snapshot is available for " + registration.bodyKey());
        }
        return snapshot;
    }

    /**
     * Captures an immutable snapshot frame on the physics owner lane.
     *
     * <p>The generated {@code frameEpoch} and current {@code worldEpoch} govern
     * publication ordering and stale-frame rejection. {@code stepSequence} and
     * {@code serverTick} are copied through as external correlation metadata.</p>
     */
    @Nonnull
    public PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrame(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled) {
        return capturePublishedSnapshotFrame(stepSequence,
            serverTick,
            status,
            stepNanos,
            profilingEnabled,
            List.of(),
            0);
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrame(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled,
        @Nonnull List<PhysicsFrameEvent> physicsEvents,
        int droppedBackendEventCount) {
        return callOwner("capture published physics snapshot frame",
            () -> capturePublishedSnapshotFrameDirect(stepSequence,
                serverTick,
                status,
                stepNanos,
                profilingEnabled,
                physicsEvents,
                droppedBackendEventCount));
    }

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

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        return applyPublishedSnapshotFrame(frame, 0L);
    }

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame,
        long publicationServerTick) {
        return lifecycleState.applyPublishedSnapshotFrame(frame, bodyRegistry, publicationServerTick);
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame getLatestPublishedFrame() {
        return lifecycleState.latestPublishedFrame();
    }

    public long getLatestSnapshotAppliedNanos() {
        return lifecycleState.latestSnapshotAppliedNanos();
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
    public WorldVoxelCollisionCache worldCollisionCache() {
        return collisionRuntime.worldVoxelCollisionCache();
    }

    @Nonnull
    @Override
    public WorldCollisionBuildStats rebuildWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        if (isAuthoritativePhysicsStoreActive()) {
            requireWorldCollisionLifecycleEnabled();
            Store<PhysicsStore> store = authoritativePhysicsStore("rebuild world collision");
            SpaceWorldCollisionSettings settings =
                requireAuthoritativeWorldCollisionSettings(store, spaceId);
            PhysicsTerrainMutationQueueResource queue =
                store.getResource(PhysicsTerrainMutationQueueResource.getResourceType());
            int removed = clearAuthoritativeWorldCollisionSpace(store, settings.spaceUuid());
            WorldCollisionPrewarmStats stats = authoritativeWorldCollisionStreaming()
                .ensureAround(world,
                    settings.spaceUuid(),
                    queue,
                    List.of(center),
                    radius,
                    Math.max(0L, world.getTick()),
                    null,
                    settings.buildOptions());
            return withRemovedBodies(stats.buildStats(), stats.buildStats().removedBodies() + removed);
        }
        requireLegacyMutationAllowed("rebuild world collision");
        requireWorldCollisionLifecycleEnabled();
        return callOwner("rebuild world collision", () -> {
            PhysicsSpaceBinding space = requireSpaceBinding(spaceId);
            requireWorldCollisionSpaceEnabled(spaceId);
            WorldCollisionBuildOptions buildOptions =
                WorldCollisionBuildOptions.fromSettings(getLiveSpaceSettings(spaceId)
                    .getWorldCollisionSettings());
            return collisionRuntime.rebuildAround(world,
                space,
                center,
                radius,
                buildOptions);
        });
    }

    @Nonnull
    @Override
    public WorldCollisionBuildStats refreshWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        if (isAuthoritativePhysicsStoreActive()) {
            requireWorldCollisionLifecycleEnabled();
            Store<PhysicsStore> store = authoritativePhysicsStore("refresh world collision");
            SpaceWorldCollisionSettings settings =
                requireAuthoritativeWorldCollisionSettings(store, spaceId);
            return authoritativeWorldCollisionStreaming().refreshAround(world,
                settings.spaceUuid(),
                store.getResource(PhysicsTerrainMutationQueueResource.getResourceType()),
                center,
                radius,
                Math.max(0L, world.getTick()),
                null,
                settings.buildOptions());
        }
        requireLegacyMutationAllowed("refresh world collision");
        requireWorldCollisionLifecycleEnabled();
        return callOwner("refresh world collision", () -> {
            PhysicsSpaceBinding space = requireSpaceBinding(spaceId);
            requireWorldCollisionSpaceEnabled(spaceId);
            WorldCollisionBuildOptions buildOptions =
                WorldCollisionBuildOptions.fromSettings(getLiveSpaceSettings(spaceId)
                    .getWorldCollisionSettings());
            return collisionRuntime.refreshAround(world,
                space,
                center,
                radius,
                buildOptions);
        });
    }

    @Nonnull
    @Override
    public WorldCollisionPrewarmStats ensureWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        if (isAuthoritativePhysicsStoreActive()) {
            requireWorldCollisionLifecycleEnabled();
            Objects.requireNonNull(centers, "centers");
            Store<PhysicsStore> store = authoritativePhysicsStore("ensure world collision");
            SpaceWorldCollisionSettings settings =
                requireAuthoritativeWorldCollisionSettings(store, spaceId);
            return authoritativeWorldCollisionStreaming().ensureAround(world,
                settings.spaceUuid(),
                store.getResource(PhysicsTerrainMutationQueueResource.getResourceType()),
                centers,
                radius,
                tick,
                null,
                settings.buildOptions());
        }
        requireLegacyMutationAllowed("ensure world collision");
        Objects.requireNonNull(centers, "centers");
        requireWorldCollisionLifecycleEnabled();
        return callOwner("ensure world collision", () -> {
            PhysicsSpaceBinding space = requireSpaceBinding(spaceId);
            requireWorldCollisionSpaceEnabled(spaceId);
            WorldCollisionBuildOptions buildOptions =
                WorldCollisionBuildOptions.fromSettings(getLiveSpaceSettings(spaceId)
                    .getWorldCollisionSettings());
            return collisionRuntime.ensureAround(world,
                space,
                centers,
                radius,
                tick,
                buildOptions);
        });
    }

    @Override
    public int clearWorldCollision(@Nonnull SpaceId spaceId) {
        if (isAuthoritativePhysicsStoreActive()) {
            Store<PhysicsStore> store = authoritativePhysicsStore("clear world collision");
            UUID spaceUuid = requireSpaceUuid(store, spaceId);
            return clearAuthoritativeWorldCollisionSpace(store, spaceUuid);
        }
        requireLegacyMutationAllowed("clear world collision");
        return callOwner("clear world collision", () -> {
            PhysicsSpaceBinding space = requireSpaceBinding(spaceId);
            return collisionRuntime.clear(space);
        });
    }

    public long worldCollisionStreamingRevision(@Nonnull SpaceId spaceId) {
        return collisionRuntime.streamingRevision(spaceId);
    }

    @Nonnull
    @Override
    public WorldCollisionStats getWorldCollisionStats() {
        if (isAuthoritativePhysicsStoreActive()) {
            return WorldCollisionLifecycle.isEnabled()
                ? authoritativeWorldCollisionStreaming().stats()
                : new WorldCollisionStats(0, 0, 0, 0);
        }
        return callOwner("read world collision stats", collisionRuntime::getStats);
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

    @Nonnull
    private SpaceWorldCollisionSettings requireAuthoritativeWorldCollisionSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        UUID spaceUuid = requireSpaceUuid(store, spaceId);
        Ref<PhysicsStore> spaceRef = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);
        if (spaceRef == null || !spaceRef.isValid()) {
            throw new IllegalStateException("PhysicsStore space id=" + spaceId.value()
                + " is not bound yet");
        }
        WorldCollisionComponent component =
            store.getComponent(spaceRef, WorldCollisionComponent.getComponentType());
        WorldCollisionComponent settings = component != null ? component : new WorldCollisionComponent();
        if (settings.getMode() == WorldCollisionMode.NONE) {
            throw new IllegalStateException("World collision is disabled for space " + spaceId);
        }
        return new SpaceWorldCollisionSettings(spaceUuid,
            settings.getMode(),
            settings.getEntityChunkBoundaryMode(),
            settings.isNativeVoxelTerrainEnabled(),
            settings.getRadius(),
            settings.getBodyRadius(),
            settings.getTtlTicks(),
            settings.getTerrainFriction(),
            settings.getTerrainRestitution());
    }

    private void clearAuthoritativeWorldCollisionStreaming(@Nonnull Store<PhysicsStore> store) {
        if (!WorldCollisionLifecycle.isEnabled() || owningStore == null) {
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
        if (WorldCollisionLifecycle.isEnabled() && owningStore != null) {
            PhysicsTerrainMutationQueueResource queue =
                store.getResource(PhysicsTerrainMutationQueueResource.getResourceType());
            removed = authoritativeWorldCollisionStreaming().clearSpace(spaceUuid, queue);
        }
        int directlyRemoved =
            PhysicsStoreTopologyMutations.clearTerrainForSpace(store, spaceUuid);
        return removed != 0 ? removed : directlyRemoved;
    }

    @Nonnull
    private static WorldCollisionBuildStats withRemovedBodies(
        @Nonnull WorldCollisionBuildStats stats,
        int removedBodies) {
        return new WorldCollisionBuildStats(stats.scannedBlocks(),
            stats.solidBlocks(),
            stats.culledInteriorBlocks(),
            stats.fullCubeRuns(),
            stats.detailBoxes(),
            stats.colliderBodies(),
            removedBodies,
            stats.sectionsBuilt(),
            stats.sectionsRebuilt(),
            stats.voxelBodies());
    }

    public void disableWorldCollisionLifecycle() {
        if (isAuthoritativePhysicsStoreActive()) {
            return;
        }
        try {
            runOwnerMutation("disable world collision lifecycle", this::disableWorldCollisionLifecycleDirect);
        } catch (RejectedExecutionException ignored) {
            // The server can unload the subplugin after a world owner lane has already closed.
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log("Failed to disable world collision lifecycle: %s",
                exception.getMessage());
        }
    }

    private void disableWorldCollisionLifecycleDirect() {
        collisionRuntime.clearRetainedTerrain(spaceRuntime.getBindings());
        restoreCollisionLodFiltersDirect();
        restoreChunkBoundaryPausedBodiesDirect();
        chunkRuntime.clearChunkBoundaryStates();
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

    private void restoreChunkBoundaryPausedBodiesDirect() {
        for (RigidBodyKey bodyKey : chunkRuntime.getChunkBoundaryPausedBodyKeys()) {
            ChunkBoundaryPauseState pauseState = chunkRuntime.getChunkBoundaryPauseState(bodyKey);
            PhysicsBodyRegistration registration = getRegistration(bodyKey);
            if (pauseState == null || registration == null) {
                chunkRuntime.clearChunkBoundaryPauseState(bodyKey);
                continue;
            }
            PhysicsSpaceBinding space = getSpaceBinding(registration.spaceId());
            if (space == null) {
                chunkRuntime.clearChunkBoundaryPauseState(bodyKey);
                continue;
            }
            space.runtime().setBodyType(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value(),
                BackendRuntimeCodes.bodyTypeCode(pauseState.getOriginalBodyType()));
            space.runtime().setBodyVelocity(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value(),
                pauseState.getLinearVelocity().x,
                pauseState.getLinearVelocity().y,
                pauseState.getLinearVelocity().z,
                pauseState.getAngularVelocity().x,
                pauseState.getAngularVelocity().y,
                pauseState.getAngularVelocity().z);
            space.runtime().activateBody(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value());
            chunkRuntime.clearChunkBoundaryPauseState(bodyKey);
        }
    }

    private static void requireWorldCollisionLifecycleEnabled() {
        if (!WorldCollisionLifecycle.isEnabled()) {
            throw new IllegalStateException("Impulse world collision subplugin is disabled");
        }
    }

    private void requireWorldCollisionSpaceEnabled(@Nonnull SpaceId spaceId) {
        if (getLiveSpaceSettings(spaceId).getWorldCollisionSettings().getWorldCollisionMode()
            == WorldCollisionMode.NONE) {
            throw new IllegalStateException("World collision is disabled for space " + spaceId);
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
        runOwnerMutation("remove physics space", () -> removeSpaceDirect(spaceId, worldName));
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
        return enqueueOwnerMutation("remove physics space",
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
                    destroyBody(registration.bodyKey(), false);
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
        runOwnerMutation("clear physics spaces", () -> clearAllSpacesDirect(worldName));
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
        return enqueueOwnerMutation("clear physics spaces",
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
        return callOwner("reset physics runtime state",
            () -> resetRuntimeStateKeepingSpacesDirect(worldName));
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
        runOwnerMutation("set physics space settings", () -> setSpaceSettingsDirect(spaceId, requested));
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
        return enqueueOwnerMutation("set physics space settings",
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

    @Nonnull
    public RigidBodyKey addBodyOnOwner(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendBodyHandle backendBodyHandle,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        assertCanAccessLiveBackendDirectly("add physics body");
        return addBodyDirect(bodyKey, spaceId, backendBodyHandle, kind, persistenceMode);
    }

    @Nonnull
    private RigidBodyKey addBodyDirect(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendBodyHandle backendBodyHandle,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return bodyRuntime.addBody(bodyKey, spaceId, backendBodyHandle, kind, persistenceMode);
    }

    @Override
    public void destroyBody(@Nonnull RigidBodyKey bodyKey) {
        if (isAuthoritativePhysicsStoreActive()) {
            PhysicsStoreTopologyMutations.destroyBody(
                authoritativePhysicsStore("destroy physics body"),
                bodyKey);
            return;
        }
        requireLegacyMutationAllowed("destroy physics body");
        destroyBody(bodyKey, true);
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<RigidBodyKey> destroyBodyAsync(@Nonnull RigidBodyKey bodyKey) {
        if (isAuthoritativePhysicsStoreActive()) {
            return enqueueAuthoritativePhysicsStoreMutation("destroy physics body",
                bodyKey,
                store -> PhysicsStoreTopologyMutations.destroyBody(store, bodyKey));
        }
        requireLegacyMutationAllowed("destroy physics body");
        return destroyBodyAsync(bodyKey, true);
    }

    public void destroyBody(@Nonnull RigidBodyKey bodyKey, boolean removeFromSpace) {
        if (isAuthoritativePhysicsStoreActive()) {
            PhysicsStoreTopologyMutations.destroyBody(
                authoritativePhysicsStore("destroy physics body"),
                bodyKey);
            return;
        }
        requireLegacyMutationAllowed("destroy physics body");
        runOwnerMutation("destroy physics body", () -> destroyBodyDirect(bodyKey, removeFromSpace));
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> destroyBodyAsync(@Nonnull RigidBodyKey bodyKey,
        boolean removeFromSpace) {
        if (isAuthoritativePhysicsStoreActive()) {
            return enqueueAuthoritativePhysicsStoreMutation("destroy physics body",
                bodyKey,
                store -> PhysicsStoreTopologyMutations.destroyBody(store, bodyKey));
        }
        requireLegacyMutationAllowed("destroy physics body");
        return enqueueOwnerMutation("destroy physics body",
            bodyKey,
            () -> destroyBodyDirect(bodyKey, removeFromSpace));
    }

    private void destroyBodyDirect(@Nonnull RigidBodyKey bodyKey, boolean removeFromSpace) {
        bodyRuntime.destroyBody(bodyKey, removeFromSpace);
    }

    @Nullable
    public RigidBodyKey getBodyKey(@Nonnull SpaceId spaceId, long backendBodyId) {
        return bodyRegistry.getBodyKey(spaceId, backendBodyId);
    }

    @Nullable
    public PhysicsBodyRegistration getBodyRegistration(@Nonnull SpaceId spaceId, long backendBodyId) {
        assertCanAccessLiveBackendDirectly("resolve physics body registration");
        RigidBodyKey bodyKey = bodyRegistry.getBodyKey(spaceId, backendBodyId);
        return bodyKey != null ? bodyRegistry.getRegistration(bodyKey) : null;
    }

    @Nullable
    public PhysicsBodyRegistration getRegistration(@Nonnull RigidBodyKey bodyKey) {
        assertCanAccessLiveBackendDirectly("resolve physics body registration");
        return bodyRegistry.getRegistration(bodyKey);
    }

    @Nullable
    @Override
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull RigidBodyKey bodyKey) {
        if (hasAttachedAuthoritativePhysicsStore()) {
            return authoritativePhysicsStore("read physics body registration view")
                .getResource(PhysicsBodyRegistrationResource.getResourceType())
                .getBodyRegistrationView(bodyKey);
        }
        return bodyRegistry.getPublishedRegistrationView(bodyKey);
    }

    @Nonnull
    public JointKey addJointOnOwner(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendJointHandle backendJointHandle,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB,
        @Nonnull JointType type,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ,
        float restLength,
        float stiffness,
        float damping,
        float lowerLimit,
        float upperLimit,
        boolean motorEnabled,
        float motorTargetVelocity,
        float motorMaxForce) {
        assertCanAccessLiveBackendDirectly("add physics joint");
        return addJointDirect(jointKey,
            spaceId,
            backendJointHandle,
            bodyA,
            bodyB,
            type,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            restLength,
            stiffness,
            damping,
            lowerLimit,
            upperLimit,
            motorEnabled,
            motorTargetVelocity,
            motorMaxForce);
    }

    @Nonnull
    private JointKey addJointDirect(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendJointHandle backendJointHandle,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB,
        @Nonnull JointType type,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ,
        float restLength,
        float stiffness,
        float damping,
        float lowerLimit,
        float upperLimit,
        boolean motorEnabled,
        float motorTargetVelocity,
        float motorMaxForce) {
        if (spaceRuntime.getBinding(spaceId) == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        jointRegistry.registerJoint(jointKey,
            spaceId,
            backendJointHandle,
            bodyA,
            bodyB,
            type,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            restLength,
            stiffness,
            damping,
            lowerLimit,
            upperLimit,
            motorEnabled,
            motorTargetVelocity,
            motorMaxForce);
        markWorldChanged();
        return jointKey;
    }

    public boolean removeJoint(@Nonnull JointKey jointKey) {
        requireLegacyMutationAllowed("remove physics joint");
        return callOwner("remove physics joint", () -> removeJointDirect(jointKey));
    }

    private boolean removeJointDirect(@Nonnull JointKey jointKey) {
        PhysicsJointRegistration registration = jointRegistry.getRegistration(jointKey);
        if (registration == null) {
            return false;
        }

        PhysicsSpaceBinding binding = spaceRuntime.getBinding(registration.spaceId());
        if (binding != null) {
            binding.runtime().removeJoint(binding.backendSpaceHandle().value(), registration.backendJointHandle().value());
        }
        jointRegistry.unregisterJoint(jointKey);
        markWorldChanged();
        return true;
    }

    @Nullable
    public JointKey getJointKey(@Nonnull SpaceId spaceId, long backendJointId) {
        assertCanAccessLiveBackendDirectly("resolve physics joint key");
        return jointRegistry.getJointKey(spaceId, backendJointId);
    }

    @Nullable
    public PhysicsJointRegistration getJointRegistration(@Nonnull JointKey jointKey) {
        assertCanAccessLiveBackendDirectly("resolve physics joint registration");
        return jointRegistry.getRegistration(jointKey);
    }

    @Nonnull
    public Collection<PhysicsJointRegistration> getJointRegistrations() {
        assertCanAccessLiveBackendDirectly("list physics joint registrations");
        return jointRegistry.getRegistrations();
    }

    @Nullable
    public PhysicsJointRegistration findJointBetween(@Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        assertCanAccessLiveBackendDirectly("resolve physics joint registration");
        return jointRegistry.findJointBetween(spaceId, bodyA, bodyB);
    }

    public boolean isBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        return bodyRuntime.isBodyCreationPending(bodyKey);
    }

    public boolean hasPublishedOrPendingBodyRegistration(@Nonnull RigidBodyKey bodyKey) {
        return getBodyRegistrationView(bodyKey) != null
            || isBodyCreationPending(bodyKey);
    }

    @Nonnull
    public PhysicsBodyRegistration requireBodyRegistration(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodyRegistration registration = getRegistration(bodyKey);
        if (registration == null) {
            throw new IllegalArgumentException("Physics body key=" + bodyKey + " is not registered");
        }
        return registration;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getBodyRegistrations() {
        assertCanAccessLiveBackendDirectly("list physics body registrations");
        return bodyRegistry.getRegistrations();
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
    public Collection<PhysicsBodyRegistration> getBodyRegistrations(@Nonnull PhysicsBodyKind kind) {
        assertCanAccessLiveBackendDirectly("list physics body registrations");
        return bodyRegistry.getRegistrations(kind);
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
    public Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull RigidBodyKey bodyKey) {
        return visualRuntime.getAttachments(bodyKey);
    }

    @Override
    public boolean hasBodyAttachments(@Nonnull RigidBodyKey bodyKey) {
        return visualRuntime.hasAttachments(bodyKey);
    }

    public void registerBodyAttachment(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> attachment) {
        visualRuntime.registerAttachment(bodyKey, attachment);
    }

    public void unregisterBodyAttachment(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> attachment) {
        visualRuntime.unregisterAttachment(bodyKey, attachment);
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey) {
        return visualRuntime.getGeneratedVisualProxy(bodyKey);
    }

    @Nonnull
    public Collection<RigidBodyKey> getGeneratedVisualProxyBodyKeys() {
        return visualRuntime.getGeneratedVisualProxyBodyKeys();
    }

    public int getGeneratedVisualProxyCount() {
        return visualRuntime.generatedVisualProxyCount();
    }

    public void setGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> proxy) {
        visualRuntime.setGeneratedVisualProxy(bodyKey, proxy);
    }

    public void clearGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey) {
        visualRuntime.clearGeneratedVisualProxy(bodyKey);
    }

    public boolean clearGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> expectedProxy) {
        return visualRuntime.clearGeneratedVisualProxy(bodyKey, expectedProxy);
    }

    public boolean isGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> proxy) {
        return visualRuntime.isGeneratedVisualProxy(bodyKey, proxy);
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

    @Override
    public void clearBodies() {
        if (isAuthoritativePhysicsStoreActive()) {
            Store<PhysicsStore> store = authoritativePhysicsStore("clear physics bodies");
            clearAuthoritativeWorldCollisionStreaming(store);
            PhysicsStoreTopologyMutations.clearBodiesKeepingSpaces(store);
            return;
        }
        requireLegacyMutationAllowed("clear physics bodies");
        runOwnerMutation("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<Void> clearBodiesAsync() {
        if (isAuthoritativePhysicsStoreActive()) {
            return enqueueAuthoritativePhysicsStoreMutation("clear physics bodies",
                null,
                store -> {
                    clearAuthoritativeWorldCollisionStreaming(store);
                    PhysicsStoreTopologyMutations.clearBodiesKeepingSpaces(store);
                });
        }
        requireLegacyMutationAllowed("clear physics bodies");
        return enqueueOwnerMutation("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    private void destroyRegisteredBodiesDirect() {
        bodyRuntime.destroyRegisteredBodies();
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
    public BodyVisualInterestState getOrCreateBodyVisualInterestState(@Nonnull RigidBodyKey bodyKey) {
        BodyVisualInterestState state = visualRuntime.getOrCreateBodyVisualInterestState(bodyKey);
        state.advanceVisualInterestTick(visualInterestTick.get());
        return state;
    }

    @Nullable
    public BodyVisualInterestState getBodyVisualInterestState(@Nonnull RigidBodyKey bodyKey) {
        BodyVisualInterestState state = visualRuntime.getBodyVisualInterestState(bodyKey);
        if (state != null) {
            state.advanceVisualInterestTick(visualInterestTick.get());
        }
        return state;
    }

    public long advanceVisualInterestTick() {
        return visualInterestTick.incrementAndGet();
    }

    public void markBodyControlled(@Nonnull UUID bodyUuid) {
        controlRuntime.markBodyControlled(bodyUuid);
    }

    public void markBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        controlRuntime.markBodyControlled(bodyKey);
    }

    public void clearControlledBody(@Nonnull UUID bodyUuid) {
        controlRuntime.clearControlledBody(bodyUuid);
    }

    public void clearControlledBody(@Nonnull RigidBodyKey bodyKey) {
        controlRuntime.clearControlledBody(bodyKey);
    }

    public boolean isBodyControlled(@Nonnull UUID bodyUuid) {
        return controlRuntime.isBodyControlled(bodyUuid);
    }

    public boolean isBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        return controlRuntime.isBodyControlled(bodyKey);
    }

    public void disableControlLifecycle() {
        controlRuntime.clear();
    }

    public void updateChunkBoundarySafeState(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        chunkRuntime.updateChunkBoundarySafeState(bodyKey, position, rotation);
    }

    public void updateChunkBoundarySafeState(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot) {
        chunkRuntime.updateChunkBoundarySafeState(bodyKey, snapshot);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(@Nonnull RigidBodyKey bodyKey) {
        return chunkRuntime.getChunkBoundarySafeState(bodyKey);
    }

    public void pauseChunkBoundaryBody(@Nonnull RigidBodyKey bodyKey,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        chunkRuntime.pauseChunkBoundaryBody(bodyKey,
            targetChunkIndex,
            originalBodyType,
            linearVelocity,
            angularVelocity);
    }

    public void pauseChunkBoundaryBody(@Nonnull RigidBodyKey bodyKey,
        long targetChunkIndex,
        @Nonnull PhysicsBodySnapshot snapshot) {
        chunkRuntime.pauseChunkBoundaryBody(bodyKey, targetChunkIndex, snapshot);
    }

    public void pauseChunkBoundaryBody(@Nonnull RigidBodyKey bodyKey,
        long targetChunkIndex,
        @Nonnull long[] targetChunkIndices,
        @Nonnull PhysicsBodySnapshot snapshot) {
        chunkRuntime.pauseChunkBoundaryBody(bodyKey, targetChunkIndex, targetChunkIndices, snapshot);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(@Nonnull RigidBodyKey bodyKey) {
        return chunkRuntime.getChunkBoundaryPauseState(bodyKey);
    }

    public void clearChunkBoundaryPauseState(@Nonnull RigidBodyKey bodyKey) {
        chunkRuntime.clearChunkBoundaryPauseState(bodyKey);
    }

    public void clearBodyRuntimeState(@Nonnull RigidBodyKey bodyKey) {
        requireLegacyMutationAllowed("clear physics body runtime state");
        runOwnerMutation("clear physics body runtime state", () -> clearBodyRuntimeStateDirect(bodyKey));
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> clearBodyRuntimeStateAsync(
        @Nonnull RigidBodyKey bodyKey) {
        requireLegacyMutationAllowed("clear physics body runtime state");
        return enqueueOwnerMutation("clear physics body runtime state",
            bodyKey,
            () -> clearBodyRuntimeStateDirect(bodyKey));
    }

    private void clearBodyRuntimeStateDirect(@Nonnull RigidBodyKey bodyKey) {
        bodyRuntime.clearBodyRuntimeState(bodyKey);
    }

    public void markContinuousCollisionForced(@Nonnull RigidBodyKey bodyKey) {
        chunkRuntime.markContinuousCollisionForced(bodyKey);
    }

    @Nonnull
    public Collection<RigidBodyKey> getForcedContinuousCollisionBodyKeys() {
        return chunkRuntime.getForcedContinuousCollisionBodyKeys();
    }

    public boolean hasForcedContinuousCollisionBodies() {
        return chunkRuntime.hasForcedContinuousCollisionBodies();
    }

    public void forEachForcedContinuousCollisionBody(@Nonnull Consumer<RigidBodyKey> consumer) {
        chunkRuntime.forEachForcedContinuousCollisionBody(consumer);
    }

    public void clearForcedContinuousCollisionBodies() {
        chunkRuntime.clearForcedContinuousCollisionBodies();
    }

    public void copyFrom(@Nonnull PhysicsWorldResource other) {
        runOwnerMutation("copy physics world resource", () -> copyFromDirect(other));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> copyFromAsync(@Nonnull PhysicsWorldResource other) {
        return enqueueOwnerMutation("copy physics world resource", () -> copyFromDirect(other));
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
        lifecycleState.markWorldChanged(bodyRegistry, ownerGateway.hasOwnerExecutor());
    }

    @Nonnull
    @Override
    public PhysicsWorldResource clone() {
        PhysicsWorldRuntimeResource copy = new PhysicsWorldRuntimeResource();
        copy.copyFrom(this);
        return copy;
    }
}
