package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionDefaults;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tracks space-level components that generated PhysicsChunk body rows inherit at runtime.
 */
public final class PhysicsChunkComponentSyncResource implements Resource<PhysicsStore> {

    @Nullable
    private static ResourceType<PhysicsStore, PhysicsChunkComponentSyncResource> resourceType;
    @Nonnull
    private final Map<UUID, ChunkCollisionSurfaceComponents> surfacesBySpaceUuid =
        new Object2ObjectOpenHashMap<>();

    public PhysicsChunkComponentSyncResource() {
    }

    @Nonnull
    public synchronized Set<UUID> replaceAll(
        @Nonnull Map<UUID, ChunkCollisionSurfaceComponents> surfaces) {
        Set<UUID> changed = new ObjectOpenHashSet<>();
        for (Map.Entry<UUID, ChunkCollisionSurfaceComponents> entry : surfaces.entrySet()) {
            ChunkCollisionSurfaceComponents previous = surfacesBySpaceUuid.get(entry.getKey());
            if (!entry.getValue().equals(previous)) {
                changed.add(entry.getKey());
            }
        }
        surfacesBySpaceUuid.clear();
        surfacesBySpaceUuid.putAll(surfaces);
        return changed;
    }

    public synchronized void clear() {
        surfacesBySpaceUuid.clear();
    }

    @Nonnull
    @Override
    public synchronized PhysicsChunkComponentSyncResource clone() {
        PhysicsChunkComponentSyncResource copy = new PhysicsChunkComponentSyncResource();
        copy.surfacesBySpaceUuid.putAll(surfacesBySpaceUuid);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsChunkComponentSyncResource> getResourceType() {
        return resourceType;
    }

    public static void setResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsChunkComponentSyncResource> type) {
        resourceType = type;
    }

    public static void clearResourceType() {
        resourceType = null;
    }

    public record ChunkCollisionSurfaceComponents(float friction,
                                                  float restitution,
                                                  int collisionGroup,
                                                  int collisionMask) {

        @Nonnull
        public static ChunkCollisionSurfaceComponents of(@Nullable MaterialComponent material,
            @Nullable CollisionFilterComponent filter) {
            return new ChunkCollisionSurfaceComponents(
                material != null ? material.getFriction() : PhysicsChunkCollisionDefaults.FRICTION,
                material != null
                    ? material.getRestitution()
                    : PhysicsChunkCollisionDefaults.RESTITUTION,
                filter != null
                    ? filter.getCollisionGroup()
                    : PhysicsChunkCollisionDefaults.COLLISION_GROUP,
                filter != null
                    ? filter.getCollisionMask()
                    : PhysicsChunkCollisionDefaults.COLLISION_MASK);
        }

        @Nonnull
        public MaterialComponent material() {
            return new MaterialComponent(friction, restitution);
        }

        @Nonnull
        public CollisionFilterComponent filter() {
            return new CollisionFilterComponent(collisionGroup, collisionMask);
        }
    }
}
