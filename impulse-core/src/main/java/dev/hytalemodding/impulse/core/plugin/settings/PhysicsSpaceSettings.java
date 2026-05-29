package dev.hytalemodding.impulse.core.plugin.settings;

import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

/**
 * Per-space configuration aggregate for terrain collision, solver tuning,
 * collision LOD, visual sync, and detached visual materialization.
 *
 * <p>Settings are attached to a space at creation time via
 * {@link PhysicsWorldResource#createSpace} and can be changed later with
 * {@link PhysicsWorldResource#setSpaceSettings}.</p>
 *
 * <p>The grouped accessors expose the domain-owned settings objects. Internal
 * code should read and mutate the domain group directly instead of adding flat
 * shortcut state here.</p>
 *
 * <p>Default settings have world collision disabled ({@link WorldCollisionMode#NONE}),
 * which keeps Impulse fully opt-in: no terrain bodies are created unless the integrator
 * explicitly opts in.</p>
 */
public class PhysicsSpaceSettings {

    @Nonnull
    private final PhysicsWorldCollisionSettings worldCollisionSettings;
    @Nonnull
    private final PhysicsVisualSyncSettings visualSyncSettings;
    @Nonnull
    private final PhysicsSolverSettings solverSettings;
    @Nonnull
    private final PhysicsVisualMaterializationSettings visualMaterializationSettings;
    @Nonnull
    private final PhysicsCollisionLodSettings collisionLodSettings;
    @Nonnull
    private final PhysicsExtensionSettings extensionSettings;

    public PhysicsSpaceSettings() {
        worldCollisionSettings = new PhysicsWorldCollisionSettings();
        visualSyncSettings = new PhysicsVisualSyncSettings();
        solverSettings = new PhysicsSolverSettings();
        visualMaterializationSettings = new PhysicsVisualMaterializationSettings();
        collisionLodSettings = new PhysicsCollisionLodSettings();
        extensionSettings = new PhysicsExtensionSettings();
    }

    public PhysicsSpaceSettings(@Nonnull PhysicsSpaceSettings settings) {
        worldCollisionSettings =
            new PhysicsWorldCollisionSettings(settings.worldCollisionSettings);
        visualSyncSettings =
            new PhysicsVisualSyncSettings(settings.visualSyncSettings);
        solverSettings =
            new PhysicsSolverSettings(settings.solverSettings);
        visualMaterializationSettings =
            new PhysicsVisualMaterializationSettings(settings.visualMaterializationSettings);
        collisionLodSettings =
            new PhysicsCollisionLodSettings(settings.collisionLodSettings);
        extensionSettings = new PhysicsExtensionSettings(settings.extensionSettings);
    }

    /**
     * Terrain collision streaming and chunk-boundary behavior.
     */
    @Nonnull
    public PhysicsWorldCollisionSettings getWorldCollisionSettings() {
        return worldCollisionSettings;
    }

    /**
     * Server-to-Hytale transform sync sampling for entity-backed and follower visuals.
     */
    @Nonnull
    public PhysicsVisualSyncSettings getVisualSyncSettings() {
        return visualSyncSettings;
    }

    /**
     * Backend solver and sleep tuning.
     */
    @Nonnull
    public PhysicsSolverSettings getSolverSettings() {
        return solverSettings;
    }

    /**
     * Generated visual proxies for detached bodies.
     */
    @Nonnull
    public PhysicsVisualMaterializationSettings getVisualMaterializationSettings() {
        return visualMaterializationSettings;
    }

    /**
     * Distance-based dynamic-body collision LOD.
     */
    @Nonnull
    public PhysicsCollisionLodSettings getCollisionLodSettings() {
        return collisionLodSettings;
    }

    /**
     * Capability-keyed backend extension settings.
     */
    @Nonnull
    public PhysicsExtensionSettings getExtensionSettings() {
        return extensionSettings;
    }

    @Nonnull
    public static PhysicsSpaceSettings defaults() {
        return new PhysicsSpaceSettings();
    }

    /**
     * Convenience factory for a space with streaming world collision enabled.
     */
    @Nonnull
    public static PhysicsSpaceSettings streamingWorldCollision() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();
        settings.getWorldCollisionSettings().setWorldCollisionMode(WorldCollisionMode.STREAMING);
        return settings;
    }
}
