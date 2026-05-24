package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import javax.annotation.Nonnull;

/**
 * Creates and configures a backend body on the physics owner.
 *
 * <p>The supplied space and returned body are live backend objects. Do not retain them for
 * world-thread use; keep the {@link PhysicsBodyId} returned by {@link PhysicsBodies#spawn} instead.</p>
 */
@FunctionalInterface
public interface PhysicsBodyFactory {

    @Nonnull
    PhysicsBody create(@Nonnull PhysicsSpace space) throws Exception;
}
