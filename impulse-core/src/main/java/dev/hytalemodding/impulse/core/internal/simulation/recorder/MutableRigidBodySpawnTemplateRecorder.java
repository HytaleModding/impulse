package dev.hytalemodding.impulse.core.internal.simulation.recorder;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RigidBodySpawnTemplateBatch;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodySpawnTemplateRecorder;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Fluent recorder for high-volume rigid body spawns sharing one copied spawn template.
 */
public final class MutableRigidBodySpawnTemplateRecorder implements RigidBodySpawnTemplateRecorder {

    @Nonnull
    private final RigidBodySpawnTemplateBatch spawns;
    private boolean sealed;

    MutableRigidBodySpawnTemplateRecorder(int expectedBodies,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        spawns = new RigidBodySpawnTemplateBatch(expectedBodies,
            spaceId,
            shape,
            mass,
            bodyType,
            settings,
            kind,
            persistenceMode);
    }

    @Nonnull
    @Override
    public RigidBodySpawnTemplateRecorder body(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f position) {
        Objects.requireNonNull(position, "position");
        return body(bodyKey, position.x, position.y, position.z);
    }

    @Nonnull
    @Override
    public RigidBodySpawnTemplateRecorder body(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ) {
        assertMutable();
        spawns.add(bodyKey, positionX, positionY, positionZ);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnTemplateRecorder body(long bodyKeyMostSignificantBits,
        long bodyKeyLeastSignificantBits,
        float positionX,
        float positionY,
        float positionZ) {
        assertMutable();
        spawns.add(bodyKeyMostSignificantBits,
            bodyKeyLeastSignificantBits,
            positionX,
            positionY,
            positionZ);
        return this;
    }

    boolean isEmpty() {
        return spawns.size() == 0;
    }

    @Nonnull
    RigidBodySpawnTemplateBatch spawns() {
        return spawns;
    }

    void seal() {
        sealed = true;
    }

    private void assertMutable() {
        if (sealed) {
            throw new IllegalStateException("Rigid body spawn template is already recorded");
        }
    }
}
