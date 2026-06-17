package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Public PhysicsChunk operations for terrain-backed collision.
 */
@SuppressWarnings("deprecation")
public final class PhysicsChunkTerrain {

    private PhysicsChunkTerrain() {
    }

    public static void enableModule() {
        PhysicsWorldCollision.enableModule();
    }

    public static void disableModule() {
        PhysicsWorldCollision.disableModule();
    }

    public static boolean isModuleEnabled() {
        return PhysicsWorldCollision.isModuleEnabled();
    }

    @Nonnull
    public static PhysicsChunkTerrainBuildStats rebuildAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        return PhysicsChunkTerrainBuildStats.fromWorldCollisionStats(
            PhysicsWorldCollision.rebuildAround(world,
                store,
                spaceId,
                center,
                radius));
    }

    @Nonnull
    public static PhysicsChunkTerrainBuildStats refreshAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        return PhysicsChunkTerrainBuildStats.fromWorldCollisionStats(
            PhysicsWorldCollision.refreshAround(world,
                store,
                spaceId,
                center,
                radius));
    }

    @Nonnull
    public static PhysicsChunkTerrainPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        return PhysicsChunkTerrainPrewarmStats.fromWorldCollisionStats(
            PhysicsWorldCollision.ensureAround(world,
                store,
                spaceId,
                Objects.requireNonNull(centers, "centers"),
                radius,
                tick));
    }

    public static int clearSpace(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        return PhysicsWorldCollision.clearSpace(world, store, spaceId);
    }

    @Nonnull
    public static PhysicsChunkTerrainStats stats(@Nonnull World world) {
        return PhysicsChunkTerrainStats.fromWorldCollisionStats(PhysicsWorldCollision.stats(world));
    }
}
