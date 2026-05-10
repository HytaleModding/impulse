package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Synchronizes physics bodies with Hytale transforms each tick.
 */
public class PhysicsSyncSystem extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, PhysicsBodyComponent> PHYSICS_BODY_TYPE = PhysicsBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE = TransformComponent.getComponentType();

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
        PhysicsBodyComponent physicsBody = chunk.getComponent(index, PHYSICS_BODY_TYPE);
        TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);

        if (physicsBody == null || transform == null) {
            return;
        }

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        SpaceId spaceId = physicsBody.getSpaceId();
        if (spaceId != null && resource.getSpace(spaceId) == null) {
            return;
        }

        PhysicsBody body = physicsBody.getBody();
        if (body.isStatic()) {
            return;
        }

        Vector3f pos = body.getPosition();
        // NOTE: use quaternions for future manipulations
        Quaternionf rot = body.getRotation();
        float offsetY = body.getCenterOfMassOffsetY();

        transform.setPosition(new Vector3d(pos.x, pos.y - offsetY, pos.z));
        transform.setRotation(toRotation3f(rot));
    }

    private static Rotation3f toRotation3f(Quaternionf q) {
        Vector3f euler = q.getEulerAnglesYXZ(new Vector3f());
        return new Rotation3f(euler.x(), euler.y(), euler.z());
    }
}
