package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * @deprecated Use {@link PhysicsChunkTerrain}.
 */
@Deprecated(forRemoval = false)
public final class PhysicsWorldCollision {

    private PhysicsWorldCollision() {
    }

    public static void enableModule() {
        PhysicsChunkTerrain.enableModule();
    }

    public static void disableModule() {
        PhysicsChunkTerrain.disableModule();
    }

    public static boolean isModuleEnabled() {
        return PhysicsChunkTerrain.isModuleEnabled();
    }

    @Nonnull
    public static WorldCollisionBuildStats rebuildAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        return PhysicsChunkTerrain.rebuildAround(world,
            store,
            spaceId,
            center,
            radius)
            .toWorldCollisionStats();
    }

    @Nonnull
    public static WorldCollisionBuildStats refreshAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        return PhysicsChunkTerrain.refreshAround(world,
            store,
            spaceId,
            center,
            radius)
            .toWorldCollisionStats();
    }

    @Nonnull
    public static WorldCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        return PhysicsChunkTerrain.ensureAround(world,
            store,
            spaceId,
            centers,
            radius,
            tick)
            .toWorldCollisionStats();
    }

    public static int clearSpace(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        return PhysicsChunkTerrain.clearSpace(world, store, spaceId);
    }

    @Nonnull
    public static WorldCollisionStats stats(@Nonnull World world) {
        return PhysicsChunkTerrain.stats(world).toWorldCollisionStats();
    }
}
