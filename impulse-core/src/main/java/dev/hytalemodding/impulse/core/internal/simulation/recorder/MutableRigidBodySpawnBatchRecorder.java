package dev.hytalemodding.impulse.core.internal.simulation.recorder;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RigidBodySpawnBatch;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodySpawnBatchRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodySpawnRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * Fluent recorder for a bulk rigid body spawn command.
 */
public final class MutableRigidBodySpawnBatchRecorder implements RigidBodySpawnBatchRecorder {

    @Nonnull
    private final RigidBodySpawnBatch spawns;
    private boolean sealed;

    MutableRigidBodySpawnBatchRecorder(int expectedBodies) {
        spawns = new RigidBodySpawnBatch(expectedBodies);
    }

    @Nonnull
    @Override
    public RigidBodySpawnBatchRecorder body(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Consumer<RigidBodySpawnRecorder> recipe) {
        assertMutable();
        MutableRigidBodySpawnRecorder spawn = new MutableRigidBodySpawnRecorder(spawns::add, bodyKey);
        try {
            Objects.requireNonNull(recipe, "recipe").accept(spawn);
            spawn.record();
        } finally {
            spawn.seal();
        }
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnBatchRecorder body(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        assertMutable();
        Objects.requireNonNull(bodyKey, "bodyKey");
        return body(bodyKey.mostSignificantBits(),
            bodyKey.leastSignificantBits(),
            spaceId,
            shape,
            mass,
            bodyType,
            positionX,
            positionY,
            positionZ,
            settings,
            kind,
            persistenceMode);
    }

    @Nonnull
    @Override
    public RigidBodySpawnBatchRecorder body(long bodyKeyMostSignificantBits,
        long bodyKeyLeastSignificantBits,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        assertMutable();
        spawns.add(bodyKeyMostSignificantBits,
            bodyKeyLeastSignificantBits,
            spaceId,
            shape,
            mass,
            bodyType,
            positionX,
            positionY,
            positionZ,
            settings,
            kind,
            persistenceMode);
        return this;
    }

    boolean isEmpty() {
        return spawns.size() == 0;
    }

    @Nonnull
    RigidBodySpawnBatch spawns() {
        return spawns;
    }

    void seal() {
        sealed = true;
    }

    private void assertMutable() {
        if (sealed) {
            throw new IllegalStateException("Rigid body spawn batch is already recorded");
        }
    }
}
