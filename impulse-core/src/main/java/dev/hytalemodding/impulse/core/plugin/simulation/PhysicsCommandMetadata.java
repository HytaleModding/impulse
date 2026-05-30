package dev.hytalemodding.impulse.core.plugin.simulation;

/**
 * Correlation data assigned when a recorded physics command batch is frozen.
 */
public record PhysicsCommandMetadata(long submittedServerTick,
                                     long commandBatchSequence) {

    public PhysicsCommandMetadata {
        submittedServerTick = Math.max(0L, submittedServerTick);
        commandBatchSequence = Math.max(0L, commandBatchSequence);
    }
}
