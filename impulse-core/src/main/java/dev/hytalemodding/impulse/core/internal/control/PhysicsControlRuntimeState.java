package dev.hytalemodding.impulse.core.internal.control;

import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Runtime-only state for bodies currently driven by an Impulse control session.
 */
public final class PhysicsControlRuntimeState {

    private final Set<PhysicsBodyId> controlledBodies = new ObjectOpenHashSet<>();

    public void markBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        controlledBodies.add(bodyId);
    }

    public void clearControlledBody(@Nonnull PhysicsBodyId bodyId) {
        controlledBodies.remove(bodyId);
    }

    public boolean isBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        return controlledBodies.contains(bodyId);
    }

    public void clearBody(@Nonnull PhysicsBodyId bodyId) {
        controlledBodies.remove(bodyId);
    }

    public void clear() {
        controlledBodies.clear();
    }
}
