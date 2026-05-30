package dev.hytalemodding.impulse.core.plugin.snapshot;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * Immutable async snapshot slice for one physics space.
 */
public record PublishedPhysicsSpaceFrame(@Nonnull SpaceId spaceId,
                                         long frameEpoch,
                                         long worldEpoch,
                                         long spaceEpoch,
                                         @Nonnull List<PublishedPhysicsBodySnapshot> bodies) {

    public PublishedPhysicsSpaceFrame {
        Objects.requireNonNull(spaceId, "spaceId");
        requireNonNegativeEpoch(frameEpoch, "frameEpoch");
        requireNonNegativeEpoch(worldEpoch, "worldEpoch");
        requireNonNegativeEpoch(spaceEpoch, "spaceEpoch");
        bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
        for (PublishedPhysicsBodySnapshot body : bodies) {
            requireBodyInFrame(spaceId, frameEpoch, worldEpoch, spaceEpoch, body);
        }
    }

    public int bodyCount() {
        return bodies.size();
    }

    public void forEachBody(@Nonnull Consumer<? super PublishedPhysicsBodySnapshot> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (PublishedPhysicsBodySnapshot body : bodies) {
            consumer.accept(body);
        }
    }

    private static void requireBodyInFrame(@Nonnull SpaceId spaceId,
        long frameEpoch,
        long worldEpoch,
        long spaceEpoch,
        @Nonnull PublishedPhysicsBodySnapshot body) {
        if (!spaceId.equals(body.spaceId())) {
            throw new IllegalArgumentException("body space id does not match space frame");
        }
        if (body.frameEpoch() != frameEpoch) {
            throw new IllegalArgumentException("body frame epoch does not match space frame");
        }
        if (body.worldEpoch() != worldEpoch) {
            throw new IllegalArgumentException("body world epoch does not match space frame");
        }
        if (body.spaceEpoch() != spaceEpoch) {
            throw new IllegalArgumentException("body space epoch does not match space frame");
        }
    }

    private static void requireNonNegativeEpoch(long epoch, @Nonnull String label) {
        if (epoch < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }
}
