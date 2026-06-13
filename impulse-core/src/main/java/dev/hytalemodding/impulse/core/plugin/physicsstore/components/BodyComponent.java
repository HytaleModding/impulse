package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Authored body identity, kind, and persistence policy.
 */
public final class BodyComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<BodyComponent> CODEC = BuilderCodec.builder(
            BodyComponent.class,
            BodyComponent::new)
        .append(new KeyedCodec<>("SpaceUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.spaceUuid = value,
            BodyComponent::getSpaceUuid)
        .add()
        .append(new KeyedCodec<>("Kind", new EnumCodec<>(PhysicsBodyKind.class), false),
            (component, value) -> component.kind = value != null ? value : PhysicsBodyKind.BODY,
            BodyComponent::getKind)
        .add()
        .append(new KeyedCodec<>("PersistenceMode", new EnumCodec<>(PhysicsBodyPersistenceMode.class), false),
            (component, value) -> component.persistenceMode = value != null
                ? value
                : PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            BodyComponent::getPersistenceMode)
        .add()
        .build();

    @Nonnull
    private UUID spaceUuid = new UUID(0L, 0L);
    @Nonnull
    private PhysicsBodyKind kind = PhysicsBodyKind.BODY;
    @Nonnull
    private PhysicsBodyPersistenceMode persistenceMode = PhysicsBodyPersistenceMode.RUNTIME_ONLY;

    public BodyComponent() {
    }

    public BodyComponent(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    public void setSpaceUuid(@Nonnull UUID spaceUuid) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
    }

    @Nonnull
    public PhysicsBodyKind getKind() {
        return kind;
    }

    public void setKind(@Nonnull PhysicsBodyKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Nonnull
    public PhysicsBodyPersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    public void setPersistenceMode(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, BodyComponent> getComponentType() {
        return PhysicsStoreTypes.bodyComponentType();
    }

    @Nonnull
    @Override
    public BodyComponent clone() {
        return new BodyComponent(spaceUuid, kind, persistenceMode);
    }
}
