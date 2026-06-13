package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied transient force or impulse request for a PhysicsStore body after backend binding.
 */
public record BodyForceRequest(@Nonnull UUID requestUuid,
                               @Nonnull UUID bodyUuid,
                               @Nonnull Kind kind,
                               float x,
                               float y,
                               float z,
                               boolean hasOffset,
                               float offsetX,
                               float offsetY,
                               float offsetZ) implements PhysicsStoreRequest {

    public BodyForceRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(kind, "kind");
    }

    @Nonnull
    public static BodyForceRequest impulse(@Nonnull UUID bodyUuid, float x, float y, float z) {
        return new BodyForceRequest(UUID.randomUUID(),
            bodyUuid,
            Kind.IMPULSE,
            x,
            y,
            z,
            false,
            0.0f,
            0.0f,
            0.0f);
    }

    @Nonnull
    public static BodyForceRequest impulseAt(@Nonnull UUID bodyUuid,
        float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        return new BodyForceRequest(UUID.randomUUID(),
            bodyUuid,
            Kind.IMPULSE,
            x,
            y,
            z,
            true,
            offsetX,
            offsetY,
            offsetZ);
    }

    @Nonnull
    public static BodyForceRequest torqueImpulse(@Nonnull UUID bodyUuid, float x, float y, float z) {
        return new BodyForceRequest(UUID.randomUUID(),
            bodyUuid,
            Kind.TORQUE_IMPULSE,
            x,
            y,
            z,
            false,
            0.0f,
            0.0f,
            0.0f);
    }

    @Nonnull
    public static BodyForceRequest force(@Nonnull UUID bodyUuid, float x, float y, float z) {
        return new BodyForceRequest(UUID.randomUUID(),
            bodyUuid,
            Kind.FORCE,
            x,
            y,
            z,
            false,
            0.0f,
            0.0f,
            0.0f);
    }

    @Nonnull
    public static BodyForceRequest forceAt(@Nonnull UUID bodyUuid,
        float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        return new BodyForceRequest(UUID.randomUUID(),
            bodyUuid,
            Kind.FORCE,
            x,
            y,
            z,
            true,
            offsetX,
            offsetY,
            offsetZ);
    }

    @Nonnull
    public static BodyForceRequest torque(@Nonnull UUID bodyUuid, float x, float y, float z) {
        return new BodyForceRequest(UUID.randomUUID(),
            bodyUuid,
            Kind.TORQUE,
            x,
            y,
            z,
            false,
            0.0f,
            0.0f,
            0.0f);
    }

    public enum Kind {
        IMPULSE,
        TORQUE_IMPULSE,
        FORCE,
        TORQUE
    }
}
