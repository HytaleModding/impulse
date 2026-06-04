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
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime readback state for ECS rigid body reconciliation.
 */
public class RigidBodyLifecycleComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyLifecycleComponent> CODEC = BuilderCodec.builder(
            RigidBodyLifecycleComponent.class,
            RigidBodyLifecycleComponent::new)
        .append(new KeyedCodec<>("State", new EnumCodec<>(State.class), false),
            (component, value) -> component.state = value != null ? value : State.PENDING,
            RigidBodyLifecycleComponent::getState)
        .add()
        .append(new KeyedCodec<>("BodyId", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyKey = value != null ? RigidBodyKey.of(value) : null,
            RigidBodyLifecycleComponent::getBodyKeyValue)
        .add()
        .append(new KeyedCodec<>("Ownership", new EnumCodec<>(RigidBodyComponent.Ownership.class), false),
            (component, value) -> component.ownership = value != null
                ? value
                : RigidBodyComponent.Ownership.ENTITY_OWNED,
            RigidBodyLifecycleComponent::getOwnership)
        .add()
        .append(new KeyedCodec<>("Message", Codec.STRING, false),
            (component, value) -> component.message = value,
            RigidBodyLifecycleComponent::getMessage)
        .add()
        .build();

    @Nonnull
    private State state = State.PENDING;
    @Nullable
    private RigidBodyKey bodyKey;
    @Nonnull
    private RigidBodyComponent.Ownership ownership =
        RigidBodyComponent.Ownership.ENTITY_OWNED;
    @Nullable
    private String message;

    public RigidBodyLifecycleComponent() {
    }

    public RigidBodyLifecycleComponent(@Nonnull State state,
        @Nullable RigidBodyKey bodyKey,
        @Nonnull RigidBodyComponent.Ownership ownership,
        @Nullable String message) {
        this.state = state;
        this.bodyKey = bodyKey;
        this.ownership = ownership;
        this.message = message;
    }

    @Nonnull
    public static RigidBodyLifecycleComponent pending(@Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyComponent.Ownership ownership) {
        return new RigidBodyLifecycleComponent(State.PENDING, bodyKey, ownership, null);
    }

    @Nonnull
    public static RigidBodyLifecycleComponent created(@Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyComponent.Ownership ownership) {
        return new RigidBodyLifecycleComponent(State.CREATED, bodyKey, ownership, null);
    }

    @Nonnull
    public static RigidBodyLifecycleComponent destroyed(@Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyComponent.Ownership ownership) {
        return new RigidBodyLifecycleComponent(State.DESTROYED, bodyKey, ownership, null);
    }

    @Nonnull
    public static RigidBodyLifecycleComponent failed(@Nullable RigidBodyKey bodyKey,
        @Nonnull RigidBodyComponent.Ownership ownership,
        @Nonnull String message) {
        return new RigidBodyLifecycleComponent(State.FAILED, bodyKey, ownership, message);
    }

    @Nonnull
    public State getState() {
        return state;
    }

    public void setState(@Nonnull State state) {
        this.state = state;
    }

    @Nullable
    public RigidBodyKey getBodyKey() {
        return bodyKey;
    }

    public void setBodyKey(@Nullable RigidBodyKey bodyKey) {
        this.bodyKey = bodyKey;
    }

    @Nonnull
    public RigidBodyComponent.Ownership getOwnership() {
        return ownership;
    }

    public void setOwnership(@Nonnull RigidBodyComponent.Ownership ownership) {
        this.ownership = ownership;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    public static ComponentType<EntityStore, RigidBodyLifecycleComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyLifecycleComponentType();
    }

    @Nullable
    private UUID getBodyKeyValue() {
        return bodyKey != null ? bodyKey.value() : null;
    }

    @Nonnull
    @Override
    public RigidBodyLifecycleComponent clone() {
        return new RigidBodyLifecycleComponent(state, bodyKey, ownership, message);
    }

    public enum State {
        PENDING,
        CREATED,
        DESTROYED,
        FAILED
    }
}
