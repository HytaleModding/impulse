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
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;

/**
 * Durable ECS shape data for an entity-authored physics body.
 */
public class PhysicsBodyShapeComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<PhysicsBodyShapeComponent> CODEC = BuilderCodec.builder(
            PhysicsBodyShapeComponent.class,
            PhysicsBodyShapeComponent::new)
        .append(new KeyedCodec<>("ShapeType", new EnumCodec<>(ShapeType.class), false),
            (component, value) -> component.shapeType = value != null ? value : ShapeType.BOX,
            PhysicsBodyShapeComponent::getShapeType)
        .add()
        .append(new KeyedCodec<>("HalfExtentX", Codec.FLOAT, false),
            (component, value) -> component.halfExtentX = value != null ? value : 0.5f,
            PhysicsBodyShapeComponent::getHalfExtentX)
        .add()
        .append(new KeyedCodec<>("HalfExtentY", Codec.FLOAT, false),
            (component, value) -> component.halfExtentY = value != null ? value : 0.5f,
            PhysicsBodyShapeComponent::getHalfExtentY)
        .add()
        .append(new KeyedCodec<>("HalfExtentZ", Codec.FLOAT, false),
            (component, value) -> component.halfExtentZ = value != null ? value : 0.5f,
            PhysicsBodyShapeComponent::getHalfExtentZ)
        .add()
        .append(new KeyedCodec<>("Radius", Codec.FLOAT, false),
            (component, value) -> component.radius = value != null ? value : 0.5f,
            PhysicsBodyShapeComponent::getRadius)
        .add()
        .append(new KeyedCodec<>("HalfHeight", Codec.FLOAT, false),
            (component, value) -> component.halfHeight = value != null ? value : 0.5f,
            PhysicsBodyShapeComponent::getHalfHeight)
        .add()
        .append(new KeyedCodec<>("Axis", new EnumCodec<>(PhysicsAxis.class), false),
            (component, value) -> component.axis = value != null ? value : PhysicsAxis.Y,
            PhysicsBodyShapeComponent::getAxis)
        .add()
        .append(new KeyedCodec<>("GroundY", Codec.FLOAT, false),
            (component, value) -> component.groundY = value != null ? value : 0.0f,
            PhysicsBodyShapeComponent::getGroundY)
        .add()
        .build();

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

    public PhysicsBodyShapeComponent() {
    }

    public PhysicsBodyShapeComponent(@Nonnull ShapeType shapeType,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float groundY) {
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType");
        this.halfExtentX = halfExtentX;
        this.halfExtentY = halfExtentY;
        this.halfExtentZ = halfExtentZ;
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.axis = Objects.requireNonNull(axis, "axis");
        this.groundY = groundY;
    }

    @Nonnull
    public static PhysicsBodyShapeComponent box(float halfX, float halfY, float halfZ) {
        return new PhysicsBodyShapeComponent(ShapeType.BOX,
            halfX,
            halfY,
            halfZ,
            0.5f,
            0.5f,
            PhysicsAxis.Y,
            0.0f);
    }

    @Nonnull
    public static PhysicsBodyShapeComponent sphere(float radius) {
        return new PhysicsBodyShapeComponent(ShapeType.SPHERE,
            0.5f,
            0.5f,
            0.5f,
            radius,
            0.5f,
            PhysicsAxis.Y,
            0.0f);
    }

    @Nonnull
    public static PhysicsBodyShapeComponent capsule(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis) {
        return new PhysicsBodyShapeComponent(ShapeType.CAPSULE,
            0.5f,
            0.5f,
            0.5f,
            radius,
            halfHeight,
            axis,
            0.0f);
    }

    @Nonnull
    public ShapeType getShapeType() {
        return shapeType;
    }

    public void setShapeType(@Nonnull ShapeType shapeType) {
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType");
    }

    @Nonnull
    public PhysicsAxis getAxis() {
        return axis;
    }

    public void setAxis(@Nonnull PhysicsAxis axis) {
        this.axis = Objects.requireNonNull(axis, "axis");
    }

    public static ComponentType<EntityStore, PhysicsBodyShapeComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyShapeComponentType();
    }

    @Nonnull
    @Override
    public PhysicsBodyShapeComponent clone() {
        return new PhysicsBodyShapeComponent(shapeType,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axis,
            groundY);
    }
}
