package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource.BodyRegistrationPublication;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource.CompletedStep;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource.StepInput;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * Publishes the last completed backend state as a copied PhysicsStore snapshot frame.
 */
public final class CompletedStepPublicationSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, TargetBindingSystem.class),
        new SystemDependency<>(Order.AFTER, TerrainColliderBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        CompletedStep completed = store.getResource(PhysicsStepSchedulerResource.getResourceType())
            .pollCompletedStep();
        if (completed == null) {
            return;
        }
        if (completed.failed()) {
            Throwable failure = completed.failure();
            String message = failure != null ? failure.getMessage() : null;
            store.getResource(PhysicsRestoreStatusResource.getResourceType())
                .markFailed(message != null ? message : "PhysicsStore owner-lane step failed");
            throw new IllegalStateException("PhysicsStore owner-lane step failed", failure);
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSnapshotResource snapshot = store.getResource(PhysicsSnapshotResource.getResourceType());
        PhysicsProfilingResource profiling = store.getResource(PhysicsProfilingResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        profiling.recordStep(completed.stepSubmitNanos(),
            completed.spaces(),
            completed.substeps(),
            completed.nativePhaseStats());
        StepInput input = completed.input();
        if (input != null) {
            profiling.recordStepScheduling(input.inputDtSeconds(),
                input.submittedDtSeconds(),
                input.backlogDtSeconds(),
                input.droppedBacklogDtSeconds(),
                input.dtCapHit());
        }
        List<PhysicsBodySnapshot> bodies = completed.bodySnapshots();
        long nextSequence = snapshot.getLatestFrame().sequence() + 1L;
        float frameDt = input != null ? input.submittedDtSeconds() : dt;
        PhysicsSnapshotFrame frame = new PhysicsSnapshotFrame(nextSequence,
            frameDt,
            bodies);
        snapshot.publish(frame);
        store.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .publish(collectRegistrationViews(store,
                systemIndex,
                runtime,
                compatibility,
                snapshot));
        profiling.recordSnapshot(completed.snapshotNanos(), bodies.size());
        store.getResource(PhysicsEventResource.getResourceType())
            .publishStepFrame(frame.sequence(),
                Math.max(0L, store.getExternalData().getWorld().getTick()),
                bodies.size(),
                profiling.getStepSubmitNanos(),
                completed.snapshotNanos(),
                completed.physicsEvents(),
                completed.droppedBackendEventCount());
    }

    @Nonnull
    private static List<BodyRegistrationPublication> collectRegistrationViews(
        @Nonnull Store<PhysicsStore> store,
        int systemIndex,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull PhysicsSnapshotResource snapshot) {
        List<BodyRegistrationPublication> registrations =
            new ArrayList<>(snapshot.getLatestFrame().bodies().size());
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectRegistrationViews(runtime,
                compatibility,
                snapshot,
                registrations,
                chunk);
        store.forEachChunk(systemIndex, collector);
        return registrations;
    }

    private static void collectRegistrationViews(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull PhysicsSnapshotResource snapshot,
        @Nonnull List<BodyRegistrationPublication> registrations,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            UUID rowUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(rowUuid)) {
                continue;
            }
            var rowRef = chunk.getReferenceTo(index);
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body != null && snapshot.containsBody(rowUuid)) {
                SpaceId spaceId = compatibility.getSpaceId(body.getSpaceUuid());
                if (spaceId != null) {
                    registrations.add(new BodyRegistrationPublication(rowRef,
                        new PhysicsBodyRegistrationView(rowUuid,
                            spaceId,
                            body.getKind(),
                            body.getPersistenceMode())));
                }
            }
            TerrainColliderComponent terrain =
                chunk.getComponent(index, TerrainColliderComponent.getComponentType());
            if (terrain != null && runtime.hasTerrainBodyHandles(rowRef)) {
                SpaceId spaceId = compatibility.getSpaceId(terrain.getSpaceUuid());
                if (spaceId != null) {
                    registrations.add(new BodyRegistrationPublication(rowRef,
                        new PhysicsBodyRegistrationView(rowUuid,
                            spaceId,
                            PhysicsBodyKind.WORLD_COLLISION,
                            PhysicsBodyPersistenceMode.RUNTIME_ONLY)));
                }
            }
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.uuidQuery();
    }
}
