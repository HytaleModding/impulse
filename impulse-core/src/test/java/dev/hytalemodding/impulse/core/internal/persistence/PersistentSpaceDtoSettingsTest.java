package dev.hytalemodding.impulse.core.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualSyncSettingsComponent;
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
        original.getPhysicsChunkTerrainSettings().setNativeVoxelTerrainEnabled(true);
        original.getPhysicsChunkTerrainSettings().setTerrainMaterial(0.85f, 0.2f);
        original.getVisualMaterializationSettings().setDetachedVisualInterestRefreshIntervalTicks(7);
        original.getVisualMaterializationSettings().setDetachedVisualCandidateRefreshIntervalTicks(9);
        original.getVisualMaterializationSettings().setDetachedVisualVisibilityCheckIntervalTicks(11);

        PhysicsChunkTerrainSettings terrain = original.getPhysicsChunkTerrainSettings();
        PersistentSpaceDto state = new PersistentSpaceDto(UUID.randomUUID(),
            "test:settings-persistence",
            new Vector3f(0.0f, -9.81f, 0.0f),
            terrain.getTerrainMode(),
            terrain.getEntityChunkBoundaryMode(),
            terrain.isNativeVoxelTerrainEnabled(),
            terrain.getTerrainRadius(),
            terrain.getBodyTerrainRadius(),
            terrain.getTerrainTtlTicks(),
            terrain.getTerrainFriction(),
            terrain.getTerrainRestitution(),
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
        PhysicsSpaceSettings decoded = Objects.requireNonNull(
            PersistentSpaceDto.CODEC.decode(encoded, new ExtraInfo())).toSettings();
        assertTrue(decoded.getPhysicsChunkTerrainSettings().isNativeVoxelTerrainEnabled());
        assertEquals(0.85f, decoded.getPhysicsChunkTerrainSettings().getTerrainFriction(), 0.0001f);
        assertEquals(0.2f, decoded.getPhysicsChunkTerrainSettings().getTerrainRestitution(), 0.0001f);
        assertDetachedVisualCadence(decoded, 7, 9, 11);

        PhysicsSpaceSettings copied = state.copy().toSettings();
        assertTrue(copied.getPhysicsChunkTerrainSettings().isNativeVoxelTerrainEnabled());
        assertEquals(0.85f, copied.getPhysicsChunkTerrainSettings().getTerrainFriction(), 0.0001f);
        assertEquals(0.2f, copied.getPhysicsChunkTerrainSettings().getTerrainRestitution(), 0.0001f);
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
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_FRICTION,
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RESTITUTION,
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
