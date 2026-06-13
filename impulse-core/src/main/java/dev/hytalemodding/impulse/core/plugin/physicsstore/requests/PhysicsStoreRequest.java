package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied request boundary for cross-store PhysicsStore communication.
 */
public interface PhysicsStoreRequest {

    @Nonnull
    UUID requestUuid();
}
