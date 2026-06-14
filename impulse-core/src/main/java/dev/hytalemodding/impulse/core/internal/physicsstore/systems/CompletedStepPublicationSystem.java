package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.BodyHitMetadata;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.BodySnapshotMetadata;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreSnapshotFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Publishes the last completed backend state as a copied PhysicsStore snapshot frame.
 */
public final class CompletedStepPublicationSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, TargetBindingSystem.class),
        new SystemDependency<>(Order.AFTER, PhysicsStoreReadRequestSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSnapshotResource snapshot = store.getResource(PhysicsSnapshotResource.getResourceType());
        PhysicsProfilingResource profiling = store.getResource(PhysicsProfilingResource.getResourceType());
        boolean profilingEnabled = profiling.isEnabled();
        long snapshotStartNanos = profilingEnabled ? System.nanoTime() : 0L;
        List<PhysicsStoreBodySnapshot> bodies = new ArrayList<>();
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) ->
            backendRuntime.snapshotBodies(spaceHandle.value(),
                bodyConsumer -> runtime.forEachBodyHandle(spaceHandle, bodyConsumer::accept),
                (bodyId,
                    _,
                    bodyTypeCode,
                    positionX,
                    positionY,
                    positionZ,
                    rotationX,
                    rotationY,
                    rotationZ,
                    rotationW,
                    linearVelocityX,
                    linearVelocityY,
                    linearVelocityZ,
                    angularVelocityX,
                    angularVelocityY,
                    angularVelocityZ,
                    sleeping,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    centerOfMassOffsetY,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _) -> collectBodySnapshot(runtime,
                        bodies,
                        bodyId,
                        bodyTypeCode,
                        positionX,
                        positionY,
                        positionZ,
                        rotationX,
                        rotationY,
                        rotationZ,
                        rotationW,
                        linearVelocityX,
                        linearVelocityY,
                        linearVelocityZ,
                        angularVelocityX,
                        angularVelocityY,
                        angularVelocityZ,
                        centerOfMassOffsetY,
                        sleeping)));
        long nextSequence = snapshot.getLatestFrame().sequence() + 1L;
        PhysicsStoreSnapshotFrame frame = new PhysicsStoreSnapshotFrame(nextSequence, dt, bodies);
        long snapshotNanos = profilingEnabled ? System.nanoTime() - snapshotStartNanos : 0L;
        snapshot.publish(frame);
        profiling.recordSnapshot(snapshotNanos, bodies.size());
        StepBackendEvents backendEvents = collectBackendEvents(store, runtime);
        store.getResource(PhysicsEventResource.getResourceType())
            .publishStepFrame(frame.sequence(),
                Math.max(0L, store.getExternalData().getWorld().getTick()),
                bodies.size(),
                profiling.getStepSubmitNanos(),
                snapshotNanos,
                backendEvents.physicsEvents,
                backendEvents.droppedBackendEventCount);
    }

    private static void collectBodySnapshot(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull List<PhysicsStoreBodySnapshot> bodies,
        long bodyId,
        int bodyTypeCode,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        float linearVelocityX,
        float linearVelocityY,
        float linearVelocityZ,
        float angularVelocityX,
        float angularVelocityY,
        float angularVelocityZ,
        float centerOfMassOffsetY,
        boolean sleeping) {
        BodySnapshotMetadata metadata = runtime.getBodySnapshotMetadata(bodyId);
        if (metadata == null) {
            return;
        }
        bodies.add(new PhysicsStoreBodySnapshot(metadata.bodyUuid(),
            metadata.spaceUuid(),
            BackendRuntimeCodes.bodyType(bodyTypeCode),
            new Vector3f(positionX, positionY, positionZ),
            new Quaternionf(rotationX, rotationY, rotationZ, rotationW),
            new Vector3f(linearVelocityX, linearVelocityY, linearVelocityZ),
            new Vector3f(angularVelocityX, angularVelocityY, angularVelocityZ),
            centerOfMassOffsetY,
            sleeping));
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
        runtime.forEachSpaceBinding((spaceUuid, _, spaceHandle, backendRuntime) -> {
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
            || bodyA.bodyKey() == null
            || bodyB == null
            || bodyB.bodyKey() == null) {
            backendEvents.droppedBackendEventCount++;
            return;
        }
        backendEvents.physicsEvents.add(new PhysicsContactEvent(spaceId,
            PhysicsContactPhase.OBSERVED,
            bodyA.bodyKey(),
            bodyB.bodyKey(),
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
