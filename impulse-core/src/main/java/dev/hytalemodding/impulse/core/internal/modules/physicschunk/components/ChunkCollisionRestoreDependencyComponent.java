package dev.hytalemodding.impulse.core.internal.modules.physicschunk.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * Utility component to recover physicschunk bodies when loading bodies on top
 */
public final class ChunkCollisionRestoreDependencyComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<ChunkCollisionRestoreDependencyComponent> CODEC =
        BuilderCodec.builder(ChunkCollisionRestoreDependencyComponent.class,
                ChunkCollisionRestoreDependencyComponent::new)
            .append(new KeyedCodec<>("SpaceUuid", Codec.UUID_BINARY, false),
                (component, value) -> component.spaceUuid = value != null
                    ? value
                    : new UUID(0L, 0L),
                ChunkCollisionRestoreDependencyComponent::getSpaceUuid)
            .add()
            .append(new KeyedCodec<>("Center", Vector3fUtil.CODEC, false),
                (component, value) -> component.center.set(value != null ? value : new Vector3f()),
                ChunkCollisionRestoreDependencyComponent::getCenter)
            .add()
            .append(new KeyedCodec<>("Radius", Codec.INTEGER, false),
                (component, value) -> component.radius = Math.max(0, value != null ? value : 0),
                ChunkCollisionRestoreDependencyComponent::getRadius)
            .add()
            .append(new KeyedCodec<>("ModeAtSave",
                    new EnumCodec<>(PhysicsChunkCollisionMode.class),
                    false),
                (component, value) -> component.modeAtSave = value != null
                    ? value
                    : PhysicsChunkCollisionMode.NONE,
                ChunkCollisionRestoreDependencyComponent::getModeAtSave)
            .add()
            .build();

    @Nonnull
    private UUID spaceUuid = new UUID(0L, 0L);
    @Nonnull
    private final Vector3f center = new Vector3f();
    @Getter
    private int radius;
    @Nonnull
    private PhysicsChunkCollisionMode modeAtSave = PhysicsChunkCollisionMode.NONE;

    public ChunkCollisionRestoreDependencyComponent() {
    }

    public ChunkCollisionRestoreDependencyComponent(@Nonnull UUID spaceUuid,
        @Nonnull Vector3f center,
        int radius,
        @Nonnull PhysicsChunkCollisionMode modeAtSave) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.center.set(Objects.requireNonNull(center, "center"));
        this.radius = Math.max(0, radius);
        this.modeAtSave = Objects.requireNonNull(modeAtSave, "modeAtSave");
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    @Nonnull
    public Vector3f getCenter() {
        return new Vector3f(center);
    }

    @Nonnull
    public PhysicsChunkCollisionMode getModeAtSave() {
        return modeAtSave;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ChunkCollisionRestoreDependencyComponent> getComponentType() {
        return PhysicsComponentTypeRegistry.chunkCollisionRestoreDependencyComponentType();
    }

    @Nonnull
    @Override
    public ChunkCollisionRestoreDependencyComponent clone() {
        return new ChunkCollisionRestoreDependencyComponent(spaceUuid,
            center,
            radius,
            modeAtSave);
    }
}
