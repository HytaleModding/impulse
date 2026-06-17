package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

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
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource.BodyRegistrationPublication;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.BodyHitMetadata;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStepSchedulerResource.CompletedStep;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStepSchedulerResource.StepInput;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

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
        Set<UUID> snapshotBodyUuids = new ObjectOpenHashSet<>();
        for (PhysicsBodySnapshot body : bodies) {
            snapshotBodyUuids.add(body.bodyUuid());
        }
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
                snapshotBodyUuids));
        profiling.recordSnapshot(completed.snapshotNanos(), bodies.size());
        StepBackendEvents backendEvents = collectBackendEvents(store, runtime);
        store.getResource(PhysicsEventResource.getResourceType())
            .publishStepFrame(frame.sequence(),
                Math.max(0L, store.getExternalData().getWorld().getTick()),
                bodies.size(),
                profiling.getStepSubmitNanos(),
                completed.snapshotNanos(),
                backendEvents.physicsEvents,
                backendEvents.droppedBackendEventCount);
    }

    @Nonnull
    private static List<BodyRegistrationPublication> collectRegistrationViews(
        @Nonnull Store<PhysicsStore> store,
        int systemIndex,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull Set<UUID> snapshotBodyUuids) {
        List<BodyRegistrationPublication> registrations = new ArrayList<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectRegistrationViews(runtime,
                compatibility,
                snapshotBodyUuids,
                registrations,
                chunk);
        store.forEachChunk(systemIndex, collector);
        return registrations;
    }

    private static void collectRegistrationViews(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull Set<UUID> snapshotBodyUuids,
        @Nonnull List<BodyRegistrationPublication> registrations,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            UUID rowUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(rowUuid)) {
                continue;
            }
            var rowRef = chunk.getReferenceTo(index);
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body != null && snapshotBodyUuids.contains(rowUuid)) {
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
    private static StepBackendEvents collectBackendEvents(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime) {
        if (!store.getResource(PhysicsWorldSettingsResource.getResourceType())
            .getSettings()
            .getEventCollectionMode()
            .collectsBackendEvents()) {
            return StepBackendEvents.EMPTY;
        }
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        StepBackendEvents backendEvents = new StepBackendEvents();
        runtime.forEachRuntimeSpaceBinding((spaceRef, _, spaceHandle, backendRuntime) -> {
            UUID spaceUuid = runtime.getSpaceUuid(spaceRef);
            if (spaceUuid == null) {
                backendEvents.droppedBackendEventCount += backendRuntime.contactCount(spaceHandle.value());
                return;
            }
            SpaceId spaceId = compatibility.getSpaceId(spaceUuid);
            if (spaceId == null) {
                backendEvents.droppedBackendEventCount += backendRuntime.contactCount(spaceHandle.value());
                return;
            }
            backendRuntime.contacts(spaceHandle.value(), (bodyAId,
                bodyBId,
                pointAX,
                pointAY,
                pointAZ,
                pointBX,
                pointBY,
                pointBZ,
                normalBX,
                normalBY,
                normalBZ,
                distance,
                impulse) -> collectContactEvent(runtime,
                    backendEvents,
                    spaceId,
                    bodyAId,
                    bodyBId,
                    pointAX,
                    pointAY,
                    pointAZ,
                    pointBX,
                    pointBY,
                    pointBZ,
                    normalBX,
                    normalBY,
                    normalBZ,
                    distance,
                    impulse));
        });
        return backendEvents;
    }

    private static void collectContactEvent(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull StepBackendEvents backendEvents,
        @Nonnull SpaceId spaceId,
        long bodyAId,
        long bodyBId,
        float pointAX,
        float pointAY,
        float pointAZ,
        float pointBX,
        float pointBY,
        float pointBZ,
        float normalBX,
        float normalBY,
        float normalBZ,
        float distance,
        float impulse) {
        BodyHitMetadata bodyA = runtime.getBodyHitMetadata(bodyAId);
        BodyHitMetadata bodyB = runtime.getBodyHitMetadata(bodyBId);
        if (bodyA == null
            || bodyA.bodyRef() == null
            || bodyB == null
            || bodyB.bodyRef() == null) {
            backendEvents.droppedBackendEventCount++;
            return;
        }
        UUID bodyAUuid = PhysicsStoreSystemSupport.rowUuid(bodyA.bodyRef());
        UUID bodyBUuid = PhysicsStoreSystemSupport.rowUuid(bodyB.bodyRef());
        if (PhysicsStoreSystemSupport.isNil(bodyAUuid)
            || PhysicsStoreSystemSupport.isNil(bodyBUuid)) {
            backendEvents.droppedBackendEventCount++;
            return;
        }
        backendEvents.physicsEvents.add(new PhysicsContactEvent(spaceId,
            PhysicsContactPhase.OBSERVED,
            bodyAUuid,
            bodyBUuid,
            new Vector3f(pointAX, pointAY, pointAZ),
            new Vector3f(pointBX, pointBY, pointBZ),
            new Vector3f(normalBX, normalBY, normalBZ),
            distance,
            impulse));
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

    private static final class StepBackendEvents {

        @Nonnull
        private static final StepBackendEvents EMPTY = new StepBackendEvents(List.of(), 0);

        @Nonnull
        private final List<PhysicsFrameEvent> physicsEvents;
        private int droppedBackendEventCount;

        private StepBackendEvents() {
            this(new ArrayList<>(), 0);
        }

        private StepBackendEvents(@Nonnull List<PhysicsFrameEvent> physicsEvents,
            int droppedBackendEventCount) {
            this.physicsEvents = physicsEvents;
            this.droppedBackendEventCount = droppedBackendEventCount;
        }
    }
}
