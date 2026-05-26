package dev.hytalemodding.impulse.core.plugin.execution;

import javax.annotation.Nonnull;

/**
 * Owner-thread mutation that resolves live backend objects through a scoped access parameter.
 */
@FunctionalInterface
public interface PhysicsOwnerScopedMutation {

    void run(@Nonnull PhysicsOwnerAccess access) throws Exception;
}
