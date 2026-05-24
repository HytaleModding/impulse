package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Canonical plugin-facing helpers for body-id-first body creation.
 *
 * <p>Use this facade for ordinary gameplay bodies. Use
 * {@link PhysicsWorldResource#addBody(SpaceId, PhysicsBody, PhysicsBodyKind, PhysicsBodyPersistenceMode)}
 * only when code already created a live backend body on the physics owner and needs the lower-level
 * registry operation.</p>
 */
public final class PhysicsBodies {

    private PhysicsBodies() {
    }

    /**
     * Creates a backend body on the physics owner, registers it with Impulse, and returns its stable
     * id.
     *
     * <p>The factory may touch the live {@link PhysicsSpace} and returned {@link PhysicsBody} only
     * during the owner callback. Store the returned {@link PhysicsBodyId} for later operations.</p>
     */
    @Nonnull
    public static PhysicsBodySpawnResult spawn(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBodySpawnSpec spec) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(spec, "spec");
        return resource.callOnPhysicsOwner("spawn physics body", () -> {
            if (resource.getBodyRegistrationView(spec.bodyId()) != null) {
                throw new IllegalArgumentException("Physics body id=" + spec.bodyId()
                    + " is already registered");
            }
            PhysicsSpace space = resource.getSpace(spec.spaceId());
            if (space == null) {
                throw new IllegalArgumentException("Physics space id=" + spec.spaceId()
                    + " is not registered");
            }
            PhysicsBody body = Objects.requireNonNull(spec.factory().create(space), "body");
            resource.addBody(spec.bodyId(),
                spec.spaceId(),
                body,
                spec.kind(),
                spec.persistenceMode());
            return new PhysicsBodySpawnResult(spec.bodyId(),
                spec.spaceId(),
                spec.kind(),
                spec.persistenceMode());
        });
    }
}
