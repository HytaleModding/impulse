package dev.hytalemodding.impulse.examples.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockComponent;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockRuntime;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

public final class ExplosiveBlockLandingSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private static final ComponentType<EntityStore, ExplosiveBlockComponent> EXPLOSIVE_TYPE =
        ExplosiveBlockComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final Query<EntityStore> QUERY = Query.and(EXPLOSIVE_TYPE,
        ATTACHMENT_TYPE,
        TRANSFORM_TYPE);

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        TimeResource time = store.getResource(TimeResource.getResourceType());
        store.forEachChunk(systemIndex,
            (BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>>)
                (chunk, commandBuffer) -> settleChunk(chunk, commandBuffer, resource, time));
    }

    private static void settleChunk(@Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull TimeResource time) {
        World world = commandBuffer.getExternalData().getWorld();
        for (int index = 0; index < chunk.size(); index++) {
            settleEntity(chunk, index, commandBuffer, resource, time, world);
        }
    }

    private static void settleEntity(@Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull TimeResource time,
        @Nonnull World world) {
        ExplosiveBlockComponent explosive = chunk.getComponent(index, EXPLOSIVE_TYPE);
        PhysicsBodyAttachmentComponent attachment = chunk.getComponent(index, ATTACHMENT_TYPE);
        TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
        if (explosive == null || attachment == null || transform == null) {
            return;
        }

        Vector3f contactPoint = ExplosiveBlockRuntime.terrainContactPoint(world, transform.getPosition());
        if (contactPoint == null) {
            return;
        }
        SpaceId spaceId = attachment.getSpaceId();
        if (spaceId == null) {
            return;
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        ExplosiveBlockRuntime.settleAndMaybeChain(commandBuffer,
            world,
            time,
            resource,
            spaceId,
            attachment.getBodyKey(),
            explosive,
            contactPoint);
        commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }
}
