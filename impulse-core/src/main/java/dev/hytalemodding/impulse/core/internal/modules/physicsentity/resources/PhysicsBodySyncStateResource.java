package dev.hytalemodding.impulse.core.internal.modules.physicsentity.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.resources.PhysicsBodyRuntimeState.BodySyncState;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only EntityStore sync state for body-to-entity transform projection.
 */
public final class PhysicsBodySyncStateResource implements Resource<EntityStore> {

    @Nullable
    private static ResourceType<EntityStore, PhysicsBodySyncStateResource> resourceType;

    private final Map<Ref<EntityStore>, BodySyncState> bodySyncStates =
        new Reference2ObjectOpenHashMap<>();

    @Nonnull
    public synchronized BodySyncState getOrCreate(@Nonnull Ref<EntityStore> entityRef) {
        return bodySyncStates.computeIfAbsent(entityRef, _ -> new BodySyncState());
    }

    @Nullable
    public synchronized BodySyncState get(@Nonnull Ref<EntityStore> entityRef) {
        return bodySyncStates.get(entityRef);
    }

    public synchronized void clear(@Nonnull Ref<EntityStore> entityRef) {
        bodySyncStates.remove(entityRef);
    }

    public synchronized void clear() {
        bodySyncStates.clear();
    }

    @Nonnull
    @Override
    public PhysicsBodySyncStateResource clone() {
        PhysicsBodySyncStateResource copy = new PhysicsBodySyncStateResource();
        synchronized (this) {
            copy.bodySyncStates.putAll(bodySyncStates);
        }
        return copy;
    }

    @Nullable
    public static ResourceType<EntityStore, PhysicsBodySyncStateResource> getResourceType() {
        return resourceType;
    }

    public static void setResourceType(
        @Nonnull ResourceType<EntityStore, PhysicsBodySyncStateResource> type) {
        resourceType = type;
    }

    public static void clearResourceType() {
        resourceType = null;
    }
}
