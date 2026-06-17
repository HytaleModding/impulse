package dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionMode;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class WorldCollisionComponentTest {

    @Test
    void deprecatedWorldCollisionComponentAccessorsMutateTerrainState() {
        WorldCollisionComponent component = new WorldCollisionComponent(WorldCollisionMode.STREAMING,
            true,
            8,
            4,
            160,
            0.7f,
            0.1f);

        assertEquals(PhysicsChunkTerrainMode.STREAMING, component.getTerrainMode());
        assertEquals(WorldCollisionMode.STREAMING, component.getMode());

        component.setMode(WorldCollisionMode.NONE);

        assertEquals(PhysicsChunkTerrainMode.NONE, component.getTerrainMode());
        assertEquals(WorldCollisionMode.NONE, component.getMode());

        WorldCollisionComponent clone = component.clone();

        assertEquals(PhysicsChunkTerrainMode.NONE, clone.getTerrainMode());
        assertEquals(8, clone.getRadius());
        assertEquals(4, clone.getBodyRadius());
        assertEquals(160, clone.getTtlTicks());
        assertEquals(0.7f, clone.getTerrainFriction(), 0.0001f);
        assertEquals(0.1f, clone.getTerrainRestitution(), 0.0001f);
    }
}
