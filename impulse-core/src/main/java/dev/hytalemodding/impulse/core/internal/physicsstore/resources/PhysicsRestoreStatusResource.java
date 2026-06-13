package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import javax.annotation.Nonnull;

/**
 * Runtime-only restore status and skip accounting.
 */
public final class PhysicsRestoreStatusResource implements Resource<PhysicsStore> {

    private boolean pending;
    private boolean failed;
    private boolean hydrated;
    @Nonnull
    private String failureMessage = "";
    @Nonnull
    private final Object2IntMap<String> softSkipsByReason = new Object2IntLinkedOpenHashMap<>();

    public PhysicsRestoreStatusResource() {
    }

    public boolean isPending() {
        return pending;
    }

    public void markPending() {
        pending = true;
        failed = false;
        hydrated = false;
        failureMessage = "";
        softSkipsByReason.clear();
    }

    public boolean isFailed() {
        return failed;
    }

    @Nonnull
    public String getFailureMessage() {
        return failureMessage;
    }

    public void markFailed(@Nonnull String failureMessage) {
        pending = false;
        failed = true;
        hydrated = false;
        this.failureMessage = failureMessage;
    }

    public void markComplete() {
        pending = false;
        failed = false;
        failureMessage = "";
    }

    public boolean isHydrated() {
        return hydrated;
    }

    public void markHydrated() {
        hydrated = true;
    }

    public void recordSoftSkip(@Nonnull String reason) {
        softSkipsByReason.mergeInt(reason, 1, Integer::sum);
    }

    @Nonnull
    public Object2IntMap<String> getSoftSkipsByReason() {
        return new Object2IntLinkedOpenHashMap<>(softSkipsByReason);
    }

    @Nonnull
    @Override
    public PhysicsRestoreStatusResource clone() {
        PhysicsRestoreStatusResource copy = new PhysicsRestoreStatusResource();
        copy.pending = pending;
        copy.failed = failed;
        copy.hydrated = hydrated;
        copy.failureMessage = failureMessage;
        copy.softSkipsByReason.putAll(softSkipsByReason);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRestoreStatusResource> getResourceType() {
        return PhysicsStoreTypes.restoreStatusResourceType();
    }
}
