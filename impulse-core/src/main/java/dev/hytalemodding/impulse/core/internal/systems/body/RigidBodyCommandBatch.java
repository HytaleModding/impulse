package dev.hytalemodding.impulse.core.internal.systems.body;

import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyKinematicTargetComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.PhysicsCommandRecorder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Collects one entity-store tick of rigid-body ECS work into a single command submission.
 */
final class RigidBodyCommandBatch {

    @Nonnull
    private final List<SpawnCommand> spawns = new ArrayList<>();
    @Nonnull
    private final List<KinematicTargetCommand> kinematicTargets = new ArrayList<>();
    @Nonnull
    private final ObjectSet<RigidBodyKey> pendingBodyKeys = new ObjectOpenHashSet<>();

    void addSpawn(@Nonnull RigidBodySpawnPlan plan, @Nonnull Vector3f position) {
        spawns.add(new SpawnCommand(plan, position));
        pendingBodyKeys.add(plan.bodyKey());
    }

    void addKinematicTarget(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyKinematicTargetComponent target) {
        kinematicTargets.add(new KinematicTargetCommand(bodyKey, target));
    }

    boolean hasPendingBody(@Nonnull RigidBodyKey bodyKey) {
        return pendingBodyKeys.contains(bodyKey);
    }

    boolean hasKinematicTargets() {
        return !kinematicTargets.isEmpty();
    }

    @Nonnull
    List<RigidBodyKey> kinematicTargetKeys() {
        List<RigidBodyKey> keys = new ArrayList<>(kinematicTargets.size());
        for (KinematicTargetCommand target : kinematicTargets) {
            keys.add(target.bodyKey());
        }
        return keys;
    }

    int expectedOperations() {
        int operations = spawns.size();
        for (KinematicTargetCommand target : kinematicTargets) {
            operations += target.operationCount();
        }
        return operations;
    }

    @Nullable
    PhysicsCommandHandle submit(@Nonnull PhysicsWorldRuntimeResource resource) {
        int expectedOperations = expectedOperations();
        if (expectedOperations == 0) {
            return null;
        }
        return resource.submitCommands(0L, expectedOperations, this::record);
    }

    void record(@Nonnull PhysicsCommandRecorder commands) {
        for (SpawnCommand spawn : spawns) {
            RigidBodySpawnPlan plan = spawn.plan();
            commands.spawnBody(plan.bodyKey(), recorder -> recorder
                .space(plan.spaceId())
                .shape(plan.requireShape())
                .mass(plan.mass())
                .type(plan.bodyType())
                .position(spawn.position())
                .settings(plan.settings())
                .kind(PhysicsBodyKind.BODY)
                .persistence(plan.persistenceMode()));
        }
        for (KinematicTargetCommand target : kinematicTargets) {
            target.record(commands);
        }
    }

    private record SpawnCommand(@Nonnull RigidBodySpawnPlan plan,
                                @Nonnull Vector3f position) {

        private SpawnCommand {
            position = new Vector3f(position);
        }
    }

    private record KinematicTargetCommand(@Nonnull RigidBodyKey bodyKey,
                                          @Nonnull Vector3f position,
                                          @Nonnull Quaternionf rotation,
                                          @Nonnull Vector3f linearVelocity,
                                          @Nonnull Vector3f angularVelocity,
                                          boolean transformEnabled,
                                          boolean velocityEnabled,
                                          boolean activate) {

        private KinematicTargetCommand(@Nonnull RigidBodyKey bodyKey,
            @Nonnull PhysicsBodyKinematicTargetComponent target) {
            this(bodyKey,
                target.getPosition(),
                target.getRotation(),
                target.getLinearVelocity(),
                target.getAngularVelocity(),
                target.isTransformEnabled(),
                target.isVelocityEnabled(),
                target.isActivate());
        }

        private KinematicTargetCommand {
            position = new Vector3f(position);
            rotation = new Quaternionf(rotation);
            linearVelocity = new Vector3f(linearVelocity);
            angularVelocity = new Vector3f(angularVelocity);
        }

        int operationCount() {
            int count = 0;
            if (transformEnabled) {
                count++;
            }
            if (velocityEnabled) {
                count++;
            }
            return count;
        }

        void record(@Nonnull PhysicsCommandRecorder commands) {
            if (transformEnabled) {
                commands.setBodyTransform(bodyKey, position, rotation, activate);
            }
            if (velocityEnabled) {
                commands.setBodyVelocity(bodyKey, linearVelocity, angularVelocity, activate);
            }
        }
    }
}
