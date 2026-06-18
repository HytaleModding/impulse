package dev.hytalemodding.impulse.core.internal.systems;

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
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkSettingsIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkSettingsIndexResource.PhysicsChunkSpaceSettings;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * Publishes copied PhysicsChunk terrain settings for PhysicsStore space entities.
 */
public final class PhysicsChunkSettingsIndexSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, IdentityIndexSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        Map<UUID, PhysicsChunkSpaceSettings> settingsBySpaceUuid =
            new Object2ObjectOpenHashMap<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectChunk(settingsBySpaceUuid, chunk);
        store.forEachChunk(systemIndex, collector);
        store.getResource(PhysicsChunkSettingsIndexResource.getResourceType())
            .replaceAll(settingsBySpaceUuid);
    }

    private static void collectChunk(
        @Nonnull Map<UUID, PhysicsChunkSpaceSettings> settingsBySpaceUuid,
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
            ChunkCollisionSettingsComponent chunkCollision = chunk.getComponent(index,
                ChunkCollisionSettingsComponent.getComponentType());
            ChunkCollisionSettingsComponent settings = chunkCollision != null
                ? chunkCollision
                : new ChunkCollisionSettingsComponent();
            MaterialComponent material = chunk.getComponent(index,
                MaterialComponent.getComponentType());
            CollisionFilterComponent filter = chunk.getComponent(index,
                CollisionFilterComponent.getComponentType());
            settingsBySpaceUuid.put(spaceUuid, new PhysicsChunkSpaceSettings(spaceUuid,
                settings.getMode(),
                settings.getEntityChunkBoundaryMode(),
                settings.isNativeVoxelCollisionEnabled(),
                settings.getRadius(),
                settings.getBodyRadius(),
                settings.getTtlTicks(),
                material != null
                    ? material.getFriction()
                    : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_FRICTION,
                material != null
                    ? material.getRestitution()
                    : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RESTITUTION,
                filter != null
                    ? filter.getCollisionGroup()
                    : PhysicsCollisionFilters.TERRAIN,
                filter != null
                    ? filter.getCollisionMask()
                    : PhysicsCollisionFilters.ALL));
        }
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.uuidQuery();
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
