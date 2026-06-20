package dev.hytalemodding.impulse.core.plugin.physics;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Factories for PhysicsStore body entity holders.
 */
public final class PhysicsBodyEntities {

    private PhysicsBodyEntities() {
    }

    @Nonnull
    public static Holder<PhysicsStore> dynamicBodyHolder(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        return bodyHolder(spaceRef,
            bodyUuid,
            bodyCenter,
            shape,
            PhysicsBodyType.DYNAMIC,
            mass,
            settings,
            linearVelocity);
    }

    @Nonnull
    public static Holder<PhysicsStore> bodyHolder(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        Store<PhysicsStore> store = requireSpaceStore(spaceRef);
        return bodyHolderWithSpaceUuid(store,
            PhysicsEntityRefs.entityUuid(spaceRef),
            bodyUuid,
            bodyCenter,
            shape,
            bodyType,
            mass,
            settings,
            linearVelocity,
            spaceRef);
    }

    @Nonnull
    private static Holder<PhysicsStore> bodyHolderWithSpaceUuid(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(bodyCenter, "bodyCenter");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(settings, "settings");

        BodyComponent body = new BodyComponent(spaceUuid);
        body.setSpaceRef(spaceRef);
        return PhysicsEntities.bodyHolder(store,
            bodyUuid,
            body,
            new DynamicsComponent(bodyType,
                mass,
                settings.hasLinearDamping() ? settings.linearDamping() : 0.0f,
                settings.hasAngularDamping() ? settings.angularDamping() : 0.0f,
                false),
            initialTarget(bodyCenter, linearVelocity),
            new ColliderComponent(new Vector3f(),
                new Quaternionf(),
                settings.hasSensor() && settings.sensor()),
            new ShapeComponent(shape.type(),
                shape.halfExtentX(),
                shape.halfExtentY(),
                shape.halfExtentZ(),
                shape.radius(),
                shape.halfHeight(),
                shape.axis(),
                shape.groundY(),
                ""),
            new MaterialComponent(settings.hasFriction() ? settings.friction() : 0.5f,
                settings.hasRestitution() ? settings.restitution() : 0.0f),
            collisionFilter(settings));
    }

    @Nonnull
    private static Store<PhysicsStore> requireSpaceStore(@Nonnull Ref<PhysicsStore> spaceRef) {
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(spaceRef, "spaceRef");
        Store<PhysicsStore> store = checkedRef.getStore();
        PhysicsThreading.requireWorldThread(store, "build a PhysicsStore body entity holder");
        if (!checkedRef.isValid()) {
            throw new IllegalStateException("PhysicsStore space ref is not valid: " + checkedRef);
        }
        return store;
    }

    @Nonnull
    private static TargetComponent initialTarget(@Nonnull Vector3f bodyCenter,
        @Nullable Vector3f linearVelocity) {
        TargetComponent target = new TargetComponent();
        target.setActive(false);
        target.setPosition(bodyCenter);
        target.setRotation(new Quaternionf());
        target.setLinearVelocity(linearVelocity != null ? linearVelocity : new Vector3f());
        target.setAngularVelocity(new Vector3f());
        target.setTransformEnabled(true);
        target.setVelocityEnabled(linearVelocity != null);
        target.setActivate(true);
        return target;
    }

    @Nonnull
    private static CollisionFilterComponent collisionFilter(@Nonnull RigidBodySpawnSettings settings) {
        return new CollisionFilterComponent(
            settings.hasCollisionFilter()
                ? settings.collisionGroup()
                : PhysicsCollisionFilters.DYNAMIC_BODY,
            settings.hasCollisionFilter()
                ? settings.collisionMask()
                : PhysicsCollisionFilters.ALL);
    }

}
