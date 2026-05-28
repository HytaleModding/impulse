package dev.hytalemodding.impulse.core.plugin.persistence;

import javax.annotation.Nonnull;

public record PhysicsPersistenceSyncResult(boolean synced,
                                           int spaces,
                                           int bodies,
                                           int joints,
                                           @Nonnull String skippedReason) {
}
