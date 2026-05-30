package dev.hytalemodding.impulse.core.plugin.simulation;

import javax.annotation.Nonnull;

/**
 * Owner-thread command-context transaction that resolves live backend objects through scoped access.
 *
 * <p>Prefer copied recorder operations whenever possible. Transactions are opaque callbacks for
 * advanced diagnostics and temporary bridge code, so they cannot be replayed, serialized, or
 * summarized beyond their operation name and count.</p>
 */
@FunctionalInterface
public interface PhysicsOwnerTransaction {

    void run(@Nonnull PhysicsOwnerAccess access) throws Exception;
}
