package dev.hytalemodding.impulse.core.plugin.physics;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/**
 * Public world-level reads and settings writes for PhysicsStore resources.
 */
public final class PhysicsWorlds {

    private PhysicsWorlds() {
    }

    @Nonnull
    public static PhysicsEventFrame latestEventFrame(@Nonnull Store<PhysicsStore> store) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read the latest PhysicsStore event frame");
        return checkedStore.getResource(PhysicsEventResource.getResourceType()).getLatestFrame();
    }

    @Nonnull
    public static CompletionStage<PhysicsEventFrame> latestEventFrameAsync(
        @Nonnull World world) {
        return PhysicsThreading.enqueueReadOnWorldThread(world,
            "queue PhysicsStore event frame read",
            PhysicsWorlds::latestEventFrame);
    }

    @Nonnull
    public static PhysicsWorldSettings settings(@Nonnull Store<PhysicsStore> store) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "read PhysicsStore world settings");
        return checkedStore.getResource(PhysicsWorldSettingsResource.getResourceType())
            .getSettings();
    }

    @Nonnull
    public static CompletionStage<PhysicsWorldSettings> settingsAsync(@Nonnull World world) {
        return PhysicsThreading.enqueueReadOnWorldThread(world,
            "queue PhysicsStore world settings read",
            PhysicsWorlds::settings);
    }

    public static void putSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsWorldSettings settings) {
        Store<PhysicsStore> checkedStore = requireWorldThread(store,
            "update PhysicsStore world settings");
        PhysicsThreading.requireBackendIdle(checkedStore, "update PhysicsStore world settings");
        PhysicsWorldSettings requested = new PhysicsWorldSettings(
            Objects.requireNonNull(settings, "settings"));
        validateStepModeSupported(checkedStore, requested.getStepMode());
        checkedStore.getResource(PhysicsWorldSettingsResource.getResourceType())
            .setSettings(requested);
    }

    @Nonnull
    public static CompletionStage<Void> putSettingsAsync(@Nonnull World world,
        @Nonnull PhysicsWorldSettings settings) {
        PhysicsWorldSettings requested = new PhysicsWorldSettings(
            Objects.requireNonNull(settings, "settings"));
        return PhysicsThreading.callWhenBackendIdleOnWorldThread(world,
            "queue PhysicsStore world settings update",
            store -> {
                putSettings(store, requested);
                return null;
            });
    }

    private static void validateStepModeSupported(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsStepMode stepMode) {
        if (stepMode != PhysicsStepMode.CCD) {
            return;
        }
        List<String> unsupportedSpaces = new ArrayList<>();
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        runtime.forEachRuntimeSpaceBinding((spaceRef, backendId, spaceHandle, backendRuntime) -> {
            if (!backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                UUID spaceUuid = runtime.getSpaceUuid(spaceRef);
                unsupportedSpaces.add((spaceUuid != null ? spaceUuid : spaceRef)
                    + " backend=" + backendId.value());
            }
        });
        if (!unsupportedSpaces.isEmpty()) {
            throw new IllegalArgumentException("CCD step mode is not supported by PhysicsStore "
                + "spaces: " + unsupportedSpaces);
        }
    }

    @Nonnull
    private static Store<PhysicsStore> requireWorldThread(@Nonnull Store<PhysicsStore> store,
        @Nonnull String operation) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore, operation);
        return checkedStore;
    }
}
