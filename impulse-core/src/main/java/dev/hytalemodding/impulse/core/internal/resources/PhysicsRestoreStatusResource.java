package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import javax.annotation.Nonnull;

/**
 * Runtime-only restore status and skip accounting.
 */
public final class PhysicsRestoreStatusResource implements Resource<PhysicsStore> {

    @Getter
    private boolean pending;
    @Getter
    private boolean failed;
    @Getter
    private boolean hydrated;
    @Nonnull
    private String failureMessage = "";
    @Nonnull
    private final Object2IntMap<String> softSkipsByReason = new Object2IntLinkedOpenHashMap<>();

    public PhysicsRestoreStatusResource() {
    }

    public void markPending() {
        pending = true;
        failed = false;
        hydrated = false;
        failureMessage = "";
        softSkipsByReason.clear();
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

    public void markHydrated() {
        hydrated = true;
    }

    public void markRecoveredFromCleanup() {
        pending = false;
        failed = false;
        hydrated = true;
        failureMessage = "";
        softSkipsByReason.clear();
    }

    public void recordSoftSkip(@Nonnull String reason) {
        softSkipsByReason.put(reason, softSkipsByReason.getInt(reason) + 1);
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
        return PhysicsResourceTypes.restoreStatusResourceType();
    }
}
