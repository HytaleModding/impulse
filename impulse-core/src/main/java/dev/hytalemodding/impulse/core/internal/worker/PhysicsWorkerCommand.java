package dev.hytalemodding.impulse.core.internal.worker;

import javax.annotation.Nonnull;

/**
 * Unit of work executed by the physics worker thread.
 */
@FunctionalInterface
public interface PhysicsWorkerCommand {

    @Nonnull
    PhysicsWorkerSnapshot run() throws Exception;
}
