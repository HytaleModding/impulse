package dev.hytalemodding.impulse.core.plugin.simulation;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Compact copied result for a batch of closest-raycast queries.
 *
 * <p>Each result slot corresponds to the same index in the submitted
 * {@link RaycastClosestBatchQuery#rays()} list. A {@code null} slot means that ray had no hit.</p>
 */
public final class RaycastClosestBatchResult {

    private final RaycastHitView[] hits;
    private final int hitCount;

    public RaycastClosestBatchResult(@Nonnull RaycastHitView[] hits) {
        this.hits = Arrays.copyOf(Objects.requireNonNull(hits, "hits"), hits.length);
        int presentHits = 0;
        for (RaycastHitView hit : this.hits) {
            if (hit != null) {
                presentHits++;
            }
        }
        hitCount = presentHits;
    }

    public int rayCount() {
        return hits.length;
    }

    public int hitCount() {
        return hitCount;
    }

    public boolean hasHit(int index) {
        checkIndex(index);
        return hits[index] != null;
    }

    @Nullable
    public RaycastHitView hit(int index) {
        checkIndex(index);
        return hits[index];
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= hits.length) {
            throw new IndexOutOfBoundsException(index);
        }
    }
}
