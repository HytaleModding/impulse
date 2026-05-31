package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Owner-lane query for every ray hit in one physics space.
 *
 * <p>The endpoints are defensively copied so callers can reuse mutable vector instances after
 * submission.</p>
 */
public record RaycastAllQuery(@Nonnull SpaceId spaceId,
                              @Nonnull Vector3f from,
                              @Nonnull Vector3f to) implements PhysicsQuery<List<RaycastHitView>> {

    public RaycastAllQuery {
        Objects.requireNonNull(spaceId, "spaceId");
        from = new Vector3f(Objects.requireNonNull(from, "from"));
        to = new Vector3f(Objects.requireNonNull(to, "to"));
    }

    @Nonnull
    @Override
    public Vector3f from() {
        return new Vector3f(from);
    }

    @Nonnull
    @Override
    public Vector3f to() {
        return new Vector3f(to);
    }
}
