package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Durable reference to a PhysicsStore row.
 */
public final class PhysicsPersistentRef {

    @Nonnull
    public static final BuilderCodec<PhysicsPersistentRef> CODEC = BuilderCodec.builder(
            PhysicsPersistentRef.class,
            PhysicsPersistentRef::new)
        .append(new KeyedCodec<>("Uuid", Codec.UUID_BINARY, false),
            (ref, value) -> ref.uuid = value != null ? value : UUID.randomUUID(),
            PhysicsPersistentRef::getUuid)
        .add()
        .build();

    @Nonnull
    private UUID uuid = UUID.randomUUID();

    public PhysicsPersistentRef() {
    }

    public PhysicsPersistentRef(@Nonnull UUID uuid) {
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
    public PhysicsPersistentRef copy() {
        return new PhysicsPersistentRef(uuid);
    }
}
