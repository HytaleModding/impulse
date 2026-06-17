package dev.hytalemodding.impulse.core.plugin.snapshots;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Immutable copied snapshot frame published by PhysicsStore after a completed backend step.
 */
public record PhysicsSnapshotFrame(long sequence,
                                   float dt,
                                   @Nonnull List<PhysicsBodySnapshot> bodies) {

    public static final PhysicsSnapshotFrame EMPTY =
        new PhysicsSnapshotFrame(0L, 0.0f, List.of());

    public PhysicsSnapshotFrame {
        bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
    }
}
