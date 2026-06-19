package dev.hytalemodding.impulse.core.plugin.settings;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import javax.annotation.Nonnull;

/**
 * Per-space configuration aggregate for chunk collision, solver tuning,
 * collision LOD, visual sync, and detached visual materialization.
 *
 * <p>Settings are stored on PhysicsStore space entities. New plugin code should create spaces and
 * change per-space settings through {@link PhysicsSpaces}; the world-resource space/settings
 * methods remain compatibility facades.</p>
 *
 * <p>The grouped accessors expose the domain-owned settings objects. Internal
 * code should read and mutate the domain group directly instead of adding flat
 * shortcut state here.</p>
 *
 * <p>Default settings have PhysicsChunk collision disabled ({@link PhysicsChunkTerrainMode#NONE}),
 * which keeps Impulse fully opt-in: no chunk-collision bodies are created unless the integrator
 * explicitly opts in.</p>
 */
public class PhysicsSpaceSettings {

    @Nonnull
    private final PhysicsChunkCollisionSettings physicsChunkCollisionSettings;
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
        physicsChunkCollisionSettings = new PhysicsChunkCollisionSettings();
        visualSyncSettings = new PhysicsVisualSyncSettings();
        solverSettings = new PhysicsSolverSettings();
        visualMaterializationSettings = new PhysicsVisualMaterializationSettings();
        collisionLodSettings = new PhysicsCollisionLodSettings();
        extensionSettings = new PhysicsExtensionSettings();
    }

    public PhysicsSpaceSettings(@Nonnull PhysicsSpaceSettings settings) {
        physicsChunkCollisionSettings =
            new PhysicsChunkCollisionSettings(settings.physicsChunkCollisionSettings);
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
     * Chunk-collision streaming and chunk-boundary behavior.
     */
    @Nonnull
    public PhysicsChunkCollisionSettings getPhysicsChunkCollisionSettings() {
        return physicsChunkCollisionSettings;
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
     * Convenience factory for a space with streaming PhysicsChunk collision enabled.
     */
    @Nonnull
    public static PhysicsSpaceSettings streamingPhysicsChunk() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();
        settings.getPhysicsChunkCollisionSettings()
            .setMode(PhysicsChunkTerrainMode.STREAMING);
        return settings;
    }
}
