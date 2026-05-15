package dev.hytalemodding.impulse.examples.commands.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One body entry inside a manual Impulse snapshot file.
 *
 * <p>Pairs an entity UUID with a full {@link PersistentPhysicsBodyComponent} clone.
 * The manual snapshot format uses these entries to match snapshot bodies back to
 * existing Hytale entities on load, rather than spawning new entities.</p>
 */
public class PersistentPhysicsSnapshotBody {

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsSnapshotBody> CODEC = BuilderCodec.builder(
            PersistentPhysicsSnapshotBody.class,
            PersistentPhysicsSnapshotBody::new)
        .append(new KeyedCodec<>("EntityUuid", Codec.UUID_BINARY), (entry, value) -> entry.entityUuid = value,
            entry -> entry.entityUuid)
        .add()
        .append(new KeyedCodec<>("Body", PersistentPhysicsBodyComponent.CODEC),
            (entry, value) -> entry.body = value.clone(),
            entry -> entry.body)
        .add()
        .build();

    @Nullable
    private UUID entityUuid;
    @Nullable
    private PersistentPhysicsBodyComponent body;

    public PersistentPhysicsSnapshotBody() {
    }

    public PersistentPhysicsSnapshotBody(@Nonnull UUID entityUuid,
        @Nonnull PersistentPhysicsBodyComponent body) {
        this.entityUuid = entityUuid;
        this.body = body;
    }

    @Nullable
    public UUID getEntityUuid() {
        return entityUuid;
    }

    @Nullable
    public PersistentPhysicsBodyComponent getBody() {
        return body;
    }

    @Nonnull
    public PersistentPhysicsSnapshotBody copy() {
        return new PersistentPhysicsSnapshotBody(entityUuid, body != null ? body.clone() : null);
    }
}
