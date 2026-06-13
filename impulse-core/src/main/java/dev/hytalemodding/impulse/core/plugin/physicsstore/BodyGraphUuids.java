package dev.hytalemodding.impulse.core.plugin.physicsstore;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Deterministic private row UUIDs for one-body PhysicsStore graphs.
 */
public final class BodyGraphUuids {

    private BodyGraphUuids() {
    }

    @Nonnull
    public static UUID collider(@Nonnull UUID bodyUuid) {
        return row(bodyUuid, "collider");
    }

    @Nonnull
    public static UUID shape(@Nonnull UUID bodyUuid) {
        return row(bodyUuid, "shape");
    }

    @Nonnull
    public static UUID material(@Nonnull UUID bodyUuid) {
        return row(bodyUuid, "material");
    }

    @Nonnull
    public static UUID filter(@Nonnull UUID bodyUuid) {
        return row(bodyUuid, "filter");
    }

    @Nonnull
    public static List<UUID> privateOwnedRows(@Nonnull UUID bodyUuid) {
        return List.of(shape(bodyUuid), material(bodyUuid), filter(bodyUuid));
    }

    @Nonnull
    private static UUID row(@Nonnull UUID bodyUuid, @Nonnull String rowKind) {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(rowKind, "rowKind");
        return UUID.nameUUIDFromBytes(("impulse:physics-body:" + bodyUuid + ':' + rowKind)
            .getBytes(StandardCharsets.UTF_8));
    }
}
