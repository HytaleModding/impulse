package dev.hytalemodding.impulse.core.internal.modules.control;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Runtime-only state for bodies currently driven by an Impulse control session.
 */
public final class PhysicsControlRuntimeState {

    private final Long2ObjectOpenHashMap<LongSet> controlledBodyLeastBitsByMostBits =
        new Long2ObjectOpenHashMap<>();

    public synchronized void markBodyControlled(@Nonnull UUID bodyUuid) {
        add(bodyUuid.getMostSignificantBits(), bodyUuid.getLeastSignificantBits());
    }

    public synchronized void markBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        add(bodyKey.mostSignificantBits(), bodyKey.leastSignificantBits());
    }

    public synchronized void clearControlledBody(@Nonnull UUID bodyUuid) {
        remove(bodyUuid.getMostSignificantBits(), bodyUuid.getLeastSignificantBits());
    }

    public synchronized void clearControlledBody(@Nonnull RigidBodyKey bodyKey) {
        remove(bodyKey.mostSignificantBits(), bodyKey.leastSignificantBits());
    }

    public synchronized boolean isBodyControlled(@Nonnull UUID bodyUuid) {
        return contains(bodyUuid.getMostSignificantBits(), bodyUuid.getLeastSignificantBits());
    }

    public synchronized boolean isBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        return contains(bodyKey.mostSignificantBits(), bodyKey.leastSignificantBits());
    }

    public synchronized void clearBody(@Nonnull UUID bodyUuid) {
        remove(bodyUuid.getMostSignificantBits(), bodyUuid.getLeastSignificantBits());
    }

    public synchronized void clearBody(@Nonnull RigidBodyKey bodyKey) {
        remove(bodyKey.mostSignificantBits(), bodyKey.leastSignificantBits());
    }

    public synchronized void clear() {
        controlledBodyLeastBitsByMostBits.clear();
    }

    private void add(long mostSignificantBits, long leastSignificantBits) {
        controlledBodyLeastBitsByMostBits.computeIfAbsent(mostSignificantBits,
            _ -> new LongOpenHashSet()).add(leastSignificantBits);
    }

    private void remove(long mostSignificantBits, long leastSignificantBits) {
        LongSet leastBits = controlledBodyLeastBitsByMostBits.get(mostSignificantBits);
        if (leastBits == null) {
            return;
        }
        leastBits.remove(leastSignificantBits);
        if (leastBits.isEmpty()) {
            controlledBodyLeastBitsByMostBits.remove(mostSignificantBits);
        }
    }

    private boolean contains(long mostSignificantBits, long leastSignificantBits) {
        LongSet leastBits = controlledBodyLeastBitsByMostBits.get(mostSignificantBits);
        return leastBits != null && leastBits.contains(leastSignificantBits);
    }
}
