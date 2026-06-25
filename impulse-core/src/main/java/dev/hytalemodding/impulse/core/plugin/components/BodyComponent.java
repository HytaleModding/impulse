package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Authored body space ownership.
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
        .build();

    @Nonnull
    private UUID spaceUuid = new UUID(0L, 0L);
    @Nullable
    private transient Ref<PhysicsStore> spaceRef;

    public BodyComponent() {
    }

    public BodyComponent(@Nonnull UUID spaceUuid) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    public void setSpaceUuid(@Nonnull UUID spaceUuid) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.spaceRef = null;
    }

    @Nullable
    public Ref<PhysicsStore> getSpaceRef() {
        return spaceRef;
    }

    public void setSpaceRef(@Nullable Ref<PhysicsStore> spaceRef) {
        this.spaceRef = spaceRef;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, BodyComponent> getComponentType() {
        return PhysicsComponentTypes.bodyComponentType();
    }

    @Nonnull
    @Override
    public BodyComponent clone() {
        BodyComponent copy = new BodyComponent(spaceUuid);
        copy.spaceRef = spaceRef;
        return copy;
    }
}
