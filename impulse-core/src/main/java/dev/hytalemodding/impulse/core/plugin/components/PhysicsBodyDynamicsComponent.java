package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;

/**
 * Durable ECS mass and damping data for an entity-authored physics body.
 */
public class PhysicsBodyDynamicsComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<PhysicsBodyDynamicsComponent> CODEC = BuilderCodec.builder(
            PhysicsBodyDynamicsComponent.class,
            PhysicsBodyDynamicsComponent::new)
        .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class), false),
            (component, value) -> component.bodyType = value != null
                ? value
                : PhysicsBodyType.DYNAMIC,
            PhysicsBodyDynamicsComponent::getBodyType)
        .add()
        .append(new KeyedCodec<>("Mass", Codec.FLOAT, false),
            (component, value) -> component.mass = value != null ? value : 1.0f,
            PhysicsBodyDynamicsComponent::getMass)
        .add()
        .append(new KeyedCodec<>("LinearDamping", Codec.FLOAT, false),
            (component, value) -> component.linearDamping = value != null ? value : 0.0f,
            PhysicsBodyDynamicsComponent::getLinearDamping)
        .add()
        .append(new KeyedCodec<>("AngularDamping", Codec.FLOAT, false),
            (component, value) -> component.angularDamping = value != null ? value : 0.0f,
            PhysicsBodyDynamicsComponent::getAngularDamping)
        .add()
        .build();

    @Nonnull
    private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
    @Setter
    @Getter
    private float mass = 1.0f;
    @Setter
    @Getter
    private float linearDamping;
    @Setter
    @Getter
    private float angularDamping;

    public PhysicsBodyDynamicsComponent() {
    }

    public PhysicsBodyDynamicsComponent(@Nonnull PhysicsBodyType bodyType,
        float mass,
        float linearDamping,
        float angularDamping) {
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.mass = mass;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
    }

    @Nonnull
    public PhysicsBodyType getBodyType() {
        return bodyType;
    }

    public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
    }

    public static ComponentType<EntityStore, PhysicsBodyDynamicsComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyDynamicsComponentType();
    }

    @Nonnull
    @Override
    public PhysicsBodyDynamicsComponent clone() {
        return new PhysicsBodyDynamicsComponent(bodyType, mass, linearDamping, angularDamping);
    }
}
