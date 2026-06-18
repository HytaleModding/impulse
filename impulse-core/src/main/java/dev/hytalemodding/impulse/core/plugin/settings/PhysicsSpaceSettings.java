package dev.hytalemodding.impulse.core.plugin.settings;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import javax.annotation.Nonnull;

/**
 * Per-space configuration aggregate for terrain collision, solver tuning,
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
 * <p>Default settings have PhysicsChunk terrain disabled ({@link PhysicsChunkTerrainMode#NONE}),
 * which keeps Impulse fully opt-in: no terrain bodies are created unless the integrator
 * explicitly opts in.</p>
 */
public class PhysicsSpaceSettings {

    @Nonnull
    private final PhysicsChunkTerrainSettings physicsChunkTerrainSettings;
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
        physicsChunkTerrainSettings = new PhysicsChunkTerrainSettings();
        visualSyncSettings = new PhysicsVisualSyncSettings();
        solverSettings = new PhysicsSolverSettings();
        visualMaterializationSettings = new PhysicsVisualMaterializationSettings();
        collisionLodSettings = new PhysicsCollisionLodSettings();
        extensionSettings = new PhysicsExtensionSettings();
    }

    public PhysicsSpaceSettings(@Nonnull PhysicsSpaceSettings settings) {
        physicsChunkTerrainSettings =
            new PhysicsChunkTerrainSettings(settings.physicsChunkTerrainSettings);
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
     * Terrain collider streaming and chunk-boundary behavior.
     */
    @Nonnull
    public PhysicsChunkTerrainSettings getPhysicsChunkTerrainSettings() {
        return physicsChunkTerrainSettings;
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
     * Convenience factory for a space with streaming PhysicsChunk terrain enabled.
     */
    @Nonnull
    public static PhysicsSpaceSettings streamingPhysicsChunk() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();
        settings.getPhysicsChunkTerrainSettings()
            .setTerrainMode(PhysicsChunkTerrainMode.STREAMING);
        return settings;
    }
}
