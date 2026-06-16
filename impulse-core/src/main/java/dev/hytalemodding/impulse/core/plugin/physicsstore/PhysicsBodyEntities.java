package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Factories for PhysicsStore body entity descriptors.
 */
public final class PhysicsBodyEntities {

    private PhysicsBodyEntities() {
    }

    @Nonnull
    public static BodyEntityDescriptor dynamicBody(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return body(spaceUuid,
            bodyUuid,
            bodyCenter,
            shape,
            PhysicsBodyType.DYNAMIC,
            mass,
            settings,
            linearVelocity,
            PhysicsBodyKind.BODY,
            persistenceMode);
    }

    @Nonnull
    public static BodyEntityDescriptor dynamicBody(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return body(spaceRef,
            bodyUuid,
            bodyCenter,
            shape,
            PhysicsBodyType.DYNAMIC,
            mass,
            settings,
            linearVelocity,
            PhysicsBodyKind.BODY,
            persistenceMode);
    }

    @Nonnull
    public static BodyEntityDescriptor body(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(bodyCenter, "bodyCenter");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(persistenceMode, "persistenceMode");

        return BodyEntityDescriptor.of(bodyUuid,
            new BodyComponent(spaceUuid,
                kind,
                persistenceMode),
            new DynamicsComponent(bodyType,
                mass,
                settings.hasLinearDamping() ? settings.linearDamping() : 0.0f,
                settings.hasAngularDamping() ? settings.angularDamping() : 0.0f,
                false),
            initialTarget(bodyCenter, linearVelocity),
            bodyUuid,
            new ColliderComponent(new Vector3f(),
                new Quaternionf(),
                settings.hasSensor() && settings.sensor()),
            bodyUuid,
            new ShapeComponent(shape.type(),
                shape.halfExtentX(),
                shape.halfExtentY(),
                shape.halfExtentZ(),
                shape.radius(),
                shape.halfHeight(),
                shape.axis(),
                shape.groundY(),
                ""),
            bodyUuid,
            new MaterialComponent(settings.hasFriction() ? settings.friction() : 0.5f,
                settings.hasRestitution() ? settings.restitution() : 0.0f),
            bodyUuid,
            collisionFilter(settings));
    }

    @Nonnull
    public static BodyEntityDescriptor body(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        BodyEntityDescriptor descriptor = body(PhysicsStoreEntityRefs.entityUuid(spaceRef),
            bodyUuid,
            bodyCenter,
            shape,
            bodyType,
            mass,
            settings,
            linearVelocity,
            kind,
            persistenceMode);
        descriptor.body().setSpaceRef(spaceRef);
        return descriptor;
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
