package dev.hytalemodding.impulse.examples.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

record BlockBodyBatchResult(@Nullable ExamplePhysicsUtils.SpawnedBlockBody[] bodies,
                            int count,
                            long physicsStoreApplyNanos,
                            long visualAttachNanos) {

    BlockBodyBatchResult {
        if (bodies != null && bodies.length != count) {
            throw new IllegalArgumentException("Collected body count does not match batch count");
        }
        count = Math.max(0, count);
        physicsStoreApplyNanos = Math.max(0L, physicsStoreApplyNanos);
        visualAttachNanos = Math.max(0L, visualAttachNanos);
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
            physicsStoreApplyNanos,
            visualAttachNanos);
    }
}
