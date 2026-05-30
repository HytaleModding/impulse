package dev.hytalemodding.impulse.core.internal.resources.owner;

/**
 * Internal operation that runs on the current live-backend owner thread and returns a value.
 *
 * @param <T> value returned to the caller
 */
@FunctionalInterface
public interface PhysicsOwnerCallable<T> {

    T call() throws Exception;
}
