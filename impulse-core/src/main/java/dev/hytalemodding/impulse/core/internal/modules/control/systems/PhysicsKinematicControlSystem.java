package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class PhysicsKinematicControlSystem extends EntityTickingSystem<EntityStore> {

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    @Nonnull
    private final ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType;
    @Nonnull
    private final Query<EntityStore> query;
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);
    private static final Vector3f ZERO_VELOCITY = new Vector3f();
    private static final Quaternionf IDENTITY_ROTATION = new Quaternionf();
    // Anchor updates are copied into PhysicsStore rows; avoid rewriting unchanged targets.
    @Nonnull
    private static final Map<Store<EntityStore>, ControlMutationState> STATES_BY_STORE =
        Collections.synchronizedMap(new WeakHashMap<>());

    public PhysicsKinematicControlSystem() {
        this(PhysicsControlSessionComponent.getComponentType());
    }

    PhysicsKinematicControlSystem(
        @Nonnull ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        this.sessionType = Objects.requireNonNull(sessionType, "sessionType");
        this.query = Query.and(sessionType, TransformComponent.getComponentType());
    }

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
        if (!ControlLifecycle.isEnabled()) {
            return;
        }
        ControlLifecycle.registerStore(store);
        PhysicsControlSessionComponent session = chunk.getComponent(index, sessionType);
        TransformComponent transform = chunk.getComponent(index,
            TransformComponent.getComponentType());
        if (session == null || transform == null || !session.isActive()) {
            return;
        }

        UUID bodyUuid = session.getBodyUuid();
        UUID anchorBodyUuid = session.getAnchorBodyUuid();
        Ref<EntityStore> targetRef = session.getTargetRef();
        if (bodyUuid == null || anchorBodyUuid == null || (targetRef != null && !targetRef.isValid())) {
            stateFor(store).clear(anchorBodyUuid);
            PhysicsControlSessionCleanup.cleanup(store, session);
            commandBuffer.removeComponent(chunk.getReferenceTo(index), sessionType);
            return;
        }

        Scratch local = scratch.get();
        Vector3d eye = local.eye;
        eye.set(transform.getPosition());
        eye.y += eyeHeight(chunk, index, chunk.getReferenceTo(index), store);

        Vector3d direction = lookDirection(chunk, index, transform, local.direction);
        Vector3f viewOffset = session.getViewOffset();
        local.right.set(Vector3dUtil.RIGHT);
        local.up.set(Vector3dUtil.UP);
        local.rotation.identity();
        rotation(chunk, index, transform).getQuaternion(local.rotation);
        local.rotation.transform(local.right);
        local.rotation.transform(local.up);
        local.target.set(
            (float) (eye.x
                + direction.x * session.getGrabDistance()
                + local.right.x * viewOffset.x
                + local.up.x * viewOffset.y
                + direction.x * viewOffset.z),
            (float) (eye.y
                + direction.y * session.getGrabDistance()
                + local.right.y * viewOffset.x
                + local.up.y * viewOffset.y
                + direction.y * viewOffset.z),
            (float) (eye.z
                + direction.z * session.getGrabDistance()
                + local.right.z * viewOffset.x
                + local.up.z * viewOffset.y
                + direction.z * viewOffset.z)
        );

        Vector3f previousTarget = session.getPreviousTarget();
        Vector3f releaseVelocity = session.getReleaseVelocity();
        float safeDt = dt > 0.0f ? dt : 1.0f / 20.0f;
        releaseVelocity.set(local.target).sub(previousTarget).div(safeDt);
        previousTarget.set(local.target);

        ControlMutationState state = stateFor(store);
        ControlAnchorUpdate update = new ControlAnchorUpdate(bodyUuid,
            anchorBodyUuid,
            local.target,
            releaseVelocity);
        ControlAnchorUpdate readyUpdate = state.selectReadyUpdate(anchorBodyUuid, update);
        if (readyUpdate == null) {
            return;
        }

        PhysicsStoreControlTargets physicsStoreTargets =
            resolvePhysicsStoreTargets(store, bodyUuid, anchorBodyUuid);
        if (physicsStoreTargets != null) {
            physicsStoreTargets.apply(readyUpdate);
            state.trackSubmittedMutation(anchorBodyUuid, readyUpdate);
            return;
        }

        stateFor(store).clear(anchorBodyUuid);
        PhysicsControlSessionCleanup.cleanup(store, session);
        commandBuffer.removeComponent(chunk.getReferenceTo(index), sessionType);
    }

    @Nullable
    private static PhysicsStoreControlTargets resolvePhysicsStoreTargets(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID anchorBodyUuid) {
        PhysicsStore physicsStore =
            ((PhysicsStoreWorld) store.getExternalData().getWorld()).getPhysicsStore();
        Store<PhysicsStore> physics = physicsStore.getStore();
        PhysicsStoreThreading.requireWorldThread(physics,
            "resolve PhysicsStore kinematic control targets");
        PhysicsIdentityIndexResource identity = physics.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        Ref<PhysicsStore> bodyRef = bodyRef(physics, identity, bodyUuid);
        Ref<PhysicsStore> anchorBodyRef = bodyRef(physics, identity, anchorBodyUuid);
        if (bodyRef == null || anchorBodyRef == null) {
            return null;
        }
        return new PhysicsStoreControlTargets(
            physics,
            bodyRef,
            anchorBodyRef);
    }

    @Nullable
    private static Ref<PhysicsStore> bodyRef(@Nonnull Store<PhysicsStore> physics,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID bodyUuid) {
        Ref<PhysicsStore> ref = identity.getByUuid(bodyUuid);
        if (ref != null
            && ref.isValid()
            && physics.getComponent(ref, BodyComponent.getComponentType()) != null) {
            return ref;
        }
        return null;
    }

    private float eyeHeight(@Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store) {
        ModelComponent modelComponent = chunk.getComponent(index,
            ModelComponent.getComponentType());
        if (modelComponent == null) {
            return 1.6f;
        }

        Model model = modelComponent.getModel();
        if (model == null) {
            return 1.6f;
        }
        return model.getEyeHeight(ref, store);
    }

    @Nonnull
    private Vector3d lookDirection(@Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull TransformComponent transform,
        @Nonnull Vector3d out) {
        Rotation3f rotation = rotation(chunk, index, transform);
        Quaterniond quaternion = rotation.getQuaternion(new Quaterniond());
        out.set(Vector3dUtil.FORWARD);
        quaternion.transform(out);
        if (out.lengthSquared() == 0.0) {
            out.set(Vector3dUtil.FORWARD);
        } else {
            out.normalize();
        }
        return out;
    }

    @Nonnull
    private Rotation3f rotation(@Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = chunk.getComponent(index, HeadRotation.getComponentType());
        return headRotation != null ? headRotation.getRotation() : transform.getRotation();
    }

    @Nonnull
    static ControlMutationState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (STATES_BY_STORE) {
            return STATES_BY_STORE.computeIfAbsent(store, _ -> new ControlMutationState());
        }
    }

    public static void clearMutationState(@Nonnull Store<EntityStore> store,
        @Nullable UUID anchorBodyUuid) {
        if (anchorBodyUuid != null) {
            stateFor(store).clear(anchorBodyUuid);
        }
    }

    record ControlAnchorUpdate(@Nonnull UUID bodyUuid,
                               @Nonnull UUID anchorBodyUuid,
                               @Nonnull Vector3f target,
                               @Nonnull Vector3f releaseVelocity) {

        ControlAnchorUpdate {
            target = new Vector3f(target);
            releaseVelocity = new Vector3f(releaseVelocity);
        }
    }

    private record PhysicsStoreControlTargets(@Nonnull Store<PhysicsStore> store,
                                              @Nonnull Ref<PhysicsStore> bodyRef,
                                              @Nonnull Ref<PhysicsStore> anchorBodyRef) {

        private void apply(@Nonnull ControlAnchorUpdate update) {
            store.putComponent(anchorBodyRef,
                TargetComponent.getComponentType(),
                target(update.target(),
                    update.releaseVelocity(),
                    true,
                    true));
            store.putComponent(bodyRef,
                TargetComponent.getComponentType(),
                target(update.target(),
                    ZERO_VELOCITY,
                    false,
                    false));
        }

        @Nonnull
        private static TargetComponent target(@Nonnull Vector3f position,
            @Nonnull Vector3f linearVelocity,
            boolean transformEnabled,
            boolean velocityEnabled) {
            TargetComponent target = new TargetComponent();
            target.setActive(true);
            target.setPosition(position);
            target.setRotation(IDENTITY_ROTATION);
            target.setLinearVelocity(linearVelocity);
            target.setAngularVelocity(ZERO_VELOCITY);
            target.setTransformEnabled(transformEnabled);
            target.setVelocityEnabled(velocityEnabled);
            target.setActivate(true);
            return target;
        }
    }

    static final class ControlMutationState {

        @Nonnull
        private final Object2ObjectMap<UUID, ControlAnchorUpdate> submittedUpdates =
            new Object2ObjectOpenHashMap<>();

        @Nullable
        synchronized ControlAnchorUpdate selectReadyUpdate(@Nonnull UUID bodyUuid,
            @Nonnull ControlAnchorUpdate currentUpdate) {
            ControlAnchorUpdate submittedUpdate = submittedUpdates.get(bodyUuid);
            if (sameTarget(currentUpdate, submittedUpdate)) {
                return null;
            }
            return currentUpdate;
        }

        synchronized void trackSubmittedMutation(@Nonnull UUID bodyUuid,
            @Nonnull ControlAnchorUpdate submittedUpdate) {
            submittedUpdates.put(bodyUuid, submittedUpdate);
        }

        synchronized void clear(@Nullable UUID bodyUuid) {
            if (bodyUuid != null) {
                submittedUpdates.remove(bodyUuid);
            }
        }

        private static boolean sameTarget(@Nonnull ControlAnchorUpdate first,
            @Nullable ControlAnchorUpdate second) {
            return second != null
                && Float.compare(first.target().x, second.target().x) == 0
                && Float.compare(first.target().y, second.target().y) == 0
                && Float.compare(first.target().z, second.target().z) == 0;
        }
    }

    private static final class Scratch {

        private final Vector3d eye = new Vector3d();
        private final Vector3d direction = new Vector3d();
        private final Vector3d right = new Vector3d();
        private final Vector3d up = new Vector3d();
        private final Quaterniond rotation = new Quaterniond();
        private final Vector3f target = new Vector3f();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
