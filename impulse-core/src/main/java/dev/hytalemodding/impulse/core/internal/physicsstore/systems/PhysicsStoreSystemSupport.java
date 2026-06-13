package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class PhysicsStoreSystemSupport {

    static final UUID NIL_UUID = new UUID(0L, 0L);
    static final ComponentType<PhysicsStore, UuidComponent> UUID_TYPE =
        UuidComponent.getComponentType();
    static final Query<PhysicsStore> UUID_QUERY = UUID_TYPE;

    private PhysicsStoreSystemSupport() {
    }

    @Nonnull
    static UUID rowUuid(@Nonnull ArchetypeChunk<PhysicsStore> chunk, int index) {
        UuidComponent uuid = chunk.getComponent(index, UUID_TYPE);
        return uuid != null ? uuid.getUuid() : NIL_UUID;
    }

    static boolean isNil(@Nonnull UUID uuid) {
        return NIL_UUID.equals(uuid);
    }

    @Nullable
    static <C extends Component<PhysicsStore>> C component(@Nonnull Store<PhysicsStore> store,
        @Nullable Ref<PhysicsStore> ref,
        @Nonnull ComponentType<PhysicsStore, C> type) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return store.getComponent(ref, type);
    }

    @Nullable
    static Ref<PhysicsStore> refForUuid(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID uuid) {
        Ref<PhysicsStore> ref = identity.getByUuid(uuid);
        return ref != null && ref.isValid() ? ref : null;
    }
}
