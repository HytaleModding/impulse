package dev.hytalemodding.impulse.core.internal.modules.control;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Runtime-only state for bodies currently driven by an Impulse control session.
 */
public final class PhysicsControlRuntimeState {

    private final Set<RigidBodyKey> controlledBodies = new ObjectOpenHashSet<>();

    public synchronized void markBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        controlledBodies.add(bodyKey);
    }

    public synchronized void clearControlledBody(@Nonnull RigidBodyKey bodyKey) {
        controlledBodies.remove(bodyKey);
    }

    public synchronized boolean isBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        return controlledBodies.contains(bodyKey);
    }

    public synchronized void clearBody(@Nonnull RigidBodyKey bodyKey) {
        controlledBodies.remove(bodyKey);
    }

    public synchronized void clear() {
        controlledBodies.clear();
    }
}
