package dev.hytalemodding.impulse.builtin.control.internal;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * PhysicsStore-keyed runtime-only state for bodies currently driven by control sessions.
 */
public final class PhysicsControlRuntimeStates {

    @Nonnull
    private static final Map<Store<PhysicsStore>, PhysicsControlRuntimeState> STATES_BY_STORE =
        Collections.synchronizedMap(new WeakHashMap<>());

    private PhysicsControlRuntimeStates() {
    }

    public static void markControlled(@Nonnull Ref<PhysicsStore> bodyRef) {
        Store<PhysicsStore> store = requireStore(bodyRef, "mark a PhysicsStore body controlled");
        stateFor(store).markBodyControlled(bodyRef);
    }

    public static void clearControlled(@Nullable Ref<PhysicsStore> bodyRef) {
        Store<PhysicsStore> store = storeOrNull(bodyRef);
        if (store == null || bodyRef == null) {
            return;
        }
        PhysicsThreading.requireWorldThread(store, "clear a controlled PhysicsStore body");
        stateFor(store).clearControlledBody(bodyRef);
    }

    public static boolean isControlled(@Nullable Ref<PhysicsStore> bodyRef) {
        Store<PhysicsStore> store = storeOrNull(bodyRef);
        if (store == null || bodyRef == null) {
            return false;
        }
        PhysicsThreading.requireWorldThread(store, "check a controlled PhysicsStore body");
        return stateFor(store).isBodyControlled(bodyRef);
    }

    public static void clear(@Nonnull Store<PhysicsStore> store) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        synchronized (STATES_BY_STORE) {
            PhysicsControlRuntimeState state = STATES_BY_STORE.get(checkedStore);
            if (state != null) {
                state.clear();
            }
        }
    }

    public static void clearAll() {
        synchronized (STATES_BY_STORE) {
            for (PhysicsControlRuntimeState state : STATES_BY_STORE.values()) {
                state.clear();
            }
        }
    }

    @Nonnull
    private static PhysicsControlRuntimeState stateFor(@Nonnull Store<PhysicsStore> store) {
        synchronized (STATES_BY_STORE) {
            return STATES_BY_STORE.computeIfAbsent(store, _ -> new PhysicsControlRuntimeState());
        }
    }

    @Nonnull
    private static Store<PhysicsStore> requireStore(@Nonnull Ref<PhysicsStore> ref,
        @Nonnull String operation) {
        Store<PhysicsStore> store = Objects.requireNonNull(ref, "ref").getStore();
        if (store == null) {
            throw new IllegalArgumentException("PhysicsStore ref has no owning store");
        }
        PhysicsThreading.requireWorldThread(store, operation);
        return store;
    }

    @Nullable
    private static Store<PhysicsStore> storeOrNull(@Nullable Ref<PhysicsStore> ref) {
        return ref != null ? ref.getStore() : null;
    }
}
