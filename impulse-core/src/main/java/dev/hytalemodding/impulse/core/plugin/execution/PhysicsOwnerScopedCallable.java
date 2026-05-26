package dev.hytalemodding.impulse.core.plugin.execution;

import javax.annotation.Nonnull;

/**
 * Owner-thread operation that resolves live backend objects through a scoped access parameter.
 *
 * @param <T> value returned to the caller
 */
@FunctionalInterface
public interface PhysicsOwnerScopedCallable<T> {

    T call(@Nonnull PhysicsOwnerAccess access) throws Exception;
}
