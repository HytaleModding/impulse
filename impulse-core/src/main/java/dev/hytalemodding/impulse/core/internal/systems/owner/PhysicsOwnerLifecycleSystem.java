package dev.hytalemodding.impulse.core.internal.systems.owner;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreRuntimeCleaner;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Starts and stops the per-world physics owner lane with the EntityStore lifecycle.
 */
public final class PhysicsOwnerLifecycleSystem extends StoreSystem<EntityStore>
    implements AutoCloseable {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    @Nonnull
    private final ResourceType<EntityStore, ? extends PhysicsOwnerResource> ownerResourceType;
    @Nonnull
    private final ResourceType<EntityStore, ? extends PhysicsWorldResource> physicsWorldResourceType;
    @Nonnull
    private final Set<PhysicsOwnerResource> activeOwners = ConcurrentHashMap.newKeySet();

    public PhysicsOwnerLifecycleSystem() {
        this(PhysicsOwnerResource.getResourceType(), PhysicsWorldResource.getResourceType());
    }

    PhysicsOwnerLifecycleSystem(
        @Nonnull ResourceType<EntityStore, ? extends PhysicsOwnerResource> ownerResourceType,
        @Nonnull ResourceType<EntityStore, ? extends PhysicsWorldResource> physicsWorldResourceType) {
        this.ownerResourceType = ownerResourceType;
        this.physicsWorldResourceType = physicsWorldResourceType;
    }

    @Override
    public void onSystemAddedToStore(@Nonnull Store<EntityStore> store) {
        PhysicsOwnerResource owner = store.getResource(ownerResourceType);
        if (startOwner(owner, worldName(store))) {
            PhysicsWorldRuntimeResource runtime =
                PhysicsWorldRuntimeResource.require(store.getResource(physicsWorldResourceType));
            runtime.attachEntityStore(store);
            runtime.attachOwnerExecutor(owner);
        }
    }

    @Override
    public void onSystemRemovedFromStore(@Nonnull Store<EntityStore> store) {
        String worldName = worldName(store);
        PhysicsOwnerResource owner = store.getResource(ownerResourceType);
        PhysicsWorldResource physics = store.getResource(physicsWorldResourceType);
        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(physics);
        RuntimeException clearFailure = tryClearSpaces(store, physics, worldName);
        boolean closedOwner = closeOwner(owner);
        runtime.detachOwnerExecutor(owner);
        runtime.detachEntityStore(store);

        // Retry if it failed before, this could happen.
        if (clearFailure != null && closedOwner) {
            clearFailure = tryClearSpaces(store, physics, worldName);
        }
        if (clearFailure != null) {
            LOGGER.at(Level.WARNING).log("Failed to clear physics spaces for world %s: %s",
                worldName,
                clearFailure.getMessage());
        }
    }

    @Override
    public void close() {
        for (PhysicsOwnerResource owner : new ArrayList<>(activeOwners)) {
            closeOwner(owner);
        }
    }

    boolean startOwner(@Nonnull PhysicsOwnerResource owner, @Nonnull String worldName) {
        try {
            owner.start(worldName);
            if (owner.isStarted()) {
                activeOwners.add(owner);
                return true;
            }
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log("Physics owner lane could not be started for world %s: %s",
                worldName,
                exception.getMessage());
        }
        return false;
    }

    boolean closeOwner(@Nonnull PhysicsOwnerResource owner) {
        activeOwners.remove(owner);
        try {
            owner.close();
            return true;
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log("Failed to close physics owner lane: %s",
                exception.getMessage());
            return false;
        }
    }

    int activeOwnerCount() {
        return activeOwners.size();
    }

    @Nonnull
    private static String worldName(@Nonnull Store<EntityStore> store) {
        return store.getExternalData().getWorld().getName();
    }

    @Nullable
    private static RuntimeException tryClearSpaces(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull String worldName) {
        try {
            Store<PhysicsStore> physicsStore = physicsStoreOrNull(store);
            if (physicsStore != null) {
                PhysicsStoreRuntimeCleaner.clearAll(physicsStore);
            } else {
                physics.clearAllSpaces(worldName);
            }
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    @Nullable
    private static Store<PhysicsStore> physicsStoreOrNull(@Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        try {
            Method accessor = world.getClass().getMethod("getPhysicsStore");
            Object physicsStore = accessor.invoke(world);
            return physicsStore instanceof PhysicsStore typedPhysicsStore
                ? typedPhysicsStore.getStore()
                : null;
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access authoritative PhysicsStore for world "
                + world.getName(), exception);
        }
    }
}
