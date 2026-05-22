package dev.hytalemodding.impulse.core.plugin.resources;

/**
 * Operation that must run on the thread currently owning live physics backend state and return a
 * value to the caller.
 *
 * <p>The callback may read or mutate Impulse backend objects such as live physics bodies and
 * spaces. It must not access Hytale ECS store state unless the caller can prove that access is
 * valid from the physics owner thread.</p>
 *
 * @param <T> value returned to the caller
 */
@FunctionalInterface
public interface PhysicsOwnerCallable<T> {

    T call() throws Exception;
}
