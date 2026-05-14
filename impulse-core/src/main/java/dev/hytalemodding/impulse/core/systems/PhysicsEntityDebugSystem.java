package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class PhysicsEntityDebugSystem extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, PhysicsBodyComponent> PHYSICS_BODY_TYPE =
        PhysicsBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(PHYSICS_BODY_TYPE, TRANSFORM_TYPE);

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        if (!resource.isDebugEnabled()) {
            return;
        }

        PhysicsBodyComponent physicsBody = chunk.getComponent(index, PHYSICS_BODY_TYPE);
        TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
        if (physicsBody == null || transform == null) {
            return;
        }

        PhysicsBody body = physicsBody.getBody();
        World world = store.getExternalData().getWorld();
        float time = PhysicsDebugRenderer.lifetime(dt);
        Vector3d center = PhysicsDebugRenderer.centerFromSyncedTransform(body, transform.getPosition());
        Quaterniond rotation = transform.getRotation().getQuaternion(new Quaterniond());

        if (resource.isDebugShapesEnabled()) {
            PhysicsDebugRenderer.renderBodyShape(world, body, center, rotation, time);
        }
        if (resource.isDebugMotionEnabled()) {
            PhysicsDebugRenderer.renderBodyMotion(world, body, center, time);
        }
    }
}
