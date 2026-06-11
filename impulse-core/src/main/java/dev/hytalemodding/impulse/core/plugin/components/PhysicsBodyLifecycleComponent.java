package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime readback state for entity-authored physics body reconciliation.
 */
public class PhysicsBodyLifecycleComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<PhysicsBodyLifecycleComponent> CODEC = BuilderCodec.builder(
            PhysicsBodyLifecycleComponent.class,
            PhysicsBodyLifecycleComponent::new)
        .append(new KeyedCodec<>("State", new EnumCodec<>(State.class), false),
            (component, value) -> component.state = value != null ? value : State.PENDING,
            PhysicsBodyLifecycleComponent::getState)
        .add()
        .append(new KeyedCodec<>("BodyId", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyKey = value != null ? RigidBodyKey.of(value) : null,
            PhysicsBodyLifecycleComponent::getBodyKeyValue)
        .add()
        .append(new KeyedCodec<>("Message", Codec.STRING, false),
            (component, value) -> component.message = value,
            PhysicsBodyLifecycleComponent::getMessage)
        .add()
        .build();

    @Nonnull
    private State state = State.PENDING;
    @Nullable
    private RigidBodyKey bodyKey;
    @Nullable
    private String message;

    public PhysicsBodyLifecycleComponent() {
    }

    public PhysicsBodyLifecycleComponent(@Nonnull State state,
        @Nullable RigidBodyKey bodyKey,
        @Nullable String message) {
        this.state = Objects.requireNonNull(state, "state");
        this.bodyKey = bodyKey;
        this.message = message;
    }

    @Nonnull
    public static PhysicsBodyLifecycleComponent pending(@Nonnull RigidBodyKey bodyKey) {
        return new PhysicsBodyLifecycleComponent(State.PENDING, bodyKey, null);
    }

    @Nonnull
    public static PhysicsBodyLifecycleComponent created(@Nonnull RigidBodyKey bodyKey) {
        return new PhysicsBodyLifecycleComponent(State.CREATED, bodyKey, null);
    }

    @Nonnull
    public static PhysicsBodyLifecycleComponent destroyed(@Nonnull RigidBodyKey bodyKey) {
        return new PhysicsBodyLifecycleComponent(State.DESTROYED, bodyKey, null);
    }

    @Nonnull
    public static PhysicsBodyLifecycleComponent failed(@Nullable RigidBodyKey bodyKey,
        @Nonnull String message) {
        return new PhysicsBodyLifecycleComponent(State.FAILED, bodyKey, message);
    }

    @Nonnull
    public State getState() {
        return state;
    }

    public void setState(@Nonnull State state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Nullable
    public RigidBodyKey getBodyKey() {
        return bodyKey;
    }

    public void setBodyKey(@Nullable RigidBodyKey bodyKey) {
        this.bodyKey = bodyKey;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    public static ComponentType<EntityStore, PhysicsBodyLifecycleComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyLifecycleComponentType();
    }

    @Nullable
    private UUID getBodyKeyValue() {
        return bodyKey != null ? bodyKey.value() : null;
    }

    @Nonnull
    @Override
    public PhysicsBodyLifecycleComponent clone() {
        return new PhysicsBodyLifecycleComponent(state, bodyKey, message);
    }

    public enum State {
        PENDING,
        CREATED,
        DESTROYED,
        FAILED
    }
}
