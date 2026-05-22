package dev.hytalemodding.impulse.core.internal.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import java.util.UUID;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WorldVoxelCollisionCacheTest {

    @Test
    void bodyTargetCacheRefreshesActiveBodiesEveryFourTicks() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();
        SpaceId spaceId = new SpaceId(1001);
        PhysicsBodyId bodyId = bodyId(1);
        WorldCollisionStreamingBounds bounds = boundsAt(10.0f, 65.0f, 10.0f);

        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 1L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 2L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 3L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 4L, 100, snapshot)
            .refresh());
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 5L, 100, snapshot)
            .refresh());

        assertEquals(1, snapshot.getBodyTargetFirstSeen());
        assertEquals(4, snapshot.getBodyTargetCacheHits());
        assertEquals(3, snapshot.getBodyTargetActiveStableSkips());
        assertEquals(1, snapshot.getBodyTargetActiveRefreshes());
    }

    @Test
    void bodyTargetCacheRefreshesSleepingBodiesOnTtlBoundedInterval() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();
        SpaceId spaceId = new SpaceId(1002);
        PhysicsBodyId bodyId = bodyId(2);
        WorldCollisionStreamingBounds bounds = boundsAt(10.0f, 65.0f, 10.0f);

        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, true, 1L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, true, 2L, 100, snapshot)
            .refresh());
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, true, 21L, 100, snapshot)
            .refresh());

        assertEquals(1, snapshot.getBodyTargetFirstSeen());
        assertEquals(2, snapshot.getBodyTargetCacheHits());
        assertEquals(1, snapshot.getBodyTargetSleepingStableSkips());
        assertEquals(1, snapshot.getBodyTargetSleepingRefreshes());
    }

    @Test
    void bodyTargetCacheRefreshesImmediatelyWhenBoundsChange() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();
        SpaceId spaceId = new SpaceId(1003);
        PhysicsBodyId bodyId = bodyId(3);

        assertTrue(cache.shouldRefreshBodyTarget(spaceId,
            bodyId,
            boundsAt(10.0f, 65.0f, 10.0f),
            false,
            1L,
            100,
            snapshot).refresh());
        assertTrue(cache.shouldRefreshBodyTarget(spaceId,
            bodyId,
            boundsAt(40.0f, 65.0f, 10.0f),
            false,
            2L,
            100,
            snapshot).refresh());

        assertEquals(1, snapshot.getBodyTargetFirstSeen());
        assertEquals(1, snapshot.getBodyTargetCacheHits());
        assertEquals(1, snapshot.getBodyTargetBoundsChanged());
    }

    @Test
    void bodyTargetCachePrunesBodiesThatDisappearPastDoubleTtl() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();
        SpaceId spaceId = new SpaceId(1004);
        PhysicsBodyId bodyId = bodyId(4);
        WorldCollisionStreamingBounds bounds = boundsAt(10.0f, 65.0f, 10.0f);

        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 1L, 100, snapshot)
            .refresh());
        assertEquals(1, cache.pruneBodyStreamingTargets(spaceId, 202L, 100, snapshot));
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 203L, 100, snapshot)
            .refresh());

        assertEquals(2, snapshot.getBodyTargetFirstSeen());
        assertEquals(1, snapshot.getBodyTargetsPruned());
    }

    private static PhysicsBodyId bodyId(long leastSignificantBits) {
        return PhysicsBodyId.of(new UUID(0L, leastSignificantBits));
    }

    private static WorldCollisionStreamingBounds boundsAt(float x, float y, float z) {
        return WorldCollisionStreamingBounds.from(new Vector3f(x, y, z), 4);
    }
}
