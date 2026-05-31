package dev.hytalemodding.impulse.core.internal.resources.owner;

/**
 * Internal mutation that runs in the current live-backend owner context.
 */
@FunctionalInterface
public interface PhysicsOwnerMutation {

    void run() throws Exception;
}
