package dev.hytalemodding.impulse.core.internal.resources.owner;

/**
 * Internal mutation that runs on the current live-backend owner thread.
 */
@FunctionalInterface
public interface PhysicsOwnerMutation {

    void run() throws Exception;
}
