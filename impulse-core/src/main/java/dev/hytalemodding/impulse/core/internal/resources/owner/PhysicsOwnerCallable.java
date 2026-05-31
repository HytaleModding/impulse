package dev.hytalemodding.impulse.core.internal.resources.owner;

/**
 * Internal operation that runs in the current live-backend owner context and returns a value.
 *
 * @param <T> value returned to the caller
 */
@FunctionalInterface
public interface PhysicsOwnerCallable<T> {

    T call() throws Exception;
}
