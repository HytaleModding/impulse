package dev.hytalemodding.impulse.core.plugin.simulation;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Public metadata view for a submitted physics simulation command batch.
 */
public final class PhysicsCommandBatch {

    @Nonnull
    private final PhysicsCommandMetadata metadata;
    private final int commandCount;

    public PhysicsCommandBatch(@Nonnull PhysicsCommandMetadata metadata,
        int commandCount) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.commandCount = Math.max(0, commandCount);
    }

    @Nonnull
    public PhysicsCommandMetadata metadata() {
        return metadata;
    }

    public int commandCount() {
        return commandCount;
    }
}
