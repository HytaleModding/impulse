package dev.hytalemodding.impulse.core.internal.resources.owner;

import javax.annotation.Nonnull;

/**
 * Unit of work executed within a serialized physics owner context.
 */
@FunctionalInterface
public interface PhysicsOwnerCommand {

    @Nonnull
    PhysicsOwnerSnapshot run() throws Exception;
}
