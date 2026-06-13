package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Copied kinematic target request from gameplay/control systems.
 */
public record BodyTargetRequest(@Nonnull UUID requestUuid,
                                @Nonnull UUID bodyUuid,
                                @Nonnull Vector3f position,
                                @Nonnull Quaternionf rotation,
                                @Nonnull Vector3f linearVelocity,
                                @Nonnull Vector3f angularVelocity,
                                boolean transformEnabled,
                                boolean velocityEnabled,
                                boolean activate) implements PhysicsStoreRequest {

    public BodyTargetRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        position = new Vector3f(Objects.requireNonNull(position, "position"));
        rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
        linearVelocity = new Vector3f(Objects.requireNonNull(linearVelocity, "linearVelocity"));
        angularVelocity = new Vector3f(Objects.requireNonNull(angularVelocity, "angularVelocity"));
    }

    @Nonnull
    public static BodyTargetRequest of(@Nonnull UUID bodyUuid,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        return new BodyTargetRequest(UUID.randomUUID(),
            bodyUuid,
            position,
            rotation,
            linearVelocity,
            angularVelocity,
            true,
            true,
            true);
    }

    @Nonnull
    public static BodyTargetRequest of(@Nonnull UUID bodyUuid,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity,
        boolean transformEnabled,
        boolean velocityEnabled,
        boolean activate) {
        return new BodyTargetRequest(UUID.randomUUID(),
            bodyUuid,
            position,
            rotation,
            linearVelocity,
            angularVelocity,
            transformEnabled,
            velocityEnabled,
            activate);
    }

    @Nonnull
    @Override
    public Vector3f position() {
        return new Vector3f(position);
    }

    @Nonnull
    @Override
    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    @Nonnull
    @Override
    public Vector3f linearVelocity() {
        return new Vector3f(linearVelocity);
    }

    @Nonnull
    @Override
    public Vector3f angularVelocity() {
        return new Vector3f(angularVelocity);
    }
}
