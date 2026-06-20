package dev.hytalemodding.impulse.core.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionDefaults;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import java.util.Objects;
import java.util.UUID;
import org.bson.BsonDocument;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PersistentSpaceDtoSettingsTest {

    @Test
    void roundTripPreservesDetachedVisualCadenceSettingsAndPhysicsChunkKeys() {
        PhysicsChunkCollisionSettings chunkCollision = new PhysicsChunkCollisionSettings();
        chunkCollision.setMode(PhysicsChunkCollisionMode.STREAMING);
        chunkCollision.setNativeVoxelCollisionEnabled(true);
        PhysicsVisualMaterializationSettings visualMaterialization =
            new PhysicsVisualMaterializationSettings();
        visualMaterialization.setDetachedVisualInterestRefreshIntervalTicks(7);
        visualMaterialization.setDetachedVisualCandidateRefreshIntervalTicks(9);
        visualMaterialization.setDetachedVisualVisibilityCheckIntervalTicks(11);
        PersistentSpaceDto state = new PersistentSpaceDto(UUID.randomUUID(),
            "test:settings-persistence",
            new Vector3f(0.0f, -9.81f, 0.0f),
            chunkCollision.getMode(),
            chunkCollision.getEntityChunkBoundaryMode(),
            chunkCollision.isNativeVoxelCollisionEnabled(),
            chunkCollision.getRadius(),
            chunkCollision.getBodyRadius(),
            chunkCollision.getTtlTicks(),
            0.85f,
            0.2f,
            new SolverSettingsComponent(),
            new VisualSyncSettingsComponent(),
            new VisualMaterializationSettingsComponent(visualMaterialization),
            new CollisionLodSettingsComponent(),
            new ExtensionSettingsComponent());

        BsonDocument encoded = PersistentSpaceDto.CODEC.encode(state, new ExtraInfo()).asDocument();

        assertTrue(encoded.containsKey("PhysicsChunkTerrainMode"));
        assertTrue(encoded.containsKey("ChunkCollisionRadius"));
        assertTrue(encoded.containsKey("BodyChunkCollisionRadius"));
        assertTrue(encoded.containsKey("ChunkCollisionTtlTicks"));
        assertTrue(encoded.containsKey("NativeVoxelCollision"));
        assertTrue(encoded.containsKey("ChunkCollisionFriction"));
        assertTrue(encoded.containsKey("ChunkCollisionRestitution"));
        assertTrue(encoded.containsKey("VisualMaterializationSettings"));
        PersistentSpaceDto decodedState = Objects.requireNonNull(
            PersistentSpaceDto.CODEC.decode(encoded, new ExtraInfo()));
        assertEquals(0.85f, decodedState.getChunkCollisionFriction(), 0.0001f);
        assertEquals(0.2f, decodedState.getChunkCollisionRestitution(), 0.0001f);

        ChunkCollisionSettingsComponent decodedChunkCollision =
            decodedState.getChunkCollisionSettings();
        assertEquals(PhysicsChunkCollisionMode.STREAMING,
            decodedChunkCollision.getMode());
        assertTrue(decodedChunkCollision.isNativeVoxelCollisionEnabled());
        assertDetachedVisualCadence(decodedState.getVisualMaterializationSettings(), 7, 9, 11);

        PersistentSpaceDto copiedState = state.copy();
        assertEquals(0.85f, copiedState.getChunkCollisionFriction(), 0.0001f);
        assertEquals(0.2f, copiedState.getChunkCollisionRestitution(), 0.0001f);

        ChunkCollisionSettingsComponent copiedChunkCollision =
            copiedState.getChunkCollisionSettings();
        assertEquals(PhysicsChunkCollisionMode.STREAMING,
            copiedChunkCollision.getMode());
        assertTrue(copiedChunkCollision.isNativeVoxelCollisionEnabled());
        assertDetachedVisualCadence(copiedState.getVisualMaterializationSettings(), 7, 9, 11);
    }

    @Test
    void roundTripPreservesChunkCollisionFilter() {
        PhysicsChunkCollisionSettings chunkCollision = new PhysicsChunkCollisionSettings();
        PersistentSpaceDto state = new PersistentSpaceDto(UUID.randomUUID(),
            "test:chunk-filter-persistence",
            new Vector3f(0.0f, -9.81f, 0.0f),
            chunkCollision.getMode(),
            chunkCollision.getEntityChunkBoundaryMode(),
            false,
            PhysicsChunkCollisionSettings.DEFAULT_RADIUS,
            PhysicsChunkCollisionSettings.DEFAULT_BODY_RADIUS,
            PhysicsChunkCollisionSettings.DEFAULT_TTL_TICKS,
            PhysicsChunkCollisionDefaults.FRICTION,
            PhysicsChunkCollisionDefaults.RESTITUTION,
            0x40,
            0x03,
            new SolverSettingsComponent(),
            new VisualSyncSettingsComponent(),
            new VisualMaterializationSettingsComponent(),
            new CollisionLodSettingsComponent(),
            new ExtensionSettingsComponent());

        BsonDocument encoded = PersistentSpaceDto.CODEC.encode(state, new ExtraInfo()).asDocument();

        assertTrue(encoded.containsKey("ChunkCollisionFilter"));
        PersistentSpaceDto decoded = Objects.requireNonNull(
            PersistentSpaceDto.CODEC.decode(encoded, new ExtraInfo()));
        assertEquals(0x40, decoded.getChunkCollisionGroup());
        assertEquals(0x03, decoded.getChunkCollisionMask());
        assertEquals(0x40, state.copy().getChunkCollisionGroup());
        assertEquals(0x03, state.copy().getChunkCollisionMask());
        assertEquals(chunkCollision.getEntityChunkBoundaryMode(),
            decoded.getChunkCollisionSettings().getEntityChunkBoundaryMode());
    }

    private static void assertDetachedVisualCadence(VisualMaterializationSettingsComponent settings,
        int interestInterval,
        int candidateInterval,
        int visibilityInterval) {
        assertEquals(interestInterval,
            settings.getDetachedVisualInterestRefreshIntervalTicks());
        assertEquals(candidateInterval,
            settings.getDetachedVisualCandidateRefreshIntervalTicks());
        assertEquals(visibilityInterval,
            settings.getDetachedVisualVisibilityCheckIntervalTicks());
    }
}
