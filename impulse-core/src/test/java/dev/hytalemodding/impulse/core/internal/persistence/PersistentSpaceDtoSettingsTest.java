package dev.hytalemodding.impulse.core.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionDefaults;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.Objects;
import java.util.UUID;
import org.bson.BsonDocument;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PersistentSpaceDtoSettingsTest {

    @Test
    void roundTripPreservesDetachedVisualCadenceSettingsAndPhysicsChunkKeys() {
        PhysicsSpaceSettings original = PhysicsSpaceSettings.defaults();
        original.getPhysicsChunkTerrainSettings().setNativeVoxelCollisionEnabled(true);
        original.getVisualMaterializationSettings().setDetachedVisualInterestRefreshIntervalTicks(7);
        original.getVisualMaterializationSettings().setDetachedVisualCandidateRefreshIntervalTicks(9);
        original.getVisualMaterializationSettings().setDetachedVisualVisibilityCheckIntervalTicks(11);

        PhysicsChunkTerrainSettings terrain = original.getPhysicsChunkTerrainSettings();
        PersistentSpaceDto state = new PersistentSpaceDto(UUID.randomUUID(),
            "test:settings-persistence",
            new Vector3f(0.0f, -9.81f, 0.0f),
            terrain.getTerrainMode(),
            terrain.getEntityChunkBoundaryMode(),
            terrain.isNativeVoxelCollisionEnabled(),
            terrain.getTerrainRadius(),
            terrain.getBodyTerrainRadius(),
            terrain.getTerrainTtlTicks(),
            0.85f,
            0.2f,
            new SolverSettingsComponent(original.getSolverSettings()),
            new VisualSyncSettingsComponent(original.getVisualSyncSettings()),
            new VisualMaterializationSettingsComponent(original.getVisualMaterializationSettings()),
            new CollisionLodSettingsComponent(original.getCollisionLodSettings()),
            new ExtensionSettingsComponent(original.getExtensionSettings()));

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

        PhysicsSpaceSettings decoded = decodedState.toSettings();
        assertTrue(decoded.getPhysicsChunkTerrainSettings().isNativeVoxelCollisionEnabled());
        assertDetachedVisualCadence(decoded, 7, 9, 11);

        PersistentSpaceDto copiedState = state.copy();
        assertEquals(0.85f, copiedState.getChunkCollisionFriction(), 0.0001f);
        assertEquals(0.2f, copiedState.getChunkCollisionRestitution(), 0.0001f);

        PhysicsSpaceSettings copied = copiedState.toSettings();
        assertTrue(copied.getPhysicsChunkTerrainSettings().isNativeVoxelCollisionEnabled());
        assertDetachedVisualCadence(copied, 7, 9, 11);
    }

    @Test
    void roundTripPreservesChunkCollisionFilter() {
        PhysicsChunkTerrainSettings terrain =
            PhysicsSpaceSettings.defaults().getPhysicsChunkTerrainSettings();
        PersistentSpaceDto state = new PersistentSpaceDto(UUID.randomUUID(),
            "test:chunk-filter-persistence",
            new Vector3f(0.0f, -9.81f, 0.0f),
            terrain.getTerrainMode(),
            terrain.getEntityChunkBoundaryMode(),
            false,
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RADIUS,
            PhysicsChunkTerrainSettings.DEFAULT_BODY_TERRAIN_RADIUS,
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_TTL_TICKS,
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
        PhysicsSpaceSettings decodedSettings = decoded.toSettings();
        assertEquals(terrain.getEntityChunkBoundaryMode(),
            decodedSettings.getPhysicsChunkTerrainSettings().getEntityChunkBoundaryMode());
    }

    private static void assertDetachedVisualCadence(PhysicsSpaceSettings settings,
        int interestInterval,
        int candidateInterval,
        int visibilityInterval) {
        assertEquals(interestInterval,
            settings.getVisualMaterializationSettings().getDetachedVisualInterestRefreshIntervalTicks());
        assertEquals(candidateInterval,
            settings.getVisualMaterializationSettings().getDetachedVisualCandidateRefreshIntervalTicks());
        assertEquals(visibilityInterval,
            settings.getVisualMaterializationSettings().getDetachedVisualVisibilityCheckIntervalTicks());
    }
}
