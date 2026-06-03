package dev.hytalemodding.impulse.core.plugin.simulation.recorder;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Fluent recorder for high-volume rigid body spawns sharing one spawn template.
 */
public interface RigidBodySpawnTemplateRecorder {

    @Nonnull
    RigidBodySpawnTemplateRecorder body(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f position);

    @Nonnull
    RigidBodySpawnTemplateRecorder body(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ);

    @Nonnull
    RigidBodySpawnTemplateRecorder body(long bodyKeyMostSignificantBits,
        long bodyKeyLeastSignificantBits,
        float positionX,
        float positionY,
        float positionZ);
}
