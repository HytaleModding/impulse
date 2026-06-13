package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Authored collision shape row shared by one or more colliders.
 */
public final class ShapeComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<ShapeComponent> CODEC = BuilderCodec.builder(
            ShapeComponent.class,
            ShapeComponent::new)
        .append(new KeyedCodec<>("ShapeType", new EnumCodec<>(ShapeType.class), false),
            (component, value) -> component.shapeType = value != null ? value : ShapeType.BOX,
            ShapeComponent::getShapeType)
        .add()
        .append(new KeyedCodec<>("HalfExtentX", Codec.FLOAT, false),
            (component, value) -> component.halfExtentX = value != null ? value : 0.5f,
            ShapeComponent::getHalfExtentX)
        .add()
        .append(new KeyedCodec<>("HalfExtentY", Codec.FLOAT, false),
            (component, value) -> component.halfExtentY = value != null ? value : 0.5f,
            ShapeComponent::getHalfExtentY)
        .add()
        .append(new KeyedCodec<>("HalfExtentZ", Codec.FLOAT, false),
            (component, value) -> component.halfExtentZ = value != null ? value : 0.5f,
            ShapeComponent::getHalfExtentZ)
        .add()
        .append(new KeyedCodec<>("Radius", Codec.FLOAT, false),
            (component, value) -> component.radius = value != null ? value : 0.5f,
            ShapeComponent::getRadius)
        .add()
        .append(new KeyedCodec<>("HalfHeight", Codec.FLOAT, false),
            (component, value) -> component.halfHeight = value != null ? value : 0.5f,
            ShapeComponent::getHalfHeight)
        .add()
        .append(new KeyedCodec<>("Axis", new EnumCodec<>(PhysicsAxis.class), false),
            (component, value) -> component.axis = value != null ? value : PhysicsAxis.Y,
            ShapeComponent::getAxis)
        .add()
        .append(new KeyedCodec<>("GroundY", Codec.FLOAT, false),
            (component, value) -> component.groundY = value != null ? value : 0.0f,
            ShapeComponent::getGroundY)
        .add()
        .append(new KeyedCodec<>("ResourceKey", Codec.STRING, false),
            (component, value) -> component.resourceKey = value != null ? value : "",
            ShapeComponent::getResourceKey)
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
    @Nonnull
    private String resourceKey = "";

    public ShapeComponent() {
    }

    public ShapeComponent(@Nonnull ShapeType shapeType,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float groundY,
        @Nonnull String resourceKey) {
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType");
        this.halfExtentX = halfExtentX;
        this.halfExtentY = halfExtentY;
        this.halfExtentZ = halfExtentZ;
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.axis = Objects.requireNonNull(axis, "axis");
        this.groundY = groundY;
        this.resourceKey = Objects.requireNonNull(resourceKey, "resourceKey");
    }

    @Nonnull
    public ShapeType getShapeType() {
        return shapeType;
    }

    public void setShapeType(@Nonnull ShapeType shapeType) {
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType");
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
        this.axis = Objects.requireNonNull(axis, "axis");
    }

    public float getGroundY() {
        return groundY;
    }

    public void setGroundY(float groundY) {
        this.groundY = groundY;
    }

    @Nonnull
    public String getResourceKey() {
        return resourceKey;
    }

    public void setResourceKey(@Nonnull String resourceKey) {
        this.resourceKey = Objects.requireNonNull(resourceKey, "resourceKey");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ShapeComponent> getComponentType() {
        return PhysicsStoreTypes.shapeComponentType();
    }

    @Nonnull
    @Override
    public ShapeComponent clone() {
        return new ShapeComponent(shapeType,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axis,
            groundY,
            resourceKey);
    }
}
