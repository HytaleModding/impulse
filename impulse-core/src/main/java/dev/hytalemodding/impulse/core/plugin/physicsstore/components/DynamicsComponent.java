package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Authored motion mode, mass, damping, and CCD flags for a body row.
 */
public final class DynamicsComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<DynamicsComponent> CODEC = BuilderCodec.builder(
            DynamicsComponent.class,
            DynamicsComponent::new)
        .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class), false),
            (component, value) -> component.bodyType = value != null ? value : PhysicsBodyType.DYNAMIC,
            DynamicsComponent::getBodyType)
        .add()
        .append(new KeyedCodec<>("Mass", Codec.FLOAT, false),
            (component, value) -> component.mass = value != null ? value : 1.0f,
            DynamicsComponent::getMass)
        .add()
        .append(new KeyedCodec<>("LinearDamping", Codec.FLOAT, false),
            (component, value) -> component.linearDamping = value != null ? value : 0.0f,
            DynamicsComponent::getLinearDamping)
        .add()
        .append(new KeyedCodec<>("AngularDamping", Codec.FLOAT, false),
            (component, value) -> component.angularDamping = value != null ? value : 0.0f,
            DynamicsComponent::getAngularDamping)
        .add()
        .append(new KeyedCodec<>("ContinuousCollision", Codec.BOOLEAN, false),
            (component, value) -> component.continuousCollisionEnabled = value != null && value,
            DynamicsComponent::isContinuousCollisionEnabled)
        .add()
        .build();

    @Nonnull
    private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
    private float mass = 1.0f;
    private float linearDamping;
    private float angularDamping;
    private boolean continuousCollisionEnabled;

    public DynamicsComponent() {
    }

    public DynamicsComponent(@Nonnull PhysicsBodyType bodyType,
        float mass,
        float linearDamping,
        float angularDamping,
        boolean continuousCollisionEnabled) {
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.mass = mass;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
        this.continuousCollisionEnabled = continuousCollisionEnabled;
    }

    @Nonnull
    public PhysicsBodyType getBodyType() {
        return bodyType;
    }

    public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
    }

    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = mass;
    }

    public float getLinearDamping() {
        return linearDamping;
    }

    public void setLinearDamping(float linearDamping) {
        this.linearDamping = linearDamping;
    }

    public float getAngularDamping() {
        return angularDamping;
    }

    public void setAngularDamping(float angularDamping) {
        this.angularDamping = angularDamping;
    }

    public boolean isContinuousCollisionEnabled() {
        return continuousCollisionEnabled;
    }

    public void setContinuousCollisionEnabled(boolean continuousCollisionEnabled) {
        this.continuousCollisionEnabled = continuousCollisionEnabled;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, DynamicsComponent> getComponentType() {
        return PhysicsStoreTypes.dynamicsComponentType();
    }

    @Nonnull
    @Override
    public DynamicsComponent clone() {
        return new DynamicsComponent(bodyType,
            mass,
            linearDamping,
            angularDamping,
            continuousCollisionEnabled);
    }
}
