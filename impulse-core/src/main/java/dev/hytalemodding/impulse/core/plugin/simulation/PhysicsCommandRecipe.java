package dev.hytalemodding.impulse.core.plugin.simulation;

import javax.annotation.Nonnull;

/**
 * Plugin-defined command authoring recipe.
 *
 * <p>Recipes run immediately on the caller thread and record copied value
 * commands into the supplied command context. They are the preferred way for plugins
 * to expose higher-level reusable physics operations without requiring Impulse
 * to execute unknown command classes.</p>
 */
@FunctionalInterface
public interface PhysicsCommandRecipe {

    void record(@Nonnull PhysicsCommandContext commands);
}
