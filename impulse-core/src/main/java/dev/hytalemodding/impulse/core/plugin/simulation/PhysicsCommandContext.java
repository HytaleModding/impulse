package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.core.plugin.simulation.recorder.PhysicsCommandRecorder;

import javax.annotation.Nonnull;

/**
 * Plugin-facing authoring context for deferred physics simulation intent.
 *
 * <p>This is an authoring surface only. Mutable storage, freezing, and owner-lane
 * execution live in the internal simulation package.</p>
 *
 * <p>Commands recorded here are copied into an internal batch before they cross
 * the physics-owner boundary. Completion of that batch means the owner lane
 * executed the operations; published snapshots may still lag by one or more
 * physics frames.</p>
 */
public interface PhysicsCommandContext extends PhysicsCommandRecorder {

    /**
     * Records another recipe into the same pending command batch.
     */
    @Nonnull
    PhysicsCommandContext compose(@Nonnull PhysicsCommandRecipe recipe);
}
