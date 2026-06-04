package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import javax.annotation.Nonnull;

/**
 * Persistence intent for an ECS rigid body.
 */
public class RigidBodyPersistenceComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyPersistenceComponent> CODEC = BuilderCodec.builder(
            RigidBodyPersistenceComponent.class,
            RigidBodyPersistenceComponent::new)
        .append(new KeyedCodec<>("PersistenceMode", new EnumCodec<>(PhysicsBodyPersistenceMode.class), false),
            (component, value) -> component.persistenceMode = value != null
                ? value
                : PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            RigidBodyPersistenceComponent::getPersistenceMode)
        .add()
        .build();

    @Nonnull
    private PhysicsBodyPersistenceMode persistenceMode = PhysicsBodyPersistenceMode.RUNTIME_ONLY;

    public RigidBodyPersistenceComponent() {
    }

    public RigidBodyPersistenceComponent(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        this.persistenceMode = persistenceMode;
    }

    @Nonnull
    public PhysicsBodyPersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    public void setPersistenceMode(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        this.persistenceMode = persistenceMode;
    }

    public static ComponentType<EntityStore, RigidBodyPersistenceComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyPersistenceComponentType();
    }

    @Nonnull
    @Override
    public RigidBodyPersistenceComponent clone() {
        return new RigidBodyPersistenceComponent(persistenceMode);
    }
}
