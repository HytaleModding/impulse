package dev.hytalemodding.impulse.core.plugin.execution;

/**
 * Opaque owner for live physics backend state.
 *
 * <p>Plugin consumers normally do not create or attach these handles directly. They are exposed so
 * the runtime resource does not leak internal worker implementation classes in public method
 * signatures.</p>
 */
public interface PhysicsOwnerHandle {

    boolean isPhysicsOwnerThread();
}
