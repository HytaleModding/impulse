package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.voxel.WorldCollisionMode;
import dev.hytalemodding.impulse.core.voxel.WorldVoxelCollisionCache;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Streams world voxel collision around online players and dynamic physics bodies
 * once per entity-store tick.
 *
 * <p>Players stream at the configured world collision radius.
 * Dynamic physics bodies stream at a smaller radius so they do not
 * aggressively pull collision into unpopulated areas, but still
 * have terrain to interact with after rolling away from players.</p>
 */
public class PhysicsWorldCollisionStreamingSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    /**
     * Radius (in blocks) used for streaming around dynamic physics bodies.
     * Smaller than the player radius because bodies should not pull collision
     * as far as players do, but they still need terrain to land on.
     */
    public static final int DEFAULT_BODY_STREAMING_RADIUS = 4;

    private static final ComponentType<EntityStore, Player> PLAYER_TYPE = Player.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyComponent> PHYSICS_BODY_TYPE =
        PhysicsBodyComponent.getComponentType();
    private static final Query<EntityStore> QUERY = Query.and(PLAYER_TYPE, TRANSFORM_TYPE);

    private long tick;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        playerPositionCount = 0;
        List<Vector3d> streamingPositions = new ArrayList<>();
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> collector =
            (chunk, commandBuffer) -> collectPlayerPositions(chunk, streamingPositions);
        store.forEachChunk(systemIndex, collector);

        World world = store.getExternalData().getWorld();
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        collectDynamicBodyPositions(resource, streamingPositions);

        long currentTick = ++tick;
        WorldVoxelCollisionCache cache = resource.getWorldVoxelCollisionCache();
        for (PhysicsSpace space : resource.iterateSpaces(world.getName())) {
            PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
            if (settings.getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
                continue;
            }

            int playerRadius = settings.getWorldCollisionRadius();
            int bodyRadius = settings.getWorldCollisionBodyRadius();
            int index = 0;
            for (Vector3d position : streamingPositions) {
                /*
                 * Player positions come first, then body positions.
                 * Use the smaller body radius for body positions.
                 */
                int radius = index < playerPositionCount ? playerRadius : bodyRadius;
                cache.ensureAround(world, space, position, radius, currentTick);
                index++;
            }
            cache.pruneUnloaded(world, space.getId(), space);
            cache.pruneUnused(space.getId(), space, currentTick, settings.getWorldCollisionTtlTicks());
        }
    }

    /**
     * Number of player positions collected in the current tick.
     * Positions at indices >= this count are dynamic body positions.
     */
    private int playerPositionCount;

    private void collectPlayerPositions(@Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull List<Vector3d> positions) {
        for (int index = 0; index < chunk.size(); index++) {
            TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
            if (transform != null) {
                positions.add(new Vector3d(transform.getPosition()));
            }
        }
        playerPositionCount = positions.size();
    }

    /**
     * Collects positions of dynamic physics bodies that belong to streaming spaces.
     * Uses the body's physics-space position (not the ECS transform) because the
     * physics position is more up-to-date for bodies that have moved since the
     * last sync.
     */
    private void collectDynamicBodyPositions(@Nonnull PhysicsWorldResource resource,
        @Nonnull List<Vector3d> positions) {
        for (PhysicsSpace space : resource.iterateSpaces()) {
            PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
            if (settings.getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
                continue;
            }
            for (PhysicsBody body : space.getBodies()) {
                if (!body.isDynamic()) {
                    continue;
                }
                if (body.isSleeping()) {
                    continue;
                }
                Vector3f pos = body.getPosition();
                positions.add(new Vector3d(pos.x, pos.y, pos.z));
            }
        }
    }
}
