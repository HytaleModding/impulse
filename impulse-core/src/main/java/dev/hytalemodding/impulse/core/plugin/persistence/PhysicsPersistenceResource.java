package dev.hytalemodding.impulse.core.plugin.persistence;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

/**
 * Plugin-facing contract for Impulse's world-level persistence resource.
 */
public abstract class PhysicsPersistenceResource implements Resource<EntityStore> {

    public static final int CURRENT_SCHEMA_VERSION = 6;

    @Nonnull
    public static ResourceType<EntityStore, ? extends PhysicsPersistenceResource> getResourceType() {
        return ImpulsePlugin.get().getPersistentPhysicsWorldResourceType();
    }

    public abstract int getSchemaVersion();

    public abstract int getSpaceCount();

    public abstract int getBodyCount();

    public abstract int getJointCount();

    public abstract boolean isRuntimeRestorePending();

    public abstract void markRuntimeRestorePending();

    public abstract boolean isRuntimeSpaceBootstrapComplete();

    public abstract boolean hasRuntimeRestoreFailed();

    public abstract boolean hasRuntimeRestoreSkips();

    @Nonnull
    public abstract String runtimeRestoreFailureSummary();

    @Nonnull
    public abstract String runtimeRestoreSummary();

    @Nonnull
    public abstract PhysicsPersistenceSyncResult saveRuntimeSnapshot(
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource runtime);

    @Nonnull
    @Override
    public abstract PhysicsPersistenceResource clone();
}
