package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource.SpaceWorldCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * Publishes copied world-collision settings for PhysicsStore space rows.
 */
public final class WorldCollisionIndexSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, IdentityIndexSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        Map<UUID, SpaceWorldCollisionSettings> settingsBySpaceUuid =
            new Object2ObjectOpenHashMap<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectChunk(settingsBySpaceUuid, chunk);
        store.forEachChunk(systemIndex, collector);
        store.getResource(PhysicsWorldCollisionIndexResource.getResourceType())
            .replaceAll(settingsBySpaceUuid);
    }

    private static void collectChunk(
        @Nonnull Map<UUID, SpaceWorldCollisionSettings> settingsBySpaceUuid,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            SpaceComponent space = chunk.getComponent(index, SpaceComponent.getComponentType());
            if (space == null) {
                continue;
            }
            UUID spaceUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(spaceUuid)) {
                continue;
            }
            WorldCollisionComponent worldCollision = chunk.getComponent(index,
                WorldCollisionComponent.getComponentType());
            WorldCollisionComponent settings = worldCollision != null
                ? worldCollision
                : new WorldCollisionComponent();
            settingsBySpaceUuid.put(spaceUuid, new SpaceWorldCollisionSettings(spaceUuid,
                settings.getMode(),
                settings.getEntityChunkBoundaryMode(),
                settings.isNativeVoxelTerrainEnabled(),
                settings.getRadius(),
                settings.getBodyRadius(),
                settings.getTtlTicks(),
                settings.getTerrainFriction(),
                settings.getTerrainRestitution()));
        }
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.UUID_QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
