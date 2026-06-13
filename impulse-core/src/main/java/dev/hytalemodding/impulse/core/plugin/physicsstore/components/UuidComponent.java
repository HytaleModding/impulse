package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Durable identity for one PhysicsStore row.
 */
public final class UuidComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<UuidComponent> CODEC = BuilderCodec.builder(
            UuidComponent.class,
            UuidComponent::new)
        .append(new KeyedCodec<>("Uuid", Codec.UUID_BINARY, false),
            (component, value) -> component.uuid = value != null ? value : UUID.randomUUID(),
            UuidComponent::getUuid)
        .add()
        .build();

    @Nonnull
    private UUID uuid = UUID.randomUUID();

    public UuidComponent() {
    }

    public UuidComponent(@Nonnull UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
    }

    @Nonnull
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(@Nonnull UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, UuidComponent> getComponentType() {
        return PhysicsStoreTypes.uuidComponentType();
    }

    @Nonnull
    @Override
    public UuidComponent clone() {
        return new UuidComponent(uuid);
    }
}
