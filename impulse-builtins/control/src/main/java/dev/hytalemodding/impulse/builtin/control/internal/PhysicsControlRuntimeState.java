package dev.hytalemodding.impulse.builtin.control.internal;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import javax.annotation.Nonnull;

/**
 * Runtime-only state for bodies currently driven by an Impulse control session.
 */
public final class PhysicsControlRuntimeState {

    private final Int2ObjectOpenHashMap<Ref<PhysicsStore>> controlledBodyRefsByRowIndex =
        new Int2ObjectOpenHashMap<>();

    public synchronized void markBodyControlled(@Nonnull Ref<PhysicsStore> bodyRef) {
        controlledBodyRefsByRowIndex.put(bodyRef.getIndex(), bodyRef);
    }

    public synchronized void clearControlledBody(@Nonnull Ref<PhysicsStore> bodyRef) {
        remove(bodyRef);
    }

    public synchronized boolean isBodyControlled(@Nonnull Ref<PhysicsStore> bodyRef) {
        Ref<PhysicsStore> controlledRef = controlledBodyRefsByRowIndex.get(bodyRef.getIndex());
        if (controlledRef == null) {
            return false;
        }
        if (!controlledRef.isValid()) {
            controlledBodyRefsByRowIndex.remove(bodyRef.getIndex());
            return false;
        }
        return controlledRef == bodyRef || sameLiveRef(controlledRef, bodyRef);
    }

    public synchronized void clearBody(@Nonnull Ref<PhysicsStore> bodyRef) {
        remove(bodyRef);
    }

    public synchronized void clear() {
        controlledBodyRefsByRowIndex.clear();
    }

    private void remove(@Nonnull Ref<PhysicsStore> bodyRef) {
        Ref<PhysicsStore> controlledRef = controlledBodyRefsByRowIndex.get(bodyRef.getIndex());
        if (controlledRef == null) {
            return;
        }
        if (controlledRef == bodyRef
            || !controlledRef.isValid()
            || sameLiveRef(controlledRef, bodyRef)) {
            controlledBodyRefsByRowIndex.remove(bodyRef.getIndex());
        }
    }

    private static boolean sameLiveRef(@Nonnull Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first.isValid()
            && second.isValid()
            && first.getStore() == second.getStore()
            && first.getIndex() == second.getIndex();
    }
}
