package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Describes a body that should be created and registered on the physics owner.
 *
 * <p>The factory receives the resolved {@link dev.hytalemodding.impulse.api.PhysicsSpace} and may
 * configure the live body before registration. Callers keep the returned {@link PhysicsBodyId}
 * instead of retaining the live backend body. A spawn spec is a single-use request: its body id is
 * reserved when the spec is created.</p>
 */
public final class PhysicsBodySpawnSpec {

    @Nonnull
    private final PhysicsBodyId bodyId;
    @Nonnull
    private final SpaceId spaceId;
    @Nonnull
    private final PhysicsBodyKind kind;
    @Nonnull
    private final PhysicsBodyPersistenceMode persistenceMode;
    @Nonnull
    private final PhysicsBodyFactory factory;

    private PhysicsBodySpawnSpec(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull PhysicsBodyFactory factory) {
        this.bodyId = Objects.requireNonNull(bodyId, "bodyId");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Nonnull
    public static PhysicsBodySpawnSpec persistentBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyFactory factory) {
        return persistentBody(PhysicsBodyId.random(), spaceId, factory);
    }

    @Nonnull
    public static PhysicsBodySpawnSpec persistentBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyFactory factory) {
        return new PhysicsBodySpawnSpec(bodyId,
            spaceId,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT,
            factory);
    }

    @Nonnull
    public static PhysicsBodySpawnSpec runtimeBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyFactory factory) {
        return runtimeBody(PhysicsBodyId.random(), spaceId, factory);
    }

    @Nonnull
    public static PhysicsBodySpawnSpec runtimeBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyFactory factory) {
        return new PhysicsBodySpawnSpec(bodyId,
            spaceId,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            factory);
    }

    @Nonnull
    public PhysicsBodyId bodyId() {
        return bodyId;
    }

    @Nonnull
    public SpaceId spaceId() {
        return spaceId;
    }

    @Nonnull
    public PhysicsBodyKind kind() {
        return kind;
    }

    @Nonnull
    public PhysicsBodyPersistenceMode persistenceMode() {
        return persistenceMode;
    }

    @Nonnull
    public PhysicsBodyFactory factory() {
        return factory;
    }
}
