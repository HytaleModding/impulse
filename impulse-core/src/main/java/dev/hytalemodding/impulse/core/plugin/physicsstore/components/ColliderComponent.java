package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.codec.ImpulseCodecs;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Collider row that binds a body to shape, material, and filter rows.
 */
public final class ColliderComponent implements Component<PhysicsStore> {

    private static final Vector3f ZERO = new Vector3f();
    private static final Quaternionf IDENTITY = new Quaternionf();

    @Nonnull
    public static final BuilderCodec<ColliderComponent> CODEC = BuilderCodec.builder(
            ColliderComponent.class,
            ColliderComponent::new)
        .append(new KeyedCodec<>("BodyUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyUuid = value,
            ColliderComponent::getBodyUuid)
        .add()
        .append(new KeyedCodec<>("ShapeUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.shapeUuid = value,
            ColliderComponent::getShapeUuid)
        .add()
        .append(new KeyedCodec<>("MaterialUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.materialUuid = value,
            ColliderComponent::getMaterialUuid)
        .add()
        .append(new KeyedCodec<>("FilterUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.filterUuid = value,
            ColliderComponent::getFilterUuid)
        .add()
        .append(new KeyedCodec<>("LocalPosition", Vector3fUtil.CODEC, false),
            (component, value) -> component.localPosition.set(value != null ? value : ZERO),
            ColliderComponent::getLocalPosition)
        .add()
        .append(new KeyedCodec<>("LocalRotation", ImpulseCodecs.QUATERNIONF, false),
            (component, value) -> component.localRotation.set(value != null ? value : IDENTITY),
            ColliderComponent::getLocalRotation)
        .add()
        .append(new KeyedCodec<>("Sensor", Codec.BOOLEAN, false),
            (component, value) -> component.sensor = value != null && value,
            ColliderComponent::isSensor)
        .add()
        .build();

    @Nonnull
    private UUID bodyUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID shapeUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID materialUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID filterUuid = new UUID(0L, 0L);
    @Nonnull
    private final Vector3f localPosition = new Vector3f();
    @Nonnull
    private final Quaternionf localRotation = new Quaternionf();
    private boolean sensor;

    public ColliderComponent() {
    }

    public ColliderComponent(@Nonnull UUID bodyUuid,
        @Nonnull UUID shapeUuid,
        @Nonnull UUID materialUuid,
        @Nonnull UUID filterUuid,
        @Nonnull Vector3f localPosition,
        @Nonnull Quaternionf localRotation,
        boolean sensor) {
        this.bodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
        this.shapeUuid = Objects.requireNonNull(shapeUuid, "shapeUuid");
        this.materialUuid = Objects.requireNonNull(materialUuid, "materialUuid");
        this.filterUuid = Objects.requireNonNull(filterUuid, "filterUuid");
        this.localPosition.set(Objects.requireNonNull(localPosition, "localPosition"));
        this.localRotation.set(Objects.requireNonNull(localRotation, "localRotation"));
        this.sensor = sensor;
    }

    @Nonnull
    public UUID getBodyUuid() {
        return bodyUuid;
    }

    public void setBodyUuid(@Nonnull UUID bodyUuid) {
        this.bodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
    }

    @Nonnull
    public UUID getShapeUuid() {
        return shapeUuid;
    }

    public void setShapeUuid(@Nonnull UUID shapeUuid) {
        this.shapeUuid = Objects.requireNonNull(shapeUuid, "shapeUuid");
    }

    @Nonnull
    public UUID getMaterialUuid() {
        return materialUuid;
    }

    public void setMaterialUuid(@Nonnull UUID materialUuid) {
        this.materialUuid = Objects.requireNonNull(materialUuid, "materialUuid");
    }

    @Nonnull
    public UUID getFilterUuid() {
        return filterUuid;
    }

    public void setFilterUuid(@Nonnull UUID filterUuid) {
        this.filterUuid = Objects.requireNonNull(filterUuid, "filterUuid");
    }

    @Nonnull
    public Vector3f getLocalPosition() {
        return new Vector3f(localPosition);
    }

    public void setLocalPosition(@Nonnull Vector3f localPosition) {
        this.localPosition.set(Objects.requireNonNull(localPosition, "localPosition"));
    }

    @Nonnull
    public Quaternionf getLocalRotation() {
        return new Quaternionf(localRotation);
    }

    public void setLocalRotation(@Nonnull Quaternionf localRotation) {
        this.localRotation.set(Objects.requireNonNull(localRotation, "localRotation"));
    }

    public boolean isSensor() {
        return sensor;
    }

    public void setSensor(boolean sensor) {
        this.sensor = sensor;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ColliderComponent> getComponentType() {
        return PhysicsStoreTypes.colliderComponentType();
    }

    @Nonnull
    @Override
    public ColliderComponent clone() {
        return new ColliderComponent(bodyUuid,
            shapeUuid,
            materialUuid,
            filterUuid,
            localPosition,
            localRotation,
            sensor);
    }
}
