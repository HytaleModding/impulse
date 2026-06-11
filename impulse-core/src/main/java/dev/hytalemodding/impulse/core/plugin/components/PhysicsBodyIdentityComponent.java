package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Durable ECS identity for an entity-authored physics body.
 */
public class PhysicsBodyIdentityComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<PhysicsBodyIdentityComponent> CODEC = BuilderCodec.builder(
            PhysicsBodyIdentityComponent.class,
            PhysicsBodyIdentityComponent::new)
        .append(new KeyedCodec<>("BodyId", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyKey = value != null
                ? RigidBodyKey.of(value)
                : RigidBodyKey.random(),
            PhysicsBodyIdentityComponent::getBodyKeyValue)
        .add()
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER, false),
            (component, value) -> component.spaceId = value != null && value > 0
                ? new SpaceId(value)
                : null,
            PhysicsBodyIdentityComponent::getSpaceIdValue)
        .add()
        .append(new KeyedCodec<>("PersistenceMode", new EnumCodec<>(PhysicsBodyPersistenceMode.class), false),
            (component, value) -> component.persistenceMode = value != null
                ? value
                : PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            PhysicsBodyIdentityComponent::getPersistenceMode)
        .add()
        .build();

    @Nonnull
    private RigidBodyKey bodyKey = RigidBodyKey.random();
    @Nullable
    private SpaceId spaceId;
    @Nonnull
    private PhysicsBodyPersistenceMode persistenceMode = PhysicsBodyPersistenceMode.RUNTIME_ONLY;

    public PhysicsBodyIdentityComponent() {
    }

    public PhysicsBodyIdentityComponent(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        this.bodyKey = Objects.requireNonNull(bodyKey, "bodyKey");
        this.spaceId = spaceId;
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
    }

    @Nonnull
    public RigidBodyKey getBodyKey() {
        return bodyKey;
    }

    public void setBodyKey(@Nonnull RigidBodyKey bodyKey) {
        this.bodyKey = Objects.requireNonNull(bodyKey, "bodyKey");
    }

    @Nullable
    public SpaceId getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(@Nullable SpaceId spaceId) {
        this.spaceId = spaceId;
    }

    @Nonnull
    public PhysicsBodyPersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    public void setPersistenceMode(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
    }

    public static ComponentType<EntityStore, PhysicsBodyIdentityComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyIdentityComponentType();
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
    public PhysicsBodyIdentityComponent clone() {
        return new PhysicsBodyIdentityComponent(bodyKey, spaceId, persistenceMode);
    }
}
