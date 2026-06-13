package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.BodySnapshotMetadata;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
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
        new SystemDependency<>(Order.BEFORE, PersistenceCaptureSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSnapshotResource snapshot = store.getResource(PhysicsSnapshotResource.getResourceType());
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
        snapshot.publish(new PhysicsStoreSnapshotFrame(nextSequence, dt, bodies));
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
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
