package dev.hytalemodding.impulse.core.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.resources.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.systems.PersistentPhysicsSpaceBootstrapSystem;
import dev.hytalemodding.impulse.core.voxel.WorldCollisionMode;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Codec-backed definition of one physics space for the persistence layer.
 *
 * <p>Captures the space identity, backend choice, gravity, and world-collision
 * settings so that {@link PersistentPhysicsSpaceBootstrapSystem} can recreate
 * the runtime {@link dev.hytalemodding.impulse.api.PhysicsSpace} after a
 * world load or manual snapshot restore.</p>
 */
@Getter
public class PersistentPhysicsSpaceState {

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsSpaceState> CODEC = BuilderCodec.builder(
            PersistentPhysicsSpaceState.class,
            PersistentPhysicsSpaceState::new)
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER),
            (state, value) -> state.spaceId = value,
            PersistentPhysicsSpaceState::getSpaceId)
        .add()
        .append(new KeyedCodec<>("BackendId", Codec.STRING),
            (state, value) -> state.backendId = value,
            PersistentPhysicsSpaceState::getBackendId)
        .add()
        .append(new KeyedCodec<>("Gravity", Vector3fUtil.CODEC),
            (state, value) -> state.gravity.set(value),
            PersistentPhysicsSpaceState::getGravity)
        .add()
        .append(new KeyedCodec<>("WorldCollisionMode", new EnumCodec<>(WorldCollisionMode.class)),
            (state, value) -> state.worldCollisionMode = value,
            PersistentPhysicsSpaceState::getWorldCollisionMode)
        .add()
        .append(new KeyedCodec<>("EntityChunkBoundaryMode",
                new EnumCodec<>(EntityChunkBoundaryMode.class)),
            (state, value) -> state.entityChunkBoundaryMode = value,
            PersistentPhysicsSpaceState::getEntityChunkBoundaryMode)
        .add()
        .append(new KeyedCodec<>("WorldCollisionRadius", Codec.INTEGER),
            (state, value) -> state.worldCollisionRadius = value,
            PersistentPhysicsSpaceState::getWorldCollisionRadius)
        .add()
        .append(new KeyedCodec<>("WorldCollisionBodyRadius", Codec.INTEGER),
            (state, value) -> state.worldCollisionBodyRadius = value,
            PersistentPhysicsSpaceState::getWorldCollisionBodyRadius)
        .add()
        .append(new KeyedCodec<>("WorldCollisionTtlTicks", Codec.INTEGER),
            (state, value) -> state.worldCollisionTtlTicks = value,
            PersistentPhysicsSpaceState::getWorldCollisionTtlTicks)
        .add()
        .build();

    @Setter
    private int spaceId;
    @Nonnull
    @Setter
    private String backendId = "";
    @Nonnull
    private final Vector3f gravity = new Vector3f(0.0f, -9.81f, 0.0f);
    @Nonnull
    @Setter
    private WorldCollisionMode worldCollisionMode = WorldCollisionMode.NONE;
    @Nonnull
    @Setter
    private EntityChunkBoundaryMode entityChunkBoundaryMode =
        PhysicsSpaceSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    @Setter
    private int worldCollisionRadius = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_RADIUS;
    @Setter
    private int worldCollisionBodyRadius = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS;
    @Setter
    private int worldCollisionTtlTicks = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_TTL_TICKS;

    public PersistentPhysicsSpaceState() {
    }

    @Nonnull
    public static PersistentPhysicsSpaceState from(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        PersistentPhysicsSpaceState state = new PersistentPhysicsSpaceState();
        state.spaceId = space.getId().value();
        state.backendId = space.getBackendId().value();
        state.gravity.set(space.getGravity());
        state.worldCollisionMode = settings.getWorldCollisionMode();
        state.entityChunkBoundaryMode = settings.getEntityChunkBoundaryMode();
        state.worldCollisionRadius = settings.getWorldCollisionRadius();
        state.worldCollisionBodyRadius = settings.getWorldCollisionBodyRadius();
        state.worldCollisionTtlTicks = settings.getWorldCollisionTtlTicks();
        return state;
    }

    @Nonnull
    public SpaceId toSpaceId() {
        return new SpaceId(spaceId);
    }

    @Nonnull
    public BackendId toBackendId() {
        return new BackendId(backendId);
    }

    @Nonnull
    public PhysicsSpaceSettings toSettings() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.setWorldCollisionMode(worldCollisionMode);
        settings.setEntityChunkBoundaryMode(entityChunkBoundaryMode);
        settings.setWorldCollisionRadius(worldCollisionRadius);
        settings.setWorldCollisionBodyRadius(worldCollisionBodyRadius);
        settings.setWorldCollisionTtlTicks(worldCollisionTtlTicks);
        return settings;
    }

    @Nonnull
    public PersistentPhysicsSpaceState copy() {
        PersistentPhysicsSpaceState copy = new PersistentPhysicsSpaceState();
        copy.spaceId = spaceId;
        copy.backendId = backendId;
        copy.gravity.set(gravity);
        copy.worldCollisionMode = worldCollisionMode;
        copy.entityChunkBoundaryMode = entityChunkBoundaryMode;
        copy.worldCollisionRadius = worldCollisionRadius;
        copy.worldCollisionBodyRadius = worldCollisionBodyRadius;
        copy.worldCollisionTtlTicks = worldCollisionTtlTicks;
        return copy;
    }
}
