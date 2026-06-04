package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;

/**
 * Value-only rigid-body shape intent for ECS rigid bodies.
 */
public class RigidBodyShapeComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyShapeComponent> CODEC = BuilderCodec.builder(
            RigidBodyShapeComponent.class,
            RigidBodyShapeComponent::new)
        .append(new KeyedCodec<>("ShapeType", new EnumCodec<>(ShapeType.class), false),
            (component, value) -> component.shapeType = value != null ? value : ShapeType.BOX,
            RigidBodyShapeComponent::getShapeType)
        .add()
        .append(new KeyedCodec<>("HalfExtentX", Codec.FLOAT, false),
            (component, value) -> component.halfExtentX = value != null ? value : 0.5f,
            RigidBodyShapeComponent::getHalfExtentX)
        .add()
        .append(new KeyedCodec<>("HalfExtentY", Codec.FLOAT, false),
            (component, value) -> component.halfExtentY = value != null ? value : 0.5f,
            RigidBodyShapeComponent::getHalfExtentY)
        .add()
        .append(new KeyedCodec<>("HalfExtentZ", Codec.FLOAT, false),
            (component, value) -> component.halfExtentZ = value != null ? value : 0.5f,
            RigidBodyShapeComponent::getHalfExtentZ)
        .add()
        .append(new KeyedCodec<>("Radius", Codec.FLOAT, false),
            (component, value) -> component.radius = value != null ? value : 0.5f,
            RigidBodyShapeComponent::getRadius)
        .add()
        .append(new KeyedCodec<>("HalfHeight", Codec.FLOAT, false),
            (component, value) -> component.halfHeight = value != null ? value : 0.5f,
            RigidBodyShapeComponent::getHalfHeight)
        .add()
        .append(new KeyedCodec<>("Axis", new EnumCodec<>(PhysicsAxis.class), false),
            (component, value) -> component.axis = value != null ? value : PhysicsAxis.Y,
            RigidBodyShapeComponent::getAxis)
        .add()
        .append(new KeyedCodec<>("GroundY", Codec.FLOAT, false),
            (component, value) -> component.groundY = value != null ? value : 0.0f,
            RigidBodyShapeComponent::getGroundY)
        .add()
        .build();

    @Nonnull
    private ShapeType shapeType = ShapeType.BOX;
    private float halfExtentX = 0.5f;
    private float halfExtentY = 0.5f;
    private float halfExtentZ = 0.5f;
    private float radius = 0.5f;
    private float halfHeight = 0.5f;
    @Nonnull
    private PhysicsAxis axis = PhysicsAxis.Y;
    private float groundY;

    public RigidBodyShapeComponent() {
    }

    public RigidBodyShapeComponent(@Nonnull ShapeType shapeType,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float groundY) {
        this.shapeType = shapeType;
        this.halfExtentX = halfExtentX;
        this.halfExtentY = halfExtentY;
        this.halfExtentZ = halfExtentZ;
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.axis = axis;
        this.groundY = groundY;
    }

    @Nonnull
    public ShapeType getShapeType() {
        return shapeType;
    }

    public void setShapeType(@Nonnull ShapeType shapeType) {
        this.shapeType = shapeType;
    }

    public float getHalfExtentX() {
        return halfExtentX;
    }

    public void setHalfExtentX(float halfExtentX) {
        this.halfExtentX = halfExtentX;
    }

    public float getHalfExtentY() {
        return halfExtentY;
    }

    public void setHalfExtentY(float halfExtentY) {
        this.halfExtentY = halfExtentY;
    }

    public float getHalfExtentZ() {
        return halfExtentZ;
    }

    public void setHalfExtentZ(float halfExtentZ) {
        this.halfExtentZ = halfExtentZ;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public float getHalfHeight() {
        return halfHeight;
    }

    public void setHalfHeight(float halfHeight) {
        this.halfHeight = halfHeight;
    }

    @Nonnull
    public PhysicsAxis getAxis() {
        return axis;
    }

    public void setAxis(@Nonnull PhysicsAxis axis) {
        this.axis = axis;
    }

    public float getGroundY() {
        return groundY;
    }

    public void setGroundY(float groundY) {
        this.groundY = groundY;
    }

    public static ComponentType<EntityStore, RigidBodyShapeComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyShapeComponentType();
    }

    @Nonnull
    @Override
    public RigidBodyShapeComponent clone() {
        return new RigidBodyShapeComponent(shapeType,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axis,
            groundY);
    }
}
