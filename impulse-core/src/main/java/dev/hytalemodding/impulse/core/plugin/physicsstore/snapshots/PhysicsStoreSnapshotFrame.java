package dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Immutable copied snapshot frame published by PhysicsStore after a completed backend step.
 */
public record PhysicsStoreSnapshotFrame(long sequence,
                                        float dt,
                                        @Nonnull List<PhysicsStoreBodySnapshot> bodies) {

    public static final PhysicsStoreSnapshotFrame EMPTY =
        new PhysicsStoreSnapshotFrame(0L, 0.0f, List.of());

    public PhysicsStoreSnapshotFrame {
        bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
    }
}
