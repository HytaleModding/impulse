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
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    // Anchor updates are copied into PhysicsStore entities; avoid rewriting unchanged targets.
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

        Ref<PhysicsStore> bodyRef = session.getBodyRef();
        Ref<PhysicsStore> anchorBodyRef = session.getAnchorBodyRef();
        Ref<EntityStore> targetRef = session.getTargetRef();
        if (bodyRef == null
            || anchorBodyRef == null
            || !bodyRef.isValid()
            || !anchorBodyRef.isValid()
            || (targetRef != null && !targetRef.isValid())) {
            stateFor(store).clear(anchorBodyRef);
            PhysicsControlSessionCleanup.cleanup(store, session);
            commandBuffer.removeComponent(chunk.getReferenceTo(index), sessionType);
            return;
        }

        Scratch local = scratch.get();
        Vector3d eye = local.eye;
        eye.set(transform.getPosition());
        eye.y += eyeHeight(chunk, index, chunk.getReferenceTo(index), store);

        Rotation3f viewRotation = rotation(chunk, index, transform);
        Vector3d direction = lookDirection(viewRotation, local.direction);
        Vector3f viewOffset = session.getViewOffset();
        viewRotation.transform(Vector3dUtil.RIGHT, local.right);
        viewRotation.transform(Vector3dUtil.UP, local.up);
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
        ControlAnchorUpdate update = new ControlAnchorUpdate(bodyRef,
            anchorBodyRef,
            local.target,
            releaseVelocity);
        ControlAnchorUpdate readyUpdate = state.selectReadyUpdate(anchorBodyRef, update);
        if (readyUpdate == null) {
            return;
        }

        PhysicsStoreControlTargets physicsStoreTargets =
            resolvePhysicsStoreTargets(store, bodyRef, anchorBodyRef);
        if (physicsStoreTargets != null) {
            physicsStoreTargets.apply(readyUpdate);
            state.trackSubmittedMutation(anchorBodyRef, readyUpdate);
            return;
        }

        stateFor(store).clear(anchorBodyRef);
        PhysicsControlSessionCleanup.cleanup(store, session);
        commandBuffer.removeComponent(chunk.getReferenceTo(index), sessionType);
    }

    @Nullable
    private static PhysicsStoreControlTargets resolvePhysicsStoreTargets(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<PhysicsStore> anchorBodyRef) {
        PhysicsStore physicsStore =
            ((PhysicsStoreWorld) store.getExternalData().getWorld()).getPhysicsStore();
        Store<PhysicsStore> physics = physicsStore.getStore();
        PhysicsThreading.requireWorldThread(physics,
            "resolve PhysicsStore kinematic control targets");
        if (!validBodyRef(physics, bodyRef) || !validBodyRef(physics, anchorBodyRef)) {
            return null;
        }
        return new PhysicsStoreControlTargets(
            physics,
            bodyRef,
            anchorBodyRef);
    }

    private static boolean validBodyRef(@Nonnull Store<PhysicsStore> physics,
        @Nonnull Ref<PhysicsStore> ref) {
        return ref.getStore() == physics
            && ref.isValid()
            && physics.getComponent(ref, BodyComponent.getComponentType()) != null;
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
    private Vector3d lookDirection(@Nonnull Rotation3f rotation,
        @Nonnull Vector3d out) {
        rotation.transform(Vector3dUtil.FORWARD, out);
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
        @Nullable Ref<PhysicsStore> anchorBodyRef) {
        if (anchorBodyRef != null) {
            stateFor(store).clear(anchorBodyRef);
        }
    }

    record ControlAnchorUpdate(@Nonnull Ref<PhysicsStore> bodyRef,
                               @Nonnull Ref<PhysicsStore> anchorBodyRef,
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
        private final Int2ObjectMap<ControlAnchorUpdate> submittedUpdates =
            new Int2ObjectOpenHashMap<>();

        @Nullable
        synchronized ControlAnchorUpdate selectReadyUpdate(@Nonnull Ref<PhysicsStore> anchorBodyRef,
            @Nonnull ControlAnchorUpdate currentUpdate) {
            ControlAnchorUpdate submittedUpdate = submittedUpdates.get(anchorBodyRef.getIndex());
            if (sameTarget(currentUpdate, submittedUpdate)) {
                return null;
            }
            return currentUpdate;
        }

        synchronized void trackSubmittedMutation(@Nonnull Ref<PhysicsStore> anchorBodyRef,
            @Nonnull ControlAnchorUpdate submittedUpdate) {
            submittedUpdates.put(anchorBodyRef.getIndex(), submittedUpdate);
        }

        synchronized void clear(@Nullable Ref<PhysicsStore> anchorBodyRef) {
            if (anchorBodyRef != null) {
                submittedUpdates.remove(anchorBodyRef.getIndex());
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
