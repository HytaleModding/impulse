package dev.hytalemodding.impulse.examples.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockComponent;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockRuntime;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveFuseComponent;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public final class ExplosiveFuseTickSystem extends EntityTickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private static final ComponentType<EntityStore, ExplosiveBlockComponent> EXPLOSIVE_TYPE =
        ExplosiveBlockComponent.getComponentType();
    private static final ComponentType<EntityStore, ExplosiveFuseComponent> FUSE_TYPE =
        ExplosiveFuseComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final Query<EntityStore> QUERY =
        Query.and(EXPLOSIVE_TYPE, FUSE_TYPE, ATTACHMENT_TYPE, TRANSFORM_TYPE);

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        ExplosiveFuseComponent fuse = chunk.getComponent(index, FUSE_TYPE);
        long tick = currentTick(store);
        if (fuse == null) {
            return;
        }
        ExplosiveBlockComponent explosive = chunk.getComponent(index, EXPLOSIVE_TYPE);
        PhysicsBodyAttachmentComponent attachment = chunk.getComponent(index, ATTACHMENT_TYPE);
        TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
        SpaceId spaceId = attachment != null ? attachment.getSpaceId() : null;
        if (explosive == null || attachment == null || transform == null || spaceId == null) {
            return;
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsBodySnapshot snapshot = bodySnapshot(resource, attachment.getBodyKey());
        Vector3d currentCenter = explosionCenter(snapshot, transform);
        if (!fuse.isArmed()) {
            ExplosiveFuseComponent updated = fuse.clone();
            if (snapshot != null && updated.observeVerticalVelocity(snapshot.linearVelocityY(),
                tick,
                currentCenter)) {
                commandBuffer.putComponent(ref, FUSE_TYPE, updated);
            }
            return;
        }
        if (!fuse.isDue(tick)) {
            return;
        }

        World world = store.getExternalData().getWorld();
        Vector3d center = fuse.explosionCenterOr(currentCenter);
        ExplosiveBlockRuntime.scheduleExplosion(store,
            world,
            spaceId,
            center,
            explosive);
        commandBuffer.removeEntity(ref, com.hypixel.hytale.component.RemoveReason.REMOVE);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    private static long currentTick(@Nonnull Store<EntityStore> store) {
        return Math.max(0L, store.getExternalData().getWorld().getTick());
    }

    @Nonnull
    private static Vector3d explosionCenter(@javax.annotation.Nullable PhysicsBodySnapshot snapshot,
        @Nonnull TransformComponent transform) {
        if (snapshot != null) {
            return ExplosiveBlockRuntime.sourceExplosionCenter(new Vector3d(snapshot.positionX(),
                snapshot.positionY(),
                snapshot.positionZ()));
        }
        return ExplosiveBlockRuntime.sourceExplosionCenter(new Vector3d(transform.getPosition()));
    }

    @javax.annotation.Nullable
    private static PhysicsBodySnapshot bodySnapshot(@Nonnull PhysicsWorldResource resource,
        @Nonnull RigidBodyKey bodyKey) {
        if (resource.getBodyRegistrationView(bodyKey) != null) {
            try {
                return resource.getBodySnapshot(bodyKey);
            } catch (IllegalArgumentException ignored) {
                // Fall back to the last synced entity transform if the source body was destroyed.
            }
        }
        return null;
    }
}
