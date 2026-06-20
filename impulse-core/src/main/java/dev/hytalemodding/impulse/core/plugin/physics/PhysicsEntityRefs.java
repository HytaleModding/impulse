package dev.hytalemodding.impulse.core.plugin.physics;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

final class PhysicsEntityRefs {

    private PhysicsEntityRefs() {
    }

    @Nonnull
    static UUID entityUuid(@Nonnull Ref<PhysicsStore> ref) {
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(ref, "ref");
        Store<PhysicsStore> store = checkedRef.getStore();
        PhysicsThreading.requireWorldThread(store, "read a PhysicsStore entity UUID");
        if (!checkedRef.isValid()) {
            throw new IllegalStateException("PhysicsStore entity ref is not valid: " + checkedRef);
        }
        UuidComponent uuid = store.getComponent(checkedRef, UuidComponent.getComponentType());
        if (uuid == null) {
            throw new IllegalStateException("PhysicsStore entity has no UUID component: " + checkedRef);
        }
        return uuid.getUuid();
    }

    static void requireSameStore(@Nonnull Ref<PhysicsStore> expectedStoreRef,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull String name) {
        if (Objects.requireNonNull(ref, name).getStore()
            != Objects.requireNonNull(expectedStoreRef, "expectedStoreRef").getStore()) {
            throw new IllegalArgumentException("PhysicsStore entity ref belongs to a different store: "
                + name);
        }
    }
}
