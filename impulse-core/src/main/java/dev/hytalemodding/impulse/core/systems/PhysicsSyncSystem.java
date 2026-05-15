package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Synchronizes physics bodies with Hytale transforms each tick.
 *
 * <p>Runs after the persistence restore group so that newly bootstrapped spaces,
 * hydrated bodies, and hydrated joints are all settled before this system reads
 * body transforms.</p>
 */
public class PhysicsSyncSystem extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, PhysicsBodyComponent> PHYSICS_BODY_TYPE = PhysicsBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE = TransformComponent.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(PHYSICS_BODY_TYPE, TRANSFORM_TYPE);

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemGroupDependency<>(Order.AFTER, PhysicsSystemGroups.PERSISTENCE_RESTORE_GROUP)
    );

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    /**
     * Hytale may run entity ticks in parallel. Each worker needs independent temporary objects
     * because the backend out-parameter getters write into caller-owned vectors.
     */
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return useParallel(archetypeChunkSize, taskCount);
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

        Scratch local = scratch.get();
        PhysicsWorldResource resource = local.getResource(store);
        SpaceId spaceId = physicsBody.getSpaceId();
        if (spaceId != null && resource.getSpace(spaceId) == null) {
            return;
        }

        PhysicsBody body = physicsBody.getBody();
        if (body.isStatic()) {
            return;
        }

        body.getPosition(local.position);
        body.getRotation(local.rotation);
        float offsetY = body.getCenterOfMassOffsetY();

        transform.getPosition().set(local.position.x, local.position.y - offsetY, local.position.z);
        local.rotation.getEulerAnglesYXZ(local.euler);
        transform.getRotation().set(local.euler.x, local.euler.y, local.euler.z);
    }

    private static final class Scratch {

        private final Vector3f position = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f euler = new Vector3f();

        @Nullable
        private Store<EntityStore> cachedStore;
        @Nullable
        private PhysicsWorldResource cachedResource;

        @Nonnull
        private PhysicsWorldResource getResource(@Nonnull Store<EntityStore> store) {
            if (cachedStore != store || cachedResource == null) {
                cachedStore = store;
                cachedResource = store.getResource(PhysicsWorldResource.getResourceType());
            }
            return cachedResource;
        }
    }
}
