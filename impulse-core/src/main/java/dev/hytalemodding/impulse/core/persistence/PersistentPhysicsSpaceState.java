package dev.hytalemodding.impulse.core.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.voxel.WorldCollisionMode;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Codec-backed definition of one physics space for the persistence layer.
 *
 * <p>Captures the space identity, backend choice, gravity, and world-collision
 * settings so that {@link PersistentPhysicsSpaceBootstrapSystem} can recreate
 * the runtime {@link dev.hytalemodding.impulse.api.PhysicsSpace} after a
 * world load or manual snapshot restore.</p>
 */
public class PersistentPhysicsSpaceState {

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsSpaceState> CODEC = BuilderCodec.builder(
            PersistentPhysicsSpaceState.class,
            PersistentPhysicsSpaceState::new)
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER), (state, value) -> state.spaceId = value,
            state -> state.spaceId)
        .add()
        .append(new KeyedCodec<>("BackendId", Codec.STRING), (state, value) -> state.backendId = value,
            state -> state.backendId)
        .add()
        .append(new KeyedCodec<>("Gravity", Vector3fUtil.CODEC), (state, value) -> state.gravity.set(value),
            state -> state.gravity)
        .add()
        .append(new KeyedCodec<>("WorldCollisionMode", new EnumCodec<>(WorldCollisionMode.class)),
            (state, value) -> state.worldCollisionMode = value,
            state -> state.worldCollisionMode)
        .add()
        .append(new KeyedCodec<>("WorldCollisionRadius", Codec.INTEGER),
            (state, value) -> state.worldCollisionRadius = value,
            state -> state.worldCollisionRadius)
        .add()
        .append(new KeyedCodec<>("WorldCollisionBodyRadius", Codec.INTEGER),
            (state, value) -> state.worldCollisionBodyRadius = value,
            state -> state.worldCollisionBodyRadius)
        .add()
        .append(new KeyedCodec<>("WorldCollisionTtlTicks", Codec.INTEGER),
            (state, value) -> state.worldCollisionTtlTicks = value,
            state -> state.worldCollisionTtlTicks)
        .add()
        .build();

    private int spaceId;
    @Nonnull
    private String backendId = "";
    @Nonnull
    private final Vector3f gravity = new Vector3f(0.0f, -9.81f, 0.0f);
    @Nonnull
    private WorldCollisionMode worldCollisionMode = WorldCollisionMode.NONE;
    private int worldCollisionRadius = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_RADIUS;
    private int worldCollisionBodyRadius = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS;
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
        state.worldCollisionRadius = settings.getWorldCollisionRadius();
        state.worldCollisionBodyRadius = settings.getWorldCollisionBodyRadius();
        state.worldCollisionTtlTicks = settings.getWorldCollisionTtlTicks();
        return state;
    }

    public int getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(int spaceId) {
        this.spaceId = spaceId;
    }

    @Nonnull
    public String getBackendId() {
        return backendId;
    }

    public void setBackendId(@Nonnull String backendId) {
        this.backendId = backendId;
    }

    @Nonnull
    public Vector3f getGravity() {
        return gravity;
    }

    @Nonnull
    public WorldCollisionMode getWorldCollisionMode() {
        return worldCollisionMode;
    }

    public void setWorldCollisionMode(@Nonnull WorldCollisionMode worldCollisionMode) {
        this.worldCollisionMode = worldCollisionMode;
    }

    public int getWorldCollisionRadius() {
        return worldCollisionRadius;
    }

    public void setWorldCollisionRadius(int worldCollisionRadius) {
        this.worldCollisionRadius = worldCollisionRadius;
    }

    public int getWorldCollisionBodyRadius() {
        return worldCollisionBodyRadius;
    }

    public void setWorldCollisionBodyRadius(int worldCollisionBodyRadius) {
        this.worldCollisionBodyRadius = worldCollisionBodyRadius;
    }

    public int getWorldCollisionTtlTicks() {
        return worldCollisionTtlTicks;
    }

    public void setWorldCollisionTtlTicks(int worldCollisionTtlTicks) {
        this.worldCollisionTtlTicks = worldCollisionTtlTicks;
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
        copy.worldCollisionRadius = worldCollisionRadius;
        copy.worldCollisionBodyRadius = worldCollisionBodyRadius;
        copy.worldCollisionTtlTicks = worldCollisionTtlTicks;
        return copy;
    }
}
