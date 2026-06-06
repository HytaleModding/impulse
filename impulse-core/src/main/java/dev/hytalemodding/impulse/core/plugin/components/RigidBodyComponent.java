package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Durable ECS authoring state for one physics rigid body.
 */
public class RigidBodyComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyComponent> CODEC = BuilderCodec.builder(
            RigidBodyComponent.class,
            RigidBodyComponent::new)
        .append(new KeyedCodec<>("BodyId", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyKey = value != null
                ? RigidBodyKey.of(value)
                : RigidBodyKey.random(),
            RigidBodyComponent::getBodyKeyValue)
        .add()
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER, false),
            (component, value) -> component.spaceId = value != null && value > 0
                ? new SpaceId(value)
                : null,
            RigidBodyComponent::getSpaceIdValue)
        .add()
        .append(new KeyedCodec<>("ShapeType", new EnumCodec<>(ShapeType.class), false),
            (component, value) -> component.shapeType = value != null ? value : ShapeType.BOX,
            RigidBodyComponent::getShapeType)
        .add()
        .append(new KeyedCodec<>("HalfExtentX", Codec.FLOAT, false),
            (component, value) -> component.halfExtentX = value != null ? value : 0.5f,
            RigidBodyComponent::getHalfExtentX)
        .add()
        .append(new KeyedCodec<>("HalfExtentY", Codec.FLOAT, false),
            (component, value) -> component.halfExtentY = value != null ? value : 0.5f,
            RigidBodyComponent::getHalfExtentY)
        .add()
        .append(new KeyedCodec<>("HalfExtentZ", Codec.FLOAT, false),
            (component, value) -> component.halfExtentZ = value != null ? value : 0.5f,
            RigidBodyComponent::getHalfExtentZ)
        .add()
        .append(new KeyedCodec<>("Radius", Codec.FLOAT, false),
            (component, value) -> component.radius = value != null ? value : 0.5f,
            RigidBodyComponent::getRadius)
        .add()
        .append(new KeyedCodec<>("HalfHeight", Codec.FLOAT, false),
            (component, value) -> component.halfHeight = value != null ? value : 0.5f,
            RigidBodyComponent::getHalfHeight)
        .add()
        .append(new KeyedCodec<>("Axis", new EnumCodec<>(PhysicsAxis.class), false),
            (component, value) -> component.axis = value != null ? value : PhysicsAxis.Y,
            RigidBodyComponent::getAxis)
        .add()
        .append(new KeyedCodec<>("GroundY", Codec.FLOAT, false),
            (component, value) -> component.groundY = value != null ? value : 0.0f,
            RigidBodyComponent::getGroundY)
        .add()
        .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class), false),
            (component, value) -> component.bodyType = value != null
                ? value
                : PhysicsBodyType.DYNAMIC,
            RigidBodyComponent::getBodyType)
        .add()
        .append(new KeyedCodec<>("Mass", Codec.FLOAT, false),
            (component, value) -> component.mass = value != null ? value : 1.0f,
            RigidBodyComponent::getMass)
        .add()
        .append(new KeyedCodec<>("Friction", Codec.FLOAT, false),
            (component, value) -> component.friction = value != null ? value : 0.5f,
            RigidBodyComponent::getFriction)
        .add()
        .append(new KeyedCodec<>("Restitution", Codec.FLOAT, false),
            (component, value) -> component.restitution = value != null ? value : 0.0f,
            RigidBodyComponent::getRestitution)
        .add()
        .append(new KeyedCodec<>("LinearDamping", Codec.FLOAT, false),
            (component, value) -> component.linearDamping = value != null ? value : 0.0f,
            RigidBodyComponent::getLinearDamping)
        .add()
        .append(new KeyedCodec<>("AngularDamping", Codec.FLOAT, false),
            (component, value) -> component.angularDamping = value != null ? value : 0.0f,
            RigidBodyComponent::getAngularDamping)
        .add()
        .append(new KeyedCodec<>("Sensor", Codec.BOOLEAN, false),
            (component, value) -> component.sensor = value != null && value,
            RigidBodyComponent::isSensor)
        .add()
        .append(new KeyedCodec<>("CollisionGroup", Codec.INTEGER, false),
            (component, value) -> component.collisionGroup = value != null
                ? value
                : PhysicsCollisionFilters.DYNAMIC_BODY,
            RigidBodyComponent::getCollisionGroup)
        .add()
        .append(new KeyedCodec<>("CollisionMask", Codec.INTEGER, false),
            (component, value) -> component.collisionMask = value != null
                ? value
                : PhysicsCollisionFilters.ALL,
            RigidBodyComponent::getCollisionMask)
        .add()
        .append(new KeyedCodec<>("PersistenceMode", new EnumCodec<>(PhysicsBodyPersistenceMode.class), false),
            (component, value) -> component.persistenceMode = value != null
                ? value
                : PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            RigidBodyComponent::getPersistenceMode)
        .add()
        .append(new KeyedCodec<>("Ownership", new EnumCodec<>(Ownership.class), false),
            (component, value) -> component.ownership = value != null
                ? value
                : Ownership.ENTITY_OWNED,
            RigidBodyComponent::getOwnership)
        .add()
        .build();

    @Nonnull
    private RigidBodyKey bodyKey = RigidBodyKey.random();
    @Nullable
    private SpaceId spaceId;
    @Nonnull
    private ShapeType shapeType = ShapeType.BOX;
    @Setter
    @Getter
    private float halfExtentX = 0.5f;
    @Setter
    @Getter
    private float halfExtentY = 0.5f;
    @Setter
    @Getter
    private float halfExtentZ = 0.5f;
    @Setter
    @Getter
    private float radius = 0.5f;
    @Setter
    @Getter
    private float halfHeight = 0.5f;
    @Nonnull
    private PhysicsAxis axis = PhysicsAxis.Y;
    @Setter
    @Getter
    private float groundY;
    @Nonnull
    private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
    @Setter
    @Getter
    private float mass = 1.0f;
    @Setter
    @Getter
    private float friction = 0.5f;
    @Setter
    @Getter
    private float restitution;
    @Setter
    @Getter
    private float linearDamping;
    @Setter
    @Getter
    private float angularDamping;
    @Setter
    @Getter
    private boolean sensor;
    @Setter
    @Getter
    private int collisionGroup = PhysicsCollisionFilters.DYNAMIC_BODY;
    @Setter
    @Getter
    private int collisionMask = PhysicsCollisionFilters.ALL;
    @Nonnull
    private PhysicsBodyPersistenceMode persistenceMode = PhysicsBodyPersistenceMode.RUNTIME_ONLY;
    @Nonnull
    private Ownership ownership = Ownership.ENTITY_OWNED;

    public RigidBodyComponent() {
    }

    public RigidBodyComponent(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull ShapeType shapeType,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float groundY,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        float friction,
        float restitution,
        float linearDamping,
        float angularDamping,
        boolean sensor,
        int collisionGroup,
        int collisionMask,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull Ownership ownership) {
        this.bodyKey = bodyKey;
        this.spaceId = spaceId;
        this.shapeType = shapeType;
        this.halfExtentX = halfExtentX;
        this.halfExtentY = halfExtentY;
        this.halfExtentZ = halfExtentZ;
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.axis = axis;
        this.groundY = groundY;
        this.bodyType = bodyType;
        this.mass = mass;
        this.friction = friction;
        this.restitution = restitution;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
        this.sensor = sensor;
        this.collisionGroup = collisionGroup;
        this.collisionMask = collisionMask;
        this.persistenceMode = persistenceMode;
        this.ownership = ownership;
    }

    @Nonnull
    public RigidBodyKey getBodyKey() {
        return bodyKey;
    }

    public void setBodyKey(@Nonnull RigidBodyKey bodyKey) {
        this.bodyKey = bodyKey;
    }

    @Nullable
    public SpaceId getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(@Nullable SpaceId spaceId) {
        this.spaceId = spaceId;
    }

    @Nonnull
    public ShapeType getShapeType() {
        return shapeType;
    }

    public void setShapeType(@Nonnull ShapeType shapeType) {
        this.shapeType = shapeType;
    }

    @Nonnull
    public PhysicsAxis getAxis() {
        return axis;
    }

    public void setAxis(@Nonnull PhysicsAxis axis) {
        this.axis = axis;
    }

    @Nonnull
    public PhysicsBodyType getBodyType() {
        return bodyType;
    }

    public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
        this.bodyType = bodyType;
    }

    @Nonnull
    public PhysicsBodyPersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    public void setPersistenceMode(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        this.persistenceMode = persistenceMode;
    }

    @Nonnull
    public Ownership getOwnership() {
        return ownership;
    }

    public void setOwnership(@Nonnull Ownership ownership) {
        this.ownership = ownership;
    }

    public static ComponentType<EntityStore, RigidBodyComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyComponentType();
    }

    @Nullable
    private Integer getSpaceIdValue() {
        return spaceId != null ? spaceId.value() : null;
    }

    @Nonnull
    private UUID getBodyKeyValue() {
        return bodyKey.value();
    }

    @Nonnull
    @Override
    public RigidBodyComponent clone() {
        return new RigidBodyComponent(bodyKey,
            spaceId,
            shapeType,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axis,
            groundY,
            bodyType,
            mass,
            friction,
            restitution,
            linearDamping,
            angularDamping,
            sensor,
            collisionGroup,
            collisionMask,
            persistenceMode,
            ownership);
    }

    public enum Ownership {
        ENTITY_OWNED,
        DETACHED_VIEW,
        FULL_DETACHED
    }
}
