package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Creates backend bodies from authoritative PhysicsStore body rows.
 */
public final class BodyBindingSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, SpaceBindingSystem.class),
        new SystemDependency<>(Order.BEFORE, ColliderBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        Map<UUID, ColliderRow> collidersByBodyUuid = collectColliders(store, systemIndex);
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> bindBodies(store, runtime, identity, restore, collidersByBodyUuid, chunk);
        store.forEachChunk(systemIndex, collector);
    }

    @Nonnull
    private static Map<UUID, ColliderRow> collectColliders(@Nonnull Store<PhysicsStore> store,
        int systemIndex) {
        Map<UUID, ColliderRow> collidersByBodyUuid = new Object2ObjectOpenHashMap<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> {
                for (int index = 0; index < chunk.size(); index++) {
                    ColliderComponent collider = chunk.getComponent(index,
                        ColliderComponent.getComponentType());
                    if (collider == null) {
                        continue;
                    }
                    UUID colliderUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
                    if (!PhysicsStoreSystemSupport.isNil(colliderUuid)) {
                        collidersByBodyUuid.putIfAbsent(collider.getBodyUuid(),
                            new ColliderRow(colliderUuid, collider));
                    }
                }
            };
        store.forEachChunk(systemIndex, collector);
        return collidersByBodyUuid;
    }

    private static void bindBodies(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Map<UUID, ColliderRow> collidersByBodyUuid,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body == null) {
                continue;
            }
            UUID bodyUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(bodyUuid)
                || runtime.getBodyHandle(bodyUuid) != null) {
                continue;
            }
            ColliderRow collider = collidersByBodyUuid.get(bodyUuid);
            if (collider == null) {
                restore.recordSoftSkip("Body has no collider: " + bodyUuid);
                continue;
            }
            bindBody(store,
                runtime,
                identity,
                restore,
                chunk.getReferenceTo(index),
                bodyUuid,
                body,
                chunk.getComponent(index, DynamicsComponent.getComponentType()),
                chunk.getComponent(index, TargetComponent.getComponentType()),
                collider);
        }
    }

    private static void bindBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyComponent body,
        @Nullable DynamicsComponent dynamics,
        @Nullable TargetComponent target,
        @Nonnull ColliderRow colliderRow) {
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(body.getSpaceUuid());
        if (spaceHandle == null) {
            restore.recordSoftSkip("Body references unbound space: " + bodyUuid);
            return;
        }
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, body.getSpaceUuid());
        if (backendRuntime == null) {
            restore.recordSoftSkip("Body references missing backend runtime: " + bodyUuid);
            return;
        }
        ShapeComponent shape = componentByUuid(store,
            identity,
            colliderRow.collider().getShapeUuid(),
            ShapeComponent.getComponentType());
        MaterialComponent material = componentByUuid(store,
            identity,
            colliderRow.collider().getMaterialUuid(),
            MaterialComponent.getComponentType());
        CollisionFilterComponent filter = componentByUuid(store,
            identity,
            colliderRow.collider().getFilterUuid(),
            CollisionFilterComponent.getComponentType());
        if (shape == null || material == null || filter == null) {
            restore.recordSoftSkip("Body references incomplete collider rows: " + bodyUuid);
            return;
        }
        DynamicsComponent bodyDynamics = dynamics != null ? dynamics : new DynamicsComponent();
        TargetComponent initialTarget = target != null ? target : new TargetComponent();
        Vector3f position = initialTarget.isActive() ? initialTarget.getPosition() : new Vector3f();
        Quaternionf rotation = initialTarget.isActive()
            ? initialTarget.getRotation()
            : new Quaternionf();
        PhysicsBodyType bodyType = bodyDynamics.getBodyType();
        float mass = bodyType == PhysicsBodyType.DYNAMIC ? bodyDynamics.getMass() : 0.0f;
        long bodyId = backendRuntime.createBody(spaceHandle.value(),
            BackendRuntimeCodes.shapeTypeCode(shape.getShapeType()),
            shape.getHalfExtentX(),
            shape.getHalfExtentY(),
            shape.getHalfExtentZ(),
            shape.getRadius(),
            shape.getHalfHeight(),
            BackendRuntimeCodes.axisCode(shape.getAxis()),
            shape.getGroundY(),
            mass,
            BackendRuntimeCodes.bodyTypeCode(bodyType),
            position.x,
            position.y,
            position.z,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w);
        BackendBodyHandle bodyHandle = new BackendBodyHandle(bodyId);
        backendRuntime.setBodyDamping(spaceHandle.value(),
            bodyId,
            bodyDynamics.getLinearDamping(),
            bodyDynamics.getAngularDamping());
        backendRuntime.setBodyFriction(spaceHandle.value(), bodyId, material.getFriction());
        backendRuntime.setBodyRestitution(spaceHandle.value(), bodyId, material.getRestitution());
        backendRuntime.setBodyCollisionFilter(spaceHandle.value(),
            bodyId,
            filter.getCollisionGroup(),
            filter.getCollisionMask());
        backendRuntime.setBodySensor(spaceHandle.value(), bodyId, colliderRow.collider().isSensor());
        if (bodyDynamics.isContinuousCollisionEnabled()
            && backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
            backendRuntime.setBodyContinuousCollision(spaceHandle.value(), bodyId, true);
        }
        runtime.putBodyHandle(bodyUuid, spaceHandle, bodyHandle);
        identity.putBodyHandle(bodyHandle, bodyRef);
    }

    @Nullable
    private static PhysicsBackendRuntime runtimeForSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID spaceUuid) {
        var backendId = runtime.getSpaceBackendId(spaceUuid);
        return backendId != null ? runtime.getRuntime(backendId) : null;
    }

    @Nullable
    private static <C extends Component<PhysicsStore>> C componentByUuid(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID uuid,
        @Nonnull com.hypixel.hytale.component.ComponentType<PhysicsStore, C> type) {
        return PhysicsStoreSystemSupport.component(store,
            PhysicsStoreSystemSupport.refForUuid(identity, uuid),
            type);
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

    private record ColliderRow(@Nonnull UUID uuid, @Nonnull ColliderComponent collider) {
    }
}
