package dev.hytalemodding.impulse.core.internal.voxel;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

/**
 * Internal bridge for systems that own streamed world-collision cache behavior.
 */
public final class WorldCollisionCacheAccess {

    private static final WorldCollisionCacheAccess TOKEN = new WorldCollisionCacheAccess();

    private WorldCollisionCacheAccess() {
    }

    @Nonnull
    public static WorldVoxelCollisionCache get(@Nonnull PhysicsWorldResource resource) {
        return (WorldVoxelCollisionCache) resource.internalWorldCollisionState(TOKEN);
    }
}
