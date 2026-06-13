package dev.hytalemodding.impulse.core.internal.systems.body;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyIdentityComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyKinematicTargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAccess;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTargetRequest;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Emits copied PhysicsStore target requests from EntityStore kinematic target components.
 */
public final class PhysicsStoreKinematicTargetProducerSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private static final ComponentType<EntityStore, PhysicsBodyIdentityComponent> IDENTITY_TYPE =
        PhysicsBodyIdentityComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyKinematicTargetComponent> TARGET_TYPE =
        PhysicsBodyKinematicTargetComponent.getComponentType();
    private static final Query<EntityStore> QUERY = Query.and(IDENTITY_TYPE, TARGET_TYPE);
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup())
    );
    @Nonnull
    private final Map<Store<EntityStore>, RigidBodyKinematicTargetState> statesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsStore physicsStore = PhysicsStoreAccess.require(store.getExternalData().getWorld());
        Store<PhysicsStore> physics = physicsStore.getStore();
        PhysicsIdentityIndexResource identity = physics.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        PhysicsRequestQueueResource queue = physics.getResource(
            PhysicsRequestQueueResource.getResourceType());
        RigidBodyKinematicTargetState targetState = stateFor(store);
        targetState.beginTick();
        try {
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> collector =
                (chunk, _) -> produceChunk(physics, identity, queue, targetState, chunk);
            store.forEachChunk(systemIndex, collector);
        } finally {
            targetState.finishTick();
        }
    }

    private static void produceChunk(@Nonnull Store<PhysicsStore> physics,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRequestQueueResource queue,
        @Nonnull RigidBodyKinematicTargetState targetState,
        @Nonnull ArchetypeChunk<EntityStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            PhysicsBodyIdentityComponent bodyIdentity = chunk.getComponent(index, IDENTITY_TYPE);
            PhysicsBodyKinematicTargetComponent target = chunk.getComponent(index, TARGET_TYPE);
            if (bodyIdentity == null || target == null) {
                continue;
            }
            RigidBodyKey bodyKey = bodyIdentity.getBodyKey();
            UUID bodyUuid = bodyKey.value();
            if (!hasPhysicsStoreBody(physics, identity, bodyUuid)) {
                targetState.clear(bodyKey);
                continue;
            }
            if (targetState.shouldSubmit(bodyKey, target)) {
                queue.enqueue(request(bodyUuid, target));
            }
        }
    }

    private static boolean hasPhysicsStoreBody(@Nonnull Store<PhysicsStore> physics,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID bodyUuid) {
        Ref<PhysicsStore> ref = identity.getByUuid(bodyUuid);
        return ref != null
            && ref.isValid()
            && physics.getComponent(ref, BodyComponent.getComponentType()) != null;
    }

    @Nonnull
    private static BodyTargetRequest request(@Nonnull UUID bodyUuid,
        @Nonnull PhysicsBodyKinematicTargetComponent target) {
        Vector3f position = target.getPosition();
        Quaternionf rotation = target.getRotation();
        Vector3f linearVelocity = target.getLinearVelocity();
        Vector3f angularVelocity = target.getAngularVelocity();
        return BodyTargetRequest.of(bodyUuid,
            position,
            rotation,
            linearVelocity,
            angularVelocity,
            target.isTransformEnabled(),
            target.isVelocityEnabled(),
            target.isActivate());
    }

    @Nonnull
    private RigidBodyKinematicTargetState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (statesByStore) {
            RigidBodyKinematicTargetState state = statesByStore.get(store);
            if (state == null) {
                state = new RigidBodyKinematicTargetState();
                statesByStore.put(store, state);
            }
            return state;
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }
}
