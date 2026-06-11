package dev.hytalemodding.impulse.core.internal.systems.body;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyKinematicTargetComponent;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Tracks the last kinematic target submitted from ECS so unchanged targets do not resubmit.
 */
final class RigidBodyKinematicTargetState {

    @Nonnull
    private final Object2ObjectMap<RigidBodyKey, TargetEntry> submittedTargets =
        new Object2ObjectOpenHashMap<>();
    private long generation;

    synchronized void beginTick() {
        generation++;
    }

    synchronized boolean shouldSubmit(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyKinematicTargetComponent target) {
        TargetSnapshot snapshot = TargetSnapshot.copyOf(target);
        if (!snapshot.hasOperations()) {
            submittedTargets.remove(bodyKey);
            return false;
        }
        TargetEntry previous = submittedTargets.get(bodyKey);
        if (previous != null && snapshot.equals(previous.snapshot())) {
            submittedTargets.put(bodyKey, new TargetEntry(snapshot, generation));
            return false;
        }
        submittedTargets.put(bodyKey, new TargetEntry(snapshot, generation));
        return true;
    }

    synchronized void finishTick() {
        submittedTargets.values().removeIf(entry -> entry.generation() != generation);
    }

    synchronized void clear(@Nonnull RigidBodyKey bodyKey) {
        submittedTargets.remove(bodyKey);
    }

    synchronized void clearAll(@Nonnull Collection<RigidBodyKey> bodyKeys) {
        for (RigidBodyKey bodyKey : bodyKeys) {
            submittedTargets.remove(bodyKey);
        }
    }

    synchronized int trackedTargetCount() {
        return submittedTargets.size();
    }

    private record TargetEntry(@Nonnull TargetSnapshot snapshot, long generation) {
    }

    private record TargetSnapshot(float positionX,
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
                                  boolean transformEnabled,
                                  boolean velocityEnabled,
                                  boolean activate) {

        @Nonnull
        static TargetSnapshot copyOf(@Nonnull PhysicsBodyKinematicTargetComponent target) {
            Vector3f position = target.getPosition();
            Quaternionf rotation = target.getRotation();
            Vector3f linearVelocity = target.getLinearVelocity();
            Vector3f angularVelocity = target.getAngularVelocity();
            return new TargetSnapshot(position.x,
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
                target.isTransformEnabled(),
                target.isVelocityEnabled(),
                target.isActivate());
        }

        boolean hasOperations() {
            return transformEnabled || velocityEnabled;
        }
    }
}
