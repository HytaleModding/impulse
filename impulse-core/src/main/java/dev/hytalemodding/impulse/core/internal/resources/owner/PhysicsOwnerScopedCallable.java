package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsOwnerAccess;
import javax.annotation.Nonnull;

/**
 * Internal owner-context operation that resolves live backend objects through scoped access.
 *
 * @param <T> value returned to the caller
 */
@FunctionalInterface
public interface PhysicsOwnerScopedCallable<T> {

    T call(@Nonnull PhysicsOwnerAccess access) throws Exception;
}
