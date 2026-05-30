package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.control.PhysicsControlRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState.BodySyncState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntime;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotVisitor;
import dev.hytalemodding.impulse.core.internal.resources.chunk.PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState;
import dev.hytalemodding.impulse.core.internal.resources.chunk.PhysicsChunkBoundaryRuntime.ChunkBoundarySafeState;
import dev.hytalemodding.impulse.core.internal.resources.chunk.PhysicsChunkBoundaryRuntime;
import dev.hytalemodding.impulse.core.internal.resources.collision.PhysicsWorldCollisionRuntime;
import dev.hytalemodding.impulse.core.internal.resources.event.PhysicsWorldEventState;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistration;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistry;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerGateway;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerHandle;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerScopedCallable;
import dev.hytalemodding.impulse.core.internal.resources.simulation.PhysicsSimulationRuntime;
import dev.hytalemodding.impulse.core.internal.resources.space.PhysicsSpaceRuntime;
import dev.hytalemodding.impulse.core.internal.resources.snapshot.PhysicsWorldSnapshotState;
import dev.hytalemodding.impulse.core.internal.resources.visual.PhysicsVisualRuntime.BodyVisualInterestState;
import dev.hytalemodding.impulse.core.internal.resources.visual.PhysicsVisualRuntime.VisualInterest;
import dev.hytalemodding.impulse.core.internal.resources.visual.PhysicsVisualRuntime;
import dev.hytalemodding.impulse.core.internal.simulation.MutablePhysicsCommandContext;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsInternalQuery;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsSimulationExecutor;
import dev.hytalemodding.impulse.core.internal.simulation.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsOwnerAccess;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsOwnerTransaction;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandCompletion;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandRecipe;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsQueryHandle;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final PhysicsWorldSnapshotState snapshotState = new PhysicsWorldSnapshotState();
    private final PhysicsWorldEventState eventState = new PhysicsWorldEventState();
    private final PhysicsBodyRuntime bodyRuntime = new PhysicsBodyRuntime(spaceRuntime,
        bodyRegistry,
        runtimeState,
        controlRuntime,
        jointRegistry,
        chunkRuntime,
        visualRuntime,
        snapshotState,
        this::markWorldChanged);

    private final AtomicLong visualInterestTick = new AtomicLong();
    private final PhysicsOwnerGateway ownerGateway = new PhysicsOwnerGateway();
    private final PhysicsOwnerAccess ownerAccess = new RuntimePhysicsOwnerAccess();
    private final PhysicsSimulationExecutor simulationExecutor = new PhysicsSimulationExecutor(this);
    private final AtomicLong commandBatchSequence = new AtomicLong();
    private final AtomicLong completedCommandBatchSequence = new AtomicLong();
    /*
     * Reader-side body materialization can observe command completion before the next published
     * registration frame has applied. Track that gap at command-batch sequence granularity so bulk
     * spawns do not need per-body pending keys.
     */
    private final AtomicLong submittedBodyCreationCommandBatchSequence = new AtomicLong();
    private final AtomicLong appliedCommandBatchSequenceWatermark = new AtomicLong();
    private final AtomicLong commandWorldEpoch = new AtomicLong();
    private final ThreadLocal<Integer> commandBatchExecutionDepth = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<Integer> ownerAccessDepth = ThreadLocal.withInitial(() -> 0);

    public PhysicsWorldRuntimeResource() {
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

    public void attachWorkerResource(@Nonnull PhysicsOwnerHandle workerResource) {
        ownerGateway.attachWorkerResource(workerResource);
    }

    public void detachWorkerResource(@Nonnull PhysicsOwnerHandle workerResource) {
        ownerGateway.detachWorkerResource(workerResource);
        if (!ownerGateway.hasWorkerResource()) {
            bodyRegistry.publishLiveRegistrationViews();
            appliedCommandBatchSequenceWatermark.accumulateAndGet(completedCommandBatchSequence.get(),
                Math::max);
        }
    }

    public boolean canAccessLiveBackendDirectly() {
        return ownerGateway.canAccessLiveBackendDirectly();
    }

    public long worldEpoch() {
        return snapshotState.worldEpoch();
    }

    public long commandWorldEpoch() {
        return commandWorldEpoch.get();
    }

    @Nonnull
    public MutablePhysicsCommandContext createMutableCommandContext(long submittedServerTick) {
        return new MutablePhysicsCommandContext(submittedServerTick, commandWorldEpoch.get());
    }

    @Nonnull
    public MutablePhysicsCommandContext createMutableCommandContext(long submittedServerTick,
        int expectedOperations) {
        return new MutablePhysicsCommandContext(submittedServerTick,
            commandWorldEpoch.get(),
            expectedOperations);
    }

    @Nonnull
    @Override
    public PhysicsCommandHandle submitCommands(long submittedServerTick,
        @Nonnull PhysicsCommandRecipe recipe) {
        MutablePhysicsCommandContext context = createMutableCommandContext(submittedServerTick);
        context.compose(recipe);
        return submitRecordedCommands(context);
    }

    @Nonnull
    @Override
    public PhysicsCommandHandle submitCommands(long submittedServerTick,
        int expectedOperations,
        @Nonnull PhysicsCommandRecipe recipe) {
        MutablePhysicsCommandContext context =
            createMutableCommandContext(submittedServerTick, expectedOperations);
        context.compose(recipe);
        return submitRecordedCommands(context);
    }

    @Nonnull
    public PhysicsCommandHandle submitRecordedCommands(@Nonnull MutablePhysicsCommandContext context) {
        Objects.requireNonNull(context, "context");
        RecordedPhysicsCommandBatch batch =
            context.freezeInternal(commandBatchSequence.incrementAndGet());
        /*
         * Body creation through commands publishes registration views with the next snapshot frame.
         * Until that frame is applied, sync/materialization must treat creation as pending even
         * though the owner may already have completed the command batch.
         */
        boolean trackBodyCreationPublication =
            batch.hasBodyCreationCommands() && ownerGateway.hasWorkerResource();
        if (trackBodyCreationPublication) {
            submittedBodyCreationCommandBatchSequence.accumulateAndGet(
                batch.metadata().commandBatchSequence(),
                Math::max);
        }
        CompletableFuture<PhysicsCommandCompletion> completion =
            ownerGateway.enqueueCall("execute physics command batch", () -> executeCommandBatch(batch));
        if (trackBodyCreationPublication) {
            completion.whenComplete((ignored, failure) -> {
                if (failure != null) {
                    appliedCommandBatchSequenceWatermark.accumulateAndGet(
                        batch.metadata().commandBatchSequence(),
                        Math::max);
                }
            });
        }
        return PhysicsCommandHandle.fromCompletionSummary(batch.publicBatch(), completion);
    }

    @Nonnull
    @Override
    public <R> PhysicsQueryHandle<R> query(@Nonnull PhysicsQuery<R> query) {
        Objects.requireNonNull(query, "query");
        CompletableFuture<R> completion =
            ownerGateway.enqueueCall("execute physics query", () -> simulationExecutor.query(query));
        return PhysicsQueryHandle.fromCompletion(query, completion);
    }

    @Nonnull
    public <R> CompletionStage<R> queryInternal(@Nonnull PhysicsInternalQuery<R> query) {
        Objects.requireNonNull(query, "query");
        CompletableFuture<R> completion =
            ownerGateway.enqueueCall("execute internal physics query",
                () -> simulationExecutor.queryInternal(query));
        return completion.minimalCompletionStage();
    }

    @Nonnull
    @Override
    public PhysicsEventFrame getLatestEventFrame() {
        return eventState.getLatestFrame();
    }

    public void assertCanAccessLiveBackendDirectly(@Nonnull String operation) {
        ownerGateway.assertCanAccessLiveBackendDirectly(operation);
    }

    private void assertOwnerAccessActive(@Nonnull String operation) {
        Objects.requireNonNull(operation, "operation");
        if (ownerAccessDepth.get() <= 0) {
            throw new IllegalStateException("Physics owner access for " + operation
                + " is only valid inside the owner callback");
        }
        assertCanAccessLiveBackendDirectly(operation);
    }

    private void runWithOwnerAccess(@Nonnull PhysicsOwnerTransaction mutation)
        throws Exception {
        Objects.requireNonNull(mutation, "mutation");
        int depth = ownerAccessDepth.get();
        ownerAccessDepth.set(depth + 1);
        try {
            mutation.run(ownerAccess);
        } finally {
            if (depth == 0) {
                ownerAccessDepth.remove();
            } else {
                ownerAccessDepth.set(depth);
            }
        }
    }

    @Nonnull
    private <T> T callWithOwnerAccess(@Nonnull PhysicsOwnerScopedCallable<T> callable)
        throws Exception {
        Objects.requireNonNull(callable, "callable");
        int depth = ownerAccessDepth.get();
        ownerAccessDepth.set(depth + 1);
        try {
            return callable.call(ownerAccess);
        } finally {
            if (depth == 0) {
                ownerAccessDepth.remove();
            } else {
                ownerAccessDepth.set(depth);
            }
        }
    }

    public void runOwnerMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        ownerGateway.run(operation, mutation);
    }

    public void runOwnerMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerTransaction mutation) {
        Objects.requireNonNull(mutation, "mutation");
        ownerGateway.run(operation, () -> runWithOwnerAccess(mutation));
    }

    public void runOwnerTransactionDirect(@Nonnull PhysicsOwnerTransaction transaction)
        throws Exception {
        runWithOwnerAccess(transaction);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> enqueueOwnerMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        return enqueueOwnerMutation(operation, null, mutation);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> enqueueOwnerMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerTransaction mutation) {
        return enqueueOwnerMutation(operation, null, mutation);
    }

    @Nonnull
    public <T> PhysicsMutationHandle<T> enqueueOwnerMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        return ownerGateway.enqueue(operation, value, mutation);
    }

    @Nonnull
    public <T> PhysicsMutationHandle<T> enqueueOwnerMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerTransaction mutation) {
        Objects.requireNonNull(mutation, "mutation");
        return ownerGateway.enqueue(operation, value, () -> runWithOwnerAccess(mutation));
    }

    @Nonnull
    public <T> T callOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        return ownerGateway.call(operation, callable);
    }

    @Nonnull
    public <T> T callOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerScopedCallable<T> callable) {
        Objects.requireNonNull(callable, "callable");
        return ownerGateway.call(operation, () -> callWithOwnerAccess(callable));
    }

    @Nonnull
    public PhysicsWorldSettings getWorldSettings() {
        return simulationRuntime.getWorldSettings();
    }

    public void setWorldSettings(@Nonnull PhysicsWorldSettings settings) {
        PhysicsWorldSettings requested = new PhysicsWorldSettings(settings);
        runOwnerMutation("set physics world settings", () -> setWorldSettingsDirect(requested));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> setWorldSettingsAsync(
        @Nonnull PhysicsWorldSettings settings) {
        PhysicsWorldSettings requested = new PhysicsWorldSettings(settings);
        return enqueueOwnerMutation("set physics world settings",
            () -> setWorldSettingsDirect(requested));
    }

    private void setWorldSettingsDirect(@Nonnull PhysicsWorldSettings settings) {
        validateStepModeSupported(settings.getStepMode());
        simulationRuntime.setWorldSettings(settings);
    }

    @Nonnull
    public SpaceId createSpace(@Nonnull BackendId backendId) {
        return createSpace(backendId, "<unknown>", PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    public SpaceId createSpace(@Nonnull BackendId backendId, @Nonnull String worldName) {
        return createSpace(backendId, worldName, PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    public SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        return createSpace(backendId, SpaceId.next(), worldName, settings);
    }

    @Nonnull
    public SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        callOwner("create physics space",
            () -> createSpaceDirect(backendId, spaceId, worldName, settings));
        return spaceId;
    }

    @Nonnull
    public PhysicsSpace createLiveSpace(@Nonnull BackendId backendId) {
        return createLiveSpace(backendId, "<unknown>", PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    public PhysicsSpace createLiveSpace(@Nonnull BackendId backendId, @Nonnull String worldName) {
        return createLiveSpace(backendId, worldName, PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    public PhysicsSpace createLiveSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        return createLiveSpace(backendId, SpaceId.next(), worldName, settings);
    }

    @Nonnull
    public PhysicsSpace createLiveSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        return callOwner("create physics space",
            () -> createSpaceDirect(backendId, spaceId, worldName, settings));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> createSpaceAsync(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        SpaceId spaceId = SpaceId.next();
        return createSpaceAsync(backendId, spaceId, worldName, settings);
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> createSpaceAsync(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        return enqueueOwnerMutation("create physics space",
            spaceId,
            () -> createSpaceDirect(backendId, spaceId, worldName, settings));
    }

    @Nonnull
    private PhysicsSpace createSpaceDirect(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsSpace space = spaceRuntime.createSpace(backendId,
            spaceId,
            worldName,
            settings,
            simulationRuntime.getWorldSettings().getStepMode());
        markWorldChanged();
        return space;
    }

    @Nullable
    public PhysicsSpace getSpace(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getSpace(spaceId);
    }

    public boolean hasSpace(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getSpace(spaceId) != null;
    }

    @Nonnull
    private PhysicsSpace requireSpace(@Nonnull SpaceId spaceId) {
        return spaceRuntime.requireSpace(spaceId);
    }

    @Nonnull
    public Collection<PhysicsSpace> getSpaces() {
        return spaceRuntime.getSpaces();
    }

    @Nonnull
    public Collection<SpaceId> getSpaceIds() {
        return spaceRuntime.getSpaceIds();
    }

    public int getSpaceCount() {
        return spaceRuntime.getSpaceCount();
    }

    /**
     * Iterate spaces without allocating a snapshot collection.
     * Use this from tick systems that do not mutate the space map while iterating.
     */
    @Nonnull
    public Iterable<PhysicsSpace> iterateSpaces() {
        return spaceRuntime.iterateSpaces();
    }

    public int refreshBodySnapshots() {
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
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodySnapshot snapshot = snapshotState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        return callOwner("refresh missing physics body snapshot",
            () -> getBodySnapshotDirect(bodyKey));
    }

    @Nonnull
    private PhysicsBodySnapshot getBodySnapshotDirect(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodySnapshot snapshot = snapshotState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        return snapshotState.captureBodySnapshot(registration);
    }

    /**
     * Captures an immutable snapshot frame on the physics owner thread.
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
        return callOwner("capture published physics snapshot frame",
            () -> capturePublishedSnapshotFrameDirect(stepSequence,
                serverTick,
                status,
                stepNanos,
                profilingEnabled));
    }

    @Nonnull
    private PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrameDirect(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled) {

        assertCanAccessLiveBackendDirectly("capture published physics snapshot frame");
        PublishedPhysicsSnapshotFrame frame = snapshotState.capturePublishedSnapshotFrame(spaceRuntime.liveSpaces(),
            bodyRegistry,
            stepSequence,
            serverTick,
            completedCommandBatchSequence.get(),
            status,
            stepNanos,
            profilingEnabled);
        eventState.publishStepCaptured(frame.worldEpoch(), frame);
        return frame;
    }

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        return applyPublishedSnapshotFrame(frame, 0L);
    }

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame,
        long publicationServerTick) {
        int applied = snapshotState.applyPublishedSnapshotFrame(frame, bodyRegistry);
        if (frame.worldEpoch() == snapshotState.worldEpoch()) {
            bodyRegistry.applyPublishedRegistrationFrame(frame);
            appliedCommandBatchSequenceWatermark.accumulateAndGet(
                frame.commandBatchSequenceWatermark(),
                Math::max);
            eventState.publishSnapshotPublication(snapshotState.worldEpoch(),
                frame,
                applied,
                publicationServerTick);
        }
        return applied;
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame getLatestPublishedFrame() {
        return snapshotState.getLatestPublishedFrame();
    }

    public long getLatestSnapshotAppliedNanos() {
        return snapshotState.getLatestSnapshotAppliedNanos();
    }

    @Nonnull
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBody body) {
        RigidBodyKey bodyKey = getBodyKey(body);
        if (bodyKey == null) {
            return callOwner("read unregistered physics body snapshot",
                () -> PhysicsBodySnapshot.from(body));
        }
        return getBodySnapshot(bodyKey);
    }

    public int getBodySnapshotCount() {
        return snapshotState.getBodySnapshotCount();
    }

    public int getBodySnapshotCount(@Nonnull SpaceId spaceId) {
        return snapshotState.getBodySnapshotCount(spaceId);
    }

    public int getBodySnapshotCellCount() {
        return snapshotState.getBodySnapshotCellCount();
    }

    @Nonnull
    public WorldVoxelCollisionCache worldCollisionCache() {
        return collisionRuntime.worldVoxelCollisionCache();
    }

    @Nonnull
    public WorldCollisionBuildStats rebuildWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        return callOwner("rebuild world collision", () -> {
            PhysicsSpace space = requireSpace(spaceId);
            return collisionRuntime.rebuildAround(world, space, center, radius);
        });
    }

    @Nonnull
    public WorldCollisionPrewarmStats ensureWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        Objects.requireNonNull(centers, "centers");
        return callOwner("ensure world collision", () -> {
            PhysicsSpace space = requireSpace(spaceId);
            return collisionRuntime.ensureAround(world, space, centers, radius, tick);
        });
    }

    public int clearWorldCollision(@Nonnull SpaceId spaceId) {
        return callOwner("clear world collision", () -> {
            PhysicsSpace space = requireSpace(spaceId);
            return collisionRuntime.clear(space);
        });
    }

    @Nonnull
    public WorldCollisionStats getWorldCollisionStats() {
        return callOwner("read world collision stats", collisionRuntime::getStats);
    }

    public void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        snapshotState.forEachBodySnapshot(spaceId, consumer);
    }

    public void forEachIndexedBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        snapshotState.forEachIndexedBodySnapshot(spaceId, visitor);
    }

    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        return snapshotState.forEachBodySnapshotNear(spaceId, center, radius, consumer);
    }

    public int forEachIndexedBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        return snapshotState.forEachIndexedBodySnapshotNear(spaceId, center, radius, visitor);
    }

    public void removeSpace(@Nonnull SpaceId spaceId) {
        removeSpace(spaceId, "<unknown>");
    }

    public void removeSpace(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        runOwnerMutation("remove physics space", () -> removeSpaceDirect(spaceId, worldName));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> removeSpaceAsync(@Nonnull SpaceId spaceId,
        @Nonnull String worldName) {
        return enqueueOwnerMutation("remove physics space",
            spaceId,
            () -> removeSpaceDirect(spaceId, worldName));
    }

    private void removeSpaceDirect(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        PhysicsSpace removed = spaceRuntime.removeSpace(spaceId);
        collisionRuntime.clear(spaceId, removed);
        if (removed != null) {
            jointRegistry.unregisterSpace(spaceId);
            for (PhysicsBodyRegistration registration : bodyRegistry.getRegistrations()) {
                if (registration.spaceId().equals(spaceId)) {
                    destroyBody(registration.id(), false);
                }
            }
            LOGGER.at(Level.FINE).log(
                "World %s removed physics space id=%s backend=%s",
                worldName,
                removed.getId(),
                removed.getBackendId());
            closeSpaceQuietly(removed, worldName, "removed physics space");
            markWorldChanged();
        }
    }

    public void clearAllSpaces(@Nonnull String worldName) {
        runOwnerMutation("clear physics spaces", () -> clearAllSpacesDirect(worldName));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> clearAllSpacesAsync(@Nonnull String worldName) {
        return enqueueOwnerMutation("clear physics spaces",
            () -> clearAllSpacesDirect(worldName));
    }

    private void clearAllSpacesDirect(@Nonnull String worldName) {
        for (SpaceId spaceId : spaceRuntime.getSpaceIds()) {
            removeSpaceDirect(spaceId, worldName);
        }
    }

    /**
     * Clears runtime physics state by replacing each native backend space with an empty
     * space that keeps the same logical id, backend, settings, and gravity.
     */
    @Nonnull
    public PhysicsRuntimeResetResult resetRuntimeStateKeepingSpaces(@Nonnull String worldName) {
        return callOwner("reset physics runtime state",
            () -> resetRuntimeStateKeepingSpacesDirect(worldName));
    }

    @Nonnull
    private PhysicsRuntimeResetResult resetRuntimeStateKeepingSpacesDirect(@Nonnull String worldName) {
        PhysicsRuntimeResetResult reset = spaceRuntime.resetKeepingSpaces(worldName,
            simulationRuntime.getWorldSettings().getStepMode(),
            collisionRuntime::clear,
            this::markWorldChanged);
        clearBodyStateDirect();
        return reset;
    }

    @Nonnull
    public PhysicsSpaceSettings getSpaceSettings(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getSpaceSettings(spaceId);
    }

    @Nonnull
    public PhysicsSpaceSettings getLiveSpaceSettings(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getLiveSpaceSettings(spaceId);
    }

    public void setSpaceSettings(@Nonnull SpaceId spaceId, @Nonnull PhysicsSpaceSettings settings) {
        PhysicsSpaceSettings requested = new PhysicsSpaceSettings(settings);
        runOwnerMutation("set physics space settings", () -> setSpaceSettingsDirect(spaceId, requested));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> setSpaceSettingsAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsSpaceSettings requested = new PhysicsSpaceSettings(settings);
        return enqueueOwnerMutation("set physics space settings",
            spaceId,
            () -> setSpaceSettingsDirect(spaceId, requested));
    }

    private void setSpaceSettingsDirect(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        spaceRuntime.setSpaceSettings(spaceId, settings);
    }

    private void validateStepModeSupported(@Nonnull PhysicsStepMode stepMode) {
        spaceRuntime.validateStepModeSupported(stepMode);
    }

    @Nonnull
    public RigidBodyKey addBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return addBody(RigidBodyKey.random(), spaceId, body, kind, persistenceMode);
    }

    @Nonnull
    public RigidBodyKey addBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return callOwner("add physics body",
            () -> addBodyDirect(bodyKey, spaceId, body, kind, persistenceMode));
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> addBodyAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        RigidBodyKey bodyKey = RigidBodyKey.random();
        return addBodyAsync(bodyKey, spaceId, body, kind, persistenceMode);
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> addBodyAsync(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        markBodyCreationPending(bodyKey);
        AtomicBoolean pendingCleared = new AtomicBoolean();
        Runnable clearPending = () -> {
            if (pendingCleared.compareAndSet(false, true)) {
                clearBodyCreationPending(bodyKey);
            }
        };
        PhysicsMutationHandle<RigidBodyKey> handle = enqueueOwnerMutation("add physics body",
            bodyKey,
            () -> {
                try {
                    addBodyDirect(bodyKey, spaceId, body, kind, persistenceMode);
                } finally {
                    clearPending.run();
                }
            });
        handle.completion().whenComplete((ignored, _) -> clearPending.run());
        return handle;
    }

    @Nonnull
    private RigidBodyKey addBodyDirect(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return bodyRuntime.addBody(bodyKey, spaceId, body, kind, persistenceMode);
    }

    @Nonnull
    public RigidBodyKey addBodyOnOwner(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        assertCanAccessLiveBackendDirectly("add physics body");
        return addBodyDirect(bodyKey, spaceId, body, kind, persistenceMode);
    }

    public void destroyBody(@Nonnull RigidBodyKey bodyKey) {
        destroyBody(bodyKey, true);
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> destroyBodyAsync(@Nonnull RigidBodyKey bodyKey) {
        return destroyBodyAsync(bodyKey, true);
    }

    public void destroyBody(@Nonnull RigidBodyKey bodyKey, boolean removeFromSpace) {
        runOwnerMutation("destroy physics body", () -> destroyBodyDirect(bodyKey, removeFromSpace));
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> destroyBodyAsync(@Nonnull RigidBodyKey bodyKey,
        boolean removeFromSpace) {
        return enqueueOwnerMutation("destroy physics body",
            bodyKey,
            () -> destroyBodyDirect(bodyKey, removeFromSpace));
    }

    private void destroyBodyDirect(@Nonnull RigidBodyKey bodyKey, boolean removeFromSpace) {
        bodyRuntime.destroyBody(bodyKey, removeFromSpace);
    }

    public void destroyBody(@Nonnull PhysicsBody body) {
        runOwnerMutation("destroy physics body", () -> destroyBodyDirect(body));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> destroyBodyAsync(@Nonnull PhysicsBody body) {
        return enqueueOwnerMutation("destroy physics body", () -> destroyBodyDirect(body));
    }

    private void destroyBodyDirect(@Nonnull PhysicsBody body) {
        bodyRuntime.destroyBody(body);
    }

    @Nullable
    public PhysicsBody getBody(@Nonnull RigidBodyKey bodyKey) {
        assertCanAccessLiveBackendDirectly("resolve live physics body");
        PhysicsBodyRegistration registration = bodyRegistry.getRegistration(bodyKey);
        return registration != null ? registration.body() : null;
    }

    @Nullable
    public RigidBodyKey getBodyKey(@Nonnull PhysicsBody body) {
        return bodyRegistry.getBodyKey(body);
    }

    @Nullable
    public PhysicsBodyRegistration getBodyRegistration(@Nonnull PhysicsBody body) {
        assertCanAccessLiveBackendDirectly("resolve live physics body registration");
        return bodyRegistry.getRegistration(body);
    }

    @Nullable
    public PhysicsBodyRegistration getRegistration(@Nonnull RigidBodyKey bodyKey) {
        assertCanAccessLiveBackendDirectly("resolve live physics body registration");
        return bodyRegistry.getRegistration(bodyKey);
    }

    @Nullable
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull RigidBodyKey bodyKey) {
        return bodyRegistry.getPublishedRegistrationView(bodyKey);
    }

    @Nonnull
    public JointKey addJoint(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsJoint joint) {
        return addJoint(JointKey.random(), spaceId, joint);
    }

    @Nonnull
    public JointKey addJoint(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsJoint joint) {
        return callOwner("add physics joint",
            () -> addJointDirect(jointKey, spaceId, joint));
    }

    @Nonnull
    private JointKey addJointDirect(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsJoint joint) {
        if (spaceRuntime.getSpace(spaceId) == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        jointRegistry.registerJoint(jointKey, spaceId, joint);
        markWorldChanged();
        return jointKey;
    }

    public boolean removeJoint(@Nonnull JointKey jointKey) {
        return callOwner("remove physics joint", () -> removeJointDirect(jointKey));
    }

    private boolean removeJointDirect(@Nonnull JointKey jointKey) {
        PhysicsJointRegistration registration = jointRegistry.getRegistration(jointKey);
        if (registration == null) {
            return false;
        }

        PhysicsSpace space = spaceRuntime.getSpace(registration.spaceId());
        if (space != null) {
            space.removeJoint(registration.joint());
        }
        jointRegistry.unregisterJoint(jointKey);
        markWorldChanged();
        return true;
    }

    @Nullable
    public PhysicsJoint getJoint(@Nonnull JointKey jointKey) {
        assertCanAccessLiveBackendDirectly("resolve live physics joint");
        PhysicsJointRegistration registration = jointRegistry.getRegistration(jointKey);
        return registration != null ? registration.joint() : null;
    }

    @Nullable
    public JointKey getJointKey(@Nonnull PhysicsJoint joint) {
        assertCanAccessLiveBackendDirectly("resolve live physics joint key");
        return jointRegistry.getJointKey(joint);
    }

    @Nullable
    public PhysicsJointRegistration getJointRegistration(@Nonnull JointKey jointKey) {
        assertCanAccessLiveBackendDirectly("resolve live physics joint registration");
        return jointRegistry.getRegistration(jointKey);
    }

    public boolean isBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        return bodyRuntime.isBodyCreationPending(bodyKey)
            || hasPendingCommandBodyCreationPublication();
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
        assertCanAccessLiveBackendDirectly("list live physics body registrations");
        return bodyRegistry.getRegistrations();
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews() {
        return bodyRegistry.getPublishedRegistrationViews();
    }

    public int getBodyRegistrationCount() {
        return bodyRegistry.getPublishedRegistrationCount();
    }

    public int getBodyRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return bodyRegistry.getPublishedRegistrationCount(persistenceMode);
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getBodyRegistrations(@Nonnull PhysicsBodyKind kind) {
        assertCanAccessLiveBackendDirectly("list live physics body registrations");
        return bodyRegistry.getRegistrations(kind);
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews(@Nonnull PhysicsBodyKind kind) {
        return bodyRegistry.getPublishedRegistrationViews(kind);
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull RigidBodyKey bodyKey) {
        return visualRuntime.getAttachments(bodyKey);
    }

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

    public void clearBodies() {
        runOwnerMutation("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> clearBodiesAsync() {
        return enqueueOwnerMutation("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    private void destroyRegisteredBodiesDirect() {
        bodyRuntime.destroyRegisteredBodies();
    }

    private void clearBodyStateDirect() {
        bodyRuntime.clearBodyState();
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

    public void markBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        controlRuntime.markBodyControlled(bodyKey);
    }

    public void clearControlledBody(@Nonnull RigidBodyKey bodyKey) {
        controlRuntime.clearControlledBody(bodyKey);
    }

    public boolean isBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        return controlRuntime.isBodyControlled(bodyKey);
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

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(@Nonnull RigidBodyKey bodyKey) {
        return chunkRuntime.getChunkBoundaryPauseState(bodyKey);
    }

    public void clearChunkBoundaryPauseState(@Nonnull RigidBodyKey bodyKey) {
        chunkRuntime.clearChunkBoundaryPauseState(bodyKey);
    }

    public void clearBodyRuntimeState(@Nonnull RigidBodyKey bodyKey) {
        runOwnerMutation("clear physics body runtime state", () -> clearBodyRuntimeStateDirect(bodyKey));
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> clearBodyRuntimeStateAsync(
        @Nonnull RigidBodyKey bodyKey) {
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
        bodyRegistry.clear();
        runtimeState.clear();
        controlRuntime.clear();
        jointRegistry.clear();
        chunkRuntime.clear();
        visualRuntime.clear();
        snapshotState.clearBodySnapshots();
        collisionRuntime.clearAll();
        simulationRuntime.copyFrom(otherRuntime.simulationRuntime);
        markWorldChanged();
    }

    @Nonnull
    public PhysicsSpace replaceSpace(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace replacement,
        @Nonnull String worldName) {
        return callOwner("replace physics space",
            () -> replaceSpaceDirect(spaceId, replacement, worldName));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> replaceSpaceAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace replacement,
        @Nonnull String worldName) {
        return enqueueOwnerMutation("replace physics space",
            spaceId,
            () -> replaceSpaceDirect(spaceId, replacement, worldName));
    }

    @Nonnull
    private PhysicsSpace replaceSpaceDirect(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace replacement,
        @Nonnull String worldName) {
        return spaceRuntime.replaceSpace(spaceId,
            replacement,
            worldName,
            simulationRuntime.getWorldSettings().getStepMode(),
            collisionRuntime::clear,
            this::markWorldChanged);
    }

    private static void closeSpaceQuietly(@Nonnull PhysicsSpace space,
        @Nonnull String worldName,
        @Nonnull String action) {
        try {
            space.close();
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log(
                "World %s failed to close %s id=%s backend=%s: %s",
                worldName,
                action,
                space.getId(),
                space.getBackendId(),
                exception.getMessage());
        }
    }

    private final class RuntimePhysicsOwnerAccess implements PhysicsOwnerAccess {

        @Nullable
        @Override
        public PhysicsSpace getSpace(@Nonnull SpaceId spaceId) {
            assertOwnerAccessActive("resolve live physics space");
            return spaceRuntime.getSpace(spaceId);
        }

        @Nonnull
        @Override
        public PhysicsSpace requireSpace(@Nonnull SpaceId spaceId) {
            assertOwnerAccessActive("resolve live physics space");
            return spaceRuntime.requireSpace(spaceId);
        }

        @Nonnull
        @Override
        public Collection<PhysicsSpace> getSpaces() {
            assertOwnerAccessActive("list live physics spaces");
            return spaceRuntime.getSpaces();
        }

        @Nullable
        @Override
        public PhysicsBody getBody(@Nonnull RigidBodyKey bodyKey) {
            assertOwnerAccessActive("resolve live physics body");
            return PhysicsWorldRuntimeResource.this.getBody(bodyKey);
        }

        @Nonnull
        @Override
        public PhysicsBody requireBody(@Nonnull RigidBodyKey bodyKey) {
            PhysicsBody body = getBody(bodyKey);
            if (body == null) {
                throw new IllegalArgumentException("Physics body key=" + bodyKey + " is not registered");
            }
            return body;
        }

        @Nullable
        @Override
        public RigidBodyKey getBodyKey(@Nonnull PhysicsBody body) {
            assertOwnerAccessActive("resolve live physics body key");
            return bodyRegistry.getBodyKey(body);
        }

        @Nonnull
        @Override
        public RigidBodyKey addBody(@Nonnull SpaceId spaceId,
            @Nonnull PhysicsBody body,
            @Nonnull PhysicsBodyKind kind,
            @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
            return addBody(RigidBodyKey.random(), spaceId, body, kind, persistenceMode);
        }

        @Nonnull
        @Override
        public RigidBodyKey addBody(@Nonnull RigidBodyKey bodyKey,
            @Nonnull SpaceId spaceId,
            @Nonnull PhysicsBody body,
            @Nonnull PhysicsBodyKind kind,
            @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
            assertOwnerAccessActive("add physics body");
            return addBodyDirect(bodyKey, spaceId, body, kind, persistenceMode);
        }

        @Nullable
        @Override
        public PhysicsJoint getJoint(@Nonnull JointKey jointKey) {
            assertOwnerAccessActive("resolve live physics joint");
            return PhysicsWorldRuntimeResource.this.getJoint(jointKey);
        }

        @Nonnull
        @Override
        public PhysicsJoint requireJoint(@Nonnull JointKey jointKey) {
            PhysicsJoint joint = getJoint(jointKey);
            if (joint == null) {
                throw new IllegalArgumentException("Physics joint key=" + jointKey + " is not registered");
            }
            return joint;
        }

        @Nullable
        @Override
        public JointKey getJointKey(@Nonnull PhysicsJoint joint) {
            assertOwnerAccessActive("resolve live physics joint key");
            return PhysicsWorldRuntimeResource.this.getJointKey(joint);
        }

        @Nonnull
        @Override
        public JointKey addJoint(@Nonnull SpaceId spaceId,
            @Nonnull PhysicsJoint joint) {
            return addJoint(JointKey.random(), spaceId, joint);
        }

        @Nonnull
        @Override
        public JointKey addJoint(@Nonnull JointKey jointKey,
            @Nonnull SpaceId spaceId,
            @Nonnull PhysicsJoint joint) {
            assertOwnerAccessActive("add physics joint");
            return addJointDirect(jointKey, spaceId, joint);
        }
    }

    private void markBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        bodyRuntime.markBodyCreationPending(bodyKey);
    }

    private void clearBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        bodyRuntime.clearBodyCreationPending(bodyKey);
    }

    private boolean hasPendingCommandBodyCreationPublication() {
        /*
         * This is intentionally conservative: any unapplied body-creation batch keeps new-body
         * readers from assuming that a missing published registration view means the body is absent.
         */
        return ownerGateway.hasWorkerResource()
            && submittedBodyCreationCommandBatchSequence.get()
                > appliedCommandBatchSequenceWatermark.get();
    }

    private void markWorldChanged() {
        snapshotState.markWorldChanged();
        if (!ownerGateway.hasWorkerResource()) {
            bodyRegistry.publishLiveRegistrationViews();
        }
        if (commandBatchExecutionDepth.get() == 0) {
            commandWorldEpoch.incrementAndGet();
            eventState.publishEmpty(snapshotState.worldEpoch(), snapshotState.getLatestPublishedFrame());
        }
    }

    @Nonnull
    private PhysicsCommandCompletion executeCommandBatch(@Nonnull RecordedPhysicsCommandBatch batch) {
        int depth = commandBatchExecutionDepth.get();
        commandBatchExecutionDepth.set(depth + 1);
        try {
            PhysicsCommandCompletion completion = simulationExecutor.execute(batch);
            completedCommandBatchSequence.accumulateAndGet(batch.metadata().commandBatchSequence(), Math::max);
            eventState.publishCommandCompletion(snapshotState.worldEpoch(),
                snapshotState.getLatestPublishedFrame(),
                batch,
                completion);
            return completion;
        } finally {
            if (depth == 0) {
                commandBatchExecutionDepth.remove();
            } else {
                commandBatchExecutionDepth.set(depth);
            }
        }
    }

    @Nonnull
    @Override
    public PhysicsWorldResource clone() {
        PhysicsWorldRuntimeResource copy = new PhysicsWorldRuntimeResource();
        copy.copyFrom(this);
        return copy;
    }
}
