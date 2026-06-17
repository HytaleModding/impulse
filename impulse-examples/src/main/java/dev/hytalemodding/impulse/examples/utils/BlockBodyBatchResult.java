package dev.hytalemodding.impulse.examples.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

record BlockBodyBatchResult(@Nullable ExamplePhysicsUtils.SpawnedBlockBody[] bodies,
                            int count,
                            long entityApplyNanos,
                            long entityAttachNanos) {

    BlockBodyBatchResult {
        if (bodies != null && bodies.length != count) {
            throw new IllegalArgumentException("Collected body count does not match batch count");
        }
        count = Math.max(0, count);
        entityApplyNanos = Math.max(0L, entityApplyNanos);
        entityAttachNanos = Math.max(0L, entityAttachNanos);
    }

    @Nonnull
    ExamplePhysicsUtils.SpawnedBlockBody[] collectedBodies() {
        if (bodies == null) {
            throw new IllegalStateException("Block body batch did not collect body results");
        }
        return bodies;
    }

    @Nonnull
    ExamplePhysicsUtils.BlockBodyBatchTiming timing() {
        return new ExamplePhysicsUtils.BlockBodyBatchTiming(count,
            entityApplyNanos,
            entityAttachNanos);
    }
}
