package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings.ExecutionMode;
import dev.hytalemodding.impulse.core.plugin.voxel.WorldCollisionMode;
import org.junit.jupiter.api.Test;

class PhysicsSpaceSettingsTest {

    @Test
    void defaultsExposeHeadlessVisualSyncRadii() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        assertEquals(PhysicsSpaceSettings.DEFAULT_VISUAL_FULL_SYNC_RADIUS,
            settings.getVisualFullSyncRadius());
        assertEquals(PhysicsSpaceSettings.DEFAULT_VISUAL_MAX_SYNC_RADIUS,
            settings.getVisualMaxSyncRadius());
    }

    @Test
    void rejectsVisualFullSyncRadiusAboveMaxRadius() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> settings.setVisualFullSyncRadius(settings.getVisualMaxSyncRadius() + 1));

        assertEquals("Visual full sync radius cannot exceed visual max sync radius",
            exception.getMessage());
    }

    @Test
    void rejectsVisualMaxSyncRadiusBelowFullRadius() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> settings.setVisualMaxSyncRadius(settings.getVisualFullSyncRadius() - 1));

        assertEquals("Visual max sync radius cannot be lower than visual full sync radius",
            exception.getMessage());
    }

    @Test
    void acceptsUpdatedVisualSyncRadiiWhenOrderingStaysValid() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        settings.setVisualMaxSyncRadius(192);
        settings.setVisualFullSyncRadius(96);

        assertEquals(192, settings.getVisualMaxSyncRadius());
        assertEquals(96, settings.getVisualFullSyncRadius());
    }

    @Test
    void rejectsNonPositiveWorldCollisionValues() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        assertEquals("World collision radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_WORLD_COLLISION_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setWorldCollisionRadius(0)).getMessage());
        assertEquals("World collision body radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_WORLD_COLLISION_BODY_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setWorldCollisionBodyRadius(0)).getMessage());
        assertEquals("World collision TTL must be between 1 and "
                + PhysicsSpaceSettings.MAX_WORLD_COLLISION_TTL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setWorldCollisionTtlTicks(0)).getMessage());
        assertEquals("Visual full sync radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_VISUAL_FULL_SYNC_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setVisualFullSyncRadius(0)).getMessage());
        assertEquals("Visual max sync radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_VISUAL_MAX_SYNC_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setVisualMaxSyncRadius(0)).getMessage());
    }

    @Test
    void defaultsFactoryReturnsFreshDefaultSettings() {
        PhysicsSpaceSettings first = PhysicsSpaceSettings.defaults();
        PhysicsSpaceSettings second = PhysicsSpaceSettings.defaults();

        assertNotSame(first, second);
        assertEquals(WorldCollisionMode.NONE, first.getWorldCollisionMode());
        assertSame(PhysicsSpaceSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            first.getEntityChunkBoundaryMode());
    }

    @Test
    void streamingWorldCollisionFactoryEnablesStreamingMode() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.streamingWorldCollision();

        assertEquals(WorldCollisionMode.STREAMING, settings.getWorldCollisionMode());
        assertEquals(PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_RADIUS,
            settings.getWorldCollisionRadius());
    }

    @Test
    void workerExecutionModeRemainsUnavailable() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        assertEquals(ExecutionMode.INLINE, PhysicsSpaceSettings.DEFAULT_EXECUTION_MODE);
        assertEquals(ExecutionMode.INLINE, settings.getExecutionMode());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> settings.setExecutionMode(ExecutionMode.WORKER));

        assertEquals("Worker physics execution is not available yet; use inline execution",
            exception.getMessage());
        assertEquals(ExecutionMode.INLINE, settings.getExecutionMode());
    }

    @Test
    void copyConstructorCopiesValuesWithoutSharingOriginalInstance() {
        PhysicsSpaceSettings original = new PhysicsSpaceSettings();
        original.setWorldCollisionMode(WorldCollisionMode.STREAMING);
        original.setWorldCollisionRadius(12);
        original.setWorldCollisionBodyRadius(6);
        original.setWorldCollisionTtlTicks(180);
        original.setVisualMaxSyncRadius(160);
        original.setVisualFullSyncRadius(80);

        PhysicsSpaceSettings copy = new PhysicsSpaceSettings(original);
        original.setWorldCollisionRadius(20);

        assertEquals(WorldCollisionMode.STREAMING, copy.getWorldCollisionMode());
        assertEquals(12, copy.getWorldCollisionRadius());
        assertEquals(6, copy.getWorldCollisionBodyRadius());
        assertEquals(180, copy.getWorldCollisionTtlTicks());
        assertEquals(160, copy.getVisualMaxSyncRadius());
        assertEquals(80, copy.getVisualFullSyncRadius());
    }
}
