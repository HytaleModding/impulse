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
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Local collider settings for one body aggregate row.
 */
public final class ColliderComponent implements Component<PhysicsStore> {

    private static final Vector3f ZERO = new Vector3f();
    private static final Quaternionf IDENTITY = new Quaternionf();

    @Nonnull
    public static final BuilderCodec<ColliderComponent> CODEC = BuilderCodec.builder(
            ColliderComponent.class,
            ColliderComponent::new)
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
    private final Vector3f localPosition = new Vector3f();
    @Nonnull
    private final Quaternionf localRotation = new Quaternionf();
    private boolean sensor;

    public ColliderComponent() {
    }

    public ColliderComponent(@Nonnull Vector3f localPosition,
        @Nonnull Quaternionf localRotation,
        boolean sensor) {
        this.localPosition.set(Objects.requireNonNull(localPosition, "localPosition"));
        this.localRotation.set(Objects.requireNonNull(localRotation, "localRotation"));
        this.sensor = sensor;
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
        return new ColliderComponent(localPosition,
            localRotation,
            sensor);
    }
}
