package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/**
 * Runtime-only handle completed when PhysicsStore drains and applies a queued request batch.
 */
public final class PhysicsStoreRequestFenceHandle {

    @Nonnull
    private final UUID fenceUuid;
    @Nonnull
    private final CompletionStage<PhysicsStoreRequestFenceResult> completion;

    public PhysicsStoreRequestFenceHandle(@Nonnull UUID fenceUuid,
        @Nonnull CompletionStage<PhysicsStoreRequestFenceResult> completion) {
        this.fenceUuid = Objects.requireNonNull(fenceUuid, "fenceUuid");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    @Nonnull
    public UUID fenceUuid() {
        return fenceUuid;
    }

    @Nonnull
    public CompletionStage<PhysicsStoreRequestFenceResult> completion() {
        return completion;
    }
}
